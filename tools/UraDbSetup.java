import com.dan.uranus.db.UranusDb;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fährt die SQL-Skripte aus db/ gegen DEMO und schreibt db_setup.log.
 * <p>
 * Aufruf: {@code java UraDbSetup} — alle Skripte 00 bis 05; mit Dateinamen nur diese.
 * Zerlegung wie in SQL*Plus: ein Schrägstrich auf eigener Zeile beendet einen PL/SQL-Block,
 * ein Semikolon am Zeilenende eine gewöhnliche Anweisung (übernommen aus FCfDbSetup).
 * Kompilierfehler stehen in USER_ERRORS und wären sonst unsichtbar — geprüft wird nur für
 * Uranus-Objekte, damit fremde Fehler (FCurvedField) nicht hier auftauchen.
 */
public class UraDbSetup {

    static final String[] DEFAULT = {"00_drop.sql", "01_schema.sql", "02_package.sql", "03_bodies.sql", "04_orbits.sql", "05_rings.sql",
            "06_events.sql", "07_voyager.sql", "08_tours.sql", "09_scenarios.sql"};

    static UraLog log;
    static int errors, statements;

    public static void main(String[] args) throws Exception {
        try (UraLog l = new UraLog("db_setup.log", "Uranus - Datenbank einrichten")) {
            log = l;
            UranusDb db = UranusDb.fromProperties();
            if (db == null) {
                log.say("FEHLT: uranus-db.properties mit url, user, password im Projektordner.");
                return;
            }
            log.say("Verbindung  : " + db);
            log.say("");
            List<String> scripts = new ArrayList<>(args.length == 0 ? Arrays.asList(DEFAULT) : Arrays.asList(args));
            try (Connection c = db.open()) {
                for (String s : scripts) run(c, new File("db", s));
                log.say("");
                log.say("=========================================================");
                log.say("Fertig. " + statements + " Anweisungen, " + errors + " Fehler.");
                if (args.length == 0) selfTest(c);          // auch nach Fehlern: die Zählungen zeigen, was fehlt
            } catch (SQLException e) {
                log.say("VERBINDUNG FEHLGESCHLAGEN: ORA-" + e.getErrorCode() + " " + UraLog.oneLine(e.getMessage()));
                log.say("Prüfe: läuft der Listener, stimmt der Servicename PDBORCL, stimmt das Kennwort in uranus-db.properties?");
            }
        } catch (Throwable t) {
            // Auffangnetz: das Protokoll darf nie mitten im Satz abreißen
            System.out.println("ABBRUCH: " + t);
        }
    }

    static void run(Connection c, File f) throws Exception {
        log.say("---------------------------------------------------------");
        log.say("Skript: " + f.getName());
        if (!f.isFile()) { log.say("  FEHLT: " + f.getAbsolutePath()); errors++; return; }
        int before = errors, n = 0;
        for (String sql : split(f)) {
            n++;
            execute(c, sql, !f.getName().startsWith("03") && !f.getName().startsWith("04") && !f.getName().startsWith("05"));
        }
        // Datenskripte sind lang: nur zusammengefasst, Fehler stehen trotzdem einzeln da
        log.say("  " + n + " Anweisungen" + (errors == before ? ", ohne Fehler" : ", " + (errors - before) + " Fehler"));
    }

    static List<String> split(File f) throws Exception {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean plsql = false;
        for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (buf.length() == 0) {
                if (t.isEmpty() || t.startsWith("--")) continue;
                String u = t.toUpperCase();
                plsql = u.startsWith("DECLARE") || u.startsWith("BEGIN")
                        || (u.startsWith("CREATE") && (u.contains(" PACKAGE") || u.contains(" PROCEDURE")
                        || u.contains(" FUNCTION") || u.contains(" TRIGGER") || u.contains(" TYPE")));
            }
            if (t.equals("/")) {
                if (buf.length() > 0) { out.add(buf.toString()); buf.setLength(0); }
                plsql = false;
                continue;
            }
            buf.append(line).append('\n');
            if (!plsql && t.endsWith(";")) {
                String s = buf.toString().trim();
                out.add(s.substring(0, s.length() - 1));
                buf.setLength(0);
            }
        }
        if (buf.toString().trim().length() > 0) out.add(buf.toString());
        return out;
    }

    static void execute(Connection c, String sql, boolean verbose) {
        String head = UraLog.oneLine(sql);
        if (head.length() > 90) head = head.substring(0, 90) + " …";
        statements++;
        try (Statement st = c.createStatement()) {
            st.execute(sql);
            if (verbose) log.say("  " + head + "   ok");
            if (head.toUpperCase().startsWith("CREATE")) compileErrors(c);
        } catch (SQLException e) {
            errors++;
            log.say("  " + head);
            log.say("     FEHLER ORA-" + e.getErrorCode() + ": " + UraLog.oneLine(e.getMessage()));
        }
    }

    static void compileErrors(Connection c) {
        String q = "SELECT name, type, line, position, text FROM user_errors WHERE attribute = 'ERROR' "
                + "AND (name LIKE 'URA\\_%' ESCAPE '\\' OR name = 'T_URA_VEC') ORDER BY name, sequence";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q)) {
            boolean any = false;
            while (rs.next()) {
                if (!any) { log.say("     KOMPILIERFEHLER:"); any = true; errors++; }
                log.say("       " + rs.getString(2) + " " + rs.getString(1) + " Zeile " + rs.getInt(3) + ", Spalte " + rs.getInt(4)
                        + ": " + UraLog.oneLine(rs.getString(5)));
            }
        } catch (SQLException ignore) {
            // hilft dann auch nicht weiter
        }
    }

    static void selfTest(Connection c) {
        log.say("");
        log.say("Selbsttest");
        log.say("---------------------------------------------------------");
        count(c, "Quellen", "SELECT COUNT(*) FROM ura_source", 9);
        count(c, "Körper (Uranus + 29 Monde)", "SELECT COUNT(*) FROM ura_body", 30);
        count(c, "Bahnen insgesamt", "SELECT COUNT(*) FROM ura_orbit", 58);
        count(c, "davon gültig", "SELECT COUNT(*) FROM ura_orbit WHERE is_current = 'J'", 29);
        count(c, "Ringe", "SELECT COUNT(*) FROM ura_ring", 13);
        count(c, "Ringe mit Entdeckungsjahr", "SELECT COUNT(*) FROM ura_ring WHERE discovered_year IS NOT NULL", 13);
        count(c, "Ereignisse", "SELECT COUNT(*) FROM ura_event", 57);
        count(c, "Sonden", "SELECT COUNT(*) FROM ura_craft", 1);
        count(c, "Sondenzustände (Horizons)", "SELECT COUNT(*) FROM ura_craft_state", 145);
        count(c, "Fahrten", "SELECT COUNT(*) FROM ura_tour", 4);
        count(c, "Stationen", "SELECT COUNT(*) FROM ura_tour_step", 33);
        count(c, "Szenarien (eingebaut)", "SELECT COUNT(*) FROM ura_scenario WHERE origin = 'BUILTIN'", 5);
        count(c, "Körper in Szenarien", "SELECT COUNT(*) FROM ura_scenario_body", 75);
        count(c, "Monde mit angenommener Dichte", "SELECT COUNT(*) FROM ura_body WHERE density_assumed = 1.3", 24);
        count(c, "Ungültige Uranus-Objekte", "SELECT COUNT(*) FROM user_objects WHERE status <> 'VALID' "
                + "AND (object_name LIKE 'URA\\_%' ESCAPE '\\' OR object_name = 'T_URA_VEC')", 0);
        try (CallableStatement cs = c.prepareCall("{ ? = call ura_api.info }")) {
            cs.registerOutParameter(1, Types.VARCHAR);
            cs.execute();
            log.say("   " + cs.getString(1));
        } catch (SQLException e) {
            log.say("   ura_api.info FEHLER: " + UraLog.oneLine(e.getMessage()));
            errors++;
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT ROUND(t.p.x), ROUND(t.p.y), ROUND(t.p.z) FROM (SELECT ura_api.position_at('Titania', 2461306.5) AS p FROM dual) t")) {
            rs.next();
            log.say("   Durchstich: Titania am 23.09.2026 bei x=" + rs.getString(1) + " y=" + rs.getString(2) + " z=" + rs.getString(3) + " km");
        } catch (SQLException e) {
            log.say("   Durchstich FEHLER: " + UraLog.oneLine(e.getMessage()));
            errors++;
        }
        log.say("");
        log.say(errors == 0 ? "Einrichtung in Ordnung. Weiter mit db_check.bat." : "Es gab Fehler — bitte db_setup.log schicken.");
    }

    static void count(Connection c, String what, String sql, int want) {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            int n = rs.getInt(1);
            boolean good = n == want;
            if (!good) errors++;
            log.say("   " + UraLog.pad(what, 30) + UraLog.pad(String.valueOf(n), 6) + (good ? "[ok]" : "[ERWARTET " + want + "]"));
        } catch (SQLException e) {
            errors++;
            log.say("   " + UraLog.pad(what, 30) + "FEHLER: " + UraLog.oneLine(e.getMessage()));
        }
    }
}
