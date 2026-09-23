import com.dan.uranus.model.Body;
import com.dan.uranus.model.BodyGroup;
import com.dan.uranus.model.Craft;
import com.dan.uranus.model.SimClock;
import com.dan.uranus.model.StoryEvent;
import com.dan.uranus.model.Tour;
import com.dan.uranus.model.UranusSystem;
import com.dan.uranus.physics.Ephemeris;
import com.dan.uranus.physics.TimeScale;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Erzeugt die Daten der Geschichten (Phase 6):
 * <ul>
 *   <li>Voyager 2: Hyperbel aus den Horizons-Vektoren, Abweichung über alle Stützstellen,</li>
 *   <li>Ereignisse: Entdeckungen, Vorbeiflug (berechnet), Jahreszeiten 1900–2100 (berechnet),</li>
 *   <li>vier Kamerafahrten,</li>
 * </ul>
 * geschrieben als db/06_events.sql, db/07_voyager.sql, db/08_tours.sql, eingebaut_*.csv und
 * db/quellen/geschichten.txt. Aufruf aus dem Projektordner nach UraGen:
 * java -cp build/classes;build/tools UraStoryGen
 */
public class UraStoryGen {

    static final Path Q = Paths.get("db", "quellen");
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN);
    static final DateTimeFormatter MIN = DateTimeFormatter.ofPattern("d. MMMM yyyy, HH:mm 'UTC'", Locale.GERMAN);
    static StringBuilder report = new StringBuilder();

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        UranusSystem sys = UranusSystem.builtIn();
        report.append("Geschichten: erzeugte Daten (tools/UraStoryGen)\n\n");

        Craft v = voyager();
        List<StoryEvent> events = new ArrayList<>();
        discoveries(sys, events);
        flyby(sys, v, events);
        seasons(events);
        events.sort((a, b) -> Double.compare(a.jdUtc, b.jdUtc));
        List<Tour> tours = tours(sys, v, events);

        writeEvents(events);
        writeTours(tours);
        UraGen.write(Q.resolve("geschichten.txt"), report.toString());
        System.out.print(report);
    }

    // ================================================================== Zeit

    static double jdOf(int y, int m, int d, int hh, int mm) {
        return SimClock.jdOf(LocalDate.of(y, m, d).atTime(hh, mm).toInstant(ZoneOffset.UTC));
    }

    static double utcOfTdb(double tdb) { return tdb - TimeScale.tdbMinusUtcSeconds(tdb) / 86_400.0; }

    static String day(double jdUtc) { return SimClock.instantOf(jdUtc).atZone(ZoneOffset.UTC).format(DAY); }

    static String minute(double jdUtc) { return SimClock.instantOf(jdUtc).atZone(ZoneOffset.UTC).format(MIN); }

    static int year(double jdUtc) { return SimClock.instantOf(jdUtc).atZone(ZoneOffset.UTC).getYear(); }

    static String km(double v) { return String.format(Locale.GERMAN, "%,.0f km", v); }

    // ================================================================== Voyager 2

    static List<double[]> states = new ArrayList<>();
    static Craft voyager;

    static Craft voyager() throws Exception {
        for (String[] c : UraGen.csv(Q.resolve("voyager2_horizons.csv"))) {
            double[] s = new double[7];
            for (int k = 0; k < 7; k++) s[k] = Double.parseDouble(c[k]);
            states.add(s);
        }
        // Zustand mit dem kleinsten Abstand: dort ist die Hyperbel am besten bestimmt
        double[] best = states.get(0);
        for (double[] s : states) if (norm(s[1], s[2], s[3]) < norm(best[1], best[2], best[3])) best = s;
        double mu = UranusSystem.URANUS_GM;
        double[] r = {best[1], best[2], best[3]}, vv = {best[4], best[5], best[6]};
        double rn = norm(r[0], r[1], r[2]), vn = norm(vv[0], vv[1], vv[2]);
        double[] h = Ephemeris.cross(r, vv);
        double hn = norm(h[0], h[1], h[2]);
        double[] ev = Ephemeris.cross(vv, h);
        for (int k = 0; k < 3; k++) ev[k] = ev[k] / mu - r[k] / rn;
        double e = norm(ev[0], ev[1], ev[2]);
        double a = 1 / (2 / rn - vn * vn / mu);                            // negativ
        double q = a * (1 - e);
        double inc = Math.toDegrees(Math.acos(h[2] / hn));
        double[] nv = {-h[1], h[0], 0};                                     // z × h
        double node = Math.toDegrees(Math.atan2(nv[1], nv[0]));
        if (node < 0) node += 360;
        double nn = norm(nv[0], nv[1], 0);
        double argp = Math.toDegrees(Math.acos((nv[0] * ev[0] + nv[1] * ev[1]) / (nn * e)));
        if (ev[2] < 0) argp = 360 - argp;
        double nu = Math.atan2(Ephemeris.dot(Ephemeris.cross(ev, r), h) / hn, Ephemeris.dot(ev, r));
        double hh = 2 * atanh(Math.sqrt((e - 1) / (e + 1)) * Math.tan(nu / 2));
        double m = e * Math.sinh(hh) - hh;
        double n = Math.sqrt(mu / Math.pow(-a, 3));
        double tp = best[0] - m / n / 86_400.0;
        double from = states.get(0)[0], to = states.get(states.size() - 1)[0];
        Craft probe = new Craft("Voyager 2", -32, tp, q, e, inc, node, argp, mu, from, to, Double.NaN, "");
        // Abweichung über alle Stützstellen (Horizons liefert ICRF → Welt)
        double worst = 0, at = 0;
        double[] p = new double[3], w = new double[3];
        for (double[] s : states) {
            probe.position(s[0], p);
            Ephemeris.icrfToWorld(new double[]{s[1], s[2], s[3]}, w);
            double d = norm(p[0] - w[0], p[1] - w[1], p[2] - w[2]);
            if (d > worst) { worst = d; at = s[0]; }
        }
        String src = "Horizons −32 · Hyperbel aus " + states.size() + " Vektoren · Missionsentwurf, Näherung";
        voyager = new Craft("Voyager 2", -32, tp, q, e, inc, node, argp, mu, from, to, UraGen.round(worst, 1), src);
        report.append(String.format(Locale.GERMAN, "Voyager 2: Periapsis %s (TDB-JD %.6f), q = %s, e = %.6f, i = %.3f°, Ω = %.3f°, ω = %.3f°%n",
                minute(utcOfTdb(tp)), tp, km(q), e, inc, node, argp));
        report.append(String.format(Locale.GERMAN, "  größte Abweichung von Horizons: %.1f km (bei %s, %s vom Zentrum)%n%n",
                worst, minute(utcOfTdb(at)), km(voyager.distance(at))));
        writeVoyager(voyager);
        return voyager;
    }

    static double atanh(double x) { return 0.5 * Math.log((1 + x) / (1 - x)); }

    static double norm(double x, double y, double z) { return Math.sqrt(x * x + y * y + z * z); }

    static void writeVoyager(Craft c) throws Exception {
        StringBuilder s = new StringBuilder(UraGen.sqlHead("07 - Voyager 2: Hyperbel und Horizons-Vektoren",
                "Erzeugt von tools/UraStoryGen aus db/quellen/voyager2_horizons.csv. Nicht von Hand ändern."));
        s.append("BEGIN\n  ura_api.put_craft(p_name => 'Voyager 2', p_naif => -32, p_tp_jd => ").append(UraGen.lit(c.tpJd))
                .append(", p_q_km => ").append(UraGen.lit(UraGen.round(c.qKm, 3))).append(", p_ecc => ").append(UraGen.lit(UraGen.round(c.e, 9))).append(",\n")
                .append("    p_incl_deg => ").append(UraGen.lit(UraGen.round(c.iDeg, 7))).append(", p_node_deg => ").append(UraGen.lit(UraGen.round(c.nodeDeg, 7)))
                .append(", p_argp_deg => ").append(UraGen.lit(UraGen.round(c.argpDeg, 7))).append(", p_gm_km3s2 => ").append(UraGen.lit(c.gm)).append(",\n")
                .append("    p_valid_from_jd => ").append(UraGen.lit(c.validFromJd)).append(", p_valid_to_jd => ").append(UraGen.lit(c.validToJd))
                .append(", p_fit_max_km => ").append(UraGen.lit(c.fitMaxKm)).append(",\n")
                .append("    p_source => 'HZ_VOYAGER2', p_note => ").append(UraGen.str(c.source)).append(");\nEND;\n/\n\n");
        s.append("-- ").append(states.size()).append(" Zustände wie von Horizons geliefert (ICRF, km, km/s)\nBEGIN\n");
        for (double[] st : states) {
            s.append("  ura_api.put_craft_state('Voyager 2', ").append(UraGen.lit(st[0]));
            for (int k = 1; k < 7; k++) s.append(", ").append(UraGen.lit(st[k]));
            s.append(");\n");
        }
        s.append("END;\n/\n\nCOMMIT;\n");
        UraGen.write(Paths.get("db", "07_voyager.sql"), s.toString());
        // eingebaute Hyperbel mit denselben gerundeten Werten wie die Datenbank
        String row = String.join(";", "Voyager 2", "-32", UraGen.lit(c.tpJd), UraGen.lit(UraGen.round(c.qKm, 3)),
                UraGen.lit(UraGen.round(c.e, 9)), UraGen.lit(UraGen.round(c.iDeg, 7)), UraGen.lit(UraGen.round(c.nodeDeg, 7)),
                UraGen.lit(UraGen.round(c.argpDeg, 7)), UraGen.lit(c.gm), UraGen.lit(c.validFromJd), UraGen.lit(c.validToJd),
                UraGen.lit(c.fitMaxKm), UranusSystem.esc(c.source));
        UraGen.write(Paths.get("src", "com", "dan", "uranus", "model", "eingebaut_sonden.csv"),
                "name;naif;tp_jd;q_km;ecc;incl_deg;node_deg;argp_deg;gm;valid_from_jd;valid_to_jd;fit_max_km;source\n" + row + "\n");
    }

    // ================================================================== Ereignisse

    static StoryEvent ev(double jd, StoryEvent.Kind k, StoryEvent.Precision p, String title, String text, String body, String ring) {
        return new StoryEvent(jd, k, p, title, text, body, ring);
    }

    static void discoveries(UranusSystem sys, List<StoryEvent> out) {
        out.add(ev(jdOf(1781, 3, 13, 22, 0), StoryEvent.Kind.DISCOVERY, StoryEvent.Precision.DAY, "Uranus entdeckt",
                "William Herschel sieht in Bath ein Scheibchen, das kein Stern ist, und hält es zuerst für einen Kometen. Es ist der erste Planet, der mit einem Fernrohr gefunden wurde.",
                null, null));
        Map<String, double[]> known = new LinkedHashMap<>();            // genau bekannte Tage
        known.put("Titania", new double[]{1787, 1, 11});
        known.put("Oberon", new double[]{1787, 1, 11});
        known.put("Ariel", new double[]{1851, 10, 24});
        known.put("Umbriel", new double[]{1851, 10, 24});
        known.put("Miranda", new double[]{1948, 2, 16});
        for (Body b : sys.bodies()) {
            double[] d = known.get(b.name);
            double jd = d != null ? jdOf((int) d[0], (int) d[1], (int) d[2], 0, 0) : jdOf(b.discoveredYear, 1, 1, 0, 0);
            out.add(ev(jd, StoryEvent.Kind.DISCOVERY, d != null ? StoryEvent.Precision.DAY : StoryEvent.Precision.YEAR,
                    b.name + " entdeckt", b.discoveredBy + (b.namesake.isEmpty() ? "" : " · Name aus " + b.namesake), b.name, null));
        }
        for (com.dan.uranus.model.Ring r : sys.rings()) {
            int y = UraGen.RING_YEAR.get(r.name);
            double jd = y == 1977 ? jdOf(1977, 3, 10, 20, 0) : jdOf(y, 1, 1, 0, 0);
            String text = y == 1977 ? "Der Stern SAO 158687 flackert vor und nach Uranus neunmal kurz: neun schmale Ringe. " + UraGen.RING_BY.get(r.name)
                    : UraGen.RING_BY.get(r.name);
            out.add(ev(jd, StoryEvent.Kind.DISCOVERY, y == 1977 ? StoryEvent.Precision.DAY : StoryEvent.Precision.YEAR,
                    "Ring " + r.name + " entdeckt", text, null, r.name));
        }
    }

    /** Berechnete Ereignisse des Vorbeiflugs: größte Annäherung, Ringebene, Miranda, Schatten. */
    static void flyby(UranusSystem sys, Craft c, List<StoryEvent> out) {
        double[] p = new double[3], m = new double[3];
        double caUtc = utcOfTdb(c.tpJd);
        double speed = c.speed(c.tpJd);
        out.add(ev(caUtc, StoryEvent.Kind.FLYBY, StoryEvent.Precision.MINUTE, "Größte Annäherung",
                String.format(Locale.GERMAN, "Voyager 2 fliegt %s über der Wolkenoberfläche vorbei (%s vom Zentrum), mit %.1f km/s relativ zu Uranus.",
                        km(c.qKm - UranusSystem.URANUS_RADIUS_KM), km(c.qKm), speed), null, null));
        report.append(String.format(Locale.GERMAN, "Größte Annäherung: %s, %s vom Zentrum, %.2f km/s%n", minute(caUtc), km(c.qKm), speed));
        // Ringebene: Vorzeichenwechsel der Weltkoordinate y
        double step = 1.0 / 1440, prevY = Double.NaN;
        for (double t = c.validFromJd; t <= c.validToJd; t += step) {
            c.position(t, p);
            if (!Double.isNaN(prevY) && Math.signum(p[1]) != Math.signum(prevY)) {
                double lo = t - step, hi = t;
                for (int i = 0; i < 50; i++) { double mid = (lo + hi) / 2; c.position(mid, m); if (Math.signum(m[1]) == Math.signum(prevY)) lo = mid; else hi = mid; }
                c.position(hi, m);
                double r = norm(m[0], m[1], m[2]);
                out.add(ev(utcOfTdb(hi), StoryEvent.Kind.FLYBY, StoryEvent.Precision.MINUTE, "Durch die Ringebene",
                        String.format(Locale.GERMAN, "Voyager 2 kreuzt die Äquatorebene %s vom Zentrum — %.1f Uranusradien, weit außerhalb der dichten Ringe. Die Plasmawellen-Antenne hört Staubkörner auf die Sonde prasseln.",
                                km(r), r / UranusSystem.URANUS_RADIUS_KM), null, null));
                report.append(String.format(Locale.GERMAN, "Ringebene: %s bei %s%n", minute(utcOfTdb(hi)), km(r)));
            }
            prevY = p[1];
        }
        // nächster Punkt zu jedem Hauptmond
        for (Body b : sys.bodies()) {
            if (b.group != BodyGroup.MAJOR) continue;
            double best = 1e18, when = 0;
            for (double t = c.tpJd - 1; t <= c.tpJd + 1; t += 1.0 / 2880) {
                c.position(t, p); b.orbit.position(t, m);
                double d = norm(p[0] - m[0], p[1] - m[1], p[2] - m[2]);
                if (d < best) { best = d; when = t; }
            }
            report.append(String.format(Locale.GERMAN, "  nächster Punkt zu %-8s %s bei %s%n", b.name + ":", minute(utcOfTdb(when)), km(best)));
            if (b.name.equals("Miranda"))
                out.add(ev(utcOfTdb(when), StoryEvent.Kind.FLYBY, StoryEvent.Precision.MINUTE, "Vorbei an Miranda",
                        String.format(Locale.GERMAN, "Nächster Punkt zu Miranda: %s. Die Bilder zeigen einen Mond aus Flicken — Kanten, Terrassen und Steilwände bis 20 km Höhe.", km(best)),
                        "Miranda", null));
        }
        // Uranusschatten (Sonnenbedeckung)
        boolean in = false;
        double enter = Double.NaN;
        double r2 = UranusSystem.URANUS_RADIUS_KM * UranusSystem.URANUS_RADIUS_KM;
        for (double t = c.tpJd - 0.5; t <= c.tpJd + 0.5; t += step) {
            c.position(t, p);
            double[] s = Ephemeris.sunDirWorld(utcOfTdb(t));
            double tp = Ephemeris.dot(p, s);
            boolean shadow = tp < 0 && Ephemeris.dot(p, p) - tp * tp < r2;
            if (shadow && !in) enter = t;
            if (!shadow && in) {
                out.add(ev(utcOfTdb(enter), StoryEvent.Kind.FLYBY, StoryEvent.Precision.MINUTE, "Im Uranusschatten",
                        String.format(Locale.GERMAN, "Für %.0f Minuten steht Uranus zwischen Sonde und Sonne. Das Sonnenlicht, das durch die Atmosphäre fällt, verrät deren Aufbau.", (t - enter) * 1440),
                        null, null));
                report.append(String.format(Locale.GERMAN, "Uranusschatten: %s bis %s%n", minute(utcOfTdb(enter)), minute(utcOfTdb(t))));
            }
            in = shadow;
        }
        report.append('\n');
    }

    /** Tagundnachtgleichen und Sonnenwenden 1900–2100 aus der Ephemeride. */
    static void seasons(List<StoryEvent> out) {
        double a = jdOf(1900, 1, 1, 0, 0), b = jdOf(2100, 12, 31, 0, 0), step = 5;
        double prev = Ephemeris.subsolarLatitudeDeg(a), prev2 = Double.NaN;
        for (double t = a + step; t <= b; t += step) {
            double l = Ephemeris.subsolarLatitudeDeg(t);
            if (Math.signum(l) != Math.signum(prev)) {
                double lo = t - step, hi = t;
                for (int i = 0; i < 50; i++) { double mid = (lo + hi) / 2; if (Math.signum(Ephemeris.subsolarLatitudeDeg(mid)) == Math.signum(prev)) lo = mid; else hi = mid; }
                boolean north = l > 0;
                out.add(ev(hi, StoryEvent.Kind.SEASON, StoryEvent.Precision.DAY, "Tagundnachtgleiche " + year(hi),
                        "Die Sonne steht über dem Äquator, die Ringe sind von der Kante beleuchtet. Danach wird es im " + (north ? "Norden" : "Süden")
                                + " Frühling — für 21 Jahre.", null, null));
                report.append("Tagundnachtgleiche: ").append(day(hi)).append('\n');
            }
            if (!Double.isNaN(prev2) && Math.abs(prev) > Math.abs(l) && Math.abs(prev) > Math.abs(prev2) && Math.abs(prev) > 60) {
                double lo = t - 2 * step, hi = t;
                for (int i = 0; i < 80; i++) {
                    double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
                    if (Math.abs(Ephemeris.subsolarLatitudeDeg(m1)) > Math.abs(Ephemeris.subsolarLatitudeDeg(m2))) hi = m2; else lo = m1;
                }
                double tt = (lo + hi) / 2, lat = Ephemeris.subsolarLatitudeDeg(tt);
                boolean north = lat > 0;
                out.add(ev(tt, StoryEvent.Kind.SEASON, StoryEvent.Precision.DAY, "Sonnenwende " + year(tt),
                        String.format(Locale.GERMAN, "Die Sonne steht über %.1f° %s, fast über dem Pol. Mittsommer im %s: seit 21 Jahren Tag am %s, am anderen Pol Nacht.",
                                Math.abs(lat), north ? "Nord" : "Süd", north ? "Norden" : "Süden", north ? "Nordpol" : "Südpol"), null, null));
                report.append(String.format(Locale.GERMAN, "Sonnenwende: %s, %.1f° %s%n", day(tt), Math.abs(lat), north ? "Nord" : "Süd"));
            }
            prev2 = prev;
            prev = l;
        }
        report.append('\n');
    }

    // ================================================================== Fahrten

    static double[] dirToYawPitch(double[] d) {
        double l = norm(d[0], d[1], d[2]);
        return new double[]{Math.toDegrees(Math.atan2(d[0] / l, d[2] / l)), Math.toDegrees(Math.asin(d[1] / l))};
    }

    static Tour.Step step(double at, double jd, double[] yp, double dist, String target, String layers, String title, String caption) {
        return new Tour.Step(at, jd, UraGen.round(yp[0], 2), UraGen.round(yp[1], 2), dist, target, layers, title, caption);
    }

    static StoryEvent find(List<StoryEvent> ev, String title) {
        for (StoryEvent e : ev) if (e.title.equals(title)) return e;
        throw new IllegalStateException("Ereignis fehlt: " + title);
    }

    static List<Tour> tours(UranusSystem sys, Craft c, List<StoryEvent> ev) {
        List<Tour> out = new ArrayList<>();
        double ca = utcOfTdb(c.tpJd);
        double[] p = new double[3], p2 = new double[3];
        // Bahnebene der Sonde (Welt): Blick senkrecht darauf zeigt die Hyperbel im Profil
        c.position(c.tpJd - 0.05, p); c.position(c.tpJd + 0.05, p2);
        double[] hn = Ephemeris.normalize(Ephemeris.cross(p, p2));
        if (hn[1] < 0) hn = new double[]{-hn[0], -hn[1], -hn[2]};
        double[] plane = dirToYawPitch(new double[]{hn[0] + 0.25, hn[1] + 0.35, hn[2]});
        StoryEvent mir = find(ev, "Vorbei an Miranda"), ring = find(ev, "Durch die Ringebene"), shade = find(ev, "Im Uranusschatten");
        List<Tour.Step> v = new ArrayList<>();
        String L0 = "moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;time=ease";
        v.add(step(0, ca - 1.25, plane, 36, "Uranus", L0, "Voyager 2 · Januar 1986",
                "Nach achteinhalb Jahren und zwei Planeten kommt die Sonde fast genau aus Richtung Sonne. Uranus zeigt ihr den Südpol: Es ist Sommer im Süden."));
        v.add(step(12, ca - 0.35, behind(c, ca - 0.35, 0.45), 4.5, "Voyager 2", L0, "Anflug",
                "Noch gut acht Stunden. Die Kameras finden zehn neue Monde und zwei neue Ringe. Das Magnetfeld ist um 59° gekippt — niemand hatte das erwartet."));
        double[] order = {mir.jdUtc, ring.jdUtc};
        String[] titles = {mir.title, ring.title};
        String[] texts = {mir.text, ring.text};
        if (ring.jdUtc < mir.jdUtc) { order = new double[]{ring.jdUtc, mir.jdUtc}; titles = new String[]{ring.title, mir.title}; texts = new String[]{ring.text, mir.text}; }
        v.add(step(26, order[0], behind(c, order[0], 0.9), 2.6, "Voyager 2", L0, titles[0], texts[0]));
        v.add(step(38, order[1], behind(c, order[1], 1.2), 2.2, "Voyager 2", L0, titles[1], texts[1]));
        StoryEvent caEv = find(ev, "Größte Annäherung");
        v.add(step(50, ca, side(c, ca), 2.4, "Voyager 2", L0, "Größte Annäherung · " + minute(ca).replace(" UTC", "") + " UTC", caEv.text));
        v.add(step(62, shade.jdUtc + 0.01, back(c, shade.jdUtc + 0.01), 7.5, "Uranus", L0, "Im Schatten", shade.text));
        v.add(step(74, ca + 1.2, plane, 38, "Uranus", L0, "Weiter nach Neptun",
                "Uranus lenkt die Sonde um. Dreieinhalb Jahre später erreicht sie Neptun — bis heute war kein anderes Raumschiff bei Uranus."));
        v.add(step(82, ca + 1.3, plane, 40, "Uranus", L0, "", ""));
        out.add(new Tour("VOYAGER", "Voyager 2 · 1986", "Der einzige Besuch: der Vorbeiflug am 24. Januar 1986",
                Tour.Overlay.VOYAGER, Tour.TimeMode.CLOCK, v));

        // ---- Ein Uranusjahr
        List<StoryEvent> ss = new ArrayList<>();
        for (StoryEvent e : ev) if (e.kind == StoryEvent.Kind.SEASON && e.year() > 1940 && e.year() < 2032) ss.add(e);
        List<Tour.Step> y = new ArrayList<>();
        String LS = "moons=off;trails=0;magneto=0;space=0;scale=0;axis=1;cam=sun;time=lin";
        double[] sunView = {48, 14};
        double at = 0;
        for (int i = 0; i < ss.size(); i++) {
            StoryEvent e = ss.get(i);
            y.add(step(at, e.jdUtc, sunView, 7.2, "Uranus", LS, e.title, e.text));
            at += 20;
        }
        StoryEvent last = ss.get(ss.size() - 1);
        y.add(step(at - 20 + 10, last.jdUtc + 60, sunView, 7.2, "Uranus", LS, "",
                ""));
        out.add(new Tour("JAHR", "Ein Uranusjahr", "84 Erdjahre in 90 Sekunden: Sonnenwenden und Tagundnachtgleichen aus der Ephemeride",
                Tour.Overlay.SEASONS, Tour.TimeMode.CLOCK, y));

        // ---- Entdeckungen (Erzählzeit, die Uhr läuft mit 1 h/s)
        List<Tour.Step> d = new ArrayList<>();
        String LD = "moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease";
        Object[][] k = {
                {0.0, jdOf(1781, 3, 13, 22, 0), 26.0, 32.0, 24.0, "1781 · Herschel", "In Bath sieht William Herschel ein Scheibchen, das kein Stern ist. Er hält es für einen Kometen — es ist der erste mit dem Fernrohr gefundene Planet."},
                {9.0, jdOf(1787, 1, 11, 0, 0), 40.0, 30.0, 26.0, "1787 · Titania und Oberon", "Sechs Jahre später findet Herschel die zwei größten Monde. Sein Sohn John benennt sie nach Shakespeares Elfenkönigspaar."},
                {18.0, jdOf(1851, 10, 24, 0, 0), 56.0, 28.0, 24.0, "1851 · Ariel und Umbriel", "William Lassell, Brauer und Amateurastronom, sieht mit seinem 24-Zoll-Spiegel zwei weitere Monde."},
                {27.0, jdOf(1948, 2, 16, 0, 0), 70.0, 26.0, 20.0, "1948 · Miranda", "Gerard Kuiper findet am McDonald-Observatorium den fünften Mond — fast hundert Jahre nach Lassell."},
                {36.0, jdOf(1977, 3, 10, 20, 0), 86.0, 24.0, 9.0, "1977 · Die Ringe", "Ein Stern flackert vor und nach Uranus neunmal: Ringe! Entdeckt aus einem Flugzeug über dem Indischen Ozean, dem Kuiper Airborne Observatory."},
                {46.0, jdOf(1986, 1, 24, 18, 0), 104.0, 22.0, 11.0, "1986 · Voyager 2", "Der einzige Besuch: zehn neue Monde von Puck bis Cordelia, dazu λ und der Staubring ζ."},
                {56.0, jdOf(1997, 9, 6, 0, 0), 122.0, 34.0, 95.0, "1997 · Caliban und Sycorax", "Die ersten irregulären Monde: eingefangene Brocken auf weiten, rückläufigen Bahnen."},
                {65.0, jdOf(2003, 6, 1, 0, 0), 140.0, 30.0, 90.0, "1999–2003 · Hubble und Bodenteleskope", "Perdita in alten Voyager-Bildern, Cupid und Mab mit Hubble, Margaret als einziger rechtläufiger Irregulärer — und die Staubringe ν und μ."},
                {74.0, jdOf(2023, 11, 4, 0, 0), 156.0, 30.0, 70.0, "2023 · S/2023 U1", "Ein 8 km großer Brocken, gefunden mit dem Magellan-Teleskop in Chile."},
                {82.0, jdOf(2025, 2, 2, 0, 0), 170.0, 26.0, 18.0, "2025 · S/2025 U1", "Das James-Webb-Teleskop findet einen 10 km kleinen Mond zwischen Ophelia und Bianca — der 29. Uranusmond."},
                {92.0, jdOf(2025, 12, 31, 0, 0), 180.0, 26.0, 30.0, "", ""}};
        for (Object[] r : k)
            d.add(new Tour.Step((Double) r[0], (Double) r[1], (Double) r[2], (Double) r[3], (Double) r[4], "Uranus", LD, (String) r[5], (String) r[6]));
        out.add(new Tour("ENTDECKUNG", "Entdeckungen", "Von Herschel bis JWST: Monde und Ringe erscheinen im Jahr ihrer Entdeckung",
                Tour.Overlay.DISCOVERY, Tour.TimeMode.STORY, d));

        // ---- Rundflug (heute, freie Uhr)
        double[] gc = Ephemeris.radec(266.40510, -28.93617), gw = new double[3];
        Ephemeris.icrfToWorld(gc, gw);
        double[] lens = dirToYawPitch(new double[]{-gw[0], -gw[1], -gw[2]});
        List<Tour.Step> r = new ArrayList<>();
        double N = Double.NaN;
        r.add(new Tour.Step(0, N, 32, 24, 30, "Uranus", "moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;flight=0;rate=0.0416667", "Uranus heute",
                "Der Planet liegt auf der Seite: 97,8° Achsneigung. Die Mulde zeigt, wie seine Masse den Raum krümmt — Tiefe aus der Masse, Breite aus dem Radius."));
        r.add(new Tour.Step(12, N, 78, 30, 34, "Uranus", "space=1", "Gitterraum", "Dasselbe als räumliches Gitter, das zu den Massen hingezogen wird."));
        r.add(new Tour.Step(24, N, 110, 18, 2.6, "Miranda", "space=0", "Miranda", "Der Flickenteppich unter den Monden: Canyons, Terrassen, Steilwände."));
        r.add(new Tour.Step(38, N, 160, 8, 26, "Uranus", "magneto=1", "Magnetfeld", "59° gegen die Drehachse gekippt und zum Südpol versetzt; der Schweif windet sich als Korkenzieher."));
        r.add(new Tour.Step(52, N, lens[0], lens[1], 16, "Uranus", "magneto=0;lens=1", "Gravitationslinse", "Hinter Uranus das Zentrum der Milchstraße, vom Planeten zu einem Ring verbogen — stark überhöht."));
        r.add(new Tour.Step(64, N, lens[0], lens[1], 16, "Uranus", "flight=1", "Im ε-Ring", "Durch den hellsten Ring, zwischen den Hirten Cordelia und Ophelia."));
        r.add(new Tour.Step(84, N, 32, 24, 30, "Uranus", "flight=0;magneto=1", "Und jetzt du", "Ziehen dreht, das Mausrad zoomt, ein Klick auf einen Mond fliegt hin."));
        r.add(new Tour.Step(92, N, 40, 24, 30, "Uranus", "", "", ""));
        out.add(new Tour("RUNDFLUG", "Rundflug", "Einführung: was die App zeigt", Tour.Overlay.NONE, Tour.TimeMode.STORY, r));
        return out;
    }

    /** Blick von hinten-oben auf die Sonde in Flugrichtung, damit Uranus vor ihr liegt. */
    static double[] behind(Craft c, double jdUtc, double up) {
        double t = jdUtc + TimeScale.tdbMinusUtcSeconds(jdUtc) / 86_400.0;
        double[] a = new double[3], b = new double[3];
        c.position(t - 0.002, a); c.position(t + 0.002, b);
        double[] v = Ephemeris.normalize(new double[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]});
        double[] h = Ephemeris.normalize(Ephemeris.cross(a, b));
        return dirToYawPitch(new double[]{-v[0] + up * h[0], -v[1] + up * h[1] + 0.2, -v[2] + up * h[2]});
    }

    /** Seitenblick auf die Sonde an der größten Annäherung, Uranus im Hintergrund. */
    static double[] side(Craft c, double jdUtc) {
        double t = jdUtc + TimeScale.tdbMinusUtcSeconds(jdUtc) / 86_400.0;
        double[] a = new double[3];
        c.position(t, a);
        double[] r = Ephemeris.normalize(a);
        double[] b = new double[3];
        c.position(t + 0.002, b);
        double[] h = Ephemeris.normalize(Ephemeris.cross(a, b));
        return dirToYawPitch(new double[]{r[0] + 0.8 * h[0], r[1] + 0.8 * h[1], r[2] + 0.8 * h[2]});
    }

    /** Von der Sonde zurück auf die Nachtseite: die Sichel im Gegenlicht. */
    static double[] back(Craft c, double jdUtc) {
        double t = jdUtc + TimeScale.tdbMinusUtcSeconds(jdUtc) / 86_400.0;
        double[] a = new double[3];
        c.position(t, a);
        return dirToYawPitch(Ephemeris.normalize(a));
    }

    // ================================================================== Ausgabe

    static void writeEvents(List<StoryEvent> ev) throws Exception {
        StringBuilder s = new StringBuilder(UraGen.sqlHead("06 - Quellen und Ereignisse der Geschichten",
                "Erzeugt von tools/UraStoryGen: Entdeckungen, Vorbeiflug und Jahreszeiten (berechnet). Nicht von Hand ändern."));
        String[][] src = {
                {"HZ_VOYAGER2", "JPL Horizons: Voyager 2 (-32) relativ zu Uranus", "https://ssd.jpl.nasa.gov/api/horizons.api", "Voyager_2_ST+refit2022_m",
                        UraGen.sha(Q.resolve("voyager2_horizons.csv")), "Bis 1989 Missionsentwurf aus Kegelschnitten (grobe Genauigkeit); 145 Vektoren 21.-27.01.1986"},
                {"CALC", "Berechnet in der App (Ephemeride, Hyperbel, Mondbahnen)", null, null, null, "tools/UraStoryGen: Jahreszeiten 1900-2100, Ereignisse des Vorbeiflugs"},
                {"HIST", "Entdeckungsgeschichte (Standardliteratur, Wikipedia: Moons of Uranus, Rings of Uranus)", "https://en.wikipedia.org/wiki/Moons_of_Uranus", null, null,
                        "Tage nur, wo sie überliefert sind; sonst das Jahr"}};
        for (String[] r : src)
            s.append("BEGIN\n  ura_api.put_source(p_code => ").append(UraGen.str(r[0])).append(", p_title => ").append(UraGen.str(r[1]))
                    .append(",\n    p_url => ").append(UraGen.str(r[2])).append(", p_ephemeris => ").append(UraGen.str(r[3]))
                    .append(", p_retrieved => DATE '2026-09-23', p_sha256 => ").append(UraGen.str(r[4]))
                    .append(",\n    p_note => ").append(UraGen.str(r[5])).append(");\nEND;\n/\n\n");
        StringBuilder res = new StringBuilder("jd_utc;kind;precision;title;text;body;ring\n");
        s.append("BEGIN\n");
        for (StoryEvent e : ev) {
            String src2 = e.kind == StoryEvent.Kind.DISCOVERY ? "HIST" : "CALC";
            s.append("  ura_api.put_event(").append(UraGen.lit(UraGen.round(e.jdUtc, 6))).append(", ").append(UraGen.str(e.kind.name()))
                    .append(", ").append(UraGen.str(e.precision.name())).append(", ").append(UraGen.str(e.title)).append(",\n    ")
                    .append(UraGen.str(e.text)).append(", ").append(UraGen.str(e.body)).append(", ").append(UraGen.str(e.ring))
                    .append(", ").append(UraGen.str(src2)).append(");\n");
            res.append(String.join(";", UraGen.lit(UraGen.round(e.jdUtc, 6)), e.kind.name(), e.precision.name(), UranusSystem.esc(e.title),
                    UranusSystem.esc(e.text), e.body == null ? "" : e.body, e.ring == null ? "" : e.ring)).append("\n");
        }
        s.append("END;\n/\n\nCOMMIT;\n");
        UraGen.write(Paths.get("db", "06_events.sql"), s.toString());
        UraGen.write(Paths.get("src", "com", "dan", "uranus", "model", "eingebaut_ereignisse.csv"), res.toString());
        report.append(ev.size()).append(" Ereignisse\n");
    }

    static void writeTours(List<Tour> tours) throws Exception {
        StringBuilder s = new StringBuilder(UraGen.sqlHead("08 - Kamerafahrten",
                "Erzeugt von tools/UraStoryGen. Blickrichtungen zur Sonde aus der Hyperbel berechnet."));
        StringBuilder res = new StringBuilder("# T;code;title;overlay;mode;description | S;code;at_s;jd_utc;yaw;pitch;dist;target;layers;title;caption\n");
        res.append("typ;code;a;b;c;d;e;f;g;h;i\n");
        int sort = 1;
        for (Tour t : tours) {
            s.append("BEGIN\n  ura_api.put_tour(").append(UraGen.str(t.code)).append(", ").append(UraGen.str(t.title)).append(", ")
                    .append(UraGen.str(t.overlay.name())).append(", ").append(UraGen.str(t.timeMode.name())).append(", ")
                    .append(sort++).append(",\n    ").append(UraGen.str(t.description)).append(");\n");
            res.append(String.join(";", "T", t.code, UranusSystem.esc(t.title), t.overlay.name(), t.timeMode.name(), UranusSystem.esc(t.description))).append("\n");
            int seq = 1;
            for (Tour.Step st : t.steps()) {
                s.append("  ura_api.put_tour_step(").append(UraGen.str(t.code)).append(", ").append(seq++).append(", ").append(UraGen.lit(st.atS))
                        .append(", ").append(Double.isNaN(st.jdUtc) ? "NULL" : UraGen.lit(UraGen.round(st.jdUtc, 6))).append(", ")
                        .append(UraGen.lit(st.yawDeg)).append(", ").append(UraGen.lit(st.pitchDeg)).append(", ").append(UraGen.lit(st.dist)).append(", ")
                        .append(UraGen.str(st.target)).append(",\n    ").append(UraGen.str(st.layers)).append(", ")
                        .append(UraGen.str(st.title.isEmpty() ? null : st.title)).append(", ").append(UraGen.str(st.caption.isEmpty() ? null : st.caption)).append(");\n");
                res.append(String.join(";", "S", t.code, UraGen.lit(st.atS), Double.isNaN(st.jdUtc) ? "" : UraGen.lit(UraGen.round(st.jdUtc, 6)),
                        UraGen.lit(st.yawDeg), UraGen.lit(st.pitchDeg), UraGen.lit(st.dist), st.target, UranusSystem.esc(st.layers),
                        UranusSystem.esc(st.title), UranusSystem.esc(st.caption))).append("\n");
            }
            s.append("END;\n/\n\n");
            report.append(String.format(Locale.GERMAN, "Fahrt %-10s %2d Stationen, %.0f s%n", t.code, t.steps().size(), t.duration()));
        }
        s.append("COMMIT;\n");
        UraGen.write(Paths.get("db", "08_tours.sql"), s.toString());
        UraGen.write(Paths.get("src", "com", "dan", "uranus", "model", "eingebaut_touren.csv"), res.toString());
    }

    @SuppressWarnings("unused")
    private static final Object KEEP = new Object[]{Instant.EPOCH};
}
