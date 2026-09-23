import com.dan.uranus.db.UranusDb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Bestandsaufnahme im Schema DEMO — liest nur, ändert nichts.
 * <p>
 * Was liegt schon da (URA_, FCF_, SOLAR_), welche Rechte und Kontingente hat DEMO, speichert die
 * Datenbank griechische Buchstaben (ε, ζ) richtig, was steht in SOLAR_BODY über Uranus.
 * Protokoll: db_inventur.log im Projektordner.
 */
public class UraDbInventur {

    public static void main(String[] args) throws Exception {
        try (UraLog log = new UraLog("db_inventur.log", "Uranus - Bestandsaufnahme im Schema (nur lesen)")) {
            UranusDb db = UranusDb.fromProperties();
            if (db == null) {
                log.say("FEHLT: uranus-db.properties mit url, user, password im Projektordner.");
                return;
            }
            log.say("Verbindung  : " + db);
            log.say("");
            try (Connection c = db.open()) {
                log.say("Datenbank   : " + UraLog.oneLine(c.getMetaData().getDatabaseProductVersion()));
                log.say("Treiber     : " + c.getMetaData().getDriverVersion());
                table(log, c, "Sitzung", "SELECT SYS_CONTEXT('USERENV','CON_NAME') AS pdb, USER AS schema_name, "
                        + "DBTIMEZONE AS db_zeitzone, SESSIONTIMEZONE AS sitzungszone FROM dual");
                table(log, c, "Zeichensatz", "SELECT parameter, value FROM nls_database_parameters "
                        + "WHERE parameter IN ('NLS_CHARACTERSET', 'NLS_NCHAR_CHARACTERSET')");
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT 'ε ζ ν μ „Der Sturm“' FROM dual")) {
                    rs.next();
                    String back = rs.getString(1);
                    log.say("Unicode-Rundreise: " + ("ε ζ ν μ „Der Sturm“".equals(back) ? "unverändert" : "VERÄNDERT zu '" + back + "'"));
                }
                table(log, c, "Rechte (Auszug)", "SELECT privilege FROM session_privs WHERE privilege IN ('CREATE TABLE', "
                        + "'CREATE SEQUENCE', 'CREATE TRIGGER', 'CREATE PROCEDURE', 'CREATE TYPE', 'CREATE VIEW', "
                        + "'UNLIMITED TABLESPACE') ORDER BY privilege");
                table(log, c, "Kontingente", "SELECT tablespace_name, bytes, max_bytes FROM user_ts_quotas");
                table(log, c, "Objekte nach Präfix", "SELECT CASE WHEN object_name LIKE 'URA\\_%' ESCAPE '\\' OR object_name = 'T_URA_VEC' THEN 'URA_ (Uranus)' "
                        + "WHEN object_name LIKE 'FCF%' THEN 'FCF_ (FCurvedField)' WHEN object_name LIKE 'SOLAR%' THEN 'SOLAR_ (FCurvedField)' "
                        + "ELSE 'andere' END AS praefix, object_type, COUNT(*) AS anzahl FROM user_objects "
                        + "GROUP BY CASE WHEN object_name LIKE 'URA\\_%' ESCAPE '\\' OR object_name = 'T_URA_VEC' THEN 'URA_ (Uranus)' "
                        + "WHEN object_name LIKE 'FCF%' THEN 'FCF_ (FCurvedField)' WHEN object_name LIKE 'SOLAR%' THEN 'SOLAR_ (FCurvedField)' "
                        + "ELSE 'andere' END, object_type ORDER BY 1, 2");
                table(log, c, "Uranus-Objekte", "SELECT object_name, object_type, status, TO_CHAR(last_ddl_time, 'DD.MM.YYYY HH24:MI') AS geaendert "
                        + "FROM user_objects WHERE object_name LIKE 'URA\\_%' ESCAPE '\\' OR object_name = 'T_URA_VEC' ORDER BY object_type, object_name");
                table(log, c, "Ungültige Objekte (alle)", "SELECT object_name, object_type FROM user_objects WHERE status <> 'VALID' ORDER BY 1");
                table(log, c, "SOLAR_BODY: Uranus und seine Monde", "SELECT b.name, b.body_type, b.radius_km, b.semi_major_au, "
                        + "ROUND(b.semi_major_au * 149597870.7) AS a_km, b.orbit_years, b.eccentricity, b.inclination_deg, b.discovered_year "
                        + "FROM solar_body b LEFT JOIN solar_body p ON p.body_id = b.parent_id "
                        + "WHERE b.name = 'Uranus' OR p.name = 'Uranus' ORDER BY b.semi_major_au");
            } catch (SQLException e) {
                log.say("");
                log.say("VERBINDUNG/ABFRAGE FEHLGESCHLAGEN: ORA-" + e.getErrorCode() + " " + UraLog.oneLine(e.getMessage()));
            }
            log.say("");
            log.say("Ende der Bestandsaufnahme. Geändert wurde nichts.");
        }
    }

    /** Gibt eine Abfrage als Tabelle aus; Fehler werden gemeldet, brechen aber nicht ab. */
    static void table(UraLog log, Connection c, String title, String sql) {
        log.say("");
        log.say("--- " + title);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            StringBuilder h = new StringBuilder("   ");
            for (int i = 1; i <= md.getColumnCount(); i++) h.append(UraLog.pad(md.getColumnLabel(i).toLowerCase(), 18)).append(' ');
            log.say(h.toString());
            int n = 0;
            while (rs.next()) {
                StringBuilder b = new StringBuilder("   ");
                for (int i = 1; i <= md.getColumnCount(); i++) b.append(UraLog.pad(UraLog.oneLine(rs.getString(i)), 18)).append(' ');
                log.say(b.toString());
                n++;
            }
            if (n == 0) log.say("   (keine Zeilen)");
        } catch (SQLException e) {
            log.say("   nicht lesbar: ORA-" + e.getErrorCode() + " " + UraLog.oneLine(e.getMessage()));
        }
    }
}
