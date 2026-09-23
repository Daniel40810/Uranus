package com.dan.uranus.ui;

import com.dan.uranus.model.Body;
import com.dan.uranus.render.Camera;
import com.dan.uranus.render.Renderer;
import com.dan.uranus.render.SceneSettings;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * Zeigt das zuletzt gerechnete Bild und übersetzt Maus und Tasten in Kamerabewegungen.
 * <ul>
 *   <li>Ziehen: Kamera drehen · Mausrad: zoomen</li>
 *   <li>Klick auf einen Mond: hinfliegen und folgen · Doppelklick ins Leere oder Esc: Übersicht</li>
 *   <li>Während einer Geschichte: Esc, Klick, Ziehen oder Mausrad beenden sie — die Kamera gehört wieder dem Nutzer</li>
 *   <li>Spielwiese: Ziehen an einem Mond verschiebt und wirft ihn, Mausrad über einem Mond ändert seine Masse,
 *       Umschalt + Ziehen setzt einen Gast (Pfeil = Richtung und Tempo), Esc löst den Griff</li>
 *   <li>Symbole oben rechts: Standbild und Hilfe; in der Hilfetafel der Knopf „Auf Standard zurück“</li>
 * </ul>
 * Das Bild wird in Gerätepixeln gezeichnet (Bildschirmskalierung), die Beschriftung in Fensterkoordinaten.
 */
public final class UranusView extends JComponent {

    private final SceneSettings settings;
    private final Camera camera;
    private volatile Renderer.Frame frame;
    private int lastX, lastY;
    private boolean dragged, tourStopped, playDrag, playMoved;
    private int pressX, pressY;
    /** Druck auf ein Symbol oder den Knopf der Hilfe: wird beim Loslassen ausgelöst. */
    private String uiPress;
    private boolean uiClick;
    private long firstFrame;
    /** Aktionen der Symbole: "standbild", "hilfe", "standard" (setzt die App). */
    public volatile java.util.function.Consumer<String> onAction;
    /** Knopf „Auf Standard zurück“ der Hilfetafel (vom Renderer), null = Tafel zu. */
    public volatile java.util.function.Supplier<double[]> helpButton;
    /** Feste Skalierung für Prüfungen (-Duranus.scale=1.5); 0 = vom Bildschirm. */
    private static final double SCALE_OVERRIDE = parseScale(System.getProperty("uranus.scale"));

    public UranusView(SceneSettings settings, Camera camera) {
        this.settings = settings;
        this.camera = camera;
        setOpaque(true);
        setBackground(new Color(3, 5, 10));
        setFocusable(true);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                lastX = e.getX(); lastY = e.getY(); pressX = e.getX(); pressY = e.getY(); dragged = false; requestFocusInWindow();
                uiClick = false;
                uiPress = uiHit(e.getX(), e.getY());
                if (uiPress != null) return;                                    // Symbol oder Knopf: nichts anderes auslösen
                if (settings.help) { settings.help = false; uiClick = true; return; }   // Klick neben die Hilfetafel schließt sie
                settings.mouseDown = true;
                if (settings.tourRunning != null) { settings.tourStopRequest = true; tourStopped = true; }   // wer eingreift, übernimmt
                else tourStopped = false;
                // Spielwiese: Mond greifen oder mit Umschalt einen Gast setzen
                if (settings.playActive && SwingUtilities.isLeftMouseButton(e) && (e.isShiftDown() || pick(e.getX(), e.getY()) != null)) {
                    playDrag = true;
                    playMoved = false;
                    settings.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.PRESS, e.getX(), e.getY(), 0, e.isShiftDown(), false));
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                settings.mouseDown = false;
                if (uiPress != null) {
                    String hit = uiHit(e.getX(), e.getY());
                    if (uiPress.equals(hit) && onAction != null) onAction.accept(hit);
                    uiPress = null;
                    uiClick = true;
                    return;
                }
                if (!playDrag) return;
                playDrag = false;
                settings.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.RELEASE, e.getX(), e.getY(), 0, false, playMoved));
            }

            @Override public void mouseDragged(MouseEvent e) {
                settings.mouseX = e.getX(); settings.mouseY = e.getY();
                if (uiPress != null || uiClick) return;
                if (playDrag) {
                    if (Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY) > 3) { playMoved = true; dragged = true; }
                    settings.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.DRAG, e.getX(), e.getY(), 0, false, playMoved));
                    return;
                }
                int dx = e.getX() - lastX, dy = e.getY() - lastY;
                if (Math.abs(dx) + Math.abs(dy) > 0) dragged = true;
                camera.rotate(-dx * 0.006, dy * 0.005);
                lastX = e.getX(); lastY = e.getY();
            }

            @Override public void mouseMoved(MouseEvent e) {
                settings.mouseX = e.getX(); settings.mouseY = e.getY();
                Body b = pick(e.getX(), e.getY());
                settings.hover = b;
                setCursor(Cursor.getPredefinedCursor(b != null || uiHit(e.getX(), e.getY()) != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override public void mouseExited(MouseEvent e) { settings.hover = null; settings.mouseX = -1; settings.mouseY = -1; }

            @Override public void mouseClicked(MouseEvent e) {
                if (uiClick) { uiClick = false; return; }
                if (dragged || tourStopped || !SwingUtilities.isLeftMouseButton(e)) return;
                Body b = pick(e.getX(), e.getY());
                if (b != null) { settings.ringFlight = false; settings.focus = b; }
                else if (e.getClickCount() >= 2) { settings.ringFlight = false; settings.focus = null; }
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (settings.tourRunning != null) settings.tourStopRequest = true;
                if (settings.playActive && (playDrag || pick(e.getX(), e.getY()) != null)) {       // Masse statt Zoom
                    settings.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.WHEEL, e.getX(), e.getY(),
                            e.getPreciseWheelRotation(), false, false));
                    return;
                }
                camera.zoom(Math.pow(1.12, e.getPreciseWheelRotation()));
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "overview");
        getActionMap().put("overview", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (settings.help) { settings.help = false; return; }                          // Esc schließt zuerst die Hilfe
                if (settings.tourRunning != null) { settings.tourStopRequest = true; return; }   // Esc beendet zuerst die Geschichte
                if (settings.playActive) {                                                     // Spielwiese: Griff lösen, Auswahl aufheben
                    playDrag = false;
                    settings.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.CANCEL, 0, 0, 0, false, false));
                    settings.focus = null;
                    return;
                }
                settings.ringFlight = false; settings.focus = null;
            }
        });
    }

    /** Vom Render-Thread aufgerufen. */
    public void show(Renderer.Frame f) {
        frame = f;
        repaint();
    }

    /** Symbol oben rechts oder Knopf der Hilfetafel unter (x, y)? */
    public String uiHit(double x, double y) {
        for (int i = 0; i < 2; i++) {
            double[] r = Renderer.iconRect(i, getWidth());
            if (x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]) return i == 0 ? "standbild" : "hilfe";
        }
        java.util.function.Supplier<double[]> hb = helpButton;
        double[] r = settings.help && hb != null ? hb.get() : null;
        if (r != null && x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]) return "standard";
        return null;
    }

    private static double parseScale(String v) {
        try { return v == null ? 0 : Math.max(1, Math.min(4, Double.parseDouble(v))); } catch (NumberFormatException e) { return 0; }
    }

    private Body pick(int x, int y) {
        Renderer.Frame f = frame;
        if (f == null) return null;
        double sx = (double) f.image.getWidth() / Math.max(1, getWidth()), sy = (double) f.image.getHeight() / Math.max(1, getHeight());
        double px = x * sx, py = y * sy, best = Double.MAX_VALUE;
        Body hit = null;
        for (Renderer.Pick p : f.picks) {
            double d = Math.hypot(p.x - px, p.y - py), reach = Math.max(10 * sx, p.r + 6 * sx);
            if (d < reach && d < best) { best = d; hit = p.body; }
        }
        return hit;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Renderer.Frame f = frame;
        if (f == null) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        // Bildschirmskalierung melden: der Render-Thread rechnet dann in Gerätepixeln
        double ds = SCALE_OVERRIDE > 0 ? SCALE_OVERRIDE : Math.max(1, g2.getTransform().getScaleX());
        if (Math.abs(ds - settings.deviceScale) > 1e-3) settings.deviceScale = ds;
        double dev = getWidth() * g2.getTransform().getScaleX();
        if (Math.abs(f.image.getWidth() - dev) > 0.5)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // erstes Bild weich einblenden (0,6 s)
        if (firstFrame == 0) firstFrame = System.nanoTime();
        float a = (float) Math.min(1, (System.nanoTime() - firstFrame) / 6e8);
        if (a < 1) {
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, a));
        }
        g2.drawImage(f.image, 0, 0, getWidth(), getHeight(), null);
        f.overlay.paint(g2);
        g2.setComposite(java.awt.AlphaComposite.SrcOver);
    }
}
