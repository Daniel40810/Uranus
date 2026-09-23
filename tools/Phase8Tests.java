import com.dan.uranus.app.AppLog;
import com.dan.uranus.app.AppPrefs;
import com.dan.uranus.app.Snapshot;
import com.dan.uranus.app.UranusApp;
import com.dan.uranus.db.UranusDb;
import com.dan.uranus.model.*;
import com.dan.uranus.physics.Ephemeris;
import com.dan.uranus.physics.Kepler;
import com.dan.uranus.physics.SpaceMap;
import com.dan.uranus.physics.TimeScale;
import com.dan.uranus.render.RenderLoop;
import com.dan.uranus.render.Renderer;
import com.dan.uranus.render.SceneSettings;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Phase 8 — Politur, ohne Fenster und ohne Datenbank: Bild gleich trotz Abkürzungen (Pixelvergleich gegen
 * den Referenzweg), Himmel aus dem letzten Bild, Karten beim Überfahren, Hilfe, Symbole, Standbild mit
 * Textfeldern, Gerätepixel, gemerkte Einstellungen (eigener Prüfknoten), Suche der Verbindungsdatei,
 * Protokoll, Fenstersymbol, README und dist ohne Kennwort.
 * Aufruf aus dem Projektordner: java -cp build/classes;build/tools;lib/FStyle.jar Phase8Tests
 */
public class Phase8Tests {

    static int ok, bad;
    static final int W = 1480, H = 820;

    static void check(String name, boolean c, String info) {
        if (c) ok++; else bad++;
        System.out.println((c ? "[ok]     " : "[FEHLER] ") + name + (info.isEmpty() ? "" : " — " + info));
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMANY);
        System.setProperty("java.awt.headless", "true");
        System.out.println("Phase 8 — Politur (ohne Verbindung)");
        File tmp = Files.createTempDirectory("uranus-p8").toFile();
        pixels();
        sky();
        hover();
        timelines();
        helpAndIcons();
        still(tmp);
        deviceScale();
        prefs();
        delivery(tmp);
        System.out.println();
        System.out.println("Ergebnis: " + ok + " ok, " + bad + " Fehler");
        System.exit(bad == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Aufbau

    static Renderer rig(SceneSettings s, SimClock c, String iso, Consumer<SceneSettings> cfg, double dist) {
        s.adaptiveScale = false;
        c.setJd(SimClock.jdOf(Instant.parse(iso)));
        if (cfg != null) cfg.accept(s);
        Renderer r = new Renderer(UranusSystem.builtIn(), s, c);
        r.settle();
        if (dist > 0) r.camera().setDistanceTarget(dist);
        return r;
    }

    /** Eine Szene deterministisch rechnen (wie die Vergleichsbilder beim Bau). */
    static BufferedImage scene(String iso, Consumer<SceneSettings> cfg, double dist, double yaw, double pitch, boolean exact) {
        Renderer.setExactReference(exact);
        try {
            SceneSettings s = new SceneSettings();
            SimClock c = new SimClock();
            Renderer r = rig(s, c, iso, cfg, dist);
            if (!Double.isNaN(yaw)) r.camera().setOrientation(yaw, pitch);
            for (int i = 0; i < 60; i++) { c.advance(1 / 15.0); r.render(400, 225, 1 / 15.0); }
            for (int i = 0; i < 2; i++) { c.advance(1 / 30.0); r.render(1600, 900, 1 / 30.0); }
            c.advance(1 / 30.0);
            Renderer.Frame f = r.render(1600, 900, 1 / 30.0);
            BufferedImage copy = new BufferedImage(f.image.getWidth(), f.image.getHeight(), BufferedImage.TYPE_INT_RGB);
            copy.getGraphics().drawImage(f.image, 0, 0, null);
            return copy;
        } finally {
            Renderer.setExactReference(false);
        }
    }

    static double[] diff(BufferedImage x, BufferedImage y) {
        double sum = 0; int mx = 0;
        for (int j = 0; j < x.getHeight(); j++) for (int i = 0; i < x.getWidth(); i++) {
            int p = x.getRGB(i, j), q = y.getRGB(i, j);
            for (int k = 0; k < 24; k += 8) { int d = Math.abs(((p >> k) & 255) - ((q >> k) & 255)); sum += d; mx = Math.max(mx, d); }
        }
        return new double[]{sum / (3.0 * x.getWidth() * x.getHeight()), mx};
    }

    // ------------------------------------------------------------------ Bild gleich, schneller

    static void pixels() {
        String now = "2026-09-23T00:00:00Z";
        Object[][] scenes = {
                {"Übersicht", now, (Consumer<SceneSettings>) s -> {}, 0.0, Double.NaN, 0.0},
                {"Uranus bildfüllend (mit Polarlicht)", now, (Consumer<SceneSettings>) s -> {}, 2.2, Double.NaN, 0.0},
                {"ε-Ring-Flug", now, (Consumer<SceneSettings>) s -> s.ringFlight = true, 0.0, Double.NaN, 0.0},
                {"Tagundnachtgleiche 2007 (Mondschatten)", "2007-12-07T12:00:00Z", (Consumer<SceneSettings>) s -> s.magneto = false, 6.0, 1.0472, 0.0}};
        for (Object[] sc : scenes) {
            @SuppressWarnings("unchecked") Consumer<SceneSettings> cfg = (Consumer<SceneSettings>) sc[2];
            BufferedImage fast = scene((String) sc[1], cfg, (Double) sc[3], (Double) sc[4], (Double) sc[5], false);
            BufferedImage ref = scene((String) sc[1], cfg, (Double) sc[3], (Double) sc[4], (Double) sc[5], true);
            double[] d = diff(fast, ref);
            check("Bild gleich trotz Abkürzungen: " + sc[0], d[0] < 1.0 && d[1] <= 8,
                    String.format(Locale.GERMAN, "im Mittel %.4f Grauwerte, höchstens %.0f", d[0], d[1]));
        }
        // Zeiten: bildfüllend schneller als der Referenzweg
        double tf = time(false), te = time(true);
        check("Uranus bildfüllend schneller als der Referenzweg", tf < te * 0.9,
                String.format(Locale.GERMAN, "%.0f ms statt %.0f ms je Bild (1 600 × 900, hier)", tf, te));
    }

    static double time(boolean exact) {
        Renderer.setExactReference(exact);
        try {
            SceneSettings s = new SceneSettings();
            SimClock c = new SimClock();
            Renderer r = rig(s, c, "2026-09-23T00:00:00Z", null, 2.2);
            for (int i = 0; i < 60; i++) { c.advance(1 / 15.0); r.render(400, 225, 1 / 15.0); }
            for (int i = 0; i < 6; i++) r.render(1600, 900, 1 / 30.0);
            long t0 = System.nanoTime();
            int n = 10;
            for (int i = 0; i < n; i++) r.render(1600, 900, 1 / 30.0);
            return (System.nanoTime() - t0) / 1e6 / n;
        } finally {
            Renderer.setExactReference(false);
        }
    }

    // ------------------------------------------------------------------ Himmel aus dem letzten Bild

    static void sky() {
        SceneSettings s = new SceneSettings();
        SimClock c = new SimClock();
        // Polarlicht und Wellen laufen in Echtzeit weiter; für den Vergleich zweier Bilder aus
        Renderer r = rig(s, c, "2026-09-23T00:00:00Z", x -> { x.magneto = false; x.waves = false; }, 0);
        r.camera().setAutoSpin(0);
        c.setPaused(true);
        for (int i = 0; i < 80; i++) r.render(640, 360, 1 / 15.0);
        long h0 = r.skyCacheHits();
        for (int i = 0; i < 3; i++) r.render(640, 360, 1 / 30.0);
        BufferedImage cached = copy(r.render(640, 360, 1 / 30.0).image);
        long h1 = r.skyCacheHits();
        Renderer.setExactReference(true);
        BufferedImage fresh = copy(r.render(640, 360, 1 / 30.0).image);
        Renderer.setExactReference(false);
        check("Kamera und Zeit stehen: der Himmel kommt aus dem letzten Bild", h1 - h0 >= 3, (h1 - h0) + " von 4 Bildern");
        double[] d = diff(cached, fresh);
        check("… und ist derselbe wie neu gerechnet", d[0] < 0.01 && d[1] <= 1, String.format(Locale.GERMAN, "im Mittel %.4f, höchstens %.0f", d[0], d[1]));
        c.setPaused(false);
        r.camera().setAutoSpin(0.3);
        long h2 = r.skyCacheHits();
        for (int i = 0; i < 5; i++) r.render(640, 360, 1 / 30.0);
        check("Dreht sich die Kamera, wird er neu gerechnet", r.skyCacheHits() == h2, "");
    }

    static BufferedImage copy(BufferedImage im) {
        BufferedImage c = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_RGB);
        c.getGraphics().drawImage(im, 0, 0, null);
        return c;
    }

    // ------------------------------------------------------------------ Karten beim Überfahren

    static Renderer.Pick pick(Renderer.Frame f, String name) {
        for (Renderer.Pick p : f.picks) if (p.body.name.equals(name)) return p;
        return null;
    }

    static void hover() throws Exception {
        SceneSettings s = new SceneSettings();
        SimClock c = new SimClock();
        Renderer r = rig(s, c, "2026-09-23T08:00:00Z", null, 9);
        r.camera().setAutoSpin(0);
        c.setPaused(true);
        for (int i = 0; i < 60; i++) r.render(W, H, 1 / 15.0);
        Renderer.Frame f = r.render(W, H, 1 / 30.0);
        Renderer.Pick t = pick(f, "Titania");
        check("Titania im Bild", t != null, "");
        if (t == null) return;
        s.mouseX = t.x; s.mouseY = t.y;
        r.render(W, H, 1 / 30.0);
        boolean early = r.hoverShown() == null;
        Thread.sleep(120);
        r.render(W, H, 1 / 30.0);
        early &= r.hoverShown() == null;
        Thread.sleep(260);
        r.render(W, H, 1 / 30.0);
        Renderer.HoverInfo h = r.hoverShown();
        check("Karte erscheint erst nach 0,3 s Verweilen", early && h != null && "Titania".equals(h.title), h == null ? "keine Karte" : h.title);
        if (h != null) {
            check("… und liegt ganz im Bild", h.x >= 0 && h.y >= 0 && h.x + h.w <= W && h.y + h.h <= H,
                    String.format(Locale.GERMAN, "%.0f, %.0f, %.0f × %.0f", h.x, h.y, h.w, h.h));
            double[] km = new double[3];
            Body ti = UranusSystem.builtIn().find("Titania");
            Kepler.position(ti.orbit, TimeScale.tdb(c.jd()), km);
            double dist = Math.sqrt(km[0] * km[0] + km[1] * km[1] + km[2] * km[2]);
            String line = h.lines.stream().filter(l -> l.startsWith("Abstand jetzt")).findFirst().orElse("");
            double shown = Double.parseDouble(line.replaceAll("^Abstand jetzt ([0-9.]+) km.*$", "$1").replace(".", ""));
            check("Abstand auf der Karte = gerechneter Abstand", Math.abs(shown - dist) < 1, line + " · gerechnet " + Math.round(dist) + " km");
        }
        s.mouseX = t.x + 3; s.mouseY = t.y + 3;
        r.render(W, H, 1 / 30.0);
        check("Bewegt sich die Maus, verschwindet die Karte bis zum nächsten Verweilen", r.hoverShown() == null, "");
        s.mouseDown = true;
        Thread.sleep(350);
        r.render(W, H, 1 / 30.0);
        check("Mit gedrückter Taste (Kamera drehen) keine Karte", r.hoverShown() == null, "");
        s.mouseDown = false;

        // Ringe im Gitterraum (Uranus liegt dann in der Ebene y = 0)
        SceneSettings g = new SceneSettings();
        SimClock gc = new SimClock();
        Renderer rg = rig(g, gc, "2026-09-23T08:00:00Z", x -> x.spaceMode = 1, 7);
        rg.camera().setAutoSpin(0);
        rg.camera().setOrientation(0.3, 1.0);
        gc.setPaused(true);
        for (int i = 0; i < 60; i++) rg.render(W, H, 1 / 15.0);
        rg.render(W, H, 1 / 30.0);
        Ring eps = null;
        for (Ring x : UranusSystem.builtIn().rings()) if (x.name.equals("ε")) eps = x;
        double[] p = new double[3], q = new double[3];
        boolean hit = false, gapFree = false;
        String info = "";
        double tdb = TimeScale.tdb(gc.jd());
        for (int k = 0; k < 24 && !hit; k++) {
            double th = k * Math.PI / 12, rr = eps.radiusRuAt(th, eps.periapsis(tdb)) * SpaceMap.PR, rg2 = 2.9 * SpaceMap.PR;
            if (!rg.camera().project(Math.cos(th) * rr, 0, Math.sin(th) * rr, p) || !rg.camera().project(Math.cos(th) * rg2, 0, Math.sin(th) * rg2, q)) continue;
            if (p[0] < 20 || p[1] < 70 || p[0] > W - 20 || p[1] > H - 20 || q[0] < 20 || q[1] < 70 || q[0] > W - 20 || q[1] > H - 20) continue;
            if (Math.hypot(p[0] - q[0], p[1] - q[1]) < 40) continue;
            Renderer.HoverInfo hr = rg.probe(p[0], p[1]), hg = rg.probe(q[0], q[1]);
            if (hr == null || !"Ring ε".equals(hr.title)) continue;
            hit = true;
            gapFree = hg == null || !"ring".equals(hg.kind);
            info = hr.lines.get(0) + " · daneben (2,9 R): " + (hg == null ? "nichts" : hg.title);
        }
        check("Ring ε trifft auf dem Ring, daneben kein Ring", hit && gapFree, info);
        // Uranus: Sonnenwende auf der Karte = Maximum der Sonnenbreite aus der Ephemeride
        double[] u = new double[3];
        rg.camera().project(0, 0, 0, u);
        Renderer.HoverInfo hu = rg.probe(u[0], u[1]);
        double best = -1, bestJd = 0, jd0 = SimClock.jdOf(Instant.parse("2026-09-23T00:00:00Z"));
        for (double jd = jd0; jd < jd0 + 365.25 * 8; jd += 0.25) {
            double lat = Math.abs(Ephemeris.sunDirWorld(jd)[1]);
            if (lat > best) { best = lat; bestJd = jd; }
        }
        String want = SimClock.instantOf(bestJd).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String want2 = SimClock.instantOf(bestJd + 1).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String want0 = SimClock.instantOf(bestJd - 1).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String line = hu == null ? "" : hu.lines.stream().filter(l -> l.startsWith("Als Nächstes")).findFirst().orElse("");
        check("Uranus-Karte: nächste Sonnenwende wie aus der Ephemeride gerechnet", hu != null && "Uranus".equals(hu.title)
                && (line.contains(want) || line.contains(want2) || line.contains(want0)), line + " · Ephemeride " + want);
    }

    // ------------------------------------------------------------------ Zeitleisten und Sonde

    static void tour(Renderer r, SimClock c, SceneSettings s, String code, double seconds) {
        s.tourRequest = code;
        double dt = 1 / 15.0;
        for (double t = 0; t < seconds; t += dt) { c.advance(dt); r.render(400, 225, dt); }
        r.render(W, H, 1 / 30.0);
    }

    static void timelines() {
        SceneSettings s = new SceneSettings();
        SimClock c = new SimClock();
        Renderer r = rig(s, c, "2026-09-23T00:00:00Z", null, 0);
        tour(r, c, s, "ENTDECKUNG", 40);
        Renderer.HoverInfo found = null;
        for (int y = H - 170; y < H && found == null; y += 2)
            for (int x = 0; x < W && found == null; x += 2) {
                Renderer.HoverInfo h = r.probe(x, y);
                if (h != null && "zeitleiste".equals(h.kind)) found = h;
            }
        check("Entdeckungen: Punkte der Zeitleiste zeigen eine Karte", found != null && found.lines.size() >= 2,
                found == null ? "keine" : found.title + " · " + found.lines.get(0));
        SceneSettings s2 = new SceneSettings();
        SimClock c2 = new SimClock();
        Renderer r2 = rig(s2, c2, "2026-09-23T00:00:00Z", null, 0);
        tour(r2, c2, s2, "VOYAGER", 30);
        double[] cw = r2.craftWorld(), p = new double[3];
        Renderer.HoverInfo hc = cw != null && r2.camera().project(cw[0], cw[1], cw[2], p) ? r2.probe(p[0], p[1]) : null;
        check("Voyager 2: Karte mit Abstand, Tempo und Zeit zur Annäherung", hc != null && "sonde".equals(hc.kind) && hc.lines.get(1).contains("zur größten Annäherung"),
                hc == null ? "keine" : hc.lines.get(0) + " · " + hc.lines.get(1));
    }

    // ------------------------------------------------------------------ Hilfe, Symbole, Fehlerzeile

    static void helpAndIcons() {
        SceneSettings s = new SceneSettings();
        SimClock c = new SimClock();
        Renderer r = rig(s, c, "2026-09-23T00:00:00Z", null, 0);
        Renderer.Frame f = r.render(W, H, 1 / 30.0);
        check("Symbole Standbild und Hilfe oben rechts", f.overlay.containsText("?") && r.helpButton() == null, "");
        s.help = true;
        f = r.render(W, H, 1 / 30.0);
        double[] b = r.helpButton();
        check("F1: Hilfetafel mit Knopf „Auf Standard zurück“", f.overlay.containsText("Bedienung") && f.overlay.containsText("Auf Standard zurück")
                && b != null && b[0] > 0 && b[1] > 0 && b[0] + b[2] < W && b[1] + b[3] < H, b == null ? "" : String.format(Locale.GERMAN, "Knopf bei %.0f, %.0f", b[0], b[1]));
        s.playLive = true;
        r.render(W, H, 1 / 30.0);
        f = r.render(W, H, 1 / 30.0);
        check("Hilfe passt sich an: in der Spielwiese die Griffe der Spielwiese", f.overlay.containsText("Umschalt + Ziehen") && f.overlay.containsText("Strg+Z"), "");
        s.playStop = true; s.help = false;
        r.render(W, H, 1 / 30.0);
        f = r.render(W, H, 1 / 30.0);
        check("Hilfe zu: kein Knopf mehr", r.helpButton() == null && !f.overlay.containsText("Auf Standard zurück"), "");
        s.renderError = "IllegalStateException: Probe";
        f = r.render(W, H, 1 / 30.0);
        check("Fehler im Render-Thread erscheinen als Zeile im Bild", f.overlay.containsText("Fehler beim Zeichnen: IllegalStateException: Probe"), "");
        s.renderError = null;
        // Karte über einem Symbol bleibt im Bild
        double[] ic = Renderer.iconRect(1, W);
        Renderer.HoverInfo h = r.probe(ic[0] + ic[2] / 2, ic[1] + ic[3] / 2);
        check("Überfahren des Symbols „?“: Karte Hilfe", h != null && "Hilfe".equals(h.title), h == null ? "" : h.title);
        List<java.awt.Image> icons = UranusApp.icons();
        BufferedImage i16 = (BufferedImage) icons.get(0), i256 = (BufferedImage) icons.get(icons.size() - 1);
        check("Fenstersymbol in 9 Größen, 16 bis 256 px, Mitte gefüllt", icons.size() == 9 && i16.getWidth() == 16 && i256.getWidth() == 256
                && (i256.getRGB(128, 128) >>> 24) > 200 && (i256.getRGB(2, 2) >>> 24) == 0, "");
    }

    // ------------------------------------------------------------------ Standbild

    static void still(File tmp) throws Exception {
        SceneSettings s = new SceneSettings();
        SimClock c = new SimClock();
        Renderer r = rig(s, c, "2026-09-23T00:00:00Z", null, 0);
        for (int i = 0; i < 20; i++) r.render(W, H, 1 / 15.0);
        s.help = true;
        s.mouseX = 700; s.mouseY = 400;
        Renderer.Frame f = r.renderStill(2 * W, 2 * H, 0.5);
        BufferedImage img = f.composite(2);
        check("Standbild in doppelter Größe", img.getWidth() == 2 * W && img.getHeight() == 2 * H, img.getWidth() + " × " + img.getHeight());
        check("… ohne Symbole, Hilfe, Karte am Zeiger und Bildzeit", !f.overlay.containsText("?") && !f.overlay.containsText("Bedienung")
                && !f.overlay.containsText(" ms · ") && r.hoverShown() == null, "");
        check("… mit Beschriftung (Datum)", f.overlay.containsText("2026"), "");
        s.help = false;
        Map<String, String> info = r.snapshotInfo();
        System.setProperty("uranus.bilder", new File(tmp, "Bilder").getPath());
        File dir = Snapshot.folder();
        File f1 = Snapshot.write(img, info, dir), f2 = Snapshot.write(img, info, dir);
        Map<String, String> back = Snapshot.readInfo(f1);
        BufferedImage read = javax.imageio.ImageIO.read(f1);
        check("PNG geschrieben, Name nach dem Datum der Szene, zweites Bild eigener Name", f1.isFile() && f2.isFile() && !f1.equals(f2)
                && f1.getName().startsWith("Uranus_2026-09-23_") && f2.getName().endsWith("_2.png"), f1.getName() + " · " + f2.getName());
        check("Textfelder im PNG lesbar (Titel, Szene, Einstellungen)", back.getOrDefault("Title", "").startsWith("Uranus · 23. September 2026")
                && back.getOrDefault("Szene", "").startsWith("2026-09-23") && back.getOrDefault("Comment", "").contains("Mulde")
                && back.getOrDefault("Description", "").contains("Sonne über"), back.get("Title") + " · " + back.get("Comment"));
        check("Bild im PNG unverändert", read.getWidth() == img.getWidth() && diff(read, img)[1] == 0, "");
        check("Ordner aus -Duranus.bilder", dir.equals(new File(tmp, "Bilder")), dir.getPath());
    }

    // ------------------------------------------------------------------ Gerätepixel

    static void deviceScale() throws Exception {
        SceneSettings s = new SceneSettings();
        SimClock c = new SimClock();
        Renderer r = rig(s, c, "2026-09-23T08:00:00Z", null, 9);
        r.camera().setAutoSpin(0);
        s.deviceScale = 1.5;
        Renderer.Frame[] got = new Renderer.Frame[1];
        RenderLoop loop = new RenderLoop(r, c, s, new RenderLoop.SizeSource() {
            @Override public int width() { return 800; }
            @Override public int height() { return 450; }
        }, fr -> { synchronized (got) { got[0] = fr; } });
        loop.start();
        Thread.sleep(2500);
        loop.stop();
        Thread.sleep(300);
        Renderer.Frame f;
        synchronized (got) { f = got[0]; }
        check("Skalierung 1,5: gerechnet in Gerätepixeln", f != null && f.image.getWidth() == 1200 && f.image.getHeight() == 675,
                f == null ? "kein Bild" : f.image.getWidth() + " × " + f.image.getHeight() + " für ein Fenster von 800 × 450");
        if (f == null) return;
        int good = 0, n = 0;
        for (Renderer.Pick p : f.picks) {
            n++;
            Renderer.HoverInfo h = r.probe(p.x * f.toView, p.y * f.toView);
            if (h != null && p.body.name.equals(h.title)) good++;
        }
        check("… Klickziele und Karten treffen weiter dieselben Monde", n > 0 && good == n, good + " von " + n);
        // Auflösungsregelung bei Skalierung 2: geregelt wird der Anteil an den Gerätepixeln (0,5 … 1)
        SceneSettings s2 = new SceneSettings();
        SimClock c2 = new SimClock();
        Renderer r2 = rig(s2, c2, "2026-09-23T08:00:00Z", null, 0);
        s2.adaptiveScale = true;
        s2.deviceScale = 2;
        double[] lo = {9, -9};
        RenderLoop l2 = new RenderLoop(r2, c2, s2, new RenderLoop.SizeSource() {
            @Override public int width() { return 1480; }
            @Override public int height() { return 780; }
        }, fr -> { synchronized (lo) { lo[0] = Math.min(lo[0], s2.renderScale); lo[1] = Math.max(lo[1], s2.renderScale); } });
        l2.start();
        Thread.sleep(6000);
        l2.stop();
        Thread.sleep(300);
        synchronized (lo) {
            check("Auflösungsregelung bei Skalierung 2 bleibt zwischen 50 und 100 % der Gerätepixel", lo[0] >= 0.5 - 1e-9 && lo[1] <= 1 + 1e-9,
                    String.format(Locale.GERMAN, "%.0f … %.0f %%", lo[0] * 100, lo[1] * 100));
        }
    }

    // ------------------------------------------------------------------ Einstellungen

    static void prefs() throws Exception {
        Map<String, String> before = realPrefs();
        String node = "com/dan/uranus-pruefung-" + System.nanoTime();
        AppPrefs p = new AppPrefs(node);
        AppPrefs.Values v = new AppPrefs.Values();
        v.space = 1; v.scale = 1; v.moons = 2; v.speed = 4; v.playSpeed = 3;
        v.trails = false; v.axis = true; v.magneto = false; v.labels = false; v.geodesics = true;
        v.window = new java.awt.Rectangle(40, 30, 1200, 800);
        p.save(v);
        AppPrefs.Values back = new AppPrefs(node).load();
        check("Einstellungen: gespeichert und beim nächsten Start gleich", back.equals(v), p.path());
        p.clear();
        check("„Auf Standard zurück“ löscht alles", new AppPrefs(node).load().equals(new AppPrefs.Values()), "");
        p.putRaw("raum", "7"); p.putRaw("bahnen", "vielleicht"); p.putRaw("fenster.x", "10"); p.putRaw("fenster.y", "10");
        p.putRaw("fenster.b", "12"); p.putRaw("fenster.h", "zwölf"); p.putRaw("tempo", "-3");
        AppPrefs.Values broken = new AppPrefs(node).load();
        check("Kaputte Werte führen zum Standard, nicht zum Absturz", broken.equals(new AppPrefs.Values()), "");
        p.removeNode();
        check("Deine eigenen Einstellungen (" + AppPrefs.NODE + ") blieben unberührt", before.equals(realPrefs()), before.size() + " Werte");
    }

    static Map<String, String> realPrefs() throws Exception {
        Map<String, String> m = new TreeMap<>();
        if (!Preferences.userRoot().nodeExists(AppPrefs.NODE)) return m;
        Preferences n = Preferences.userRoot().node(AppPrefs.NODE);
        for (String k : n.keys()) m.put(k, n.get(k, ""));
        return m;
    }

    // ------------------------------------------------------------------ Auslieferung

    static void delivery(File tmp) throws Exception {
        File work = new File(tmp, "arbeit"), jar = new File(tmp, "projekt/dist"), proj = new File(tmp, "projekt"), home = new File(tmp, "home/.uranus");
        for (File d : new File[]{work, jar, home}) d.mkdirs();
        List<File> l = UranusDb.searchPlaces(null, work, jar, home);
        check("Verbindungsdatei: Suche Arbeitsordner, neben der jar, Projektordner, .uranus",
                l.size() == 4 && l.get(0).getParentFile().getName().equals("arbeit") && l.get(1).getParentFile().getName().equals("dist")
                        && l.get(2).getParentFile().getName().equals("projekt") && l.get(3).getParentFile().getName().equals(".uranus"),
                l.stream().map(f -> f.getParentFile().getName()).reduce((x, y) -> x + " → " + y).orElse(""));
        check("-Duranus.db gilt allein", UranusDb.searchPlaces("x/y.properties", work, jar, home).size() == 1, "");
        Files.write(new File(home, UranusDb.FILE).toPath(), "url=jdbc:oracle:thin:@//h:1/S\nuser=U\npassword=p1\n".getBytes(StandardCharsets.UTF_8));
        check("… gefunden unter .uranus, wenn sonst keine da ist", first(l) != null && first(l).getParentFile().getName().equals(".uranus"), "");
        Files.write(new File(proj, UranusDb.FILE).toPath(), "url=jdbc:oracle:thin:@//h:1/S\nuser=U\npassword=p2\n".getBytes(StandardCharsets.UTF_8));
        check("… der Projektordner (über dist) kommt vor .uranus", first(l) != null && first(l).getParentFile().getName().equals("projekt")
                && UranusDb.read(first(l)) != null, "");
        // Protokoll: Wechsel bei 1 MB
        File logDir = new File(tmp, "log");
        AppLog.init(logDir);
        String line = "x".repeat(990);
        for (int i = 0; i < 1100; i++) AppLog.info(line);
        File log = new File(logDir, "uranus.log"), old = new File(logDir, "uranus.log.1");
        check("Protokoll wechselt bei 1 MB auf eine Vorgängerdatei", old.isFile() && log.length() < AppLog.MAX_BYTES && old.length() >= AppLog.MAX_BYTES,
                String.format(Locale.GERMAN, "uranus.log %,d B · uranus.log.1 %,d B", log.length(), old.length()));
        // dist ohne Kennwort (nur wenn NetBeans schon gebaut hat)
        File dist = new File("dist");
        if (dist.isDirectory()) {
            boolean clean = !new File(dist, UranusDb.FILE).exists();
            File jf = new File(dist, "Uranus.jar");
            if (jf.isFile()) try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(jf)) { clean &= z.getEntry(UranusDb.FILE) == null; }
            check("dist enthält keine Verbindungsdatei (auch nicht in Uranus.jar)", clean, "");
        } else System.out.println("[--]     dist fehlt (noch kein „Clean and Build“) — Prüfung übersprungen");
        // README
        File readme = new File("README.md");
        String rd = readme.isFile() ? new String(Files.readAllBytes(readme.toPath()), StandardCharsets.UTF_8) : "";
        boolean sections = rd.contains("## Starten") && rd.contains("## Bedienung") && rd.contains("## Datenbank") && rd.contains("## Prüfen")
                && rd.contains("## Aufbau") && rd.contains("## Quellen");
        boolean noPwd = !rd.matches("(?s).*password=(?!\\.\\.\\.|…)\\S+.*");
        check("README.md: Starten, Bedienung, Datenbank, Prüfen, Aufbau, Quellen — ohne Kennwort", sections && noPwd, readme.isFile() ? rd.length() + " Zeichen" : "fehlt");
    }

    static File first(List<File> l) {
        for (File f : l) if (f.isFile()) return f;
        return null;
    }
}
