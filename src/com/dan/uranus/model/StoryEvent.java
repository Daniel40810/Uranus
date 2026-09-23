package com.dan.uranus.model;

/**
 * Ein Ereignis auf der Zeitachse: Entdeckung, Vorbeiflug, Jahreszeit, Beobachtung.
 * Zeitpunkt als Julianisches Datum in UTC (wie die Uhr der App).
 */
public final class StoryEvent {

    public enum Kind { DISCOVERY, FLYBY, SEASON, OBSERVATION }

    /** Wie genau der Zeitpunkt bekannt ist — bestimmt die Anzeige. */
    public enum Precision { YEAR, DAY, MINUTE }

    public final double jdUtc;
    public final Kind kind;
    public final Precision precision;
    public final String title, text;
    /** Betroffener Mond oder Ring (Name) oder null. */
    public final String body, ring;

    public StoryEvent(double jdUtc, Kind kind, Precision precision, String title, String text, String body, String ring) {
        this.jdUtc = jdUtc; this.kind = kind; this.precision = precision; this.title = title; this.text = text;
        this.body = body; this.ring = ring;
    }

    /** Jahr als Dezimalzahl (für Zeitleisten). */
    public double year() { return 2000 + (jdUtc - SimClock.JD_J2000) / 365.25; }

    @Override
    public String toString() { return title; }
}
