import com.dan.uranus.model.*;
import com.dan.uranus.play.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Erzeugt die fünf eingebauten Szenarien der Spielwiese (Phase 7):
 * db/09_scenarios.sql, src/.../eingebaut_szenarien.csv und den Bericht db/quellen/spielwiese.txt.
 * <p>
 * Alle Szenarien starten am 23.09.2026, 00:00 UTC aus der Ephemeride; die Startgeschwindigkeiten sind
 * an die Ephemeride angeglichen (Playground.fromSystem). Zahlen werden einmal gerundet und dann als
 * derselbe Text in SQL und CSV geschrieben — damit ist „Datenbank = eingebaut“ exakt prüfbar.
 * Aufruf nach UraGen und UraStoryGen: java -cp build/classes;build/tools UraPlayGen
 */
public class UraPlayGen {

    static final double START = SimClock.jdOf(Instant.parse("2026-09-23T00:00:00Z"));
    static final Path Q = Paths.get("db", "quellen");
    static final StringBuilder REPORT = new StringBuilder();

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMANY);
        UranusSystem sys = UranusSystem.builtIn();
        List<Scenario> list = new ArrayList<>();
        say("Spielwiese: eingebaute Szenarien (tools/UraPlayGen), Start 23.09.2026, 00:00 UTC");
        say("");

        // 1) Heute, frei
        Playground p = base(sys);
        list.add(rounded(p.toScenario("HEUTE", "Heute, frei",
                "Der echte Stand vom 23. September 2026, nur weitergerechnet: die Hauptmonde mit Masse, die Irregulären als "
                        + "Testkörper, die Sonne als Störer. Kleine Kreise zeigen, wo die Ephemeride die Monde sieht — der Abstand "
                        + "ist die ehrliche Probe der Rechnung.", Scenario.Origin.BUILTIN, 10), true));

        // 2) Schwere Titania
        p = base(sys);
        int ti = p.indexOf("Titania");
        p.setGm(ti, p.body(ti).gm * 100);
        list.add(rounded(p.toScenario("TITANIA", "Schwere Titania",
                "Titania mit hundertfacher Masse: 3,4·10²³ kg, das 4,6-Fache unseres Mondes. Die Mulde um sie wird tief; "
                        + "die Kreise zeigen, wo die Monde ohne den Eingriff stünden.", Scenario.Origin.BUILTIN, 10), true));

        // 3) Miranda fällt: an ihrer Stelle so abgebremst, dass das Perizentrum bei 45 000 km liegt
        p = base(sys);
        int mi = p.indexOf("Miranda");
        double[] r = new double[3], v = new double[3];
        p.position(mi, r);
        p.velocity(mi, v);
        double rn = norm(r), rp = 45_000, mu = UranusSystem.URANUS_GM + p.body(mi).gm;
        double vr = dot(r, v) / rn;
        double[] vt = {v[0] - vr * r[0] / rn, v[1] - vr * r[1] / rn, v[2] - vr * r[2] / rn};
        double va = Math.sqrt(mu * 2 * rp / (rn * (rn + rp))), vtn = norm(vt);
        p.place(mi, r, new double[]{vt[0] / vtn * va, vt[1] / vtn * va, vt[2] / vtn * va});
        say(String.format("Miranda fällt: bei %,.0f km von %.3f auf %.3f km/s abgebremst, Perizentrum 45.000 km, Roche-Grenze %,.0f km",
                rn, vtn, va, p.rocheKm(mi)));
        list.add(rounded(p.toScenario("MIRANDA", "Miranda fällt",
                String.format("Miranda wird an ihrer Stelle abgebremst, sodass ihr Perizentrum bei 45 000 km liegt — unter der "
                        + "Roche-Grenze für ihre Dichte (%,.0f km). Beim ersten Durchgang zerbricht sie; die Trümmer ziehen sich "
                        + "über die Umläufe zum Ring.", p.rocheKm(mi)), Scenario.Origin.BUILTIN, 0.1), true));

        // 4) Besucher: Erdmasse auf einer Hyperbel, q = 800 000 km, v∞ = 5 km/s, 30° geneigt, Perizentrum nach 30 Tagen
        p = base(sys);
        double gmE = 5.972e24 * UranusSystem.G_KM, muE = UranusSystem.URANUS_GM + gmE, vinf = 5, q = 800_000;
        double aH = muE / (vinf * vinf), e = 1 + q * vinf * vinf / muE, n = Math.sqrt(muE / (aH * aH * aH));
        double m = n * (-30 * 86_400.0), h = Math.log(m / e + Math.sqrt(m / e * m / e + 1));
        for (int k = 0; k < 60; k++) h -= (e * Math.sinh(h) - h - m) / (e * Math.cosh(h) - 1);
        double hd = n / (e * Math.cosh(h) - 1), s = Math.sqrt(e * e - 1);
        double xp = aH * (e - Math.cosh(h)), yp = aH * s * Math.sinh(h), vxp = -aH * Math.sinh(h) * hd, vyp = aH * s * Math.cosh(h) * hd;
        double inc = Math.toRadians(30);
        double[] pHat = {1, 0, 0}, qHat = {0, Math.sin(inc), Math.cos(inc)};                // Normale (0, −cos, sin): rechtläufig wie die Monde
        double[] rg = {xp * pHat[0] + yp * qHat[0], xp * pHat[1] + yp * qHat[1], xp * pHat[2] + yp * qHat[2]};
        double[] vg = {vxp * pHat[0] + vyp * qHat[0], vxp * pHat[1] + vyp * qHat[1], vxp * pHat[2] + vyp * qHat[2]};
        p.addGuest("Besucher", 5.972e24, 6371, rg, vg);
        say(String.format("Besucher: Erdmasse, Hyperbel e = %.3f, q = 800.000 km, v∞ = 5 km/s, Start in %,.0f km mit %.2f km/s", e, norm(rg), norm(vg)));
        list.add(rounded(p.toScenario("BESUCHER", "Besucher",
                "Ein Körper von Erdmasse kommt mit 5 km/s aus der Ferne und fliegt 30 Tage nach dem Start in 800 000 km an Uranus "
                        + "vorbei, 30° gegen den Äquator geneigt — gut 200 000 km an Oberons Bahn vorbei.", Scenario.Origin.BUILTIN, 10), true));

        // 5) Gedränge: nur die inneren Monde, Massen ×1000
        p = Playground.fromSystem(sys, START, true, false, "GEDRAENGE", "", "");
        for (int i = p.size() - 1; i >= 1; i--) {
            PlayBody b = p.body(i);
            if (b.source != null && b.source.group == BodyGroup.INNER) p.setGm(i, b.gm * 1000);
        }
        Scenario g = p.toScenario("GEDRAENGE", "Gedränge", "", Scenario.Origin.BUILTIN, 20);
        List<Scenario.Item> inner = new ArrayList<>();
        for (Scenario.Item it : g.items()) {
            Body b = sys.find(it.name);
            if (b != null && b.group == BodyGroup.INNER) inner.add(it);
        }
        list.add(rounded(new Scenario("GEDRAENGE", "Gedränge",
                "Nur die inneren Monde, mit tausendfach überhöhter Masse (Dichte 1,3 g/cm³ angenommen). So zeigt sich in Minuten, "
                        + "was sonst Jahrmillionen dauert. Ob es wirklich geschieht, ist offen: Frühere Rechnungen sagten "
                        + "Zusammenstöße voraus (Cressida–Desdemona unter 100 Mio. Jahren), eine Arbeit von 2022 findet die "
                        + "Belinda-Gruppe über 10⁸ Jahre stabil.", START, 30, Scenario.Origin.BUILTIN, false, inner), false));

        say("");
        for (Scenario sc : list) say(String.format("%-10s %-16s %2d Körper · %5.2f d/s · Energie %.6e J", sc.code, sc.title,
                sc.items().size(), sc.rateDaysPerS, sc.energyJ()));
        say("");
        runs(sys, list);
        writeSql(list);
        writeCsv(list);
        UraGen.write(Q.resolve("spielwiese.txt"), REPORT.toString());
        System.out.print(REPORT);
    }

    static Playground base(UranusSystem sys) {
        return Playground.fromSystem(sys, START, false, true, "", "", "");
    }

    /** Einmal runden (Text wie im SQL) und daraus wieder lesen: CSV und Datenbank tragen dieselben Zahlen. */
    static Scenario rounded(Scenario s, boolean compare) {
        List<Scenario.Item> items = new ArrayList<>();
        for (Scenario.Item it : s.items())
            items.add(new Scenario.Item(it.name, it.role, num(mass(it.massKg)), it.massEstimated, num(UraGen.lit(it.radiusKm)),
                    new double[]{num(pos(it.r[0])), num(pos(it.r[1])), num(pos(it.r[2]))},
                    new double[]{num(vel(it.v[0])), num(vel(it.v[1])), num(vel(it.v[2]))}));
        return new Scenario(s.code, s.title, s.description, num(UraGen.lit(s.startJd)), s.rateDaysPerS, s.origin, compare, items);
    }

    static String pos(double v) { return String.format(Locale.ROOT, "%.6f", v); }                // mm

    static String vel(double v) { return String.format(Locale.ROOT, "%.12f", v); }               // nm/s

    static String mass(double v) { return String.format(Locale.ROOT, "%.6e", v); }

    static double num(String s) { return Double.parseDouble(s); }

    /** Probeläufe: was in jedem Szenario passiert, für den Bericht (und als Anhalt für die Prüfungen). */
    static void runs(UranusSystem sys, List<Scenario> list) {
        say("Probeläufe (ohne Zeitbudget)");
        for (Scenario sc : list) {
            Playground p = Playground.fromScenario(sc, sys);
            double days = sc.code.equals("MIRANDA") ? 2 : sc.code.equals("GEDRAENGE") ? 400 : sc.code.equals("BESUCHER") ? 120 : 365;
            long t0 = System.nanoTime();
            double step = Math.max(0.5, days / 200);
            for (double d = 0; d < days; d += step) p.advance(step * 86_400, 0);
            double ms = (System.nanoTime() - t0) / 1e6;
            say(String.format("  %-10s %5.0f Tage in %6.0f ms (%4.0f Tage je Rechensekunde), Energiefehler %.1e", sc.code, days, ms,
                    days / ms * 1000, p.energyError()));
            for (PlayEvent e : p.events()) say(String.format("             %7.2f d  %s", e.jd - sc.startJd, e.text));
            if (sc.compareEphemeris && sc.code.equals("HEUTE")) {
                double[] r = new double[3], x = new double[3];
                for (int i = 1; i < p.size(); i++) {
                    PlayBody b = p.body(i);
                    if (b.role != PlayBody.Role.MOON || b.source == null) continue;
                    p.position(i, r);
                    b.source.orbit.position(com.dan.uranus.physics.TimeScale.tdb(p.jdUtc()), x);
                    say(String.format("             %-8s nach einem Jahr %.2f° neben der Ephemeride", b.name, angle(r, x)));
                }
            }
            if (sc.code.equals("BESUCHER") || sc.code.equals("TITANIA"))
                for (int i = 1; i < p.size(); i++) {
                    PlayBody b = p.body(i);
                    if (b.role != PlayBody.Role.MOON) continue;
                    double[] el = p.elements(i);
                    say(String.format("             %-8s danach e = %.3f, i = %.1f° (vorher e = %.3f)", b.name, el[1], el[2], b.source.orbit.e));
                }
            for (Debris d : p.debris()) {
                double[] ar = d.aRange();
                say(String.format("             Trümmer von %s: %d Teilchen, Halbachsen %,.0f–%,.0f km", d.from, d.count, ar[0], ar[1]));
            }
        }
    }

    static void writeSql(List<Scenario> list) throws Exception {
        StringBuilder s = new StringBuilder(UraGen.sqlHead("09 - Szenarien der Spielwiese",
                "Erzeugt von tools/UraPlayGen: Zustandsvektoren relativ zu Uranus, ICRF (km, km/s). Nicht von Hand ändern."));
        for (Scenario sc : list) {
            s.append("BEGIN\n  ura_api.put_scenario(p_code => ").append(UraGen.str(sc.code)).append(", p_title => ").append(UraGen.str(sc.title))
                    .append(",\n    p_description => ").append(UraGen.str(sc.description))
                    .append(",\n    p_start_jd => ").append(UraGen.lit(sc.startJd)).append(", p_rate => ").append(UraGen.lit(sc.rateDaysPerS))
                    .append(", p_origin => 'BUILTIN', p_compare => ").append(sc.compareEphemeris ? "'J'" : "'N'").append(");\n");
            int k = 1;
            for (Scenario.Item it : sc.items())
                s.append("  ura_api.put_scenario_body(").append(UraGen.str(sc.code)).append(", ").append(k++).append(", ")
                        .append(UraGen.str(it.name)).append(", ").append(UraGen.str(it.role.name())).append(", ")
                        .append(mass(it.massKg)).append(", ").append(it.massEstimated ? "'J'" : "'N'").append(", ").append(UraGen.lit(it.radiusKm))
                        .append(",\n    ").append(pos(it.r[0])).append(", ").append(pos(it.r[1])).append(", ").append(pos(it.r[2]))
                        .append(", ").append(vel(it.v[0])).append(", ").append(vel(it.v[1])).append(", ").append(vel(it.v[2])).append(");\n");
            s.append("END;\n/\n\n");
        }
        s.append("COMMIT;\n");
        UraGen.write(Paths.get("db", "09_scenarios.sql"), s.toString());
    }

    static void writeCsv(List<Scenario> list) throws Exception {
        StringBuilder s = new StringBuilder("# S;code;title;start_jd;rate;origin;compare;description | B;code;seq;name;role;mass_kg;estimated;radius_km;x;y;z;vx;vy;vz\n");
        s.append("typ;code;a;b;c;d;e;f;g;h;i;j;k;l\n");
        for (Scenario sc : list) {
            s.append("S;").append(sc.code).append(';').append(esc(sc.title)).append(';').append(UraGen.lit(sc.startJd)).append(';')
                    .append(UraGen.lit(sc.rateDaysPerS)).append(';').append(sc.origin).append(';').append(sc.compareEphemeris ? "J" : "N")
                    .append(';').append(esc(sc.description)).append('\n');
            int k = 1;
            for (Scenario.Item it : sc.items())
                s.append("B;").append(sc.code).append(';').append(k++).append(';').append(esc(it.name)).append(';').append(it.role).append(';')
                        .append(mass(it.massKg)).append(';').append(it.massEstimated ? "J" : "N").append(';').append(UraGen.lit(it.radiusKm)).append(';')
                        .append(pos(it.r[0])).append(';').append(pos(it.r[1])).append(';').append(pos(it.r[2])).append(';')
                        .append(vel(it.v[0])).append(';').append(vel(it.v[1])).append(';').append(vel(it.v[2])).append('\n');
        }
        UraGen.write(Paths.get("src", "com", "dan", "uranus", "model", "eingebaut_szenarien.csv"), s.toString());
    }

    static String esc(String s) { return s.replace("\n", "\\n").replace(";", "\\s"); }

    static double norm(double[] a) { return Math.sqrt(dot(a, a)); }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double angle(double[] a, double[] b) { return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot(a, b) / (norm(a) * norm(b)))))); }

    static void say(String s) { REPORT.append(s).append('\n'); }
}
