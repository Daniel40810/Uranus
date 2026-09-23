import com.dan.uranus.model.*;
import com.dan.uranus.physics.*;
import com.dan.uranus.render.*;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Headless-Prüfungen für Phase 1–3: Ephemeride, Kepler, Maßstab, Datensatz, Bilder.
 * Aufruf: java -Djava.awt.headless=true -cp build/classes;tools UranusTests
 */
public class UranusTests {

    static int ok, bad;

    static void check(String name, boolean c, String info) {
        if (c) ok++; else bad++;
        System.out.println((c ? "[ok]     " : "[FEHLER] ") + name + (info.isEmpty() ? "" : " — " + info));
    }

    static double jd(String iso) { return SimClock.jdOf(Instant.parse(iso)); }

    public static void main(String[] a) {
        Locale.setDefault(Locale.GERMANY);
        ephemeris();
        kepler();
        spaceMap();
        dataset();
        shadows();
        images();
        System.out.println();
        System.out.println("Ergebnis: " + ok + " ok, " + bad + " Fehler");
        System.exit(bad == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Ephemeride
    static void ephemeris() {
        double[] x = Ephemeris.X_W, y = Ephemeris.Y_W, z = Ephemeris.Z_W;
        double ortho = Math.abs(Ephemeris.dot(x, y)) + Math.abs(Ephemeris.dot(y, z)) + Math.abs(Ephemeris.dot(x, z));
        double[] xy = Ephemeris.cross(x, y);
        check("Weltbasis orthonormal und rechtshändig", ortho < 1e-12 && Ephemeris.dot(xy, z) > 0.999999, String.format("Summe der Skalarprodukte %.1e", ortho));

        // Tagundnachtgleiche: Vorzeichenwechsel der Unterpunktbreite
        double prev = Ephemeris.subsolarLatitudeDeg(jd("2005-01-01T00:00:00Z")), eq = Double.NaN;
        for (double t = jd("2005-01-01T00:00:00Z"); t < jd("2010-01-01T00:00:00Z"); t += 0.5) {
            double l = Ephemeris.subsolarLatitudeDeg(t);
            if (prev < 0 && l >= 0) { eq = t; break; }
            prev = l;
        }
        double off = eq - jd("2007-12-07T00:00:00Z");
        check("Tagundnachtgleiche Dezember 2007", Math.abs(off) < 3, String.format("gerechnet %s (Abweichung %.1f Tage)", SimClock.instantOf(eq), off));

        double v = Ephemeris.subsolarLatitudeDeg(jd("1986-01-24T17:59:00Z"));
        check("Voyager 2, 1986: Südpol zur Sonne", v < -80 && v > -83, String.format("%.2f°", v));

        double best = -99, when = 0;
        for (double t = jd("2025-01-01T00:00:00Z"); t < jd("2036-01-01T00:00:00Z"); t += 2) {
            double l = Ephemeris.subsolarLatitudeDeg(t);
            if (l > best) { best = l; when = t; }
        }
        String yr = SimClock.instantOf(when).toString().substring(0, 4);
        check("Nächste Sonnenwende: Nordpol 82° zur Sonne", best > 81.5 && best < 82.6 && yr.equals("2030"), String.format("%.2f° am %s", best, SimClock.instantOf(when).toString().substring(0, 10)));

        double d = Ephemeris.sunDistanceAu(jd("2026-09-22T12:00:00Z"));
        check("Sonnenabstand 2026", d > 19.3 && d < 19.6, String.format("%.3f AE", d));

        double s0 = Ephemeris.spinAngle(1e4 + SimClock.JD_J2000), s1 = Ephemeris.spinAngle(1e4 + SimClock.JD_J2000 + UranusSystem.ROTATION_DAYS);
        double ds = Math.abs(Math.IEEEremainder(s1 - s0, Math.PI * 2));
        check("Rotation: nach 17 h 14 min wieder gleich", ds < 0.01, String.format("Rest %.4f rad", ds));
        double s2 = Ephemeris.spinAngle(1e4 + SimClock.JD_J2000 + 0.1);
        check("Rotation im Drehsinn der Monde (Winkel wächst)", Math.IEEEremainder(s2 - s0, Math.PI * 2) > 0, "");
    }

    // ------------------------------------------------------------------ Kepler
    static void kepler() {
        double worst = 0;
        for (double e : new double[]{0, 0.1, 0.3, 0.52, 0.66, 0.9})
            for (int k = 0; k < 400; k++) {
                double m = -Math.PI + k * (2 * Math.PI / 400);
                double ea = Kepler.solveE(m, e);
                worst = Math.max(worst, Math.abs(Math.IEEEremainder(ea - e * Math.sin(ea) - m, 2 * Math.PI)));
            }
        check("Kepler-Gleichung gelöst (e bis 0,9)", worst < 1e-12, String.format("größter Rest %.1e", worst));

        UranusSystem sys = UranusSystem.builtIn();
        Body mir = sys.find("Miranda");
        double[] p = new double[3];
        double rmin = 1e99, rmax = 0;
        for (int k = 0; k < 600; k++) {
            Kepler.position(mir.orbit, SimClock.JD_J2000 + 9000 + k * mir.orbit.periodDays * 3 / 600, p);
            double r = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
            rmin = Math.min(rmin, r); rmax = Math.max(rmax, r);
        }
        double swing = (rmax - rmin) / (rmax + rmin);
        check("Miranda: Abstandsschwankung = Exzentrizität", Math.abs(swing - mir.orbit.e) < 2e-4, String.format("%.5f gegen e = %.4f", swing, mir.orbit.e));

        double[] q = new double[3];
        Body syc = sys.find("Sycorax");
        Kepler.position(syc.orbit, SimClock.JD_J2000 + 5000, p);
        Kepler.position(syc.orbit, SimClock.JD_J2000 + 5000 + syc.orbit.periodDays, q);
        double dd = Math.sqrt(Math.pow(p[0] - q[0], 2) + Math.pow(p[1] - q[1], 2) + Math.pow(p[2] - q[2], 2));
        check("Sycorax nach einem Umlauf am selben Ort", dd / syc.orbit.aKm < 1e-9, String.format("Abweichung %.2e a", dd / syc.orbit.aKm));

        Body ariel = sys.find("Ariel");
        Kepler.position(ariel.orbit, SimClock.JD_J2000 + 8000, p);
        Kepler.position(ariel.orbit, SimClock.JD_J2000 + 8000.1, q);
        double da = Math.IEEEremainder(Math.atan2(q[2], q[0]) - Math.atan2(p[2], p[0]), 2 * Math.PI);
        check("Ariel läuft im Drehsinn des Planeten", da > 0 && Math.abs(p[1]) / ariel.orbit.aKm < 0.01, String.format("Winkelschritt %.4f rad", da));

        // Rückläufig gegen die Ekliptik: Drehimpuls zeigt gegen die Ekliptiknormale
        double[] en = new double[3], icrf = new double[3];
        Ephemeris.eclipticToIcrf(0, 0, 1, icrf);
        Ephemeris.icrfToWorld(icrf, en);
        Kepler.position(syc.orbit, SimClock.JD_J2000 + 5000, p);
        Kepler.position(syc.orbit, SimClock.JD_J2000 + 5000.5, q);
        double[] l = Ephemeris.cross(p, new double[]{q[0] - p[0], q[1] - p[1], q[2] - p[2]});
        double cos = Ephemeris.dot(Ephemeris.normalize(l), en);
        check("Sycorax rückläufig (Drehimpuls gegen Ekliptiknormale)", Math.abs(Math.acos(cos) * 180 / Math.PI - syc.orbit.iDeg) < 0.01, String.format("Neigung aus der Bahn %.2f°", Math.acos(cos) * 180 / Math.PI));
    }

    // ------------------------------------------------------------------ Maßstab
    static void spaceMap() {
        boolean mono = true, cont = true;
        for (double s : new double[]{0, 0.3, 0.7, 1}) {
            double prev = -1;
            for (double r = 0; r < 1000; r += 0.01) {
                double m = SpaceMap.map(r, s);
                if (m <= prev) mono = false;
                prev = m;
            }
        }
        for (double r : new double[]{3.9, 30}) {
            // stetig: der Sprung schrumpft mit dem Abstand (an 3,9 R wurzelförmig)
            double j1 = Math.abs(SpaceMap.compressed(r + 1e-6) - SpaceMap.compressed(r - 1e-6));
            double j2 = Math.abs(SpaceMap.compressed(r + 1e-10) - SpaceMap.compressed(r - 1e-10));
            if (j1 > 3e-3 || j2 > 3e-5 || j2 >= j1) cont = false;
        }
        check("Maßstab streng monoton (alle Überblendungen)", mono, "");
        check("Maßstab stetig an den Nahtstellen 3,9 R und 30 R", cont, "");
        check("Maßstabsgetreu ist linear", Math.abs(SpaceMap.map(22.83, 1) - SpaceMap.PR * 22.83) < 1e-12, "");
        double oberon = SpaceMap.compressed(583_500 / UranusSystem.URANUS_RADIUS_KM), ferd = SpaceMap.compressed(20_901_000 / UranusSystem.URANUS_RADIUS_KM);
        check("Gestaucht passt alles in ein Bild", oberon < 16 && ferd < 40, String.format("Oberon %.1f, Ferdinand %.1f Welteinheiten (echt: %.0f / %.0f)", oberon, ferd,
                SpaceMap.real(583_500 / UranusSystem.URANUS_RADIUS_KM), SpaceMap.real(20_901_000 / UranusSystem.URANUS_RADIUS_KM)));
        check("Ringe bleiben unverzerrt (ε bei 2,0 R)", Math.abs(SpaceMap.compressed(2.0) - 2.0 * SpaceMap.PR) < 1e-12, "");
    }

    // ------------------------------------------------------------------ Datensatz
    static void dataset() {
        UranusSystem sys = UranusSystem.builtIn();
        int maj = 0, inn = 0, irr = 0;
        for (Body b : sys.bodies()) { if (b.group == BodyGroup.MAJOR) maj++; else if (b.group == BodyGroup.INNER) inn++; else irr++; }
        check("29 Monde: 5 Haupt-, 14 innere, 10 irreguläre", maj == 5 && inn == 14 && irr == 10, maj + "/" + inn + "/" + irr);
        check("13 Ringe", sys.rings().size() == 13, "");
        double pc = sys.find("Cordelia").orbit.periodDays, pp = sys.find("Puck").orbit.periodDays;
        check("Umlaufzeiten aus dem 3. Kepler-Gesetz (Cordelia, Puck)", Math.abs(pc - 0.335) < 0.006 && Math.abs(pp - 0.762) < 0.01, String.format("%.3f d / %.3f d", pc, pp));
        double pm = UranusSystem.periodFromA(129_390);
        check("3. Kepler-Gesetz trifft Miranda auf 1 %", Math.abs(pm / 1.413479 - 1) < 0.01, String.format("%.4f d statt 1,4135 d", pm));
        int retro = 0;
        for (Body b : sys.bodies()) if (b.group == BodyGroup.IRREGULAR && b.orbit.isRetrograde()) retro++;
        check("Irreguläre: 9 rückläufig, Margaret rechtläufig", retro == 9 && !sys.find("Margaret").orbit.isRetrograde(), "");
        int fit = 0, mean = 0;
        for (Body b : sys.bodies()) { if (b.quality == OrbitQuality.FIT) fit++; else mean++; }
        check("Bahnen gemessen: 28 an Horizons angeglichen, 1 JPL-Mittelwert (S/2025 U1)", fit == 28 && mean == 1
                && sys.find("S/2025 U1").quality == OrbitQuality.MEAN, fit + " / " + mean);
    }

    // ------------------------------------------------------------------ Mondschatten
    /** Fällt der Schatten eines Hauptmondes irgendwann innerhalb eines Umlaufs auf die Scheibe? */
    static int transits(double jd0, double scale) {
        UranusSystem sys = UranusSystem.builtIn();
        double[] sun = Ephemeris.sunDirWorld(jd0), km = new double[3], w = new double[3];
        int hits = 0;
        for (Body b : sys.bodies()) {
            if (b.group != BodyGroup.MAJOR) continue;
            for (int k = 0; k < 400; k++) {
                double t = jd0 + b.orbit.periodDays * k / 400;
                Kepler.position(b.orbit, t, km);
                SpaceMap.toWorld(km, scale, w);
                double tp = Ephemeris.dot(w, sun);
                if (tp <= 0) continue;                   // Mond muss zwischen Sonne und Planet stehen
                double d2 = Ephemeris.dot(w, w) - tp * tp;
                if (d2 < SpaceMap.PR * SpaceMap.PR) { hits++; break; }
            }
        }
        return hits;
    }

    static void shadows() {
        int eq = transits(jd("2007-12-07T00:00:00Z"), 1), now = transits(jd("2026-09-22T00:00:00Z"), 1);
        check("Mondschatten auf Uranus um die Tagundnachtgleiche 2007", eq >= 4, eq + " von 5 Hauptmonden werfen Schatten");
        check("…und 2026 keiner (Sonne 73° über dem Pol)", now == 0, now + " Schattenwürfe");
    }

    // ------------------------------------------------------------------ Bilder
    static BufferedImage frame(SceneSettings s, String when, double yaw, double pitch) {
        SimClock c = new SimClock();
        c.setJd(jd(when));
        c.setPaused(true);
        Renderer r = new Renderer(UranusSystem.builtIn(), s, c);
        r.camera().setOrientation(yaw, pitch);
        r.settle();
        Renderer.Frame f = null;
        for (int i = 0; i < 3; i++) f = r.render(800, 450, 1 / 60.0);
        return f.image;
    }

    static int diff(BufferedImage a, BufferedImage b) {
        int n = 0;
        for (int y = 0; y < a.getHeight(); y += 2) for (int x = 0; x < a.getWidth(); x += 2) {
            int p = a.getRGB(x, y), q = b.getRGB(x, y);
            int d = Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) + Math.abs((p & 255) - (q & 255));
            if (d > 24) n++;
        }
        return n;
    }

    static void images() {
        SceneSettings s = new SceneSettings();
        BufferedImage mulde = frame(s, "2026-09-22T21:30:00Z", 0.55, 0.42);
        // Uranus sitzt in der Bildmitte (Kamera zielt auf ihn): Cyan, blau > rot
        int best = 0; int col = 0;
        for (int y = 150; y < 300; y++) for (int x = 330; x < 470; x++) {
            int p = mulde.getRGB(x, y), r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            if (g + b > best && b > r + 25 && g > r + 25) { best = g + b; col = p; }
        }
        check("Uranus ist cyan", best > 200, String.format("hellster Cyan-Punkt #%06X", col & 0xFFFFFF));

        s.spaceMode = 1;
        BufferedImage gitter = frame(s, "2026-09-22T21:30:00Z", 0.55, 0.42);
        int dg = diff(mulde, gitter);
        check("Gitterraum sieht anders aus als die Mulde", dg > 2000, dg + " abweichende Stichproben");

        // Linse: Blick durch Uranus auf das galaktische Zentrum
        double[] gc = new double[3];
        Ephemeris.icrfToWorld(Ephemeris.radec(266.40510, -28.93617), gc);
        double yaw = Math.atan2(-gc[0], -gc[2]), pitch = Math.asin(-gc[1]);
        s = new SceneSettings();
        s.trails = false;
        BufferedImage lensOn = frame(s, "2026-09-22T21:30:00Z", yaw, pitch);
        s.lens = false;
        BufferedImage lensOff = frame(s, "2026-09-22T21:30:00Z", yaw, pitch);
        int dl = diff(lensOn, lensOff);
        check("Gravitationslinse verändert den Hintergrund", dl > 300, dl + " abweichende Stichproben");

        s = new SceneSettings();
        s.bloom = false;
        BufferedImage noBloom = frame(s, "2026-09-22T21:30:00Z", 0.55, 0.42);
        int db = diff(mulde, noBloom);
        check("Bloom wirkt (hell glühende Stellen)", db > 20, db + " abweichende Stichproben");

        // Leistung (Container, 2 Kerne): Mittel über 20 Bilder in 1280 × 720
        SimClock c = new SimClock();
        Renderer r = new Renderer(UranusSystem.builtIn(), new SceneSettings(), c);
        r.settle();
        for (int i = 0; i < 10; i++) r.render(1280, 720, 1 / 60.0);
        long t0 = System.nanoTime();
        for (int i = 0; i < 20; i++) r.render(1280, 720, 1 / 60.0);
        double ms = (System.nanoTime() - t0) / 1e6 / 20;
        check("Bildzeit 1280 × 720 gemessen", ms < 200, String.format("%.1f ms je Bild auf %d Kernen", ms, Runtime.getRuntime().availableProcessors()));

        // Mausauswahl: Titania ist anklickbar
        Renderer.Frame f = r.render(1280, 720, 1 / 60.0);
        List<Renderer.Pick> picks = f.picks;
        boolean titania = picks.stream().anyMatch(p -> p.body.name.equals("Titania"));
        check("Hauptmonde anklickbar", titania && picks.size() >= 5, picks.size() + " anklickbare Monde");
    }
}
