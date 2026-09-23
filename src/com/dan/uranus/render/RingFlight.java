package com.dan.uranus.render;

import com.dan.uranus.model.Ring;
import com.dan.uranus.model.UranusSystem;
import com.dan.uranus.physics.SpaceMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Flug durch den ε-Ring: die Kamera schwenkt aus der Übersicht ein, reist dicht über der
 * Ringebene mit dem Ring um Uranus und fliegt dabei langsam durch die Teilchen.
 * <p>
 * Teilchen: der Ring wird in Bahnen (radiale Spuren) und Winkelzellen geteilt; jede Zelle trägt
 * Teilchen, deren Lage aus einem Hash folgt — dieselbe Zelle liefert immer dieselben Teilchen,
 * nichts flackert. Jede Spur läuft mit ihrer eigenen Kepler-Winkelgeschwindigkeit (innen
 * schneller), dadurch schert der Ring bei Zeitraffer sichtbar. Die Hirtenmonde Cordelia (innen)
 * und Ophelia (außen) wellen die Ränder.
 * <p>
 * Überhöht: die Ringbreite (echt 20–96 km, dargestellt ~7-fach) und die Teilchengröße.
 */
final class RingFlight {

    static final double R_KM = 51_149, WIDTH_KM = 58;
    static final int LANES = 14, PER_CELL = 5;
    static final double DPHI = 0.0009, RANGE = 0.21;
    /** Dargestellte halbe Breite und Dicke in Welteinheiten. */
    static final double HALF_W = 0.012, THICK = 0.0024;
    static final double CELLS = Math.floor(2 * Math.PI / DPHI);

    // Flugzustand
    private boolean was;
    private double entry, phiStart, days0, rt0;
    private double sYaw, sPitch, sDist, sTx, sTy, sTz;
    private double camPhi;

    /** Winkelgeschwindigkeit (rad/Tag) im Abstand rKm vom Uranusmittelpunkt. */
    static double meanMotion(double rKm) { return 2 * Math.PI / UranusSystem.periodFromA(rKm); }

    /** Weltradius des ε-Rings (große Halbachse, wie die Ringlinie). */
    static double worldRadius() { return R_KM / UranusSystem.URANUS_RADIUS_KM * SpaceMap.PR; }

    // Form des ε-Rings: Ellipse mit Uranus im Brennpunkt, am Perizentrum schmal, am Apozentrum breit
    private static volatile double ecc = 0, wMin = WIDTH_KM, wMax = WIDTH_KM, peri = 0;

    /** Übernimmt die Form aus den Ringdaten (null = Kreis) für den Zeitpunkt jdTdb. */
    static void shape(Ring eps, double jdTdb) {
        if (eps == null) { ecc = 0; wMin = wMax = WIDTH_KM; return; }
        ecc = eps.ecc; wMin = eps.widthMinKm; wMax = eps.widthMaxKm; peri = eps.periapsis(jdTdb);
    }

    /** Weltradius der Ringmitte beim Weltwinkel phi. */
    static double radiusAt(double phi) {
        return worldRadius() * (1 - ecc * ecc) / (1 + ecc * Math.cos(phi - peri));
    }

    /** Breitenfaktor beim Weltwinkel phi, bezogen auf die mittlere Breite (ε: 0,34 … 1,66). */
    static double widthScale(double phi) {
        if (ecc == 0 || wMax <= wMin) return 1;
        double f = (1 - Math.cos(phi - peri)) * 0.5;
        return (wMin + (wMax - wMin) * f) / ((wMin + wMax) * 0.5);
    }

    static double hash(int a, int b, int c) {
        int h = a * 73_856_093 ^ b * 19_349_663 ^ c * 83_492_791;
        h = (h ^ (h >>> 13)) * 1_274_126_177;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / 2_147_483_647.0;
    }

    /** Lage eines Teilchens (Spur, Zelle, Nummer) zum Zeitpunkt days (Tage seit J2000) → {winkel, radiusOffset, höhe, größe}. */
    static void particleAt(int lane, long cell, int k, double days, double[] out) {
        particle(lane, cell, k, meanMotion(R_KM + ((lane + 0.5) / LANES - 0.5) * WIDTH_KM) * days, out);
    }

    /** wie oben, mit vorab gerechnetem Umlaufwinkel der Spur (n·t). */
    static void particle(int lane, long cell, int k, double laneAngle, double[] out) {
        int c = (int) Math.floorMod(cell, (long) CELLS);
        double h1 = hash(lane, c, k * 4), h2 = hash(lane, c, k * 4 + 1), h3 = hash(lane, c, k * 4 + 2), h4 = hash(lane, c, k * 4 + 3);
        double laneFrac = (lane + h2) / LANES - 0.5;                       // −0,5..0,5 über die Breite
        out[0] = (cell + h1) * DPHI + laneAngle;
        out[1] = laneFrac * 2 * HALF_W;
        out[2] = (h3 - 0.5) * THICK * (1 - Math.abs(laneFrac));
        out[3] = 0.00004 + 0.0009 * Math.pow(h4, 16);                    // viel Staub und Kies, wenige große Brocken
    }

    boolean active() { return was; }

    /** Führt die Kamera. entry 0→1 schwenkt aus der Ausgangslage ein. */
    void update(Camera cam, boolean on, double days, double realTime, double ucY, double dt) {
        if (on && !was) {
            was = true;
            entry = 0;
            sYaw = cam.yaw(); sPitch = cam.pitch(); sDist = cam.distance();
            sTx = cam.cx + cam.fx * cam.distance(); sTy = cam.cy + cam.fy * cam.distance(); sTz = cam.cz + cam.fz * cam.distance();
            phiStart = Math.atan2(cam.cz, cam.cx);
            days0 = days; rt0 = realTime;
        }
        if (!on) { was = false; return; }
        entry = Math.min(1, entry + dt / 4.5);
        double e = entry * entry * (3 - 2 * entry);
        camPhi = phiStart + meanMotion(R_KM) * (days - days0) + 0.016 * (realTime - rt0);
        double R = radiusAt(camPhi);
        double tx = R * Math.cos(camPhi), ty = ucY + 0.0035, tz = R * Math.sin(camPhi);
        double yaw = Math.PI - camPhi - 1.0 + 0.06 * Math.sin((realTime - rt0) * 0.21);   // Blick schräg nach innen: Uranus füllt die Seite
        double pitch = 0.075 + 0.02 * Math.sin((realTime - rt0) * 0.13);
        double dist = 0.045;
        double dy = Math.IEEEremainder(yaw - sYaw, 2 * Math.PI);
        cam.setPose(sTx + (tx - sTx) * e, sTy + (ty - sTy) * e, sTz + (tz - sTz) * e,
                sYaw + dy * e, sPitch + (pitch - sPitch) * e, Math.exp(Math.log(sDist) + (Math.log(dist) - Math.log(sDist)) * e));
    }

    double entry() { return was ? entry : 0; }

    // ------------------------------------------------------------------ Teilchen

    private static final class Rock { double sx, sy, sr, depth; float r, g, b; boolean behind; }

    // Teilchen dieses Bildes: x, y, Radius, Helligkeit, hinter Uranus (1/0)
    private float[] pts = new float[5 * 4096];
    private int count;
    private final List<Rock> rocks = new ArrayList<>();

    /**
     * Rechnet die Teilchen in der Nähe der Kamera für dieses Bild (einmal je Bild).
     * @param phiCordelia, phiOphelia Winkel der Hirtenmonde (Welt) für die Randwellen
     * @return Zahl der Teilchen im Bild
     */
    int prepare(Camera cam, double days, double ucY, double[] sun, double planetDepth,
                double alpha, double phiCordelia, double phiOphelia) {
        count = 0;
        rocks.clear();
        if (alpha < 0.01) return 0;
        double camAng = Math.atan2(cam.cz, cam.cx);
        double[] pr = new double[4], p = new double[3];
        for (int lane = 0; lane < LANES; lane++) {
            double laneFrac = (lane + 0.5) / LANES - 0.5;
            double laneAngle = meanMotion(R_KM + laneFrac * WIDTH_KM) * days;
            double local = camAng - laneAngle;                             // Kamerawinkel im Spursystem
            long c0 = (long) Math.floor(local / DPHI);
            long span = (long) (RANGE / DPHI);
            for (long c = c0 - span; c <= c0 + span; c++) {
                for (int k = 0; k < PER_CELL; k++) {
                    particle(lane, c, k, laneAngle, pr);
                    double phi = pr[0];
                    double off = pr[1] * widthScale(phi) + wake(phi, phiCordelia, laneFrac, true) + wake(phi, phiOphelia, laneFrac, false);
                    double rr = radiusAt(phi) + off;
                    double cph = Math.cos(phi), sph = Math.sin(phi);
                    double x = rr * cph, y = ucY + pr[2], z = rr * sph;
                    if (!cam.project(x, y, z, p)) continue;
                    if (p[0] < -40 || p[1] < -40 || p[0] > cam.width + 40 || p[1] > cam.height + 40) continue;
                    double sr = cam.foc * pr[3] / p[2];
                    if (sr > 60 || p[2] < 0.006) continue;                    // direkt vor der Linse: überspringen
                    boolean behind = planetDepth > 0 && p[2] > planetDepth;
                    // Licht: beleuchteter Anteil der sichtbaren Scheibe, Uranusschatten
                    double tcx = cam.cx - x, tcy = cam.cy - y, tcz = cam.cz - z, tl = Math.sqrt(tcx * tcx + tcy * tcy + tcz * tcz);
                    double litFrac = 0.5 + 0.5 * (tcx * sun[0] + tcy * sun[1] + tcz * sun[2]) / tl;
                    double py = y - ucY, tp = x * sun[0] + py * sun[1] + z * sun[2];
                    boolean shadow = tp < 0 && x * x + py * py + z * z - tp * tp < SpaceMap.PR * SpaceMap.PR;
                    double bright = (shadow ? 0.04 : 0.62 * (0.10 + 0.90 * litFrac)) + 0.035;
                    if (sr >= 5.0) {
                        Rock r = new Rock();
                        r.sx = p[0]; r.sy = p[1]; r.sr = sr; r.depth = p[2]; r.behind = behind;
                        float alb = (float) (0.45 + 0.35 * hash(lane, (int) Math.floorMod(c, (long) CELLS), k + 97));
                        r.r = shadow ? 0.03f : 0.66f * alb + 0.1f; r.g = shadow ? 0.03f : 0.64f * alb + 0.1f; r.b = shadow ? 0.035f : 0.62f * alb + 0.11f;
                        rocks.add(r);
                    } else {
                        if ((count + 1) * 5 > pts.length) { float[] np = new float[pts.length * 2]; System.arraycopy(pts, 0, np, 0, pts.length); pts = np; }
                        int o = count * 5;
                        pts[o] = (float) p[0]; pts[o + 1] = (float) p[1]; pts[o + 2] = (float) sr;
                        pts[o + 3] = (float) (bright * alpha); pts[o + 4] = behind ? 1 : 0;
                        count++;
                    }
                }
            }
        }
        rocks.sort((a, b) -> Double.compare(b.depth, a.depth));
        return count + rocks.size();
    }

    /** Zeichnet die vorbereiteten Teilchen; behind = die hinter Uranus (vor dem Planeten zeichnen). */
    void draw(Hdr hdr, Camera cam, double[] sun, double alpha, boolean behind) {
        if (alpha < 0.01) return;
        float want = behind ? 1 : 0;
        for (int i = 0; i < count; i++) {
            int o = i * 5;
            if (pts[o + 4] != want) continue;
            float sr = pts[o + 2], br = pts[o + 3];
            if (sr < 1.2f) {
                float k2 = Math.min(1.4f, 0.3f + sr);
                hdr.splat(pts[o], pts[o + 1], 0.62f * br * k2, 0.63f * br * k2, 0.66f * br * k2);
            } else {
                hdr.glow(pts[o], pts[o + 1], sr * 1.3, 0.62f * br, 0.63f * br, 0.66f * br);
            }
        }
        // Brocken: nach Tiefe sortiert, parallel über Bildstreifen (jeder Streifen hält die Reihenfolge ein)
        List<Rock> mine = new ArrayList<>();
        for (Rock r : rocks) if (r.behind == behind) mine.add(r);
        if (mine.isEmpty()) return;
        final int band = 24, bands = (hdr.h + band - 1) / band;
        final float al = (float) alpha;
        java.util.stream.IntStream.range(0, bands).parallel().forEach(bi -> {
            int ys = bi * band, ye = Math.min(hdr.h - 1, ys + band - 1);
            for (Rock r : mine) {
                if (r.sy + r.sr * 1.3 < ys || r.sy - r.sr * 1.3 > ye) continue;
                rock(hdr, cam, r, sun, al, ys, ye);
            }
        });
    }

    /** Randwellen, die ein Hirtenmond in den benachbarten Rand schreibt. */
    private static double wake(double phi, double phiMoon, double laneFrac, boolean inner) {
        if (Double.isNaN(phiMoon)) return 0;
        double edge = inner ? Math.max(0, -laneFrac - 0.25) : Math.max(0, laneFrac - 0.25);   // nur die äußeren Spuren
        if (edge <= 0) return 0;
        double d = Math.IEEEremainder(phi - phiMoon, 2 * Math.PI);
        double behindMoon = inner ? -d : d;                                  // der innere, schnellere Hirte zieht voraus
        if (behindMoon < 0) return 0;
        return 0.016 * edge * Math.sin(behindMoon * 55) * Math.exp(-behindMoon / 0.5) * (inner ? -1 : 1);
    }

    /** Ein naher Brocken als beleuchtete, leicht unebene Kugel. */
    private static void rock(Hdr hdr, Camera cam, Rock r, double[] sun, float alpha, int ys, int ye) {
        double ext = r.sr * 1.3;
        int x0 = (int) Math.max(0, Math.floor(r.sx - ext)), x1 = (int) Math.min(hdr.w - 1, Math.ceil(r.sx + ext));
        int y0 = (int) Math.max(ys, Math.floor(r.sy - ext)), y1 = (int) Math.min(ye, Math.ceil(r.sy + ext));
        if (x0 > x1 || y0 > y1) return;
        int seed = (int) (r.sx * 131 + r.sy * 71);
        double s1 = (seed & 255) * 0.0245, s2 = ((seed >> 8) & 255) * 0.0245;
        for (int y = y0; y <= y1; y++) {
            double b = (y + 0.5 - r.sy) / r.sr;
            for (int x = x0; x <= x1; x++) {
                double a = (x + 0.5 - r.sx) / r.sr, q0 = a * a + b * b;
                if (q0 >= 1.44) continue;
                // unregelmäßiger Umriss: Radius schwankt mit dem Winkel (kein Billardkugel-Look)
                double ang = Math.atan2(b, a);
                double lump = 1 + 0.16 * Math.sin(3 * ang + s1) + 0.09 * Math.sin(5 * ang + s2) + 0.05 * Math.sin(8 * ang + s1 * 2);
                double q = q0 / (lump * lump);
                if (q >= 1) continue;
                double mu = Math.sqrt(1 - q), aa = a / lump, bb = b / lump;
                double nx = aa * cam.rx - bb * cam.ux - mu * cam.fx, ny = aa * cam.ry - bb * cam.uy - mu * cam.fy, nz = aa * cam.rz - bb * cam.uz - mu * cam.fz;
                double lam = Math.max(0, nx * sun[0] + ny * sun[1] + nz * sun[2]);
                // eine flache Delle je Brocken
                double da = a - ((seed & 7) - 3.5) * 0.12, db = b - (((seed >> 3) & 7) - 3.5) * 0.12;
                double bump = 1 - 0.18 * Math.max(0, 1 - (da * da + db * db) * 6);
                float k = (float) ((0.06 + 0.94 * lam) * bump);
                float cov = (float) Math.min(1, (1 - Math.sqrt(q)) * r.sr * lump + 0.3) * alpha;
                hdr.over(x, y, r.r * k, r.g * k, r.b * k, cov);
            }
        }
    }
}
