package com.dan.uranus.render;

import com.dan.uranus.model.Body;

/**
 * Schalter der Szene, von der Oberfläche gesetzt, vom Render-Thread gelesen.
 * Die Übergänge (Mulde ↔ Gitterraum, gestaucht ↔ echt, Ein-/Ausblenden) laufen im Renderer
 * weich nach; hier stehen nur die Zielwerte.
 */
public final class SceneSettings {

    /** 0 = Mulde, 1 = Gitterraum. */
    public volatile int spaceMode = 0;
    /** 0 = gestaucht, 1 = maßstabsgetreu. */
    public volatile int scaleMode = 0;
    /** 0 = Hauptmonde, 1 = + innere, 2 = alle 29. */
    public volatile int moonLevel = 0;
    public volatile boolean trails = true;
    public volatile boolean axis = false;
    public volatile boolean lens = true;
    public volatile boolean bloom = true;
    public volatile boolean waves = true;
    public volatile boolean labels = true;
    /** Magnetfeld, Polarlichter, Schweif, Bugstoßwelle, Sonnenwind. */
    public volatile boolean magneto = true;
    /** Lichtbahnen (Geodäten) aus der Sonnenrichtung. */
    public volatile boolean geodesics = false;
    /** Flug durch den ε-Ring. */
    public volatile boolean ringFlight = false;
    /** Mond, dem die Kamera folgt (null = Übersicht). */
    public volatile Body focus;
    /** Mond unter dem Mauszeiger. */
    public volatile Body hover;
    /** Kamerafahrt starten (Code aus URA_TOUR); der Render-Thread übernimmt und löscht die Anfrage. */
    public volatile String tourRequest;
    /** Laufende Fahrt beenden bzw. anhalten/fortsetzen (Anfragen an den Render-Thread). */
    public volatile boolean tourStopRequest, tourPauseRequest;
    /** Vom Render-Thread gemeldet: Code der laufenden Fahrt oder null; angehalten? */
    public volatile String tourRunning;
    public volatile boolean tourPaused;
    // ---------------------------------------------------------------- Spielwiese (Phase 7)
    /** Anfragen an den Render-Thread: Szenario starten (oder den echten Stand von jetzt), beenden, rückgängig, sichern. */
    public volatile com.dan.uranus.model.Scenario playScenario;
    public volatile boolean playLive, playStop, playUndo, playSaveRequest;
    /** Tempo in simulierten Tagen je Sekunde; angehalten. */
    public volatile double playRate = 1;
    public volatile boolean playPaused;
    /** Eingaben der Maus in Fensterkoordinaten, abgearbeitet im Render-Thread. */
    public final java.util.concurrent.ConcurrentLinkedQueue<PlayInput> playInput = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** Vom Render-Thread gemeldet. */
    public volatile boolean playActive;
    public volatile String playTitle = "";
    public volatile int playUndoDepth;
    /** Der zum Speichern erzeugte Stand (die Oberfläche holt ihn ab und speichert im Hintergrund). */
    public volatile com.dan.uranus.model.Scenario playSaved;

    /** Eine Mauseingabe der Spielwiese. */
    public static final class PlayInput {
        public enum Kind { PRESS, DRAG, RELEASE, WHEEL, CANCEL }
        public final Kind kind;
        public final double x, y, wheel;
        public final boolean shift, moved;
        public final long nanos = System.nanoTime();
        public PlayInput(Kind kind, double x, double y, double wheel, boolean shift, boolean moved) {
            this.kind = kind; this.x = x; this.y = y; this.wheel = wheel; this.shift = shift; this.moved = moved;
        }
    }

    /** Renderauflösung relativ zur Fenstergröße; wird bei Bedarf automatisch gesenkt. */
    public volatile double renderScale = 1.0;
    public volatile boolean adaptiveScale = true;

    // ---------------------------------------------------------------- Politur (Phase 8)
    /**
     * Bildschirmskalierung: Gerätepixel je logischem Pixel (Windows 1,25 / 1,5 / 2 …). Die Oberfläche trägt
     * sie ein; gerechnet wird in Gerätepixeln, damit das Bild nicht weich hochskaliert wird.
     */
    public volatile double deviceScale = 1.0;
    /** Mauszeiger in Fensterkoordinaten (logisch); −1 = nicht über dem Bild. */
    public volatile double mouseX = -1, mouseY = -1;
    /** Maustaste gedrückt (Kamera drehen, Ziehen in der Spielwiese): keine Karte beim Überfahren. */
    public volatile boolean mouseDown;
    /** Karten beim Überfahren (nach 0,3 s Verweilen). */
    public volatile boolean hoverCards = true;
    /** Hilfetafel (F1 oder ?). */
    public volatile boolean help;
    /** Standbild anfordern: der Render-Thread rechnet ein Bild in doppelter Größe und legt es in {@link #snapshot} ab. */
    public volatile boolean snapshotRequest;
    public volatile java.awt.image.BufferedImage snapshot;
    /** Szene und Einstellungen zum Standbild (Textfelder im PNG). */
    public volatile java.util.Map<String, String> snapshotInfo;
    /** Letzter Fehler im Render-Thread (erscheint dezent im Bild); null = keiner. */
    public volatile String renderError;
    /** Ordner der Standbilder (Anzeige in Hilfe und Karte). */
    public volatile String snapshotDir = "";
}
