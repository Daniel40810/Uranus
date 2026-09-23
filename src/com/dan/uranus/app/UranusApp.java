package com.dan.uranus.app;

import com.dan.fframe.FFrame;
import com.dan.ficons.FIconType;
import com.dan.uranus.db.UranusDao;
import com.dan.uranus.db.UranusDb;
import com.dan.uranus.model.SimClock;
import com.dan.uranus.model.UranusSystem;
import com.dan.uranus.render.RenderLoop;
import com.dan.uranus.render.Renderer;
import com.dan.uranus.render.SceneSettings;
import com.dan.uranus.ui.UranusView;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Uranus — der Planet, der auf der Seite liegt, mit Ringen und Monden im gekrümmten Raum.
 * <p>
 * Startet sofort mit dem eingebauten Datensatz und lädt im Hintergrund das Schema DEMO
 * (URA_API). Kommt die Datenbank, gleiten die Monde auf ihre Lage aus der Datenbank; kommt sie
 * nicht, bleibt der eingebaute Satz und die Statuszeile nennt den Grund.
 */
public final class UranusApp {

    private final FFrame frame;
    private final RenderLoop loop;
    private final SceneSettings settings;
    private final SimClock clock;
    private final ControlBar bar;
    private final Renderer renderer;
    private final UranusView view;
    private final AppPrefs prefs;
    private final AppPrefs.Values start;
    private volatile boolean snapping;

    public UranusApp() {
        settings = new SceneSettings();
        clock = new SimClock();
        renderer = new Renderer(UranusSystem.builtIn(), settings, clock);
        view = new UranusView(settings, renderer.camera());
        loop = new RenderLoop(renderer, clock, settings, new RenderLoop.SizeSource() {
            @Override public int width() { return view.getWidth(); }
            @Override public int height() { return view.getHeight(); }
        }, view::show);
        bar = new ControlBar(settings, clock, renderer, loop);

        frame = new FFrame("Uranus");
        frame.setLogoType(FIconType.GEO);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel pane = frame.getComponentPane();
        pane.setLayout(new BorderLayout());
        pane.setBackground(new Color(3, 5, 10));
        frame.setComponentPaneColor(new Color(3, 5, 10));
        pane.add(view, BorderLayout.CENTER);
        pane.add(bar, BorderLayout.SOUTH);
        frame.setPreferredFrameSize(new Dimension(DEFAULT_W, DEFAULT_H));
        frame.setIconImages(icons());
        loop.onError = e -> AppLog.error("Fehler beim Zeichnen", e);
        settings.snapshotDir = Snapshot.folder().getPath();

        // Gemerkte Einstellungen (Fenster folgt in show())
        prefs = new AppPrefs();
        start = prefs.load();
        bar.apply(start);

        // Tasten
        key("SPACE", bar::togglePause);
        key("F1", this::toggleHelp);
        key("typed ?", this::toggleHelp);
        key("typed s", this::snapshot);
        key("typed S", this::snapshot);
        key("typed g", bar::toggleSpace);
        key("typed G", bar::toggleSpace);
        key("typed t", bar::toggleTrails);
        key("typed T", bar::toggleTrails);
        key("typed l", this::toggleLabels);
        key("typed L", this::toggleLabels);
        key("typed +", () -> bar.stepSpeed(1));
        key("typed -", () -> bar.stepSpeed(-1));
        key("ctrl Z", () -> { if (settings.playActive) settings.playUndo = true; });
        key("typed 0", () -> focus(null));
        String[] majors = {"Miranda", "Ariel", "Umbriel", "Titania", "Oberon"};
        for (int i = 0; i < majors.length; i++) { String n = majors[i]; key("typed " + (i + 1), () -> focus(n)); }

        view.helpButton = renderer::helpButton;
        view.onAction = a -> {
            switch (a) {
                case "standbild": snapshot(); break;
                case "hilfe": toggleHelp(); break;
                case "standard": resetDefaults(); break;
                default:
            }
        };
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { saveSettings(); loop.stop(); AppLog.info("Beendet"); }
        });
    }

    static final int DEFAULT_W = 1480, DEFAULT_H = 960;

    private void key(String stroke, Runnable r) {
        KeyStroke ks = KeyStroke.getKeyStroke(stroke);
        if (ks == null) throw new IllegalArgumentException(stroke);
        view.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, stroke);
        view.getActionMap().put(stroke, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    /** Taste 0 = Übersicht, 1–5 = ein Hauptmond. Eine laufende Geschichte endet wie bei einem Klick. */
    public void focus(String name) {
        if (settings.tourRunning != null) settings.tourStopRequest = true;
        settings.ringFlight = false;
        settings.focus = name == null ? null : renderer.system().find(name);
        if (name == null) renderer.requestFit();
    }

    public void toggleHelp() { settings.help = !settings.help; }

    public void toggleLabels() {
        settings.labels = !settings.labels;
        renderer.note(settings.labels ? "Beschriftung an" : "Beschriftung aus");
    }

    /** Standbild: der Render-Thread rechnet es, gespeichert wird im Hintergrund. */
    public void snapshot() {
        if (snapping) return;
        snapping = true;
        settings.snapshot = null;
        settings.snapshotRequest = true;
        Thread t = new Thread(() -> {
            try {
                java.awt.image.BufferedImage img = null;
                for (int k = 0; k < 500 && (img = settings.snapshot) == null; k++) Thread.sleep(20);
                if (img == null) { renderer.note("Standbild: kein Bild erhalten"); return; }
                settings.snapshot = null;
                java.io.File f = Snapshot.write(img, settings.snapshotInfo, Snapshot.folder());
                lastSnapshot = f;
                AppLog.info("Standbild " + f + " (" + img.getWidth() + "×" + img.getHeight() + ")");
                renderer.note("Standbild gespeichert: " + f.getPath());
            } catch (Exception ex) {
                AppLog.error("Standbild nicht gespeichert", ex);
                renderer.note("Standbild nicht gespeichert: " + ex.getMessage());
            } finally {
                snapping = false;
            }
        }, "uranus-standbild");
        t.setDaemon(true);
        t.start();
    }

    private volatile java.io.File lastSnapshot;

    /** Zuletzt gespeichertes Standbild (für Prüfungen). */
    public java.io.File lastSnapshot() { return lastSnapshot; }

    /** „Auf Standard zurück“: gemerkte Werte löschen, Bedienung und Fenster auf den Anfang. */
    public void resetDefaults() {
        prefs.clear();
        bar.apply(new AppPrefs.Values());
        frame.setExtendedState(java.awt.Frame.NORMAL);
        frame.setSize(DEFAULT_W, DEFAULT_H);
        frame.setLocationRelativeTo(null);
        settings.help = false;
        renderer.note("Auf Standard zurückgesetzt");
        AppLog.info("Einstellungen auf Standard");
    }

    /** Merkt Fenster, Ebenen, Raum, Maßstab, Monde und Tempo. */
    public void saveSettings() {
        AppPrefs.Values v = bar.current();
        v.maximized = (frame.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0;
        if (!v.maximized) v.window = frame.getBounds();
        else v.window = start.window;
        prefs.save(v);
    }

    public AppPrefs prefs() { return prefs; }

    public UranusView view() { return view; }

    /** Fenstersymbol: Uranus mit fast senkrechtem Ring, in mehreren Größen für Titel und Taskleiste. */
    public static java.util.List<java.awt.Image> icons() {
        java.util.List<java.awt.Image> l = new java.util.ArrayList<>();
        for (int n : new int[]{16, 20, 24, 32, 40, 48, 64, 128, 256}) {
            java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(n, n, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = im.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
            double c = n / 2.0, r = n * 0.30;
            java.awt.geom.AffineTransform tilt = java.awt.geom.AffineTransform.getRotateInstance(Math.toRadians(-8), c, c);
            java.awt.Shape ring = tilt.createTransformedShape(new java.awt.geom.Ellipse2D.Double(c - n * 0.15, c - n * 0.47, n * 0.30, n * 0.94));
            g.setStroke(new java.awt.BasicStroke((float) Math.max(1.2, n / 22.0)));
            g.setColor(new Color(170, 226, 232, 150));
            g.setClip(new java.awt.Rectangle(0, 0, n, (int) Math.round(c)));
            g.draw(ring);                                           // hinten (obere Hälfte liegt hinter dem Planeten)
            g.setClip(null);
            g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(c - r * 0.35, c - r * 0.35), (float) (r * 1.4),
                    new float[]{0f, 0.55f, 1f}, new Color[]{new Color(196, 242, 246), new Color(104, 196, 208), new Color(36, 92, 110)}));
            g.fill(new java.awt.geom.Ellipse2D.Double(c - r, c - r, 2 * r, 2 * r));
            g.setColor(new Color(200, 238, 242, 230));
            g.setClip(new java.awt.Rectangle(0, (int) Math.round(c), n, n));
            g.draw(ring);                                           // vorn
            g.dispose();
            l.add(im);
        }
        return l;
    }

    public FFrame frame() { return frame; }

    public SceneSettings settings() { return settings; }

    public UranusSystem system() { return renderer.system(); }

    public Renderer renderer() { return renderer; }

    /** Lädt den Datensatz aus der Datenbank im Hintergrund; ohne Datenbank bleibt der eingebaute. */
    public Thread loadDatabase(UranusDb db) {
        renderer.setDataLabel("Daten: eingebaut · Datenbank wird geladen …", db == null ? "" : db.toString());
        Thread t = new Thread(() -> {
            UranusDao.Result r = UranusDao.loadOrBuiltIn(db);
            if (r.fromDatabase) { renderer.setSystem(r.system, "Daten: Datenbank DEMO", r.message); AppLog.info("Datenbank geladen: " + r.message); }
            else {
                String why = db == null ? UranusDb.lastSearch : r.message;
                renderer.setDataLabel("Daten: eingebaut", "Datenbank nicht genutzt: " + why);
                AppLog.info("Datenbank nicht genutzt: " + why);
            }
        }, "uranus-db");
        t.setDaemon(true);
        t.start();
        return t;
    }

    public SimClock clock() { return clock; }

    public ControlBar controlBar() { return bar; }

    public void show() {
        frame.pack();
        if (start.window != null && onScreen(start.window)) frame.setBounds(start.window);
        else frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        if (start.maximized) frame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
        frame.setIconImages(icons());                               // nach dem Sichtbarwerden, falls FFrame ein eigenes setzt
        loop.start();
        UranusDb db = UranusDb.fromProperties();
        AppLog.info(UranusDb.lastSearch);
        loadDatabase(db);
    }

    /** Liegt die gemerkte Fensterlage noch zu einem guten Teil auf einem Bildschirm (Bildschirm abgesteckt)? */
    static boolean onScreen(java.awt.Rectangle r) {
        if (java.awt.GraphicsEnvironment.isHeadless()) return false;
        for (java.awt.GraphicsDevice d : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            java.awt.Rectangle b = d.getDefaultConfiguration().getBounds().intersection(r);
            if (!b.isEmpty() && b.width >= 200 && b.height >= 150) return true;
        }
        return false;
    }

    public void stop() { loop.stop(); }

    public static void main(String[] args) {
        AppLog.init(AppLog.homeDir());
        AppLog.info("Start · Java " + System.getProperty("java.version") + " · " + System.getProperty("os.name"));
        SwingUtilities.invokeLater(() -> {
            try { new UranusApp().show(); }
            catch (RuntimeException | Error e) { AppLog.error("Start fehlgeschlagen", e); throw e; }
        });
    }
}
