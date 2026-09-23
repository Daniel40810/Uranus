package com.dan.uranus.model;

import com.dan.uranus.physics.Ephemeris;

/**
 * Eine Raumsonde auf einer Hyperbel um Uranus (Vorbeiflug).
 * <p>
 * Elemente bezogen auf den ICRF-Äquator: Periapsiszeit (JD, TDB), Periapsisabstand q, Exzentrizität
 * e &gt; 1, Neigung, Knoten, Argument der Periapsis. Die Hyperbel ist aus Horizons-Vektoren abgeleitet
 * (tools/UraStoryGen); bis 1989 ist die Voyager-Bahn bei JPL selbst ein Missionsentwurf aus
 * Kegelschnitten — um Uranus also genau eine Hyperbel, aber mit grober Genauigkeit.
 */
public final class Craft {

    public final String name;
    public final int naifId;
    public final double tpJd, qKm, e, iDeg, nodeDeg, argpDeg, gm;
    /** Zeitraum, für den die Hyperbel gilt (JD, TDB). */
    public final double validFromJd, validToJd;
    /** Größte Abweichung von den Horizons-Vektoren im gültigen Zeitraum (km). */
    public final double fitMaxKm;
    public final String source;

    private final double[] pw = new double[3], qw = new double[3];
    private final double p, aAbs, n;

    public Craft(String name, int naifId, double tpJd, double qKm, double e, double iDeg, double nodeDeg, double argpDeg,
                 double gm, double validFromJd, double validToJd, double fitMaxKm, String source) {
        if (!(e > 1)) throw new IllegalArgumentException("Vorbeiflug braucht e > 1");
        this.name = name; this.naifId = naifId; this.tpJd = tpJd; this.qKm = qKm; this.e = e; this.iDeg = iDeg;
        this.nodeDeg = nodeDeg; this.argpDeg = argpDeg; this.gm = gm; this.validFromJd = validFromJd; this.validToJd = validToJd;
        this.fitMaxKm = fitMaxKm; this.source = source;
        p = qKm * (1 + e);
        aAbs = qKm / (e - 1);
        n = Math.sqrt(gm / (aAbs * aAbs * aAbs));                       // rad/s
        double o = Math.toRadians(nodeDeg), i = Math.toRadians(iDeg), w = Math.toRadians(argpDeg);
        double co = Math.cos(o), so = Math.sin(o), ci = Math.cos(i), si = Math.sin(i), cw = Math.cos(w), sw = Math.sin(w);
        double[] pi = {co * cw - so * sw * ci, so * cw + co * sw * ci, sw * si};
        double[] qi = {-co * sw - so * cw * ci, -so * sw + co * cw * ci, cw * si};
        Ephemeris.icrfToWorld(pi, pw);
        Ephemeris.icrfToWorld(qi, qw);
    }

    public boolean covers(double jdTdb) { return jdTdb >= validFromJd && jdTdb <= validToJd; }

    /** Weltlage (km, Uranusmitte) zum Zeitpunkt jdTdb. */
    public void position(double jdTdb, double[] out) {
        double h = anomaly(jdTdb);
        double nu = 2 * Math.atan(Math.sqrt((e + 1) / (e - 1)) * Math.tanh(h / 2));
        double r = p / (1 + e * Math.cos(nu));
        double x = r * Math.cos(nu), y = r * Math.sin(nu);
        for (int k = 0; k < 3; k++) out[k] = x * pw[k] + y * qw[k];
    }

    /** Weltlage bei der hyperbolischen Anomalie h (für gleichmäßig verteilte Bahnpunkte). */
    public void positionAtAnomaly(double h, double[] out) {
        double nu = 2 * Math.atan(Math.sqrt((e + 1) / (e - 1)) * Math.tanh(h / 2));
        double r = p / (1 + e * Math.cos(nu));
        double x = r * Math.cos(nu), y = r * Math.sin(nu);
        for (int k = 0; k < 3; k++) out[k] = x * pw[k] + y * qw[k];
    }

    /** Hyperbolische Anomalie aus M = e·sinh H − H (Newton). */
    public double anomaly(double jdTdb) {
        double m = n * (jdTdb - tpJd) * 86_400.0;
        double x = m / e, h = Math.log(x + Math.sqrt(x * x + 1));          // asinh(M/e) als Start
        for (int k = 0; k < 60; k++) {
            double d = (e * Math.sinh(h) - h - m) / (e * Math.cosh(h) - 1);
            h -= d;
            if (Math.abs(d) < 1e-14) break;
        }
        return h;
    }

    /** Abstand vom Uranusmittelpunkt (km). */
    public double distance(double jdTdb) {
        return aAbs * (e * Math.cosh(anomaly(jdTdb)) - 1);
    }

    /** Geschwindigkeit relativ zu Uranus (km/s), Vis-viva. */
    public double speed(double jdTdb) {
        double r = distance(jdTdb);
        return Math.sqrt(gm * (2 / r + 1 / aAbs));
    }
}
