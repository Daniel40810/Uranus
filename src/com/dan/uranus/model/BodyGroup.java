package com.dan.uranus.model;

/**
 * Gruppe eines Uranus-Mondes. Die Reihenfolge ist zugleich die Zuschalt-Stufe:
 * MAJOR ist immer sichtbar, INNER kommt mit Stufe 2, IRREGULAR mit Stufe 3.
 */
public enum BodyGroup {
    /** Die fünf großen, runden Monde Miranda, Ariel, Umbriel, Titania, Oberon. */
    MAJOR("Hauptmond"),
    /** 14 kleine Monde dicht am Ringsystem (Cordelia bis Mab, dazu S/2025 U1). */
    INNER("innerer Mond"),
    /** 10 eingefangene Monde weit draußen, schräg und meist rückläufig. */
    IRREGULAR("irregulärer Mond");

    private final String label;

    BodyGroup(String label) { this.label = label; }

    public String label() { return label; }

    /** Sichtbar bei der gewählten Stufe (0 = nur Hauptmonde, 1 = + innere, 2 = alle). */
    public boolean visibleAt(int level) { return ordinal() <= level; }
}
