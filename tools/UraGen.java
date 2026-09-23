import com.dan.uranus.model.BodyGroup;
import com.dan.uranus.model.OrbitElements;
import com.dan.uranus.model.UranusSystem;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Erzeugt aus den gesicherten Quellen in db/quellen alles, was Bahnen und Ringe braucht:
 * <ul>
 *   <li>den Angleich an Horizons (Phase und Periode je Mond, Präzession geprüft),</li>
 *   <li>db/03_bodies.sql, db/04_orbits.sql, db/05_rings.sql für die Datenbank,</li>
 *   <li>eingebaut_bahnen.csv und eingebaut_ringe.csv für den eingebauten Datensatz,</li>
 *   <li>db/quellen/angleich.txt als Bericht.</li>
 * </ul>
 * Kein Wert wird von Hand übertragen: DB und App bekommen dieselben Zahlen aus derselben Rechnung.
 * Aufruf aus dem Projektordner: java -cp build/classes;build/tools UraGen
 */
public class UraGen {

    static final Path Q = Paths.get("db", "quellen");

    /** Eine Zeile der JPL-Tabelle. */
    static final class JplRow {
        int code; String ephemeris, frame, epochText; double epoch;
        double a, e, argp, m, i, node, p, papsis, pnode, ra, dec;
        OrbitElements orbit() {
            OrbitElements.Frame f = frame.equals("equatorial") ? OrbitElements.Frame.EQUATORIAL
                    : frame.equals("Laplace") ? OrbitElements.Frame.LAPLACE : OrbitElements.Frame.ECLIPTIC;
            return new OrbitElements(f, epoch, a, e, i, node, argp, m, p, papsis, pnode, ra, dec);
        }
    }

    /** Ergebnis des Angleichs. */
    static final class Fit {
        OrbitElements orbit; boolean precession; double max, mean; int samples; double dM, dn;
    }

    static final Map<Integer, List<double[]>> HZ = new HashMap<>();

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        List<JplRow> jpl = readJpl();
        readHorizons();
        Map<Integer, UranusSystem.Facts> facts = new LinkedHashMap<>();
        for (UranusSystem.Facts f : UranusSystem.facts()) facts.put(f.naifId, f);

        StringBuilder report = new StringBuilder();
        report.append("Angleich der JPL-Mittelelemente an Horizons-Positionen\n");
        report.append("Quelle Form: db/quellen/jpl_uranus_sat_elem.csv, Referenz: db/quellen/horizons_referenz.csv\n");
        report.append("Abweichung = Winkel zwischen gerechneter und Horizons-Richtung vom Uranusmittelpunkt aus,\n");
        report.append("über alle Stützstellen 1975–2030. Angeglichen werden nur mittlere Anomalie und Periode.\n\n");
        report.append(String.format("%-10s %-7s %9s %9s | %9s %9s  %-12s %12s %14s%n",
                "Mond", "Ephem.", "JPL max", "JPL mittel", "neu max", "neu mittel", "Präzession", "ΔM0 [°]", "P neu [d]"));

        StringBuilder orbits = new StringBuilder(sqlHead("04 - Bahnen: JPL-Mittelelemente wie veröffentlicht und an Horizons angeglichen",
                "Erzeugt von tools/UraGen aus db/quellen. Nicht von Hand ändern — neu erzeugen."));
        StringBuilder res = new StringBuilder("name;naif;quality;source;frame;epoch_jd;a_km;ecc;incl_deg;node_deg;argp_deg;mean_anom_deg;"
                + "period_days;apsis_period_yr;node_period_yr;pole_ra_deg;pole_dec_deg;fit_max_deg;fit_mean_deg\n");

        for (UranusSystem.Facts f : facts.values()) {
            List<JplRow> rows = new ArrayList<>();
            for (JplRow r : jpl) if (r.code == f.naifId) rows.add(r);
            if (rows.isEmpty()) throw new IllegalStateException("keine JPL-Zeile für " + f.name);
            JplRow base = rows.get(0);
            for (JplRow r : rows) if (r.epoch > base.epoch) base = r;
            List<double[]> ref = HZ.get(f.naifId);
            Fit fit = ref == null ? null : fit(base.orbit(), ref);

            for (JplRow r : rows) {
                double[] st = ref == null ? null : stats(r.orbit(), ref);
                boolean current = fit == null && r == base;
                orbits.append(putOrbit(f.name, "JPL_" + r.ephemeris, r.ephemeris, r.orbit(), "MEAN", current,
                        st == null ? Double.NaN : st[0], st == null ? Double.NaN : st[1],
                        "wie veröffentlicht (Epoche " + r.epochText + ")"
                                + (ref == null ? "; kein Horizons-Abgleich möglich" : "")));
                if (current) res.append(resRow(f, "MEAN", "JPL " + r.ephemeris + " · mittlere Elemente", r.orbit(), Double.NaN, Double.NaN));
                if (r == base) {
                    report.append(String.format("%-10s %-7s %9s %9s | ", f.name, r.ephemeris,
                            st == null ? "–" : String.format("%.2f", st[0]), st == null ? "–" : String.format("%.2f", st[1])));
                    if (fit == null) report.append("kein Horizons-Abgleich (keine Referenz)\n");
                    else report.append(String.format("%9.2f %9.2f  %-12s %12.3f %14.7f%n", fit.max, fit.mean,
                            fit.precession ? "wie JPL" : "ohne", fit.dM, fit.orbit.periodDays));
                }
            }
            if (fit != null) {
                String note = "Form JPL " + base.ephemeris + "; mittlere Anomalie und Periode an " + fit.samples
                        + " Horizons-Positionen 1975-2030 angeglichen" + (fit.precession ? "" : "; Präzession verworfen (passt ohne besser)");
                orbits.append(putOrbit(f.name, "HZ_FIT", base.ephemeris, fit.orbit, "FIT", true, fit.max, fit.mean, note));
                res.append(resRow(f, "FIT", "JPL " + base.ephemeris + " · an Horizons angeglichen", fit.orbit, fit.max, fit.mean));
            }
        }
        orbits.append("COMMIT;\n");

        write(Paths.get("db", "04_orbits.sql"), orbits.toString());
        write(Paths.get("src", "com", "dan", "uranus", "model", "eingebaut_bahnen.csv"), res.toString());
        write(Q.resolve("angleich.txt"), report.toString());
        writeBodies(facts);
        writeRings();
        System.out.print(report);
        System.out.println();
        System.out.println("geschrieben: db/03_bodies.sql, db/04_orbits.sql, db/05_rings.sql, eingebaut_bahnen.csv, eingebaut_ringe.csv, db/quellen/angleich.txt");
    }

    // ------------------------------------------------------------------ Quellen lesen

    static List<String[]> csv(Path p) throws IOException {
        List<String[]> out = new ArrayList<>();
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        for (int k = 1; k < lines.size(); k++) if (!lines.get(k).isEmpty()) out.add(lines.get(k).split(";", -1));
        return out;
    }

    static double num(String s) {
        s = s.trim();
        if (s.isEmpty() || s.equals("-")) return 0;
        return Double.parseDouble(s);
    }

    /** "2000-01-01.5" → JD. */
    static double epochJd(String s) {
        String[] p = s.split("-");
        int y = Integer.parseInt(p[0]), mo = Integer.parseInt(p[1]);
        double d = Double.parseDouble(p[2]);
        int a = (14 - mo) / 12, yy = y + 4800 - a, mm = mo + 12 * a - 3;
        int day = (int) Math.floor(d);
        long jdn = day + (153L * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
        return jdn - 0.5 + (d - day);
    }

    static List<JplRow> readJpl() throws IOException {
        List<JplRow> out = new ArrayList<>();
        for (String[] c : csv(Q.resolve("jpl_uranus_sat_elem.csv"))) {
            JplRow r = new JplRow();
            r.code = Integer.parseInt(c[3]); r.ephemeris = c[4]; r.frame = c[5]; r.epochText = c[6]; r.epoch = epochJd(c[6]);
            r.a = num(c[7]); r.e = num(c[8]); r.argp = num(c[9]); r.m = num(c[10]); r.i = num(c[11]); r.node = num(c[12]);
            r.p = num(c[13]); r.papsis = num(c[14]); r.pnode = num(c[15]);
            r.ra = c[16].isEmpty() ? Double.NaN : num(c[16]); r.dec = c[17].isEmpty() ? Double.NaN : num(c[17]);
            out.add(r);
        }
        return out;
    }

    static void readHorizons() throws IOException {
        for (String[] c : csv(Q.resolve("horizons_referenz.csv")))
        {
            // Horizons liefert ICRF; verglichen wird in Weltkoordinaten wie in der App
            double[] w = new double[3];
            com.dan.uranus.physics.Ephemeris.icrfToWorld(new double[]{num(c[2]), num(c[3]), num(c[4])}, w);
            HZ.computeIfAbsent(Integer.parseInt(c[0]), k -> new ArrayList<>()).add(new double[]{num(c[1]), w[0], w[1], w[2]});
        }
    }

    // ------------------------------------------------------------------ Angleich

    static double angle(double[] a, double[] b) {
        double la = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]), lb = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
        double c = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (la * lb);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
    }

    /** Abweichung einer Bahn von Horizons: {max, mittel} in Grad. */
    static double[] stats(OrbitElements o, List<double[]> ref) {
        double[] p = new double[3];
        double max = 0, sum = 0;
        for (double[] s : ref) {
            o.position(s[0], p);
            double a = angle(p, new double[]{s[1], s[2], s[3]});
            max = Math.max(max, a); sum += a;
        }
        return new double[]{max, sum / ref.size()};
    }

    /** Mittlere Anomalie (Verschiebung in Grad), bei der die Bahn die Horizons-Richtung am besten trifft. */
    static double bestShift(OrbitElements o, double[] s) {
        double[] target = {s[1], s[2], s[3]}, p = new double[3];
        double best = 0, bestErr = 1e9;
        for (int k = 0; k < 360; k++) {
            o.withPhase(o.meanAnomalyDeg + k, o.periodDays, true).position(s[0], p);
            double a = angle(p, target);
            if (a < bestErr) { bestErr = a; best = k; }
        }
        double lo = best - 1, hi = best + 1;
        for (int it = 0; it < 50; it++) {
            double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
            o.withPhase(o.meanAnomalyDeg + m1, o.periodDays, true).position(s[0], p);
            double a1 = angle(p, target);
            o.withPhase(o.meanAnomalyDeg + m2, o.periodDays, true).position(s[0], p);
            double a2 = angle(p, target);
            if (a1 < a2) hi = m2; else lo = m1;
        }
        return (lo + hi) / 2;
    }

    static Fit fit(OrbitElements base, List<double[]> ref) {
        Fit best = null;
        for (boolean prec : new boolean[]{true, false}) {
            OrbitElements b = base.withPhase(base.meanAnomalyDeg, base.periodDays, prec);
            if (!prec && b.apsisPeriodYr == base.apsisPeriodYr && b.nodePeriodYr == base.nodePeriodYr) continue;
            // Phase an jeder Stützstelle
            int n = ref.size();
            double[] t = new double[n], ph = new double[n];
            double span = 0;
            for (int k = 0; k < n; k++) {
                t[k] = ref.get(k)[0] - b.epochJd;
                ph[k] = bestShift(b, ref.get(k));
                span = Math.max(span, Math.abs(t[k]));
            }
            // Periodogramm: Zusatzrate dn, bei der die Phasen am besten übereinstimmen
            double n0 = b.meanMotionDegPerDay(), step = 5.0 / span, range = 0.006 * n0;
            double bestAbs = -1, dn = 0, dM = 0;
            for (long k = -(long) (range / step); k * step <= range; k++) {
                double d = k * step, sx = 0, sy = 0;
                for (int j = 0; j < n; j++) {
                    double a = Math.toRadians(ph[j] - d * t[j]);
                    sx += Math.cos(a); sy += Math.sin(a);
                }
                double abs = Math.hypot(sx, sy);
                if (abs > bestAbs) { bestAbs = abs; dn = d; dM = Math.toDegrees(Math.atan2(sy, sx)); }
            }
            // Feinschliff: Ausgleichsgerade durch die Restphasen
            for (int it = 0; it < 3; it++) {
                double sx = 0, sy = 0, sxx = 0, sxy = 0;
                for (int j = 0; j < n; j++) {
                    double r = ph[j] - (dM + dn * t[j]);
                    r = ((r + 180) % 360 + 360) % 360 - 180;
                    sx += t[j]; sy += r; sxx += t[j] * t[j]; sxy += t[j] * r;
                }
                double bb = (n * sxy - sx * sy) / (n * sxx - sx * sx), aa = (sy - bb * sx) / n;
                dn += bb; dM += aa;
            }
            double m0 = ((b.meanAnomalyDeg + dM) % 360 + 360) % 360;
            // neue Periode so, dass die mittlere Länge um dn schneller läuft
            double p = 360.0 / (n0 + dn);
            Fit f = new Fit();
            double m0r = round(m0, 4);
            if (m0r >= 360) m0r -= 360;                                  // CHECK mean_anom_deg < 360
            f.orbit = b.withPhase(m0r, round(p, 8), prec);
            double[] st = stats(f.orbit, ref);
            f.max = st[0]; f.mean = st[1]; f.precession = prec; f.samples = n; f.dM = dM; f.dn = dn;
            if (best == null || f.mean < best.mean) best = f;
        }
        return best;
    }

    static double round(double v, int d) { double k = Math.pow(10, d); return Math.round(v * k) / k; }

    // ------------------------------------------------------------------ Ausgabe

    static String sqlHead(String title, String note) {
        return "-- =====================================================================\n"
                + "-- Uranus / Schema DEMO\n"
                + "-- " + title + "\n"
                + "-- " + note + "\n"
                + "-- =====================================================================\n\n";
    }

    static String lit(double v) {
        if (Double.isNaN(v)) return "NULL";
        String s = String.format(Locale.ROOT, "%.10f", v).replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        if (s.equals("-0")) s = "0";
        return s;
    }

    static String str(String s) { return s == null ? "NULL" : "'" + s.replace("'", "''") + "'"; }

    static String putOrbit(String body, String source, String ephem, OrbitElements o, String quality, boolean current,
                           double max, double mean, String note) {
        return "BEGIN\n  ura_api.put_orbit(p_body => " + str(body) + ", p_source => " + str(source)
                + ", p_ephemeris => " + str(ephem) + ",\n"
                + "    p_frame => " + str(o.frame.name()) + ", p_epoch_jd => " + lit(o.epochJd)
                + ", p_a_km => " + lit(o.aKm) + ", p_ecc => " + lit(o.e) + ",\n"
                + "    p_incl_deg => " + lit(o.iDeg) + ", p_node_deg => " + lit(o.nodeDeg)
                + ", p_argp_deg => " + lit(o.argPeriDeg) + ", p_mean_anom_deg => " + lit(o.meanAnomalyDeg) + ",\n"
                + "    p_period_days => " + lit(o.periodDays)
                + ", p_apsis_period_yr => " + (o.apsisPeriodYr > 0 ? lit(o.apsisPeriodYr) : "NULL")
                + ", p_node_period_yr => " + (o.nodePeriodYr > 0 ? lit(o.nodePeriodYr) : "NULL") + ",\n"
                + "    p_pole_ra_deg => " + lit(o.poleRaDeg) + ", p_pole_dec_deg => " + lit(o.poleDecDeg)
                + ", p_quality => " + str(quality) + ", p_current => " + str(current ? "J" : "N") + ",\n"
                + "    p_fit_max_deg => " + lit(Double.isNaN(max) ? max : round(max, 3))
                + ", p_fit_mean_deg => " + lit(Double.isNaN(mean) ? mean : round(mean, 3)) + ",\n"
                + "    p_note => " + str(note) + ");\nEND;\n/\n\n";
    }

    static String resRow(UranusSystem.Facts f, String quality, String source, OrbitElements o, double max, double mean) {
        return String.join(";", f.name, String.valueOf(f.naifId), quality, source, o.frame.name(), lit(o.epochJd),
                lit(o.aKm), lit(o.e), lit(o.iDeg), lit(o.nodeDeg), lit(o.argPeriDeg), lit(o.meanAnomalyDeg),
                lit(o.periodDays), o.apsisPeriodYr > 0 ? lit(o.apsisPeriodYr) : "", o.nodePeriodYr > 0 ? lit(o.nodePeriodYr) : "",
                Double.isNaN(o.poleRaDeg) ? "" : lit(o.poleRaDeg), Double.isNaN(o.poleDecDeg) ? "" : lit(o.poleDecDeg),
                Double.isNaN(max) ? "" : lit(round(max, 3)), Double.isNaN(mean) ? "" : lit(round(mean, 3))) + "\n";
    }

    static String sha(Path p) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static void writeBodies(Map<Integer, UranusSystem.Facts> facts) throws Exception {
        StringBuilder s = new StringBuilder(sqlHead("03 - Quellen und Körper: Uranus und seine 29 Monde",
                "Erzeugt von tools/UraGen. Fakten aus UranusSystem.facts(), Quellen mit SHA-256 der gesicherten Datei."));
        String jplSha = sha(Q.resolve("jpl_uranus_sat_elem.csv")), hzSha = sha(Q.resolve("horizons_referenz.csv")),
                wikiSha = sha(Q.resolve("wikipedia_uranus_ringe.csv"));
        String[][] src = {
                {"JPL_URA182", "JPL Planetary Satellite Mean Elements, Ephemeride URA182", "https://ssd.jpl.nasa.gov/sats/elem/", "URA182", jplSha,
                        "Jacobson & Park 2025, AJ 169:65. Mittlere Elemente zum Äquator, Epoche 2000-01-01.5 TDB"},
                {"JPL_URA184", "JPL Planetary Satellite Mean Elements, Ephemeride URA184", "https://ssd.jpl.nasa.gov/sats/elem/", "URA184", jplSha,
                        "Brozović 2025 (JWST-Update). Laplace-Ebene, Epoche 2025-01-01.0 TDB; passt zu Horizons erst 0,01 Tage später"},
                {"JPL_URA117", "JPL Planetary Satellite Mean Elements, Ephemeride URA117", "https://ssd.jpl.nasa.gov/sats/elem/", "URA117", jplSha,
                        "Sheppard et al. 2024. Ekliptik J2000, Epoche 2020-01-01.0 TDB; mittlere Anomalie taugt nicht als Ort"},
                {"HZ_FIT", "Angleich an JPL Horizons (ura184_merged)", "https://ssd.jpl.nasa.gov/api/horizons.api", "HORIZONS", hzSha,
                        "Form aus JPL, mittlere Anomalie und Periode an Horizons-Vektoren 1975-2030 angeglichen (tools/UraGen)"},
                {"WIKI_RINGS", "Wikipedia: Rings of Uranus, Ringtabelle (Revision 1372354849)", "https://en.wikipedia.org/wiki/Rings_of_Uranus", null, wikiSha,
                        "Radien Esposito 2002, Breiten Karkoschka 2001/de Pater 2006, e und i Stone 1986/French 1988"},
                {"IAU_2015", "IAU WGCCRE 2015: Pol und Rotation des Uranus", "https://doi.org/10.1007/s10569-017-9805-5", null, null,
                        "Nordpol RA 257,311°, Dec -15,175°; W = 203,81° - 501,1600928°/Tag"}};
        for (String[] r : src) {
            s.append("BEGIN\n  ura_api.put_source(p_code => ").append(str(r[0])).append(", p_title => ").append(str(r[1]))
                    .append(",\n    p_url => ").append(str(r[2])).append(", p_ephemeris => ").append(str(r[3]))
                    .append(", p_retrieved => DATE '2026-09-23', p_sha256 => ").append(str(r[4]))
                    .append(",\n    p_note => ").append(str(r[5])).append(");\nEND;\n/\n\n");
        }
        s.append("BEGIN\n  ura_api.put_body(p_name => 'Uranus', p_naif => 799, p_group => 'PLANET', p_parent => NULL,\n")
                .append("    p_radius_km => ").append(lit(UranusSystem.URANUS_RADIUS_KM)).append(", p_gm_km3s2 => ").append(lit(UranusSystem.URANUS_GM))
                .append(", p_albedo => 0.3, p_year => 1781,\n")
                .append("    p_by => 'William Herschel', p_namesake => 'Uranos, griechischer Himmelsgott',\n")
                .append("    p_pole_ra => 257.311, p_pole_dec => -15.175, p_w0 => 203.81, p_wdot => -501.1600928, p_sort => 0);\nEND;\n/\n\n");
        int k = 1;
        for (UranusSystem.Facts f : facts.values()) {
            s.append("BEGIN\n  ura_api.put_body(p_name => ").append(str(f.name)).append(", p_naif => ").append(f.naifId)
                    .append(", p_group => ").append(str(f.group.name())).append(", p_parent => 'Uranus',\n")
                    .append("    p_radius_km => ").append(lit(f.radiusKm)).append(", p_gm_km3s2 => ")
                    .append(f.massKg > 0 ? lit(round(f.massKg * UranusSystem.G_KM, 4)) : "NULL")
                    .append(", p_albedo => ").append(lit(f.albedo)).append(", p_year => ").append(f.year).append(",\n")
                    .append("    p_by => ").append(str(f.by)).append(", p_namesake => ").append(str(f.namesake))
                    .append(", p_sort => ").append(k++)
                    .append(f.massKg > 0 ? "" : ", p_density => " + lit(UranusSystem.DENSITY_ASSUMED / 1000))   // g/cm³, Masse geschätzt
                    .append(");\nEND;\n/\n\n");
        }
        s.append("COMMIT;\n");
        write(Paths.get("db", "03_bodies.sql"), s.toString());
    }

    /**
     * Entdeckung der Ringe (Wikipedia „Rings of Uranus“): neun schmale Ringe 1977 bei einer
     * Sternbedeckung vom Kuiper Airborne Observatory aus, λ und ζ (1986U2R) von Voyager 2, ν und μ
     * auf Hubble-Aufnahmen von 2003 (veröffentlicht 2005).
     */
    static final Map<String, Integer> RING_YEAR = new HashMap<>();
    static final Map<String, String> RING_BY = new HashMap<>();
    static {
        for (String n : new String[]{"6", "5", "4", "α", "β", "η", "γ", "δ", "ε"}) {
            RING_YEAR.put(n, 1977); RING_BY.put(n, "Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)");
        }
        RING_YEAR.put("λ", 1986); RING_BY.put("λ", "Voyager 2");
        RING_YEAR.put("ζ", 1986); RING_BY.put("ζ", "Voyager 2 (als 1986U2R)");
        RING_YEAR.put("ν", 2003); RING_BY.put("ν", "Showalter, Lissauer (Hubble)");
        RING_YEAR.put("μ", 2003); RING_BY.put("μ", "Showalter, Lissauer (Hubble)");
    }

    /** Die 13 Ringe der App aus der Wikipedia-Tabelle. */
    static void writeRings() throws IOException {
        String[] names = {"ζ", "6", "5", "4", "α", "β", "η", "γ", "δ", "λ", "ε", "ν", "μ"};
        Map<String, String[]> tab = new HashMap<>();
        for (String[] c : csv(Q.resolve("wikipedia_uranus_ringe.csv"))) tab.put(c[0], c);
        StringBuilder sql = new StringBuilder(sqlHead("05 - Ringe aus der gesicherten Wikipedia-Tabelle",
                "Erzeugt von tools/UraGen. Bereiche 'a–b' werden zu Minimum und Maximum, '?' zu NULL."));
        StringBuilder res = new StringBuilder("name;a_km;width_min_km;width_max_km;ecc;incl_deg;tau_min;tau_max;dusty;discovered_year;discoverer\n");
        for (String n : names) {
            int year = RING_YEAR.get(n);
            String by = RING_BY.get(n);
            String[] c = tab.get(n);
            if (c == null) throw new IllegalStateException("Ring " + n + " fehlt in der Quelle");
            double[] rad = range(c[1]), wid = range(c[2]), tau = range(c[4]);
            double ecc = single(c[6]), inc = single(c[7]);
            boolean dusty = n.equals("ζ") || n.equals("ν") || n.equals("μ");
            // Staubringe: Bereich → Mitte, bei ν und μ die hellste Stelle aus der Anmerkung
            double a = rad[0] == rad[1] ? rad[0] : (rad[0] + rad[1]) / 2;
            String note = "";
            java.util.regex.Matcher pk = java.util.regex.Pattern.compile("peak brightness at ([0-9 ]+) km").matcher(c[8]);
            if (pk.find()) { a = Double.parseDouble(pk.group(1).replace(" ", "")); note = "Radius = hellste Stelle laut Anmerkung"; }
            else if (rad[0] != rad[1]) note = "Radius = Mitte des Bereichs " + c[1] + " km";
            double wMin = rad[0] != rad[1] ? wid[0] : wid[0], wMax = wid[1];
            sql.append("BEGIN\n  ura_api.put_ring(p_name => ").append(str(n)).append(", p_a_km => ").append(lit(a))
                    .append(", p_width_min_km => ").append(lit(wMin)).append(", p_width_max_km => ").append(lit(wMax)).append(",\n")
                    .append("    p_ecc => ").append(lit(ecc)).append(", p_incl_deg => ").append(lit(inc))
                    .append(", p_tau_min => ").append(lit(tau[0])).append(", p_tau_max => ").append(lit(tau[1]))
                    .append(", p_dusty => ").append(str(dusty ? "J" : "N")).append(",\n")
                    .append("    p_year => ").append(year).append(", p_by => ").append(str(by)).append(",\n")
                    .append("    p_source => 'WIKI_RINGS', p_note => ").append(str(note.isEmpty() ? null : note)).append(");\nEND;\n/\n\n");
            res.append(String.join(";", n, lit(a), lit(wMin), lit(wMax), Double.isNaN(ecc) ? "" : lit(ecc),
                    Double.isNaN(inc) ? "" : lit(inc), lit(tau[0]), lit(tau[1]), dusty ? "J" : "N", String.valueOf(year), by)).append("\n");
        }
        sql.append("COMMIT;\n");
        write(Paths.get("db", "05_rings.sql"), sql.toString());
        write(Paths.get("src", "com", "dan", "uranus", "model", "eingebaut_ringe.csv"), res.toString());
    }

    /** "1.6–2.2" → {1.6, 2.2}; "26 840–34 890" → {26840, 34890}; "~ 0.0001" → {1e-4, 1e-4}; "?" → NaN. */
    static double[] range(String s) {
        s = s.replace("~", "").replace("<", "").replace("?", "").replace(" ", "").trim();
        if (s.isEmpty()) return new double[]{Double.NaN, Double.NaN};
        String[] p = s.split("–");
        double a = Double.parseDouble(p[0]), b = p.length > 1 ? Double.parseDouble(p[1]) : a;
        return new double[]{a, b};
    }

    static double single(String s) {
        s = s.replace("?", "").trim();
        return s.isEmpty() ? Double.NaN : Double.parseDouble(s);
    }

    static void write(Path p, String s) throws IOException {
        Files.createDirectories(p.toAbsolutePath().getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8))) { w.print(s); }
    }

    @SuppressWarnings("unused")
    private static final BodyGroup KEEP = BodyGroup.MAJOR;
}
