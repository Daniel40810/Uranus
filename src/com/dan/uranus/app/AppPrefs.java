package com.dan.uranus.app;

import java.awt.Rectangle;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Gemerkte Einstellungen über {@link java.util.prefs.Preferences}; unter Windows in der Registry unter
 * {@code HKCU/Software/JavaSoft/Prefs/com/dan/uranus} (Registry).
 * <p>
 * Gemerkt werden Fenster, Raum, Maßstab, Mondstufe, Ebenen und Tempo. Datum, laufende Geschichte und
 * Spielwiese nicht: jeder Start beginnt in der Gegenwart. Kaputte oder fremde Werte führen zum Standard.
 * Prüfungen benutzen einen eigenen Knoten ({@code -Duranus.prefs=...}), die echten Einstellungen bleiben unberührt.
 */
public final class AppPrefs {

    public static final String NODE = "com/dan/uranus";

    /** Ein vollständiger Satz Einstellungen; der Konstruktor liefert den Standard. */
    public static final class Values {
        public int space = 0, scale = 0, moons = 0, speed = 2, playSpeed = 1;
        public boolean trails = true, lens = true, bloom = true, axis = false, waves = true, magneto = true, geodesics = false, labels = true;
        /** Fensterlage; null = zentriert in Standardgröße. */
        public Rectangle window;
        public boolean maximized;

        @Override public boolean equals(Object o) {
            if (!(o instanceof Values)) return false;
            Values v = (Values) o;
            return space == v.space && scale == v.scale && moons == v.moons && speed == v.speed && playSpeed == v.playSpeed
                    && trails == v.trails && lens == v.lens && bloom == v.bloom && axis == v.axis && waves == v.waves
                    && magneto == v.magneto && geodesics == v.geodesics && labels == v.labels && maximized == v.maximized
                    && java.util.Objects.equals(window, v.window);
        }

        @Override public int hashCode() { return java.util.Objects.hash(space, scale, moons, speed, playSpeed, window); }
    }

    private final Preferences node;

    public AppPrefs() { this(System.getProperty("uranus.prefs", NODE)); }

    public AppPrefs(String path) { node = Preferences.userRoot().node(path); }

    public String path() { return node.absolutePath(); }

    /** Gemerkte Werte; was fehlt, kaputt oder außerhalb des Bereichs liegt, bekommt den Standard. */
    public Values load() {
        Values v = new Values();
        v.space = range(node.getInt("raum", v.space), 0, 1, v.space);
        v.scale = range(node.getInt("massstab", v.scale), 0, 1, v.scale);
        v.moons = range(node.getInt("monde", v.moons), 0, 2, v.moons);
        v.speed = range(node.getInt("tempo", v.speed), 0, 4, v.speed);
        v.playSpeed = range(node.getInt("spieltempo", v.playSpeed), 0, 4, v.playSpeed);
        v.trails = bool("bahnen", v.trails);
        v.lens = bool("linse", v.lens);
        v.bloom = bool("bloom", v.bloom);
        v.axis = bool("achse", v.axis);
        v.waves = bool("wellen", v.waves);
        v.magneto = bool("magnetfeld", v.magneto);
        v.geodesics = bool("lichtbahnen", v.geodesics);
        v.labels = bool("beschriftung", v.labels);
        v.maximized = bool("fenster.max", false);
        int x = node.getInt("fenster.x", Integer.MIN_VALUE), y = node.getInt("fenster.y", Integer.MIN_VALUE);
        int w = node.getInt("fenster.b", -1), h = node.getInt("fenster.h", -1);
        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && w >= 640 && h >= 480 && w <= 20_000 && h <= 20_000) v.window = new Rectangle(x, y, w, h);
        return v;
    }

    public void save(Values v) {
        node.putInt("raum", v.space);
        node.putInt("massstab", v.scale);
        node.putInt("monde", v.moons);
        node.putInt("tempo", v.speed);
        node.putInt("spieltempo", v.playSpeed);
        node.putBoolean("bahnen", v.trails);
        node.putBoolean("linse", v.lens);
        node.putBoolean("bloom", v.bloom);
        node.putBoolean("achse", v.axis);
        node.putBoolean("wellen", v.waves);
        node.putBoolean("magnetfeld", v.magneto);
        node.putBoolean("lichtbahnen", v.geodesics);
        node.putBoolean("beschriftung", v.labels);
        node.putBoolean("fenster.max", v.maximized);
        if (v.window != null) {
            node.putInt("fenster.x", v.window.x); node.putInt("fenster.y", v.window.y);
            node.putInt("fenster.b", v.window.width); node.putInt("fenster.h", v.window.height);
        }
        flush();
    }

    /** „Auf Standard zurück“: alles vergessen. */
    public void clear() {
        try { node.clear(); } catch (BackingStoreException e) { AppLog.error("Einstellungen nicht löschbar", e); }
        flush();
    }

    /** Roh-Zugriff für Prüfungen (kaputte Werte einschleusen). */
    public void putRaw(String key, String value) { node.put(key, value); flush(); }

    /** Den Knoten ganz entfernen (nur für Prüfknoten). */
    public void removeNode() {
        try { node.removeNode(); node.flush(); } catch (BackingStoreException | IllegalStateException e) { /* schon weg */ }
    }

    private void flush() {
        try { node.flush(); } catch (BackingStoreException e) { AppLog.error("Einstellungen nicht gespeichert", e); }
    }

    private boolean bool(String key, boolean def) {
        String s = node.get(key, null);
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;
        return def;
    }

    private static int range(int v, int lo, int hi, int def) { return v < lo || v > hi ? def : v; }
}
