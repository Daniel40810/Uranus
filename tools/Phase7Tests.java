import com.dan.uranus.model.*;
import com.dan.uranus.physics.Ephemeris;
import com.dan.uranus.physics.Kepler;
import com.dan.uranus.physics.TimeScale;
import com.dan.uranus.play.*;
import com.dan.uranus.render.Renderer;
import com.dan.uranus.render.SceneSettings;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Phase 7 — Spielwiese, ohne Fenster und ohne Datenbank: Integrator, Kräfte, Angleich an die Ephemeride
 * und an Horizons, Zusammenstöße, Roche-Grenze, Hill-Sphäre, Rückgängig, Szenarien, Vorausschau,
 * Bedienung über die Eingaben des Renderers, Skript 09.
 * Aufruf aus dem Projektordner: java -cp build/classes;build/tools Phase7Tests
 */
public class Phase7Tests {

    static int ok, bad;
    static final double START = SimClock.jdOf(Instant.parse("2026-09-23T00:00:00Z"));

    static void check(String name, boolean c, String info) {
        if (c) ok++; else bad++;
        System.out.println((c ? "[ok]     " : "[FEHLER] ") + name + (info.isEmpty() ? "" : " — " + info));
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMANY);
        System.out.println("Phase 7 — Spielwiese (ohne Verbindung)");
        UranusSystem sys = UranusSystem.builtIn();
        integrator();
        forces(sys);
        ephemeris(sys);
        events(sys);
        undo(sys);
        scenarios(sys);
        ui(sys);
        scripts();
        System.out.println();
        System.out.println("Ergebnis: " + ok + " ok, " + bad + " Fehler");
        System.exit(bad == 0 ? 0 : 1);
    }

    static double len(double[] v) { return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); }

    static double angle(double[] a, double[] b) {
        double c = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (len(a) * len(b));
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
    }

    // ------------------------------------------------------------------ Integrator
    static void integrator() {
        double gm = UranusSystem.URANUS_GM, rp = 129_846 * 0.7, e = 0.3, a = 129_846;
        double vp = Math.sqrt(gm * (1 + e) / rp), period = 2 * Math.PI * Math.sqrt(a * a * a / gm);
        Ias15 in = new Ias15((t, x, acc) -> {
            double r2 = x[0] * x[0] + x[1] * x[1] + x[2] * x[2], r = Math.sqrt(r2), k = -gm / (r2 * r);
            acc[0] = k * x[0]; acc[1] = k * x[1]; acc[2] = k * x[2];
        });
        in.set(0, new double[]{rp, 0, 0}, new double[]{0, vp, 0}, period / 100);
        double e0 = 0.5 * vp * vp - gm / rp;
        long t0 = System.nanoTime();
        in.integrateTo(1000 * period, 0);
        double ms = (System.nanoTime() - t0) / 1e6;
        double[] x = in.x(), v = in.v();
        double en = 0.5 * (v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) - gm / len(x);
        double dx = Math.hypot(x[0] - rp, x[1]);
        check("IAS15: Keplerbahn e = 0,3 nach 1000 Umläufen wieder am Perizentrum", dx < 1e-3,
                String.format("%.1e km daneben, %d Schritte (%.0f je Umlauf), %.0f ms", dx, in.steps(), in.steps() / 1000.0, ms));
        check("IAS15: Energie auf 10⁻¹² erhalten", Math.abs((en - e0) / e0) < 1e-12, String.format("%.1e", (en - e0) / e0));
        in.integrateTo(0, 0);
        x = in.x();
        check("IAS15: rückwärts gerechnet wieder am Start", Math.hypot(x[0] - rp, x[1]) < 1e-3, String.format("%.1e km", Math.hypot(x[0] - rp, x[1])));
    }

    // ------------------------------------------------------------------ Kräfte
    static Scenario one(String name, double[] rWorld, double[] vWorld, Scenario.Role role, double mass) {
        double[] ri = new double[3], vi = new double[3];
        Ephemeris.worldToIcrf(rWorld[0], rWorld[1], rWorld[2], ri);
        Ephemeris.worldToIcrf(vWorld[0], vWorld[1], vWorld[2], vi);
        return new Scenario("T", "T", "", START, 1, Scenario.Origin.USER, false,
                List.of(new Scenario.Item(name, role, mass, false, 10, ri, vi)));
    }

    static void forces(UranusSystem sys) {
        // J2-Präzession: Testkörper, leicht exzentrisch in der Äquatorebene, gegen die Formel des ε-Rings
        double a = 51_149, e = 0.01, gm = UranusSystem.URANUS_GM;
        double rp = a * (1 - e), vp = Math.sqrt(gm * (1 + e) / rp);
        Playground p = Playground.withoutSun(Playground.fromScenario(one("Probe", new double[]{rp, 0, 0}, new double[]{0, 0, vp},
                Scenario.Role.TEST, 0), sys));
        // Ausgleichsgerade über 200 Stichproben: die oskulierende Exzentrizität schwankt im abgeplatteten Feld kurzperiodisch
        double days = 20, st = 0, stt = 0, sw = 0, stw = 0, last = 0;
        int n = 200;
        for (int k = 0; k <= n; k++) {
            if (k > 0) p.advance(days * 86_400 / n, 0);
            double w = periapsis(p, 1);
            w = last + Math.IEEEremainder(w - last, 2 * Math.PI);
            last = w;
            double t = days * k / n;
            st += t; stt += t * t; sw += w; stw += t * w;
        }
        double rate = Math.toDegrees(((n + 1) * stw - st * sw) / ((n + 1) * stt - st * st)), want = Ring.apsisRate(a);
        check("J2: Perizentrum am ε-Ring dreht wie die Formel (J4 und J2² ändern < 2 %)", Math.abs(Math.abs(rate) / want - 1) < 0.02,
                String.format("%.4f°/Tag gerechnet, Formel %.4f°/Tag", Math.abs(rate), want));
        // Energie ohne Sonne über ein Jahr
        Playground h = Playground.withoutSun(Playground.fromScenario(sys.scenario("HEUTE"), sys));
        h.advance(365 * 86_400, 0);
        check("Hauptmonde ohne Sonne: Energie über ein Jahr erhalten (mit J2, J4, gegenseitig)", Math.abs(h.energyError()) < 1e-10,
                String.format("%.1e", h.energyError()));
    }

    /** Richtung des Perizentrums (Winkel in der Äquatorebene, Welt x–z). */
    static double periapsis(Playground p, int i) {
        double[] r = new double[3], v = new double[3];
        p.position(i, r);
        p.velocity(i, v);
        double mu = UranusSystem.URANUS_GM, rn = len(r), v2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2], rv = r[0] * v[0] + r[1] * v[1] + r[2] * v[2];
        double ex = (v2 - mu / rn) * r[0] / mu - rv * v[0] / mu, ez = (v2 - mu / rn) * r[2] / mu - rv * v[2] / mu;
        return Math.atan2(ez, ex);
    }

    // ------------------------------------------------------------------ gegen Ephemeride und Horizons
    static void ephemeris(UranusSystem sys) throws Exception {
        Playground p = Playground.fromScenario(sys.scenario("HEUTE"), sys);
        p.advance(365 * 86_400, 0);
        double worst = 0;
        String who = "";
        double[] r = new double[3], e = new double[3];
        for (int i = 1; i < p.size(); i++) {
            PlayBody b = p.body(i);
            if (b.role != PlayBody.Role.MOON) continue;
            p.position(i, r);
            Kepler.position(b.source.orbit, TimeScale.tdb(p.jdUtc()), e);
            double d = angle(r, e);
            if (d > worst) { worst = d; who = b.name; }
        }
        check("„Heute, frei“ nach einem Jahr: Hauptmonde höchstens 1° neben der Ephemeride", worst < 1,
                String.format("größte Abweichung %.2f° (%s)", worst, who));
        // gegen Horizons: Start J2000 aus der Ephemeride, nach 1000 Tagen mit den Horizons-Vektoren vergleichen
        Map<String, double[]> hz = new HashMap<>();
        for (String l : Files.readAllLines(Paths.get("db", "quellen", "horizons_referenz.csv"), StandardCharsets.UTF_8)) {
            if (l.startsWith("code")) continue;
            String[] c = l.split(";");
            hz.put(c[0] + "@" + c[1], new double[]{Double.parseDouble(c[2]), Double.parseDouble(c[3]), Double.parseDouble(c[4])});
        }
        double t0 = 2451545, t1 = 2452545;
        Playground q = Playground.fromSystem(sys, t0 - TimeScale.tdbMinusUtcSeconds(t0) / 86_400, false, true, "H", "H", "");
        q.advance((t1 - t0) * 86_400, 0);
        double w = 0, weph = 0;
        String ww = "";
        double[] h = new double[3];
        for (int i = 1; i < q.size(); i++) {
            PlayBody b = q.body(i);
            if (b.role != PlayBody.Role.MOON) continue;
            double[] ref = hz.get(b.source.naifId + "@" + (long) t1);
            if (ref == null) continue;
            Ephemeris.icrfToWorld(ref, h);
            q.position(i, r);
            b.source.orbit.position(t1, e);
            double d = angle(r, h);
            if (d > w) { w = d; ww = b.name; }
            weph = Math.max(weph, angle(e, h));
        }
        check("Gegen Horizons: ab J2000 nach 1000 Tagen alle Hauptmonde unter 2°", w < 2,
                String.format("größte Abweichung %.2f° (%s); die Ephemeride selbst %.2f°", w, ww, weph));
    }

    // ------------------------------------------------------------------ Ereignisse
    static void events(UranusSystem sys) {
        // Roche: Miranda fällt
        Playground m = Playground.fromScenario(sys.scenario("MIRANDA"), sys);
        double roche = m.rocheKm(m.indexOf("Miranda"));
        for (int k = 0; k < 50 && m.events().isEmpty(); k++) m.advance(0.02 * 86_400, 0);
        PlayEvent br = m.events().isEmpty() ? null : m.events().get(0);
        double at = br == null ? 0 : len(br.km);
        check("Miranda zerbricht beim ersten Durchgang an der Roche-Grenze", br != null && br.kind == PlayEvent.Kind.BREAKUP
                        && at <= roche && at > roche * 0.95 && br.jd - START < 0.5,
                br == null ? "kein Ereignis" : String.format("nach %.2f d bei %,.0f km (Grenze %,.0f km)", br.jd - START, at, roche));
        check("… Trümmer: über 2000 gebundene Teilchen, Miranda ist weg", !m.debris().isEmpty() && m.debris().get(0).count > 2000
                && m.indexOf("Miranda") < 0, m.debris().isEmpty() ? "" : m.debris().get(0).count + " Teilchen");
        check("Cordelia (von Anfang an innerhalb ihrer Grenze) zerbricht nicht", cordeliaSafe(sys), "");
        // Verschmelzen: zwei Gäste frontal, Impuls und Masse bleiben
        // weit draußen (5·10⁷ km), damit Uranus den Impuls in der kurzen Zeit kaum ändert
        Playground p = Playground.withoutSun(Playground.fromScenario(one("A", new double[]{5e7, 0, 0}, new double[]{0, 0, 0}, Scenario.Role.GUEST, 1e21), sys));
        int b = p.addGuest("B", 3e21, 500, new double[]{5e7 + 3000, 0, 0}, new double[]{-2, 0, 0});
        double[] before = momentum(p);
        double mass0 = p.body(1).massKg() + p.body(b).massKg();
        for (int k = 0; k < 20 && p.events().isEmpty(); k++) p.advance(600, 0);
        double[] after = momentum(p);
        int kept = p.indexOf("B");
        check("Zusammenstoß: verschmelzen, Masse und Gesamtimpuls bleiben", !p.events().isEmpty() && p.events().get(0).kind == PlayEvent.Kind.MERGE
                        && p.size() == 2 && kept == 1 && Math.abs(p.body(1).massKg() / mass0 - 1) < 1e-12
                        && Math.hypot(Math.hypot(after[0] - before[0], after[1] - before[1]), after[2] - before[2]) < 1e-4 * len(before),
                p.events().isEmpty() ? "kein Ereignis" : p.events().get(0).text + String.format(" · Impuls ±%.1e",
                        Math.hypot(Math.hypot(after[0] - before[0], after[1] - before[1]), after[2] - before[2]) / len(before)));
        // Sturz in Uranus
        Playground f = Playground.withoutSun(Playground.fromScenario(one("Stein", new double[]{200_000, 0, 0}, new double[]{0, 0, 0.2}, Scenario.Role.GUEST, 1e18), sys));
        double mu0 = f.body(0).gm;
        f.advance(3 * 86_400, 0);
        check("Sturz in Uranus: Körper verschwindet, Uranus wird schwerer", !f.events().isEmpty() && f.events().get(0).kind == PlayEvent.Kind.IMPACT
                && f.size() == 1 && f.body(0).gm > mu0, f.events().isEmpty() ? "kein Ereignis" : f.events().get(0).text);
        // Hill-Sphäre
        Playground g = Playground.withoutSun(Playground.fromScenario(one("Flüchtling", new double[]{6.9e7, 0, 0}, new double[]{2, 0, 0}, Scenario.Role.GUEST, 1e18), sys));
        g.advance(20 * 86_400, 0);
        check("Wer die Hill-Sphäre (70 Mio. km) verlässt, wird gemeldet und entfernt", !g.events().isEmpty()
                && g.events().get(0).kind == PlayEvent.Kind.ESCAPE && g.size() == 1, g.events().isEmpty() ? "kein Ereignis" : g.events().get(0).text);
    }

    static boolean cordeliaSafe(UranusSystem sys) {
        Playground p = Playground.fromSystem(sys, START, true, false, "C", "C", "");
        int c = p.indexOf("Cordelia");
        boolean inside = p.distance(c) < p.rocheKm(c);
        p.advance(2 * 86_400, 0);
        return inside && p.indexOf("Cordelia") > 0 && p.debris().isEmpty();
    }

    static double[] momentum(Playground p) {
        double[] m = new double[3], v = new double[3];
        // relativ zu Uranus, gewichtet — Uranus selbst trägt −Σ bei, also Summe der Monde reicht zum Vergleich
        for (int i = 1; i < p.size(); i++) {
            p.velocity(i, v);
            for (int k = 0; k < 3; k++) m[k] += p.body(i).massKg() * v[k];
        }
        return m;
    }

    // ------------------------------------------------------------------ Rückgängig
    static void undo(UranusSystem sys) {
        Playground p = Playground.fromScenario(sys.scenario("HEUTE"), sys);
        int t = p.indexOf("Titania");
        double[] r0 = new double[3], r1 = new double[3];
        p.position(t, r0);
        double gm0 = p.body(t).gm;
        p.setGm(t, gm0 * 100);
        p.advance(10 * 86_400, 0);
        boolean undone = p.undo();
        p.position(p.indexOf("Titania"), r1);
        check("Rückgängig: Masse und Lage wie vor dem Eingriff", undone && p.body(p.indexOf("Titania")).gm == gm0 && len(new double[]{r1[0] - r0[0], r1[1] - r0[1], r1[2] - r0[2]}) < 1e-9
                && p.time() == 0, String.format("GM %.4g, Zeit %.1f s", p.body(p.indexOf("Titania")).gm, p.time()));
    }

    // ------------------------------------------------------------------ Szenarien
    static void scenarios(UranusSystem sys) {
        List<String> codes = new ArrayList<>();
        List<Integer> n = new ArrayList<>();
        for (Scenario s : sys.scenarios()) { codes.add(s.code); n.add(s.items().size()); }
        check("5 eingebaute Szenarien mit 15/15/15/16/14 Körpern", codes.equals(List.of("HEUTE", "TITANIA", "MIRANDA", "BESUCHER", "GEDRAENGE"))
                && n.equals(List.of(15, 15, 15, 16, 14)), codes + " " + n);
        Scenario t = sys.scenario("TITANIA"), h = sys.scenario("HEUTE");
        check("Schwere Titania: hundertfache Masse, sonst derselbe Stand", Math.abs(t.item("Titania").massKg / h.item("Titania").massKg / 100 - 1) < 1e-6
                && Arrays.equals(t.item("Oberon").r, h.item("Oberon").r), String.format("%.3g kg", t.item("Titania").massKg));
        Scenario g = sys.scenario("GEDRAENGE");
        boolean inner = true;
        for (Scenario.Item it : g.items()) { Body b = sys.find(it.name); inner &= b != null && b.group == BodyGroup.INNER && it.massEstimated; }
        check("Gedränge: nur innere Monde, Massen als geschätzt markiert", inner && g.items().size() == 14, "");
        // Rundreise Szenario → Spielwiese → Szenario (ICRF ↔ Welt)
        Playground p = Playground.fromScenario(h, sys);
        Scenario back = p.toScenario("X", "X", "", Scenario.Origin.USER, 1);
        double worst = 0, vworst = 0;
        for (int i = 0; i < h.items().size(); i++) {
            Scenario.Item a = h.items().get(i), b = back.items().get(i);
            worst = Math.max(worst, len(new double[]{a.r[0] - b.r[0], a.r[1] - b.r[1], a.r[2] - b.r[2]}));
            vworst = Math.max(vworst, len(new double[]{a.v[0] - b.v[0], a.v[1] - b.v[1], a.v[2] - b.v[2]}));
        }
        check("Speichern ohne Verlust: Szenario → Spielwiese → Szenario", worst < 1e-6 && vworst < 1e-12 && Math.abs(back.energyJ() / h.energyJ() - 1) < 1e-12,
                String.format("%.1e km, %.1e km/s", worst, vworst));
        // Vorausschau = was dann passiert
        Playground q = Playground.fromScenario(h, sys);
        int o = q.indexOf("Oberon");
        double[] r = new double[3], v = new double[3];
        q.position(o, r);
        q.velocity(o, v);
        double[][] pred = q.predict(o, r, v, 0, 5, 50, 0);
        q.advance(5 * 86_400, 0);
        q.position(o, r);
        double[] last = pred[pred.length - 1];
        double d = len(new double[]{r[0] - last[0], r[1] - last[1], r[2] - last[2]});
        check("Vorausschau trifft, wohin Oberon in 5 Tagen wirklich kommt", d < 1, String.format("%.2e km", d));
        // Tempo: wie viel Spielzeit schafft eine Rechensekunde?
        Playground s = Playground.fromScenario(h, sys);
        long t0 = System.nanoTime();
        s.advance(100 * 86_400, 0);
        double dps = 100 / ((System.nanoTime() - t0) / 1e9);
        check("Tempo: „Heute, frei“ schafft über 100 Tage je Rechensekunde", dps > 100, String.format("%.0f Tage je Sekunde hier im Prüfcontainer", dps));
    }

    // ------------------------------------------------------------------ Bedienung
    static void ui(UranusSystem sys) {
        SceneSettings s = new SceneSettings();
        s.magneto = false;
        SimClock c = new SimClock();
        c.setJd(START);
        c.setRate(0.25);
        Renderer r = new Renderer(sys, s, c);
        r.settle();
        Renderer.Frame f = r.render(800, 450, 0.05);
        s.playLive = true;
        f = r.render(800, 450, 0.05);
        check("Knopf „Spielwiese“: echter Stand, Uhr folgt der Spielzeit", s.playActive && r.playground() != null
                && Math.abs(c.jd() - r.playground().jdUtc()) < 1e-9, String.valueOf(s.playActive));
        for (int i = 0; i < 8; i++) f = r.render(800, 450, 0.05);
        Renderer.Pick ti = null;
        for (Renderer.Pick p : f.picks) if (p.body.name.equals("Titania")) ti = p;
        check("Titania im Bild anklickbar", ti != null, "");
        if (ti == null) return;
        Playground play = r.playground();
        int t = play.indexOf("Titania");
        double gm = play.body(t).gm;
        s.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.WHEEL, ti.x, ti.y, -1, false, false));
        f = r.render(800, 450, 0.05);
        check("Mausrad über Titania: Masse ×√10", Math.abs(play.body(play.indexOf("Titania")).gm / gm - Math.sqrt(10)) < 1e-9,
                String.format("×%.4f", play.body(play.indexOf("Titania")).gm / gm));
        // Ziehen: 60 Pixel nach außen, loslassen
        for (Renderer.Pick p : f.picks) if (p.body.name.equals("Titania")) ti = p;
        double[] r0 = new double[3], r1 = new double[3];
        play.position(play.indexOf("Titania"), r0);
        double dx = ti.x - 400, dy = ti.y - 225, l = Math.hypot(dx, dy);
        s.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.PRESS, ti.x, ti.y, 0, false, false));
        r.render(800, 450, 0.05);
        double tBefore = play.time();
        for (int k = 1; k <= 6; k++) {
            s.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.DRAG, ti.x + dx / l * 10 * k, ti.y + dy / l * 10 * k, 0, false, true));
            r.render(800, 450, 0.05);
        }
        check("Beim Ziehen steht die Spielzeit", play.time() == tBefore, "");
        s.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.RELEASE, ti.x + dx / l * 60, ti.y + dy / l * 60, 0, false, true));
        r.render(800, 450, 0.05);
        play.position(play.indexOf("Titania"), r1);
        check("Ziehen und Loslassen: Titania liegt weiter draußen", len(r1) > len(r0) * 1.05,
                String.format("%,.0f → %,.0f km", len(r0), len(r1)));
        // Umschalt + Ziehen: ein Gast
        int n0 = play.size();
        s.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.PRESS, 600, 300, 0, true, false));
        r.render(800, 450, 0.05);
        for (int k = 1; k <= 5; k++) {
            s.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.DRAG, 600 - k * 8, 300 - k * 10, 0, false, true));
            r.render(800, 450, 0.05);
        }
        s.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.RELEASE, 560, 250, 0, false, true));
        r.render(800, 450, 0.05);
        check("Umschalt + Ziehen setzt einen Gast", play.size() == n0 + 1 && play.body(play.size() - 1).role == PlayBody.Role.GUEST,
                play.size() > n0 ? play.body(play.size() - 1).name : "kein Gast");
        s.playUndo = true;
        r.render(800, 450, 0.05);
        check("Rückgängig nimmt den Gast wieder weg", play.size() == n0, "");
        // Speichern: Stand als eigenes Szenario
        s.playSaveRequest = true;
        r.render(800, 450, 0.05);
        Scenario saved = s.playSaved;
        check("Speichern erzeugt ein eigenes Szenario", saved != null && saved.origin == Scenario.Origin.USER && saved.code.startsWith("EIGEN_")
                && saved.items().size() == play.size() - 1, saved == null ? "" : saved.title);
        // Szenario wählen, dann zurück
        s.playScenario = sys.scenario("MIRANDA");
        r.render(800, 450, 0.05);
        check("Szenario starten: Tempo aus dem Szenario", r.playground() != null && r.playground().code.equals("MIRANDA") && s.playRate == 0.1, "");
        s.playStop = true;
        r.render(800, 450, 0.05);
        check("„Zurück“: Ephemeride, Uhr und Tempo wie vor der Spielwiese", !s.playActive && r.playground() == null
                && Math.abs(c.jd() - START) < 1e-3 && c.rate() == 0.25 && !c.isPaused(), String.format("%.4f", c.jd() - START));
        s.tourRequest = "VOYAGER";
        s.playLive = true;
        r.render(800, 450, 0.05);
        check("Während der Spielwiese startet keine Geschichte", r.activeTour() == null && s.playActive, "");
        s.playStop = true;
        r.render(800, 450, 0.05);
    }

    // ------------------------------------------------------------------ Skripte
    static int count(String s, String sub) { int n = 0, i = 0; while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); } return n; }

    static void scripts() throws Exception {
        String sc = Files.readString(Paths.get("db", "09_scenarios.sql"), StandardCharsets.UTF_8);
        check("09_scenarios.sql: 5 Szenarien, 75 Körper", count(sc, "ura_api.put_scenario(") == 5 && count(sc, "ura_api.put_scenario_body(") == 75,
                count(sc, "ura_api.put_scenario(") + " / " + count(sc, "ura_api.put_scenario_body("));
        String schema = Files.readString(Paths.get("db", "01_schema.sql"), StandardCharsets.UTF_8);
        check("Schema: URA_SCENARIO, URA_SCENARIO_BODY, Dichte nur ohne gemessene Masse", schema.contains("CREATE TABLE ura_scenario (")
                && schema.contains("CREATE TABLE ura_scenario_body (") && schema.contains("ura_body_dens_ck")
                && schema.contains("ON DELETE CASCADE,\n  CONSTRAINT ura_sb_body_fk"), "");
        String pk = Files.readString(Paths.get("db", "02_package.sql"), StandardCharsets.UTF_8);
        check("URA_API 1.2: scenarios, scenario_bodies, energy, put_/drop_scenario", pk.contains("'1.2'") && pk.contains("FUNCTION scenarios")
                && pk.contains("FUNCTION scenario_bodies") && pk.contains("FUNCTION energy") && pk.contains("PROCEDURE drop_scenario")
                && pk.contains("-20005"), "");
        String drop = Files.readString(Paths.get("db", "00_drop.sql"), StandardCharsets.UTF_8);
        check("00_drop räumt Szenarien und ihre Sequenzen mit ab", drop.contains("'URA_SCENARIO_BODY'") && drop.contains("'URA_SB_SEQ'"), "");
        String b = Files.readString(Paths.get("db", "03_bodies.sql"), StandardCharsets.UTF_8);
        check("03_bodies.sql: 24 Monde mit angenommener Dichte 1,3", count(b, "p_density => 1.3") == 24, count(b, "p_density => 1.3") + "");
    }
}
