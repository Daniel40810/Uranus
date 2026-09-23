package com.dan.uranus.model;

/**
 * Ein Ring des Uranus als schmale, präzedierende Ellipse.
 * <p>
 * Messwerte (Tabelle „Rings of Uranus“ nach Esposito 2002, Karkoschka 2001, de Pater 2006/2013,
 * French 1988): große Halbachse, kleinste und größte Breite, Exzentrizität, Neigung, optische Tiefe.
 * Bei exzentrischen Ringen ist die Breite am Perizentrum am kleinsten und wächst linear mit dem
 * Abstand — beim ε-Ring von 19,7 auf 96,4 km.
 * <p>
 * Das Perizentrum dreht sich durch die Abplattung (J2) vorwärts, ε-Ring 1,35°/Tag. Seine Lage zur
 * Epoche ist nicht bekannt und auf 0° gesetzt (Darstellung).
 * Deckkraft und Farbe sind Darstellungswerte und stehen nur im Code (die echten Ringe sind sehr
 * dunkel, Albedo ~2 %).
 */
public final class Ring {

    public final String name;
    public final double aKm, widthMinKm, widthMaxKm, ecc, inclDeg, tauMin, tauMax;
    public final double radiusRu;
    /** Mittlere Breite in Uranusradien. */
    public final double widthRu;
    public final float opacity;
    public final float red, green, blue;
    /** Staubring: breit, schwach, leuchtet im Gegenlicht auf (ζ, ν, μ). */
    public final boolean dusty;
    /** Drehung des Perizentrums in Grad je Tag. */
    public final double apsisRate;
    /** Entdeckung (0 = unbekannt). */
    public final int discoveredYear;
    public final String discoveredBy;

    public Ring(String name, double aKm, double widthMinKm, double widthMaxKm, double ecc, double inclDeg,
                double tauMin, double tauMax, boolean dusty, float opacity, float red, float green, float blue) {
        this(name, aKm, widthMinKm, widthMaxKm, ecc, inclDeg, tauMin, tauMax, dusty, opacity, red, green, blue, 0, "");
    }

    public Ring(String name, double aKm, double widthMinKm, double widthMaxKm, double ecc, double inclDeg,
                double tauMin, double tauMax, boolean dusty, float opacity, float red, float green, float blue,
                int discoveredYear, String discoveredBy) {
        this.discoveredYear = discoveredYear;
        this.discoveredBy = discoveredBy == null ? "" : discoveredBy;
        this.name = name;
        this.aKm = aKm;
        this.widthMinKm = widthMinKm;
        this.widthMaxKm = widthMaxKm;
        this.ecc = ecc;
        this.inclDeg = inclDeg;
        this.tauMin = tauMin;
        this.tauMax = tauMax;
        this.dusty = dusty;
        this.opacity = opacity;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.radiusRu = aKm / UranusSystem.URANUS_RADIUS_KM;
        this.widthRu = (widthMinKm + widthMaxKm) * 0.5 / UranusSystem.URANUS_RADIUS_KM;
        this.apsisRate = apsisRate(aKm);
    }

    /** Perizentrumsdrehung durch J2: dϖ/dt = 3/2 · n · J2 · (R/a)² (Grad je Tag). */
    public static double apsisRate(double aKm) {
        double n = 360.0 / UranusSystem.periodFromA(aKm);
        double q = UranusSystem.URANUS_RADIUS_KM / aKm;
        return 1.5 * n * UranusSystem.J2 * q * q;
    }

    /** Perizentrumslänge (Weltwinkel atan2(z, x), Bogenmaß) zum Zeitpunkt. */
    public double periapsis(double jdTdb) { return Math.toRadians(apsisRate * (jdTdb - SimClock.JD_J2000)); }

    /** Abstand der Ringmitte in Uranusradien beim Weltwinkel theta. */
    public double radiusRuAt(double theta, double periapsis) {
        if (ecc == 0) return radiusRu;
        return radiusRu * (1 - ecc * ecc) / (1 + ecc * Math.cos(theta - periapsis));
    }

    /** Breite in Uranusradien beim Weltwinkel theta: am Perizentrum schmal, am Apozentrum breit. */
    public double widthRuAt(double theta, double periapsis) {
        if (ecc == 0) return widthRu;
        double f = (1 - Math.cos(theta - periapsis)) * 0.5;               // 0 am Perizentrum, 1 am Apozentrum
        return (widthMinKm + (widthMaxKm - widthMinKm) * f) / UranusSystem.URANUS_RADIUS_KM;
    }

    @Override
    public String toString() { return name; }
}
