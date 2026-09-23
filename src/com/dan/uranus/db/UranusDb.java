package com.dan.uranus.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Verbindungsdaten für das Schema DEMO.
 * <p>
 * Gelesen aus {@code uranus-db.properties}. Gesucht wird nacheinander: die Datei, die
 * {@code -Duranus.db=...} nennt; der Arbeitsordner (beim Start aus NetBeans der Projektordner);
 * der Ordner neben {@code Uranus.jar} (Start aus {@code dist}); der Ordner darüber (Projektordner);
 * {@code %USERPROFILE%\.uranus}. Das Kennwort steht nur dort, nicht im Code, nicht im Werkbuch
 * und wird nicht nach {@code dist} kopiert.
 * <pre>
 * url=jdbc:oracle:thin:@//localhost:1521/PDBORCL
 * user=DEMO
 * password=...
 * </pre>
 */
public final class UranusDb {

    public static final String DEFAULT_URL = "jdbc:oracle:thin:@//localhost:1521/PDBORCL";
    public static final String DEFAULT_USER = "DEMO";

    public final String url, user;
    private final String password;
    /** Woher die Angaben stammen (für Meldungen). */
    public final String origin;

    public UranusDb(String url, String user, String password, String origin) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.origin = origin;
    }

    public static final String FILE = "uranus-db.properties";

    /** Was die letzte Suche ergab (für die Statuszeile): gefundene Datei oder die gesuchten Orte. */
    public static volatile String lastSearch = "";

    /** Liest die Verbindungsdatei; ohne Datei oder ohne Kennwort gibt es keine Datenbank (null). */
    public static UranusDb fromProperties() {
        java.util.List<File> places = searchPlaces(System.getProperty("uranus.db"), new File(System.getProperty("user.dir", ".")),
                jarDir(), new File(System.getProperty("user.home", "."), ".uranus"));
        File f = null;
        for (File c : places) if (c.isFile()) { f = c; break; }
        if (f == null) {
            StringBuilder b = new StringBuilder("keine " + FILE + " gefunden; gesucht in: ");
            for (int i = 0; i < places.size(); i++) b.append(i == 0 ? "" : " · ").append(places.get(i).getParentFile() == null ? places.get(i) : places.get(i).getParent());
            lastSearch = b.toString();
            return null;
        }
        lastSearch = "Verbindung aus " + f.getAbsolutePath();
        return read(f);
    }

    /**
     * Orte der Verbindungsdatei in Suchreihenfolge. explicit (-Duranus.db) gilt allein, wenn gesetzt.
     * jarDir = Ordner der laufenden jar (null beim Start aus build\classes).
     */
    public static java.util.List<File> searchPlaces(String explicit, File workDir, File jarDir, File homeDir) {
        java.util.List<File> l = new java.util.ArrayList<>();
        if (explicit != null && !explicit.isBlank()) { l.add(new File(explicit)); return l; }
        java.util.LinkedHashSet<File> seen = new java.util.LinkedHashSet<>();
        seen.add(abs(new File(workDir, FILE)));
        if (jarDir != null) {
            seen.add(abs(new File(jarDir, FILE)));
            if (jarDir.getAbsoluteFile().getParentFile() != null) seen.add(abs(new File(jarDir.getAbsoluteFile().getParentFile(), FILE)));
        }
        if (homeDir != null) seen.add(abs(new File(homeDir, FILE)));
        l.addAll(seen);
        return l;
    }

    private static File abs(File f) {
        try { return f.getCanonicalFile(); } catch (IOException e) { return f.getAbsoluteFile(); }
    }

    /** Ordner der jar, aus der die App läuft; null, wenn sie aus einem Klassenordner läuft. */
    public static File jarDir() {
        try {
            java.net.URL u = UranusDb.class.getProtectionDomain().getCodeSource().getLocation();
            File f = new File(u.toURI());
            return f.isFile() ? f.getParentFile() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Liest eine bestimmte Verbindungsdatei; ohne Kennwort null. */
    public static UranusDb read(File f) {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return null;
        }
        String pwd = p.getProperty("password", "").trim();
        if (pwd.isEmpty()) return null;
        return new UranusDb(p.getProperty("url", DEFAULT_URL).trim(), p.getProperty("user", DEFAULT_USER).trim(),
                pwd, f.getAbsolutePath());
    }

    /** Öffnet eine Verbindung; wartet höchstens einige Sekunden auf den Listener. */
    public Connection open() throws SQLException {
        DriverManager.setLoginTimeout(6);
        Connection c = DriverManager.getConnection(url, user, password);
        c.setAutoCommit(true);
        return c;
    }

    @Override
    public String toString() { return user + " @ " + url; }
}
