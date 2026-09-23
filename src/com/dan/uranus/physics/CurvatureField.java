package com.dan.uranus.physics;

/**
 * Das Krümmungsfeld: Mulde (Höhenfläche) und Gitterraum (Verschiebung zu den Massen).
 * <p>
 * Mulde wie in FCurvedField: y(x,z) = −Σ dᵢ·(1 + rᵢ²/wᵢ²)^−1,5. Die Tiefe dᵢ folgt der Masse,
 * die Breite wᵢ der Größe. Dazu laufen gedämpfte Wellen über die Fläche.
 * <p>
 * Wird je Bild vom Render-Thread neu befüllt ({@link #clear()}, {@link #addWell}); nicht
 * für mehrere Threads gleichzeitig gedacht — die Leseaufrufe sind aber reine Funktionen und
 * dürfen parallel laufen, solange niemand schreibt.
 */
public final class CurvatureField {

    public static final int MAX = 64;
    /** Uranus: Tiefe und Breite der Hauptmulde in Welteinheiten. */
    public static final double URANUS_DEPTH = 2.6, URANUS_WIDTH = 4.0;

    private final double[] wx = new double[MAX], wy = new double[MAX], wz = new double[MAX];
    private final double[] wd = new double[MAX], winv2 = new double[MAX], wpull = new double[MAX];
    private int n;

    private final double[] rx = new double[8], rz = new double[8], rt = new double[8];
    private int rn;
    private double now;

    public void clear() { n = 0; }

    /**
     * Fügt eine Masse hinzu.
     * @param depth Muldentiefe
     * @param width Muldenbreite
     * @param pull  Stärke des Zugs im Gitterraum
     */
    public void addWell(double x, double y, double z, double depth, double width, double pull) {
        if (n >= MAX) return;
        wx[n] = x; wy[n] = y; wz[n] = z; wd[n] = depth; winv2[n] = 1.0 / (width * width); wpull[n] = pull;
        n++;
    }

    public int wellCount() { return n; }

    /** Löst eine Welle am Ort (x, z) aus. */
    public void addRipple(double x, double z, double time) {
        if (rn == rx.length) { System.arraycopy(rx, 1, rx, 0, rn - 1); System.arraycopy(rz, 1, rz, 0, rn - 1); System.arraycopy(rt, 1, rt, 0, rn - 1); rn--; }
        rx[rn] = x; rz[rn] = z; rt[rn] = time; rn++;
    }

    /** Setzt die Zeit (Sekunden) für die Wellen und räumt abgeklungene ab. */
    public void setTime(double seconds) {
        now = seconds;
        int k = 0;
        for (int i = 0; i < rn; i++) if (now - rt[i] < 7.0) { rx[k] = rx[i]; rz[k] = rz[i]; rt[k] = rt[i]; k++; }
        rn = k;
    }

    public int rippleCount() { return rn; }

    public double ripple(double x, double z) {
        double y = 0;
        for (int i = 0; i < rn; i++) {
            double a = now - rt[i], dx = x - rx[i], dz = z - rz[i];
            double e = (Math.sqrt(dx * dx + dz * dz) - 2.6 * a) / 1.15;
            if (e * e < 9) y += 0.34 * Math.exp(-e * e - a * 0.5) * Math.cos(e * 3.3);
        }
        return y;
    }

    /** Höhe der Mulde an (x, z). */
    public double sheetY(double x, double z) {
        double y = 0;
        for (int i = 0; i < n; i++) {
            double dx = x - wx[i], dz = z - wz[i];
            double q = 1.0 / (1.0 + (dx * dx + dz * dz) * winv2[i]);
            y -= wd[i] * q * Math.sqrt(q);
        }
        return rn > 0 ? y + ripple(x, z) : y;
    }

    /** Höhe der Mulde ohne Wellen (für Bahnlinien, die sonst mitzittern). */
    public double sheetYStatic(double x, double z) {
        double y = 0;
        for (int i = 0; i < n; i++) {
            double dx = x - wx[i], dz = z - wz[i];
            double q = 1.0 / (1.0 + (dx * dx + dz * dz) * winv2[i]);
            y -= wd[i] * q * Math.sqrt(q);
        }
        return y;
    }

    /** Verschiebt einen Gitterpunkt zu den Massen hin (Gitterraum). */
    public void displace(double x, double y, double z, double[] out) {
        double ox = 0, oy = 0, oz = 0;
        for (int i = 0; i < n; i++) {
            if (wpull[i] <= 0) continue;
            double vx = wx[i] - x, vy = wy[i] - y, vz = wz[i] - z;
            double r = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-9;
            double s = wpull[i] / (1 + 0.17 * r * r);
            if (s > 0.8 * r) s = 0.8 * r;
            ox += vx / r * s; oy += vy / r * s; oz += vz / r * s;
        }
        out[0] = x + ox;
        out[1] = y + oy + (rn > 0 ? 0.8 * ripple(x, z) : 0);
        out[2] = z + oz;
    }
}
