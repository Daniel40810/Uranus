import com.dan.uranus.model.*;
import com.dan.uranus.physics.Ephemeris;
import com.dan.uranus.physics.TimeScale;
import com.dan.uranus.render.Renderer;
import com.dan.uranus.render.SceneSettings;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Phase 6 — Geschichten, ohne Fenster und ohne Datenbank:
 * Voyager-Hyperbel gegen Horizons, berechnete Ereignisse, Jahreszeiten, Fahrten, Abspieler,
 * Entdeckungsfilter, Wiederherstellung nach der Fahrt, Einrichtungsskripte 06–08.
 * Aufruf aus dem Projektordner: java -cp build/classes;build/tools Phase6Tests
 */
public class Phase6Tests {

    static int ok, bad;
    static final Path Q = Paths.get("db", "quellen");
    static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    static void check(String name, boolean c, String info) {
        if (c) ok++; else bad++;
        System.out.println((c ? "[ok]     " : "[FEHLER] ") + name + (info.isEmpty() ? "" : " — " + info));
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMANY);
        System.out.println("Phase 6 — Geschichten (ohne Verbindung)");
        UranusSystem sys = UranusSystem.builtIn();
        voyager(sys);
        seasons(sys);
        events(sys);
        tours(sys);
        player(sys);
        discovery(sys);
        year(sys);
        roundTrip(sys);
        scripts();
        System.out.println();
        System.out.println("Ergebnis: " + ok + " ok, " + bad + " Fehler");
        System.exit(bad == 0 ? 0 : 1);
    }

    static String utc(double jdUtc) { return SimClock.instantOf(jdUtc).atZone(ZoneOffset.UTC).format(HM) + " UTC"; }

    static double utcOf(double jdTdb) { return jdTdb - TimeScale.tdbMinusUtcSeconds(jdTdb) / 86_400.0; }

    // ------------------------------------------------------------------ Voyager 2
    static void voyager(UranusSystem sys) throws Exception {
        String sha = UraGen.sha(Q.resolve("voyager2_horizons.csv"));
        check("Voyager-Vektoren unverändert (SHA-256 wie im Browser berechnet)",
                sha.equals("1fd6487f8da11a2ce823acdfb8f112be47cb64f29766936bfa5b71b006dbf25d"), sha.substring(0, 16) + "…");
        List<String[]> rows = UraGen.csv(Q.resolve("voyager2_horizons.csv"));
        check("Horizons: 145 Vektoren, stündlich 21.–27.01.1986", rows.size() == 145, rows.size() + " Zeilen");
        Craft v = sys.craft("Voyager 2");
        check("Eingebaute Sonde Voyager 2 (NAIF −32, Hyperbel e > 1)", v != null && v.naifId == -32 && v.e > 1, v == null ? "fehlt" : String.format("e = %.6f", v.e));
        if (v == null) return;
        double worst = 0, worstJd = 0;
        double[] icrf = new double[3], w = new double[3], p = new double[3];
        double sample18 = Double.NaN;
        for (String[] r : rows) {
            double jd = Double.parseDouble(r[0]);
            for (int k = 0; k < 3; k++) icrf[k] = Double.parseDouble(r[1 + k]);
            Ephemeris.icrfToWorld(icrf, w);
            v.position(jd, p);
            double d = Math.sqrt((w[0] - p[0]) * (w[0] - p[0]) + (w[1] - p[1]) * (w[1] - p[1]) + (w[2] - p[2]) * (w[2] - p[2]));
            if (d > worst) { worst = d; worstJd = jd; }
            if (Math.abs(jd - 2446455.25) < 1e-6) sample18 = Math.sqrt(icrf[0] * icrf[0] + icrf[1] * icrf[1] + icrf[2] * icrf[2]);
        }
        check("Hyperbel trifft alle 145 Vektoren besser als 200 km", worst < 200,
                String.format("größte Abweichung %.1f km am %s", worst, utc(utcOf(worstJd))));
        check("Angegebene Abweichung (fit_max) stimmt", Math.abs(worst - v.fitMaxKm) < 0.5, String.format("%.1f km gerechnet, %.1f km gespeichert", worst, v.fitMaxKm));
        // größte Annäherung: Minimum des Abstands durch Goldenen Schnitt
        double lo = v.validFromJd, hi = v.validToJd, g = (Math.sqrt(5) - 1) / 2;
        for (int i = 0; i < 200; i++) {
            double m1 = hi - g * (hi - lo), m2 = lo + g * (hi - lo);
            if (v.distance(m1) < v.distance(m2)) hi = m2; else lo = m1;
        }
        double tMin = (lo + hi) / 2, dMin = v.distance(tMin);
        String hm = SimClock.instantOf(utcOf(tMin)).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm"));
        check("Größte Annäherung am 24.01.1986 um 17:58/17:59 UTC", utc(utcOf(tMin)).startsWith("24.01.1986") && (hm.equals("17:58") || hm.equals("17:59")), utc(utcOf(tMin)));
        check("Abstand dabei ≈ 107.000 km (Literatur 107.000 km vom Zentrum)", Math.abs(dMin - 107_000) < 400, String.format("%,.0f km", dMin));
        check("Horizons selbst: um 18:00 TDB auch ≈ q (Daten, nicht nur Anpassung)", Math.abs(sample18 - v.qKm) < 300, String.format("%,.0f km", sample18));
        check("Geschwindigkeit dabei ≈ 18 km/s (Vis-viva)", Math.abs(v.speed(tMin) - 18.03) < 0.1, String.format("%.2f km/s", v.speed(tMin)));
        check("Zeitraum der Hyperbel: 21.–27. Januar 1986", v.covers(2446455.0) && !v.covers(2446400) && !v.covers(2446500), "");
    }

    // ------------------------------------------------------------------ Jahreszeiten
    static double lat(double jd) { return Math.toDegrees(Math.asin(Ephemeris.sunDirWorld(jd)[1])); }

    static void seasons(UranusSystem sys) {
        int eq = 0, sol = 0;
        boolean eqOk = true, solOk = true;
        String eqInfo = "", solInfo = "";
        for (StoryEvent e : sys.events()) {
            if (e.kind != StoryEvent.Kind.SEASON) continue;
            double l = lat(e.jdUtc);
            if (e.title.startsWith("Tagundnachtgleiche")) {
                eq++;
                if (Math.abs(l) > 0.02) { eqOk = false; eqInfo = e.title + String.format(" %.3f°", l); }
            } else {
                sol++;
                boolean extreme = Math.abs(l) > Math.abs(lat(e.jdUtc - 30)) && Math.abs(l) > Math.abs(lat(e.jdUtc + 30));
                if (!extreme || Math.abs(Math.abs(l) - 82.2) > 0.2) { solOk = false; solInfo = e.title + String.format(" %.2f°", l); }
            }
        }
        check("1900–2100: 5 Tagundnachtgleichen, Sonne dort über dem Äquator (< 0,02°)", eq == 5 && eqOk, eq + " gefunden " + eqInfo);
        check("1900–2100: 5 Sonnenwenden, Sonne dort am weitesten vom Äquator (82,2°)", sol == 5 && solOk, sol + " gefunden " + solInfo);
        StoryEvent e07 = find(sys, "Tagundnachtgleiche 2007"), s30 = find(sys, "Sonnenwende 2030");
        check("Tagundnachtgleiche 2007 im Dezember 2007", e07 != null && utc(e07.jdUtc).contains(".12.2007"), e07 == null ? "fehlt" : utc(e07.jdUtc));
        check("Sonnenwende 2030 im April 2030, Sonne über Nord", s30 != null && utc(s30.jdUtc).contains(".04.2030") && lat(s30.jdUtc) > 82, s30 == null ? "fehlt" : utc(s30.jdUtc));
    }

    static StoryEvent find(UranusSystem sys, String title) {
        for (StoryEvent e : sys.events()) if (e.title.equals(title)) return e;
        return null;
    }

    // ------------------------------------------------------------------ Ereignisse
    static void events(UranusSystem sys) {
        Map<StoryEvent.Kind, Integer> n = new EnumMap<>(StoryEvent.Kind.class);
        double last = -1;
        boolean sorted = true, refs = true;
        String bad1 = "";
        for (StoryEvent e : sys.events()) {
            n.merge(e.kind, 1, Integer::sum);
            if (e.jdUtc < last) sorted = false;
            last = e.jdUtc;
            if (e.body != null && !e.body.isEmpty() && sys.find(e.body) == null) { refs = false; bad1 = e.body; }
            if (e.ring != null && !e.ring.isEmpty() && sys.ring(e.ring) == null) { refs = false; bad1 = e.ring; }
        }
        check("57 Ereignisse: 43 Entdeckungen, 4 Vorbeiflug, 10 Jahreszeiten", sys.events().size() == 57
                && n.getOrDefault(StoryEvent.Kind.DISCOVERY, 0) == 43 && n.getOrDefault(StoryEvent.Kind.FLYBY, 0) == 4
                && n.getOrDefault(StoryEvent.Kind.SEASON, 0) == 10, sys.events().size() + " " + n);
        check("Ereignisse zeitlich geordnet, Monde und Ringe existieren", sorted && refs, bad1);
        int moons = 0, rings = 0;
        for (StoryEvent e : sys.events()) if (e.kind == StoryEvent.Kind.DISCOVERY) { if (e.body != null && !e.body.isEmpty()) moons++; if (e.ring != null && !e.ring.isEmpty()) rings++; }
        check("Jede Entdeckung einmal: 29 Monde, 13 Ringe (+ Uranus)", moons == 29 && rings == 13, moons + " Monde, " + rings + " Ringe");
        boolean yearOk = true;
        for (Ring r : sys.rings()) if (r.discoveredYear < 1977) yearOk = false;
        check("Ringe haben Entdeckungsjahr und Entdecker", yearOk && sys.ring("ε").discoveredYear == 1977 && sys.ring("ν").discoveredYear == 2003,
                "ε " + sys.ring("ε").discoveredYear + " · λ " + sys.ring("λ").discoveredYear + " · ν " + sys.ring("ν").discoveredYear);
    }

    // ------------------------------------------------------------------ Fahrten
    static final Set<String> KEYS = new HashSet<>(Arrays.asList("moons", "trails", "magneto", "space", "scale", "axis", "lens", "flight", "rate", "time", "cam"));

    static void tours(UranusSystem sys) {
        List<String> codes = new ArrayList<>();
        int steps = 0;
        for (Tour t : sys.tours()) { codes.add(t.code); steps += t.steps().size(); }
        check("4 Fahrten, 33 Stationen", codes.equals(Arrays.asList("VOYAGER", "JAHR", "ENTDECKUNG", "RUNDFLUG")) && steps == 33, codes + ", " + steps);
        boolean targets = true, layers = true, times = true;
        String info = "";
        for (Tour t : sys.tours()) {
            for (Tour.Step s : t.steps()) {
                if (!s.target.equals("Uranus") && sys.find(s.target) == null && sys.craft(s.target) == null) { targets = false; info = t.code + ": " + s.target; }
                for (String kv : s.layers.split(";")) {
                    if (kv.isBlank()) continue;
                    String[] p = kv.split("=");
                    if (p.length != 2 || !KEYS.contains(p[0].trim())) { layers = false; info = t.code + ": " + kv; continue; }
                    String k = p[0].trim(), val = p[1].trim();
                    try {
                        if (k.equals("rate")) Double.parseDouble(val);
                        else if (k.equals("moons")) { if (!val.equals("off")) Integer.parseInt(val); }
                        else if (k.equals("time")) { if (!val.equals("lin") && !val.equals("ease")) layers = false; }
                        else if (k.equals("cam")) { if (!val.equals("sun")) layers = false; }
                        else Integer.parseInt(val);
                    } catch (NumberFormatException ex) { layers = false; info = t.code + ": " + kv; }
                }
                boolean needJd = t.timeMode == Tour.TimeMode.CLOCK || t.overlay == Tour.Overlay.DISCOVERY;
                if (needJd && Double.isNaN(s.jdUtc)) { times = false; info = t.code + " @" + s.atS; }
            }
        }
        check("Alle Ziele existieren (Uranus, Monde, Sonde)", targets, info);
        check("Alle Schalter bekannt und lesbar", layers, info);
        check("Uhr- und Erzählfahrten haben an jeder Station eine Zeit", times, info);
        Tour v = sys.tour("VOYAGER");
        check("Voyager-Fahrt: Zeiten innerhalb der Hyperbel", v != null && sys.craft("Voyager 2").covers(TimeScale.tdb(v.steps().get(0).jdUtc))
                && sys.craft("Voyager 2").covers(TimeScale.tdb(v.steps().get(v.steps().size() - 1).jdUtc)), "");
    }

    // ------------------------------------------------------------------ Abspieler
    static final class Rig {
        final SceneSettings s = new SceneSettings();
        final SimClock c = new SimClock();
        final Renderer r;
        Renderer.Frame f;
        Rig(UranusSystem sys, String when) {
            c.setJd(SimClock.jdOf(java.time.Instant.parse(when)));
            c.setRate(0.25);
            r = new Renderer(sys, s, c);
            r.settle();
        }
        void frame(double dt) { c.advance(dt); f = r.render(320, 180, dt); }
        /** Bis zur Fahrtzeit t; der letzte Schritt trifft t genau. */
        void until(double t, double dt) {
            int guard = 0;
            while (r.activeTour() != null && r.tourTime() < t - 1e-9 && guard++ < 100_000) frame(Math.min(dt, t - r.tourTime()));
        }
        void seconds(double sec, double dt) { for (int i = 0; i < Math.round(sec / dt); i++) frame(dt); }
    }

    static void player(UranusSystem sys) {
        Rig g = new Rig(sys, "2026-09-23T00:00:00Z");
        g.s.moonLevel = 0; g.s.magneto = true; g.s.trails = true; g.s.axis = false;
        g.frame(0.05);
        double jd0 = g.c.jd() + 0.05 * g.c.rate();          // Stand beim Start (die Uhr läuft im Startbild noch 0,05 s)
        g.s.tourRequest = "VOYAGER";
        g.frame(0.05);
        check("Fahrt startet und meldet sich (tourRunning)", g.r.activeTour() != null && "VOYAGER".equals(g.s.tourRunning), String.valueOf(g.s.tourRunning));
        check("Station 1 setzt die Ebenen (moons=1)", g.s.moonLevel == 1, "moonLevel " + g.s.moonLevel);
        g.until(49.75, 0.25);
        double[] cw = g.r.craftWorld();
        com.dan.uranus.render.Camera cam = g.r.camera();
        double tx = cam.cx + cam.fx * cam.distance(), ty = cam.cy + cam.fy * cam.distance(), tz = cam.cz + cam.fz * cam.distance();
        double off = cw == null ? 99 : Math.sqrt((tx - cw[0]) * (tx - cw[0]) + (ty - cw[1]) * (ty - cw[1]) + (tz - cw[2]) * (tz - cw[2]));
        check("Kamera folgt der Sonde (Ziel am Streckenende auf Voyager 2)", off < 0.02, String.format("%.4f Welteinheiten", off));
        g.until(50, 0.25);
        double want = sys.tour("VOYAGER").steps().get(4).jdUtc;
        check("Uhr erreicht bei 50 s die größte Annäherung", Math.abs(g.c.jd() - want) * 86400 < 1, String.format("%s (Abweichung %.2f s)", utc(g.c.jd()), (g.c.jd() - want) * 86400));
        check("Sonde im Bild vorhanden (Hyperbel gilt)", g.r.craftWorld() != null, "");
        // Anhalten
        g.s.tourPauseRequest = true;
        g.frame(0.1);
        double t0 = g.r.tourTime(), j0 = g.c.jd();
        g.seconds(2, 0.1);
        check("Leertaste hält die Fahrt an: Fahrtzeit und Uhr stehen", g.s.tourPaused && Math.abs(g.r.tourTime() - t0) < 1e-9 && g.c.jd() == j0, String.format("t = %.2f s", g.r.tourTime()));
        g.s.tourPauseRequest = true;
        g.seconds(1, 0.1);
        check("… und läuft weiter", !g.s.tourPaused && g.r.tourTime() > t0 + 0.9, String.format("t = %.2f s", g.r.tourTime()));
        // Beenden stellt alles wieder her
        g.s.tourStopRequest = true;
        g.frame(0.05);
        check("Esc beendet die Fahrt", g.r.activeTour() == null && g.s.tourRunning == null, "");
        check("Nach der Fahrt: Ebenen wie vorher", g.s.moonLevel == 0 && g.s.magneto && g.s.trails && !g.s.axis && !g.s.ringFlight, "moonLevel " + g.s.moonLevel);
        check("Nach der Fahrt: Uhr zurück auf das Ausgangsdatum, Tempo wie vorher", Math.abs(g.c.jd() - jd0) * 86400 < 1 && Math.abs(g.c.rate() - 0.25) < 1e-12 && !g.c.isPaused(),
                utc(g.c.jd()));
    }

    static void discovery(UranusSystem sys) {
        Rig g = new Rig(sys, "2026-09-23T00:00:00Z");
        g.s.tourRequest = "ENTDECKUNG";
        g.frame(0.05);
        int guard = 0;
        while (!(g.r.storyYear() >= 1900) && guard++ < 10_000) g.frame(0.1);
        g.s.tourPauseRequest = true;
        g.seconds(5, 0.1);
        List<String> seen = new ArrayList<>();
        for (Body b : sys.bodies()) if (g.r.revealOf(b.name) > 0.99) seen.add(b.name);
        int rings = 0;
        for (Ring r : sys.rings()) if (g.r.revealOf(r.name) > 0.01) rings++;
        check("Erzählzeit 1900: genau vier Monde sichtbar (Titania, Oberon, Ariel, Umbriel)", new HashSet<>(seen).equals(new HashSet<>(Arrays.asList("Titania", "Oberon", "Ariel", "Umbriel"))),
                String.format("%.1f: %s", g.r.storyYear(), seen));
        check("Erzählzeit 1900: keine Ringe", rings == 0, rings + " sichtbar");
        int picks = g.f.picks.size();
        check("Verborgene Monde sind nicht anklickbar", picks <= 4, picks + " anklickbar");
        g.s.tourPauseRequest = true;
        g.until(50, 0.1);
        double y = g.r.storyYear();
        check("Nach Voyager (Erzählzeit " + String.format("%.0f", y) + "): ε, λ, ζ und Puck da, ν und Caliban noch nicht",
                g.r.revealOf("ε") > 0.99 && g.r.revealOf("λ") > 0.9 && g.r.revealOf("ζ") > 0.9 && g.r.revealOf("Puck") > 0.99
                        && g.r.revealOf("ν") < 0.01 && g.r.revealOf("Caliban") < 0.01,
                String.format("ε %.2f λ %.2f ν %.2f Caliban %.2f", g.r.revealOf("ε"), g.r.revealOf("λ"), g.r.revealOf("ν"), g.r.revealOf("Caliban")));
        g.until(200, 0.25);
        g.seconds(2, 0.1);
        boolean all = true;
        for (Body b : sys.bodies()) if (g.r.revealOf(b.name) < 0.99) all = false;
        for (Ring r : sys.rings()) if (g.r.revealOf(r.name) < 0.99) all = false;
        check("Nach der Fahrt ist wieder alles sichtbar", g.r.activeTour() == null && all && Double.isNaN(g.r.storyYear()), "");
    }

    static void year(UranusSystem sys) {
        Rig g = new Rig(sys, "2026-09-23T00:00:00Z");
        g.s.tourRequest = "JAHR";
        g.frame(0.05);
        g.seconds(3, 0.1);
        check("Ein Uranusjahr: Monde ausgeblendet, keine anklickbar", g.f.picks.isEmpty(), g.f.picks.size() + " anklickbar");
        check("Ein Uranusjahr: Achse an", g.s.axis, "");
        g.until(60, 0.25);
        StoryEvent e = find(sys, "Tagundnachtgleiche 2007");
        check("Bei 60 s steht die Uhr auf der Tagundnachtgleiche 2007", e != null && Math.abs(g.c.jd() - e.jdUtc) < 1e-6,
                utc(g.c.jd()) + String.format(", Sonne über %.3f°", lat(g.c.jd())));
        g.until(200, 0.5);
        g.seconds(3, 0.1);
        check("Danach: Monde wieder da, Achse aus", g.r.activeTour() == null && !g.s.axis && !g.f.picks.isEmpty(), g.f.picks.size() + " anklickbar");
    }

    static void roundTrip(UranusSystem sys) {
        Rig g = new Rig(sys, "2026-09-23T00:00:00Z");
        g.s.tourRequest = "RUNDFLUG";
        g.frame(0.05);
        g.until(70, 0.25);
        check("Rundflug: bei 70 s im ε-Ring", g.s.ringFlight, "");
        g.until(88, 0.25);
        check("Rundflug: bei 88 s wieder draußen", !g.s.ringFlight, "");
        g.until(200, 0.25);
        check("Rundflug endet von selbst", g.r.activeTour() == null && g.s.tourRunning == null && !g.s.ringFlight, "");
        // eigener Ringflug bleibt, wenn eine Fahrt ohne Ringflug abbricht
        g.s.tourRequest = "VOYAGER";
        g.frame(0.05);
        g.s.tourStopRequest = true;
        g.s.ringFlight = true;
        g.frame(0.05);
        check("Ringflug-Knopf während einer Fahrt: Fahrt endet, Ringflug bleibt", g.r.activeTour() == null && g.s.ringFlight, "");
    }

    // ------------------------------------------------------------------ Spaltenbreiten

    /**
     * Jeder Text in den Ladeskripten 03–08 passt in seine Spalte — gemessen in UTF-8-Bytes, denn VARCHAR2(n)
     * zählt ohne CHAR-Angabe Bytes (Ä, ·, — belegen 2–3). Lehre aus dem ersten Lauf: 'Voyager_2_ST+refit2022_m'
     * (24) passte nicht in ura_source.ephemeris (20), und daran hingen Sonde und Vektoren.
     */
    static void widths() throws Exception {
        Map<String, Integer> col = new HashMap<>();
        String schema = Files.readString(Paths.get("db", "01_schema.sql"), StandardCharsets.UTF_8);
        java.util.regex.Matcher t = java.util.regex.Pattern.compile("CREATE TABLE (\\w+) \\((.*?)\\n\\);", java.util.regex.Pattern.DOTALL).matcher(schema);
        while (t.find()) {
            java.util.regex.Matcher c = java.util.regex.Pattern.compile("(?m)^\\s+(\\w+)\\s+VARCHAR2\\((\\d+)( CHAR)?\\)").matcher(t.group(2));
            while (c.find()) col.put(t.group(1).toLowerCase(Locale.ROOT) + "." + c.group(1).toLowerCase(Locale.ROOT), Integer.parseInt(c.group(2)) * (c.group(3) != null ? -1 : 1));
        }
        Map<String, String> table = new HashMap<>(Map.of("put_source", "ura_source", "put_body", "ura_body", "put_orbit", "ura_orbit", "put_ring", "ura_ring",
                "put_craft", "ura_craft", "put_event", "ura_event", "put_tour", "ura_tour", "put_tour_step", "ura_tour_step"));
        table.put("put_scenario", "ura_scenario");
        table.put("put_scenario_body", "ura_scenario_body");
        Map<String, String[]> positional = Map.of("put_event", new String[]{null, "kind", "time_prec", "title", "event_text", null, null, null},
                "put_tour", new String[]{"code", "title", "overlay", "time_mode", null, "description"},
                "put_tour_step", new String[]{null, null, null, null, null, null, null, "target", "layers", "title", "caption"},
                "put_scenario_body", new String[]{null, null, "name", "role", null, "mass_estimated", null});
        Map<String, String> rename = Map.of("p_group", "body_group", "p_by", "discoverer", "p_compare", "compare_eph");
        int texts = 0, worstPct = 0;
        String bad1 = "", worstAt = "";
        for (String f : new String[]{"03_bodies.sql", "04_orbits.sql", "05_rings.sql", "06_events.sql", "07_voyager.sql", "08_tours.sql", "09_scenarios.sql"}) {
            if (!Files.exists(Paths.get("db", f))) continue;
            String sql = Files.readString(Paths.get("db", f), StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("ura_api\\.(put_\\w+)\\(").matcher(sql);
            while (m.find()) {
                String proc = m.group(1), tab = table.get(proc);
                if (tab == null) continue;
                List<String> args = args(sql, m.end());
                for (int i = 0; i < args.size(); i++) {
                    String a = args.get(i).trim(), column = null;
                    int arrow = a.indexOf("=>");
                    if (arrow > 0 && a.startsWith("p_")) {
                        String p = a.substring(0, arrow).trim();
                        column = rename.getOrDefault(p, p.substring(2));
                        a = a.substring(arrow + 2).trim();
                    } else if (positional.containsKey(proc) && i < positional.get(proc).length) column = positional.get(proc)[i];
                    if (column == null || !a.startsWith("'") || !a.endsWith("'")) continue;
                    Integer w = col.get(tab + "." + column);
                    if (w == null) continue;
                    String v = a.substring(1, a.length() - 1).replace("''", "'");
                    int len = w < 0 ? v.codePointCount(0, v.length()) : v.getBytes(StandardCharsets.UTF_8).length, lim = Math.abs(w);
                    texts++;
                    if (len > lim && bad1.isEmpty()) bad1 = f + ": " + tab + "." + column + " " + len + " > " + lim + " («" + (v.length() > 30 ? v.substring(0, 30) + "…" : v) + "»)";
                    int pct = len * 100 / lim;
                    if (pct > worstPct) { worstPct = pct; worstAt = tab + "." + column + " " + len + "/" + lim; }
                }
            }
        }
        check("Alle Texte der Skripte 03–09 passen in ihre Spalten (UTF-8-Bytes)", bad1.isEmpty() && texts > 300,
                bad1.isEmpty() ? texts + " Texte, am knappsten " + worstAt : bad1);
    }

    /** Argumente eines Aufrufs ab der öffnenden Klammer, Kommas in Zeichenketten und Klammern bleiben drin. */
    static List<String> args(String s, int from) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean q = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q) {
                cur.append(c);
                if (c == '\'') { if (i + 1 < s.length() && s.charAt(i + 1) == '\'') { cur.append('\''); i++; } else q = false; }
                continue;
            }
            if (c == '\'') { q = true; cur.append(c); }
            else if (c == '(') { depth++; cur.append(c); }
            else if (c == ')') { if (depth == 0) { out.add(cur.toString()); return out; } depth--; cur.append(c); }
            else if (c == ',' && depth == 0) { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        return out;
    }

    // ------------------------------------------------------------------ Skripte
    static int count(String s, String sub) { int n = 0, i = 0; while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); } return n; }

    static void scripts() throws Exception {
        String e = Files.readString(Paths.get("db", "06_events.sql"), StandardCharsets.UTF_8);
        String v = Files.readString(Paths.get("db", "07_voyager.sql"), StandardCharsets.UTF_8);
        String t = Files.readString(Paths.get("db", "08_tours.sql"), StandardCharsets.UTF_8);
        check("06_events.sql: 3 Quellen, 57 Ereignisse", count(e, "ura_api.put_source(") == 3 && count(e, "ura_api.put_event(") == 57,
                count(e, "ura_api.put_source(") + " / " + count(e, "ura_api.put_event("));
        check("07_voyager.sql: 1 Sonde, 145 Zustände", count(v, "ura_api.put_craft(") == 1 && count(v, "ura_api.put_craft_state(") == 145,
                count(v, "ura_api.put_craft(") + " / " + count(v, "ura_api.put_craft_state("));
        check("08_tours.sql: 4 Fahrten, 33 Stationen", count(t, "ura_api.put_tour(") == 4 && count(t, "ura_api.put_tour_step(") == 33,
                count(t, "ura_api.put_tour(") + " / " + count(t, "ura_api.put_tour_step("));
        String all = e + v + t;
        check("Keine Kennwörter in den Skripten", !all.toLowerCase(Locale.ROOT).contains("password") && !all.contains("uranus-db.properties"), "");
        widths();
        String pk = Files.readString(Paths.get("db", "02_package.sql"), StandardCharsets.UTF_8);
        check("Ereignisse mit gleichem Zeitpunkt kommen in Ladereihenfolge (nicht nach Titel)",
                pk.contains("ORDER BY e.event_jd, e.event_id"), "");
        check("Package ab 1.1 mit Geschichten-Schnittstelle", pk.matches("(?s).*c_version CONSTANT VARCHAR2\\(10\\) := '1\\.[1-9]'.*") && pk.contains("FUNCTION tour_steps") && pk.contains("FUNCTION craft_states") && pk.contains("PROCEDURE put_event"), "");
    }
}
