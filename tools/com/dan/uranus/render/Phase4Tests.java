package com.dan.uranus.render;

import com.dan.uranus.model.SimClock;
import com.dan.uranus.model.UranusSystem;
import com.dan.uranus.physics.SpaceMap;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Locale;

/**
 * Prüfungen für Phase 4 (Magnetosphäre, Polarlicht, Lichtbahnen, ε-Ring-Flug).
 * Liegt im Paket com.dan.uranus.render, weil die Ebenen paketintern sind.
 */
public class Phase4Tests {

    static int ok, bad;

    static void check(String name, boolean c, String info) {
        if (c) ok++; else bad++;
        System.out.println((c ? "[ok]     " : "[FEHLER] ") + name + (info.isEmpty() ? "" : " — " + info));
    }

    public static void main(String[] a) {
        Locale.setDefault(Locale.GERMANY);
        magneto();
        geodesics();
        ring();
        images();
        System.out.println();
        System.out.println("Ergebnis Phase 4: " + ok + " ok, " + bad + " Fehler");
        System.exit(bad == 0 ? 0 : 1);
    }

    static double angle(double[] u, double[] v) {
        double d = u[0] * v[0] + u[1] * v[1] + u[2] * v[2];
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d))));
    }

    // ------------------------------------------------------------------ Magnetosphäre
    static void magneto() {
        double[] m = new double[3], e1 = new double[3], e2 = new double[3];
        double worst = 0;
        for (int k = 0; k < 36; k++) {
            MagnetoLayer.axis(k * 0.7, m, e1, e2);
            worst = Math.max(worst, Math.abs(angle(m, new double[]{0, 1, 0}) - 59));
        }
        check("Magnetachse 59° gegen die Drehachse", worst < 1e-9, String.format("größte Abweichung %.1e°", worst));
        MagnetoLayer.axis(1.234, m, e1, e2);
        double ortho = Math.abs(m[0] * e1[0] + m[1] * e1[1] + m[2] * e1[2]) + Math.abs(m[0] * e2[0] + m[1] * e2[1] + m[2] * e2[2]) + Math.abs(e1[0] * e2[0] + e1[1] * e2[1] + e1[2] * e2[2]);
        check("Magnetische Basis orthonormal", ortho < 1e-12, String.format("%.1e", ortho));
        double[] m2 = new double[3];
        MagnetoLayer.axis(1.234 + 2 * Math.PI, m2, e1, e2);
        check("Achse dreht mit dem Planeten (nach einer Umdrehung deckungsgleich)", angle(m, m2) < 1e-6, "");
        MagnetoLayer.axis(0.3, m, e1, e2);
        double[] p = new double[3];
        double dev = 0;
        for (double th = 0.2; th < Math.PI - 0.2; th += 0.05) {
            MagnetoLayer.dipolePoint(4.6, 0.7, th, m, e1, e2, p);
            p[1] += MagnetoLayer.OFFSET;
            double r = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
            dev = Math.max(dev, Math.abs(r - 4.6 * Math.sin(th) * Math.sin(th)));
        }
        check("Feldlinie folgt r = L·sin²θ", dev < 1e-12, String.format("größte Abweichung %.1e R", dev));
        MagnetoLayer.dipolePoint(5, 0, 0, m, e1, e2, p);
        check("Dipol um 0,3 R zum IAU-Südpol versetzt", Math.abs(p[0]) < 1e-12 && Math.abs(p[1] + 0.3) < 1e-12 && Math.abs(p[2]) < 1e-12, "");
        double foot = Math.toDegrees(MagnetoLayer.footColatitude(6));
        check("Polarlicht-Oval am Fußpunkt der Schale L = 6", Math.abs(foot - Math.toDegrees(PlanetLayer.AURORA_COLAT)) < 1e-9 && Math.abs(foot - 24.09) < 0.01,
                String.format("%.2f° um den Magnetpol", foot));
    }

    // ------------------------------------------------------------------ Lichtbahnen
    static void geodesics() {
        double[] v0 = {0, 0, -1};
        double K = GeodesicLayer.K, R = SpaceMap.PR;
        // weit draußen gilt die Näherung des schwachen Feldes; lange Bahn, damit fast die ganze Ablenkung erfasst ist
        GeodesicLayer.Ray r5 = GeodesicLayer.trace(new double[]{25, 0, 300}, v0, 0, 0, 0, R, K, 600);
        double d5 = r5.deflection(v0), weak = 2 * K / 25;
        check("Schwaches Feld: Ablenkung ≈ 2K/b", Math.abs(d5 / weak - 1) < 0.05, String.format("b = 25: %.5f rad gegen %.5f rad", d5, weak));
        double prev = 9;
        boolean mono = true;
        for (double b : new double[]{3, 4, 6, 9, 14}) {
            GeodesicLayer.Ray r = GeodesicLayer.trace(new double[]{b, 0, 40}, v0, 0, 0, 0, R, K, 80);
            double d = r.deflection(v0);
            if (r.hit || d >= prev) mono = false;
            prev = d;
        }
        check("Ablenkung nimmt mit dem Abstand ab", mono, "");
        GeodesicLayer.Ray hit = GeodesicLayer.trace(new double[]{1.0, 0, 40}, v0, 0, 0, 0, R, K, 80);
        GeodesicLayer.Ray miss = GeodesicLayer.trace(new double[]{2.6, 0, 40}, v0, 0, 0, 0, R, K, 80);
        check("Zu nahe Strahlen enden auf Uranus, weitere laufen vorbei", hit.hit && !miss.hit, "");
        GeodesicLayer.Ray side = GeodesicLayer.trace(new double[]{3, 0, 40}, v0, 0, 0, 0, R, K, 80);
        int e = (side.n - 1) * 3;
        check("Ablenkung zur Masse hin", side.pts[e] < 3, String.format("x am Ende %.2f (Start 3)", side.pts[e]));
        // die echte Größe zur Einordnung
        double alpha = 4 * UranusSystem.URANUS_GM * 1e9 / (299_792_458.0 * 299_792_458.0 * UranusSystem.URANUS_RADIUS_KM * 1e3);
        double mas = Math.toDegrees(alpha) * 3_600_000;
        check("Echte Lichtablenkung am Uranusrand", mas > 2.0 && mas < 2.2, String.format("%.2e rad = %.2f Millibogensekunden", alpha, mas));
    }

    // ------------------------------------------------------------------ ε-Ring
    static void ring() {
        double p = UranusSystem.periodFromA(RingFlight.R_KM) * 24;
        check("ε-Ring: Umlaufzeit aus Kepler", Math.abs(p - 8.39) < 0.05, String.format("%.2f h", p));
        double ni = RingFlight.meanMotion(RingFlight.R_KM - 29), no = RingFlight.meanMotion(RingFlight.R_KM + 29);
        double ratio = ni / no, kepler = Math.pow((RingFlight.R_KM + 29) / (RingFlight.R_KM - 29), 1.5);
        check("Innen schneller als außen (Scherung nach Kepler)", ni > no && Math.abs(ratio - kepler) < 1e-12, String.format("Verhältnis %.6f", ratio));
        double[] a = new double[4], b = new double[4];
        RingFlight.particleAt(3, 12345, 1, 9000.25, a);
        RingFlight.particleAt(3, 12345, 1, 9000.25, b);
        check("Teilchen sind reproduzierbar (kein Flackern)", a[0] == b[0] && a[1] == b[1] && a[2] == b[2] && a[3] == b[3], "");
        boolean inside = true;
        double maxSize = 0;
        for (int lane = 0; lane < RingFlight.LANES; lane++)
            for (long c = -500; c < 500; c += 7)
                for (int k = 0; k < RingFlight.PER_CELL; k++) {
                    RingFlight.particleAt(lane, c, k, 9000, a);
                    if (Math.abs(a[1]) > RingFlight.HALF_W + 1e-12 || Math.abs(a[2]) > RingFlight.THICK / 2 + 1e-12) inside = false;
                    maxSize = Math.max(maxSize, a[3]);
                }
        check("Teilchen bleiben im Ringband", inside, String.format("größter Brocken %.5f Welteinheiten", maxSize));

        // Phase 5: der ε-Ring ist eine Ellipse, der Flug folgt ihr
        com.dan.uranus.model.Ring eps = null;
        for (com.dan.uranus.model.Ring rg : UranusSystem.builtIn().rings()) if (rg.name.equals("ε")) eps = rg;
        RingFlight.shape(eps, com.dan.uranus.model.SimClock.JD_J2000);
        double k0 = RingFlight.widthScale(0), k1 = RingFlight.widthScale(Math.PI);
        check("Ringflug folgt der Ellipse (Breite ×0,34 … ×1,66, Radius ±0,79 %)", Math.abs(k0 - 19.7 / 58.05) < 1e-3 && Math.abs(k1 - 96.4 / 58.05) < 1e-3
                && Math.abs(RingFlight.radiusAt(0) / RingFlight.worldRadius() - (1 - 0.0079)) < 1e-9, String.format("%.2f / %.2f", k0, k1));
        RingFlight.shape(null, 0);

        // Kamera nach dem Einschwenken: dicht über dem Ring, auf seinem Radius
        Camera cam = new Camera();
        cam.setViewport(1280, 720);
        cam.setPose(0, 0, 0, 0.5, 0.4, 30);
        RingFlight f = new RingFlight();
        for (int i = 0; i < 400; i++) f.update(cam, true, 9000 + i * 1e-6, i / 60.0, -1.55, 1 / 60.0);
        double tx = cam.cx + cam.fx * cam.distance(), ty = cam.cy + cam.fy * cam.distance(), tz = cam.cz + cam.fz * cam.distance();
        double r = Math.hypot(tx, tz);
        check("Ringflug: Kamera auf dem ε-Radius, knapp über der Ebene", Math.abs(r - RingFlight.radiusAt(Math.atan2(tz, tx))) < 1e-6 && Math.abs(ty - (-1.55 + 0.0035)) < 1e-6 && Math.abs(cam.distance() - 0.045) < 1e-9,
                String.format("Radius %.4f, Abstand %.3f", r, cam.distance()));
    }

    // ------------------------------------------------------------------ Bilder
    static BufferedImage frame(SceneSettings s, int frames, double dt, double yaw, double pitch, double dist) {
        SimClock c = new SimClock();
        c.setJd(SimClock.jdOf(Instant.parse("2026-09-22T21:30:00Z")));
        c.setPaused(true);
        Renderer r = new Renderer(UranusSystem.builtIn(), s, c);
        r.camera().setOrientation(yaw, pitch);
        r.settle();
        if (dist > 0) { r.camera().setDistanceTarget(dist); r.camera().snap(); }
        Renderer.Frame f = null;
        for (int i = 0; i < frames; i++) f = r.render(800, 450, dt);
        return f.image;
    }

    static int diff(BufferedImage a, BufferedImage b) {
        int n = 0;
        for (int y = 0; y < a.getHeight(); y += 2) for (int x = 0; x < a.getWidth(); x += 2) {
            int p = a.getRGB(x, y), q = b.getRGB(x, y);
            int d = Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) + Math.abs((p & 255) - (q & 255));
            if (d > 24) n++;
        }
        return n;
    }

    static void images() {
        SceneSettings s = new SceneSettings();
        s.trails = false;
        BufferedImage on = frame(s, 4, 1 / 30.0, 1.1, 0.1, 40);
        s.magneto = false;
        BufferedImage off = frame(s, 4, 1 / 30.0, 1.1, 0.1, 40);
        int dm = diff(on, off);
        check("Magnetosphäre sichtbar", dm > 300, dm + " abweichende Stichproben");

        s = new SceneSettings();
        s.trails = false;
        BufferedImage aur = frame(s, 4, 1 / 30.0, 2.0, -0.55, 5.2);
        s.magneto = false;
        BufferedImage noAur = frame(s, 4, 1 / 30.0, 2.0, -0.55, 5.2);
        int violet = 0;
        for (int y = 0; y < aur.getHeight(); y += 2) for (int x = 0; x < aur.getWidth(); x += 2) {
            int p = aur.getRGB(x, y), q = noAur.getRGB(x, y);
            int dr = ((p >> 16) & 255) - ((q >> 16) & 255), db = (p & 255) - (q & 255), dg = ((p >> 8) & 255) - ((q >> 8) & 255);
            if (db > 25 && dr > 15 && dg < db) violet++;
        }
        check("Polarlicht auf der Nachtseite (violett)", violet > 150, violet + " violette Stichproben");

        s = new SceneSettings();
        s.magneto = false; s.trails = false;
        BufferedImage noRays = frame(s, 4, 1 / 30.0, 0.55, 0.3, 24);
        s.geodesics = true;
        BufferedImage rays = frame(s, 4, 1 / 30.0, 0.55, 0.3, 24);
        int dr = diff(rays, noRays);
        check("Lichtbahnen sichtbar", dr > 200, dr + " abweichende Stichproben");

        s = new SceneSettings();
        s.magneto = false; s.ringFlight = true;
        BufferedImage fl = frame(s, 170, 1 / 30.0, 0.55, 0.42, 0);
        int cyanRight = 0, grey = 0;
        for (int y = 0; y < fl.getHeight(); y += 3) for (int x = 0; x < fl.getWidth(); x += 3) {
            int p = fl.getRGB(x, y), r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            if (x > fl.getWidth() * 0.6 && g > r + 20 && b > r + 20 && g > 50) cyanRight++;
            if (y > fl.getHeight() * 0.45 && Math.abs(r - b) < 14 && r > 60) grey++;
        }
        check("Ringflug: Uranus füllt die Seite, Brocken im Vordergrund", cyanRight > 800 && grey > 40, cyanRight + " Cyan-, " + grey + " Brocken-Stichproben");
    }
}
