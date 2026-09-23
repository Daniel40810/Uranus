package com.dan.uranus.physics;

/**
 * Zeitskalen: die Uhr der App läuft in UTC, die Bahnelemente und Horizons in TDB.
 * <p>
 * TDB − UTC = 32,184 s + Schaltsekunden (TDB − TT bleibt unter 2 ms und wird vernachlässigt).
 * Der Unterschied ist nicht klein: 69 s entsprechen bei Cordelia fast einem Grad Umlauf.
 * Nach der letzten Schaltsekunde (2017) wird der Wert fortgeschrieben; vor 1972 gilt der
 * Wert von 1972 als Näherung.
 */
public final class TimeScale {

    private TimeScale() {}

    /** Beginn jeder Schaltsekunde als JD (UTC), 1972–2017; dazu TAI − UTC ab diesem Tag. */
    private static final double[] LEAP_JD = {
            2441317.5, 2441499.5, 2441683.5, 2442048.5, 2442413.5, 2442778.5, 2443144.5, 2443509.5,
            2443874.5, 2444239.5, 2444786.5, 2445151.5, 2445516.5, 2446247.5, 2447161.5, 2447892.5,
            2448257.5, 2448804.5, 2449169.5, 2449534.5, 2450083.5, 2450630.5, 2451179.5, 2453736.5,
            2454832.5, 2456109.5, 2457204.5, 2457754.5};
    private static final int[] TAI_UTC = {
            10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33,
            34, 35, 36, 37};

    /** TDB − UTC in Sekunden zum Zeitpunkt jdUtc. */
    public static double tdbMinusUtcSeconds(double jdUtc) {
        int n = 0;
        for (int i = 0; i < LEAP_JD.length; i++) if (jdUtc >= LEAP_JD[i]) n = TAI_UTC[i];
        if (n == 0) n = TAI_UTC[0];
        return 32.184 + n;
    }

    /** UTC → TDB (beides als Julianisches Datum). */
    public static double tdb(double jdUtc) { return jdUtc + tdbMinusUtcSeconds(jdUtc) / 86_400.0; }
}
