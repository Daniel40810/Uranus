package com.dan.uranus.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uranus, 29 Monde, 13 Ringe.
 * <p>
 * Zwei Quellen mit demselben Inhalt: der eingebaute Datensatz ({@link #builtIn()}) und das Schema
 * DEMO (URA_BODY, URA_ORBIT, URA_RING über URA_API). Die App startet mit dem eingebauten Satz und
 * übernimmt den aus der Datenbank, sobald er geladen ist.
 * <p>
 * Die Fakten der Monde (Radius, Entdeckung, Namensherkunft) stehen hier im Code. Bahnen und Ringe
 * liest der eingebaute Satz aus zwei mitgelieferten Dateien, die {@code tools/UraGen} aus den
 * gesicherten Quellen erzeugt — dieselben Werte, die auch in die Datenbank gehen.
 */
public final class UranusSystem {

    public static final double URANUS_RADIUS_KM = 25_559.0;
    public static final double URANUS_MASS_KG = 8.681e25;
    /** GM des Uranus in km³/s². */
    public static final double URANUS_GM = 5_793_939.0;
    /** Zonaler Schwerekoeffizient J2 (Bezugsradius 25 559 km). */
    public static final double J2 = 3.51068e-3;
    /** J4 (Jacobson 2014, Bezugsradius 25 559 km) — für die Spielwiese. */
    public static final double J4 = -3.417e-5;
    /** Angenommene Dichte der kleinen Monde (kg/m³), deren Masse nicht gemessen ist; steht auch in URA_BODY. */
    public static final double DENSITY_ASSUMED = 1300;

    /** Masse eines Monds: gemessen, sonst aus Radius und angenommener Dichte. */
    public static double massOf(Body b) {
        return b.massKg > 0 ? b.massKg : DENSITY_ASSUMED * 4.0 / 3.0 * Math.PI * Math.pow(b.radiusKm * 1000, 3);
    }
    /** Siderische Rotation in Tagen (17 h 14 min 24 s), rückläufig. */
    public static final double ROTATION_DAYS = 0.71833;
    /** Neigung der Drehachse gegen die Bahnebene. */
    public static final double OBLIQUITY_DEG = 97.77;
    /** Gravitationskonstante in km³/(kg·s²). */
    public static final double G_KM = 6.6743e-20;

    private final List<Body> bodies;
    private final List<Ring> rings;
    private final List<StoryEvent> events;
    private final List<Craft> crafts;
    private final List<Tour> tours;
    private final List<Scenario> scenarios;
    /** Woher der Datensatz stammt, für die Statuszeile. */
    public final String origin;

    public UranusSystem(List<Body> bodies, List<Ring> rings, String origin) {
        this(bodies, rings, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), origin);
    }

    public UranusSystem(List<Body> bodies, List<Ring> rings, List<StoryEvent> events, List<Craft> crafts, List<Tour> tours, String origin) {
        this(bodies, rings, events, crafts, tours, Collections.emptyList(), origin);
    }

    public UranusSystem(List<Body> bodies, List<Ring> rings, List<StoryEvent> events, List<Craft> crafts, List<Tour> tours,
                        List<Scenario> scenarios, String origin) {
        this.scenarios = Collections.unmodifiableList(new ArrayList<>(scenarios));
        this.bodies = Collections.unmodifiableList(new ArrayList<>(bodies));
        this.rings = Collections.unmodifiableList(new ArrayList<>(rings));
        List<StoryEvent> ev = new ArrayList<>(events);
        ev.sort((a, b) -> Double.compare(a.jdUtc, b.jdUtc));
        this.events = Collections.unmodifiableList(ev);
        this.crafts = Collections.unmodifiableList(new ArrayList<>(crafts));
        this.tours = Collections.unmodifiableList(new ArrayList<>(tours));
        this.origin = origin;
    }

    public List<Body> bodies() { return bodies; }

    public List<Ring> rings() { return rings; }

    /** Ereignisse, nach Zeit sortiert. */
    public List<StoryEvent> events() { return events; }

    public List<Craft> crafts() { return crafts; }

    public List<Tour> tours() { return tours; }

    /** Szenarien der Spielwiese: eingebaute zuerst, dann eigene. */
    public List<Scenario> scenarios() { return scenarios; }

    public Scenario scenario(String code) {
        for (Scenario s : scenarios) if (s.code.equals(code)) return s;
        return null;
    }

    /** Derselbe Datensatz mit anderen Szenarien (nach dem Speichern eines eigenen). */
    public UranusSystem withScenarios(List<Scenario> s) {
        return new UranusSystem(bodies, rings, events, crafts, tours, s, origin);
    }

    public Tour tour(String code) {
        for (Tour t : tours) if (t.code.equals(code)) return t;
        return null;
    }

    public Craft craft(String name) {
        for (Craft c : crafts) if (c.name.equals(name)) return c;
        return null;
    }

    public Ring ring(String name) {
        for (Ring r : rings) if (r.name.equals(name)) return r;
        return null;
    }

    public Body find(String name) {
        for (Body b : bodies) if (b.name.equalsIgnoreCase(name)) return b;
        return null;
    }

    /** Umlaufzeit in Tagen aus der großen Halbachse (3. Kepler-Gesetz, Uranusmasse). */
    public static double periodFromA(double aKm) {
        return 2 * Math.PI * Math.sqrt(aKm * aKm * aKm / URANUS_GM) / 86_400.0;
    }

    // ================================================================== Fakten

    /** Fakten eines Mondes ohne Bahn. */
    public static final class Facts {
        public final String name;
        public final int naifId;
        public final BodyGroup group;
        public final double radiusKm, massKg, albedo;
        public final float red, green, blue;
        public final Body.Surface surface;
        public final int year;
        public final String by, namesake;

        Facts(String name, int naifId, BodyGroup group, double radiusKm, double massKg, double albedo,
              float red, float green, float blue, Body.Surface surface, int year, String by, String namesake) {
            this.name = name; this.naifId = naifId; this.group = group; this.radiusKm = radiusKm; this.massKg = massKg;
            this.albedo = albedo; this.red = red; this.green = green; this.blue = blue; this.surface = surface;
            this.year = year; this.by = by; this.namesake = namesake;
        }

        public Body with(OrbitElements o, OrbitQuality q, String source, double fitMax) {
            return new Body(name, naifId, group, radiusKm, massKg, albedo, red, green, blue, surface,
                    year, by, namesake, o, q, source, fitMax);
        }
    }

    private static final List<Facts> FACTS = new ArrayList<>();

    static {
        major("Miranda", 705, 235.8, 6.4e19, 0.32, .66f, .66f, .67f, Body.Surface.PATCHWORK, 1948, "Gerard Kuiper", "„Der Sturm“ — Prosperos Tochter");
        major("Ariel", 701, 578.9, 1.251e21, 0.53, .80f, .80f, .81f, Body.Surface.CANYONS, 1851, "William Lassell", "„Der Sturm“ und Pope — ein Luftgeist");
        major("Umbriel", 702, 584.7, 1.275e21, 0.26, .40f, .40f, .42f, Body.Surface.DARK_RING, 1851, "William Lassell", "Pope, „Der Lockenraub“ — ein düsterer Geist");
        major("Titania", 703, 788.4, 3.40e21, 0.35, .66f, .61f, .57f, Body.Surface.CHASMA, 1787, "William Herschel", "„Ein Sommernachtstraum“ — Elfenkönigin");
        major("Oberon", 704, 761.4, 3.076e21, 0.31, .55f, .47f, .42f, Body.Surface.CRATERED, 1787, "William Herschel", "„Ein Sommernachtstraum“ — Elfenkönig");
        inner("Cordelia", 706, 20, 1986, "„König Lear“");
        inner("Ophelia", 707, 21, 1986, "„Hamlet“");
        inner("S/2025 U1", 75052, 5, 2025, "noch unbenannt (JWST)");
        inner("Bianca", 708, 26, 1986, "„Der Widerspenstigen Zähmung“");
        inner("Cressida", 709, 40, 1986, "„Troilus und Cressida“");
        inner("Desdemona", 710, 32, 1986, "„Othello“");
        inner("Juliet", 711, 47, 1986, "„Romeo und Julia“");
        inner("Portia", 712, 68, 1986, "„Der Kaufmann von Venedig“");
        inner("Rosalind", 713, 36, 1986, "„Wie es euch gefällt“");
        inner("Cupid", 727, 9, 2003, "„Timon von Athen“");
        inner("Belinda", 714, 45, 1986, "Pope, „Der Lockenraub“");
        inner("Perdita", 725, 15, 1999, "„Das Wintermärchen“");
        inner("Puck", 715, 81, 1985, "„Ein Sommernachtstraum“");
        inner("Mab", 726, 12, 2003, "„Romeo und Julia“");
        irregular("Francisco", 722, 11, 2001, "„Der Sturm“");
        irregular("Caliban", 716, 36, 1997, "„Der Sturm“");
        irregular("Stephano", 720, 16, 1999, "„Der Sturm“");
        irregular("S/2023 U1", 75051, 4, 2023, "noch unbenannt");
        irregular("Trinculo", 721, 9, 2001, "„Der Sturm“");
        irregular("Sycorax", 717, 75, 1997, "„Der Sturm“");
        irregular("Margaret", 723, 10, 2003, "„Viel Lärm um nichts“");
        irregular("Prospero", 718, 25, 1999, "„Der Sturm“");
        irregular("Setebos", 719, 24, 1999, "„Der Sturm“");
        irregular("Ferdinand", 724, 10, 2001, "„Der Sturm“");
    }

    private static void major(String name, int naif, double rKm, double mass, double albedo, float cr, float cg, float cb,
                              Body.Surface s, int year, String by, String namesake) {
        FACTS.add(new Facts(name, naif, BodyGroup.MAJOR, rKm, mass, albedo, cr, cg, cb, s, year, by, namesake));
    }

    private static void inner(String name, int naif, double rKm, int year, String namesake) {
        FACTS.add(new Facts(name, naif, BodyGroup.INNER, rKm, 0, 0.07, .52f, .52f, .54f, Body.Surface.SMALL_DARK, year,
                year >= 2025 ? "JWST" : year >= 2003 ? "Hubble" : "Voyager 2", namesake));
    }

    private static void irregular(String name, int naif, double rKm, int year, String namesake) {
        FACTS.add(new Facts(name, naif, BodyGroup.IRREGULAR, rKm, 0, 0.05, .62f, .46f, .40f, Body.Surface.CAPTURED_RED,
                year, "Bodenteleskop", namesake));
    }

    /** Die Fakten aller 29 Monde in Anzeigereihenfolge. */
    public static List<Facts> facts() { return Collections.unmodifiableList(FACTS); }

    /** Darstellung der Ringe (Deckkraft, Farbe): steht nur im Code. */
    public static float[] ringLook(String name) {
        switch (name) {
            case "ζ": return new float[]{0.05f, .63f, .75f, .82f};
            case "α": case "γ": return new float[]{0.42f, .82f, .85f, .87f};
            case "β": return new float[]{0.40f, .82f, .85f, .87f};
            case "η": return new float[]{0.26f, .80f, .83f, .85f};
            case "δ": return new float[]{0.48f, .84f, .86f, .88f};
            case "λ": return new float[]{0.20f, .80f, .83f, .85f};
            case "ε": return new float[]{0.85f, .89f, .91f, .92f};
            case "ν": return new float[]{0.07f, .84f, .59f, .55f};
            case "μ": return new float[]{0.075f, .43f, .65f, 1.0f};
            default: return new float[]{0.30f, .80f, .83f, .85f};           // Ringe 6, 5, 4
        }
    }

    // ================================================================== Eingebauter Datensatz

    private static UranusSystem cached;

    /** Der eingebaute Datensatz (dieselben Werte wie in der Datenbank). */
    public static synchronized UranusSystem builtIn() {
        if (cached == null) {
            try {
                cached = new UranusSystem(readBodies(), readRings(), readEvents(), readCrafts(), readTours(), readScenarios(), "eingebaut");
            } catch (IOException ex) {
                throw new IllegalStateException("Eingebaute Bahndaten fehlen: " + ex.getMessage(), ex);
            }
        }
        return cached;
    }

    private static List<String[]> csv(String resource) throws IOException {
        InputStream in = UranusSystem.class.getResourceAsStream(resource);
        if (in == null) throw new IOException(resource + " nicht im Klassenpfad");
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean head = true;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (head) { head = false; continue; }
                rows.add(line.split(";", -1));
            }
        }
        return rows;
    }

    static double num(String s) { return s == null || s.isEmpty() ? Double.NaN : Double.parseDouble(s); }

    private static List<Body> readBodies() throws IOException {
        Map<String, String[]> orbit = new HashMap<>();
        for (String[] c : csv("eingebaut_bahnen.csv")) orbit.put(c[0], c);
        List<Body> out = new ArrayList<>();
        for (Facts f : FACTS) {
            String[] c = orbit.get(f.name);
            if (c == null) throw new IOException("keine Bahn für " + f.name);
            out.add(f.with(orbitOf(c, 4), OrbitQuality.valueOf(c[2]), c[3], num(c[17])));
        }
        return out;
    }

    /** Bahn aus den Spalten frame;epoch;a;e;i;node;argp;M;P;Papsis;Pnode;poleRa;poleDec ab Spalte k. */
    static OrbitElements orbitOf(String[] c, int k) {
        return new OrbitElements(OrbitElements.Frame.valueOf(c[k]), num(c[k + 1]), num(c[k + 2]), num(c[k + 3]),
                num(c[k + 4]), num(c[k + 5]), num(c[k + 6]), num(c[k + 7]), num(c[k + 8]),
                zero(num(c[k + 9])), zero(num(c[k + 10])), num(c[k + 11]), num(c[k + 12]));
    }

    private static double zero(double v) { return Double.isNaN(v) ? 0 : v; }

    private static List<Ring> readRings() throws IOException {
        Map<String, Ring> byName = new LinkedHashMap<>();
        for (String[] c : csv("eingebaut_ringe.csv")) {
            float[] look = ringLook(c[0]);
            byName.put(c[0], new Ring(c[0], num(c[1]), num(c[2]), num(c[3]), zero(num(c[4])), zero(num(c[5])),
                    num(c[6]), num(c[7]), "J".equals(c[8]), look[0], look[1], look[2], look[3],
                    c.length > 9 && !c[9].isEmpty() ? Integer.parseInt(c[9]) : 0, c.length > 10 ? c[10] : ""));
        }
        return new ArrayList<>(byName.values());
    }

    /** Ereignisse: jd_utc;kind;precision;title;text;body;ring (Text ohne Semikolon, Zeilenumbruch als \n). */
    private static List<StoryEvent> readEvents() throws IOException {
        List<StoryEvent> out = new ArrayList<>();
        for (String[] c : csv("eingebaut_ereignisse.csv"))
            out.add(new StoryEvent(num(c[0]), StoryEvent.Kind.valueOf(c[1]), StoryEvent.Precision.valueOf(c[2]),
                    unesc(c[3]), unesc(c[4]), c[5].isEmpty() ? null : c[5], c[6].isEmpty() ? null : c[6]));
        return out;
    }

    /** Sonden: name;naif;tp_jd;q_km;ecc;incl;node;argp;gm;valid_from;valid_to;fit_max_km;source. */
    private static List<Craft> readCrafts() throws IOException {
        List<Craft> out = new ArrayList<>();
        for (String[] c : csv("eingebaut_sonden.csv"))
            out.add(new Craft(c[0], Integer.parseInt(c[1]), num(c[2]), num(c[3]), num(c[4]), num(c[5]), num(c[6]), num(c[7]),
                    num(c[8]), num(c[9]), num(c[10]), num(c[11]), unesc(c[12])));
        return out;
    }

    /**
     * Szenarien: S;code;title;start_jd;rate;origin;compare;description und
     * B;code;seq;name;role;mass_kg;estimated;radius_km;x;y;z;vx;vy;vz (ICRF, relativ zu Uranus).
     * Fehlt die Datei (vor dem ersten Lauf von UraPlayGen), gibt es keine.
     */
    private static List<Scenario> readScenarios() throws IOException {
        if (UranusSystem.class.getResource("eingebaut_szenarien.csv") == null) return Collections.emptyList();
        Map<String, String[]> head = new LinkedHashMap<>();
        Map<String, List<Scenario.Item>> items = new HashMap<>();
        for (String[] c : csv("eingebaut_szenarien.csv")) {
            if (c[0].equals("S")) head.put(c[1], c);
            else items.computeIfAbsent(c[1], k -> new ArrayList<>()).add(new Scenario.Item(unesc(c[3]), Scenario.Role.valueOf(c[4]),
                    num(c[5]), c[6].equals("J"), num(c[7]), new double[]{num(c[8]), num(c[9]), num(c[10])},
                    new double[]{num(c[11]), num(c[12]), num(c[13])}));
        }
        List<Scenario> out = new ArrayList<>();
        for (String[] h : head.values())
            out.add(new Scenario(h[1], unesc(h[2]), unesc(h[7]), num(h[3]), num(h[4]), Scenario.Origin.valueOf(h[5]), h[6].equals("J"),
                    items.getOrDefault(h[1], Collections.emptyList())));
        return out;
    }

    /** Fahrten: T;code;title;overlay;mode;description und S;code;at_s;jd;yaw;pitch;dist;target;layers;title;caption. */
    private static List<Tour> readTours() throws IOException {
        Map<String, String[]> head = new LinkedHashMap<>();
        Map<String, List<Tour.Step>> steps = new HashMap<>();
        for (String[] c : csv("eingebaut_touren.csv")) {
            if (c[0].equals("T")) head.put(c[1], c);
            else steps.computeIfAbsent(c[1], k -> new ArrayList<>()).add(new Tour.Step(num(c[2]), num(c[3]), num(c[4]), num(c[5]),
                    num(c[6]), c[7], unesc(c[8]), unesc(c[9]), unesc(c[10])));
        }
        List<Tour> out = new ArrayList<>();
        for (String[] h : head.values())
            out.add(new Tour(h[1], unesc(h[2]), unesc(h[5]), Tour.Overlay.valueOf(h[3]), Tour.TimeMode.valueOf(h[4]), steps.get(h[1])));
        return out;
    }

    /** Die Datendateien kodieren Zeilenumbrüche als \n und Semikolons als \s. */
    public static String unesc(String s) { return s.replace("\\n", "\n").replace("\\s", ";"); }

    public static String esc(String s) { return s == null ? "" : s.replace(";", "\\s").replace("\n", "\\n"); }
}
