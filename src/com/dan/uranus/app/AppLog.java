package com.dan.uranus.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Protokoll der App: {@code %USERPROFILE%/.uranus/uranus.log} (oder der Ordner aus {@code -Duranus.home}).
 * <p>
 * Beim Start aus {@code dist} per Doppelklick gibt es keine Konsole; Fehler landen deshalb hier.
 * Wird die Datei größer als 1 MB, wird sie zu {@code uranus.log.1} (eine Vorgängerdatei) und neu begonnen.
 */
public final class AppLog {

    public static final long MAX_BYTES = 1_000_000;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static File file;

    private AppLog() { }

    /** Ordner für Protokoll, Einstellungen als Datei und Ähnliches: -Duranus.home oder ~/.uranus. */
    public static File homeDir() {
        String h = System.getProperty("uranus.home");
        return h != null && !h.isBlank() ? new File(h) : new File(System.getProperty("user.home", "."), ".uranus");
    }

    /** Legt das Protokoll an und fängt ab jetzt auch unbehandelte Fehler aller Threads. */
    public static synchronized void init(File dir) {
        dir.mkdirs();
        file = new File(dir, "uranus.log");
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> error("Unbehandelter Fehler in " + t.getName(), e));
    }

    public static synchronized File file() { return file; }

    public static void info(String msg) { write("INFO ", msg, null); }

    public static void error(String msg, Throwable t) { write("FEHLER", msg, t); }

    private static synchronized void write(String level, String msg, Throwable t) {
        if (file == null) { System.err.println(level + " " + msg); if (t != null) t.printStackTrace(); return; }
        try {
            if (file.length() > MAX_BYTES) {
                File old = new File(file.getParentFile(), file.getName() + ".1");
                if (old.exists() && !old.delete()) return;
                if (!file.renameTo(old)) return;
            }
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                w.write(LocalDateTime.now().format(TS) + " " + level + " " + msg + System.lineSeparator());
                if (t != null) {
                    StringWriter sw = new StringWriter();
                    t.printStackTrace(new PrintWriter(sw));
                    w.write(sw.toString());
                }
            }
        } catch (IOException e) {
            System.err.println("Protokoll nicht schreibbar: " + e.getMessage());
        }
    }
}
