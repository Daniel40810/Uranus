package com.dan.uranus.render;

import com.dan.uranus.model.SimClock;

import java.util.function.Consumer;

/**
 * Eigener Render-Thread: rechnet Bild für Bild, übergibt das fertige Bild an die Oberfläche
 * und passt die Renderauflösung an, wenn der Rechner nicht mitkommt (Ziel 60 Bilder/s).
 * Der Swing-EDT zeichnet nur noch das fertige Bild und bleibt frei für die Bedienung.
 */
public final class RenderLoop implements Runnable {

    public interface SizeSource { int width(); int height(); }

    private final Renderer renderer;
    private final SimClock clock;
    private final SceneSettings settings;
    private final SizeSource size;
    private final Consumer<Renderer.Frame> sink;
    private volatile boolean running;
    private Thread thread;
    private volatile double fps, frameMs;

    public RenderLoop(Renderer renderer, SimClock clock, SceneSettings settings, SizeSource size, Consumer<Renderer.Frame> sink) {
        this.renderer = renderer;
        this.clock = clock;
        this.settings = settings;
        this.size = size;
        this.sink = sink;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "Uranus-Render");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    public double fps() { return fps; }

    public double frameMs() { return frameMs; }

    @Override
    public void run() {
        long last = System.nanoTime();
        double avg = 16, fpsAvg = 60;
        int slow = 0, fast = 0;
        final long target = 16_666_667L;
        while (running) {
            long t0 = System.nanoTime();
            double dt = Math.min(0.1, (t0 - last) / 1e9);
            last = t0;
            clock.advance(dt);
            int vw = size.width(), vh = size.height();
            if (vw < 16 || vh < 16) { sleep(50); continue; }
            // in Gerätepixeln rechnen: auf einem Bildschirm mit 150 % sind das 1,5 × so viele Pixel je Richtung
            double rs = settings.renderScale, sc = rs * Math.max(1, settings.deviceScale);
            int w = Math.max(16, (int) Math.round(vw * sc)), h = Math.max(16, (int) Math.round(vh * sc));
            try {
                if (settings.snapshotRequest) snapshot(vw, vh);
                Renderer.Frame f = renderer.render(w, h, dt, (double) vw / w);
                sink.accept(f);
                avg = avg * 0.9 + f.renderMs * 0.1;
                frameMs = avg;
                if (settings.renderError != null && System.nanoTime() - errorAt > 10_000_000_000L) settings.renderError = null;
            } catch (RuntimeException | OutOfMemoryError ex) {
                reportError(ex);
                sleep(200);
            }
            // Auflösung anpassen: zu langsam → kleiner rechnen, deutlich schneller → wieder größer
            if (settings.adaptiveScale) {
                // geregelt wird der Anteil an den Gerätepixeln (0,5 … 1), nicht die Pixelzahl selbst
                if (avg > 26) { if (++slow > 30 && rs > 0.5) { settings.renderScale = Math.max(0.5, rs - 0.1); slow = 0; } } else slow = 0;
                if (avg < 11) { if (++fast > 90 && rs < 1.0) { settings.renderScale = Math.min(1.0, rs + 0.1); fast = 0; } } else fast = 0;
            }
            long spent = System.nanoTime() - t0;
            if (spent < target) sleep((target - spent) / 1_000_000L);
            double inst = 1e9 / Math.max(1, System.nanoTime() - t0);
            fpsAvg = fpsAvg * 0.92 + inst * 0.08;
            fps = fpsAvg;
        }
    }

    private long errorAt;
    /** Fehler im Render-Thread: ins Protokoll und als dezente Zeile ins Bild; die Schleife läuft weiter. */
    private void reportError(Throwable ex) {
        errorAt = System.nanoTime();
        settings.renderError = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
        if (onError != null) onError.accept(ex); else ex.printStackTrace();
    }

    /** Empfänger für Fehler (das Protokoll der App); null = Konsole. */
    public volatile java.util.function.Consumer<Throwable> onError;

    /**
     * Standbild: ein Bild in doppelter Fenstergröße (höchstens 3 840 px breit), ohne Zeitschritt und ohne
     * Symbole, Hilfe und Karte am Zeiger. Die Beschriftung wird mitvergrößert und bleibt scharf.
     */
    private void snapshot(int vw, int vh) {
        settings.snapshotRequest = false;
        double f = Math.min(2.0, 3840.0 / vw);
        int w = (int) Math.round(vw * f), h = (int) Math.round(vh * f);
        Renderer.Frame fr = renderer.renderStill(w, h, (double) vw / w);
        settings.snapshotInfo = renderer.snapshotInfo();
        settings.snapshot = fr.composite(f);
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
