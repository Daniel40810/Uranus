import com.dan.uranus.db.UranusDao;
import com.dan.uranus.db.UranusDb;
import com.dan.uranus.model.*;
import com.dan.uranus.physics.*;
import com.dan.uranus.render.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Headless-Prüfungen für Phase 5 ohne Datenbank: Quellen, Zeitskala, Bezugsebenen, Abgleich mit
 * Horizons, Ringe als Ellipsen, Datensatzwechsel mit Gleiten, Rückfall, Einrichtungsskripte.
 * Aufruf aus dem Projektordner: java -cp build/classes;build/tools Phase5Tests
 */
public class Phase5Tests {

    static int ok, bad;

    static void check(String name, boolean c, String info) {
        if (c) ok++; else bad++;
        System.out.println((c ? "[ok]     " : "[FEHLER] ") + name + (info.isEmpty() ? "" : " — " + info));
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMANY);
        System.out.println("Phase 5 — Datenbank (ohne Verbindung)");
        sources();
        time();
        frames();
        horizons();
        rings();
        glide();
        fallback();
        scripts();
        System.out.println();
        System.out.println("Ergebnis: " + ok + " ok, " + bad + " Fehler");
        System.exit(bad == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Quellen
    static void sources() throws Exception {
        String jpl = UraGen.sha(Paths.get("db", "quellen", "jpl_uranus_sat_elem.csv"));
        String wiki = UraGen.sha(Paths.get("db", "quellen", "wikipedia_uranus_ringe.csv"));
        check("JPL-Tabelle unverändert (SHA-256 wie im Browser berechnet)", jpl.equals("117586215e999efbe00794669d8a7aca826f441e4d42c4e56fd01a8cc74a5a31"), jpl.substring(0, 16) + "…");
        check("Ringtabelle unverändert (SHA-256 wie im Browser berechnet)", wiki.equals("943f9b598014bd8a4c9331df48a180504fc26d495938ff7b56eebda516a15d60"), wiki.substring(0, 16) + "…");
        List<UraGen.JplRow> rows = UraGen.readJpl();
        check("JPL: 30 Zeilen für 29 Monde (Puck aus URA182 und URA184)", rows.size() == 30, rows.size() + " Zeilen");
        UraGen.HZ.clear();
        UraGen.readHorizons();
        int samples = 0;
        for (List<double[]> l : UraGen.HZ.values()) samples += l.size();
        check("Horizons-Referenz: 28 Monde, 532 Positionen (S/2025 U1 fehlt dort)", UraGen.HZ.size() == 28 && samples == 532 && !UraGen.HZ.containsKey(75052),
                UraGen.HZ.size() + " Monde, " + samples + " Positionen");
    }

    // ------------------------------------------------------------------ Zeit
    static void time() {
        double d26 = TimeScale.tdbMinusUtcSeconds(SimClock.jdOf(java.time.Instant.parse("2026-09-23T00:00:00Z")));
        double d86 = TimeScale.tdbMinusUtcSeconds(SimClock.jdOf(java.time.Instant.parse("1986-01-24T18:00:00Z")));
        check("TDB − UTC: 69,184 s (2026) und 55,184 s (1986)", Math.abs(d26 - 69.184) < 1e-9 && Math.abs(d86 - 55.184) < 1e-9,
                String.format("%.3f s / %.3f s", d26, d86));
        Body cor = UranusSystem.builtIn().find("Cordelia");
        double deg = cor.orbit.meanMotionDegPerDay() * 69.184 / 86400;
        check("Der Unterschied zählt: Cordelia läuft in 69 s " + String.format("%.2f°", deg), deg > 0.8, "");
    }

    // ------------------------------------------------------------------ Bezugsebenen
    static void frames() {
        boolean ortho = true;
        for (OrbitElements.Frame f : OrbitElements.Frame.values()) {
            double[][] b = OrbitElements.basisIcrf(f, 77.3, 15.2);
            double s = Math.abs(Ephemeris.dot(b[0], b[1])) + Math.abs(Ephemeris.dot(b[1], b[2])) + Math.abs(Ephemeris.dot(b[0], b[2]));
            double[] xy = Ephemeris.cross(b[0], b[1]);
            if (s > 1e-12 || Ephemeris.dot(xy, b[2]) < 0.999999) ortho = false;
        }
        check("Basis jeder Bezugsebene orthonormal und rechtshändig", ortho, "");
        double[][] eq = OrbitElements.basisIcrf(OrbitElements.Frame.EQUATORIAL, Double.NaN, Double.NaN);
        double[] w = new double[3];
        Ephemeris.icrfToWorld(eq[2], w);
        check("Äquator: Bahnpol zeigt gegen den IAU-Nordpol (Welt −y)", Math.abs(w[1] + 1) < 1e-12, String.format("y = %.12f", w[1]));
        double[][] lp = OrbitElements.basisIcrf(OrbitElements.Frame.LAPLACE, 77.3, 15.2);
        double tilt = Math.toDegrees(Math.acos(-Ephemeris.dot(lp[2], Ephemeris.POLE_ICRF)));
        check("Laplace-Pol der inneren Monde fast parallel zum Bahnpol des Äquators", tilt < 0.1, String.format("%.3f°", tilt));
        Body mir = UranusSystem.builtIn().find("Miranda"), syc = UranusSystem.builtIn().find("Sycorax");
        check("Präzession: Mirandas Knoten läuft rückwärts, das Perizentrum vorwärts", mir.orbit.nodeRate() < 0 && mir.orbit.argPeriRate() > 0,
                String.format("%.4f / %.4f °/Tag", mir.orbit.nodeRate(), mir.orbit.argPeriRate()));
        check("Sycorax: Präzession beim Angleich verworfen (passt ohne besser)", syc.orbit.nodePeriodYr == 0, "");
    }

    // ------------------------------------------------------------------ Horizons
    static void horizons() throws Exception {
        UranusSystem sys = UranusSystem.builtIn();
        double[] worst = new double[3];
        String[] who = new String[3];
        boolean stored = true;
        for (Body b : sys.bodies()) {
            List<double[]> ref = UraGen.HZ.get(b.naifId);
            if (ref == null) continue;
            double[] st = UraGen.stats(b.orbit, ref);
            int g = b.group.ordinal();
            if (st[0] > worst[g]) { worst[g] = st[0]; who[g] = b.name; }
            if (Math.abs(st[0] - b.fitMaxDeg) > 0.002) stored = false;
        }
        check("Hauptmonde folgen Horizons 1975–2030", worst[0] < 2.5, String.format("größte Abweichung %.2f° (%s)", worst[0], who[0]));
        check("Innere Monde folgen Horizons 1975–2030", worst[1] < 6, String.format("größte Abweichung %.2f° (%s)", worst[1], who[1]));
        check("Irreguläre Monde folgen Horizons 1975–2030", worst[2] < 25, String.format("größte Abweichung %.2f° (%s)", worst[2], who[2]));
        check("Gespeicherte Abweichung = nachgerechnete", stored, "");

        // warum der Angleich nötig ist: die veröffentlichten Werte
        Map<String, Double> pub = new HashMap<>();
        for (UraGen.JplRow r : UraGen.readJpl()) {
            List<double[]> ref = UraGen.HZ.get(r.code);
            if (ref != null && !(r.code == 715 && r.ephemeris.equals("URA182"))) pub.put(r.ephemeris + ":" + r.code, UraGen.stats(r.orbit(), ref)[0]);
        }
        double cordPub = pub.get("URA184:706"), cordFit = sys.find("Cordelia").fitMaxDeg;
        check("Cordelia: veröffentlichte Periode 0,3347 d läuft weg, angeglichen 0,335036 d", cordPub > 100 && cordFit < 0.5
                        && Math.abs(sys.find("Cordelia").orbit.periodDays - 0.335036) < 2e-6,
                String.format("%.0f° → %.2f°", cordPub, cordFit));
        // die URA184-Epoche: an der Epoche selbst liegen alle inneren Monde um dieselbe Zeit daneben
        double[] p = new double[3];
        boolean shift = true;
        for (UraGen.JplRow r : UraGen.readJpl()) {
            if (!r.ephemeris.equals("URA184") || !UraGen.HZ.containsKey(r.code)) continue;
            double[] ref = null;
            for (double[] s : UraGen.HZ.get(r.code)) if (Math.abs(s[0] - r.epoch) < 1e-6) ref = s;
            r.orbit().position(r.epoch - 0.01, p);
            if (UraGen.angle(p, new double[]{ref[1], ref[2], ref[3]}) > 0.1) shift = false;
        }
        check("URA184: Elemente treffen Horizons genau 0,01 Tage nach der Epoche", shift, "Befund, im Angleich aufgefangen");

        long t0 = System.nanoTime();
        for (int k = 0; k < 2000; k++) for (Body b : sys.bodies()) b.orbit.position(2461306.5 + k * 0.37, p);
        double ms = (System.nanoTime() - t0) / 1e6;
        check("Schnell genug: 29 Monde × 2000 Zeitpunkte", ms < 400, String.format("%.1f ms", ms));
    }

    // ------------------------------------------------------------------ Ringe
    static void rings() {
        Ring eps = null;
        for (Ring r : UranusSystem.builtIn().rings()) if (r.name.equals("ε")) eps = r;
        double rp = eps.radiusRuAt(0, 0) * UranusSystem.URANUS_RADIUS_KM, ra = eps.radiusRuAt(Math.PI, 0) * UranusSystem.URANUS_RADIUS_KM;
        check("ε-Ring als Ellipse: 50 745 bis 51 553 km", Math.abs(rp - 51149 * (1 - 0.0079)) < 0.5 && Math.abs(ra - 51149 * (1 + 0.0079)) < 0.5,
                String.format("%.0f / %.0f km", rp, ra));
        double wp = eps.widthRuAt(0, 0) * UranusSystem.URANUS_RADIUS_KM, wa = eps.widthRuAt(Math.PI, 0) * UranusSystem.URANUS_RADIUS_KM;
        check("ε-Ring schmal am Perizentrum, breit am Apozentrum", Math.abs(wp - 19.7) < 1e-9 && Math.abs(wa - 96.4) < 1e-9, String.format("%.1f / %.1f km", wp, wa));
        check("ε-Perizentrum dreht sich durch J2 (French 1988: 1,36°/Tag)", Math.abs(eps.apsisRate - 1.36) < 0.03, String.format("%.3f °/Tag", eps.apsisRate));
        int n = 0;
        for (Ring r : UranusSystem.builtIn().rings()) if (r.widthMaxKm >= r.widthMinKm && r.aKm > 39000) n++;
        check("13 Ringe mit gültiger Breite, von ζ bis μ", n == 13, n + "");
    }

    // ------------------------------------------------------------------ Datensatzwechsel
    static void glide() {
        UranusSystem base = UranusSystem.builtIn();
        List<Body> bodies = new ArrayList<>();
        for (Body b : base.bodies()) {
            if (b.name.equals("Ariel")) {
                OrbitElements o = b.orbit.withPhase((b.orbit.meanAnomalyDeg + 90) % 360, b.orbit.periodDays, true);
                bodies.add(b.withOrbit(o, b.quality, "Prüfung", b.fitMaxDeg));
            } else bodies.add(b);
        }
        UranusSystem moved = new UranusSystem(bodies, base.rings(), "Prüfung");
        SimClock c = new SimClock();
        c.setJd(2461306.5);
        c.setPaused(true);
        SceneSettings s = new SceneSettings();
        Renderer r = new Renderer(base, s, c);
        r.settle();
        r.render(320, 180, 0.001);
        double[] before = r.worldOf("Ariel");
        Renderer r2 = new Renderer(moved, s, c);
        r2.settle();
        r2.render(320, 180, 0.001);
        double[] target = r2.worldOf("Ariel");
        r.setSystem(moved, "Daten: Prüfung", "");
        r.render(320, 180, 0.001);
        double[] start = r.worldOf("Ariel");
        r.render(320, 180, 0.9);
        double[] mid = r.worldOf("Ariel");
        for (int i = 0; i < 10; i++) r.render(320, 180, 0.1);
        double[] end = r.worldOf("Ariel");
        double dStart = Math.hypot(start[0] - before[0], start[1] - before[1]), dEnd = Math.hypot(end[0] - target[0], end[1] - target[1]);
        double dMid = Math.hypot(mid[0] - target[0], mid[1] - target[1]);
        check("Datensatzwechsel: Ariel gleitet von der alten auf die neue Lage", dStart < 1e-3 && dEnd < 1e-9 && dMid > 1e-3,
                String.format("Start %.1e, Mitte noch %.3f, Ende %.1e Welteinheiten", dStart, dMid, dEnd));
        check("Der neue Datensatz ist übernommen", r.system() == moved && "Daten: Prüfung".equals(r.dataLabel()), "");
    }

    // ------------------------------------------------------------------ Rückfall
    static void fallback() {
        UranusDao.Result r0 = UranusDao.loadOrBuiltIn(null);
        check("Ohne Verbindungsdatei: eingebauter Satz", !r0.fromDatabase && r0.system == UranusSystem.builtIn(), r0.message);
        long t0 = System.currentTimeMillis();
        UranusDao.Result r1 = UranusDao.loadOrBuiltIn(new UranusDb("jdbc:oracle:thin:@//127.0.0.1:9/NIRGENDS", "X", "Y", "Prüfung"));
        long ms = System.currentTimeMillis() - t0;
        check("Datenbank nicht erreichbar: eingebauter Satz mit Grund, ohne Hängen", !r1.fromDatabase && r1.system == UranusSystem.builtIn()
                && !r1.message.isEmpty() && ms < 15_000, ms + " ms · " + r1.message);
    }

    // ------------------------------------------------------------------ Skripte
    static void scripts() throws Exception {
        String s1 = read("01_schema.sql"), s2 = read("02_package.sql"), s3 = read("03_bodies.sql"), s4 = read("04_orbits.sql"), s5 = read("05_rings.sql");
        check("03: 6 Quellen und 30 Körper", count(s3, "ura_api.put_source") == 6 && count(s3, "ura_api.put_body") == 30,
                count(s3, "put_source") + " / " + count(s3, "put_body"));
        check("04: 58 Bahnen (30 JPL wie veröffentlicht, 28 angeglichen)", count(s4, "ura_api.put_orbit") == 58
                && count(s4, "p_source => 'HZ_FIT'") == 28, count(s4, "put_orbit") + " Bahnen");
        Map<String, Integer> cur = new HashMap<>();
        Matcher m = Pattern.compile("p_body => '([^']+)'[^;]*?p_current => 'J'", Pattern.DOTALL).matcher(s4);
        while (m.find()) cur.merge(m.group(1), 1, Integer::sum);
        boolean one = cur.size() == 29;
        for (int v : cur.values()) if (v != 1) one = false;
        check("04: genau eine gültige Bahn je Mond", one, cur.size() + " Monde");
        check("05: 13 Ringe", count(s5, "ura_api.put_ring") == 13, "");
        // jeder PL/SQL-Block endet mit / (sonst schluckt der Lauf die nächste Anweisung)
        boolean slash = true;
        for (String s : new String[]{s2, s3, s4, s5}) if (count(s, "\nEND") != count(s, "\n/\n") - (s == s2 ? 1 : 0)) slash = false;
        check("Jeder Block endet mit /", slash, "");
        check("Hauskonvention: keine IDENTITY-Spalten", !s1.toUpperCase().contains("IDENTITY"), "");
        check("Kein Fremdschlüssel auf SOLAR_BODY", !s1.toUpperCase().contains("REFERENCES SOLAR"), "");
        boolean star = false;
        for (String line : s2.split("\n")) { int c = line.indexOf("--"); if ((c < 0 ? line : line.substring(0, c)).contains("SELECT *")) star = true; }
        check("URA_API liest keine Tabelle mit SELECT * (virtuelle Spalten)", !star, "");
    }

    static String read(String f) throws Exception { return new String(Files.readAllBytes(Paths.get("db", f)), StandardCharsets.UTF_8); }

    static int count(String s, String sub) {
        int n = 0;
        for (int k = s.indexOf(sub); k >= 0; k = s.indexOf(sub, k + sub.length())) n++;
        return n;
    }
}
