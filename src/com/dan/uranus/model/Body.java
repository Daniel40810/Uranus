package com.dan.uranus.model;

/**
 * Ein Mond des Uranus. Unveränderlich; die Lage zu einem Zeitpunkt rechnet
 * {@link OrbitElements#position} aus den Elementen.
 */
public final class Body {

    /** Oberflächen-Stil für den prozeduralen Shader. */
    public enum Surface { PATCHWORK, CANYONS, DARK_RING, CHASMA, CRATERED, SMALL_DARK, CAPTURED_RED }

    public final String name;
    /** JPL-Satellitennummer (NAIF-ID; bei den neuen Monden die vorläufige JPL-Nummer). */
    public final int naifId;
    public final BodyGroup group;
    public final double radiusKm;
    /** Masse in kg (0, wenn unbekannt/vernachlässigbar). */
    public final double massKg;
    public final double albedo;
    /** Grundfarbe, linear 0..1 — Darstellung, steht nur im Code. */
    public final float red, green, blue;
    public final Surface surface;
    public final int discoveredYear;
    public final String discoveredBy;
    public final String namesake;
    public final OrbitElements orbit;
    public final OrbitQuality quality;
    /** Kurzangabe der Bahnquelle, z. B. „JPL URA184 · an Horizons angeglichen“. */
    public final String orbitSource;
    /** Größte Abweichung von Horizons 1975–2030 in Grad (NaN = nicht geprüft). */
    public final double fitMaxDeg;

    public Body(String name, int naifId, BodyGroup group, double radiusKm, double massKg, double albedo,
                float red, float green, float blue, Surface surface,
                int discoveredYear, String discoveredBy, String namesake,
                OrbitElements orbit, OrbitQuality quality, String orbitSource, double fitMaxDeg) {
        this.name = name;
        this.naifId = naifId;
        this.group = group;
        this.radiusKm = radiusKm;
        this.massKg = massKg;
        this.albedo = albedo;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.surface = surface;
        this.discoveredYear = discoveredYear;
        this.discoveredBy = discoveredBy;
        this.namesake = namesake;
        this.orbit = orbit;
        this.quality = quality;
        this.orbitSource = orbitSource;
        this.fitMaxDeg = fitMaxDeg;
    }

    /** Derselbe Mond mit anderer Bahn (etwa aus der Datenbank). */
    public Body withOrbit(OrbitElements o, OrbitQuality q, String source, double fitMax) {
        return new Body(name, naifId, group, radiusKm, massKg, albedo, red, green, blue, surface,
                discoveredYear, discoveredBy, namesake, o, q, source, fitMax);
    }

    /** Große Halbachse in Uranusradien. */
    public double aRu() { return orbit.aKm / UranusSystem.URANUS_RADIUS_KM; }

    @Override
    public String toString() { return name; }
}
