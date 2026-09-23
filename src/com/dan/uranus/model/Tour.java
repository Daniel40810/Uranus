package com.dan.uranus.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Eine Kamerafahrt: Stationen, zwischen denen weich übergeblendet wird.
 * <p>
 * {@link TimeMode#CLOCK}: die Stationen setzen die Uhr (Voyager, Jahreszeiten).
 * {@link TimeMode#STORY}: die Uhr läuft frei; die Zeit der Stationen ist Erzählzeit (Entdeckungen) oder
 * leer (Rundflug).
 */
public final class Tour {

    public enum Overlay { NONE, VOYAGER, SEASONS, DISCOVERY }

    public enum TimeMode { CLOCK, STORY }

    /** Eine Station. */
    public static final class Step {
        /** Zeitpunkt in der Fahrt (Sekunden ab Start). */
        public final double atS;
        /** Uhr- oder Erzählzeit (JD, UTC); NaN = frei. */
        public final double jdUtc;
        public final double yawDeg, pitchDeg, dist;
        /** Ziel: „Uranus“, ein Mondname oder der Name einer Sonde. */
        public final String target;
        /** Ebenen und Schalter, z. B. „moons=2;magneto=0;cam=sun“. */
        public final String layers;
        public final String title, caption;

        public Step(double atS, double jdUtc, double yawDeg, double pitchDeg, double dist, String target, String layers,
                    String title, String caption) {
            this.atS = atS; this.jdUtc = jdUtc; this.yawDeg = yawDeg; this.pitchDeg = pitchDeg; this.dist = dist;
            this.target = target; this.layers = layers == null ? "" : layers; this.title = title == null ? "" : title;
            this.caption = caption == null ? "" : caption;
        }

        /** Wert eines Schalters aus {@link #layers}, sonst null. */
        public String layer(String key) {
            for (String kv : layers.split(";")) {
                int k = kv.indexOf('=');
                if (k > 0 && kv.substring(0, k).trim().equals(key)) return kv.substring(k + 1).trim();
            }
            return null;
        }
    }

    public final String code, title, description;
    public final Overlay overlay;
    public final TimeMode timeMode;
    private final List<Step> steps;

    public Tour(String code, String title, String description, Overlay overlay, TimeMode timeMode, List<Step> steps) {
        if (steps.size() < 2) throw new IllegalArgumentException("Eine Fahrt braucht mindestens zwei Stationen");
        for (int i = 1; i < steps.size(); i++)
            if (!(steps.get(i).atS > steps.get(i - 1).atS)) throw new IllegalArgumentException("Stationen müssen zeitlich aufsteigen: " + code);
        this.code = code; this.title = title; this.description = description; this.overlay = overlay; this.timeMode = timeMode;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public List<Step> steps() { return steps; }

    /** Dauer der Fahrt in Sekunden. */
    public double duration() { return steps.get(steps.size() - 1).atS; }

    @Override
    public String toString() { return title; }
}
