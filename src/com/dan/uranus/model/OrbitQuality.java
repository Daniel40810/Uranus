package com.dan.uranus.model;

/** Herkunft und Güte einer Bahn. */
public enum OrbitQuality {
    /** Mittlere Elemente, wie JPL sie veröffentlicht. */
    MEAN("mittlere Elemente (JPL)"),
    /** JPL-Form mit Phase und Periode an Horizons-Positionen angeglichen. */
    FIT("an Horizons angeglichen");

    private final String label;

    OrbitQuality(String label) { this.label = label; }

    public String label() { return label; }
}
