package com.dan.uranus.play;

/** Etwas, das in der Spielwiese passiert ist: für Aufblitzen, Meldung und Prüfung. */
public final class PlayEvent {

    public enum Kind { MERGE, BREAKUP, IMPACT, ESCAPE }

    public final Kind kind;
    /** Spielzeit (JD, UTC). */
    public final double jd;
    public final String text;
    /** Ort relativ zu Uranus (Welt, km). */
    public final double[] km;
    /** Masse des größeren Beteiligten (kg) — bestimmt die Helligkeit des Blitzes. */
    public final double massKg;

    PlayEvent(Kind kind, double jd, String text, double[] km, double massKg) {
        this.kind = kind; this.jd = jd; this.text = text; this.km = km.clone(); this.massKg = massKg;
    }

    @Override
    public String toString() { return text; }
}
