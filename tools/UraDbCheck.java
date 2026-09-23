import com.dan.uranus.db.UranusDao;
import com.dan.uranus.db.UranusDb;
import com.dan.uranus.model.Body;
import com.dan.uranus.model.Craft;
import com.dan.uranus.model.OrbitElements;
import com.dan.uranus.model.OrbitQuality;
import com.dan.uranus.model.Ring;
import com.dan.uranus.model.Scenario;
import com.dan.uranus.model.StoryEvent;
import com.dan.uranus.model.Tour;
import com.dan.uranus.model.UranusSystem;
import com.dan.uranus.physics.Ephemeris;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Durchstich gegen das Schema DEMO: genau das, was die App tut, und die Regeln, die das Schema
 * schützen. Läuft ZWEIMAL hintereinander — eine Prüfung, die nur direkt nach dem Aufbau läuft,
 * prüft den Aufbau und nicht den Betrieb (Lehre aus FCurvedField).
 * <p>
 * Alles, was schreibt, läuft in einer Transaktion, die zurückgerollt wird.
 * Protokoll: db_check.log im Projektordner.
 */
public class UraDbCheck {

    static UraLog log;
    static final double[] DATES = {2446455.25, 2461306.5, 2462604.5};   // Voyager 1986, heute, Sonnenwende 2030 (TDB)

    public static void main(String[] args) {
        try (UraLog l = new UraLog("db_check.log", "Uranus - Durchstich gegen die Datenbank")) {
            log = l;
            Locale.setDefault(Locale.GERMANY);
            UranusDb db = UranusDb.fromProperties();
            if (db == null) { log.check("uranus-db.properties vorhanden", false, "Datei mit url, user, password fehlt"); return; }
            log.say("Verbindung  : " + db);
            UraGen.HZ.clear();
            UraGen.readHorizons();
            for (int pass = 1; pass <= 2; pass++) {
                log.say("");
                log.say("================================================= Lauf " + pass + " von 2");
                try (Connection c = db.open()) {
                    pass(c, db, pass);
                } catch (SQLException e) {
                    log.check("Verbindung", false, "ORA-" + e.getErrorCode() + " " + UraLog.oneLine(e.getMessage()));
                }
            }
            log.say("");
            log.say("Ergebnis: " + log.ok + " ok, " + log.bad + " Fehler");
        } catch (Throwable t) {
            // Auffangnetz: ein durchschlagender Fehler soll im Protokoll stehen, nicht nur im Fenster
            if (log != null) log.say("ABBRUCH: " + t);
            System.out.println("ABBRUCH: " + t);
        }
    }

    static void pass(Connection c, UranusDb db, int pass) throws Exception {
        log.say("--- 1. Bestand");
        log.check("Quellen (6 Bahnen/Ringe + 3 Geschichten)", one(c, "SELECT COUNT(*) FROM ura_source") == 9, "");
        log.check("Keine eigenen Szenarien übrig (von früheren Prüfläufen)", one(c, "SELECT COUNT(*) FROM ura_scenario WHERE code LIKE 'PRUEF\\_%' ESCAPE '\\'") == 0, "");
        log.check("Uranus und 29 Monde", one(c, "SELECT COUNT(*) FROM ura_body") == 30, "");
        long rows = one(c, "SELECT COUNT(*) FROM ura_orbit"), cur = one(c, "SELECT COUNT(*) FROM ura_orbit WHERE is_current = 'J'");
        log.check("58 Bahnen, 29 davon gültig", rows == 58 && cur == 29, rows + " / " + cur);
        long fit = one(c, "SELECT COUNT(*) FROM ura_orbit WHERE is_current = 'J' AND quality = 'FIT'");
        log.check("Gültig: 28 angeglichen, S/2025 U1 als JPL-Mittelwert", fit == 28
                && one(c, "SELECT COUNT(*) FROM ura_orbit o JOIN ura_body b ON b.body_id = o.body_id "
                + "WHERE b.name = 'S/2025 U1' AND o.is_current = 'J' AND o.quality = 'MEAN'") == 1, fit + " angeglichen");
        log.check("13 Ringe", one(c, "SELECT COUNT(*) FROM ura_ring") == 13, "");
        log.check("Keine ungültigen Uranus-Objekte", one(c, "SELECT COUNT(*) FROM user_objects WHERE status <> 'VALID' "
                + "AND (object_name LIKE 'URA\\_%' ESCAPE '\\' OR object_name = 'T_URA_VEC')") == 0, "");
        log.check("Griechische Ringnamen unverändert gespeichert", one(c, "SELECT COUNT(*) FROM ura_ring WHERE name IN ('ε', 'ζ', 'ν', 'μ')") == 4, "");
        log.check("Virtuelle Spalte mass_kg: Titania 3,4·10²¹ kg, Cupid ohne GM → NULL",
                one(c, "SELECT COUNT(*) FROM ura_body WHERE name = 'Titania' AND ABS(mass_kg / 3.40E21 - 1) < 1E-4") == 1
                        && one(c, "SELECT COUNT(*) FROM ura_body WHERE name = 'Cupid' AND mass_kg IS NULL") == 1, "");

        log.say("--- 2. Was die App lädt");
        UranusDao.Result r = UranusDao.loadOrBuiltIn(db);
        log.check("App-Weg: Datensatz kommt aus der Datenbank", r.fromDatabase, r.message);
        UranusSystem sys = r.system, in = UranusSystem.builtIn();
        int same = 0;
        String diff = "";
        for (Body b : in.bodies()) {
            Body d = sys.find(b.name);
            if (d != null && equal(b.orbit, d.orbit) && b.quality == d.quality && Math.abs(b.radiusKm - d.radiusKm) < 1e-9
                    && b.naifId == d.naifId && b.discoveredYear == d.discoveredYear) same++;
            else if (diff.isEmpty()) diff = b.name;
        }
        // nur mit echtem Datenbank-Satz aussagekräftig: sonst verglichen wir den eingebauten mit sich selbst
        log.check("Datenbank = eingebauter Satz (29 Monde, alle Bahnelemente)", r.fromDatabase && same == 29 && sys.bodies().size() == 29, same + " gleich" + (diff.isEmpty() ? "" : ", zuerst anders: " + diff));
        int ringSame = 0;
        for (int i = 0; i < Math.min(in.rings().size(), sys.rings().size()); i++) {
            Ring a = in.rings().get(i), b = sys.rings().get(i);
            if (a.name.equals(b.name) && a.aKm == b.aKm && a.widthMinKm == b.widthMinKm && a.widthMaxKm == b.widthMaxKm && a.ecc == b.ecc) ringSame++;
        }
        log.check("Ringe = eingebauter Satz, gleiche Reihenfolge", r.fromDatabase && ringSame == 13 && sys.rings().size() == 13, ringSame + " gleich");
        if (pass == 1) {
            // nur im ersten Lauf: ein Fehlversuch zwischen zwei gelungenen Anmeldungen sperrt nichts
            UranusDao.Result wrong = UranusDao.loadOrBuiltIn(new UranusDb(db.url, db.user, "falsch-" + System.nanoTime(), "Prüfung"));
            log.check("Falsches Kennwort: eingebauter Satz, Grund ORA-01017", !wrong.fromDatabase && wrong.system == in && wrong.message.contains("01017"), wrong.message);
        }

        log.say("--- 3. PL/SQL rechnet wie Java");
        double worst = 0;
        String who = "";
        int n = 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT t.p.x, t.p.y, t.p.z FROM (SELECT ura_api.position_at(?, ?) AS p FROM dual) t")) {
            double[] j = new double[3];
            for (Body b : sys.bodies())
                for (double jd : DATES) {
                    ps.setString(1, b.name);
                    ps.setDouble(2, jd);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        b.orbit.position(jd, j);
                        double d = Math.sqrt(sq(rs.getDouble(1) - j[0]) + sq(rs.getDouble(2) - j[1]) + sq(rs.getDouble(3) - j[2]));
                        if (d > worst) { worst = d; who = b.name; }
                        n++;
                    }
                }
        }
        log.check("position_at = OrbitElements.position (29 Monde × 3 Zeitpunkte)", n == 87 && worst < 0.01,
                String.format("%d Vergleiche, größter Abstand %.2e km (%s)", n, worst, who));
        try (PreparedStatement ps = c.prepareStatement("SELECT t.p.x, t.p.z FROM (SELECT ura_api.position_at('Cordelia', 2460676.5, 'JPL_URA184') AS p FROM dual) t")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                log.check("position_at mit Quelle: veröffentlichte URA184-Bahn abrufbar", Math.hypot(rs.getDouble(1), rs.getDouble(2)) > 45_000, "");
            }
        }

        log.say("--- 4. Gegen Horizons");
        double[] w = new double[3];
        String[] g = {"Haupt", "innere", "irreguläre"};
        for (Body b : sys.bodies()) {
            List<double[]> ref = UraGen.HZ.get(b.naifId);
            if (ref == null) continue;
            double[] st = UraGen.stats(b.orbit, ref);
            int k = b.group.ordinal();
            w[k] = Math.max(w[k], st[0]);
        }
        log.check("Bahnen aus der Datenbank folgen Horizons 1975–2030", r.fromDatabase && w[0] < 2.5 && w[1] < 6 && w[2] < 25,
                String.format("größte Abweichung %s %.2f°, %s %.2f°, %s %.2f°", g[0], w[0], g[1], w[1], g[2], w[2]));
        long stored = one(c, "SELECT COUNT(*) FROM ura_orbit WHERE is_current = 'J' AND fit_max_deg IS NOT NULL");
        log.check("Abweichung je Bahn in fit_max_deg hinterlegt", stored == 28, stored + " Bahnen");
        Map<String, OrbitElements> pub = new UranusDao(c).orbitsOf("JPL_URA184");
        log.check("Quellen nebeneinander abrufbar: 14 Bahnen aus URA184", pub.size() == 14, pub.size() + "");

        log.say("--- 5. Geschichten");
        stories(c, r, in);

        log.say("--- 6. Spielwiese");
        play(c, r, in, pass);

        log.say("--- 7. Regeln des Schemas (Transaktion wird zurückgerollt)");
        c.setAutoCommit(false);
        try {
            expect(c, "Zweite gültige Bahn für Ariel wird abgewiesen", "UPDATE ura_orbit SET is_current = 'J' WHERE body_id = "
                    + "(SELECT body_id FROM ura_body WHERE name = 'Ariel') AND source_id = (SELECT source_id FROM ura_source WHERE code = 'JPL_URA182')", 1);
            expect(c, "Exzentrizität 1,2 wird abgewiesen", "UPDATE ura_orbit SET ecc = 1.2 WHERE rownum = 1", 2290);
            expect(c, "Laplace-Bahn ohne Pol wird abgewiesen", "UPDATE ura_orbit SET pole_ra_deg = NULL WHERE frame = 'LAPLACE' AND rownum = 1", 2290);
            expect(c, "Ring mit größter Breite unter der kleinsten wird abgewiesen", "UPDATE ura_ring SET width_max_km = 1 WHERE name = 'ε'", 2290);
            expect(c, "Mond ohne Planet wird abgewiesen", "UPDATE ura_body SET parent_id = NULL WHERE name = 'Puck'", 2290);
            expect(c, "Unbekannter Körper: ORA-20002 aus position_at", "SELECT ura_api.position_at('Vulkan', 2461306.5) FROM dual", 20002);
            expect(c, "Unbekannte Quelle: ORA-20001 aus put_orbit", "BEGIN ura_api.put_orbit('Ariel', 'NIRGENDS', NULL, 'EQUATORIAL', 2451545, 1, 0, 0, 0, 0, 0, 1, "
                    + "NULL, NULL, NULL, NULL, 'MEAN', 'N', NULL, NULL, NULL); END;", 20001);
            // die gültige Bahn wechseln, wie ein neuer Datenstand es täte
            try (Statement st = c.createStatement()) {
                st.execute("BEGIN FOR r IN (SELECT o.ephemeris, o.frame, o.epoch_jd, o.a_km, o.ecc, o.incl_deg, o.node_deg, o.argp_deg, "
                        + "o.mean_anom_deg, o.period_days, o.apsis_period_yr, o.node_period_yr, o.pole_ra_deg, o.pole_dec_deg, o.quality, "
                        + "o.fit_max_deg, o.fit_mean_deg, o.note FROM ura_orbit o JOIN ura_body b ON b.body_id = o.body_id "
                        + "JOIN ura_source s ON s.source_id = o.source_id WHERE b.name = 'Ariel' AND s.code = 'JPL_URA182') LOOP "
                        + "ura_api.put_orbit('Ariel', 'JPL_URA182', r.ephemeris, r.frame, r.epoch_jd, r.a_km, r.ecc, r.incl_deg, r.node_deg, "
                        + "r.argp_deg, r.mean_anom_deg, r.period_days, r.apsis_period_yr, r.node_period_yr, r.pole_ra_deg, r.pole_dec_deg, "
                        + "r.quality, 'J', r.fit_max_deg, r.fit_mean_deg, r.note); END LOOP; END;");
            }
            long ariel = one(c, "SELECT COUNT(*) FROM ura_orbit o JOIN ura_body b ON b.body_id = o.body_id WHERE b.name = 'Ariel' AND o.is_current = 'J'");
            long ariMean = one(c, "SELECT COUNT(*) FROM ura_orbit o JOIN ura_body b ON b.body_id = o.body_id WHERE b.name = 'Ariel' AND o.is_current = 'J' AND o.quality = 'MEAN'");
            log.check("put_orbit wechselt die gültige Bahn: weiterhin genau eine", ariel == 1 && ariMean == 1, "");
            expect(c, "Sonde mit e = 0,9 (Ellipse statt Vorbeiflug) wird abgewiesen", "UPDATE ura_craft SET ecc = 0.9", 2290);
            expect(c, "Szenario-Körper mit unbekannter Rolle wird abgewiesen", "UPDATE ura_scenario_body SET role = 'GEIST' WHERE rownum = 1", 2290);
            expect(c, "Negative Masse wird abgewiesen", "UPDATE ura_scenario_body SET mass_kg = -1 WHERE rownum = 1", 2290);
            expect(c, "Gast mit Verweis auf einen echten Mond wird abgewiesen", "UPDATE ura_scenario_body SET body_id = "
                    + "(SELECT MIN(body_id) FROM ura_body) WHERE role = 'GUEST'", 2290);
            expect(c, "Dichte neben gemessener Masse wird abgewiesen", "UPDATE ura_body SET density_assumed = 1.3 WHERE name = 'Titania'", 2290);
            expect(c, "Eingebautes Szenario löschen: ORA-20005", "BEGIN ura_api.drop_scenario('HEUTE'); END;", 20005);
            expect(c, "Eingebautes als eigenes überschreiben: ORA-20005", "BEGIN ura_api.put_scenario('HEUTE', 'x', NULL, 2461306.5, 1, 'USER', 'N'); END;", 20005);
            try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM ura_scenario WHERE code = 'GEDRAENGE'"); }
            long sb = one(c, "SELECT COUNT(*) FROM ura_scenario_body sb JOIN ura_scenario s ON s.scenario_id = sb.scenario_id WHERE s.origin = 'BUILTIN'");
            log.check("Szenario löschen nimmt seine Körper mit (ON DELETE CASCADE)", sb == 75 - 14, sb + " Körper übrig");
            expect(c, "Station mit Abstand 0 wird abgewiesen", "UPDATE ura_tour_step SET dist = 0 WHERE rownum = 1", 2290);
            expect(c, "Ereignis mit Mond UND Ring wird abgewiesen", "UPDATE ura_event SET ring_id = (SELECT MIN(ring_id) FROM ura_ring) "
                    + "WHERE title = 'Miranda entdeckt'", 2290);
            expect(c, "Doppelter Ereignistitel wird abgewiesen", "UPDATE ura_event SET title = 'Miranda entdeckt' WHERE title = 'Puck entdeckt'", 1);
            expect(c, "Unbekannte Fahrt: ORA-20001 aus put_tour_step", "BEGIN ura_api.put_tour_step('NIRGENDS', 1, 0, NULL, 0, 0, 10, 'Uranus', NULL, NULL, NULL); END;", 20001);
            try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM ura_tour WHERE code = 'VOYAGER'"); }
            long steps = one(c, "SELECT COUNT(*) FROM ura_tour_step");
            log.check("Fahrt löschen nimmt ihre Stationen mit (ON DELETE CASCADE)", steps == 33 - 8, steps + " Stationen übrig");
        } finally {
            c.rollback();
            c.setAutoCommit(true);
        }
        long back = one(c, "SELECT COUNT(*) FROM ura_orbit o JOIN ura_body b ON b.body_id = o.body_id WHERE b.name = 'Ariel' AND o.is_current = 'J' AND o.quality = 'FIT'");
        log.check("Nach dem Zurückrollen ist Ariels angeglichene Bahn wieder gültig", back == 1, "");

        log.say("--- 8. Gegenprobe mit SOLAR_BODY (FCurvedField)");
        if (one(c, "SELECT COUNT(*) FROM user_tables WHERE table_name = 'SOLAR_BODY'") == 0) {
            log.note("SOLAR_BODY fehlt", "Gegenprobe entfällt (FCurvedField nicht eingerichtet)");
        } else {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT s.name, s.semi_major_au * 149597870.7 AS a_solar, o.a_km FROM solar_body s JOIN ura_body b ON b.name = s.name "
                            + "JOIN ura_orbit o ON o.body_id = b.body_id AND o.is_current = 'J' ORDER BY s.name")) {
                int k = 0; double dev = 0; StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    double d = Math.abs(rs.getDouble(2) / rs.getDouble(3) - 1);
                    dev = Math.max(dev, d); k++;
                    sb.append(rs.getString(1)).append(String.format(" %.2f %%  ", d * 100));
                }
                log.check("Monde in beiden Schemata: große Halbachse stimmt auf 1 %", k >= 2 && dev < 0.01, k + " Monde · " + sb.toString().trim());
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT s.radius_km, b.radius_km FROM solar_body s JOIN ura_body b ON b.name = s.name WHERE s.name = 'Uranus'")) {
                if (rs.next()) log.note("Uranus-Radius", rs.getString(1) + " km (SOLAR_BODY, mittlerer) gegen " + rs.getString(2)
                        + " km (URA_BODY, Äquator) — beides richtig, verschiedene Definition");
            }
        }
    }

    // ------------------------------------------------------------------ Geschichten

    static void stories(Connection c, UranusDao.Result r, UranusSystem in) throws Exception {
        long ev = one(c, "SELECT COUNT(*) FROM ura_event"), st = one(c, "SELECT COUNT(*) FROM ura_craft_state"),
                tours = one(c, "SELECT COUNT(*) FROM ura_tour"), steps = one(c, "SELECT COUNT(*) FROM ura_tour_step");
        log.check("57 Ereignisse, 1 Sonde mit 145 Zuständen, 4 Fahrten mit 33 Stationen",
                ev == 57 && one(c, "SELECT COUNT(*) FROM ura_craft") == 1 && st == 145 && tours == 4 && steps == 33,
                ev + " / " + st + " / " + tours + " / " + steps);
        UranusSystem sys = r.system;
        // Ereignisse
        int same = 0;
        String diff = "";
        for (int i = 0; i < Math.min(in.events().size(), sys.events().size()); i++) {
            StoryEvent a = in.events().get(i), b = sys.events().get(i);
            if (Math.abs(a.jdUtc - b.jdUtc) < 1e-9 && a.kind == b.kind && a.precision == b.precision && a.title.equals(b.title)
                    && a.text.equals(b.text) && eq(a.body, b.body) && eq(a.ring, b.ring)) same++;
            else if (diff.isEmpty()) diff = a.title;
        }
        log.check("Ereignisse aus der DB = eingebauter Satz (Zeit, Art, Text, Mond/Ring)", r.fromDatabase && same == 57 && sys.events().size() == 57,
                same + " gleich" + (diff.isEmpty() ? "" : ", zuerst anders: " + diff));
        // Sonde
        Craft a = in.craft("Voyager 2"), b = sys.craft("Voyager 2");
        boolean craftSame = a != null && b != null && a.naifId == b.naifId && a.tpJd == b.tpJd && a.qKm == b.qKm && a.e == b.e
                && a.iDeg == b.iDeg && a.nodeDeg == b.nodeDeg && a.argpDeg == b.argpDeg && a.gm == b.gm
                && a.validFromJd == b.validFromJd && a.validToJd == b.validToJd && a.fitMaxKm == b.fitMaxKm;
        log.check("Voyager 2 aus der DB = eingebaute Hyperbel", r.fromDatabase && craftSame, b == null ? "fehlt" : String.format("q = %,.3f km, e = %.9f", b.qKm, b.e));
        // die gespeicherten Horizons-Vektoren gegen die Hyperbel aus der DB
        if (b != null) {
            List<double[]> states = new UranusDao(c).craftStates("Voyager 2");
            double worst = 0;
            double[] w = new double[3], p = new double[3];
            for (double[] s : states) {
                Ephemeris.icrfToWorld(new double[]{s[1], s[2], s[3]}, w);
                b.position(s[0], p);
                worst = Math.max(worst, Math.sqrt(sq(w[0] - p[0]) + sq(w[1] - p[1]) + sq(w[2] - p[2])));
            }
            log.check("Hyperbel trifft die 145 Vektoren aus URA_CRAFT_STATE wie gespeichert (fit_max_km)",
                    states.size() == 145 && Math.abs(worst - b.fitMaxKm) < 0.5, String.format("%d Vektoren, größte Abweichung %.1f km", states.size(), worst));
        }
        // Fahrten
        int tSame = 0;
        for (Tour t : in.tours()) {
            Tour d = sys.tour(t.code);
            if (d == null || d.steps().size() != t.steps().size() || d.overlay != t.overlay || d.timeMode != t.timeMode || !d.title.equals(t.title)) continue;
            boolean ok = true;
            for (int i = 0; i < t.steps().size(); i++) {
                Tour.Step x = t.steps().get(i), y = d.steps().get(i);
                ok &= x.atS == y.atS && (Double.isNaN(x.jdUtc) ? Double.isNaN(y.jdUtc) : Math.abs(x.jdUtc - y.jdUtc) < 1e-9)
                        && x.yawDeg == y.yawDeg && x.pitchDeg == y.pitchDeg && x.dist == y.dist && x.target.equals(y.target)
                        && x.layers.equals(y.layers) && x.title.equals(y.title) && x.caption.equals(y.caption);
            }
            if (ok) tSame++;
        }
        log.check("Fahrten aus der DB = eingebaute Kopie (alle Stationen, Schalter, Texte)", r.fromDatabase && tSame == 4 && sys.tours().size() == 4, tSame + " gleich");
        int years = 0;
        for (Ring x : sys.rings()) {
            Ring y = in.ring(x.name);
            if (y != null && x.discoveredYear == y.discoveredYear && x.discoveredBy.equals(y.discoveredBy) && x.discoveredYear >= 1977) years++;
        }
        log.check("Ringe mit Entdeckungsjahr und Entdecker aus der DB", r.fromDatabase && years == 13, years + " von 13");
    }

    // ------------------------------------------------------------------ Spielwiese

    static void play(Connection c, UranusDao.Result r, UranusSystem in, int pass) throws Exception {
        long n = one(c, "SELECT COUNT(*) FROM ura_scenario WHERE origin = 'BUILTIN'"), nb = one(c, "SELECT COUNT(*) FROM ura_scenario_body sb "
                + "JOIN ura_scenario s ON s.scenario_id = sb.scenario_id WHERE s.origin = 'BUILTIN'");
        log.check("5 eingebaute Szenarien mit 75 Körpern", n == 5 && nb == 75, n + " / " + nb);
        long dens = one(c, "SELECT COUNT(*) FROM ura_body WHERE density_assumed = 1.3 AND gm_km3s2 IS NULL"),
                meas = one(c, "SELECT COUNT(*) FROM ura_body WHERE density_assumed IS NULL AND gm_km3s2 IS NOT NULL");
        log.check("Masse: 24 Monde mit angenommener Dichte 1,3 g/cm³, Uranus und 5 gemessen", dens == 24 && meas == 6
                && Math.abs(UranusSystem.DENSITY_ASSUMED - 1300) < 1e-9, dens + " / " + meas);
        UranusSystem sys = r.system;
        int same = 0;
        String diff = "";
        for (Scenario a : in.scenarios()) {
            Scenario b = sys.scenario(a.code);
            if (b != null && equal(a, b)) same++;
            else if (diff.isEmpty()) diff = a.code;
        }
        log.check("Szenarien aus der DB = eingebaute Kopie (alle Zustandsvektoren exakt)", r.fromDatabase && same == 5,
                same + " gleich" + (diff.isEmpty() ? "" : ", zuerst anders: " + diff));
        UranusDao dao = new UranusDao(c);
        double worst = 0;
        for (Scenario a : in.scenarios()) worst = Math.max(worst, Math.abs(dao.energy(a.code) / a.energyJ() - 1));
        log.check("Energie in PL/SQL = Java (alle 5 Szenarien)", worst < 1e-12, String.format("größte relative Abweichung %.1e", worst));
        // eigener Stand: speichern, zurücklesen, löschen — wie der Knopf „Speichern“ in der App
        Scenario h = in.scenario("HEUTE");
        String code = "PRUEF_" + pass + "_" + (System.currentTimeMillis() % 100000);
        Scenario mine = new Scenario(code, "Prüfung " + pass, "vom Durchstich, wird gleich wieder gelöscht", h.startJd + 1.25, 3,
                Scenario.Origin.USER, false, h.items());
        dao.saveScenario(mine);
        Scenario back = null;
        for (Scenario s : dao.scenarios()) if (s.code.equals(code)) back = s;
        log.check("Eigenes Szenario speichern und zurücklesen: derselbe Zustand", back != null && equal(mine, back)
                && back.origin == Scenario.Origin.USER, back == null ? "fehlt" : back.items().size() + " Körper");
        log.check("… Energie in PL/SQL wie gespeichert", back != null && Math.abs(dao.energy(code) / mine.energyJ() - 1) < 1e-12, "");
        dao.dropScenario(code);
        long left = one(c, "SELECT COUNT(*) FROM ura_scenario WHERE code = '" + code + "'")
                + one(c, "SELECT COUNT(*) FROM ura_scenario_body sb JOIN ura_scenario s ON s.scenario_id = sb.scenario_id WHERE s.code = '" + code + "'");
        log.check("… und wieder löschen (mit Körpern)", left == 0, "");
    }

    static boolean equal(Scenario a, Scenario b) {
        if (!a.code.equals(b.code) || !a.title.equals(b.title) || !a.description.equals(b.description) || a.startJd != b.startJd
                || a.rateDaysPerS != b.rateDaysPerS || a.compareEphemeris != b.compareEphemeris || a.items().size() != b.items().size()) return false;
        for (int i = 0; i < a.items().size(); i++) {
            Scenario.Item x = a.items().get(i), y = b.items().get(i);
            if (!x.name.equals(y.name) || x.role != y.role || x.massKg != y.massKg || x.massEstimated != y.massEstimated || x.radiusKm != y.radiusKm)
                return false;
            for (int k = 0; k < 3; k++) if (x.r[k] != y.r[k] || x.v[k] != y.v[k]) return false;
        }
        return true;
    }

    static boolean eq(String a, String b) {
        return (a == null || a.isEmpty()) ? (b == null || b.isEmpty()) : a.equals(b);
    }

    // ------------------------------------------------------------------ Hilfen

    static double sq(double v) { return v * v; }

    static boolean equal(OrbitElements a, OrbitElements b) {
        return a.frame == b.frame && a.epochJd == b.epochJd && a.aKm == b.aKm && a.e == b.e && a.iDeg == b.iDeg
                && a.nodeDeg == b.nodeDeg && a.argPeriDeg == b.argPeriDeg && a.meanAnomalyDeg == b.meanAnomalyDeg
                && a.periodDays == b.periodDays && a.apsisPeriodYr == b.apsisPeriodYr && a.nodePeriodYr == b.nodePeriodYr
                && (Double.isNaN(a.poleRaDeg) ? Double.isNaN(b.poleRaDeg) : a.poleRaDeg == b.poleRaDeg);
    }

    static long one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Erwartet einen bestimmten Fehler; eine erfolgreiche Anweisung ist hier ein Fehler. */
    static void expect(Connection c, String name, String sql, int code) {
        try (Statement st = c.createStatement()) {
            if (st.execute(sql)) try (ResultSet rs = st.getResultSet()) { while (rs.next()) { /* Fehler kommt beim Holen */ } }
            log.check(name, false, "Anweisung lief durch, erwartet war ORA-" + String.format("%05d", code));
        } catch (SQLException e) {
 log.check(name, code < 0 ? e.getErrorCode() >= 20000 && e.getErrorCode() <= 20999 : e.getErrorCode() == code, "ORA-" + String.format("%05d", e.getErrorCode()) + " " + UraLog.oneLine(e.getMessage()).replaceFirst("^ORA-\\d+:\\s*", ""));
        }
    }

    @SuppressWarnings("unused")
    private static final Object KEEP = new Object[]{OrbitQuality.FIT, Types.VARCHAR, CallableStatement.class};
}
