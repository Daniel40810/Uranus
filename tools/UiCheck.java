import com.dan.uranus.app.UranusApp;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Startet die App unter einem (virtuellen) Bildschirm, prüft die Bedienleiste und fotografiert das Fenster. */
public class UiCheck {
    static int ok = 0, bad = 0;
    static void check(String name, boolean c, String info) { if (c) ok++; else bad++; System.out.println((c ? "[ok]     " : "[FEHLER] ") + name + (info.isEmpty() ? "" : " — " + info)); }

    static Robot robot;
    static JFrame win;

    /**
     * Tasten gehen nur an das Uranus-Fenster: Hat es den Fokus nicht (du arbeitest gerade woanders),
     * bricht der Teil ab, statt Tasten in ein fremdes Programm zu schicken.
     */
    static void focusGuard() throws Exception {
        for (int k = 0; k < 10 && !win.isActive(); k++) { SwingUtilities.invokeAndWait(() -> { win.toFront(); win.requestFocus(); }); Thread.sleep(200); }
        if (!win.isActive()) throw new IllegalStateException("Das Uranus-Fenster hat keinen Fokus — Tasten nicht gesendet");
    }

    /** Taste drücken und loslassen (mit Umschalt/Strg), dann kurz warten. */
    static void key(int vk, boolean ctrl) throws Exception {
        focusGuard();
        if (ctrl) robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
        robot.keyPress(vk); robot.keyRelease(vk);
        if (ctrl) robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
        Thread.sleep(500);
    }

    /** Linksklick auf eine Stelle der Ansicht (Fensterkoordinaten der Ansicht). */
    static void click(Component view, double x, double y) throws Exception {
        focusGuard();
        Point o = view.getLocationOnScreen();
        robot.mouseMove(o.x + (int) Math.round(x), o.y + (int) Math.round(y));
        Thread.sleep(150);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(600);
    }

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "shots"); dir.mkdirs();
        // eigener Einstellungsknoten und eigener Bilderordner: deine Einstellungen und Bilder bleiben unberührt
        String node = "com/dan/uranus-uicheck";
        new com.dan.uranus.app.AppPrefs(node).removeNode();
        System.setProperty("uranus.prefs", node);
        File bilder = new File(dir, "standbilder");
        System.setProperty("uranus.bilder", bilder.getPath());
        UranusApp[] app = new UranusApp[1];
        SwingUtilities.invokeAndWait(() -> { app[0] = new UranusApp(); app[0].show(); });
        Thread.sleep(5000);
        JFrame f = app[0].frame();
        Rectangle r = f.getBounds();
        check("Fenster sichtbar", f.isShowing(), r.width + "x" + r.height);
        // Jede Reihe der Bedienleiste muss in ihre Breite passen (FlowLayout bricht sonst unsichtbar um)
        JComponent bar = app[0].controlBar();
        int i = 0;
        for (Component row : bar.getComponents()) {
            if (!(row instanceof JPanel) || !row.isVisible()) continue;
            i++;
            int need = 0;
            for (Component c : ((JPanel) row).getComponents()) need += c.getPreferredSize().width + 8;
            boolean all = true;
            String bad1 = "";
            for (Component c : ((JPanel) row).getComponents()) {
                if (c instanceof Box.Filler) continue;
                Rectangle b = c.getBounds();
                if (b.width <= 0 || b.height <= 0 || b.x + b.width > row.getWidth() || b.y + b.height > row.getHeight()) { all = false; bad1 = c.getClass().getSimpleName() + " " + b; }
            }
            check("Reihe " + i + ": alle Elemente sichtbar", all, "braucht " + need + " px von " + row.getWidth() + (need > row.getWidth() ? " — umgebrochen" : "") + (all ? "" : "; " + bad1));
        }
        // schmales Fenster: die Reihen müssen umbrechen statt abzuschneiden
        SwingUtilities.invokeAndWait(() -> { f.setSize(980, 820); f.validate(); });
        Thread.sleep(1200);
        SwingUtilities.invokeAndWait(() -> f.validate());
        boolean allNarrow = true;
        for (Component row : bar.getComponents()) {
            if (!(row instanceof JPanel) || !row.isVisible()) continue;
            for (Component c : ((JPanel) row).getComponents()) {
                if (c instanceof Box.Filler) continue;
                Rectangle b = c.getBounds();
                if (b.width <= 0 || b.x + b.width > row.getWidth() || b.y + b.height > row.getHeight()) allNarrow = false;
            }
        }
        check("Schmales Fenster (980 px): Bedienleiste bricht um, nichts abgeschnitten", allNarrow, "Leistenhöhe " + bar.getHeight() + " px");
        ImageIO.write(new Robot().createScreenCapture(f.getBounds()), "png", new File(dir, "ui_0_schmal.png"));
        SwingUtilities.invokeAndWait(() -> { f.setBounds(r); f.validate(); });
        Thread.sleep(1500);
        Robot robot = new Robot();
        BufferedImage img = robot.createScreenCapture(r);
        ImageIO.write(img, "png", new File(dir, "ui_1_start.png"));
        // Farbvielfalt in der Szene (nicht nur schwarz)
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int y = 60; y < r.height - 160; y += 7) for (int x = 20; x < r.width - 20; x += 7) colors.add(img.getRGB(x, y) & 0xF0F0F0);
        check("Szene gezeichnet", colors.size() > 60, colors.size() + " Farben");
        // Fokus auf Titania, dann Gitterraum + alle Monde
        SwingUtilities.invokeAndWait(() -> app[0].settings().focus = app[0].system().find("Titania"));
        Thread.sleep(3500);
        ImageIO.write(robot.createScreenCapture(r), "png", new File(dir, "ui_2_titania.png"));
        SwingUtilities.invokeAndWait(() -> { app[0].settings().focus = null; app[0].settings().spaceMode = 1; app[0].settings().moonLevel = 2; app[0].settings().axis = true; });
        Thread.sleep(4500);
        ImageIO.write(robot.createScreenCapture(r), "png", new File(dir, "ui_3_gitter_alle.png"));
        // ε-Ring-Flug über den Knopf-Weg der Bedienleiste
        SwingUtilities.invokeAndWait(() -> { app[0].settings().spaceMode = 0; app[0].settings().moonLevel = 0; app[0].settings().axis = false; app[0].controlBar().startRingFlight(); });
        Thread.sleep(8000);
        BufferedImage fl = robot.createScreenCapture(r);
        ImageIO.write(fl, "png", new File(dir, "ui_4_ringflug.png"));
        check("Ringflug läuft (Echtzeit eingestellt)", app[0].settings().ringFlight && app[0].clock().rate() < 1e-4, "");
        SwingUtilities.invokeAndWait(() -> app[0].settings().ringFlight = false);
        Thread.sleep(3000);
        ImageIO.write(robot.createScreenCapture(r), "png", new File(dir, "ui_5_zurueck.png"));
        // Geschichten: Knopf startet, Leertaste-Weg hält an, derselbe Knopf beendet
        int tourButtons = 0;
        for (Component row : bar.getComponents())
            if (row instanceof JPanel) for (Component c : ((JPanel) row).getComponents())
                if (c instanceof com.dan.fbutton.FButton && app[0].system().tours().stream().anyMatch(t -> t.title.equals(((com.dan.fbutton.FButton) c).getText()))) tourButtons++;
        check("Reihe 3: GESCHICHTEN mit vier Knöpfen", tourButtons == 4, tourButtons + " Knöpfe");
        SwingUtilities.invokeAndWait(() -> app[0].controlBar().toggleTour("VOYAGER"));
        Thread.sleep(14000);
        ImageIO.write(robot.createScreenCapture(r), "png", new File(dir, "ui_6_voyager.png"));
        check("Geschichte „Voyager 2“ läuft, Uhr im Januar 1986", "VOYAGER".equals(app[0].settings().tourRunning)
                && app[0].clock().instant().toString().startsWith("1986-01"), app[0].clock().instant().toString());
        SwingUtilities.invokeAndWait(() -> app[0].controlBar().togglePause());
        Thread.sleep(800);
        check("Anhalten hält die Geschichte an", app[0].settings().tourPaused, "");
        SwingUtilities.invokeAndWait(() -> app[0].controlBar().toggleTour("VOYAGER"));
        Thread.sleep(3000);
        check("Derselbe Knopf beendet sie, Uhr wieder in der Gegenwart", app[0].settings().tourRunning == null
                && app[0].clock().instant().toString().startsWith("20"), app[0].clock().instant().toString());
        // Spielwiese: Knopf in Reihe 2, Reihe 3 wechselt, Szenario, Zurück
        SwingUtilities.invokeAndWait(() -> app[0].controlBar().togglePlay());
        Thread.sleep(2500);
        Component row3 = bar.getComponent(2), row3play = bar.getComponent(3);
        check("Spielwiese an: Reihe 3 zeigt die Spielwiese", app[0].settings().playActive && !row3.isVisible() && row3play.isVisible(), "");
        boolean allPlay = true;
        for (Component c : ((JPanel) row3play).getComponents()) {
            Rectangle b = c.getBounds();
            if (!(c instanceof Box.Filler) && (b.width <= 0 || b.x + b.width > row3play.getWidth() || b.y + b.height > row3play.getHeight())) allPlay = false;
        }
        int needPlay = 0;
        for (Component c : ((JPanel) row3play).getComponents()) needPlay += c.getPreferredSize().width + 8;
        check("Reihe der Spielwiese: alle Elemente sichtbar", allPlay, "braucht " + needPlay + " px von " + row3play.getWidth()
                + (needPlay > row3play.getWidth() ? " — umgebrochen" : ""));
        SwingUtilities.invokeAndWait(() -> app[0].settings().playScenario = app[0].system().scenario("MIRANDA"));
        Thread.sleep(6000);
        ImageIO.write(robot.createScreenCapture(r), "png", new File(dir, "ui_7_spielwiese.png"));
        check("Szenario „Miranda fällt“ läuft und Miranda zerbricht", app[0].renderer().playground() != null
                && !app[0].renderer().playground().events().isEmpty(), app[0].renderer().playground() == null ? "" : String.valueOf(app[0].renderer().playground().events()));
        SwingUtilities.invokeAndWait(() -> app[0].settings().playStop = true);
        Thread.sleep(2500);
        check("Zurück: Reihe 3 wieder mit Zeit und Geschichten", !app[0].settings().playActive && row3.isVisible() && !row3play.isVisible(), "");
        try { phase8(app[0], f, r, dir, bilder, node); }
        catch (IllegalStateException e) { check("Tasten und Mausklicks (Phase 8)", false, e.getMessage()); new com.dan.uranus.app.AppPrefs(node).removeNode(); }
        System.out.println("Ergebnis: " + ok + " ok, " + bad + " Fehler");
        app[0].stop();
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Phase 8: Tasten, Symbole, Standbild, Karte beim Überfahren, Hilfe, gemerkte Einstellungen. */
    static void phase8(UranusApp app, JFrame f, Rectangle r, File dir, File bilder, String node) throws Exception {
        robot = new Robot();
        win = f;
        com.dan.uranus.render.SceneSettings s = app.settings();
        com.dan.uranus.ui.UranusView view = app.view();
        SwingUtilities.invokeAndWait(() -> { f.toFront(); view.requestFocusInWindow(); });
        Thread.sleep(800);
        Point vo = view.getLocationOnScreen();
        robot.mouseMove(vo.x + 30, vo.y + view.getHeight() - 30);          // Maus aus dem Weg
        int sm = s.spaceMode;
        key(java.awt.event.KeyEvent.VK_G, false);
        boolean g1 = s.spaceMode != sm;
        key(java.awt.event.KeyEvent.VK_G, false);
        check("Taste G: Mulde ↔ Gitterraum (und zurück)", g1 && s.spaceMode == sm, "");
        boolean tr = s.trails;
        key(java.awt.event.KeyEvent.VK_T, false);
        boolean t1 = s.trails != tr;
        key(java.awt.event.KeyEvent.VK_T, false);
        check("Taste T: Bahnen aus und wieder an", t1 && s.trails == tr, "");
        boolean lb = s.labels;
        key(java.awt.event.KeyEvent.VK_L, false);
        boolean l1 = s.labels != lb;
        key(java.awt.event.KeyEvent.VK_L, false);
        check("Taste L: Beschriftung aus und wieder an", l1 && s.labels == lb, "");
        double rate = app.clock().rate();
        key(java.awt.event.KeyEvent.VK_ADD, false);
        double faster = app.clock().rate();
        key(java.awt.event.KeyEvent.VK_SUBTRACT, false);
        check("Tasten + und −: Tempo eine Stufe schneller und zurück", faster > rate && Math.abs(app.clock().rate() - rate) < 1e-12,
                String.format(java.util.Locale.GERMAN, "%.4f → %.4f → %.4f d/s", rate, faster, app.clock().rate()));
        key(java.awt.event.KeyEvent.VK_4, false);
        boolean ti = s.focus != null && "Titania".equals(s.focus.name);
        Thread.sleep(3000);
        // Karte beim Überfahren: Titania im Bild suchen, Maus hinstellen, 0,3 s warten
        com.dan.uranus.render.Renderer rd = app.renderer();
        double tx = -1, ty = -1;
        for (int y = 60; y < view.getHeight() - 20 && tx < 0; y += 6)
            for (int x = 10; x < view.getWidth() - 10 && tx < 0; x += 6) {
                com.dan.uranus.render.Renderer.HoverInfo h = rd.probe(x, y);
                if (h != null && "Titania".equals(h.title)) { tx = x; ty = y; }
            }
        robot.mouseMove(vo.x + (int) tx, vo.y + (int) ty);
        Thread.sleep(1200);
        com.dan.uranus.render.Renderer.HoverInfo shown = rd.hoverShown();
        ImageIO.write(robot.createScreenCapture(r), "png", new File(dir, "ui_9_karte.png"));
        check("Taste 4: Titania; Maus darauf → Karte am Zeiger", ti && shown != null && "Titania".equals(shown.title),
                shown == null ? "keine Karte bei " + tx + ", " + ty : shown.title + " · " + shown.lines.get(1));
        robot.mouseMove(vo.x + 30, vo.y + view.getHeight() - 30);
        key(java.awt.event.KeyEvent.VK_0, false);
        check("Taste 0: zurück zur Übersicht", s.focus == null, "");
        key(java.awt.event.KeyEvent.VK_F1, false);
        boolean h1 = s.help;
        Thread.sleep(600);
        ImageIO.write(robot.createScreenCapture(r), "png", new File(dir, "ui_8_hilfe.png"));
        key(java.awt.event.KeyEvent.VK_ESCAPE, false);
        check("F1 öffnet die Hilfe, Esc schließt sie", h1 && !s.help, "");
        // Standbild über die Taste S
        key(java.awt.event.KeyEvent.VK_S, false);
        File snap = null;
        for (int k = 0; k < 60 && (snap = app.lastSnapshot()) == null; k++) Thread.sleep(250);
        int want = (int) Math.round(Math.min(2.0, 3840.0 / view.getWidth()) * view.getWidth());
        BufferedImage si = snap == null ? null : ImageIO.read(snap);
        check("Taste S: Standbild in doppelter Größe gespeichert", si != null && si.getWidth() == want && snap.getParentFile().equals(bilder),
                snap == null ? "keins" : snap.getName() + " · " + si.getWidth() + " × " + si.getHeight());
        // Standbild über das Kamerasymbol
        double[] cam = com.dan.uranus.render.Renderer.iconRect(0, view.getWidth());
        File before = snap;
        click(view, cam[0] + cam[2] / 2, cam[1] + cam[3] / 2);
        File snap2 = null;
        for (int k = 0; k < 60 && ((snap2 = app.lastSnapshot()) == null || snap2.equals(before)); k++) Thread.sleep(250);
        check("Klick auf das Kamerasymbol: zweites Standbild", snap2 != null && !snap2.equals(before) && snap2.isFile(), snap2 == null ? "" : snap2.getName());
        // Einstellungen: ändern, merken, eine zweite App liest dasselbe
        key(java.awt.event.KeyEvent.VK_G, false);
        key(java.awt.event.KeyEvent.VK_T, false);
        SwingUtilities.invokeAndWait(app::saveSettings);
        com.dan.uranus.app.AppPrefs.Values mine = app.controlBar().current();
        UranusApp[] second = new UranusApp[1];
        SwingUtilities.invokeAndWait(() -> second[0] = new UranusApp());
        com.dan.uranus.app.AppPrefs.Values theirs = second[0].controlBar().current();
        Rectangle saved = second[0].prefs().load().window;
        check("Einstellungen gemerkt: ein neuer Start hat Gitterraum, Bahnen aus und dieselbe Fensterlage",
                theirs.equals(mine) && theirs.space == 1 && !theirs.trails && f.getBounds().equals(saved), saved == null ? "keine Fensterlage" : saved.toString());
        second[0].stop();
        // Hilfe über das Symbol, dann „Auf Standard zurück“
        double[] hi = com.dan.uranus.render.Renderer.iconRect(1, view.getWidth());
        click(view, hi[0] + hi[2] / 2, hi[1] + hi[3] / 2);
        boolean opened = s.help;
        double[] btn = rd.helpButton();
        for (int k = 0; k < 20 && btn == null; k++) { Thread.sleep(100); btn = rd.helpButton(); }
        if (btn != null) click(view, btn[0] + btn[2] / 2, btn[1] + btn[3] / 2);
        Thread.sleep(800);
        com.dan.uranus.app.AppPrefs.Values now = app.controlBar().current();
        check("Symbol „?“ öffnet die Hilfe; „Auf Standard zurück“ setzt alles zurück und schließt sie",
                opened && btn != null && !s.help && now.equals(new com.dan.uranus.app.AppPrefs.Values()) && s.spaceMode == 0 && s.trails
                        && new com.dan.uranus.app.AppPrefs(node).load().equals(new com.dan.uranus.app.AppPrefs.Values()), "");
        // Strg+Z in der Spielwiese
        SwingUtilities.invokeAndWait(() -> app.controlBar().togglePlay());
        Thread.sleep(6000);
        SwingUtilities.invokeAndWait(() -> { f.toFront(); view.requestFocusInWindow(); });
        Thread.sleep(300);
        int depth = s.playUndoDepth;
        double pt0 = app.renderer().playground().time();
        focusGuard();
        robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
        robot.keyPress(java.awt.event.KeyEvent.VK_Z); robot.keyRelease(java.awt.event.KeyEvent.VK_Z);
        robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
        Thread.sleep(150);
        double pt1 = app.renderer().playground().time();
        check("Strg+Z in der Spielwiese: Spielzeit springt auf einen Zwischenstand zurück", depth > 0 && pt1 < pt0,
                String.format(java.util.Locale.GERMAN, "%d Zwischenstände · %.3f → %.3f Tage", depth, pt0 / 86400, pt1 / 86400));
        SwingUtilities.invokeAndWait(() -> s.playStop = true);
        Thread.sleep(1500);
        new com.dan.uranus.app.AppPrefs(node).removeNode();
    }
}
