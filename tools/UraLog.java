import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Protokoll für die Datenbank-Werkzeuge: schreibt gleichzeitig auf die Konsole und in eine Datei
 * im Projektordner. Die Datei ist der Rückkanal — die Datenbank ist von Claudes Seite nicht
 * erreichbar, das Protokoll im verbundenen Ordner schon.
 */
final class UraLog implements AutoCloseable {

    private final PrintWriter out;
    final File file;
    int ok, bad;

    UraLog(String name, String title) throws Exception {
        file = new File(name).getAbsoluteFile();
        out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8), true);
        say(title);
        say("Zeit        : " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date()));
        say("Verzeichnis : " + new File(".").getCanonicalPath());
    }

    void say(String s) {
        System.out.println(s);
        out.println(s);
    }

    /** Eine Prüfung: [ok] oder [FEHLER], gezählt. */
    void check(String name, boolean c, String info) {
        if (c) ok++; else bad++;
        say((c ? "[ok]     " : "[FEHLER] ") + name + (info == null || info.isEmpty() ? "" : " — " + info));
    }

    /** Eine Beobachtung ohne Urteil (zählt nicht). */
    void note(String name, String info) { say("[info]   " + name + (info == null || info.isEmpty() ? "" : " — " + info)); }

    static String oneLine(String s) {
        if (s == null) return "";
        return s.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    static String pad(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s;
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    @Override
    public void close() {
        out.flush();
        out.close();
    }
}
