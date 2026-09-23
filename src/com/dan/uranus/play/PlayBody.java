package com.dan.uranus.play;

import com.dan.uranus.model.Body;
import com.dan.uranus.model.Scenario;

/** Ein Körper der Spielwiese. Lage und Geschwindigkeit stehen im Integrator, hier nur Eigenschaften. */
public final class PlayBody {

    public enum Role { URANUS, MOON, TEST, GUEST }

    private static int nextId = 1;

    /** Bleibt über Verschmelzen und Rückgängig gleich — der Renderer ordnet darüber zu. */
    public final int id;
    public final String name;
    public final Role role;
    /** Der echte Mond dahinter (Farbe, Oberfläche, Ephemeride) oder null. */
    public final Body source;
    /** GM in km³/s²; bei Testkörpern 0. */
    public double gm;
    /** Masse beim Start (für die Stufen des Mausrads). */
    public final double gmStart;
    public final boolean massEstimated;
    public double radiusKm;
    /** Zerbricht unter der Roche-Grenze — nur, wer von außen hineingerät. */
    public boolean rocheArmed;

    PlayBody(String name, Role role, Body source, double gm, boolean massEstimated, double radiusKm) {
        this(nextId++, name, role, source, gm, gm, massEstimated, radiusKm);
    }

    private PlayBody(int id, String name, Role role, Body source, double gm, double gmStart, boolean massEstimated, double radiusKm) {
        this.id = id; this.name = name; this.role = role; this.source = source; this.gm = gm; this.gmStart = gmStart;
        this.massEstimated = massEstimated; this.radiusKm = radiusKm;
    }

    PlayBody copy() {
        PlayBody b = new PlayBody(id, name, role, source, gm, gmStart, massEstimated, radiusKm);
        b.rocheArmed = rocheArmed;
        return b;
    }

    /** Neuer Körper mit anderem Namen, Masse und Radius (Verschmelzen) — behält die Nummer des Schwereren. */
    PlayBody merged(double gm, double radiusKm) {
        PlayBody b = new PlayBody(id, name, role, source, gm, gmStart, massEstimated, radiusKm);
        b.rocheArmed = rocheArmed;
        return b;
    }

    public double massKg() { return gm / com.dan.uranus.model.UranusSystem.G_KM; }

    /** Mittlere Dichte in kg/m³. */
    public double density() { return massKg() / (4.0 / 3.0 * Math.PI * Math.pow(radiusKm * 1000, 3)); }

    public boolean massive() { return gm > 0; }

    static Role of(Scenario.Role r) {
        switch (r) {
            case TEST: return Role.TEST;
            case GUEST: return Role.GUEST;
            default: return Role.MOON;
        }
    }

    Scenario.Role scenarioRole() {
        return role == Role.TEST ? Scenario.Role.TEST : role == Role.GUEST ? Scenario.Role.GUEST : Scenario.Role.MOON;
    }

    @Override
    public String toString() { return name; }
}
