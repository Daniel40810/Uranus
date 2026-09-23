package com.dan.uranus.render;

import com.dan.uranus.model.Body;
import com.dan.uranus.model.BodyGroup;
import com.dan.uranus.model.Craft;
import com.dan.uranus.model.OrbitElements;
import com.dan.uranus.model.OrbitQuality;
import com.dan.uranus.model.Ring;
import com.dan.uranus.model.Scenario;
import com.dan.uranus.model.SimClock;
import com.dan.uranus.model.StoryEvent;
import com.dan.uranus.model.Tour;
import com.dan.uranus.model.UranusSystem;
import com.dan.uranus.physics.CurvatureField;
import com.dan.uranus.physics.Ephemeris;
import com.dan.uranus.physics.Kepler;
import com.dan.uranus.physics.SpaceMap;
import com.dan.uranus.physics.TimeScale;
import com.dan.uranus.play.PlayBody;
import com.dan.uranus.play.PlayEvent;
import com.dan.uranus.play.Playground;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Setzt ein Bild zusammen: Himmel mit Linse → Raum (hinten) → Monde hinter Uranus →
 * Ringe (hinten) → Uranus → Ringe/Raum (vorn) → Monde davor → Bloom und Tonwerte →
 * Beschriftung.
 * <p>
 * Läuft ausschließlich im Render-Thread. Die fertigen Bilder werden abwechselnd in zwei
 * Puffer geschrieben, damit die Oberfläche immer ein vollständiges Bild zeichnet.
 */
public final class Renderer {

    /** Ergebnis eines Bildes, für Anzeige und Mausauswahl. */
    public static final class Frame {
        public final BufferedImage image;
        public final List<Pick> picks;
        public final double renderMs;
        /** Beschriftung in Fensterkoordinaten. */
        public final Overlay overlay;
        /** Fenster / Renderbild. */
        public final double toView;
        public Frame(BufferedImage image, List<Pick> picks, double renderMs, Overlay overlay, double toView) {
            this.image = image; this.picks = picks; this.renderMs = renderMs; this.overlay = overlay; this.toView = toView;
        }

        /** Bild in Fenstergröße mit Beschriftung (für Standbilder). */
        public BufferedImage composite() { return composite(1.0); }

        /** Bild mit Beschriftung, um scale vergrößert (Standbild in doppelter Größe: Schrift bleibt scharf). */
        public BufferedImage composite(double scale) {
            int w = (int) Math.round(image.getWidth() * toView * scale), h = (int) Math.round(image.getHeight() * toView * scale);
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, w, h, null);
            g.scale(scale, scale);
            overlay.paint(g);
            g.dispose();
            return out;
        }
    }

    /** Anklickbarer Mond in Bildkoordinaten (des gerenderten Bildes). */
    public static final class Pick {
        public final Body body; public final double x, y, r;
        Pick(Body body, double x, double y, double r) { this.body = body; this.x = x; this.y = y; this.r = r; }
    }

    static final boolean PROFILE = Boolean.getBoolean("uranus.profile");

    private UranusSystem system;
    /** Neuer Datensatz aus einem anderen Thread (Datenbank); übernommen am Anfang des nächsten Bildes. */
    private volatile UranusSystem pending;
    /** Beim Wechsel gleiten die Monde von der alten auf die neue Lage. */
    private Map<String, Body> glideFrom;
    private double glideStart = -1;
    static final double GLIDE_SECONDS = 1.8;
    private volatile String dataLabel = "Daten: eingebaut", dataDetail = "";
    private final SceneSettings settings;
    private final SimClock clock;
    private final Camera camera = new Camera();
    private final SkyLayer sky = new SkyLayer(1781);
    private final PlanetLayer planet = new PlanetLayer();
    private final MoonLayer moonLayer = new MoonLayer();
    private final PostFx post = new PostFx();
    private final CurvatureField field = new CurvatureField();
    private final SegmentList back = new SegmentList(), front = new SegmentList();
    private final PointList backPts = new PointList(), frontPts = new PointList();
    private final MagnetoLayer magneto = new MagnetoLayer();
    private final GeodesicLayer geodesics = new GeodesicLayer();
    private final RingFlight flight = new RingFlight();
    private final TourPlayer player = new TourPlayer();
    // Spielwiese (Phase 7)
    private Playground play;
    private final PlayLayer playLayer = new PlayLayer();
    private final Map<Integer, Body> guestBodies = new HashMap<>();
    private double playJd0, playRate0, playSnapAt, playIntroUntil;
    private boolean playPaused0, playThrottled, playAdoptLater;
    private Map<String, double[]> glideKm;
    private double lastToView = 1;
    private int dragId = -1;
    private boolean dragGuest;
    private double[] dragKm, dragNormal, guestStartKm;
    private final java.util.ArrayDeque<double[]> dragHist = new java.util.ArrayDeque<>();
    private double guestMassKg = 1e22, dragSx, dragSy;
    private String dragText = "";
    private volatile String note;
    /** Standbild: ohne Symbole, Hilfe, Karte am Zeiger und Bildzeit. */
    private boolean clean;
    /** Stellen auf den Zeitleisten, die beim Überfahren eine Karte zeigen (je Bild neu). */
    private final List<Spot> spots = new ArrayList<>();
    private double hoverMx = -1, hoverMy = -1;
    private long hoverSince;
    private volatile HoverInfo hoverShown;
    /** Knopf „Auf Standard zurück“ der Hilfetafel in Fensterkoordinaten; null = Tafel zu. */
    private volatile double[] helpButton;
    /** Sichtbarkeit je Ring (Entdeckungsgeschichte), parallel zu system.rings(). */
    private float[] ringReveal = new float[0];
    private boolean snapReveal;
    private double hideT;
    // Sonde (Voyager 2) in diesem Bild
    private Craft craft;
    private boolean craftOn;
    private final double[] craftW = new double[3], craftKm = new double[3];
    // Jahreszeitenstreifen: Breite des Sonnenstands, einmal je Fahrt berechnet
    private String seasonKey;
    private double[] seasonLat;
    private double seasonJd0, seasonJd1;
    private final Scene3D scene = new Scene3D() {
        @Override public void seg(double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b, float a, boolean add) {
            Renderer.this.seg(x0, y0, z0, x1, y1, z1, r, g, b, a, add);
        }
        @Override public void glow(double x, double y, double z, float r, float g, float b, double radiusPx) { Renderer.this.glowAt(x, y, z, r, g, b, radiusPx); }
        @Override public Camera camera() { return camera; }
        @Override public double ucY() { return ucY; }
        @Override public double scaleT() { return scaleT; }
        @Override public double[] sun() { return sun; }
        @Override public double time() { return realTime; }
    };
    private List<Body> bodies;
    private MoonLayer.Instance[] inst;
    private double[] prevSin;

    private Hdr hdr;
    private final BufferedImage[] images = new BufferedImage[2];
    private int flip;

    // weich nachgeführte Übergänge
    private double modeT, scaleT, innerT, irrT, axisT, lensT = 1, trailsT = 1, extent = 16;
    private double magT = 1, geoT, flightT;
    private double spin, spinStep;
    private int flightParticles;
    private boolean fitRequested = true;
    private Body lastFocus;
    private double realTime;

    // Bildgrößen dieses Bildes
    private final double[] sun = new double[3];
    private double ucY, planetSx, planetSy, planetDepth, planetRs;
    private double[] eclipticNormal;
    private String caption = "";
    private double captionUntil;

    private final double[] tmp = new double[3], tmp2 = new double[3], km = new double[3];
    private final Font serifBig, serifMid, serifSmall, mono, monoSmall, body;

    public Renderer(UranusSystem system, SceneSettings settings, SimClock clock) {
        this.system = system;
        this.settings = settings;
        this.clock = clock;
        adoptBodies(system);
        planet.setRings(system.rings());
        ringReveal = ones(system.rings().size());
        dataLabel = "Daten: " + system.origin;
        dataDetail = system.origin;
        double[] ep = new double[3];
        Ephemeris.eclipticToIcrf(0, 0, 1, ep);
        eclipticNormal = new double[3];
        Ephemeris.icrfToWorld(ep, eclipticNormal);
        String serif = pickFont("Georgia", "Cambria", "Palatino Linotype", "Serif");
        serifBig = new Font(serif, Font.ITALIC, 24);
        serifMid = new Font(serif, Font.ITALIC, 17);
        serifSmall = new Font(serif, Font.ITALIC, 13);
        body = new Font(serif, Font.PLAIN, 15);
        String m = pickFont("Consolas", "DejaVu Sans Mono", "Monospaced");
        mono = new Font(m, Font.PLAIN, 12);
        monoSmall = new Font(m, Font.PLAIN, 11);
    }

    private static String pickFont(String... names) {
        Set<String> have = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) if (have.contains(n)) return n;
        return names[names.length - 1];
    }

    public Camera camera() { return camera; }

    /** Kamera beim nächsten Bild auf die sichtbaren Monde einpassen. */
    public void requestFit() { fitRequested = true; }

    /**
     * Übergibt einen neuen Datensatz (etwa aus der Datenbank). Darf aus jedem Thread gerufen werden;
     * der Render-Thread übernimmt ihn am Anfang des nächsten Bildes, die Monde gleiten dabei von
     * ihrer alten auf die neue Lage.
     */
    public void setSystem(UranusSystem s, String label, String detail) {
        dataLabel = label;
        dataDetail = detail;
        pending = s;
    }

    /** Nur die Beschriftung ändern (etwa: Datenbank nicht erreichbar, eingebauter Satz bleibt). */
    public void setDataLabel(String label, String detail) { dataLabel = label; dataDetail = detail; }

    /** Weltlage (x, z) eines Mondes im letzten Bild — für Prüfungen. */
    public double[] worldOf(String name) {
        for (MoonLayer.Instance m : inst) if (m.body.name.equals(name)) return new double[]{m.x, m.z};
        return null;
    }

    /** Herkunft des Datensatzes für die Statuszeile, kurz und ausführlich. */
    public String dataLabel() { return dataLabel; }
    public String dataDetail() { return dataDetail; }

    public UranusSystem system() { return system; }

    /** Die laufende Spielwiese oder null (Render-Thread, Prüfungen). */
    public Playground playground() { return play; }

    /** Hinweis unten rechts (aus jedem Thread), etwa „Gespeichert“. */
    public void note(String text) { note = text; }

    /** Laufende Kamerafahrt oder null (Render-Thread, Prüfungen). */
    public Tour activeTour() { return player.tour(); }

    /** Sekunden seit Beginn der laufenden Fahrt. */
    public double tourTime() { return player.time(); }

    /** Erzählzeit der Entdeckungsgeschichte als Jahr, sonst NaN. */
    public double storyYear() {
        double jd = player.storyJd();
        return Double.isNaN(jd) ? Double.NaN : 2000 + (jd - SimClock.JD_J2000) / 365.25;
    }

    /** Sichtbarkeit (0..1) eines Mondes oder Rings nach der Entdeckungsgeschichte; NaN, wenn unbekannt. */
    public double revealOf(String name) {
        for (MoonLayer.Instance m : inst) if (m.body.name.equals(name)) return m.reveal;
        List<Ring> rs = system.rings();
        for (int i = 0; i < rs.size() && i < ringReveal.length; i++) if (rs.get(i).name.equals(name)) return ringReveal[i];
        return Double.NaN;
    }

    /** Weltlage der Sonde im letzten Bild oder null, wenn keine Sonde in der Nähe ist. */
    public double[] craftWorld() { return craftOn ? craftW.clone() : null; }

    private static float[] ones(int n) { float[] f = new float[n]; Arrays.fill(f, 1f); return f; }

    private void adoptBodies(UranusSystem s) {
        bodies = new ArrayList<>(s.bodies());
        MoonLayer.Instance[] old = inst;
        inst = new MoonLayer.Instance[bodies.size()];
        prevSin = new double[bodies.size()];
        Arrays.fill(prevSin, Double.NaN);
        Map<String, MoonLayer.Instance> byName = new HashMap<>();
        if (old != null) for (MoonLayer.Instance m : old) byName.put(m.body.name, m);
        for (int i = 0; i < inst.length; i++) {
            MoonLayer.Instance m = byName.get(bodies.get(i).name);
            inst[i] = m != null ? m : new MoonLayer.Instance();       // Zustand (Fade, Radius) bleibt erhalten
            inst[i].body = bodies.get(i);
        }
    }

    /** Übernimmt einen wartenden Datensatz (nur im Render-Thread). */
    private void takePending() {
        UranusSystem s = pending;
        if (s == null) return;
        pending = null;
        if (play != null) {                                                  // während der Spielwiese nur merken
            system = s;
            playAdoptLater = true;
            return;
        }
        glideFrom = new HashMap<>();
        for (Body b : system.bodies()) glideFrom.put(b.name, b);
        glideStart = realTime;
        system = s;
        adoptBodies(s);
        planet.setRings(s.rings());
        ringReveal = ones(s.rings().size());
        snapReveal = true;
        // Auswahl und Fokus zeigen auf die neuen Objekte gleichen Namens
        if (settings.focus != null) settings.focus = s.find(settings.focus.name);
        if (settings.hover != null) settings.hover = s.find(settings.hover.name);
        lastFocus = settings.focus;
    }

    /** Setzt alle Übergänge sofort auf ihre Zielwerte (Standbilder, Prüfungen). */
    public void settle() {
        modeT = settings.spaceMode; scaleT = settings.scaleMode;
        innerT = settings.moonLevel >= 1 ? 1 : 0; irrT = settings.moonLevel >= 2 ? 1 : 0;
        axisT = settings.axis ? 1 : 0; lensT = settings.lens ? 1 : 0; trailsT = settings.trails ? 1 : 0;
        magT = settings.magneto ? 1 : 0; geoT = settings.geodesics ? 1 : 0; flightT = settings.ringFlight ? 1 : 0;
        hideT = player.moonsHidden ? 1 : 0;
        snapReveal = true;
        computeWorld(0);
        extent = targetExtent();
        fit();
        camera.snap();
    }

    // ================================================================== Bild

    public Frame render(int w, int h, double dt) { return render(w, h, dt, 1.0); }

    /** @param toView Verhältnis Fenstergröße / Renderbild (Beschriftung bleibt scharf) */
    public Frame render(int w, int h, double dt, double toView) {
        long t0 = System.nanoTime();
        lastToView = toView;
        realTime += dt;
        if (hdr == null || hdr.w != w || hdr.h != h) {
            hdr = new Hdr(w, h);
            images[0] = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            images[1] = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }
        takePending();
        tourStep(dt);
        playStep(dt);
        step(dt);
        computeWorld(dt);
        cameraStep(dt, w, h);

        // Uranus im Bild
        if (camera.project(0, ucY, 0, tmp)) { planetSx = tmp[0]; planetSy = tmp[1]; planetDepth = tmp[2]; planetRs = camera.foc * SpaceMap.PR / tmp[2]; }
        else { planetSx = -1e5; planetSy = -1e5; planetDepth = -1; planetRs = 0; }
        for (MoonLayer.Instance m : inst) projectMoon(m);

        long tA = System.nanoTime();
        // 1) Himmel mit Linse
        double thetaE = planetDepth > 0 ? Math.min(lensT * 1.25 * planetRs, Math.min(w, h) * 0.6) : 0;
        sky.render(hdr, camera, planetSx, planetSy, thetaE, sun);

        long tB = System.nanoTime();
        // 2) Linien und Leuchtpunkte sammeln
        back.clear(); front.clear(); backPts.clear(); frontPts.clear();
        double calm = 1 - flightT;                    // beim Ringflug treten Raum und Bahnen zurück
        if (modeT < 0.995 && calm > 0.01) buildSheet((1 - modeT) * calm);
        if (modeT > 0.005 && calm > 0.01) buildLattice(modeT * calm);
        if (play != null) playLayer.build(scene, this::playWorld, play, trailsT * calm, play.compareEphemeris, realTime);
        else if (trailsT > 0.01 && calm > 0.01) buildOrbits(trailsT * calm);
        buildRings();
        if (craftOn) buildCraft();
        if (axisT > 0.01) buildAxis(axisT);
        magneto.build(scene, spin, spinStep, magT * (1 - 0.4 * flightT), dt);
        geodesics.build(scene, SpaceMap.PR, geoT * calm);

        long tC = System.nanoTime();
        // 3) hinten → Uranus → vorn
        setShadowCasters();
        back.draw(hdr);
        backPts.draw(hdr);
        double phC = shepherdAngle("Cordelia"), phO = shepherdAngle("Ophelia");
        RingFlight.shape(epsilon(), TimeScale.tdb(clock.jd()));
        flightParticles = flight.prepare(camera, clock.daysSinceJ2000(), ucY, sun, planetDepth, flightT, phC, phO);
        flight.draw(hdr, camera, sun, flightT, true);
        Body hl = settings.hover != null ? settings.hover : settings.focus;
        for (MoonLayer.Instance m : sortedMoons(true)) moonLayer.render(hdr, camera, m, m.body == hl);
        planet.setPlanet(0, ucY, 0, SpaceMap.PR);
        planet.setSun(sun);
        planet.setRotation(spin, clock.daysSinceJ2000());
        planet.setRingPhase(TimeScale.tdb(clock.jd()));
        planet.setRingFade(ringReveal);
        double[] mm = new double[3], me1 = new double[3], me2 = new double[3];
        MagnetoLayer.axis(spin, mm, me1, me2);
        planet.setAurora(mm, me1, me2, magT, realTime);
        planet.setRotationBlur((Math.abs(spinStep) - 0.15) / 0.6);
        long tD = System.nanoTime();
        planet.render(hdr, camera);
        long tE = System.nanoTime();
        front.draw(hdr);
        frontPts.draw(hdr);
        flight.draw(hdr, camera, sun, flightT, false);
        for (MoonLayer.Instance m : sortedMoons(false)) moonLayer.render(hdr, camera, m, m.body == hl);

        long tF = System.nanoTime();
        // 4) Nachbearbeitung
        BufferedImage img = images[flip];
        flip ^= 1;
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        post.apply(hdr, px, settings.bloom);

        long tG = System.nanoTime();
        if (PROFILE) System.out.printf(Locale.ROOT, "welt %.1f  himmel %.1f  linien %.1f  hinten %.1f  planet %.1f  vorn %.1f  post %.1f%n",
                (tA - t0) / 1e6, (tB - tA) / 1e6, (tC - tB) / 1e6, (tD - tC) / 1e6, (tE - tD) / 1e6, (tF - tE) / 1e6, (tG - tF) / 1e6);
        // 5) Beschriftung
        List<Pick> picks = new ArrayList<>();
        for (MoonLayer.Instance m : inst) if (m.onScreen && m.fade > 0.3f) picks.add(new Pick(m.body, m.sx, m.sy, m.sr));
        double ms = (System.nanoTime() - t0) / 1e6;
        Overlay ov = overlay(w, h, toView, ms);
        return new Frame(img, picks, (System.nanoTime() - t0) / 1e6, ov, toView);
    }

    // ================================================================== Zustand

    // ================================================================== Spielwiese

    /** Anfragen übernehmen, Eingaben abarbeiten, rechnen, Uhr nachführen (vor step()). */
    private void playStep(double dt) {
        if (settings.playStop) { settings.playStop = false; stopPlay(); }
        Scenario sc = settings.playScenario;
        boolean live = settings.playLive;
        if (sc != null || live) {
            settings.playScenario = null;
            settings.playLive = false;
            startPlay(sc);
        }
        if (play == null) { settings.playActive = false; return; }
        if (settings.playUndo) {
            settings.playUndo = false;
            dragId = -1; dragGuest = false; playLayer.prediction = null; playLayer.arrowFrom = playLayer.arrowTo = null;
            if (play.undo()) playLayer.afterUndo(play);
        }
        if (settings.playSaveRequest) { settings.playSaveRequest = false; settings.playSaved = savedScenario(); }
        handlePlayInput();
        boolean holding = dragId >= 0 || dragGuest;
        playThrottled = false;
        if (!settings.playPaused && !holding) {
            playThrottled = !play.advance(settings.playRate * 86_400 * dt, 10_000_000L);
            if (realTime - playSnapAt > 2) { play.snapshot(); playSnapAt = realTime; }
        }
        clock.setJd(play.jdUtc());
        playLayer.record(play, realTime);
        syncPlayInstances();
        Body f = settings.focus;
        playLayer.selectedIndex = -1;
        if (f != null) {
            MoonLayer.Instance m = null;
            for (MoonLayer.Instance x : inst) if (x.body == f) m = x;
            if (m == null) settings.focus = null;                            // verschmolzen, zerbrochen, entkommen
            else playLayer.selectedIndex = play.indexOf(m.playId);
        }
        settings.playActive = true;
        settings.playTitle = play.title;
        settings.playUndoDepth = play.undoDepth();
    }

    private void startPlay(Scenario sc) {
        if (play == null) { playJd0 = clock.jd(); playRate0 = clock.rate(); playPaused0 = clock.isPaused(); }
        if (player.active()) player.stop(settings, clock);
        settings.tourRequest = null;
        settings.ringFlight = false;
        settings.focus = null;
        play = sc == null
                ? Playground.fromSystem(system, clock.jd(), false, true, "JETZT", "Jetzt",
                "Der echte Stand zu diesem Zeitpunkt: die Hauptmonde mit Masse, die Irregulären als Testkörper. Ziehen verschiebt einen Mond, "
                        + "das Mausrad ändert seine Masse, Umschalt und Ziehen setzt einen Gast.")
                : Playground.fromScenario(sc, system);
        settings.playRate = sc == null ? 1 : sc.rateDaysPerS;
        settings.playPaused = false;
        for (PlayBody b : play.bodies())
            if (b.source != null && b.source.group == BodyGroup.INNER && settings.moonLevel < 1) settings.moonLevel = 1;
        clock.setPaused(true);
        playLayer.reset();
        play.onStep = playLayer::sample;
        guestBodies.clear();
        playSnapAt = realTime;
        playIntroUntil = realTime + 12;
        dragId = -1; dragGuest = false;
        camera.setAutoSpin(0);
        syncPlayInstances();
        fitRequested = true;
    }

    private void stopPlay() {
        if (play == null) return;
        glideKm = new HashMap<>();
        double[] r = new double[3];
        for (int i = 1; i < play.size(); i++) {
            PlayBody b = play.body(i);
            if (b.source == null) continue;
            play.position(i, r);
            glideKm.put(b.source.name, r.clone());
        }
        play = null;
        clock.setJd(playJd0);
        clock.setRate(playRate0);
        clock.setPaused(playPaused0);
        adoptBodies(system);
        if (playAdoptLater) { planet.setRings(system.rings()); ringReveal = ones(system.rings().size()); playAdoptLater = false; }
        glideStart = realTime;
        camera.setAutoSpin(0.03);
        settings.focus = null;
        settings.playActive = false;
        dragId = -1; dragGuest = false;
        playLayer.reset();
        fitRequested = true;
    }

    /** Instanzen an die Körper der Spielwiese anpassen (Verschmelzen, Gäste, Zerbrechen). */
    private void syncPlayInstances() {
        int n = play.size() - 1;
        boolean same = inst != null && inst.length == n;
        if (same) for (int i = 0; i < n; i++) if (inst[i].playId != play.body(i + 1).id) { same = false; break; }
        if (!same) {
            Map<Integer, MoonLayer.Instance> old = new HashMap<>();
            if (inst != null) for (MoonLayer.Instance m : inst) if (m.playId >= 0) old.put(m.playId, m);
            Map<String, MoonLayer.Instance> byName = new HashMap<>();
            if (inst != null) for (MoonLayer.Instance m : inst) byName.put(m.body.name, m);
            MoonLayer.Instance[] ni = new MoonLayer.Instance[n];
            List<Body> nb = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                PlayBody b = play.body(i + 1);
                MoonLayer.Instance m = old.get(b.id);
                if (m == null) m = byName.get(b.name);
                if (m == null) m = new MoonLayer.Instance();
                m.playId = b.id;
                m.body = bodyFor(b);
                m.reveal = 1f;
                ni[i] = m;
                nb.add(m.body);
            }
            inst = ni;
            bodies = nb;
            prevSin = new double[n];
            Arrays.fill(prevSin, Double.NaN);
        }
        for (int i = 0; i < n; i++) {
            PlayBody b = play.body(i + 1);
            inst[i].playMassKg = b.massive() ? b.massKg() : 0;
            inst[i].playRadiusKm = b.radiusKm;
        }
    }

    /** Der echte Mond hinter einem Körper, oder ein Aussehen für Gäste. */
    private Body bodyFor(PlayBody b) {
        if (b.source != null) return b.source;
        return guestBodies.computeIfAbsent(b.id, k -> new Body(b.name, 0, BodyGroup.MAJOR, b.radiusKm, b.massKg(), 0.35,
                0.86f, 0.70f, 0.52f, Body.Surface.CRATERED, 0, "", "Gast der Spielwiese",
                new com.dan.uranus.model.OrbitElements(com.dan.uranus.model.OrbitElements.Frame.EQUATORIAL, SimClock.JD_J2000, 1e5, 0, 0, 0, 0, 0,
                        UranusSystem.periodFromA(1e5), 0, 0, Double.NaN, Double.NaN), OrbitQuality.MEAN, "Spielwiese", Double.NaN));
    }

    /** km relativ zu Uranus → Welt (wie die Monde: gestaucht oder echt, in der Mulde auf der Fläche). */
    private void playWorld(double[] kmIn, double radius, double[] out) {
        SpaceMap.toWorld(kmIn, scaleT, out);
        out[1] = heightOf(out[0], out[2], out[1], radius, false);
    }

    /** Umlaufzeit, wie die Bahn gerade ist, für die Beschriftung. */
    private String playPeriod(MoonLayer.Instance m) {
        int i = play == null ? -1 : play.indexOf(m.playId);
        if (i <= 0) return "";
        double[] el = play.elements(i);
        return el[0] > 0 ? String.format(Locale.GERMAN, "%.2f d", el[3]) : "ungebunden";
    }

    /** Stand zum Speichern (die Oberfläche speichert im Hintergrund über URA_API). */
    private Scenario savedScenario() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String code = "EIGEN_" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String title = "Eigen · " + now.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"));
        String desc = String.format(Locale.GERMAN, "Gespeichert aus „%s“ am %s (Spielzeit).", play.title,
                SimClock.instantOf(play.jdUtc()).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("d. MMMM yyyy, HH:mm 'UTC'", Locale.GERMAN)));
        return play.toScenario(code, title, desc, Scenario.Origin.USER, settings.playRate);
    }

    // ------------------------------------------------------------ Eingaben

    private void handlePlayInput() {
        SceneSettings.PlayInput in;
        while ((in = settings.playInput.poll()) != null) {
            double px = in.x / lastToView, py = in.y / lastToView;
            switch (in.kind) {
                case PRESS:
                    if (in.shift) {
                        double[] k = rayToKm(px, py, new double[]{0, -1, 0});
                        if (k != null) { dragGuest = true; guestStartKm = k; dragKm = k; dragNormal = new double[]{0, -1, 0}; }
                    } else {
                        MoonLayer.Instance m = pickPlay(px, py);
                        int i = m == null ? -1 : play.indexOf(m.playId);
                        if (i > 0) {
                            double[] el = new double[3], v = new double[3];
                            play.position(i, el);
                            play.velocity(i, v);
                            double hx = el[1] * v[2] - el[2] * v[1], hy = el[2] * v[0] - el[0] * v[2], hz = el[0] * v[1] - el[1] * v[0];
                            double hn = Math.sqrt(hx * hx + hy * hy + hz * hz);
                            dragNormal = hn > 0 ? new double[]{hx / hn, hy / hn, hz / hn} : new double[]{0, -1, 0};
                            dragId = m.playId;
                            dragKm = el;
                            dragHist.clear();
                        }
                    }
                    dragSx = in.x; dragSy = in.y;
                    break;
                case DRAG:
                    if (dragId < 0 && !dragGuest) break;
                    double[] k = rayToKm(px, py, dragNormal);
                    if (k == null) break;
                    dragKm = k;
                    dragSx = in.x; dragSy = in.y;
                    dragHist.addLast(new double[]{k[0], k[1], k[2], in.nanos});
                    while (dragHist.size() > 2 && (in.nanos - (long) dragHist.peekFirst()[3]) > 150_000_000L) dragHist.pollFirst();
                    updatePreview();
                    break;
                case RELEASE:
                    if (dragGuest) {
                        double[] v = guestVelocity();
                        if (in.moved && v != null) {
                            int gi = play.addGuest(guestMassKg, guestRadius(guestMassKg), guestStartKm, v);
                            note = play.body(gi).name + String.format(Locale.GERMAN, " · %s · %.2f km/s", massText(guestMassKg), len(v));
                        }
                    } else if (dragId >= 0 && in.moved) {
                        int i = play.indexOf(dragId);
                        if (i > 0) play.place(i, dragKm, releaseVelocity(i));
                    }
                    dragId = -1; dragGuest = false; dragHist.clear();
                    playLayer.prediction = null; playLayer.arrowFrom = playLayer.arrowTo = null;
                    break;
                case WHEEL:
                    double f = Math.pow(10, -0.5 * Math.signum(in.wheel));
                    if (dragGuest) {
                        guestMassKg = Math.max(1e18, Math.min(1e26, guestMassKg * f));
                        updatePreview();
                        break;
                    }
                    MoonLayer.Instance m = pickPlay(px, py);
                    int i = m == null ? -1 : play.indexOf(m.playId);
                    if (i > 0) {
                        PlayBody b = play.body(i);
                        double ref = b.gmStart > 0 ? b.gmStart : (b.source != null ? UranusSystem.massOf(b.source) * UranusSystem.G_KM : b.gm);
                        double now = b.gm > 0 ? b.gm : ref / f;
                        double gm = Math.max(ref / 1000, Math.min(ref * 1e4, now * f));
                        play.setGm(i, gm);
                        note = String.format(Locale.GERMAN, "%s: Masse ×%s → %s", b.name, factorText(gm / ref), massText(gm / UranusSystem.G_KM));
                    }
                    break;
                case CANCEL:
                    dragId = -1; dragGuest = false; dragHist.clear();
                    playLayer.prediction = null; playLayer.arrowFrom = playLayer.arrowTo = null;
                    break;
            }
        }
    }

    private MoonLayer.Instance pickPlay(double px, double py) {
        MoonLayer.Instance best = null;
        double bd = Double.MAX_VALUE;
        for (MoonLayer.Instance m : inst) {
            if (!m.onScreen || m.fade < 0.3f || m.playId < 0) continue;
            double d = Math.hypot(m.sx - px, m.sy - py);
            if (d < Math.max(10, m.sr + 6) && d < bd) { bd = d; best = m; }
        }
        return best;
    }

    /**
     * Pixel → Ort in km in der Ebene durch Uranus mit dieser Normalen. In der Mulde liegt ein Körper auf der
     * gekrümmten Fläche; deshalb wird die Ebene einmal um den Höhenunterschied verschoben und neu geschnitten.
     */
    private double[] rayToKm(double px, double py, double[] n) {
        double[] dir = new double[3];
        camera.rayDir(px, py, dir);
        double shift = 0;
        double[] out = null;
        for (int it = 0; it < 3; it++) {
            double ox = 0, oy = ucY + shift, oz = 0;
            double den = dir[0] * n[0] + dir[1] * n[1] + dir[2] * n[2];
            if (Math.abs(den) < 1e-6) return out;
            double t = ((ox - camera.cx) * n[0] + (oy - camera.cy) * n[1] + (oz - camera.cz) * n[2]) / den;
            if (t <= 0) return out;
            double dx = camera.cx + dir[0] * t - ox, dy = camera.cy + dir[1] * t - oy, dz = camera.cz + dir[2] * t - oz;
            double wr = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (wr < 1e-9) return out;
            double rKm = SpaceMap.inverse(wr, scaleT) * UranusSystem.URANUS_RADIUS_KM;
            out = new double[]{dx / wr * rKm, dy / wr * rKm, dz / wr * rKm};
            // wo erschiene der Körper? Unterschied zur Ebene als neue Verschiebung
            double[] w = new double[3];
            playWorld(out, 0.05, w);
            double planeY = ucY + dy;
            shift += w[1] - (planeY + shift);
        }
        return out;
    }

    private void updatePreview() {
        if (dragGuest) {
            double[] v = guestVelocity();
            playLayer.arrowFrom = guestStartKm;
            playLayer.arrowTo = dragKm;
            if (v == null) { playLayer.prediction = null; dragText = ""; return; }
            double r = len(guestStartKm), mu = UranusSystem.URANUS_GM + guestMassKg * UranusSystem.G_KM;
            double days = Math.min(400, 2 * 2 * Math.PI * Math.sqrt(r * r * r / mu) / 86_400);
            playLayer.prediction = play.predict(-1, guestStartKm, v, guestMassKg * UranusSystem.G_KM, days, 160, 6_000_000L);
            dragText = String.format(Locale.GERMAN, "Gast · %s · %.2f km/s · Mausrad: Masse", massText(guestMassKg), len(v));
        } else if (dragId >= 0) {
            int i = play.indexOf(dragId);
            if (i <= 0) return;
            double[] v = releaseVelocity(i);
            double r = len(dragKm), mu = UranusSystem.URANUS_GM + play.body(i).gm;
            double days = Math.min(400, 2 * 2 * Math.PI * Math.sqrt(r * r * r / mu) / 86_400);
            playLayer.prediction = play.predict(i, dragKm, v, 0, days, 160, 6_000_000L);
            dragText = String.format(Locale.GERMAN, "%s · %,.0f km vom Zentrum · %.2f km/s", play.body(i).name, r, len(v));
        }
    }

    /** Kreisbahn an der neuen Stelle plus der Schwung der Maus (in Spielzeit umgerechnet). */
    private double[] releaseVelocity(int i) {
        double[] v = Playground.circularVelocity(dragKm, dragNormal, play.body(i).gm);
        if (dragHist.size() >= 2) {
            double[] a = dragHist.peekFirst(), b = dragHist.peekLast();
            double dt = (b[3] - a[3]) / 1e9;
            if (dt > 0.01) {
                double sx = (b[0] - a[0]) / dt, sy = (b[1] - a[1]) / dt, sz = (b[2] - a[2]) / dt;   // km je echter Sekunde
                double sp = Math.sqrt(sx * sx + sy * sy + sz * sz), r = len(dragKm);
                if (sp > 0.02 * r) {
                    double k = 1 / (Math.max(settings.playRate, 1e-3) * 86_400), vc = len(v);
                    double tx = sx * k, ty = sy * k, tz = sz * k, tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                    if (tl > 3 * vc) { tx *= 3 * vc / tl; ty *= 3 * vc / tl; tz *= 3 * vc / tl; }
                    v[0] += tx; v[1] += ty; v[2] += tz;
                }
            }
        }
        return v;
    }

    /** Pfeil halb so lang wie der Abstand = Kreisbahngeschwindigkeit. */
    private double[] guestVelocity() {
        double dx = dragKm[0] - guestStartKm[0], dy = dragKm[1] - guestStartKm[1], dz = dragKm[2] - guestStartKm[2];
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz), r = len(guestStartKm);
        if (l < r * 0.02 || r == 0) return null;
        double vc = Math.sqrt((UranusSystem.URANUS_GM + guestMassKg * UranusSystem.G_KM) / r) * l / (0.5 * r);
        return new double[]{dx / l * vc, dy / l * vc, dz / l * vc};
    }

    static double guestRadius(double massKg) { return Math.cbrt(3 * massKg / (4 * Math.PI * 2000)) / 1000; }   // 2 g/cm³

    private static double len(double[] a) { return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); }

    static String massText(double kg) {
        int e = (int) Math.floor(Math.log10(kg));
        return String.format(Locale.GERMAN, "%.1f·10%s kg", kg / Math.pow(10, e), sup(e));
    }

    private static String factorText(double f) {
        return f >= 1 ? String.format(Locale.GERMAN, "%.3g", f) : String.format(Locale.GERMAN, "1/%.3g", 1 / f);
    }

    private static String sup(int e) {
        String d = "⁰¹²³⁴⁵⁶⁷⁸⁹", out = "";
        for (char c : Integer.toString(e).toCharArray()) out += c == '-' ? "⁻" : String.valueOf(d.charAt(c - '0'));
        return out;
    }

    /** Anfragen der Oberfläche übernehmen und die Fahrt ein Bild weiterführen (vor step()). */
    private void tourStep(double dt) {
        if (settings.tourStopRequest) {
            settings.tourStopRequest = false;
            if (player.active()) { player.stop(settings, clock); fitRequested = true; }
        }
        String req = settings.tourRequest;
        if (req != null && play != null) settings.tourRequest = req = null;       // während der Spielwiese keine Fahrten
        if (req != null) {
            settings.tourRequest = null;
            Tour t = system.tour(req);
            if (t != null) {
                player.start(t, settings, clock);
                seasonKey = null;
            }
        }
        if (settings.tourPauseRequest) {
            settings.tourPauseRequest = false;
            if (player.active()) player.togglePause(clock);
        }
        if (player.active() && !player.update(dt, settings, clock)) fitRequested = true;   // zu Ende: zurück in die Übersicht
        Tour t = player.tour();
        settings.tourRunning = t == null ? null : t.code;
        settings.tourPaused = t != null && player.paused();
    }

    private void step(double dt) {
        double k = 1 - Math.exp(-dt * 2.6);
        modeT += (settings.spaceMode - modeT) * k;
        scaleT += (settings.scaleMode - scaleT) * (1 - Math.exp(-dt * 1.6));
        innerT += ((settings.moonLevel >= 1 ? 1 : 0) - innerT) * k;
        irrT += ((settings.moonLevel >= 2 ? 1 : 0) - irrT) * k;
        axisT += ((settings.axis ? 1 : 0) - axisT) * k;
        lensT += ((settings.lens ? 1 : 0) - lensT) * k;
        trailsT += ((settings.trails ? 1 : 0) - trailsT) * k;
        magT += ((settings.magneto ? 1 : 0) - magT) * k;
        geoT += ((settings.geodesics ? 1 : 0) - geoT) * k;
        flightT += ((settings.ringFlight ? 1 : 0) - flightT) * (1 - Math.exp(-dt * 1.4));
        hideT += ((player.moonsHidden ? 1 : 0) - hideT) * k;
        field.setTime(realTime);
        spin = Ephemeris.spinAngle(clock.jd());
        spinStep = clock.isPaused() ? 0 : clock.rate() * dt / UranusSystem.ROTATION_DAYS * 2 * Math.PI;
    }

    private static double smooth(double e0, double e1, double x) {
        double t = (x - e0) / (e1 - e0); t = t < 0 ? 0 : t > 1 ? 1 : t; return t * t * (3 - 2 * t);
    }

    private float fadeOf(Body b) {
        return b.group == BodyGroup.MAJOR ? 1f : b.group == BodyGroup.INNER ? (float) innerT : (float) irrT;
    }

    /** Lage aller Körper in Weltkoordinaten, Mulde, Wellen. */
    private void computeWorld(double dt) {
        double tdb = TimeScale.tdb(clock.jd());
        double[] s = Ephemeris.sunDirWorld(clock.jd());
        double glide = glideStart < 0 ? 1 : smooth(0, GLIDE_SECONDS, realTime - glideStart);
        if (glide >= 1) glideFrom = null;
        sun[0] = s[0]; sun[1] = s[1]; sun[2] = s[2];
        double ucMulde = -CurvatureField.URANUS_DEPTH + 1.05;
        ucY = ucMulde * (1 - modeT);
        double storyYear = storyYear();
        float kr = snapReveal ? 1f : (float) (1 - Math.exp(-dt * 2.2));
        // 1) flache Lage und wahre Höhe
        if (glide >= 1) glideKm = null;
        for (MoonLayer.Instance m : inst) {
            if (play != null && m.playId >= 0) {
                int pi = play.indexOf(m.playId);
                if (pi > 0) play.position(pi, km);
            } else {
                Kepler.position(m.body.orbit, tdb, km);
                Body from = glideFrom == null ? null : glideFrom.get(m.body.name);
                if (from != null) {
                    // Datensatzwechsel: von der alten Lage auf die neue gleiten
                    Kepler.position(from.orbit, tdb, tmp2);
                    for (int k = 0; k < 3; k++) km[k] = tmp2[k] + (km[k] - tmp2[k]) * glide;
                }
                double[] fk = glideKm == null ? null : glideKm.get(m.body.name);
                if (fk != null) for (int k = 0; k < 3; k++) km[k] = fk[k] + (km[k] - fk[k]) * glide;   // aus der Spielwiese zurück
            }
            SpaceMap.toWorld(km, scaleT, tmp);
            m.x = tmp[0]; m.z = tmp[2]; m.y = tmp[1];          // y vorläufig: wahre Höhe über dem Äquator
            boolean inner = m.body.group == BodyGroup.INNER;
            double rKm = m.playId >= 0 ? m.playRadiusKm : m.body.radiusKm;
            m.radius = SpaceMap.moonRadius(rKm, inner ? Math.max(scaleT, flightT) : scaleT);
            m.orbitAngle = Math.atan2(m.z, m.x);
            // Entdeckungsgeschichte: erst sichtbar ab dem Jahr der Entdeckung
            float target = Double.isNaN(storyYear) || m.body.discoveredYear <= 0 || storyYear >= m.body.discoveredYear - 0.2 ? 1f : 0f;
            m.reveal += (target - m.reveal) * kr;
            m.fade = m.playId >= 0 && guestBodies.containsValue(m.body) ? 1f : fadeOf(m.body);
            if (isShepherd(m.body)) m.fade = Math.max(m.fade, (float) flightT);
            m.fade *= m.reveal * (float) (1 - hideT);
        }
        List<Ring> rs = system.rings();
        for (int i = 0; i < rs.size() && i < ringReveal.length; i++) {
            int y = rs.get(i).discoveredYear;
            float target = Double.isNaN(storyYear) || y <= 0 || storyYear >= y - 0.2 ? 1f : 0f;
            ringReveal[i] += (target - ringReveal[i]) * kr;
        }
        snapReveal = false;
        // Sonde: nur in ihrem Gültigkeitszeitraum (Hyperbel um Uranus)
        craft = system.crafts().isEmpty() ? null : system.crafts().get(0);
        craftOn = craft != null && craft.covers(tdb);
        if (craftOn) craftPoint(craft.anomaly(tdb), craftW);
        // 2) Mulde aus Uranus und den Hauptmonden
        field.clear();
        field.addWell(0, ucMulde * (1 - modeT), 0, CurvatureField.URANUS_DEPTH, CurvatureField.URANUS_WIDTH, 3.4);
        for (MoonLayer.Instance m : inst) {
            double mass = m.playId >= 0 ? m.playMassKg : m.body.massKg, rKm = m.playId >= 0 ? m.playRadiusKm : m.body.radiusKm;
            if (mass < 1e19) continue;
            double depth = 0.18 + 0.12 * Math.log10(mass / 1e19);
            field.addWell(m.x, 0, m.z, depth, 0.9, 0.22 + Math.min(rKm, 3000) / 1600);
        }
        // 3) Höhe: in der Mulde auf der Fläche (innen in der Ringebene), im Gitterraum frei
        for (MoonLayer.Instance m : inst) {
            double yTrue = m.y;
            m.y = heightOf(m.x, m.z, yTrue, m.radius);
            // Verfinsterung im Uranusschatten
            double wx = m.x, wy = m.y - ucY, wz = m.z;
            double tp = wx * sun[0] + wy * sun[1] + wz * sun[2];
            if (tp < 0) {
                double d = Math.sqrt(Math.max(0, wx * wx + wy * wy + wz * wz - tp * tp));
                m.eclipse = 1 - smooth(SpaceMap.PR * 0.92, SpaceMap.PR * 1.06, d);
            } else m.eclipse = 0;
        }
        detectConjunctions();
    }

    /** Darstellungshöhe eines Körpers an (x, z) mit wahrer Höhe yTrue über dem Äquator. */
    private double heightOf(double x, double z, double yTrue, double radius) { return heightOf(x, z, yTrue, radius, true); }

    private double heightOf(double x, double z, double yTrue, double radius, boolean withWaves) {
        double rxz = Math.sqrt(x * x + z * z);
        double ucMulde = -CurvatureField.URANUS_DEPTH + 1.05;
        double onSheet = (withWaves ? field.sheetY(x, z) : field.sheetYStatic(x, z)) + radius * 0.55;
        double mulde = ucMulde + (onSheet - ucMulde) * smooth(3.5, 6.5, rxz) + yTrue;
        return mulde + (yTrue - mulde) * modeT;
    }

    private void detectConjunctions() {
        if (!settings.waves || clock.isPaused() || clock.rate() > 1.5) { Arrays.fill(prevSin, Double.NaN); return; }
        MoonLayer.Instance prev = null;
        int pi = -1;
        for (int i = 0; i < inst.length; i++) {
            MoonLayer.Instance m = inst[i];
            if (m.body.group != BodyGroup.MAJOR) continue;
            if (prev != null) {
                double s = Math.sin(prev.orbitAngle - m.orbitAngle), c = Math.cos(prev.orbitAngle - m.orbitAngle);
                if (!Double.isNaN(prevSin[pi]) && prevSin[pi] < 0 && s >= 0 && c > 0) {
                    field.addRipple(prev.x, prev.z, realTime);
                    caption = "Konjunktion · " + prev.body.name + " und " + m.body.name;
                    captionUntil = realTime + 3.2;
                }
                prevSin[pi] = s;
            }
            prev = m; pi = i;
        }
    }

    private double targetExtent() {
        double e = 15;
        if (play != null) {
            for (MoonLayer.Instance m : inst) {
                if (m.fade < 0.3f) continue;
                e = Math.max(e, Math.min(400, Math.hypot(m.x, m.z) * 1.1 + 2));
            }
            return e;
        }
        for (MoonLayer.Instance m : inst) {
            if (!m.body.group.visibleAt(settings.moonLevel)) continue;
            double r = SpaceMap.map(m.body.orbit.aKm * (1 + m.body.orbit.e) / UranusSystem.URANUS_RADIUS_KM, settings.scaleMode);
            e = Math.max(e, r * 1.1 + 2);
        }
        return e;
    }

    private void fit() {
        double e = targetExtent();
        camera.setTarget(0, ucY * 0.75, 0);
        camera.setDistanceTarget(e * 1.75);
        fitRequested = false;
    }

    private void cameraStep(double dt, int w, int h) {
        camera.setViewport(w, h);
        double te = targetExtent();
        extent *= Math.exp((Math.log(te) - Math.log(extent)) * (1 - Math.exp(-dt * 1.8)));
        if (player.active() && !settings.ringFlight) {
            // Kamerafahrt: der Abspieler führt die Kamera (nach einem Ringflug aus dessen letzter Lage heraus)
            if (flight.active()) flight.update(camera, false, 0, realTime, ucY, dt);
            camera.near = Math.max(0.0004, Math.min(0.05, camera.distance() * 0.2));
            player.applyCamera(camera, this::targetOf, sun);
            lastFocus = settings.focus;
            fitRequested = false;
            return;
        }
        if (settings.ringFlight) {
            camera.near = 0.0004;
            flight.update(camera, true, clock.daysSinceJ2000(), realTime, ucY, dt);
            lastFocus = null;
            return;
        }
        if (flight.active()) {                       // Flug beendet: zurück in die Übersicht
            flight.update(camera, false, 0, realTime, ucY, dt);
            fitRequested = true;
        }
        camera.near = 0.05;
        Body f = settings.focus;
        if (f != lastFocus) {
            lastFocus = f;
            if (f == null) fitRequested = true;
            else {
                MoonLayer.Instance m = instanceOf(f);
                camera.setDistanceTarget(Math.max(m.radius * 10, 1.2));
            }
        }
        if (fitRequested) fit();
        if (f != null) {
            MoonLayer.Instance m = instanceOf(f);
            camera.minDist = Math.max(0.05, m.radius * 2.2);
            camera.setTarget(m.x, m.y, m.z);
        } else {
            camera.minDist = SpaceMap.PR * 1.5;
            camera.setTarget(0, ucY * 0.75, 0);
        }
        camera.update(dt);
    }

    /** Weltlage eines Fahrtziels: Uranus, ein Mond oder die Sonde. */
    private double[] targetOf(String name) {
        if (name == null || name.isEmpty() || name.equals("Uranus")) return new double[]{0, ucY, 0};
        if (craft != null && craft.name.equals(name)) return craftOn ? craftW.clone() : new double[]{0, ucY, 0};
        for (MoonLayer.Instance m : inst) if (m.body.name.equals(name)) return new double[]{m.x, m.y, m.z};
        return null;
    }

    private MoonLayer.Instance instanceOf(Body b) {
        for (MoonLayer.Instance m : inst) if (m.body == b) return m;
        return inst[0];
    }

    private void projectMoon(MoonLayer.Instance m) {
        m.onScreen = false;
        if (m.fade <= 0.01f) return;
        if (!camera.project(m.x, m.y, m.z, tmp)) return;
        m.sx = tmp[0]; m.sy = tmp[1]; m.depth = tmp[2];
        m.sr = camera.foc * m.radius / tmp[2];
        m.onScreen = m.sx > -m.sr - 40 && m.sy > -m.sr - 40 && m.sx < camera.width + m.sr + 40 && m.sy < camera.height + m.sr + 40;
    }

    private List<MoonLayer.Instance> sortedMoons(boolean behind) {
        List<MoonLayer.Instance> l = new ArrayList<>();
        for (MoonLayer.Instance m : inst) {
            if (!m.onScreen) continue;
            boolean isBehind = planetDepth > 0 && m.depth > planetDepth;
            if (isBehind == behind) l.add(m);
        }
        l.sort((a, b) -> Double.compare(b.depth, a.depth));
        return l;
    }

    private void setShadowCasters() {
        double[] c = new double[inst.length * 4];
        int n = 0;
        for (MoonLayer.Instance m : inst) {
            if (m.body.group != BodyGroup.MAJOR || m.fade < 0.5f) continue;
            c[n * 4] = m.x; c[n * 4 + 1] = m.y; c[n * 4 + 2] = m.z; c[n * 4 + 3] = m.radius; n++;
        }
        planet.setShadowCasters(c, n);
        moonLayer.setSun(sun);
        moonLayer.setPlanet(0, ucY, 0);
    }

    // ================================================================== Linien

    /** Ordnet ein Segment nach vorn/hinten und legt es ab. */
    private void seg(double wx0, double wy0, double wz0, double wx1, double wy1, double wz1,
                     float r, float g, float b, float a, boolean additive) {
        if (!camera.project(wx0, wy0, wz0, tmp) || !camera.project(wx1, wy1, wz1, tmp2)) return;
        double mx = (wx0 + wx1) * 0.5, my = (wy0 + wy1) * 0.5 - ucY, mz = (wz0 + wz1) * 0.5;
        boolean inside = mx * mx + my * my + mz * mz < SpaceMap.PR * SpaceMap.PR;
        boolean isBack = inside || (planetDepth > 0 && (tmp[2] + tmp2[2]) * 0.5 > planetDepth);
        (isBack ? back : front).add(tmp[0], tmp[1], tmp2[0], tmp2[1], r, g, b, a, additive);
    }

    /** Leuchtpunkt in Weltkoordinaten ablegen (vor/hinter Uranus). */
    private void glowAt(double x, double y, double z, float r, float g, float b, double radiusPx) {
        if (!camera.project(x, y, z, tmp)) return;
        if (tmp[0] < -20 || tmp[1] < -20 || tmp[0] > camera.width + 20 || tmp[1] > camera.height + 20) return;
        double dx = x, dy = y - ucY, dz = z;
        boolean isBack = dx * dx + dy * dy + dz * dz < SpaceMap.PR * SpaceMap.PR || (planetDepth > 0 && tmp[2] > planetDepth);
        (isBack ? backPts : frontPts).add(tmp[0], tmp[1], radiusPx, r, g, b);
    }

    private static boolean isShepherd(Body b) { return b.name.equals("Cordelia") || b.name.equals("Ophelia"); }

    private double shepherdAngle(String name) {
        for (MoonLayer.Instance m : inst) if (m.body.name.equals(name)) return Math.atan2(m.z, m.x);
        return Double.NaN;
    }

    private void buildSheet(double alpha) {
        double L = extent;
        int lines = 41, steps = 110;
        double[] px = new double[steps + 1], py = new double[steps + 1], pz = new double[steps + 1];
        for (int dir = 0; dir < 2; dir++) {
            for (int i = 0; i < lines; i++) {
                double c = -L + 2 * L * i / (lines - 1);
                boolean[] ok = new boolean[steps + 1];
                for (int k = 0; k <= steps; k++) {
                    double s = -L + 2 * L * k / steps, x = dir == 0 ? s : c, z = dir == 0 ? c : s;
                    ok[k] = x * x + z * z <= L * L;
                    px[k] = x; pz[k] = z; py[k] = ok[k] ? field.sheetY(x, z) : 0;
                }
                for (int k = 0; k < steps; k++) {
                    if (!ok[k] || !ok[k + 1]) continue;
                    double r = Math.sqrt(px[k] * px[k] + pz[k] * pz[k]);
                    double depth = Math.min(1, -(py[k] + py[k + 1]) * 0.5 / 1.5);
                    double edge = Math.max(0, Math.min(1, (L - r) / (L * 0.3)));
                    float in = (float) ((0.12 + 0.88 * depth) * edge * alpha * 0.62);
                    seg(px[k], py[k], pz[k], px[k + 1], py[k + 1], pz[k + 1], 0.26f, 0.76f, 0.84f, in, true);
                }
            }
        }
    }

    private void buildLattice(double alpha) {
        double L = extent, s = L / 15.0;
        double[] layers = {-3.2 * s, -1.1 * s, 1.1 * s, 3.2 * s};
        double[] a = new double[3], b = new double[3];
        for (double y : layers) {
            for (double c = -L; c <= L + 1e-9; c += 2 * s) {
                latticeLine(-L, y, c, L, y, c, 48, alpha, a, b, L);
                latticeLine(c, y, -L, c, y, L, 48, alpha, a, b, L);
            }
        }
        for (double x = -12 * s; x <= 12 * s + 1e-9; x += 6 * s)
            for (double z = -12 * s; z <= 12 * s + 1e-9; z += 6 * s)
                latticeLine(x, -3.2 * s, z, x, 3.2 * s, z, 16, alpha, a, b, L);
    }

    private void latticeLine(double x0, double y0, double z0, double x1, double y1, double z1, int n,
                             double alpha, double[] a, double[] b, double L) {
        field.displace(x0, y0, z0, a);
        for (int k = 1; k <= n; k++) {
            double t = (double) k / n;
            double x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t, z = z0 + (z1 - z0) * t;
            field.displace(x, y, z, b);
            double pull = Math.sqrt((b[0] - x) * (b[0] - x) + (b[1] - y) * (b[1] - y) + (b[2] - z) * (b[2] - z));
            double r = Math.sqrt(x * x + z * z);
            double edge = Math.max(0, Math.min(1, (L - r) / (L * 0.27)));
            float in = (float) ((0.10 + 0.90 * Math.min(1, pull / 1.0)) * edge * (1 - Math.abs(y) / (L * 0.6)) * alpha * 0.55);
            if (in > 0) seg(a[0], a[1], a[2], b[0], b[1], b[2], 0.30f, 0.72f, 0.92f, in, true);
            a[0] = b[0]; a[1] = b[1]; a[2] = b[2];
        }
    }

    private void buildOrbits(double alpha) {
        double days = TimeScale.tdb(clock.jd());
        for (MoonLayer.Instance m : inst) {
            if (m.fade <= 0.01f) continue;
            OrbitElements o = m.body.orbit;
            boolean major = m.body.group == BodyGroup.MAJOR;
            // ganze Bahn, schwach
            int n = major ? 160 : 120;
            float br, bg, bb, ba;
            if (m.body.group == BodyGroup.IRREGULAR) {
                if (o.isRetrograde()) { br = 0.95f; bg = 0.55f; bb = 0.45f; } else { br = 0.55f; bg = 0.75f; bb = 1f; }
                ba = 0.16f;
            } else { br = 0.70f; bg = 0.86f; bb = 0.92f; ba = major ? 0.10f : 0.06f; }
            double px = 0, py = 0, pz = 0;
            for (int k = 0; k <= n; k++) {
                double t = days + o.periodDays * k / n;
                orbitPoint(o, t, m.radius);
                double qx = tmp[0], qy = tmp[1], qz = tmp[2];      // seg() überschreibt tmp
                if (k > 0) seg(px, py, pz, qx, qy, qz, br, bg, bb, (float) (ba * alpha * m.fade), true);
                px = qx; py = qy; pz = qz;
            }
            // Spur hinter den Hauptmonden
            if (major) {
                int tn = 64;
                double span = o.periodDays * 0.32;
                orbitPoint(o, days, m.radius);
                px = tmp[0]; py = tmp[1]; pz = tmp[2];
                for (int k = 1; k <= tn; k++) {
                    orbitPoint(o, days - span * k / tn, m.radius);
                    double qx = tmp[0], qy = tmp[1], qz = tmp[2];
                    float fa = (float) (0.55 * (1 - (double) k / tn) * alpha);
                    seg(px, py, pz, qx, qy, qz, 0.80f, 0.94f, 0.98f, fa, true);
                    px = qx; py = qy; pz = qz;
                }
            }
        }
    }

    /** Weltlage eines Bahnpunkts zum Zeitpunkt t (JD, TDB) → tmp. */
    private void orbitPoint(OrbitElements o, double t, double radius) {
        Kepler.position(o, t, km);
        SpaceMap.toWorld(km, scaleT, tmp);
        double y = heightOf(tmp[0], tmp[2], tmp[1], radius, false);
        tmp[1] = y;
    }

    private void buildRings() {
        List<Ring> rings = system.rings();
        // Beleuchtete Seite? Staub leuchtet im Gegenlicht auf.
        double ccx = camera.cx, ccy = camera.cy - ucY, ccz = camera.cz;
        double cl = Math.sqrt(ccx * ccx + ccy * ccy + ccz * ccz);
        boolean litSide = Math.signum(sun[1]) == Math.signum(ccy);
        double forward = Math.max(0, -(ccx * sun[0] + ccy * sun[1] + ccz * sun[2]) / cl);
        double dustBoost = 1 + 5 * smooth(0.3, 0.95, forward);
        double pixelWorld = planetDepth > 0 ? planetDepth / camera.foc : 1;
        int n = 360;
        double[] a = new double[3], b = new double[3];
        double tdb = TimeScale.tdb(clock.jd());
        for (int ri = 0; ri < rings.size(); ri++) {
            Ring rg = rings.get(ri);
            float reveal = ri < ringReveal.length ? ringReveal[ri] : 1f;
            if (reveal < 0.01f) continue;
            double R = rg.radiusRu * SpaceMap.PR;
            double peri = rg.periapsis(tdb);
            int bands = rg.dusty ? 7 : 1;
            float lightK = (float) (rg.dusty ? dustBoost * (litSide ? 1 : 0.8) : (litSide ? 1.0 : 0.38));
            for (int band = 0; band < bands; band++) {
                double rr = rg.dusty ? R + (band - (bands - 1) * 0.5) / bands * rg.widthRu * SpaceMap.PR : R;
                float alpha = rg.opacity * lightK * reveal;
                if (rg.name.equals("ε")) alpha *= (float) (1 - flightT);      // beim Ringflug ersetzen ihn die Teilchen
                if (rg.dusty) alpha *= (float) (1 - Math.pow(Math.abs(band - (bands - 1) * 0.5) / (bands * 0.5), 2)) * 0.9f;
                else {
                    // schmale Ringe: sichtbar halten, beim Heranzoomen etwas breiter/heller
                    double wpx = rg.widthRu * SpaceMap.PR / pixelWorld;
                    alpha *= (float) Math.min(1.6, 0.8 + wpx * 0.4);
                }
                for (int k = 0; k < n; k++) {
                    double t0 = k * Math.PI * 2 / n, t1 = (k + 1) * Math.PI * 2 / n;
                    // exzentrische Ringe: Ellipse um Uranus im Brennpunkt (ε: ±400 km)
                    double r0 = rg.dusty ? rr : rg.radiusRuAt(t0, peri) * SpaceMap.PR;
                    double r1 = rg.dusty ? rr : rg.radiusRuAt(t1, peri) * SpaceMap.PR;
                    a[0] = Math.cos(t0) * r0; a[1] = ucY; a[2] = Math.sin(t0) * r0;
                    b[0] = Math.cos(t1) * r1; b[1] = ucY; b[2] = Math.sin(t1) * r1;
                    // wo der Ring breiter ist, liegt mehr Material: etwas heller (ε: 20 → 96 km)
                    float wk = rg.dusty || rg.ecc == 0 ? 1f
                            : (float) (0.7 + 0.3 * rg.widthRuAt((t0 + t1) * 0.5, peri) / rg.widthRu);
                    // Planetenschatten auf den Ringen
                    double mx = (a[0] + b[0]) * 0.5, mz = (a[2] + b[2]) * 0.5;
                    double tp = mx * sun[0] + mz * sun[2];
                    boolean shadow = tp < 0 && (mx * mx + mz * mz - tp * tp) < SpaceMap.PR * SpaceMap.PR;
                    float al = (shadow ? alpha * 0.07f : alpha) * wk;
                    seg(a[0], a[1], a[2], b[0], b[1], b[2], rg.red, rg.green, rg.blue, Math.min(1f, al), rg.dusty);
                }
            }
        }
    }

    /** Weltlage der Sonde bei der hyperbolischen Anomalie h → out (Höhe wie die Monde, ohne Wellen). */
    private void craftPoint(double h, double[] out) {
        craft.positionAtAnomaly(h, craftKm);
        SpaceMap.toWorld(craftKm, scaleT, out);
        out[1] = heightOf(out[0], out[2], out[1], 0.02, false);
    }

    /**
     * Voyager 2: Hyperbel (gleichmäßig in der Anomalie abgetastet, zurückgelegt hell, voraus schwach),
     * Stundenmarken (±24 h stündlich, sonst alle 6 h), die berechneten Ereignisse und ein kleines Modell —
     * Schüssel zur Sonne, Magnetometer-, RTG- und Instrumentenausleger. Das Modell hat feste Bildgröße.
     */
    private void buildCraft() {
        double tdb = TimeScale.tdb(clock.jd());
        double h0 = craft.anomaly(craft.validFromJd), h1 = craft.anomaly(craft.validToJd), hn = craft.anomaly(tdb);
        int n = 360;
        double[] a = new double[3], b = new double[3];
        craftPoint(h0, a);
        for (int k = 1; k <= n; k++) {
            double h = h0 + (h1 - h0) * k / n;
            craftPoint(h, b);
            double edge = Math.min(1, Math.min(k, n - k) / 14.0);
            float al = (float) ((h <= hn ? 0.60 : 0.20) * edge);
            seg(a[0], a[1], a[2], b[0], b[1], b[2], 0.98f, 0.80f, 0.48f, al, true);
            a[0] = b[0]; a[1] = b[1]; a[2] = b[2];
        }
        // Stundenmarken
        for (int k = -72; k <= 72; k++) {
            if (Math.abs(k) > 24 && k % 6 != 0) continue;
            double jd = craft.tpJd + k / 24.0;
            if (!craft.covers(jd)) continue;
            craftPoint(craft.anomaly(jd), a);
            boolean big = k % 6 == 0;
            glowAt(a[0], a[1], a[2], 0.95f, 0.78f, 0.50f, big ? 1.7 : 1.0);
        }
        // berechnete Ereignisse des Vorbeiflugs
        for (StoryEvent e : system.events()) {
            if (e.kind != StoryEvent.Kind.FLYBY) continue;
            double jd = TimeScale.tdb(e.jdUtc);
            if (!craft.covers(jd)) continue;
            craftPoint(craft.anomaly(jd), a);
            glowAt(a[0], a[1], a[2], 0.70f, 0.95f, 1.0f, 2.8);
        }
        // Modell
        double px = craftW[0], py = craftW[1], pz = craftW[2];
        if (!camera.project(px, py, pz, tmp)) return;
        double sz = tmp[2] * 10 / camera.foc;                         // 10 Pixel je Einheit: Schüssel ~18 px
        double sx = sun[0], sy = sun[1], sz2 = sun[2];
        // Basis senkrecht zur Sonnenrichtung
        double ux = sy * 0 - sz2 * 1, uy = sz2 * 0 - sx * 0, uz = sx * 1 - sy * 0;  // sun × (0,1,0)
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-6) { ux = 1; uy = 0; uz = 0; ul = 1; }
        ux /= ul; uy /= ul; uz /= ul;
        double vx = sy * uz - sz2 * uy, vy = sz2 * ux - sx * uz, vz = sx * uy - sy * ux;
        // im Uranusschatten dunkler
        double wx = px, wy = py - ucY, wz = pz, tp = wx * sx + wy * sy + wz * sz2;
        double shade = tp < 0 ? 0.25 + 0.75 * smooth(SpaceMap.PR * 0.95, SpaceMap.PR * 1.05, Math.sqrt(Math.max(0, wx * wx + wy * wy + wz * wz - tp * tp))) : 1;
        float lr = (float) (0.93 * shade), lg = (float) (0.92 * shade), lb = (float) (0.86 * shade);
        double cx = px + sx * 0.35 * sz, cy = py + sy * 0.35 * sz, cz = pz + sz2 * 0.35 * sz, rd = 0.9 * sz;
        double qx = 0, qy = 0, qz = 0;
        for (int k = 0; k <= 16; k++) {
            double t = k * Math.PI * 2 / 16, c = Math.cos(t) * rd, si = Math.sin(t) * rd;
            double x = cx + ux * c + vx * si, y = cy + uy * c + vy * si, z = cz + uz * c + vz * si;
            if (k > 0) seg(qx, qy, qz, x, y, z, lr, lg, lb, 0.95f, false);
            if (k % 4 == 0) seg(cx + sx * 0.25 * sz, cy + sy * 0.25 * sz, cz + sz2 * 0.25 * sz, x, y, z, lr, lg, lb, 0.55f, false);
            qx = x; qy = y; qz = z;
        }
        seg(px - sx * 0.35 * sz, py - sy * 0.35 * sz, pz - sz2 * 0.35 * sz, cx, cy, cz, lr, lg, lb, 0.95f, false);   // Bus
        seg(px, py, pz, px + ux * 3.2 * sz, py + uy * 3.2 * sz, pz + uz * 3.2 * sz, lr, lg, lb, 0.65f, false);     // Magnetometer
        seg(px, py, pz, px - ux * 1.4 * sz, py - uy * 1.4 * sz, pz - uz * 1.4 * sz, lr, lg, lb, 0.85f, false);     // RTG
        seg(px, py, pz, px - vx * 1.6 * sz, py - vy * 1.6 * sz, pz - vz * 1.6 * sz, lr, lg, lb, 0.85f, false);     // Instrumente
        glowAt(px - ux * 1.4 * sz, py - uy * 1.4 * sz, pz - uz * 1.4 * sz, 1.0f, 0.42f, 0.22f, 1.2);             // RTG glüht
        glowAt(px, py, pz, 1.0f, 0.84f, 0.55f, 2.4 * shade);
    }

    private Ring epsilon() {
        for (Ring r : system.rings()) if (r.name.equals("ε")) return r;
        return null;
    }

    private void buildAxis(double alpha) {
        float al = (float) alpha;
        double len = 2.4 * SpaceMap.PR;
        dashed(0, 1, 0, len, 0.86f, 0.93f, 0.94f, 0.55f * al, 10);
        double[] e = eclipticNormal;
        dashed(e[0], e[1], e[2], len, 0.80f, 0.72f, 0.57f, 0.5f * al, 24);
        // Bogen 97,77° zwischen Drehachse und Bahnnormale
        double ang = Math.acos(Math.max(-1, Math.min(1, e[1])));
        double px = e[0], py = e[1] - Math.cos(ang), pz = e[2];
        double pl = Math.sqrt(px * px + py * py + pz * pz); px /= pl; py /= pl; pz /= pl;
        double ar = 1.8 * SpaceMap.PR, qx = 0, qy = 0, qz = 0;
        for (int k = 0; k <= 40; k++) {
            double t = ang * k / 40, c = Math.cos(t), s = Math.sin(t);
            double x = ar * (px * s), y = ucY + ar * (c + py * s), z = ar * (pz * s);
            if (k > 0) seg(qx, qy, qz, x, y, z, 0.80f, 0.72f, 0.57f, 0.7f * al, true);
            qx = x; qy = y; qz = z;
        }
    }

    private void dashed(double dx, double dy, double dz, double len, float r, float g, float b, float a, int pattern) {
        int n = 60;
        for (int k = -n; k < n; k++) {
            if (((k + n) / 2) % 2 == 1 && pattern < 20) continue;
            if (((k + n) % 3) != 0 && pattern >= 20) continue;
            double t0 = len * k / n, t1 = len * (k + 1) / n;
            if (Math.abs(t0) < SpaceMap.PR && Math.abs(t1) < SpaceMap.PR) continue;
            seg(dx * t0, ucY + dy * t0, dz * t0, dx * t1, ucY + dy * t1, dz * t1, r, g, b, a, true);
        }
    }

    // ================================================================== Beschriftung

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. MMMM yyyy, HH:mm", Locale.GERMAN);

    /** Nimmt die Beschriftung in Fensterkoordinaten auf (k = Fenster / Renderbild). */
    private Overlay overlay(int w, int h, double k, double ms) {
        Overlay o = new Overlay();
        double vw = w * k, vh = h * k;
        spots.clear();
        // Datum und Sonnenstand
        ZoneId zone = ZoneId.systemDefault();
        String suffix = zone.getId().equals("Europe/Berlin") ? (zone.getRules().isDaylightSavings(clock.instant()) ? " MESZ" : " MEZ") : "";
        Tour tr = player.tour();
        if (tr != null || craftOn) {
            // Geschichten zeigen oft Planet und Ringe hinter der Kopfzeile: dezenter Grund für die Lesbarkeit
            o.setColor(new Color(4, 8, 14));
            o.setAlpha(0.55f);
            o.rect(0, 0, 660, craftOn ? 82 : 64);
            o.setAlpha(1f);
        }
        o.setFont(serifBig);
        o.setColor(new Color(226, 240, 242));
        double sy = storyYear();
        if (!Double.isNaN(sy)) o.text(String.format(Locale.GERMAN, "Jahr %d", (int) Math.floor(sy)), 18, 34);
        else if (tr != null && tr.overlay == Tour.Overlay.VOYAGER) o.text(clock.instant().atZone(ZoneOffset.UTC).format(DATE) + " UTC", 18, 34);
        else o.text(clock.instant().atZone(zone).format(DATE) + suffix, 18, 34);
        double lat = Math.toDegrees(Math.asin(sun[1]));
        o.setFont(mono);
        o.setColor(new Color(145, 168, 179));
        o.text(String.format(Locale.GERMAN, "Sonne über %.1f° %s · %.2f AE · %s%s",
                Math.abs(lat), lat >= 0 ? "Nord" : "Süd", Ephemeris.sunDistanceAu(clock.jd()), rateText(),
                Double.isNaN(sy) ? "" : " · Himmel und Bahnen von heute"), 19, 54);
        if (craftOn) craftInfo(o, k);
        o.setFont(monoSmall);
        o.setColor(new Color(98, 121, 135));
        String perf = String.format(Locale.GERMAN, "%.1f ms · %d×%d", ms, w, h);
        if (!clean) o.text(perf, vw - o.stringWidth(perf) - 14, 22);

        if (settings.labels) labels(o, k);
        if (axisT > 0.3) {
            o.setFont(monoSmall);
            o.setAlpha((float) Math.min(1, axisT));
            if (camera.project(0, ucY + 2.4 * SpaceMap.PR, 0, tmp)) { o.setColor(new Color(220, 238, 240)); o.text("Drehachse", tmp[0] * k + 6, tmp[1] * k); }
            double[] e = eclipticNormal;
            if (camera.project(e[0] * 2.4 * SpaceMap.PR, ucY + e[1] * 2.4 * SpaceMap.PR, e[2] * 2.4 * SpaceMap.PR, tmp)) { o.setColor(new Color(205, 184, 146)); o.text("Bahnnormale", tmp[0] * k + 6, tmp[1] * k); }
            double ang = Math.acos(e[1]) / 2, px = e[0], py = e[1] - Math.cos(ang * 2), pz = e[2];
            double pl = Math.sqrt(px * px + py * py + pz * pz), ar = 1.8 * SpaceMap.PR;
            if (camera.project(ar * px / pl * Math.sin(ang), ucY + ar * (Math.cos(ang) + py / pl * Math.sin(ang)), ar * pz / pl * Math.sin(ang), tmp)) {
                o.setColor(new Color(205, 184, 146)); o.text("97,77°", tmp[0] * k + 6, tmp[1] * k - 4);
            }
            o.setAlpha(1f);
        }
        String nt = note;
        if (nt != null) { note = null; caption = nt; captionUntil = realTime + 4; }
        if (realTime < captionUntil && tr == null) {                    // während einer Geschichte spricht die Karte
            o.setAlpha((float) Math.min(1, (captionUntil - realTime) / 0.8) * 0.92f);
            o.setFont(serifMid);
            o.setColor(new Color(226, 240, 242));
            o.text(caption, vw - o.stringWidth(caption) - 18, vh - 18);
            o.setAlpha(1f);
        }
        Body f = settings.focus;
        if (f != null) { if (play != null) playCard(o, f, vh); else card(o, f, vh); }
        if (play != null) playOverlay(o, vw, vh, k);
        if (flightT > 0.3 && tr == null) {
            o.setAlpha((float) Math.min(1, (flightT - 0.3) / 0.5));
            o.setFont(serifBig);
            o.setColor(new Color(226, 240, 242));
            o.text("Im ε-Ring", 18, vh - 58);
            o.setFont(mono);
            o.setColor(new Color(160, 182, 192));
            Ring eps = epsilon();
            double camPhi = Math.atan2(camera.cz, camera.cx);
            if (eps != null) {
                double peri = eps.periapsis(TimeScale.tdb(clock.jd()));
                double rKm = eps.radiusRuAt(camPhi, peri) * UranusSystem.URANUS_RADIUS_KM, wKm = eps.widthRuAt(camPhi, peri) * UranusSystem.URANUS_RADIUS_KM;
                o.text(String.format(Locale.GERMAN, "hier %,.0f km vom Zentrum und %.0f km breit (Ellipse: 20–96 km, dargestellt ~7-fach) · Brocken von Zentimeter- bis Metergröße",
                        rKm, wKm), 19, vh - 38);
            }
            o.text(String.format(Locale.GERMAN, "Hirten: Cordelia innen, Ophelia außen · %,d Teilchen im Bild · Esc: zurück", flightParticles), 19, vh - 20);
            o.setAlpha(1f);
        }
        if (tr != null) {
            double bottom = vh - 16;
            if (tr.overlay == Tour.Overlay.SEASONS) bottom = seasonsBand(o, tr, vw, vh);
            else if (tr.overlay == Tour.Overlay.DISCOVERY) bottom = discoveryBand(o, vw, vh, sy);
            tourCard(o, tr, vw, bottom);
        }
        if (!clean) {
            icons(o, vw);
            errorLine(o, vw, vh);
            if (settings.help) { help(o, vw, vh); hoverShown = null; }
            else { helpButton = null; hoverCard(o, vw, vh, k); }
        } else hoverShown = null;
        return o;
    }

    private String rateText() {
        if (play != null) return "Zeit der Spielwiese";
        if (clock.isPaused()) return "angehalten";
        double r = clock.rate();
        if (Math.abs(r - 1.0 / 86400) < 1e-9) return "Echtzeit";
        if (r < 1.0 / 24 + 1e-9) return String.format(Locale.GERMAN, "1 s ≙ %.0f min", r * 1440);
        if (r < 1) return String.format(Locale.GERMAN, "1 s ≙ %.0f h", r * 24);
        return String.format(Locale.GERMAN, "1 s ≙ %.0f d", r);
    }

    private void labels(Overlay o, double k) {
        List<double[]> placed = new ArrayList<>();
        placed.add(new double[]{0, 0, 680, craftOn ? 80 : 62});          // Kopfzeilen (Datum, Sonne, Sonde) freihalten
        placed.add(new double[]{camera.width * k - 90, 0, camera.width * k, 60});   // Bildzeit und Symbole oben rechts
        Tour tr = player.tour();
        if (tr != null) {                                                   // Karte, Streifen und Jahreskarten der Geschichte
            double vw = camera.width * k, vh = camera.height * k;
            placed.add(new double[]{0, vh - 330, 620, vh});
            if (tr.overlay != Tour.Overlay.NONE) placed.add(new double[]{0, vh - 150, vw, vh});
            if (tr.overlay == Tour.Overlay.DISCOVERY) placed.add(new double[]{vw - 290, 60, vw, 480});
        }
        Body hover = settings.hover, focus = settings.focus;
        List<MoonLayer.Instance> order = new ArrayList<>(Arrays.asList(inst));
        order.sort((a, b) -> {
            boolean sa = a.body == hover || a.body == focus, sb = b.body == hover || b.body == focus;
            if (sa != sb) return sa ? -1 : 1;
            int ga = a.body.group.ordinal(), gb = b.body.group.ordinal();
            return ga != gb ? Integer.compare(ga, gb) : Double.compare(b.body.radiusKm, a.body.radiusKm);
        });
        for (MoonLayer.Instance m : order) {
            if (!m.onScreen || m.fade < 0.35f) continue;
            if (m.sx < 0 || m.sy < 0 || m.sx > camera.width || m.sy > camera.height) continue;
            boolean major = m.body.group == BodyGroup.MAJOR, special = m.body == hover || m.body == focus;
            if (m.body.group == BodyGroup.INNER && !special && planetRs * k < 120) continue;
            // hinter der Planetenscheibe verdeckt: keine Beschriftung
            if (planetDepth > 0 && m.depth > planetDepth && Math.hypot(m.sx - planetSx, m.sy - planetSy) < planetRs) continue;
            double sx = m.sx * k, sy = m.sy * k, sr = m.sr * k;
            o.setFont(major || special ? serifMid : serifSmall);
            int tw = o.stringWidth(m.body.name);
            double x = sx + Math.max(5, sr) + 7, y = sy - Math.max(4, sr) - 3;
            double[] box = {x - 2, y - 15, x + Math.max(tw, 40) + 2, y + (major ? 16 : 3)};
            boolean clash = false;
            for (double[] p : placed) if (box[0] < p[2] && box[2] > p[0] && box[1] < p[3] && box[3] > p[1]) { clash = true; break; }
            if (clash && (!special || tr != null)) continue;
            placed.add(box);
            float a = m.fade * (special ? 1f : major ? 0.92f : 0.72f);
            o.setAlpha(a * 0.4f);
            o.setColor(new Color(220, 238, 240));
            o.line(sx + sr * 0.7, sy - sr * 0.7, x - 3, y + 3);
            o.setAlpha(a);
            o.setColor(special ? new Color(160, 232, 238) : new Color(232, 243, 244));
            o.text(m.body.name, x, y);
            if (major) {
                o.setFont(monoSmall);
                o.setColor(new Color(145, 168, 179));
                o.text(m.playId >= 0 ? playPeriod(m) : String.format(Locale.GERMAN, "%.2f d", m.body.orbit.periodDays), x, y + 13);
            }
        }
        o.setAlpha(1f);
        if (planetDepth > 0 && planetSx > 0 && planetSx < camera.width) {
            o.setFont(serifBig);
            o.setColor(new Color(226, 240, 242));
            o.setAlpha(0.9f);
            String u = "Uranus";
            int tw = o.stringWidth(u);
            o.text(u, (planetSx - planetRs * 1.1) * k - tw - 6, (planetSy - planetRs * 0.95) * k);
            o.setAlpha(1f);
        }
    }

    private void card(Overlay o, Body b, double vh) {
        String[] lines = {
                b.group.label() + " · " + b.orbitSource,
                String.format(Locale.GERMAN, "Radius %,.0f km · Abstand %,.0f km", b.radiusKm, b.orbit.aKm),
                String.format(Locale.GERMAN, "Umlauf %s · Neigung %.1f°%s", period(b.orbit.periodDays), b.orbit.iDeg, b.orbit.isRetrograde() ? " (rückläufig)" : ""),
                "Entdeckt " + b.discoveredYear + (b.discoveredBy.isEmpty() ? "" : " · " + b.discoveredBy),
                "Name aus " + b.namesake};
        o.setFont(monoSmall);
        int lw = 0;
        for (String l : lines) lw = Math.max(lw, o.stringWidth(l));
        double bw = Math.max(lw, 160) + 28, bh = 34 + lines.length * 16 + 10, x = 16, y = vh - bh - 16;
        o.box(x, y, bw, bh);
        o.setFont(serifBig);
        o.setColor(new Color(232, 243, 244));
        o.text(b.name, x + 14, y + 28);
        o.setFont(monoSmall);
        o.setColor(new Color(160, 182, 192));
        for (int i = 0; i < lines.length; i++) o.text(lines[i], x + 14, y + 50 + i * 16);
    }

    // ------------------------------------------------------------ Geschichten

    private static final Color INK = new Color(226, 240, 242), INK2 = new Color(160, 182, 192), MUTED = new Color(98, 121, 135);
    /** Dataviz-Palette (dunkel): Nord warm, Süd kühl — geprüft mit validate_palette.js. */
    private static final Color NORTH = new Color(0xd9, 0x59, 0x26), SOUTH = new Color(0x39, 0x87, 0xe5);

    /** Zeile unter dem Sonnenstand und Beschriftung der Sonde im Bild. */
    private void craftInfo(Overlay o, double k) {
        double tdb = TimeScale.tdb(clock.jd());
        double min = (tdb - craft.tpJd) * 1440;
        long m = Math.round(Math.abs(min));
        String tt = String.format(Locale.GERMAN, "T%s%d h %02d min", min < 0 ? "−" : "+", m / 60, m % 60);
        o.setColor(NORTH.brighter());
        o.dot(24, 68, 3.2);
        o.setFont(mono);
        o.setColor(new Color(145, 168, 179));
        o.text(String.format(Locale.GERMAN, "%s · %,.0f km vom Zentrum · %.1f km/s · %s zur größten Annäherung",
                craft.name, craft.distance(tdb), craft.speed(tdb), tt), 33, 72);
        // Beschriftung am Modell
        if (camera.project(craftW[0], craftW[1], craftW[2], tmp)) {
            double x = tmp[0] * k, y = tmp[1] * k;
            if (x > 0 && y > 0 && x < camera.width * k && y < camera.height * k) {
                o.setAlpha(0.45f);
                o.setColor(INK);
                o.line(x + 6, y - 6, x + 22, y - 22);
                o.setAlpha(1f);
                o.setFont(serifMid);
                o.text(craft.name, x + 26, y - 24);
            }
        }
        double[] nameBox = camera.project(craftW[0], craftW[1], craftW[2], tmp)
                ? new double[]{tmp[0] * k + 22, tmp[1] * k - 42, tmp[0] * k + 120, tmp[1] * k - 18} : null;
        // Ereignisse am Bahnverlauf (nur in der Voyager-Fahrt, ohne Überlappung)
        Tour tr = player.tour();
        if (tr == null || tr.overlay != Tour.Overlay.VOYAGER) return;
        List<double[]> placed = new ArrayList<>();
        if (nameBox != null) placed.add(nameBox);
        double[] a = new double[3];
        o.setFont(monoSmall);
        for (StoryEvent e : system.events()) {
            if (e.kind != StoryEvent.Kind.FLYBY) continue;
            double jd = TimeScale.tdb(e.jdUtc);
            if (!craft.covers(jd)) continue;
            craftPoint(craft.anomaly(jd), a);
            if (!camera.project(a[0], a[1], a[2], tmp)) continue;
            double x = tmp[0] * k + 8, y = tmp[1] * k + 14;
            String t = e.title + " · " + SimClock.instantOf(e.jdUtc).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm")) + " UTC";
            double[] box = {x - 2, y - 11, x + o.stringWidth(t) + 2, y + 3};
            boolean clash = false;
            for (double[] p : placed) if (box[0] < p[2] && box[2] > p[0] && box[1] < p[3] && box[3] > p[1]) { clash = true; break; }
            if (clash) continue;
            placed.add(box);
            o.setAlpha(jd <= tdb ? 0.95f : 0.6f);
            o.setColor(new Color(178, 236, 245));
            o.text(t, x, y);
        }
        o.setAlpha(1f);
    }

    /** Karte der aktuellen Station: Titel, umbrochener Text, Fortschritt, Bedienhinweis. Unterkante bei bottom. */
    private void tourCard(Overlay o, Tour tr, double vw, double bottom) {
        Tour.Step st = player.step();
        if (st == null || (st.title.isEmpty() && st.caption.isEmpty())) return;
        List<Tour.Step> steps = tr.steps();
        int idx = steps.indexOf(st);
        double w = Math.min(580, Math.max(320, vw * 0.44));
        o.setFont(body);
        List<String> lines = wrap(o, st.caption, w - 30);
        o.setFont(serifBig);
        Font tf = o.stringWidth(st.title) > w - 30 ? serifMid : serifBig;
        double bh = 58 + lines.size() * 20 + 40, x = 16, y = bottom - bh;
        float a = (float) Math.min(1, Math.max(0.15, (player.time() - st.atS) / 0.7));
        o.setAlpha(a);
        o.box(x, y, w, bh);
        o.setFont(monoSmall);
        o.setColor(MUTED);
        o.text(String.format(Locale.GERMAN, "%s · %d/%d", tr.title.toUpperCase(Locale.GERMAN), idx + 1, steps.size() - 1), x + 15, y + 20);
        o.setFont(tf);
        o.setColor(INK);
        o.text(st.title, x + 15, y + 46);
        o.setFont(body);
        o.setColor(INK2);
        for (int i = 0; i < lines.size(); i++) o.text(lines.get(i), x + 15, y + 70 + i * 20);
        // Fortschritt der ganzen Fahrt mit Stationsmarken
        double py = y + bh - 30, pw = w - 30, f = Math.min(1, player.time() / tr.duration());
        o.setColor(new Color(40, 60, 76));
        o.rect(x + 15, py, pw, 3);
        o.setColor(new Color(143, 221, 224));
        o.rect(x + 15, py, pw * f, 3);
        for (Tour.Step s2 : steps) {
            double sx = x + 15 + pw * s2.atS / tr.duration();
            o.setColor(s2.atS <= player.time() ? new Color(143, 221, 224) : new Color(70, 96, 112));
            o.rect(sx - 1, py - 3, 2, 9);
        }
        o.setFont(monoSmall);
        o.setColor(MUTED);
        o.text(player.paused() ? "angehalten · Leertaste: weiter · Esc beendet" : "Leertaste hält an · Esc beendet", x + 15, y + bh - 10);
        o.setAlpha(1f);
    }

    /** Bricht Text in Zeilen der Breite w (aktuelle Schrift). */
    private static List<String> wrap(Overlay o, String text, double w) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            String t = line.length() == 0 ? word : line + " " + word;
            if (o.stringWidth(t) > w && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(word); }
            else { line.setLength(0); line.append(t); }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /**
     * Jahreszeitenstreifen: Breite des Sonnenstands über die Fahrt (Nord warm, Süd kühl, Balken von der
     * Nulllinie), Sonnenwenden und Tagundnachtgleichen aus URA_EVENT, Marke für jetzt. Gibt die Oberkante zurück.
     */
    private double seasonsBand(Overlay o, Tour tr, double vw, double vh) {
        if (seasonLat == null || !tr.code.equals(seasonKey)) {
            seasonJd0 = tr.steps().get(0).jdUtc;
            seasonJd1 = tr.steps().get(tr.steps().size() - 1).jdUtc;
            seasonLat = new double[300];
            for (int i = 0; i < seasonLat.length; i++) {
                double jd = seasonJd0 + (seasonJd1 - seasonJd0) * i / (seasonLat.length - 1);
                seasonLat[i] = Math.toDegrees(Math.asin(Ephemeris.sunDirWorld(jd)[1]));
            }
            seasonKey = tr.code;
        }
        double x0 = 18, x1 = vw - 18, h = 52, yLab = vh - 14, zero = yLab - 22 - h / 2;
        double span = seasonJd1 - seasonJd0;
        int n = seasonLat.length;
        double bw = (x1 - x0) / n;
        o.box(x0 - 8, zero - h / 2 - 34, x1 - x0 + 16, h + 34 + 26 + 8);
        // Balken von der Nulllinie; Lücke von 1 px zwischen den Balken (Flächenfarbe)
        for (int i = 0; i < n; i++) {
            double lat = seasonLat[i], bh = Math.abs(lat) / 90 * (h / 2);
            o.setColor(lat >= 0 ? NORTH : SOUTH);
            o.setAlpha(0.9f);
            o.rect(x0 + i * bw, lat >= 0 ? zero - bh : zero, Math.max(1, bw - 1), bh);
        }
        o.setAlpha(1f);
        o.setColor(new Color(70, 96, 112));
        o.line(x0, zero, x1, zero);
        // Ereignisse
        o.setFont(monoSmall);
        for (StoryEvent e : system.events()) {
            if (e.kind != StoryEvent.Kind.SEASON || e.jdUtc < seasonJd0 - 1 || e.jdUtc > seasonJd1 + 1) continue;
            double x = x0 + (x1 - x0) * (e.jdUtc - seasonJd0) / span;
            o.setColor(MUTED);
            o.line(x, zero - h / 2 - 2, x, yLab - 12);
            String t = e.title;
            int tw = o.stringWidth(t);
            o.setColor(INK2);
            o.text(t, Math.max(x0, Math.min(x1 - tw, x - tw / 2.0)), yLab);
            spots.add(new Spot(x, zero, 8, e.title, eventLines(e)));
        }
        // Jetzt
        double jd = clock.jd(), x = x0 + (x1 - x0) * Math.max(0, Math.min(1, (jd - seasonJd0) / span));
        double lat = Math.toDegrees(Math.asin(sun[1]));
        o.setColor(INK);
        o.rect(x - 1, zero - h / 2 - 6, 2, h + 12);
        o.setFont(mono);
        String t = String.format(Locale.GERMAN, "%d · %.1f° %s", SimClock.instantOf(jd).atZone(ZoneOffset.UTC).getYear(), Math.abs(lat), lat >= 0 ? "N" : "S");
        int tw = o.stringWidth(t);
        o.text(t, Math.max(x0, Math.min(x1 - tw, x - tw / 2.0)), zero - h / 2 - 12);
        // Legende: Farbe am Zeichen, Text in Textfarbe
        o.setFont(monoSmall);
        double ly = zero - h / 2 - 22;
        o.setColor(INK2);
        o.text("SONNENSTAND", x0, ly);
        double lx = x0 + o.stringWidth("SONNENSTAND") + 14;
        o.setColor(NORTH); o.rect(lx, ly - 8, 10, 8);
        o.setColor(INK2); o.text("über Nord", lx + 14, ly);
        lx += 14 + o.stringWidth("über Nord") + 14;
        o.setColor(SOUTH); o.rect(lx, ly - 8, 10, 8);
        o.setColor(INK2); o.text("über Süd", lx + 14, ly);
        return zero - h / 2 - 34 - 18;
    }

    /**
     * Entdeckungszeitleiste 1775–2030: ein Punkt je Entdeckung (gleiche Jahre gestapelt), hell, sobald die
     * Erzählzeit sie erreicht; rechts Karten der letzten Entdeckungsjahre. Gibt die Oberkante zurück.
     */
    private double discoveryBand(Overlay o, double vw, double vh, double sy) {
        double y0 = 1775, y1 = 2030, x0 = 30, x1 = vw - 30, base = vh - 34;
        java.util.function.DoubleUnaryOperator xOf = yr -> x0 + (x1 - x0) * (yr - y0) / (y1 - y0);
        List<StoryEvent> disc = new ArrayList<>();
        for (StoryEvent e : system.events()) if (e.kind == StoryEvent.Kind.DISCOVERY) disc.add(e);
        int maxStack = 1;
        Map<Integer, Integer> stack = new HashMap<>();
        for (StoryEvent e : disc) maxStack = Math.max(maxStack, stack.merge((int) Math.floor(e.year()), 1, Integer::sum));
        double step = Math.min(6, 60.0 / maxStack), top = base - 10 - maxStack * step - 26;
        o.box(x0 - 16, top, x1 - x0 + 32, vh - 8 - top);
        o.setColor(new Color(70, 96, 112));
        o.line(x0, base, x1, base);
        // Legende: Farbe am Punkt, Text in Textfarbe
        o.setFont(monoSmall);
        o.setColor(INK2);
        o.text("ENTDECKUNGEN", x0, top + 18);
        double lx = x0 + o.stringWidth("ENTDECKUNGEN") + 16;
        o.setColor(INK); o.dot(lx, top + 14, 2.6);
        o.setColor(INK2); o.text("Mond", lx + 7, top + 18);
        lx += 7 + o.stringWidth("Mond") + 16;
        o.setColor(new Color(205, 184, 146)); o.dot(lx, top + 14, 2.6);
        o.setColor(INK2); o.text("Ring", lx + 7, top + 18);
        o.setFont(monoSmall);
        for (int yr = 1800; yr <= 2025; yr += 25) {
            double x = xOf.applyAsDouble(yr);
            o.setColor(new Color(70, 96, 112));
            o.line(x, base, x, base + 4);
            if (yr % 50 == 0) { o.setColor(MUTED); String t = Integer.toString(yr); o.text(t, x - o.stringWidth(t) / 2.0, base + 17); }
        }
        stack.clear();
        for (StoryEvent e : disc) {
            int yr = (int) Math.floor(e.year());
            int s2 = stack.merge(yr, 1, Integer::sum) - 1;
            boolean lit = !Double.isNaN(sy) && e.year() <= sy + 0.05;
            o.setColor(lit ? (e.ring != null ? new Color(205, 184, 146) : INK) : new Color(70, 96, 112));
            o.setAlpha(lit ? 0.95f : 0.6f);
            o.dot(xOf.applyAsDouble(e.year()), base - 6 - s2 * step, Math.min(2.6, step * 0.45));
            spots.add(new Spot(xOf.applyAsDouble(e.year()), base - 6 - s2 * step, Math.max(3, Math.min(2.6, step * 0.45)), e.title, eventLines(e)));
        }
        o.setAlpha(1f);
        if (!Double.isNaN(sy)) {
            double x = xOf.applyAsDouble(Math.max(y0, Math.min(y1, sy)));
            o.setColor(new Color(143, 221, 224));
            o.rect(x - 1, top + 22, 2, base - top - 18);
            o.setFont(serifMid);
            String t = Integer.toString((int) Math.floor(sy));
            int tw = o.stringWidth(t);
            o.setColor(INK);
            o.text(t, Math.max(x0, Math.min(x1 - tw, x - tw / 2.0)), top + 18);
        }
        // Karten der letzten Jahre (neueste oben)
        Map<Integer, List<String>> byYear = new java.util.TreeMap<>(java.util.Collections.reverseOrder());
        for (StoryEvent e : disc) {
            if (Double.isNaN(sy) || e.year() > sy + 0.05) continue;
            String name = e.body != null && !e.body.isEmpty() ? e.body : e.ring != null && !e.ring.isEmpty() ? "Ring\u00a0" + e.ring : e.title.replace(" entdeckt", "");
            byYear.computeIfAbsent((int) Math.floor(e.year()), q -> new ArrayList<>()).add(name);
        }
        double cw = 250, cx = vw - 16 - cw, cy = 76;
        int shown = 0;
        for (Map.Entry<Integer, List<String>> en : byYear.entrySet()) {
            if (shown++ >= 4) break;
            o.setFont(monoSmall);
            List<String> lines = wrap(o, String.join(" · ", en.getValue()), cw - 28);
            double ch = 34 + lines.size() * 15 + 8;
            if (cy + ch > top - 8) break;
            o.setAlpha(shown == 1 ? 1f : 0.8f);
            o.box(cx, cy, cw, ch);
            o.setFont(serifMid);
            o.setColor(INK);
            o.text(Integer.toString(en.getKey()), cx + 14, cy + 24);
            o.setFont(monoSmall);
            o.setColor(INK2);
            for (int i = 0; i < lines.size(); i++) o.text(lines.get(i), cx + 14, cy + 42 + i * 15);
            cy += ch + 8;
        }
        o.setAlpha(1f);
        return top - 10;
    }

    /** Karte eines Körpers der Spielwiese: Bahn, wie sie gerade ist, Masse, Roche-Grenze, Resonanzen, Ephemeride. */
    private void playCard(Overlay o, Body b, double vh) {
        MoonLayer.Instance m = null;
        for (MoonLayer.Instance x : inst) if (x.body == b) m = x;
        int i = m == null ? -1 : play.indexOf(m.playId);
        if (i <= 0) return;
        PlayBody pb = play.body(i);
        List<String> lines = playLines(i, false);
        o.setFont(monoSmall);
        int lw = 0;
        for (String l : lines) lw = Math.max(lw, o.stringWidth(l));
        double bw = Math.max(lw, 200) + 28, bh = 34 + lines.size() * 16 + 10, x = 16, y = vh - bh - 16;
        o.box(x, y, bw, bh);
        o.setFont(serifBig);
        o.setColor(new Color(232, 243, 244));
        o.text(pb.name, x + 14, y + 28);
        o.setFont(monoSmall);
        for (int k = 0; k < lines.size(); k++) {
            o.setColor(k == lines.size() - 1 ? MUTED : INK2);
            o.text(lines.get(k), x + 14, y + 50 + k * 16);
        }
    }

    /** Zeilen der Karte eines Körpers der Spielwiese; brief = nur das Wichtigste (Karte am Zeiger). */
    private List<String> playLines(int i, boolean brief) {
        PlayBody pb = play.body(i);
        double[] el = play.elements(i);
        double d = play.distance(i);
        List<String> lines = new ArrayList<>();
        String role = pb.role == PlayBody.Role.GUEST ? "Gast" : pb.role == PlayBody.Role.TEST ? "Testkörper (masselos)" : "Mond, rechnet mit Masse";
        lines.add(role + (pb.massEstimated && pb.role != PlayBody.Role.TEST ? " · Masse geschätzt" : ""));
        if (pb.massive()) {
            double kg = pb.massKg();
            lines.add(String.format(Locale.GERMAN, "Masse %s · %.3g Mondmassen · %.3g Erdmassen", massText(kg), kg / 7.342e22, kg / 5.972e24));
        }
        lines.add(String.format(Locale.GERMAN, "Abstand %,.0f km · Radius %,.0f km", d, pb.radiusKm));
        if (el[0] > 0) lines.add(String.format(Locale.GERMAN, "Bahn jetzt: a %,.0f km · e %.4f · i %.2f° · %s", el[0], el[1], el[2], period(el[3])));
        else lines.add(String.format(Locale.GERMAN, "ungebunden: e %.3f — verlässt Uranus", el[1]));
        if (brief) {
            lines.add("Ziehen verschiebt · Mausrad ändert die Masse");
            return lines;
        }
        if (pb.role != PlayBody.Role.TEST)
            lines.add(String.format(Locale.GERMAN, "Roche-Grenze %,.0f km%s", play.rocheKm(i),
                    pb.rocheArmed ? "" : " · gilt erst, wenn er einmal draußen war"));
        for (String r : play.resonances(i)) lines.add("Umlaufverhältnis zu " + r);
        if (play.compareEphemeris && pb.source != null && pb.role != PlayBody.Role.TEST) {
            double[] r = new double[3], e = new double[3];
            play.position(i, r);
            Kepler.position(pb.source.orbit, TimeScale.tdb(play.jdUtc()), e);
            double c = (r[0] * e[0] + r[1] * e[1] + r[2] * e[2]) / (len(r) * len(e));
            lines.add(String.format(Locale.GERMAN, "neben der Ephemeride: %.2f° · %,.0f km", Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c)))),
                    Math.sqrt(Math.pow(r[0] - e[0], 2) + Math.pow(r[1] - e[1], 2) + Math.pow(r[2] - e[2], 2))));
        }
        lines.add("Ziehen verschiebt · Mausrad ändert die Masse");
        return lines;
    }

    /** Zeile unter dem Sonnenstand, Ereignisse, Einführung des Szenarios, Hinweis beim Ziehen. */
    private void playOverlay(Overlay o, double vw, double vh, double k) {
        o.setFont(mono);
        o.setColor(new Color(245, 205, 150));
        o.dot(24, 68, 3.2);
        o.setColor(new Color(145, 168, 179));
        double err = play.energyError();
        o.text(String.format(Locale.GERMAN, "Spielwiese · %s · 1 s ≙ %s · Energie %s%.1e%s%s", play.title, rateLabel(settings.playRate),
                err >= 0 ? "+" : "", err, settings.playPaused ? " · angehalten" : "", playThrottled ? " · Rechner kommt nicht mit" : ""), 33, 72);
        // Ereignisse
        List<PlayEvent> ev = play.events();
        o.setFont(monoSmall);
        int shown = 0;
        for (int j = ev.size() - 1; j >= 0 && shown < 5; j--, shown++) {
            PlayEvent e = ev.get(j);
            String t = String.format(Locale.GERMAN, "T+%.2f d · %s", e.jd - play.startJd, e.text);
            o.setColor(shown == 0 ? INK : INK2);
            o.setAlpha(shown == 0 ? 1f : 0.75f);
            o.text(t, vw - o.stringWidth(t) - 16, 44 + shown * 15);
        }
        o.setAlpha(1f);
        // Einführung des Szenarios
        if (realTime < playIntroUntil && play.description != null && !play.description.isEmpty()) {
            float a = (float) Math.min(1, (playIntroUntil - realTime) / 1.5);
            double w = Math.min(460, vw * 0.36);
            o.setFont(body);
            List<String> lines = wrap(o, play.description, w - 30);
            double bh = 44 + lines.size() * 20 + 10, x = vw - w - 16, y = vh - bh - 16;
            o.setAlpha(a);
            o.box(x, y, w, bh);
            o.setFont(serifMid);
            o.setColor(INK);
            o.text(play.title, x + 15, y + 28);
            o.setFont(body);
            o.setColor(INK2);
            for (int j = 0; j < lines.size(); j++) o.text(lines.get(j), x + 15, y + 52 + j * 20);
            o.setAlpha(1f);
        }
        // beim Ziehen
        if ((dragId >= 0 || dragGuest) && !dragText.isEmpty()) {
            o.setFont(monoSmall);
            int tw = o.stringWidth(dragText);
            o.setColor(new Color(9, 16, 26));
            o.setAlpha(0.7f);
            o.rect(dragSx + 14, dragSy + 8, tw + 12, 18);
            o.setAlpha(1f);
            o.setColor(new Color(255, 220, 170));
            o.text(dragText, dragSx + 20, dragSy + 21);
        }
    }

    private static String rateLabel(double r) {
        if (r < 1) return String.format(Locale.GERMAN, "%.1f h", r * 24);
        return String.format(Locale.GERMAN, "%.0f d", r);
    }

    // ================================================================== Politur (Phase 8)

    /** Ein Bild für das Standbild: ohne Zeitschritt, ohne Symbole, Hilfe und Karte am Zeiger. */
    public Frame renderStill(int w, int h, double toView) {
        clean = true;
        try { return render(w, h, 0, toView); }
        finally { clean = false; }
    }

    /** Textfelder für das PNG des Standbilds: Szene, Sonnenstand, Einstellungen. */
    public Map<String, String> snapshotInfo() {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        ZoneId zone = ZoneId.systemDefault();
        double lat = Math.toDegrees(Math.asin(sun[1]));
        m.put("Title", "Uranus · " + clock.instant().atZone(zone).format(DATE));
        m.put("Description", String.format(Locale.GERMAN, "Sonne über %.1f° %s · %.2f AE · %s", Math.abs(lat), lat >= 0 ? "Nord" : "Süd",
                Ephemeris.sunDistanceAu(clock.jd()), rateText()));
        m.put("Szene", clock.instant().toString());
        StringBuilder c = new StringBuilder();
        c.append(settings.spaceMode == 0 ? "Mulde" : "Gitterraum").append(" · ").append(settings.scaleMode == 0 ? "gestaucht" : "maßstabsgetreu")
                .append(" · ").append(new String[]{"Hauptmonde", "+ innere", "alle 29"}[Math.max(0, Math.min(2, settings.moonLevel))]);
        String[][] layers = {{"Bahnen", "" + settings.trails}, {"Linse", "" + settings.lens}, {"Bloom", "" + settings.bloom},
                {"Achse", "" + settings.axis}, {"Wellen", "" + settings.waves}, {"Magnetfeld", "" + settings.magneto}, {"Lichtbahnen", "" + settings.geodesics}};
        for (String[] l : layers) if (Boolean.parseBoolean(l[1])) c.append(" · ").append(l[0]);
        if (settings.focus != null) c.append(" · Fokus ").append(settings.focus.name);
        if (player.tour() != null) c.append(" · Geschichte „").append(player.tour().title).append('“');
        if (play != null) c.append(" · Spielwiese „").append(play.title).append('“');
        m.put("Comment", c.toString());
        m.put("Software", "Uranus (com.dan.uranus)");
        m.put("Creation Time", java.time.OffsetDateTime.now().withNano(0).toString());
        return m;
    }

    /** Symbole oben rechts im Bild: 0 = Standbild, 1 = Hilfe (Fensterkoordinaten x, y, Breite, Höhe). */
    public static double[] iconRect(int i, double vw) {
        double s = 30, h = 24, gap = 6, y = 30;
        return new double[]{vw - 14 - s - (1 - i) * (s + gap), y, s, h};
    }

    /** Knopf „Auf Standard zurück“ der offenen Hilfetafel, sonst null. */
    public double[] helpButton() { return helpButton; }

    /** Referenzweg ohne die Abkürzungen von Phase 8 (für den Pixelvergleich in den Prüfungen). */
    public static void setExactReference(boolean on) { PlanetLayer.exact = on; }

    /** Wie oft der Himmel unverändert übernommen wurde. */
    public long skyCacheHits() { return sky.cacheHits(); }

    /** Die Karte am Zeiger im letzten Bild (null = keine). */
    public HoverInfo hoverShown() { return hoverShown; }

    /** Was eine Karte an der Stelle (mx, my) im letzten Bild zeigen würde, ohne Wartezeit (für Prüfungen). */
    public HoverInfo probe(double mx, double my) {
        Card c = hoverFind(mx, my, lastToView, camera.width * lastToView);
        return c == null ? null : new HoverInfo(c.kind, c.title, new ArrayList<>(c.lines), mx, my, 0, 0);
    }

    /** Was die Karte am Zeiger zeigt. */
    public static final class HoverInfo {
        public final String kind, title;
        public final List<String> lines;
        public final double x, y, w, h;
        HoverInfo(String kind, String title, List<String> lines, double x, double y, double w, double h) {
            this.kind = kind; this.title = title; this.lines = lines; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    /** Stelle auf einer Zeitleiste mit eigener Karte. */
    private static final class Spot {
        final double x, y, r; final String title; final List<String> lines;
        Spot(double x, double y, double r, String title, List<String> lines) { this.x = x; this.y = y; this.r = r; this.title = title; this.lines = lines; }
    }

    /** Inhalt einer Karte; hint ist die gedämpfte letzte Zeile. */
    private static final class Card {
        final String kind, title, hint; final List<String> lines;
        Card(String kind, String title, List<String> lines, String hint) { this.kind = kind; this.title = title; this.lines = lines; this.hint = hint; }
    }

    private static final double HOVER_DELAY_S = 0.3;

    private void icons(Overlay o, double vw) {
        double mx = settings.mouseX, my = settings.mouseY;
        for (int i = 0; i < 2; i++) {
            double[] r = iconRect(i, vw);
            boolean over = mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
            boolean on = i == 1 && settings.help;
            o.setAlpha(over || on ? 1f : 0.62f);
            o.box(r[0], r[1], r[2], r[3]);
            o.setColor(over || on ? INK : INK2);
            double cx = r[0] + r[2] / 2, cy = r[1] + r[3] / 2;
            if (i == 0) {                                             // Kamera: Gehäuse, Sucher, Objektiv
                o.line(cx - 8, cy - 4, cx + 8, cy - 4); o.line(cx + 8, cy - 4, cx + 8, cy + 6);
                o.line(cx + 8, cy + 6, cx - 8, cy + 6); o.line(cx - 8, cy + 6, cx - 8, cy - 4);
                o.line(cx - 3, cy - 4, cx - 1, cy - 7); o.line(cx - 1, cy - 7, cx + 3, cy - 7); o.line(cx + 3, cy - 7, cx + 4, cy - 4);
                o.dot(cx, cy + 1, 2.6);
            } else {
                o.setFont(mono);
                o.text("?", cx - o.stringWidth("?") / 2.0, cy + 4.5);
            }
        }
        o.setAlpha(1f);
    }

    /** Fehler im Render-Thread: eine dezente Zeile unten rechts (10 s lang). */
    private void errorLine(Overlay o, double vw, double vh) {
        String e = settings.renderError;
        if (e == null) return;
        o.setFont(monoSmall);
        o.setColor(new Color(229, 143, 118));
        String t = "Fehler beim Zeichnen: " + e + " — Einzelheiten in uranus.log";
        o.text(t, vw - o.stringWidth(t) - 14, vh - 40);
    }

    /** Hilfetafel: Tasten und Mausgriffe passend zum Modus, Knopf „Auf Standard zurück“. */
    private void help(Overlay o, double vw, double vh) {
        List<String[]> rows = new ArrayList<>();
        String mode;
        if (play != null) {
            mode = "Spielwiese";
            rows.add(new String[]{"Mond ziehen", "verschieben; beim Loslassen Kreisbahn plus Schwung"});
            rows.add(new String[]{"Mausrad über Mond", "Masse in Stufen von ×√10"});
            rows.add(new String[]{"Umschalt + Ziehen", "Gast setzen, Mausrad wählt die Masse"});
            rows.add(new String[]{"Strg+Z", "Rückgängig"});
            rows.add(new String[]{"Leertaste · + / −", "anhalten · Tempo"});
            rows.add(new String[]{"Esc", "Griff lösen, Auswahl aufheben"});
        } else if (player.tour() != null) {
            mode = "Geschichte „" + player.tour().title + "“";
            rows.add(new String[]{"Leertaste", "Fahrt anhalten / weiter"});
            rows.add(new String[]{"Esc · Klick · Ziehen · Mausrad", "Fahrt beenden, Uhr und Ebenen wie vorher"});
        } else {
            mode = "Übersicht";
            rows.add(new String[]{"Ziehen · Mausrad", "Kamera drehen · zoomen"});
            rows.add(new String[]{"Klick auf einen Mond", "hinfliegen, folgen, Karte"});
            rows.add(new String[]{"Doppelklick ins Leere · Esc", "zurück zur Übersicht"});
            rows.add(new String[]{"Leertaste · + / −", "Zeit anhalten · Tempo"});
        }
        rows.add(new String[]{"Überfahren", "kurze Karte nach 0,3 s: Monde, Ringe, Uranus, Sonde, Zeitleisten"});
        rows.add(new String[]{"0 · 1–5", "Uranus · Miranda, Ariel, Umbriel, Titania, Oberon"});
        rows.add(new String[]{"G · T · L", "Mulde/Gitterraum · Bahnen · Beschriftung"});
        rows.add(new String[]{"S", "Standbild in doppelter Größe nach " + (settings.snapshotDir.isEmpty() ? "Bilder\\Uranus" : settings.snapshotDir)});
        rows.add(new String[]{"F1 · ?", "diese Hilfe ein und aus"});
        o.setFont(mono);
        double kw = 0, dw = 0;
        for (String[] r : rows) { kw = Math.max(kw, o.stringWidth(r[0])); dw = Math.max(dw, o.stringWidth(r[1])); }
        String foot1 = "setzt Ebenen, Raum, Maßstab, Monde und Tempo zurück", foot2 = "F1, ? oder Esc schließt · Einstellungen werden beim Beenden gemerkt";
        String bt = "Auf Standard zurück";
        double btw = o.stringWidth(bt) + 28;
        o.setFont(monoSmall);
        double fw = Math.max(btw + 14 + o.stringWidth(foot1), o.stringWidth(foot2));
        o.setFont(mono);
        double bw = Math.min(vw - 40, Math.max(kw + dw + 26, fw) + 44), lh = 20, bh = 78 + rows.size() * lh + 76;
        double x = (vw - bw) / 2, y = Math.max(70, (vh - bh) / 2);
        o.box(x, y, bw, bh);
        o.setFont(serifBig);
        o.setColor(INK);
        o.text("Bedienung", x + 22, y + 36);
        o.setFont(monoSmall);
        o.setColor(MUTED);
        o.text(mode.toUpperCase(Locale.GERMAN), x + 22, y + 56);
        o.setFont(mono);
        for (int i = 0; i < rows.size(); i++) {
            double ty = y + 84 + i * lh;
            o.setColor(new Color(178, 236, 245));
            o.text(rows.get(i)[0], x + 22, ty);
            o.setColor(INK2);
            o.text(rows.get(i)[1], x + 22 + kw + 26, ty);
        }
        // Knopf „Auf Standard zurück“
        double bx = x + 22, by = y + bh - 62, bwid = btw, bht = 28;
        boolean over = settings.mouseX >= bx && settings.mouseX <= bx + bwid && settings.mouseY >= by && settings.mouseY <= by + bht;
        o.setAlpha(over ? 1f : 0.8f);
        o.box(bx, by, bwid, bht);
        o.setColor(over ? INK : new Color(178, 236, 245));
        o.text(bt, bx + 14, by + 19);
        o.setAlpha(1f);
        o.setFont(monoSmall);
        o.setColor(MUTED);
        o.text(foot1, bx + bwid + 14, by + 18);
        o.text(foot2, bx, by + bht + 20);
        helpButton = new double[]{bx, by, bwid, bht};
    }

    /** Karte am Zeiger nach 0,3 s Verweilen; Werte dieses Augenblicks. */
    private void hoverCard(Overlay o, double vw, double vh, double k) {
        double mx = settings.mouseX, my = settings.mouseY;
        HoverInfo shown = null;
        try {
            if (mx < 0 || settings.mouseDown || !settings.hoverCards) { hoverMx = -1; return; }
            long now = System.nanoTime();
            if (Math.abs(mx - hoverMx) > 2 || Math.abs(my - hoverMy) > 2) { hoverMx = mx; hoverMy = my; hoverSince = now; return; }
            if (now - hoverSince < HOVER_DELAY_S * 1e9) return;
            Card c = hoverFind(mx, my, k, vw);
            if (c == null) return;
            shown = drawCard(o, c, mx, my, vw, vh);
        } finally {
            hoverShown = shown;
        }
    }

    private HoverInfo drawCard(Overlay o, Card c, double mx, double my, double vw, double vh) {
        o.setFont(monoSmall);
        double lw = 0;
        for (String l : c.lines) lw = Math.max(lw, o.stringWidth(l));
        if (c.hint != null) lw = Math.max(lw, o.stringWidth(c.hint));
        o.setFont(serifMid);
        lw = Math.max(lw, o.stringWidth(c.title));
        double w = lw + 26, h = 34 + c.lines.size() * 15 + (c.hint != null ? 17 : 0) + 6;
        double x = mx + 16, y = my + 18;
        if (x + w > vw - 8) x = mx - w - 12;
        if (y + h > vh - 8) y = my - h - 12;
        x = Math.max(8, x); y = Math.max(8, y);
        o.box(x, y, w, h);
        o.setColor(INK);
        o.text(c.title, x + 13, y + 24);
        o.setFont(monoSmall);
        o.setColor(INK2);
        for (int i = 0; i < c.lines.size(); i++) o.text(c.lines.get(i), x + 13, y + 43 + i * 15);
        if (c.hint != null) { o.setColor(MUTED); o.text(c.hint, x + 13, y + 43 + c.lines.size() * 15 + 2); }
        return new HoverInfo(c.kind, c.title, new ArrayList<>(c.lines), x, y, w, h);
    }

    /** Was liegt unter dem Zeiger? Symbole, Zeitleisten, Monde, Sonde, dann Ringe vor Uranus. */
    private Card hoverFind(double mx, double my, double k, double vw) {
        for (int i = 0; i < 2; i++) {
            double[] r = iconRect(i, vw);
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3])
                return i == 0 ? new Card("symbol", "Standbild", List.of("Taste S · PNG in doppelter Fenstergröße, mit Beschriftung",
                        "nach " + (settings.snapshotDir.isEmpty() ? "Bilder\\Uranus" : settings.snapshotDir)), null)
                        : new Card("symbol", "Hilfe", List.of("F1 oder ? · alle Tasten und Mausgriffe"), null);
        }
        Spot bs = null;
        double bd = Double.MAX_VALUE;
        for (Spot sp : spots) {
            double d = Math.hypot(sp.x - mx, sp.y - my);
            if (d < sp.r + 4 && d < bd) { bd = d; bs = sp; }
        }
        if (bs != null) return new Card("zeitleiste", bs.title, bs.lines, null);
        MoonLayer.Instance bm = null;
        bd = Double.MAX_VALUE;
        for (MoonLayer.Instance m : inst) {
            if (!m.onScreen || m.fade < 0.3f) continue;
            double d = Math.hypot(m.sx * k - mx, m.sy * k - my), reach = Math.max(10, m.sr * k + 6);
            if (d < reach && d < bd) { bd = d; bm = m; }
        }
        if (bm != null) {
            if (play != null && bm.playId >= 0) {
                int i = play.indexOf(bm.playId);
                if (i > 0) return new Card("mond", play.body(i).name, playLines(i, true).subList(0, playLines(i, true).size() - 1),
                        "Ziehen verschiebt · Mausrad ändert die Masse");
            } else return moonCard(bm.body);
        }
        if (craftOn && camera.project(craftW[0], craftW[1], craftW[2], tmp) && Math.hypot(tmp[0] * k - mx, tmp[1] * k - my) < 16) return craftCard();
        // Sehstrahl: trifft er einen Ring vor Uranus, oder Uranus selbst?
        double[] d = new double[3];
        camera.rayDir(mx / k, my / k, d);
        double ox = camera.cx, oy = camera.cy - ucY, oz = camera.cz;
        double b = ox * d[0] + oy * d[1] + oz * d[2], c = ox * ox + oy * oy + oz * oz - SpaceMap.PR * SpaceMap.PR, disc = b * b - c;
        double tS = disc > 0 ? -b - Math.sqrt(disc) : -1;
        double tP = Math.abs(d[1]) > 1e-9 ? -oy / d[1] : -1;
        if (tP > 0 && (tS <= 0 || tP < tS) && flightT < 0.5) {
            double px = ox + tP * d[0], pz = oz + tP * d[2];
            Card rc = ringCard(px, pz, tP);
            if (rc != null) return rc;
        }
        if (tS > 0) return uranusCard();
        return null;
    }

    private Card moonCard(Body b) {
        double tdb = TimeScale.tdb(clock.jd());
        double[] r = new double[3], r2 = new double[3];
        Kepler.position(b.orbit, tdb, r);
        Kepler.position(b.orbit, tdb + 60 / 86_400.0, r2);
        double dist = len(r), v = Math.sqrt(Math.pow(r2[0] - r[0], 2) + Math.pow(r2[1] - r[1], 2) + Math.pow(r2[2] - r[2], 2)) / 60;
        List<String> l = new ArrayList<>();
        l.add(b.group.label() + (b.orbit.isRetrograde() ? " · läuft rückläufig" : ""));
        l.add(String.format(Locale.GERMAN, "Abstand jetzt %,.0f km · %.2f km/s", dist, v));
        OrbitElements oe = b.orbit;
        double rate = oe.meanAnomalyRate();
        if (oe.e < 0.001) l.add(String.format(Locale.GERMAN, "Bahn fast kreisrund (e %.4f) · Umlauf %s", oe.e, period(oe.periodDays)));
        else if (rate > 0) {
            double m = ((oe.meanAnomalyDeg + rate * (tdb - oe.epochJd)) % 360 + 360) % 360;
            l.add(String.format(Locale.GERMAN, "%s nach dem Perizentrum · Umlauf %s", duration(m / rate), period(oe.periodDays)));
        }
        double kg = UranusSystem.massOf(b);
        l.add(b.massKg > 0 ? "Masse " + massText(kg) + " (gemessen)" : "Masse ≈ " + massText(kg) + " (geschätzt aus 1,3 g/cm³)");
        l.add(!Double.isNaN(b.fitMaxDeg) ? String.format(Locale.GERMAN, "Bahn trifft Horizons 1975–2030 auf %.2f°", b.fitMaxDeg) : b.orbitSource);
        return new Card("mond", b.name, l, "Klick: hinfliegen und Karte");
    }

    private Card craftCard() {
        double tdb = TimeScale.tdb(clock.jd());
        double min = (tdb - craft.tpJd) * 1440;
        long mm = Math.round(Math.abs(min));
        List<String> l = new ArrayList<>();
        l.add(String.format(Locale.GERMAN, "Abstand zum Zentrum %,.0f km · %.1f km/s", craft.distance(tdb), craft.speed(tdb)));
        l.add(String.format(Locale.GERMAN, "T%s%d h %02d min zur größten Annäherung", min < 0 ? "−" : "+", mm / 60, mm % 60));
        l.add(String.format(Locale.GERMAN, "Hyperbel e %.3f · Perizentrum %,.0f km · ±%,.0f km zu Horizons", craft.e, craft.qKm, craft.fitMaxKm));
        return new Card("sonde", craft.name, l, null);
    }

    /** Ring an der Stelle (x, z) der Ringebene (Welt, relativ zu Uranus), t = Entfernung längs des Strahls. */
    private Card ringCard(double x, double z, double t) {
        List<Ring> rs = system.rings();
        double rRu = Math.hypot(x, z) / SpaceMap.PR, th = Math.atan2(z, x);
        double tol = 6 * t / camera.foc / SpaceMap.PR, tdb = TimeScale.tdb(clock.jd());
        Ring best = null;
        double bd = Double.MAX_VALUE, bR = 0, bW = 0;
        for (int i = 0; i < rs.size(); i++) {
            if (i < ringReveal.length && ringReveal[i] < 0.5f) continue;
            Ring rg = rs.get(i);
            double peri = rg.periapsis(tdb);
            double ri = rg.dusty ? rg.radiusRu : rg.radiusRuAt(th, peri), wi = rg.dusty ? rg.widthRu : rg.widthRuAt(th, peri);
            double dd = Math.abs(rRu - ri);
            if (dd < Math.max(wi * 0.5, tol) && dd < bd) { bd = dd; best = rg; bR = ri; bW = wi; }
        }
        if (best == null) return null;
        double ru = UranusSystem.URANUS_RADIUS_KM;
        List<String> l = new ArrayList<>();
        l.add(String.format(Locale.GERMAN, "hier %,.0f km vom Zentrum · %,.0f km breit", bR * ru, bW * ru));
        if (best.ecc > 0) l.add(String.format(Locale.GERMAN, "Ellipse e %.5f: %,.0f bis %,.0f km", best.ecc, best.aKm * (1 - best.ecc), best.aKm * (1 + best.ecc)));
        if (best.dusty) l.add("Staubring, im Gegenlicht am hellsten");
        else if (best.tauMax > 0) l.add(best.tauMin == best.tauMax ? String.format(Locale.GERMAN, "optische Tiefe %.2g", best.tauMax)
                : String.format(Locale.GERMAN, "optische Tiefe %.2g bis %.2g", best.tauMin, best.tauMax));
        if (best.discoveredYear > 0) l.add("Entdeckt " + best.discoveredYear + (best.discoveredBy.isEmpty() ? "" : " · " + best.discoveredBy));
        return new Card("ring", "Ring " + best.name, l, null);
    }

    private Card uranusCard() {
        double jd = clock.jd();
        double lat = Math.toDegrees(Math.asin(sun[1])), later = Math.toDegrees(Math.asin(Ephemeris.sunDirWorld(jd + 60)[1]));
        String season = lat >= 0 ? (later > lat ? "Frühling im Norden" : "Sommer im Norden") : (later < lat ? "Frühling im Süden" : "Sommer im Süden");
        List<String> l = new ArrayList<>();
        l.add(String.format(Locale.GERMAN, "Sonne über %.1f° %s · %s", Math.abs(lat), lat >= 0 ? "Nord" : "Süd", season));
        StoryEvent next = null;
        for (StoryEvent e : system.events())
            if (e.kind == StoryEvent.Kind.SEASON && e.jdUtc > jd && (next == null || e.jdUtc < next.jdUtc)) next = e;
        if (next != null) l.add("Als Nächstes: " + next.title + " am " + SimClock.instantOf(next.jdUtc).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        double au = Ephemeris.sunDistanceAu(jd);
        l.add(String.format(Locale.GERMAN, "Abstand zur Sonne %.2f AE · Licht %.1f h unterwegs", au, au * 499.005 / 3600));
        l.add("Tag 17 h 14 min, rückläufig · Achse 97,77° gekippt");
        l.add(String.format(Locale.GERMAN, "Radius %,.0f km · %.1f Erdmassen", UranusSystem.URANUS_RADIUS_KM, UranusSystem.URANUS_MASS_KG / 5.972e24));
        return new Card("uranus", "Uranus", l, "Doppelklick ins Leere: Übersicht");
    }

    /** Zeilen einer Ereigniskarte: Datum und Text, umbrochen. */
    private static List<String> eventLines(StoryEvent e) {
        List<String> l = new ArrayList<>();
        java.time.ZonedDateTime z = SimClock.instantOf(e.jdUtc).atZone(ZoneOffset.UTC);
        l.add(e.precision == StoryEvent.Precision.YEAR ? Integer.toString(z.getYear()) : z.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)));
        StringBuilder line = new StringBuilder();
        for (String w : e.text.split(" ")) {
            if (line.length() + w.length() + 1 > 56) { l.add(line.toString()); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) l.add(line.toString());
        return l;
    }

    private static String duration(double days) {
        if (days < 1) { long m = Math.round(days * 1440); return String.format(Locale.GERMAN, "%d h %02d min", m / 60, m % 60); }
        if (days < 400) return String.format(Locale.GERMAN, "%.1f Tage", days);
        return String.format(Locale.GERMAN, "%.1f Jahre", days / 365.25);
    }

    private static String period(double days) {
        if (days < 1) return String.format(Locale.GERMAN, "%.1f h", days * 24);
        if (days < 400) return String.format(Locale.GERMAN, "%.2f d", days);
        return String.format(Locale.GERMAN, "%.2f Jahre", days / 365.25);
    }
}
