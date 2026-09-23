package com.dan.uranus.model;

import com.dan.uranus.physics.Ephemeris;
import com.dan.uranus.physics.Kepler;

/**
 * Bahnelemente eines Mondes als präzedierende Ellipse (mittlere Elemente nach JPL).
 * <p>
 * Winkel in Grad, große Halbachse in km, Epoche als Julianisches Datum in TDB.
 * Die Bezugsebene ist je nach {@link Frame} verschieden; der Knoten wird immer ab dem
 * aufsteigenden Knoten der Bezugsebene auf dem ICRF-Äquator gezählt (JPL-Legende).
 * <ul>
 *   <li>{@link Frame#EQUATORIAL}: Uranusäquator. Der Bahnpol zeigt in Gegenrichtung zum
 *       IAU-Nordpol, weil die Monde mit dem Planeten rückläufig umlaufen (Neigungen nahe 0°).</li>
 *   <li>{@link Frame#LAPLACE}: lokale Laplace-Ebene mit eigenem Pol (RA/Dec, ICRF).</li>
 *   <li>{@link Frame#ECLIPTIC}: Ekliptik J2000 — so werden die irregulären Monde angegeben.</li>
 * </ul>
 * Die Präzession ist in Umlaufzeiten angegeben (Jahre, 0 = keine): das Perizentrum läuft vorwärts,
 * der Knoten läuft bei Neigung unter 90° rückwärts, darüber vorwärts. {@link #periodDays} ist die
 * siderische Periode, also die Periode der mittleren Länge. Beides ist gegen Horizons geprüft.
 * <p>
 * Die Bahn wird bei jedem Bild aus Zeit und Elementen neu berechnet und nie integriert.
 */
public final class OrbitElements {

    public enum Frame { EQUATORIAL, LAPLACE, ECLIPTIC }

    public final double aKm, e, iDeg, nodeDeg, argPeriDeg, meanAnomalyDeg, periodDays;
    public final Frame frame;
    /** Epoche (JD, TDB). */
    public final double epochJd;
    /** Präzessionsperioden in Jahren; 0 = keine. */
    public final double apsisPeriodYr, nodePeriodYr;
    /** Pol der Laplace-Ebene (ICRF, Grad); NaN bei den anderen Bezugsebenen. */
    public final double poleRaDeg, poleDecDeg;

    /** Basis der Bezugsebene in Weltkoordinaten: X (Knotenrichtung), Y, Z (Pol). */
    private final double[] bx, by, bz;
    /** Raten in Grad je Tag. */
    private final double wDot, nodeDot, mDot;

    public OrbitElements(Frame frame, double epochJd, double aKm, double e, double iDeg, double nodeDeg,
                         double argPeriDeg, double meanAnomalyDeg, double periodDays,
                         double apsisPeriodYr, double nodePeriodYr, double poleRaDeg, double poleDecDeg) {
        if (aKm <= 0) throw new IllegalArgumentException("große Halbachse muss positiv sein");
        if (e < 0 || e >= 1) throw new IllegalArgumentException("Exzentrizität muss in [0,1) liegen");
        if (!(periodDays > 0)) throw new IllegalArgumentException("Umlaufzeit muss positiv sein");
        if (frame == Frame.LAPLACE && (Double.isNaN(poleRaDeg) || Double.isNaN(poleDecDeg)))
            throw new IllegalArgumentException("Laplace-Ebene braucht einen Pol");
        this.frame = frame;
        this.epochJd = epochJd;
        this.aKm = aKm;
        this.e = e;
        this.iDeg = iDeg;
        this.nodeDeg = nodeDeg;
        this.argPeriDeg = argPeriDeg;
        this.meanAnomalyDeg = meanAnomalyDeg;
        this.periodDays = periodDays;
        this.apsisPeriodYr = apsisPeriodYr > 0 ? apsisPeriodYr : 0;
        this.nodePeriodYr = nodePeriodYr > 0 ? nodePeriodYr : 0;
        this.poleRaDeg = frame == Frame.LAPLACE ? poleRaDeg : Double.NaN;
        this.poleDecDeg = frame == Frame.LAPLACE ? poleDecDeg : Double.NaN;

        wDot = this.apsisPeriodYr > 0 ? 360.0 / (this.apsisPeriodYr * 365.25) : 0;
        double nd = this.nodePeriodYr > 0 ? 360.0 / (this.nodePeriodYr * 365.25) : 0;
        nodeDot = iDeg > 90 ? nd : -nd;
        mDot = 360.0 / periodDays - wDot - nodeDot;

        double[][] b = basisIcrf(frame, poleRaDeg, poleDecDeg);
        bx = new double[3]; by = new double[3]; bz = new double[3];
        Ephemeris.icrfToWorld(b[0], bx);
        Ephemeris.icrfToWorld(b[1], by);
        Ephemeris.icrfToWorld(b[2], bz);
    }

    /** Basis der Bezugsebene im ICRF: X = aufsteigender Knoten auf dem ICRF-Äquator, Z = Pol. */
    public static double[][] basisIcrf(Frame frame, double poleRaDeg, double poleDecDeg) {
        if (frame == Frame.ECLIPTIC) {
            double[] x = new double[3], y = new double[3], z = new double[3];
            Ephemeris.eclipticToIcrf(1, 0, 0, x);
            Ephemeris.eclipticToIcrf(0, 1, 0, y);
            Ephemeris.eclipticToIcrf(0, 0, 1, z);
            return new double[][]{x, y, z};
        }
        double[] q = frame == Frame.LAPLACE ? Ephemeris.radec(poleRaDeg, poleDecDeg)
                : new double[]{-Ephemeris.POLE_ICRF[0], -Ephemeris.POLE_ICRF[1], -Ephemeris.POLE_ICRF[2]};
        double[] x = Ephemeris.normalize(Ephemeris.cross(new double[]{0, 0, 1}, q));
        double[] y = Ephemeris.cross(q, x);
        return new double[][]{x, y, q};
    }

    /** Dieselbe Bahn mit anderer Phase und Periode (für den Angleich an Horizons). */
    public OrbitElements withPhase(double meanAnomalyDeg, double periodDays, boolean keepPrecession) {
        return new OrbitElements(frame, epochJd, aKm, e, iDeg, nodeDeg, argPeriDeg, meanAnomalyDeg, periodDays,
                keepPrecession ? apsisPeriodYr : 0, keepPrecession ? nodePeriodYr : 0, poleRaDeg, poleDecDeg);
    }

    /** Rückläufig bezogen auf die Bezugsebene (Neigung über 90°). */
    public boolean isRetrograde() { return iDeg > 90; }

    /** Mittlere Bewegung (Länge) in Grad je Tag. */
    public double meanMotionDegPerDay() { return 360.0 / periodDays; }

    public double meanAnomalyRate() { return mDot; }
    public double argPeriRate() { return wDot; }
    public double nodeRate() { return nodeDot; }

    /** Weltlage (km, relativ zum Uranusmittelpunkt) zum Zeitpunkt jdTdb. */
    public void position(double jdTdb, double[] out) {
        double dt = jdTdb - epochJd;
        double m = Math.toRadians(meanAnomalyDeg + mDot * dt);
        double w = Math.toRadians(argPeriDeg + wDot * dt), om = Math.toRadians(nodeDeg + nodeDot * dt);
        double inc = Math.toRadians(iDeg);
        double ea = Kepler.solveE(m, e);
        double xp = aKm * (Math.cos(ea) - e);
        double yp = aKm * Math.sqrt(1 - e * e) * Math.sin(ea);
        double cw = Math.cos(w), sw = Math.sin(w), co = Math.cos(om), so = Math.sin(om), ci = Math.cos(inc), si = Math.sin(inc);
        double x = (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp;
        double y = (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp;
        double z = (sw * si) * xp + (cw * si) * yp;
        out[0] = x * bx[0] + y * by[0] + z * bz[0];
        out[1] = x * bx[1] + y * by[1] + z * bz[1];
        out[2] = x * bx[2] + y * by[2] + z * bz[2];
    }
}
