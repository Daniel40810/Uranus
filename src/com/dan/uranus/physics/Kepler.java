package com.dan.uranus.physics;

import com.dan.uranus.model.OrbitElements;

/**
 * Lage auf einer Kepler-Bahn, analytisch aus der Zeit.
 * <p>
 * Grundsatz (übernommen aus FCurvedField): die Bahn wird nie integriert, sondern zu jedem
 * Zeitpunkt aus den Elementen neu berechnet. Dadurch kann sie weder zerfallen noch driften.
 */
public final class Kepler {

    private Kepler() {}

    private static final double TWO_PI = Math.PI * 2;

    /** Löst M = E − e·sin E per Newton; M im Bogenmaß. */
    public static double solveE(double m, double e) {
        m = Math.IEEEremainder(m, TWO_PI);
        double ea = e < 0.8 ? m + e * Math.sin(m) : (m < 0 ? -Math.PI : Math.PI);
        for (int k = 0; k < 40; k++) {
            double f = ea - e * Math.sin(ea) - m;
            double d = f / (1 - e * Math.cos(ea));
            ea -= d;
            if (Math.abs(d) < 1e-14) break;
        }
        return ea;
    }

    /**
     * Lage des Mondes in Weltkoordinaten (km, relativ zum Uranusmittelpunkt).
     *
     * @param jdTdb Zeitpunkt als Julianisches Datum in TDB ({@link TimeScale#tdb})
     */
    public static void position(OrbitElements o, double jdTdb, double[] out) { o.position(jdTdb, out); }
}
