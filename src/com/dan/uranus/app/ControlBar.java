package com.dan.uranus.app;

import com.dan.fbutton.FButton;
import com.dan.fbutton.FButtonVariant;
import com.dan.fsegmentedcontrol.FSegmentedControl;
import com.dan.fstyle.FFontRole;
import com.dan.fstyle.FTheme;
import com.dan.ftogglebutton.FToggleButton;
import com.dan.uranus.db.UranusDao;
import com.dan.uranus.db.UranusDb;
import com.dan.uranus.model.Scenario;
import com.dan.uranus.model.SimClock;
import com.dan.uranus.model.Tour;
import com.dan.uranus.render.RenderLoop;
import com.dan.uranus.render.Renderer;
import com.dan.uranus.render.SceneSettings;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Bedienleiste unter der Szene: drei Reihen FStyle-Elemente (Darstellung, Ebenen, Zeit). */
public final class ControlBar extends JPanel {

    static final Color BG = new Color(10, 17, 26);
    static final Color LABEL = new Color(98, 121, 135);
    static final Color TEXT = new Color(200, 222, 228);
    static final Color ACCENT = new Color(143, 221, 224);

    /** Zeitraffer-Stufen in simulierten Tagen je Sekunde. */
    static final double[] RATES = {1.0 / 86_400, 1.0 / 24, 0.25, 1, 4};

    private final SceneSettings settings;
    private final SimClock clock;
    private final FButton pause;
    private final FSegmentedControl speed;
    private final JLabel status;
    private final JLabel data;
    private final Font labelFont;
    /** Knöpfe der Geschichten mit ihrem Code. */
    private final List<FButton> tourButtons = new ArrayList<>();
    private final List<String> tourCodes = new ArrayList<>();
    /** Spielwiese: Tempo in simulierten Tagen je Sekunde. */
    static final double[] PLAY_RATES = {0.1, 1, 10, 30, 100};
    private final Renderer renderer;
    private final JPanel r3, r3play;
    private final FButton playButton, playPause;
    private boolean dbReady;
    private final FSegmentedControl playSpeed;
    private boolean syncing, saving;
    /** Bedienelemente, die Tasten und gemerkte Einstellungen stellen (Phase 8). */
    private final FSegmentedControl spaceSeg, scaleSeg, moonSeg;
    private final java.util.Map<String, FToggleButton> toggles = new java.util.LinkedHashMap<>();

    public ControlBar(SceneSettings settings, SimClock clock, Renderer renderer, RenderLoop loop) {
        this.settings = settings;
        this.clock = clock;
        this.renderer = renderer;
        setBackground(BG);
        setOpaque(true);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(24, 38, 52)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        labelFont = new Font(Font.MONOSPACED, Font.PLAIN, 11);

        // ---------------- Reihe 1: Darstellung
        JPanel r1 = row();
        spaceSeg = segments(settings.spaceMode, i -> settings.spaceMode = i, "Mulde", "Gitterraum");
        r1.add(group("RAUM", spaceSeg));
        r1.add(gap());
        scaleSeg = segments(settings.scaleMode, i -> { settings.scaleMode = i; if (settings.focus == null) renderer.requestFit(); }, "Gestaucht", "Maßstabsgetreu");
        r1.add(group("MASSSTAB", scaleSeg));
        r1.add(gap());
        moonSeg = segments(settings.moonLevel, i -> { settings.moonLevel = i; if (settings.focus == null) renderer.requestFit(); }, "Hauptmonde", "+ innere", "alle 29");
        r1.add(group("MONDE", moonSeg));
        r1.add(gap());
        r1.add(button("Übersicht", FButtonVariant.GHOST, () -> { settings.tourStopRequest = settings.tourRunning != null; settings.focus = null; settings.ringFlight = false; renderer.requestFit(); }));
        r1.add(gap());
        data = new JLabel(" ");
        data.setFont(labelFont);
        data.setForeground(LABEL);
        r1.add(data);
        add(r1);

        // ---------------- Reihe 2: Ebenen
        JPanel r2 = row();
        r2.add(caption("EBENEN"));
        r2.add(toggle("Bahnen", settings.trails, b -> settings.trails = b));
        r2.add(toggle("Linse", settings.lens, b -> settings.lens = b));
        r2.add(toggle("Bloom", settings.bloom, b -> settings.bloom = b));
        r2.add(toggle("Achse", settings.axis, b -> settings.axis = b));
        r2.add(toggle("Wellen", settings.waves, b -> settings.waves = b));
        r2.add(toggle("Magnetfeld", settings.magneto, b -> settings.magneto = b));
        r2.add(toggle("Lichtbahnen", settings.geodesics, b -> settings.geodesics = b));
        r2.add(gap());
        playButton = button("Spielwiese", FButtonVariant.GHOST, this::togglePlay);
        playButton.setToolTipText("N-Körper-Rechnung ab dem echten Stand: Monde ziehen, werfen, schwerer machen");
        r2.add(playButton);
        r2.add(gap());
        status = new JLabel(" ");
        status.setFont(labelFont);
        status.setForeground(LABEL);
        r2.add(status);
        add(r2);

        // ---------------- Reihe 3: Zeit und Geschichten
        r3 = row();
        r3.add(caption("ZEIT"));
        pause = button(clock.isPaused() ? "Weiter" : "Anhalten", FButtonVariant.PRIMARY, this::togglePause);
        r3.add(pause);
        r3.add(button("Jetzt", FButtonVariant.GHOST, () -> clock.setNow()));
        r3.add(caption("1 s ≙"));
        speed = segments(2, i -> clock.setRate(RATES[i]), "1 s", "1 h", "6 h", "1 d", "4 d");
        r3.add(speed);
        r3.add(gap());
        r3.add(caption("GESCHICHTEN"));
        for (Tour t : renderer.system().tours()) {
            FButton b = button(t.title, FButtonVariant.GHOST, () -> toggleTour(t.code));
            b.setToolTipText(t.description);
            tourButtons.add(b);
            tourCodes.add(t.code);
            r3.add(b);
        }
        r3.add(gap());
        r3.add(button("ε-Ring-Flug", FButtonVariant.PRIMARY, this::startRingFlight));
        add(r3);

        // ---------------- Reihe 3 in der Spielwiese
        r3play = row();
        r3play.add(caption("SPIELWIESE"));
        for (Scenario sc : renderer.system().scenarios()) {
            if (sc.origin != Scenario.Origin.BUILTIN) continue;
            FButton b = button(shortTitle(sc), FButtonVariant.GHOST, () -> settings.playScenario = sc);
            b.setToolTipText("<html><body style='width:320px'>" + sc.description + "</body></html>");
            r3play.add(b);
        }
        FButton own = button("Eigene ▾", FButtonVariant.GHOST, null);
        own.addActionListener(e -> ownMenu(own));
        r3play.add(own);
        playSpeed = segments(1, i -> { if (!syncing) settings.playRate = PLAY_RATES[i]; }, "2,4 h", "1 d", "10 d", "30 d", "100 d");
        playSpeed.setToolTipText("Tempo: so viel Spielzeit je Sekunde");
        r3play.add(playSpeed);
        playPause = button("Anhalten", FButtonVariant.PRIMARY, this::togglePause);
        r3play.add(playPause);
        r3play.add(button("Rückgängig", FButtonVariant.GHOST, () -> settings.playUndo = true));
        r3play.add(button("Zurück", FButtonVariant.PRIMARY, () -> settings.playStop = true));
        r3play.setVisible(false);
        add(r3play);

        new Timer(500, e -> {
            status.setText(String.format(Locale.GERMAN, "%.0f Bilder/s · %.1f ms · Auflösung %.0f %%",
                    loop.fps(), loop.frameMs(), settings.renderScale * 100));
            String d = renderer.dataLabel();
            if (!d.equals(data.getText())) {
                data.setText(d);
                data.setForeground(d.contains("Datenbank DEMO") ? ACCENT : LABEL);
            }
            data.setToolTipText(renderer.dataDetail().isEmpty() ? null : renderer.dataDetail());
            refreshTourState();
            refreshPlayState();
        }).start();
    }

    /** Spielwiese ein/aus; beim Einschalten der echte Stand von jetzt. */
    public void togglePlay() {
        if (settings.playActive) settings.playStop = true;
        else { settings.tourStopRequest = settings.tourRunning != null; settings.playLive = true; }
    }

    /** Kurzname auf dem Knopf (der volle Titel steht in der Karte des Szenarios). */
    static String shortTitle(Scenario sc) {
        switch (sc.code) {
            case "HEUTE": return "Heute";
            case "TITANIA": return "Titania ×100";
            case "MIRANDA": return "Miranda";
            default: return sc.title;
        }
    }

    /** Speichern, „Jetzt“ und die eigenen Szenarien. */
    private void ownMenu(Component anchor) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem save = new JMenuItem(saving ? "Wird gespeichert …" : "Stand speichern (URA_SCENARIO)");
        save.setEnabled(dbReady && !saving);
        if (!dbReady) save.setToolTipText("Speichern braucht die Datenbank");
        save.addActionListener(e -> savePlay());
        m.add(save);
        m.addSeparator();
        JMenuItem now = new JMenuItem("Jetzt — echter Stand der Uhr");
        now.addActionListener(e -> settings.playLive = true);
        m.add(now);
        boolean any = false;
        for (Scenario sc : renderer.system().scenarios()) {
            if (sc.origin != Scenario.Origin.USER) continue;
            if (!any) { m.addSeparator(); any = true; }
            JMenuItem it = new JMenuItem(sc.title);
            it.setToolTipText(sc.description);
            it.addActionListener(e -> settings.playScenario = sc);
            m.add(it);
        }
        if (!any) { JMenuItem none = new JMenuItem("(noch keine gespeichert)"); none.setEnabled(false); m.add(none); }
        m.show(anchor, 0, anchor.getHeight());
    }

    /** Stand erzeugen lassen (Render-Thread), dann im Hintergrund über URA_API speichern. */
    public void savePlay() {
        if (saving) return;
        saving = true;
        settings.playSaved = null;
        settings.playSaveRequest = true;
        Thread t = new Thread(() -> {
            Scenario sc = null;
            for (int k = 0; k < 100 && (sc = settings.playSaved) == null; k++) {
                try { Thread.sleep(20); } catch (InterruptedException ex) { return; }
            }
            if (sc == null) { saving = false; return; }
            settings.playSaved = null;
            List<Scenario> list = new ArrayList<>();
            String err = UranusDao.saveAndReload(UranusDb.fromProperties(), sc, list);
            if (err == null) {
                renderer.setSystem(renderer.system().withScenarios(list), renderer.dataLabel(), renderer.dataDetail());
                renderer.note("Gespeichert: " + sc.title + " (" + sc.items().size() + " Körper) — unter „Eigene ▾“");
            } else renderer.note("Nicht gespeichert: " + err);
            saving = false;
        }, "uranus-save");
        t.setDaemon(true);
        t.start();
    }

    /** Anhalten/Weiter: während einer Geschichte hält es die Fahrt an, sonst die Uhr. */
    public void togglePause() {
        if (settings.playActive) {
            settings.playPaused = !settings.playPaused;
            playPause.setText(settings.playPaused ? "Weiter" : "Anhalten");
            return;
        }
        if (settings.tourRunning != null) {
            settings.tourPauseRequest = true;
            pause.setText(settings.tourPaused ? "Anhalten" : "Weiter");     // Render-Thread bestätigt beim nächsten Takt
            return;
        }
        clock.setPaused(!clock.isPaused());
        pause.setText(clock.isPaused() ? "Weiter" : "Anhalten");
    }

    /** Startet eine Geschichte; derselbe Knopf während sie läuft beendet sie. */
    public void toggleTour(String code) {
        if (code.equals(settings.tourRunning)) { settings.tourStopRequest = true; return; }
        settings.tourRequest = code;
    }

    /** Reihe 3 wechselt mit der Spielwiese; Tempo und Knöpfe folgen dem Render-Thread. */
    private void refreshPlayState() {
        boolean on = settings.playActive;
        if (r3play.isVisible() != on) {
            r3play.setVisible(on);
            r3.setVisible(!on);
            revalidate();
            repaint();
        }
        FButtonVariant v = on ? FButtonVariant.PRIMARY : FButtonVariant.GHOST;
        if (playButton.getVariant() != v) playButton.setVariant(v);
        if (!on) return;
        int best = -1;
        for (int i = 0; i < PLAY_RATES.length; i++) if (Math.abs(PLAY_RATES[i] - settings.playRate) < 1e-9) best = i;
        if (best >= 0 && best != playSpeed.getSelectedIndex()) { syncing = true; playSpeed.setSelectedIndex(best); syncing = false; }
        String t = settings.playPaused ? "Weiter" : "Anhalten";
        if (!t.equals(playPause.getText())) playPause.setText(t);
        dbReady = renderer.dataLabel().contains("Datenbank DEMO");
    }

    /** Knopf der laufenden Geschichte hervorheben, Pausenknopf nachführen. */
    private void refreshTourState() {
        String run = settings.tourRunning;
        for (int i = 0; i < tourButtons.size(); i++) {
            FButtonVariant v = tourCodes.get(i).equals(run) ? FButtonVariant.PRIMARY : FButtonVariant.GHOST;
            if (tourButtons.get(i).getVariant() != v) tourButtons.get(i).setVariant(v);
        }
        boolean paused = run != null ? settings.tourPaused : clock.isPaused();
        String t = paused ? "Weiter" : "Anhalten";
        if (!t.equals(pause.getText())) pause.setText(t);
    }

    /** Flug durch den ε-Ring: Echtzeit, damit der Ring mit der Kamera mitläuft; Esc oder „Übersicht“ beendet ihn. */
    public void startRingFlight() {
        if (settings.tourRunning != null) settings.tourStopRequest = true;
        settings.focus = null;
        speed.setSelectedIndex(0);
        clock.setRate(RATES[0]);
        if (clock.isPaused()) togglePause();
        settings.ringFlight = true;
    }

    // ------------------------------------------------------------ Tasten und gemerkte Einstellungen (Phase 8)

    /** Stellt die Bedienelemente (und damit die Szene) auf die Werte v; Fenster stellt die App. */
    public void apply(AppPrefs.Values v) {
        spaceSeg.setSelectedIndex(v.space);
        scaleSeg.setSelectedIndex(v.scale);
        moonSeg.setSelectedIndex(v.moons);
        speed.setSelectedIndex(v.speed);
        clock.setRate(RATES[v.speed]);
        syncing = true; playSpeed.setSelectedIndex(v.playSpeed); syncing = false;
        if (!settings.playActive) settings.playRate = PLAY_RATES[v.playSpeed];
        setToggle("Bahnen", v.trails);
        setToggle("Linse", v.lens);
        setToggle("Bloom", v.bloom);
        setToggle("Achse", v.axis);
        setToggle("Wellen", v.waves);
        setToggle("Magnetfeld", v.magneto);
        setToggle("Lichtbahnen", v.geodesics);
        settings.labels = v.labels;
    }

    /** Der jetzige Stand der Bedienelemente (ohne Fenster). */
    public AppPrefs.Values current() {
        AppPrefs.Values v = new AppPrefs.Values();
        v.space = spaceSeg.getSelectedIndex();
        v.scale = scaleSeg.getSelectedIndex();
        v.moons = moonSeg.getSelectedIndex();
        v.speed = Math.max(0, speed.getSelectedIndex());
        v.playSpeed = Math.max(0, playSpeed.getSelectedIndex());
        v.trails = toggles.get("Bahnen").isSelected();
        v.lens = toggles.get("Linse").isSelected();
        v.bloom = toggles.get("Bloom").isSelected();
        v.axis = toggles.get("Achse").isSelected();
        v.waves = toggles.get("Wellen").isSelected();
        v.magneto = toggles.get("Magnetfeld").isSelected();
        v.geodesics = toggles.get("Lichtbahnen").isSelected();
        v.labels = settings.labels;
        return v;
    }

    private void setToggle(String name, boolean on) {
        FToggleButton t = toggles.get(name);
        if (t != null && t.isSelected() != on) t.setSelected(on);
    }

    /** Tempo eine Stufe schneller (+1) oder langsamer (−1); in der Spielwiese deren Stufen. */
    public void stepSpeed(int dir) {
        if (settings.playActive) {
            int i = Math.max(0, Math.min(PLAY_RATES.length - 1, playSpeed.getSelectedIndex() + dir));
            playSpeed.setSelectedIndex(i);
            settings.playRate = PLAY_RATES[i];
            renderer.note("Tempo der Spielwiese: " + playSpeedLabel(i) + " je Sekunde");
            return;
        }
        int i = Math.max(0, Math.min(RATES.length - 1, speed.getSelectedIndex() + dir));
        speed.setSelectedIndex(i);
        clock.setRate(RATES[i]);
        renderer.note("Tempo: 1 s ≙ " + new String[]{"1 s", "1 h", "6 h", "1 d", "4 d"}[i]);
    }

    private static String playSpeedLabel(int i) { return new String[]{"2,4 h", "1 d", "10 d", "30 d", "100 d"}[i]; }

    /** G: Mulde ↔ Gitterraum. */
    public void toggleSpace() { spaceSeg.setSelectedIndex(1 - spaceSeg.getSelectedIndex()); renderer.note(spaceSeg.getSelectedIndex() == 0 ? "Raum: Mulde" : "Raum: Gitterraum"); }

    /** T: Bahnen ein/aus. */
    public void toggleTrails() {
        boolean on = !toggles.get("Bahnen").isSelected();
        setToggle("Bahnen", on);
        renderer.note(on ? "Bahnen an" : "Bahnen aus");
    }

    // ------------------------------------------------------------ Bausteine

    private JPanel row() {
        // WrapLayout statt FlowLayout: bricht bei schmalem Fenster sauber um, statt abzuschneiden
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JLabel caption(String s) {
        JLabel l = new JLabel(s);
        l.setFont(labelFont);
        l.setForeground(LABEL);
        return l;
    }

    /** Beschriftung und Element bleiben beim Umbrechen zusammen. */
    private JPanel group(String caption, JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        p.add(caption(caption));
        p.add(c);
        return p;
    }

    private Component gap() { return Box.createHorizontalStrut(10); }

    private FSegmentedControl segments(int selected, IntConsumer onChange, String... items) {
        FSegmentedControl c = new FSegmentedControl(items);
        // FSegmentedControl hat eine feste Vorzugsbreite (240 px) und schneidet längere Einträge ab:
        // Segmentbreite nach dem längsten Eintrag in der FTheme-Knopfschrift setzen und die Breite mitführen
        Font f = FTheme.getInstance().getFont(FFontRole.BUTTON);
        java.awt.FontMetrics fm = c.getFontMetrics(f);
        int wmax = 0;
        for (String it : items) wmax = Math.max(wmax, fm.stringWidth(it));
        int seg = wmax + (items.length >= 5 && wmax < 60 ? 20 : 30);
        c.setFixedSegmentWidth(seg);
        c.setPreferredSize(new Dimension(seg * items.length + 12, c.getPreferredSize().height));
        c.setSelectedIndex(selected);
        c.getModel().addPropertyChangeListener(e -> onChange.accept(c.getSelectedIndex()));
        return c;
    }

    private JComponent toggle(String text, boolean on, Consumer<Boolean> onChange) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        FToggleButton t = new FToggleButton(on);
        toggles.put(text, t);
        t.getModel().addPropertyChangeListener(e -> onChange.accept(t.isSelected()));
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        l.setFont(l.getFont().deriveFont(12f));
        p.add(t);
        p.add(l);
        return p;
    }

    private FButton button(String text, FButtonVariant v, Runnable r) {
        FButton b = new FButton(text);
        b.setVariant(v);
        if (r != null) b.addActionListener(e -> r.run());
        return b;
    }

    @Override
    public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
}
