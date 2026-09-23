package com.dan.uranus.play;

import com.dan.uranus.model.UranusSystem;
import com.dan.uranus.physics.Ephemeris;

/**
 * Kräfte der Spielwiese (Welt-Koordinaten, km, s):
 * <ul>
 *   <li>jeder Körper mit Masse zieht an jedem — Uranus ist Körper 0 und bewegt sich mit;</li>
 *   <li>Uranus ist abgeplattet: J2 und J4 um seine Achse (Welt-y), mit Rückwirkung auf Uranus;</li>
 *   <li>die Sonne stört als Gezeitenkraft, also als Unterschied ihrer Anziehung am Körper und an Uranus.</li>
 * </ul>
 * Testkörper (GM 0) werden angezogen, ziehen aber nicht.
 */
final class PlayForces implements Ias15.Forces {

    static final double GM_SUN = 1.32712440018e11;
    static final double AU_KM = 1.495978707e8;

    final double[] gm;
    final boolean sun;
    /** Startzeit der Spielzeit (JD) — für den Sonnenstand. */
    final double startJd;
    private final double[] sunKm = new double[3];
    private double sunT = Double.NaN;

    PlayForces(double[] gm, boolean sun, double startJd) { this.gm = gm; this.sun = sun; this.startJd = startJd; }

    /** Richtung und Abstand der Sonne von Uranus (km, Welt) zur Spielzeit t; ändert sich langsam, alle 6 h neu. */
    double[] sunAt(double t) {
        if (Double.isNaN(sunT) || Math.abs(t - sunT) > 21_600) {
            double jd = startJd + t / 86_400.0;
            double[] d = Ephemeris.sunDirWorld(jd);
            double dist = Ephemeris.sunDistanceAu(jd) * AU_KM;
            sunKm[0] = d[0] * dist; sunKm[1] = d[1] * dist; sunKm[2] = d[2] * dist;
            sunT = t;
        }
        return sunKm;
    }

    @Override
    public void acc(double t, double[] x, double[] a) {
        int n = gm.length;
        java.util.Arrays.fill(a, 0, 3 * n, 0);
        for (int i = 0; i < n; i++) {
            double xi = x[3 * i], yi = x[3 * i + 1], zi = x[3 * i + 2], ax = 0, ay = 0, az = 0;
            for (int j = 0; j < n; j++) {
                if (j == i || gm[j] == 0) continue;
                double dx = x[3 * j] - xi, dy = x[3 * j + 1] - yi, dz = x[3 * j + 2] - zi;
                double d2 = dx * dx + dy * dy + dz * dz, k = gm[j] / (d2 * Math.sqrt(d2));
                ax += dx * k; ay += dy * k; az += dz * k;
            }
            a[3 * i] += ax; a[3 * i + 1] += ay; a[3 * i + 2] += az;
        }
        // Abplattung
        double gu = gm[0], rr = UranusSystem.URANUS_RADIUS_KM, r2u = rr * rr, r4u = r2u * r2u;
        double[] s = sun ? sunAt(t) : null;
        double s3 = 0;
        if (s != null) { double sn = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]); s3 = GM_SUN / (sn * sn * sn); }
        for (int i = 1; i < n; i++) {
            double rx = x[3 * i] - x[0], ry = x[3 * i + 1] - x[1], rz = x[3 * i + 2] - x[2];
            double r2 = rx * rx + ry * ry + rz * rz, r = Math.sqrt(r2), u2 = ry * ry / r2;
            double r5 = r2 * r2 * r, r7 = r5 * r2;
            double k2 = gu * UranusSystem.J2 * r2u / r5, k4 = gu * UranusSystem.J4 * r4u / r7;
            double fxz = k2 * 1.5 * (5 * u2 - 1) + k4 * 15.0 / 8.0 * (21 * u2 * u2 - 14 * u2 + 1);
            double fy = k2 * 1.5 * (5 * u2 - 3) + k4 * 5.0 / 8.0 * (63 * u2 * u2 - 70 * u2 + 15);
            double ex = fxz * rx, ey = fy * ry, ez = fxz * rz;
            a[3 * i] += ex; a[3 * i + 1] += ey; a[3 * i + 2] += ez;
            if (gm[i] > 0) { double w = gm[i] / gu; a[0] -= w * ex; a[1] -= w * ey; a[2] -= w * ez; }
            if (s != null) {
                double dx = s[0] - rx, dy = s[1] - ry, dz = s[2] - rz, d2 = dx * dx + dy * dy + dz * dz;
                double kd = GM_SUN / (d2 * Math.sqrt(d2));
                a[3 * i] += dx * kd - s[0] * s3; a[3 * i + 1] += dy * kd - s[1] * s3; a[3 * i + 2] += dz * kd - s[2] * s3;
            }
        }
    }

    /** Zusätzliches Potential der Abplattung je Masseneinheit (km²/s²) an der Stelle rel (relativ zu Uranus). */
    double oblatePotential(double rx, double ry, double rz) {
        double r2 = rx * rx + ry * ry + rz * rz, r = Math.sqrt(r2), u2 = ry * ry / r2, rr = UranusSystem.URANUS_RADIUS_KM;
        double p2 = (3 * u2 - 1) / 2, p4 = (35 * u2 * u2 - 30 * u2 + 3) / 8;
        return gm[0] * (UranusSystem.J2 * rr * rr * p2 / (r2 * r) + UranusSystem.J4 * Math.pow(rr, 4) * p4 / (r2 * r2 * r));
    }
}
