package com.dan.uranus.physics;

import com.dan.uranus.model.SimClock;

/**
 * Stellung von Uranus zur Sonne und Lage seiner Achse — daraus folgen Sonnenstand,
 * Jahreszeiten und Rotation zu jedem Datum.
 * <p>
 * <b>Weltkoordinaten</b> (so rechnet die ganze Szene):
 * <ul>
 *   <li>y = Nordpol nach IAU (RA 257,311°, Dec −15,175°). Uranus dreht sich rückläufig um +y,
 *       die Monde laufen im selben Sinn: von +y aus gesehen im Uhrzeigersinn, d. h. der Winkel
 *       atan2(z, x) wächst.</li>
 *   <li>x = Knoten des Uranusäquators auf dem ICRF-Äquator (IAU-Bezugspunkt Q).</li>
 *   <li>z = x × y (rechtshändig).</li>
 * </ul>
 * Uranusbahn: mittlere Elemente nach Standish (JPL, gültig 1800–2050, Ekliptik J2000).
 */
public final class Ephemeris {

    private Ephemeris() {}

    public static final double DEG = Math.PI / 180.0;
    /** Schiefe der Ekliptik J2000. */
    public static final double OBLIQUITY_ECLIPTIC = 23.43928 * DEG;

    /** IAU-Pol des Uranus (Nord nach IAU-Konvention) in ICRF. */
    public static final double[] POLE_ICRF = radec(257.311, -15.175);

    /** Weltbasis ausgedrückt in ICRF. */
    public static final double[] X_W, Y_W, Z_W;
    /** Galaktische Basis in ICRF (für Milchstraße und Sternverteilung). */
    public static final double[] GAL_X, GAL_Y, GAL_Z;

    static {
        Y_W = POLE_ICRF.clone();
        X_W = normalize(cross(new double[]{0, 0, 1}, Y_W));
        Z_W = cross(X_W, Y_W);
        GAL_Z = radec(192.85948, 27.12825);
        double[] gc = radec(266.40510, -28.93617);
        double k = dot(gc, GAL_Z);
        GAL_X = normalize(new double[]{gc[0] - k * GAL_Z[0], gc[1] - k * GAL_Z[1], gc[2] - k * GAL_Z[2]});
        GAL_Y = cross(GAL_Z, GAL_X);
    }

    // ------------------------------------------------------------------ Vektor-Hilfen

    public static double[] radec(double raDeg, double decDeg) {
        double a = raDeg * DEG, d = decDeg * DEG;
        return new double[]{Math.cos(d) * Math.cos(a), Math.cos(d) * Math.sin(a), Math.sin(d)};
    }

    public static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    public static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    public static double[] normalize(double[] v) {
        double l = Math.sqrt(dot(v, v));
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    /** ICRF → Welt. */
    public static void icrfToWorld(double[] v, double[] out) {
        double x = dot(v, X_W), y = dot(v, Y_W), z = dot(v, Z_W);
        out[0] = x; out[1] = y; out[2] = z;
    }

    /** Welt → ICRF. */
    public static void worldToIcrf(double x, double y, double z, double[] out) {
        out[0] = x * X_W[0] + y * Y_W[0] + z * Z_W[0];
        out[1] = x * X_W[1] + y * Y_W[1] + z * Z_W[1];
        out[2] = x * X_W[2] + y * Y_W[2] + z * Z_W[2];
    }

    /** Ekliptik J2000 → ICRF (Drehung um x). */
    public static void eclipticToIcrf(double x, double y, double z, double[] out) {
        double c = Math.cos(OBLIQUITY_ECLIPTIC), s = Math.sin(OBLIQUITY_ECLIPTIC);
        out[0] = x;
        out[1] = y * c - z * s;
        out[2] = y * s + z * c;
    }

    // ------------------------------------------------------------------ Uranusbahn

    /** Heliozentrische Lage von Uranus in AE, Ekliptik J2000. */
    public static double[] uranusHeliocentric(double jd) {
        double t = (jd - SimClock.JD_J2000) / 36_525.0;
        double a = 19.18916464 - 0.00196176 * t;
        double e = 0.04725744 - 0.00004397 * t;
        double inc = (0.77263783 - 0.00242939 * t) * DEG;
        double l = 313.23810451 + 428.48202785 * t;
        double peri = 170.95427630 + 0.40805281 * t;
        double node = 74.01692503 + 0.04240589 * t;
        double w = (peri - node) * DEG, om = node * DEG;
        double m = Math.toRadians(((l - peri) % 360.0 + 360.0) % 360.0);
        double ea = Kepler.solveE(m, e);
        double xp = a * (Math.cos(ea) - e), yp = a * Math.sqrt(1 - e * e) * Math.sin(ea);
        double cw = Math.cos(w), sw = Math.sin(w), co = Math.cos(om), so = Math.sin(om), ci = Math.cos(inc), si = Math.sin(inc);
        return new double[]{
                (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp,
                (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp,
                (sw * si) * xp + (cw * si) * yp};
    }

    /** Einheitsvektor von Uranus zur Sonne in Weltkoordinaten. */
    public static double[] sunDirWorld(double jd) {
        double[] h = uranusHeliocentric(jd);
        double[] eq = new double[3];
        eclipticToIcrf(-h[0], -h[1], -h[2], eq);
        double[] w = new double[3];
        icrfToWorld(normalize(eq), w);
        return w;
    }

    /** Abstand Uranus–Sonne in AE. */
    public static double sunDistanceAu(double jd) {
        double[] h = uranusHeliocentric(jd);
        return Math.sqrt(dot(h, h));
    }

    /** Breite des Sonnenunterpunkts in Grad (+ = IAU-Nordhalbkugel beschienen). */
    public static double subsolarLatitudeDeg(double jd) {
        return Math.asin(sunDirWorld(jd)[1]) / DEG;
    }

    /**
     * Drehwinkel des Nullmeridians in der Weltebene (Bogenmaß, wächst mit der Zeit).
     * IAU: W = 203,81° − 501,1600928°·d; der Nullmeridian liegt bei atan2(z, x) = −W.
     */
    public static double spinAngle(double jd) {
        double d = jd - SimClock.JD_J2000;
        double w = 203.81 - 501.1600928 * d;
        return Math.IEEEremainder(-w, 360.0) * DEG;
    }
}
