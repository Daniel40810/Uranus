package com.dan.uranus.render;

import com.dan.uranus.physics.SpaceMap;

import java.util.Random;

/**
 * Die Magnetosphäre des Uranus — die schrägste im Sonnensystem.
 * <ul>
 *   <li>Dipol um 59° gegen die Drehachse gekippt und um 0,3 R zum Südpol (IAU) versetzt,
 *       dreht mit dem Planeten (17 h 14 min).</li>
 *   <li>Geschlossene Feldlinien r = L·sin²θ, tagseitig vom Sonnenwind gestaucht, nachtseitig
 *       zum Schweif gestreckt; darauf pendeln gefangene Plasmateilchen zwischen den Spiegelpunkten.</li>
 *   <li>Der Schweif windet sich wie ein Korkenzieher, weil sich der schräge Dipol mitdreht.</li>
 *   <li>Bugstoßwelle (~23 R) und Sonnenwind, der um die Magnetosphäre herumströmt.</li>
 * </ul>
 * Alle Abstände in Uranusradien, auf Weltabstände über {@link SpaceMap} (gestaucht/echt).
 * Bei schnellem Zeitraffer werden die Feldlinien über die Drehung eines Bildes gemittelt
 * (Bewegungsunschärfe), statt zu flackern.
 */
final class MagnetoLayer {

    static final double TILT = Math.toRadians(59.0);
    /** Versatz des Dipols in Uranusradien (entlang −y, also zum IAU-Südpol). */
    static final double OFFSET = 0.3;
    /** Längengrad des Dipols im Körpersystem (Darstellungswert). */
    static final double LON0 = 0.8;
    static final double BOW_NOSE = 23.0;

    private static final double[] SHELLS = {2.2, 3.2, 4.6, 6.5, 9.0};
    private static final int AZIMUTHS = 10, STEPS = 72;

    // Schweif-Teilchen (fest erzeugt, Bewegung aus der Zeit)
    private static final int TAIL = 2200;
    private final double[] tailU = new double[TAIL], tailJa = new double[TAIL], tailJr = new double[TAIL], tailB = new double[TAIL];
    // Sonnenwind (mit Zustand)
    private static final int WIND = 150;
    private final double[] windX = new double[WIND], windR = new double[WIND], windA = new double[WIND];
    private final Random rnd = new Random(1986);

    MagnetoLayer() {
        for (int i = 0; i < TAIL; i++) { tailU[i] = rnd.nextDouble(); tailJa[i] = rnd.nextGaussian(); tailJr[i] = rnd.nextGaussian(); tailB[i] = rnd.nextDouble(); }
        for (int i = 0; i < WIND; i++) respawnWind(i, true);
    }

    // ------------------------------------------------------------------ Geometrie (rein, prüfbar)

    /** Magnetachse m und Basis e1, e2 in der Welt zum Drehwinkel spin. */
    static void axis(double spin, double[] m, double[] e1, double[] e2) {
        double a = spin + LON0, st = Math.sin(TILT), ct = Math.cos(TILT);
        m[0] = st * Math.cos(a); m[1] = ct; m[2] = st * Math.sin(a);
        e1[0] = ct * Math.cos(a); e1[1] = -st; e1[2] = ct * Math.sin(a);
        e2[0] = m[1] * e1[2] - m[2] * e1[1];
        e2[1] = m[2] * e1[0] - m[0] * e1[2];
        e2[2] = m[0] * e1[1] - m[1] * e1[0];
    }

    /** Punkt der Feldlinie (Schale L, Azimut phi, Poldistanz theta) in Uranusradien, relativ zum Planetenmittelpunkt, ohne Sonnenwind-Verformung. */
    static void dipolePoint(double L, double phi, double theta, double[] m, double[] e1, double[] e2, double[] out) {
        double r = L * Math.sin(theta) * Math.sin(theta);
        double sx = r * Math.sin(theta) * Math.cos(phi), sy = r * Math.cos(theta), sz = r * Math.sin(theta) * Math.sin(phi);
        for (int k = 0; k < 3; k++) out[k] = sx * e1[k] + sy * m[k] + sz * e2[k];
        out[1] -= OFFSET;
    }

    /** Fußpunkt-Poldistanz einer Schale auf der Oberfläche (ohne Versatz): sin²θ = 1/L. */
    static double footColatitude(double L) { return Math.asin(Math.sqrt(1.0 / L)); }

    /** Sonnenwind: tagseitig stauchen, nachtseitig strecken (p in Uranusradien). */
    private static void squeeze(double[] p, double[] s) {
        double d = p[0] * s[0] + p[1] * s[1] + p[2] * s[2];
        double r = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        double k = d < 0 ? d * 0.9 * (r / 12.0) : d * 0.14;     // nachts: weiter weg = stärker gestreckt
        p[0] -= s[0] * k; p[1] -= s[1] * k; p[2] -= s[2] * k;
    }

    /** Uranusradien (relativ) → Welt. false, wenn im Planeten. */
    private static boolean toWorld(double[] p, double scale, double ucY, double[] w) {
        double r = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        if (r < 1.02) return false;
        double f = SpaceMap.map(r, scale) / r;
        w[0] = p[0] * f; w[1] = ucY + p[1] * f; w[2] = p[2] * f;
        return true;
    }

    // ------------------------------------------------------------------ Zeichnen

    /**
     * @param spin     Drehwinkel des Planeten
     * @param spinStep Drehung je Bild (für die Bewegungsunschärfe)
     * @param alpha    Einblendung 0..1
     */
    void build(Scene3D sc, double spin, double spinStep, double alpha, double dt) {
        if (alpha < 0.01) return;
        double[] s = sc.sun();
        double scale = sc.scaleT(), ucY = sc.ucY(), t = sc.time();
        double[] m = new double[3], e1 = new double[3], e2 = new double[3], p = new double[3], w = new double[3], q = new double[3];
        int samples = (int) Math.max(1, Math.min(6, Math.ceil(Math.abs(spinStep) / 0.25)));
        // 1) Feldlinien (bei schnellem Zeitraffer über die Drehung eines Bildes gemittelt)
        for (int smp = 0; smp < samples; smp++) {
            double sp = spin - spinStep * smp / samples;
            axis(sp, m, e1, e2);
            for (int li = 0; li < SHELLS.length; li++) {
                double L = SHELLS[li];
                float a = (float) ((0.28 - li * 0.042) * alpha / samples);
                double th0 = Math.asin(Math.sqrt(Math.min(1, 0.55 / L)));
                for (int ai = 0; ai < AZIMUTHS; ai++) {
                    double phi = ai * Math.PI * 2 / AZIMUTHS + li * 0.31;
                    boolean have = false;
                    for (int k = 0; k <= STEPS; k++) {
                        double th = th0 + (Math.PI - 2 * th0) * k / STEPS;
                        dipolePoint(L, phi, th, m, e1, e2, p);
                        squeeze(p, s);
                        if (!toWorld(p, scale, ucY, w)) { have = false; continue; }
                        if (have) sc.seg(q[0], q[1], q[2], w[0], w[1], w[2], 0.60f, 0.46f, 1.0f, a, true);
                        q[0] = w[0]; q[1] = w[1]; q[2] = w[2]; have = true;
                    }
                }
            }
        }
        // 2) gefangene Plasmateilchen: pendeln zwischen den Spiegelpunkten, driften im Azimut
        axis(spin, m, e1, e2);
        for (int li = 0; li < SHELLS.length; li++) {
            double L = SHELLS[li], th0 = Math.asin(Math.sqrt(Math.min(1, 0.7 / L)));
            for (int ai = 0; ai < AZIMUTHS; ai++) {
                for (int k = 0; k < 3; k++) {
                    double u = frac(t * (0.10 + 0.03 * k) / Math.sqrt(L) + ai * 0.137 + k * 0.33 + li * 0.21);
                    double th = th0 + (Math.PI - 2 * th0) * (0.5 - 0.5 * Math.cos(2 * Math.PI * u));
                    double phi = ai * Math.PI * 2 / AZIMUTHS + li * 0.31 + t * 0.05 * (1 + k * 0.4);
                    dipolePoint(L, phi, th, m, e1, e2, p);
                    squeeze(p, s);
                    if (toWorld(p, scale, ucY, w)) sc.glow(w[0], w[1], w[2], (float) (0.9 * alpha), (float) (0.75 * alpha), (float) (1.25 * alpha), 1.9);
                }
            }
        }
        // 3) Korkenzieher-Schweif auf der Nachtseite
        double ax = -s[0], ay = -s[1], az = -s[2];
        double b1x = ay * 0 - az * 1, b1y = az * 0 - ax * 0, b1z = ax * 1 - ay * 0;     // a × y
        double bl = Math.sqrt(b1x * b1x + b1y * b1y + b1z * b1z);
        if (bl < 1e-6) { b1x = 1; b1y = 0; b1z = 0; bl = 1; }
        b1x /= bl; b1y /= bl; b1z /= bl;
        double b2x = ay * b1z - az * b1y, b2y = az * b1x - ax * b1z, b2z = ax * b1y - ay * b1x;
        double blur = Math.min(1, Math.abs(spinStep) / 1.5);
        for (int i = 0; i < TAIL; i++) {
            double u = frac(tailU[i] + t * 0.035);
            double d = 2.5 + 60 * Math.pow(u, 1.4);
            int lobe = i & 1;
            double psi = spin - d * 0.11 + lobe * Math.PI + tailJa[i] * (0.35 + blur * 2.5);
            double rho = 1.2 + 0.16 * d + tailJr[i] * 0.6;
            p[0] = ax * d + rho * (Math.cos(psi) * b1x + Math.sin(psi) * b2x);
            p[1] = ay * d + rho * (Math.cos(psi) * b1y + Math.sin(psi) * b2y);
            p[2] = az * d + rho * (Math.cos(psi) * b1z + Math.sin(psi) * b2z);
            if (!toWorld(p, scale, ucY, w)) continue;
            float b = (float) (0.32 * Math.pow(1 - u, 1.2) * (0.5 + tailB[i]) * alpha);
            sc.glow(w[0], w[1], w[2], 0.42f * b, 0.55f * b, 1.0f * b, 1.0);
        }
        // 4) Bugstoßwelle: Paraboloid vor der Tagseite
        float ba = (float) (0.05 * alpha);
        for (int ring = 0; ring < 6; ring++) {
            double x = BOW_NOSE - ring * 7;
            double rho = Math.sqrt(2 * BOW_NOSE * (BOW_NOSE - x));
            boolean have = false;
            for (int k = 0; k <= 72; k++) {
                double ang = k * Math.PI * 2 / 72;
                bowPoint(s, b1x, b1y, b1z, b2x, b2y, b2z, x, rho, ang, p);
                if (!toWorld(p, scale, ucY, w)) { have = false; continue; }
                if (have) sc.seg(q[0], q[1], q[2], w[0], w[1], w[2], 0.45f, 0.80f, 0.95f, ba, true);
                q[0] = w[0]; q[1] = w[1]; q[2] = w[2]; have = true;
            }
        }
        for (int mer = 0; mer < 12; mer++) {
            double ang = mer * Math.PI * 2 / 12;
            boolean have = false;
            for (int k = 0; k <= 40; k++) {
                double x = BOW_NOSE - k * 1.2, rho = Math.sqrt(2 * BOW_NOSE * (BOW_NOSE - x));
                bowPoint(s, b1x, b1y, b1z, b2x, b2y, b2z, x, rho, ang, p);
                if (!toWorld(p, scale, ucY, w)) { have = false; continue; }
                if (have) sc.seg(q[0], q[1], q[2], w[0], w[1], w[2], 0.45f, 0.80f, 0.95f, ba * 0.7f, true);
                q[0] = w[0]; q[1] = w[1]; q[2] = w[2]; have = true;
            }
        }
        // 5) Sonnenwind: strömt von der Sonne heran und weicht der Magnetosphäre aus
        for (int i = 0; i < WIND; i++) {
            windX[i] -= 24 * Math.min(dt, 0.1);
            double rb = windX[i] < BOW_NOSE ? Math.sqrt(2 * BOW_NOSE * (BOW_NOSE - windX[i])) + 1.0 : 0;
            if (windR[i] < rb) windR[i] += (rb - windR[i]) * Math.min(1, dt * 6);
            if (windX[i] < -35) respawnWind(i, false);
            bowPoint(s, b1x, b1y, b1z, b2x, b2y, b2z, windX[i], windR[i], windA[i], p);
            double[] p2 = {p[0] + s[0] * 1.6, p[1] + s[1] * 1.6, p[2] + s[2] * 1.6};
            // nur in der Nähe der Stoßwelle zeigen, wo das Ausweichen zu sehen ist
            double near = Math.exp(-Math.pow((Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]) - BOW_NOSE) / 16, 2));
            if (toWorld(p, scale, ucY, w) && toWorld(p2, scale, ucY, q))
                sc.seg(q[0], q[1], q[2], w[0], w[1], w[2], 1.0f, 0.90f, 0.70f, (float) (0.12 * near * alpha), true);
        }
    }

    private static void bowPoint(double[] s, double b1x, double b1y, double b1z, double b2x, double b2y, double b2z,
                                 double x, double rho, double ang, double[] out) {
        double c = Math.cos(ang), sn = Math.sin(ang);
        out[0] = s[0] * x + rho * (c * b1x + sn * b2x);
        out[1] = s[1] * x + rho * (c * b1y + sn * b2y);
        out[2] = s[2] * x + rho * (c * b1z + sn * b2z);
    }

    private void respawnWind(int i, boolean anywhere) {
        windX[i] = anywhere ? -35 + rnd.nextDouble() * 85 : 45 + rnd.nextDouble() * 8;
        windR[i] = rnd.nextDouble() * 38;
        windA[i] = rnd.nextDouble() * Math.PI * 2;
    }

    private static double frac(double v) { return v - Math.floor(v); }
}
