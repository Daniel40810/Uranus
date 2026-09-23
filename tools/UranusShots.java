import com.dan.uranus.model.*;
import com.dan.uranus.physics.Ephemeris;
import com.dan.uranus.render.*;

import javax.imageio.ImageIO;
import java.io.File;
import java.time.Instant;
import java.util.function.Consumer;

/** Rendert Standbilder ohne Fenster: java -cp build/classes;build/tools UranusShots [Zielordner] [nur-mit-Namensteil] */
public class UranusShots {

    static File dir;
    static String only = "";

    public static void main(String[] a) throws Exception {
        dir = new File(a.length > 0 ? a[0] : "shots");
        if (a.length > 1) only = a[1];
        dir.mkdirs();
        String now = "2026-09-22T21:30:00Z";
        shot("01_mulde", now, s -> s.magneto = false, 0.55, 0.42, 0, 3, 1 / 60.0);
        shot("02_gitterraum", now, s -> { s.spaceMode = 1; s.magneto = false; }, 0.55, 0.42, 0, 3, 1 / 60.0);
        shot("03_alle_monde", now, s -> { s.moonLevel = 2; s.magneto = false; }, 0.9, 0.55, 0, 3, 1 / 60.0);
        shot("04_echt", now, s -> { s.scaleMode = 1; s.magneto = false; }, 0.55, 0.42, 0, 3, 1 / 60.0);
        shot("05_achse_2007", "2007-12-07T12:00:00Z", s -> { s.moonLevel = 1; s.axis = true; s.magneto = false; }, 0.3, 0.25, 0, 3, 1 / 60.0);
        shot("06_voyager_1986", "1986-01-24T17:59:00Z", s -> { s.moonLevel = 1; s.magneto = false; }, 2.2, -0.35, 0, 3, 1 / 60.0);
        double[] gc = new double[3];
        Ephemeris.icrfToWorld(Ephemeris.radec(266.40510, -28.93617), gc);
        shot("07_linse", now, s -> { s.spaceMode = 1; s.magneto = false; }, Math.atan2(-gc[0], -gc[2]) + 0.02, Math.asin(-gc[1]) + 0.01, 7.5, 3, 1 / 60.0);
        // Phase 4
        shot("08_magnetosphaere", now, s -> { s.trails = false; }, 1.1, 0.10, 40, 40, 1 / 30.0);
        shot("09_polarlicht", now, s -> { s.trails = false; }, 2.0, -0.55, 5.2, 20, 1 / 30.0);
        shot("10_lichtbahnen", now, s -> { s.geodesics = true; s.magneto = false; s.trails = false; }, 0.55, 0.30, 24, 20, 1 / 30.0);
        shot("11_ringflug", now, s -> { s.ringFlight = true; s.magneto = false; }, 0.55, 0.42, 0, 170, 1 / 30.0);
        shot("12_ringflug_hirte", "2026-09-23T03:10:00Z", s -> { s.ringFlight = true; s.magneto = false; s.moonLevel = 1; }, 0.55, 0.42, 0, 170, 1 / 30.0);
        // Phase 6: Geschichten
        tour("13_voyager_anflug", "VOYAGER", 16);
        tour("14_voyager_annaeherung", "VOYAGER", 52.5);
        tour("15_voyager_schatten", "VOYAGER", 66);
        tour("16_jahr_2007", "JAHR", 63);
        tour("17_entdeckung_1851", "ENTDECKUNG", 20.5);
        tour("18_entdeckung_1986", "ENTDECKUNG", 49);
        tour("19_rundflug_miranda", "RUNDFLUG", 28);
        // Phase 7: Spielwiese
        play("20_spielwiese_heute", "HEUTE", 40, s -> s.focus = UranusSystem.builtIn().find("Titania"), 0.9, 0.55, 0);
        play("21_schwere_titania", "TITANIA", 200, null, 0.7, 0.75, 0);
        play("22_miranda_zerbricht", "MIRANDA", 0.34, null, 0.5, 0.6, 9);
        play("23_truemmerring", "MIRANDA", 12, null, 0.5, 0.55, 12);
        play("24_besucher", "BESUCHER", 31, null, 0.5, 0.35, 0);
        play("25_gedraenge", "GEDRAENGE", 400, s -> s.moonLevel = 1, 0.6, 0.6, 16);
        guest("26_gast_wurf");
    }

    static Renderer playRig(String code, SceneSettings s, SimClock c) {
        c.setJd(SimClock.jdOf(Instant.parse("2026-09-23T00:00:00Z")));
        Renderer r = new Renderer(UranusSystem.builtIn(), s, c);
        r.settle();
        if (code == null) s.playLive = true; else s.playScenario = UranusSystem.builtIn().scenario(code);
        r.render(400, 225, 1 / 15.0);
        return r;
    }

    /** Standbild aus der Spielwiese nach days Tagen Spielzeit. */
    static void play(String name, String code, double days, Consumer<SceneSettings> cfg, double yaw, double pitch, double dist) throws Exception {
        if (!name.contains(only)) return;
        SceneSettings s = new SceneSettings();
        s.magneto = false;
        SimClock c = new SimClock();
        Renderer r = playRig(code, s, c);
        if (cfg != null) cfg.accept(s);
        r.camera().setOrientation(yaw, pitch);
        double dt = 1 / 15.0;
        int guard = 0;
        while (r.playground().time() < days * 86_400 - 1 && guard++ < 20_000) {
            double rest = days * 86_400 - r.playground().time();
            s.playRate = Math.min(20, rest / 86_400 / dt);
            r.render(400, 225, dt);
        }
        s.playPaused = true;
        if (dist > 0) r.camera().setDistanceTarget(dist);
        for (int i = 0; i < 40; i++) r.render(400, 225, dt);                 // Kamera kommt an
        for (int i = 0; i < 3; i++) r.render(1600, 900, 1 / 30.0);
        long t0 = System.nanoTime();
        Renderer.Frame f = r.render(1600, 900, 1 / 30.0);
        double ms = (System.nanoTime() - t0) / 1e6;
        ImageIO.write(f.composite(), "png", new File(dir, name + ".png"));
        System.out.printf("%-22s %6.1f ms/Bild · %d Ereignisse%n", name, ms, r.playground().events().size());
    }

    /** Gast setzen: Umschalt + Ziehen, Standbild mit Pfeil und Vorausschau vor dem Loslassen. */
    static void guest(String name) throws Exception {
        if (!name.contains(only)) return;
        SceneSettings s = new SceneSettings();
        s.magneto = false;
        SimClock c = new SimClock();
        Renderer r = playRig(null, s, c);
        r.camera().setOrientation(0.3, 1.0);
        for (int i = 0; i < 40; i++) r.render(1600, 900, 1 / 15.0);
        s.playPaused = true;
        s.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.PRESS, 1060, 470, 0, true, false));
        r.render(1600, 900, 1 / 30.0);
        for (int k = 1; k <= 8; k++) {
            s.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.DRAG, 1060 - k * 6, 470 - k * 22, 0, false, true));
            r.render(1600, 900, 1 / 30.0);
        }
        s.playInput.add(new SceneSettings.PlayInput(SceneSettings.PlayInput.Kind.WHEEL, 1012, 294, -1, false, false));
        r.render(1600, 900, 1 / 30.0);
        Renderer.Frame f = r.render(1600, 900, 1 / 30.0);
        ImageIO.write(f.composite(), "png", new File(dir, name + ".png"));
        System.out.printf("%-22s gesetzt%n", name);
    }

    /** Standbild aus einer laufenden Fahrt: vorspulen in kleiner Auflösung, dann ein Bild in voller Größe. */
    static void tour(String name, String code, double at) throws Exception {
        if (!name.contains(only)) return;
        SceneSettings s = new SceneSettings();
        SimClock c = new SimClock();
        c.setJd(SimClock.jdOf(Instant.parse("2026-09-23T00:00:00Z")));
        Renderer r = new Renderer(UranusSystem.builtIn(), s, c);
        r.settle();
        s.tourRequest = code;
        double dt = 1 / 15.0;
        c.advance(dt);
        r.render(400, 225, dt);
        while (r.activeTour() != null && r.tourTime() < at - 1e-9) {
            double d = Math.min(dt, at - r.tourTime());
            c.advance(d);
            r.render(400, 225, d);
        }
        s.tourPauseRequest = true;                      // Standbild: anhalten, Übergänge ausklingen lassen
        for (int i = 0; i < 3; i++) { c.advance(1 / 30.0); r.render(1600, 900, 1 / 30.0); }
        long t0 = System.nanoTime();
        Renderer.Frame f = r.render(1600, 900, 1 / 30.0);
        double ms = (System.nanoTime() - t0) / 1e6;
        ImageIO.write(f.composite(), "png", new File(dir, name + ".png"));
        System.out.printf("%-22s %6.1f ms/Bild%n", name, ms);
    }

    static void shot(String name, String when, Consumer<SceneSettings> cfg, double yaw, double pitch, double dist,
                     int frames, double dt) throws Exception {
        if (!name.contains(only)) return;
        SceneSettings s = new SceneSettings();
        cfg.accept(s);
        SimClock c = new SimClock();
        c.setJd(SimClock.jdOf(Instant.parse(when)));
        c.setPaused(true);
        Renderer r = new Renderer(UranusSystem.builtIn(), s, c);
        r.camera().setOrientation(yaw, pitch);
        r.settle();
        if (dist > 0) { r.camera().setDistanceTarget(dist); r.camera().snap(); }
        Renderer.Frame f = null;
        for (int i = 0; i < frames; i++) f = r.render(1600, 900, dt);
        long t0 = System.nanoTime();
        f = r.render(1600, 900, dt);
        double ms = (System.nanoTime() - t0) / 1e6;
        ImageIO.write(f.composite(), "png", new File(dir, name + ".png"));
        System.out.printf("%-22s %6.1f ms/Bild%n", name, ms);
    }
}
