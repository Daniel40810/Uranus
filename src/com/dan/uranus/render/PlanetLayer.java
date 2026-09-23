package com.dan.uranus.render;

import com.dan.uranus.model.Ring;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Uranus pro Pixel: Strahl-Kugel-Schnitt je Bildpunkt (perspektivisch exakt, auch aus der Nähe),
 * Methan-Cyan mit feinen Bändern, Randverdunklung, heller Polarhut, rotierende Wolkenflecken,
 * weiche Tag-Nacht-Grenze, Dunstsaum im Gegenlicht, Schatten der Ringe und der Monde,
 * Atmosphärenschein außerhalb der Scheibe. Die Zeilen werden parallel gerechnet.
 */
public final class PlanetLayer {

    // Wolkenflecken: Breite, Länge (rad), Größe², Helligkeit, Eigendrift (rad/Tag)
    private static final double[][] CLOUDS = {
            {0.52, 0.4, 0.010, 0.20, 0.9}, {0.35, 2.6, 0.006, 0.16, 1.1}, {0.78, 4.4, 0.014, 0.13, 0.7},
            {-0.28, 1.7, 0.008, 0.12, 1.3}, {0.62, 5.6, 0.005, 0.18, 1.0}, {0.18, 3.5, 0.004, 0.10, 1.2}};

    // Eingaben je Bild (vom Render-Thread gesetzt, danach nur gelesen)
    private double ux, uy, uz, radius;
    private double sunX, sunY, sunZ, spin, days;
    private double[] ringR = new double[0], ringHalf = new double[0], ringOp = new double[0], ringBase = new double[0];
    /** Exzentrizität und Perizentrum (Weltwinkel) je Ring — der ε-Ring ist eine Ellipse. */
    private double[] ringE = new double[0], ringPeri = new double[0];
    private List<Ring> ringList = java.util.Collections.emptyList();
    private double[] casters = new double[0];   // je Mond x, y, z, r
    private int casterCount;
    /** Monde, die in diesem Bild überhaupt einen Schatten auf Uranus werfen können (Vorauswahl je Bild). */
    private double[] active = new double[0];
    private int activeCount;
    /** Breitenabhängiger Teil der Bänder als Tabelle über die Breite (−π/2 … π/2). */
    private static final int BAND_N = 4096;
    private static final double[] BAND = new double[BAND_N + 2];
    static {
        for (int i = 0; i <= BAND_N + 1; i++) {
            double lat = -Math.PI / 2 + Math.PI * i / BAND_N;
            BAND[i] = 0.035 * Math.sin(lat * 7.0 + 0.4) + 0.018 * Math.sin(lat * 19.0 + 1.1)
                    - 0.05 * Math.exp(-(lat + 0.78) * (lat + 0.78) / 0.01);
        }
    }
    /** Polarlicht: Mitte des Ovals und Vorhang-Rauschen hängen nur von der magnetischen Länge ab — je Bild als Tabelle. */
    private static final int AUR_N = 2048;
    private final double[] aurCenter = new double[AUR_N + 1], aurCurtain = new double[AUR_N + 1];
    private double aurTableTime = Double.NaN;
    /** |cos| der Poldistanz, zwischen denen das Oval liegen kann (spart acos außerhalb). */
    private static final double AUR_C_MIN = Math.cos(Math.asin(Math.sqrt(1.0 / 6.0)) + 0.33),
            AUR_C_MAX = Math.cos(Math.asin(Math.sqrt(1.0 / 6.0)) - 0.33);
    private float intensity = 1f;
    // Polarlicht: Magnetachse und Basis (Welt), Stärke, Zeit; Unschärfe bei schneller Drehung
    private final double[] magM = {0, 1, 0}, magE1 = {1, 0, 0}, magE2 = {0, 0, 1};
    private double aurora, auroraTime, blur;
    /** Poldistanz des Polarlicht-Ovals um den Magnetpol (Fußpunkt der Schale L ≈ 6). */
    private static final double TWO_PI = Math.PI * 2;
    /**
     * Referenzweg für die Prüfung: rechnet Bänder, Polarlicht, Schattenwerfer und das Innere der Scheibe
     * ohne Tabellen und Abkürzungen, so wie vor Phase 8. Die Abkürzungen müssen dasselbe Bild liefern.
     */
    static volatile boolean exact;
    static final double AURORA_COLAT = Math.asin(Math.sqrt(1.0 / 6.0));

    public void setPlanet(double x, double y, double z, double r) { ux = x; uy = y; uz = z; radius = r; }

    public void setSun(double[] s) { sunX = s[0]; sunY = s[1]; sunZ = s[2]; }

    public void setRotation(double spinAngle, double daysSinceJ2000) { spin = spinAngle; days = daysSinceJ2000; }

    public void setRings(List<Ring> rings) {
        ringR = new double[rings.size()]; ringHalf = new double[rings.size()]; ringOp = new double[rings.size()];
        ringE = new double[rings.size()]; ringPeri = new double[rings.size()]; ringBase = new double[rings.size()];
        ringList = rings;
        for (int i = 0; i < rings.size(); i++) {
            Ring r = rings.get(i);
            ringR[i] = r.radiusRu;
            // schmale Ringe werfen sichtbar überhöhte Schattenstreifen
            ringHalf[i] = r.dusty ? r.widthRu * 0.5 : Math.max(0.004, r.widthRu * 6);
            ringOp[i] = r.dusty ? r.opacity * 0.6 : Math.min(0.85, r.opacity);
            ringE[i] = r.dusty ? 0 : r.ecc;
            ringBase[i] = ringOp[i];
        }
    }

    /** Sichtbarkeit je Ring 0..1 (Entdeckungsgeschichte): ein noch nicht entdeckter Ring wirft keinen Schatten. */
    public void setRingFade(float[] fade) {
        for (int i = 0; i < ringOp.length && i < fade.length; i++) ringOp[i] = ringBase[i] * fade[i];
    }

    /** Lage der Perizentren zum Zeitpunkt (JD, TDB). */
    public void setRingPhase(double jdTdb) {
        for (int i = 0; i < ringPeri.length && i < ringList.size(); i++) ringPeri[i] = ringList.get(i).periapsis(jdTdb);
    }

    public void setShadowCasters(double[] xyzr, int count) { casters = xyzr; casterCount = count; }

    /** Polarlicht an der Magnetachse m (Basis e1, e2); strength 0 = aus. */
    public void setAurora(double[] m, double[] e1, double[] e2, double strength, double time) {
        System.arraycopy(m, 0, magM, 0, 3); System.arraycopy(e1, 0, magE1, 0, 3); System.arraycopy(e2, 0, magE2, 0, 3);
        aurora = strength; auroraTime = time;
    }

    /** 0 = scharf, 1 = der Planet dreht sich so schnell, dass Einzelheiten verwischen. */
    public void setRotationBlur(double b) { blur = clamp01(b); }

    /** Zeichnet Uranus in den Puffer. */
    public void render(Hdr hdr, Camera cam) {
        double ocx = cam.cx - ux, ocy = cam.cy - uy, ocz = cam.cz - uz;
        double dist = Math.sqrt(ocx * ocx + ocy * ocy + ocz * ocz);
        if (dist <= radius * 1.001) return;
        double[] p = new double[3];
        if (!cam.project(ux, uy, uz, p)) return;
        double rs = cam.foc * radius / p[2];
        double reach = rs * 1.45 + 4;
        int x0 = (int) Math.max(0, Math.floor(p[0] - reach)), x1 = (int) Math.min(hdr.w - 1, Math.ceil(p[0] + reach));
        int y0 = (int) Math.max(0, Math.floor(p[1] - reach)), y1 = (int) Math.min(hdr.h - 1, Math.ceil(p[1] + reach));
        if (x0 > x1 || y0 > y1) return;
        prepareCasters();
        if (aurora > 0.01 && !exact) prepareAurora();
        final double pixelWorld = dist / cam.foc;
        final int fx0 = x0, fx1 = x1;
        // Große Scheibe (Nahflug): Farbe in halber Auflösung rechnen, Kante und Schein in voller
        final boolean half = rs > 170;
        final int hx0 = x0, hy0 = y0, hw = half ? (x1 - x0) / 2 + 2 : 0, hh2 = half ? (y1 - y0) / 2 + 2 : 0;
        final float[] hr = half ? ensureHalf(hw * hh2 * 3) : null;
        final boolean[] deep = half ? ensureDeep(hw * hh2) : null;
        // „tief“ = so weit innen, dass jeder Pixel der Nachbarzellen ganz bedeckt ist und keinen Schein bekommt
        final double deepHh = Math.min(radius, 0.955 * radius) - 6 * pixelWorld;
        final double oc2h = ocx * ocx + ocy * ocy + ocz * ocz;
        long qh0 = System.nanoTime();
        if (half) {
            IntStream.range(0, hh2).parallel().forEach(j -> {
                double[] d = new double[3], col = new double[3];
                for (int i = 0; i < hw; i++) {
                    int o = (j * hw + i) * 3;
                    if (!surface(cam, hx0 + i * 2 + 0.5, hy0 + j * 2 + 0.5, ocx, ocy, ocz, d, col)) { hr[o] = Float.NaN; deep[o / 3] = false; continue; }
                    hr[o] = (float) col[0]; hr[o + 1] = (float) col[1]; hr[o + 2] = (float) col[2];
                    double b = ocx * d[0] + ocy * d[1] + ocz * d[2];
                    deep[o / 3] = Math.sqrt(Math.max(0, oc2h - b * b)) < deepHh;
                }
            });
        }
        long qq0 = System.nanoTime();
        final double oc2 = ocx * ocx + ocy * ocy + ocz * ocz;
        IntStream.rangeClosed(y0, y1).parallel().forEach(y -> row(hdr, cam, y, fx0, fx1, ocx, ocy, ocz, oc2, pixelWorld, half, hx0, hy0, hw, hh2, hr, deep));
        if (Renderer.PROFILE) System.out.printf(java.util.Locale.ROOT, "PLANET halb %.1f voll %.1f%n", (qq0 - qh0) / 1e6, (System.nanoTime() - qq0) / 1e6);
    }

    /** Eine Bildzeile der vollen Auflösung: Kante, Farbe (aus dem Halbraster oder direkt) und Schein. */
    private void row(Hdr hdr, Camera cam, int y, int fx0, int fx1, double ocx, double ocy, double ocz, double oc2, double pixelWorld,
                     boolean half, int hx0, int hy0, int hw, int hh2, float[] hr, boolean[] deep) {
        double[] col = new double[3];
        int row = y * hdr.w;
        float[] R = hdr.r, G = hdr.g, B = hdr.b;
        // Sehstrahl wie Camera.rayDir, die Zeilenanteile einmal je Zeile
        double bq = (cam.halfH - (y + 0.5)) / cam.foc;
        double sxw = cam.fx + bq * cam.ux, syw = cam.fy + bq * cam.uy, szw = cam.fz + bq * cam.uz;
        double rx = cam.rx, ry = cam.ry, rz = cam.rz, hW = cam.halfW, foc = cam.foc;
        double vRow = (y - hy0) * 0.5;
        int jRow = (int) vRow;
        double fvRow = vRow - jRow;
        boolean rowHalf = half && jRow + 1 < hh2 && !exact;
        for (int x = fx0; x <= fx1; x++) {
            if (rowHalf) {
                // tief im Inneren: nur die Farbe aus dem Halbraster, ohne Sehstrahl (Bedeckung 1, kein Schein)
                double u = (x - hx0) * 0.5;
                int i0 = (int) u;
                if (i0 + 1 < hw) {
                    int c00 = jRow * hw + i0;
                    if (deep[c00] && deep[c00 + 1] && deep[c00 + hw] && deep[c00 + hw + 1]) {
                        int a00 = c00 * 3, a10 = a00 + 3, a01 = a00 + hw * 3, a11 = a01 + 3;
                        double fu = u - i0, w00 = (1 - fu) * (1 - fvRow), w10 = fu * (1 - fvRow), w01 = (1 - fu) * fvRow, w11 = fu * fvRow;
                        int o = row + x;
                        R[o] = (float) (hr[a00] * w00 + hr[a10] * w10 + hr[a01] * w01 + hr[a11] * w11);
                        G[o] = (float) (hr[a00 + 1] * w00 + hr[a10 + 1] * w10 + hr[a01 + 1] * w01 + hr[a11 + 1] * w11);
                        B[o] = (float) (hr[a00 + 2] * w00 + hr[a10 + 2] * w10 + hr[a01 + 2] * w01 + hr[a11 + 2] * w11);
                        continue;
                    }
                }
            }
            double aq = (x + 0.5 - hW) / foc;
            double vx = sxw + aq * rx, vy = syw + aq * ry, vz = szw + aq * rz;
            double il = 1.0 / Math.sqrt(vx * vx + vy * vy + vz * vz);
            double dx = vx * il, dy = vy * il, dz = vz * il;
            double b = ocx * dx + ocy * dy + ocz * dz;
            if (b >= 0) continue;
            double h2 = oc2 - b * b, hh = Math.sqrt(Math.max(0, h2));
            double cov = (radius - hh) / pixelWorld + 0.5;
            int o = row + x;
            if (cov > 0) {
                boolean done = false;
                if (half) {
                    // bilinear aus dem Halbraster, nur wenn alle vier Stützstellen auf der Scheibe liegen
                    double u = (x - hx0) * 0.5, v = (y - hy0) * 0.5;
                    int i0 = (int) u, j0 = (int) v;
                    if (i0 + 1 < hw && j0 + 1 < hh2) {
                        int a00 = (j0 * hw + i0) * 3, a10 = a00 + 3, a01 = a00 + hw * 3, a11 = a01 + 3;
                        if (!Float.isNaN(hr[a00]) && !Float.isNaN(hr[a10]) && !Float.isNaN(hr[a01]) && !Float.isNaN(hr[a11])) {
                            double fu = u - i0, fv = v - j0, w00 = (1 - fu) * (1 - fv), w10 = fu * (1 - fv), w01 = (1 - fu) * fv, w11 = fu * fv;
                            col[0] = hr[a00] * w00 + hr[a10] * w10 + hr[a01] * w01 + hr[a11] * w11;
                            col[1] = hr[a00 + 1] * w00 + hr[a10 + 1] * w10 + hr[a01 + 1] * w01 + hr[a11 + 1] * w11;
                            col[2] = hr[a00 + 2] * w00 + hr[a10 + 2] * w10 + hr[a01 + 2] * w01 + hr[a11 + 2] * w11;
                            done = true;
                        }
                    }
                }
                if (!done) {
                    double disc = radius * radius - h2;
                    double t = -b - Math.sqrt(Math.max(0, disc));
                    double nx = (ocx + t * dx) / radius, ny = (ocy + t * dy) / radius, nz = (ocz + t * dz) / radius;
                    double mu = -(nx * dx + ny * dy + nz * dz);
                    shade(nx, ny, nz, mu < 0 ? 0 : mu, col);
                }
                float a = (float) Math.min(1, cov);
                float k = 1 - a;
                R[o] = R[o] * k + (float) col[0] * a;
                G[o] = G[o] * k + (float) col[1] * a;
                B[o] = B[o] * k + (float) col[2] * a;
            }
            // Atmosphärenschein außerhalb der Scheibe, zur Sonne hin stärker
            double e = (hh - radius) / (radius * 0.09);
            if (e > -0.5 && e < 5) {
                double cx = ocx + (-b) * dx, cy = ocy + (-b) * dy, cz = ocz + (-b) * dz;
                double cl = Math.sqrt(cx * cx + cy * cy + cz * cz) + 1e-9;
                double sunward = (cx * sunX + cy * sunY + cz * sunZ) / cl;
                double g = Math.exp(-Math.max(0, e) * 1.25) * Math.max(0, sunward + 0.3) * 0.22 * intensity;
                if (e < 0) g *= Math.max(0, 1 + e * 2);
                R[o] += (float) (g * 0.45); G[o] += (float) (g * 0.85); B[o] += (float) (g * 0.95);
            }
        }
    }

    /**
     * Vorauswahl der Schattenwerfer: Ein Mond trifft Uranus nur, wenn er auf der Sonnenseite liegt
     * und sein Abstand von der Linie Uranus–Sonne kleiner ist als Radius plus Schattenbreite.
     * Die übrigen tragen in {@link #shade} ohnehin nichts bei.
     */
    private void prepareCasters() {
        if (active.length < casterCount * 4) active = new double[casterCount * 4];
        int n = 0;
        for (int i = 0; i < casterCount; i++) {
            if (exact) { System.arraycopy(casters, i * 4, active, n * 4, 4); n++; continue; }
            double wx = casters[i * 4] - ux, wy = casters[i * 4 + 1] - uy, wz = casters[i * 4 + 2] - uz, rm = casters[i * 4 + 3];
            double tp = wx * sunX + wy * sunY + wz * sunZ;
            if (tp <= -radius) continue;
            double perp2 = wx * wx + wy * wy + wz * wz - tp * tp, reach = radius + rm * 1.31;
            if (perp2 > reach * reach) continue;
            System.arraycopy(casters, i * 4, active, n * 4, 4);
            n++;
        }
        activeCount = n;
    }

    /** Tabelle der Ovalmitte und des Vorhangs über die magnetische Länge für die aktuelle Zeit. */
    private void prepareAurora() {
        if (auroraTime == aurTableTime) return;
        for (int i = 0; i <= AUR_N; i++) {
            double lonM = -Math.PI + 2 * Math.PI * i / AUR_N;
            double cl0 = Math.cos(lonM), sl0 = Math.sin(lonM);
            aurCenter[i] = AURORA_COLAT + 0.035 * Math.sin(lonM * 2 + 0.7)
                    + 0.025 * (com.dan.uranus.fx.Noise.value(cl0 * 1.8 + 5, sl0 * 1.8, auroraTime * 0.05) - 0.5);
            aurCurtain[i] = com.dan.uranus.fx.Noise.fbm(cl0 * 3.2, sl0 * 3.2, auroraTime * 0.12, 3);
        }
        aurTableTime = auroraTime;
    }

    private float[] halfBuf = new float[0];

    private boolean[] deepBuf = new boolean[0];

    private boolean[] ensureDeep(int n) {
        if (deepBuf.length < n) deepBuf = new boolean[n];
        return deepBuf;
    }

    private float[] ensureHalf(int n) {
        if (halfBuf.length < n) halfBuf = new float[n];
        return halfBuf;
    }

    /** Farbe des Oberflächenpunkts unter der Pixelmitte (px, py); false, wenn der Strahl vorbeigeht. */
    private boolean surface(Camera cam, double px, double py, double ocx, double ocy, double ocz, double[] d, double[] col) {
        cam.rayDir(px, py, d);
        double b = ocx * d[0] + ocy * d[1] + ocz * d[2];
        if (b >= 0) return false;
        double h2 = ocx * ocx + ocy * ocy + ocz * ocz - b * b;
        double disc = radius * radius - h2;
        if (disc <= 0) return false;
        double t = -b - Math.sqrt(disc);
        double nx = (ocx + t * d[0]) / radius, ny = (ocy + t * d[1]) / radius, nz = (ocz + t * d[2]) / radius;
        double mu = -(nx * d[0] + ny * d[1] + nz * d[2]);
        shade(nx, ny, nz, mu < 0 ? 0 : mu, col);
        return true;
    }

    /** Farbe eines Oberflächenpunkts; n = Normale (Welt), mu = Blickwinkel-Kosinus. */
    private void shade(double nx, double ny, double nz, double mu, double[] out) {
        double ndl = nx * sunX + ny * sunY + nz * sunZ;
        double lit = (ndl + 0.10) / 1.10;
        lit = lit <= 0 ? 0 : lit * (1.12 - 0.12 * lit);          // ≈ lit^0,85, ohne pow
        double lat = Math.asin(ny > 1 ? 1 : ny < -1 ? -1 : ny);
        double lon = Math.atan2(nz, nx) - spin;
        double bt = (lat + Math.PI / 2) / Math.PI * BAND_N;
        int bi = bt <= 0 ? 0 : bt >= BAND_N ? BAND_N : (int) bt;
        double bf = bt - bi;
        double band = (exact ? 0.035 * Math.sin(lat * 7.0 + 0.4) + 0.018 * Math.sin(lat * 19.0 + 1.1)
                - 0.05 * Math.exp(-(lat + 0.78) * (lat + 0.78) / 0.01) : BAND[bi] + (BAND[bi + 1] - BAND[bi]) * bf)
                + 0.02 * Math.sin(lat * 41 + 0.7 * Math.sin(lon * 3));
        double cl = 0, cosLat = Math.sqrt(Math.max(0, 1 - ny * ny));
        for (double[] c : CLOUDS) {
            double dl = lat - c[0];
            if (dl * dl >= 6 * c[2] && !exact) continue;        // zu weit in der Breite: trägt nichts bei
            double dn = lon - c[1] - c[4] * days * 0.02;
            dn = (dn - TWO_PI * Math.floor((dn + Math.PI) / TWO_PI)) * cosLat;
            double q = (dl * dl + dn * dn) / c[2];
            if (q < 6) cl += c[3] * Math.exp(-q);
        }
        cl *= 1 - blur;                        // verwischt bei schnellem Zeitraffer
        double hood = clamp01((lat - 0.95) / 0.35);
        double base = 1 + band + cl;
        double r = 0.42 * base + 0.26 * hood, g = 0.80 * base + 0.14 * hood, b = 0.88 * base + 0.11 * hood;
        double k = lit * (0.42 + 0.58 * Math.sqrt(mu));
        if (k > 0) {
            // Ringschatten: Strahl von der Oberfläche zur Sonne durch die Ringebene
            double px = nx * radius, py = ny * radius, pz = nz * radius;
            if (Math.abs(sunY) > 1e-6) {
                double t = -py / sunY;
                if (t > 0) {
                    double qx = px + t * sunX, qz = pz + t * sunZ;
                    double rr = Math.sqrt(qx * qx + qz * qz) / radius;
                    double th = Double.NaN;
                    for (int i = 0; i < ringR.length; i++) {
                        double ri = ringR[i];
                        if (ringE[i] > 0 && Math.abs(rr - ri) < ri * ringE[i] * 1.2 + ringHalf[i]) {
                            if (Double.isNaN(th)) th = Math.atan2(qz, qx);
                            ri = ri * (1 - ringE[i] * ringE[i]) / (1 + ringE[i] * Math.cos(th - ringPeri[i]));
                        }
                        double dd = Math.abs(rr - ri);
                        if (dd < ringHalf[i]) k *= 1 - ringOp[i] * (1 - dd / ringHalf[i] * 0.4);
                    }
                }
            }
            // Mondschatten (Transits): nur um die Tagundnachtgleiche möglich
            for (int i = 0; i < activeCount; i++) {
                double wx = active[i * 4] - (ux + px), wy = active[i * 4 + 1] - (uy + py), wz = active[i * 4 + 2] - (uz + pz);
                double tp = wx * sunX + wy * sunY + wz * sunZ;
                if (tp <= 0) continue;
                double d2 = wx * wx + wy * wy + wz * wz - tp * tp, rm = active[i * 4 + 3];
                if (d2 < rm * rm * 1.7) {
                    double f = 1 - smooth(rm * 0.75, rm * 1.3, Math.sqrt(Math.max(0, d2)));
                    k *= 1 - 0.93 * f;
                }
            }
        }
        r *= k; g *= k; b *= k;
        double hz = (1 - mu) * (1 - mu) * (1 - mu) * Math.max(0, ndl + 0.25) * 0.45;
        if (aurora > 0.01) {
            // Punkt relativ zum versetzten Dipol (0,3 R zum IAU-Südpol), Winkel zur Magnetachse
            double qx = nx, qy = ny + MagnetoLayer.OFFSET, qz = nz;
            double ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
            double c = Math.min(1, Math.abs((qx * magM[0] + qy * magM[1] + qz * magM[2]) / ql));
            double oval = 0, fringe = 0;
            // |colat − Oval| < 0,33 heißt: |cos| zwischen zwei festen Grenzen; acos nur dort
            if (c > AUR_C_MIN && c < AUR_C_MAX && blur < 0.999) {
                double colat = Math.acos(c);
                double lonM = Math.atan2(qx * magE2[0] + qy * magE2[1] + qz * magE2[2], qx * magE1[0] + qy * magE1[1] + qz * magE1[2]);
                // das Oval wandert und franst aus; Vorhänge als langsam treibendes Rauschen entlang des Ovals (Tabelle je Bild)
                double at = (lonM + Math.PI) / (2 * Math.PI) * AUR_N;
                int ai = at <= 0 ? 0 : at >= AUR_N ? AUR_N - 1 : (int) at;
                double af = at - ai;
                double center = aurCenter[ai] + (aurCenter[ai + 1] - aurCenter[ai]) * af;
                if (exact) {
                    double cl0 = Math.cos(lonM), sl0 = Math.sin(lonM);
                    center = AURORA_COLAT + 0.035 * Math.sin(lonM * 2 + 0.7)
                            + 0.025 * (com.dan.uranus.fx.Noise.value(cl0 * 1.8 + 5, sl0 * 1.8, auroraTime * 0.05) - 0.5);
                }
                double e = (colat - center) / 0.028, halo = (colat - center - 0.02) / 0.085;
                if (halo * halo < 16) {
                    double curtain = exact ? com.dan.uranus.fx.Noise.fbm(Math.cos(lonM) * 3.2, Math.sin(lonM) * 3.2, auroraTime * 0.12, 3)
                            : aurCurtain[ai] + (aurCurtain[ai + 1] - aurCurtain[ai]) * af;
                    double rays = 0.75 + 0.25 * Math.sin(lonM * 38 + auroraTime * 0.9 + 3 * curtain);
                    curtain = Math.max(0, curtain - 0.28) / 0.72;
                    oval = (Math.exp(-e * e) * rays + 0.35 * Math.exp(-halo * halo)) * (0.2 + 1.3 * curtain);
                    double f = (colat - center - 0.045) / 0.02;
                    fringe = Math.exp(-f * f) * (1 - blur);
                }
            }
            // bei schneller Drehung verschmiert das Oval zu einem Band um die Drehachse
            double smear = 0;
            if (blur > 0.001) {
                double d = (Math.acos(Math.min(1, Math.abs(ny))) - MagnetoLayer.TILT) / 0.42;
                smear = 0.28 * Math.exp(-d * d);
            }
            double a = (oval * (1 - blur) + smear * blur) * aurora * (0.22 + 0.78 * (1 - Math.min(1, lit * 1.4)));
            r += a * 0.80 + a * fringe * 0.10;
            g += a * 0.26 + a * fringe * 0.55;
            b += a * 1.05 + a * fringe * 0.40;
        }
        out[0] = (r + hz * 0.50 + 0.008) * intensity;
        out[1] = (g + hz * 0.85 + 0.017) * intensity;
        out[2] = (b + hz * 0.95 + 0.027) * intensity;
    }

    private static double clamp01(double v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    private static double smooth(double e0, double e1, double x) {
        double t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }
}
