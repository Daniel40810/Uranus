package com.dan.uranus.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ein Szenario der Spielwiese: Startdatum, Tempo und für jeden Körper ein vollständiger Zustandsvektor
 * relativ zur Uranusmitte im ICRF (km, km/s) — dieselbe Form, in der Horizons liefert.
 * Uranus selbst steht nicht in der Liste, er ist immer der Ursprung.
 */
public final class Scenario {

    public enum Origin { BUILTIN, USER }

    /** MOON: rechnet mit Masse · TEST: masselos (Irreguläre) · GUEST: neu hinzugefügt. */
    public enum Role { MOON, TEST, GUEST }

    public static final class Item {
        public final String name;
        public final Role role;
        public final double massKg;
        /** Masse aus Radius und angenommener Dichte statt gemessen. */
        public final boolean massEstimated;
        public final double radiusKm;
        /** Lage und Geschwindigkeit relativ zu Uranus, ICRF. */
        public final double[] r, v;

        public Item(String name, Role role, double massKg, boolean massEstimated, double radiusKm, double[] r, double[] v) {
            this.name = name; this.role = role; this.massKg = massKg; this.massEstimated = massEstimated; this.radiusKm = radiusKm;
            this.r = r.clone(); this.v = v.clone();
        }
    }

    public final String code, title, description;
    /** Startzeit als JD (UTC, wie die Uhr der App). */
    public final double startJd;
    /** Vorgeschlagenes Tempo in simulierten Tagen je Sekunde. */
    public final double rateDaysPerS;
    public final Origin origin;
    /** Neben den Monden die Lage nach der Ephemeride zeigen („Heute, frei“). */
    public final boolean compareEphemeris;
    private final List<Item> items;

    public Scenario(String code, String title, String description, double startJd, double rateDaysPerS, Origin origin,
                    boolean compareEphemeris, List<Item> items) {
        this.code = code; this.title = title; this.description = description == null ? "" : description;
        this.startJd = startJd; this.rateDaysPerS = rateDaysPerS; this.origin = origin; this.compareEphemeris = compareEphemeris;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public List<Item> items() { return items; }

    public Item item(String name) {
        for (Item i : items) if (i.name.equals(name)) return i;
        return null;
    }

    /**
     * Energie (J) in der einfachen, überall gleich nachrechenbaren Form: uranuszentriert, Punktmassen,
     * E = Σ ½·m·v² − Σ G·M_U·m / r − Σ G·m_i·m_j / r_ij. Masselose Testkörper tragen nichts bei.
     * Dieselbe Formel rechnet URA_API.energy in PL/SQL — der Durchstich vergleicht beide.
     */
    public double energyJ() {
        double g = UranusSystem.G_KM, e = 0;                  // km³/(kg s²)
        for (int i = 0; i < items.size(); i++) {
            Item a = items.get(i);
            double m = a.role == Role.TEST ? 0 : a.massKg;
            if (m == 0) continue;
            double v2 = a.v[0] * a.v[0] + a.v[1] * a.v[1] + a.v[2] * a.v[2];
            double r = Math.sqrt(a.r[0] * a.r[0] + a.r[1] * a.r[1] + a.r[2] * a.r[2]);
            e += 0.5 * m * v2 - g * UranusSystem.URANUS_MASS_KG * m / r;
            for (int j = i + 1; j < items.size(); j++) {
                Item b = items.get(j);
                double mb = b.role == Role.TEST ? 0 : b.massKg;
                if (mb == 0) continue;
                double dx = a.r[0] - b.r[0], dy = a.r[1] - b.r[1], dz = a.r[2] - b.r[2];
                e -= g * m * mb / Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }
        return e * 1e6;                                       // km²/s² · kg → J
    }

    @Override
    public String toString() { return title; }
}
