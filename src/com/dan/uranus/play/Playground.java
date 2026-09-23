package com.dan.uranus.play;

import com.dan.uranus.model.Body;
import com.dan.uranus.model.BodyGroup;
import com.dan.uranus.model.Scenario;
import com.dan.uranus.model.UranusSystem;
import com.dan.uranus.physics.Ephemeris;
import com.dan.uranus.physics.Kepler;
import com.dan.uranus.physics.TimeScale;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Die Spielwiese: N-Körper-Rechnung ab einem echten oder gespeicherten Stand.
 * <p>
 * Uranus ist Körper 0 und bewegt sich mit; alle Lagen nach außen sind relativ zu ihm (Welt, km).
 * Nach jedem Integratorschritt wird geprüft, ob sich zwei Körper berührt haben (auch zwischen den
 * Schritten, linear eingegrenzt), ob einer unter seine Roche-Grenze geraten ist oder die Hill-Sphäre
 * verlassen hat. Jeder Eingriff legt vorher einen Zwischenstand ab, dazu alle paar Sekunden einer.
 * <p>
 * Nicht thread-sicher: gehört dem Render-Thread.
 */
public final class Playground {

    /** Hill-Radius des Uranus (km): a·(m / 3 M☉)^(1/3) = 2,87·10⁹ km · 0,0244. */
    public static final double HILL_KM = 7.0e7;
    public static final int DEBRIS_PER_BREAKUP = 2500;
    public static final int MAX_SNAPSHOTS = 60;
    /** Mittlere Dichte des Uranus (kg/m³) für die Roche-Grenze. */
    public static final double URANUS_DENSITY = UranusSystem.URANUS_MASS_KG
            / (4.0 / 3.0 * Math.PI * Math.pow(UranusSystem.URANUS_RADIUS_KM * 1000, 3));

    private List<PlayBody> bodies = new ArrayList<>();
    private Ias15 ias;
    private PlayForces forces;
    public final double startJd;
    private final boolean sun;
    private List<Debris> debris = new ArrayList<>();
    private final List<PlayEvent> events = new ArrayList<>();
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private double e0;
    public final String code, title, description;
    public final boolean compareEphemeris;
    private int guests;
    private long seed = 1781;
    /** Wird nach jedem Integratorschritt gerufen (Spuren der Darstellung); darf null sein. */
    public java.util.function.Consumer<Playground> onStep;

    private Playground(double startJd, boolean sun, String code, String title, String description, boolean compare) {
        this.startJd = startJd; this.sun = sun; this.code = code; this.title = title; this.description = description;
        this.compareEphemeris = compare;
    }

    // ================================================================== Aufbau

    /**
     * Der echte Stand zu einem Datum: Hauptmonde mit Masse, Irreguläre als Testkörper, innere Monde nur
     * wenn gewünscht. Geschwindigkeit aus der Ephemeride (zentraler Differenzenquotient über ±30 s) — so
     * stimmt die mittlere Bewegung mit der an Horizons angeglichenen Periode überein.
     */
    public static Playground fromSystem(UranusSystem sys, double jdUtc, boolean inner, boolean irregular, String code, String title,
                                        String description) {
        return fromSystem(sys, jdUtc, inner, irregular, code, title, description, TUNE_DAYS);
    }

    /** Über so viele Tage wird die Startgeschwindigkeit an die Ephemeride angeglichen (0 = gar nicht). */
    public static final double TUNE_DAYS = 60;

    public static Playground fromSystem(UranusSystem sys, double jdUtc, boolean inner, boolean irregular, String code, String title,
                                        String description, double tuneDays) {
        Playground p = new Playground(jdUtc, true, code, title, description, true);
        List<double[]> xs = new ArrayList<>(), vs = new ArrayList<>();
        p.bodies.add(new PlayBody("Uranus", PlayBody.Role.URANUS, null, UranusSystem.URANUS_GM, false, UranusSystem.URANUS_RADIUS_KM));
        xs.add(new double[3]); vs.add(new double[3]);
        double tdb = TimeScale.tdb(jdUtc);
        for (Body b : sys.bodies()) {
            boolean major = b.group == BodyGroup.MAJOR, inn = b.group == BodyGroup.INNER, irr = b.group == BodyGroup.IRREGULAR;
            if (!(major || (inn && inner) || (irr && irregular))) continue;
            PlayBody.Role role = irr ? PlayBody.Role.TEST : PlayBody.Role.MOON;
            double gm = role == PlayBody.Role.TEST ? 0 : UranusSystem.massOf(b) * UranusSystem.G_KM;
            p.bodies.add(new PlayBody(b.name, role, b, gm, b.massKg <= 0, b.radiusKm));
            double[] r = new double[3], r1 = new double[3], r2 = new double[3];
            Kepler.position(b.orbit, tdb, r);
            double h = 30.0 / 86_400.0;
            Kepler.position(b.orbit, tdb - h, r1);
            Kepler.position(b.orbit, tdb + h, r2);
            xs.add(r);
            vs.add(new double[]{(r2[0] - r1[0]) / 60, (r2[1] - r1[1]) / 60, (r2[2] - r1[2]) / 60});
        }
        if (tuneDays > 0) p.tune(xs, vs, tuneDays);
        p.init(xs, vs);
        return p;
    }

    /**
     * Angleich der Startgeschwindigkeit an die Ephemeride. Die Lagen stammen aus mittleren Elementen; deren
     * Halbachse und die an Horizons angeglichene Periode passen nicht genau zum dritten Kepler-Gesetz mit
     * dem GM des Uranus (J2 und die Nachbarmonde ändern die Umlaufzeit). Mit der rohen Geschwindigkeit
     * liefe Oberon in 1000 Tagen um 15° davon. Also: tuneDays weit rechnen, alle zwei Tage den Vorlauf
     * gegenüber der Ephemeride messen, per Ausgleichsgerade die Drift bestimmen (kurzperiodische Störungen
     * mitteln sich heraus) und die Geschwindigkeit nachstellen (δv/v = −⅓ · δn/n). Zwei Runden.
     * Nur Monde mit Masse; die Irregulären laufen zu langsam für so einen Vergleich.
     */
    private void tune(List<double[]> xs, List<double[]> vs, double tuneDays) {
        double tdb0 = TimeScale.tdb(startJd);
        double[] r = new double[3], e = new double[3], v = new double[3];
        int samples = (int) Math.max(8, tuneDays / 2);
        for (int round = 0; round < 2; round++) {
            Playground q = new Playground(startJd, sun, code, title, description, compareEphemeris);
            for (PlayBody b : bodies) q.bodies.add(b.copy());
            List<double[]> cx = new ArrayList<>(), cv = new ArrayList<>();
            for (int i = 0; i < xs.size(); i++) { cx.add(xs.get(i).clone()); cv.add(vs.get(i).clone()); }
            q.init(cx, cv);
            int n = bodies.size();
            double[] st = new double[n], stt = new double[n], sd = new double[n], std = new double[n], last = new double[n];
            for (int k = 1; k <= samples; k++) {
                q.advance(tuneDays * 86_400 / samples, 0);
                double tk = tuneDays * k / samples;
                for (int i = 1; i < n; i++) {
                    PlayBody b = bodies.get(i);
                    if (b.role != PlayBody.Role.MOON || b.source == null) continue;
                    q.position(i, r);
                    q.velocity(i, v);
                    Kepler.position(b.source.orbit, tdb0 + tk, e);
                    double hx = r[1] * v[2] - r[2] * v[1], hy = r[2] * v[0] - r[0] * v[2], hz = r[0] * v[1] - r[1] * v[0];
                    double cx1 = r[1] * e[2] - r[2] * e[1], cy1 = r[2] * e[0] - r[0] * e[2], cz1 = r[0] * e[1] - r[1] * e[0];
                    double hn = Math.sqrt(hx * hx + hy * hy + hz * hz);
                    double d = Math.atan2((cx1 * hx + cy1 * hy + cz1 * hz) / hn, r[0] * e[0] + r[1] * e[1] + r[2] * e[2]);
                    d = last[i] + Math.IEEEremainder(d - last[i], 2 * Math.PI);          // stetig halten
                    last[i] = d;
                    st[i] += tk; stt[i] += tk * tk; sd[i] += d; std[i] += tk * d;
                }
            }
            for (int i = 1; i < n; i++) {
                PlayBody b = bodies.get(i);
                if (b.role != PlayBody.Role.MOON || b.source == null) continue;
                double slope = (samples * std[i] - st[i] * sd[i]) / (samples * stt[i] - st[i] * st[i]);   // rad je Tag
                double nn = 2 * Math.PI / b.source.orbit.periodDays;
                double f = 1 - slope / nn / 3;
                double[] vi = vs.get(i);
                vi[0] *= f; vi[1] *= f; vi[2] *= f;
            }
        }
    }

    /** Ein gespeichertes Szenario. Namen echter Monde holen Farbe, Oberfläche und Ephemeride aus sys. */
    public static Playground fromScenario(Scenario sc, UranusSystem sys) {
        Playground p = new Playground(sc.startJd, true, sc.code, sc.title, sc.description, sc.compareEphemeris);
        List<double[]> xs = new ArrayList<>(), vs = new ArrayList<>();
        p.bodies.add(new PlayBody("Uranus", PlayBody.Role.URANUS, null, UranusSystem.URANUS_GM, false, UranusSystem.URANUS_RADIUS_KM));
        xs.add(new double[3]); vs.add(new double[3]);
        for (Scenario.Item it : sc.items()) {
            PlayBody.Role role = PlayBody.of(it.role);
            Body src = sys == null ? null : sys.find(it.name);
            double gm = role == PlayBody.Role.TEST ? 0 : it.massKg * UranusSystem.G_KM;
            p.bodies.add(new PlayBody(it.name, role, src, gm, it.massEstimated, it.radiusKm));
            if (role == PlayBody.Role.GUEST) p.guests++;
            double[] r = new double[3], v = new double[3];
            Ephemeris.icrfToWorld(it.r, r);
            Ephemeris.icrfToWorld(it.v, v);
            xs.add(r); vs.add(v);
        }
        p.init(xs, vs);
        return p;
    }

    private void init(List<double[]> xs, List<double[]> vs) {
        int n = bodies.size();
        double[] x = new double[3 * n], v = new double[3 * n];
        double px = 0, py = 0, pz = 0;
        for (int i = 0; i < n; i++) {
            System.arraycopy(xs.get(i), 0, x, 3 * i, 3);
            System.arraycopy(vs.get(i), 0, v, 3 * i, 3);
            double g = bodies.get(i).gm;
            if (i > 0) { px += g * v[3 * i]; py += g * v[3 * i + 1]; pz += g * v[3 * i + 2]; }
        }
        // Gesamtimpuls null: Uranus bewegt sich gegen seine Monde
        double gu = bodies.get(0).gm;
        v[0] = -px / gu; v[1] = -py / gu; v[2] = -pz / gu;
        for (int i = 1; i < n; i++) for (int k = 0; k < 3; k++) v[3 * i + k] += v[k];
        rebuild(0, x, v);
        for (int i = 1; i < n; i++) bodies.get(i).rocheArmed = distance(i) > rocheKm(i);
        e0 = energy();
    }

    private void rebuild(double t, double[] x, double[] v) {
        double[] gm = new double[bodies.size()];
        for (int i = 0; i < gm.length; i++) gm[i] = bodies.get(i).gm;
        forces = new PlayForces(gm, sun, startJd);
        double dtGuess = ias == null ? 600 : Math.max(60, Math.abs(ias.dt()));
        ias = new Ias15(forces);
        ias.set(t, x, v, dtGuess);
    }

    /** Stand als Szenario (für „Speichern“): Zustandsvektoren relativ zu Uranus im ICRF. */
    public Scenario toScenario(String code, String title, String description, Scenario.Origin origin, double rate) {
        List<Scenario.Item> items = new ArrayList<>();
        double[] r = new double[3], v = new double[3], ri = new double[3], vi = new double[3];
        for (int i = 1; i < bodies.size(); i++) {
            PlayBody b = bodies.get(i);
            position(i, r);
            velocity(i, v);
            Ephemeris.worldToIcrf(r[0], r[1], r[2], ri);
            Ephemeris.worldToIcrf(v[0], v[1], v[2], vi);
            double mass = b.role == PlayBody.Role.TEST ? (b.source != null ? UranusSystem.massOf(b.source) : 0) : b.massKg();
            items.add(new Scenario.Item(b.name, b.scenarioRole(), mass, b.massEstimated, b.radiusKm, ri, vi));
        }
        return new Scenario(code, title, description, jdUtc(), rate, origin, compareEphemeris, items);
    }

    // ================================================================== Abfragen

    public int size() { return bodies.size(); }

    public PlayBody body(int i) { return bodies.get(i); }

    public List<PlayBody> bodies() { return Collections.unmodifiableList(bodies); }

    public int indexOf(int id) {
        for (int i = 0; i < bodies.size(); i++) if (bodies.get(i).id == id) return i;
        return -1;
    }

    public int indexOf(String name) {
        for (int i = 0; i < bodies.size(); i++) if (bodies.get(i).name.equals(name)) return i;
        return -1;
    }

    /** Spielzeit in Sekunden seit dem Start. */
    public double time() { return ias.time(); }

    /** Spielzeit als JD (UTC). */
    public double jdUtc() { return startJd + ias.time() / 86_400.0; }

    /** Lage relativ zu Uranus (Welt, km). */
    public void position(int i, double[] out) {
        double[] x = ias.x();
        out[0] = x[3 * i] - x[0]; out[1] = x[3 * i + 1] - x[1]; out[2] = x[3 * i + 2] - x[2];
    }

    public void velocity(int i, double[] out) {
        double[] v = ias.v();
        out[0] = v[3 * i] - v[0]; out[1] = v[3 * i + 1] - v[1]; out[2] = v[3 * i + 2] - v[2];
    }

    public double distance(int i) {
        double[] r = new double[3];
        position(i, r);
        return Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
    }

    /** Roche-Grenze für einen flüssigen Körper dieser Dichte (km): 2,44 · R · (ρ_U / ρ)^(1/3). */
    public double rocheKm(int i) {
        PlayBody b = bodies.get(i);
        double rho = b.gm > 0 ? b.density() : UranusSystem.DENSITY_ASSUMED;
        return 2.44 * UranusSystem.URANUS_RADIUS_KM * Math.cbrt(URANUS_DENSITY / rho);
    }

    public List<Debris> debris() { return Collections.unmodifiableList(debris); }

    public List<PlayEvent> events() { return Collections.unmodifiableList(events); }

    public int undoDepth() { return undo.size(); }

    /** Energie ohne Sonne (baryzentrisch, mit Abplattung) — bleibt ohne Sonne erhalten. Einheit: km⁵/s⁴ (E·G). */
    public double energy() {
        double[] x = ias.x(), v = ias.v(), gm = forces.gm;
        int n = gm.length;
        double e = 0;
        for (int i = 0; i < n; i++) {
            if (gm[i] == 0) continue;
            e += 0.5 * gm[i] * (v[3 * i] * v[3 * i] + v[3 * i + 1] * v[3 * i + 1] + v[3 * i + 2] * v[3 * i + 2]);
            for (int j = i + 1; j < n; j++) {
                if (gm[j] == 0) continue;
                double dx = x[3 * i] - x[3 * j], dy = x[3 * i + 1] - x[3 * j + 1], dz = x[3 * i + 2] - x[3 * j + 2];
                e -= gm[i] * gm[j] / Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            if (i > 0) e += gm[i] * forces.oblatePotential(x[3 * i] - x[0], x[3 * i + 1] - x[1], x[3 * i + 2] - x[2]);
        }
        return e;
    }

    /** Relativer Energiefehler seit dem letzten Eingriff (mit Sonne enthält er auch deren echte Arbeit). */
    public double energyError() { return e0 == 0 ? 0 : (energy() - e0) / Math.abs(e0); }

    /** Bahn, wie sie gerade ist (oskulierend, um Uranus): {a km, e, i° gegen den Äquator, Periode d}; a < 0 = ungebunden. */
    public double[] elements(int i) {
        double[] r = new double[3], v = new double[3];
        position(i, r);
        velocity(i, v);
        return elements(r, v, bodies.get(0).gm + bodies.get(i).gm);
    }

    public static double[] elements(double[] r, double[] v, double mu) {
        double rn = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
        double v2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
        double a = 1 / (2 / rn - v2 / mu);
        double hx = r[1] * v[2] - r[2] * v[1], hy = r[2] * v[0] - r[0] * v[2], hz = r[0] * v[1] - r[1] * v[0];
        double hn = Math.sqrt(hx * hx + hy * hy + hz * hz);
        double e = Math.sqrt(Math.max(0, 1 - hn * hn / (mu * a)));
        // die Monde laufen rechtläufig zur Drehung des Uranus, also um −y: Neigung gegen −y gemessen
        double inc = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, -hy / hn))));
        double period = a > 0 ? 2 * Math.PI * Math.sqrt(a * a * a / mu) / 86_400.0 : Double.NaN;
        return new double[]{a, e, inc, period};
    }

    /**
     * Umlaufverhältnis zu den Nachbarn (gebundene Körper mit Masse oder Gäste, nach Halbachse) und die
     * nächste einfache Resonanz p:q (q ≤ 4) innerhalb von 1 %. Leer, wenn es keine Nachbarn gibt.
     */
    public List<String> resonances(int i) {
        List<String> out = new ArrayList<>();
        double[] ei = elements(i);
        if (!(ei[0] > 0)) return out;
        int inner = -1, outer = -1;
        double ai = ei[0], bestIn = 0, bestOut = Double.MAX_VALUE;
        for (int j = 1; j < bodies.size(); j++) {
            if (j == i || bodies.get(j).role == PlayBody.Role.TEST) continue;
            double aj = elements(j)[0];
            if (!(aj > 0)) continue;
            if (aj < ai && aj > bestIn) { bestIn = aj; inner = j; }
            if (aj > ai && aj < bestOut) { bestOut = aj; outer = j; }
        }
        for (int j : new int[]{inner, outer}) {
            if (j < 0) continue;
            double pj = elements(j)[3], ratio = Math.max(pj, ei[3]) / Math.min(pj, ei[3]);
            String near = "";
            outer:
            for (int q = 1; q <= 4; q++)
                for (int p = q + 1; p <= 4 * q + 1; p++)
                    if (gcd(p, q) == 1 && Math.abs(ratio / ((double) p / q) - 1) < 0.01) { near = " · nahe " + p + ":" + q; break outer; }
            out.add(String.format(java.util.Locale.GERMAN, "%s %.3f%s", bodies.get(j).name, ratio, near));
        }
        return out;
    }

    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    // ================================================================== Rechnen

    /**
     * Rechnet um dt Sekunden weiter (auch rückwärts), mit allen Prüfungen nach jedem Schritt.
     * false: das Zeitbudget (ns, 0 = unbegrenzt) war vorher erschöpft — die Spielzeit steht dann dazwischen.
     */
    public boolean advance(double dtSeconds, long budgetNanos) {
        long t0 = System.nanoTime();
        double tEnd = ias.time() + dtSeconds;
        double[] prev = new double[ias.x().length];
        while (Math.abs(tEnd - ias.time()) > 1e-6) {
            if (prev.length != ias.x().length) prev = new double[ias.x().length];
            System.arraycopy(ias.x(), 0, prev, 0, prev.length);
            ias.stepTowards(tEnd);
            check(prev);
            if (onStep != null) onStep.accept(this);
            if (budgetNanos > 0 && System.nanoTime() - t0 > budgetNanos) return Math.abs(tEnd - ias.time()) <= 1e-6;
        }
        return true;
    }

    /** Zusammenstöße, Roche-Grenze, Hill-Sphäre — nach jedem Schritt. */
    private void check(double[] prev) {
        double[] x = ias.x();
        int n = bodies.size();
        // 1) Berührungen, auch zwischen den Schritten (kleinster Abstand auf der Verbindungsstrecke)
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                PlayBody a = bodies.get(i), b = bodies.get(j);
                if (!a.massive() && !b.massive()) continue;
                double r = a.radiusKm + b.radiusKm;
                double d0x = prev[3 * i] - prev[3 * j], d0y = prev[3 * i + 1] - prev[3 * j + 1], d0z = prev[3 * i + 2] - prev[3 * j + 2];
                double d1x = x[3 * i] - x[3 * j], d1y = x[3 * i + 1] - x[3 * j + 1], d1z = x[3 * i + 2] - x[3 * j + 2];
                double ex = d1x - d0x, ey = d1y - d0y, ez = d1z - d0z, ee = ex * ex + ey * ey + ez * ez;
                double s = ee > 0 ? Math.max(0, Math.min(1, -(d0x * ex + d0y * ey + d0z * ez) / ee)) : 1;
                double mx = d0x + s * ex, my = d0y + s * ey, mz = d0z + s * ez;
                if (mx * mx + my * my + mz * mz < r * r) { collide(i, j); check(ias.x().clone()); return; }
            }
        }
        // 2) Roche-Grenze und Hill-Sphäre
        for (int i = 1; i < n; i++) {
            PlayBody b = bodies.get(i);
            double d = distance(i);
            if (d > HILL_KM) { escape(i); check(ias.x().clone()); return; }
            if (b.role == PlayBody.Role.TEST) continue;
            double roche = rocheKm(i);
            if (!b.rocheArmed && d > roche * 1.05) b.rocheArmed = true;
            if (b.rocheArmed && d < roche) { breakup(i); check(ias.x().clone()); return; }
        }
    }

    private void collide(int i, int j) {
        PlayBody a = bodies.get(i), b = bodies.get(j);
        double[] x = ias.x().clone(), v = ias.v().clone();
        double[] where = new double[3];
        if (i == 0) {                                                        // stürzt in Uranus
            position(j, where);
            double gu = a.gm, gb = b.gm, g = gu + gb;
            for (int k = 0; k < 3; k++) v[k] = (gu * v[k] + gb * v[3 * j + k]) / g;
            bodies.set(0, a.merged(g, a.radiusKm));
            events.add(new PlayEvent(PlayEvent.Kind.IMPACT, jdUtc(), b.name + " stürzt in Uranus", where, b.massive() ? b.massKg() : 0));
            remove(j, x, v);
            return;
        }
        boolean aHeavier = a.gm >= b.gm;
        int keep = aHeavier ? i : j, drop = aHeavier ? j : i;
        PlayBody k = bodies.get(keep), d = bodies.get(drop);
        double g = k.gm + d.gm;
        if (d.gm > 0) {
            for (int c = 0; c < 3; c++) {
                x[3 * keep + c] = (k.gm * x[3 * keep + c] + d.gm * x[3 * drop + c]) / g;
                v[3 * keep + c] = (k.gm * v[3 * keep + c] + d.gm * v[3 * drop + c]) / g;
            }
        }
        double r = Math.cbrt(Math.pow(k.radiusKm, 3) + Math.pow(d.radiusKm, 3));
        bodies.set(keep, k.merged(g, r));
        for (int c = 0; c < 3; c++) where[c] = x[3 * keep + c] - x[c];
        events.add(new PlayEvent(PlayEvent.Kind.MERGE, jdUtc(), d.name + " und " + k.name + " verschmelzen", where, g / UranusSystem.G_KM));
        remove(drop, x, v);
    }

    private void escape(int i) {
        double[] where = new double[3];
        position(i, where);
        PlayBody b = bodies.get(i);
        events.add(new PlayEvent(PlayEvent.Kind.ESCAPE, jdUtc(), b.name + " verlässt die Hill-Sphäre", where, b.massKg()));
        remove(i, ias.x().clone(), ias.v().clone());
    }

    private void breakup(int i) {
        PlayBody b = bodies.get(i);
        double[] r = new double[3], v = new double[3];
        position(i, r);
        velocity(i, v);
        float cr = b.source != null ? b.source.red : 0.8f, cg = b.source != null ? b.source.green : 0.78f, cb = b.source != null ? b.source.blue : 0.72f;
        List<Debris> d = new ArrayList<>(debris);
        d.add(new Debris(b.name, ias.time(), r, v, b.gm, b.radiusKm, DEBRIS_PER_BREAKUP, seed++, cr, cg, cb));
        debris = d;
        events.add(new PlayEvent(PlayEvent.Kind.BREAKUP, jdUtc(), String.format(java.util.Locale.GERMAN,
                "%s zerbricht bei %,.0f km (Roche-Grenze %,.0f km)", b.name, Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]), rocheKm(i)), r, b.massKg()));
        remove(i, ias.x().clone(), ias.v().clone());
    }

    private void remove(int i, double[] x, double[] v) {
        int n = bodies.size();
        double[] nx = new double[3 * (n - 1)], nv = new double[3 * (n - 1)];
        for (int j = 0, k = 0; j < n; j++) {
            if (j == i) continue;
            System.arraycopy(x, 3 * j, nx, 3 * k, 3);
            System.arraycopy(v, 3 * j, nv, 3 * k, 3);
            k++;
        }
        List<PlayBody> nb = new ArrayList<>(bodies);
        nb.remove(i);
        bodies = nb;
        rebuild(ias.time(), nx, nv);
        e0 = energy();
    }

    // ================================================================== Eingriffe

    /** Masse eines Körpers setzen (GM). Testkörper bekommen so zum ersten Mal Masse. */
    public void setGm(int i, double gm) {
        snapshot();
        PlayBody b = bodies.get(i);
        b.gm = Math.max(0, gm);
        double[] x = ias.x().clone(), v = ias.v().clone();
        rebuild(ias.time(), x, v);
        b.rocheArmed = distance(i) > rocheKm(i);
        e0 = energy();
    }

    /** Lage und Geschwindigkeit relativ zu Uranus setzen (Ziehen und Werfen). */
    public void place(int i, double[] relKm, double[] relVel) {
        snapshot();
        double[] x = ias.x().clone(), v = ias.v().clone();
        for (int k = 0; k < 3; k++) { x[3 * i + k] = x[k] + relKm[k]; v[3 * i + k] = v[k] + relVel[k]; }
        rebuild(ias.time(), x, v);
        bodies.get(i).rocheArmed = distance(i) > rocheKm(i);
        e0 = energy();
    }

    /** Neuer Gast; gibt seinen Index zurück. */
    public int addGuest(double massKg, double radiusKm, double[] relKm, double[] relVel) {
        return addGuest(null, massKg, radiusKm, relKm, relVel);
    }

    public int addGuest(String name, double massKg, double radiusKm, double[] relKm, double[] relVel) {
        snapshot();
        guests++;
        PlayBody g = new PlayBody(name != null ? name : "Gast " + guests, PlayBody.Role.GUEST, null, massKg * UranusSystem.G_KM, false, radiusKm);
        double[] x0 = ias.x(), v0 = ias.v();
        int n = bodies.size();
        double[] x = new double[3 * (n + 1)], v = new double[3 * (n + 1)];
        System.arraycopy(x0, 0, x, 0, 3 * n);
        System.arraycopy(v0, 0, v, 0, 3 * n);
        for (int k = 0; k < 3; k++) { x[3 * n + k] = x0[k] + relKm[k]; v[3 * n + k] = v0[k] + relVel[k]; }
        List<PlayBody> nb = new ArrayList<>(bodies);
        nb.add(g);
        bodies = nb;
        rebuild(ias.time(), x, v);
        g.rocheArmed = distance(n) > rocheKm(n);
        e0 = energy();
        return n;
    }

    /** Geschwindigkeit einer Kreisbahn um Uranus an dieser Stelle, in der Ebene senkrecht zu normal (km/s). */
    public static double[] circularVelocity(double[] relKm, double[] normal, double gm) {
        double r = Math.sqrt(relKm[0] * relKm[0] + relKm[1] * relKm[1] + relKm[2] * relKm[2]);
        double s = Math.sqrt((UranusSystem.URANUS_GM + gm) / r);
        double tx = normal[1] * relKm[2] - normal[2] * relKm[1], ty = normal[2] * relKm[0] - normal[0] * relKm[2], tz = normal[0] * relKm[1] - normal[1] * relKm[0];
        double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tl == 0) return new double[3];
        return new double[]{tx / tl * s, ty / tl * s, tz / tl * s};
    }

    /**
     * Vorausschau: Bahn eines Körpers (Index, oder −1 für einen gedachten Gast) über days Tage in samples
     * Punkten, relativ zu Uranus — ohne Zusammenstöße, mit Zeitbudget. relKm/relVel ersetzen seinen Stand.
     */
    public double[][] predict(int i, double[] relKm, double[] relVel, double gmGuest, double days, int samples, long budgetNanos) {
        double[] x = ias.x().clone(), v = ias.v().clone(), gm = forces.gm.clone();
        int idx = i;
        if (i < 0) {
            int n = gm.length;
            x = java.util.Arrays.copyOf(x, 3 * n + 3);
            v = java.util.Arrays.copyOf(v, 3 * n + 3);
            gm = java.util.Arrays.copyOf(gm, n + 1);
            gm[n] = gmGuest;
            idx = n;
        }
        if (relKm != null) for (int k = 0; k < 3; k++) { x[3 * idx + k] = x[k] + relKm[k]; v[3 * idx + k] = v[k] + relVel[k]; }
        PlayForces f = new PlayForces(gm, sun, startJd);
        Ias15 p = new Ias15(f);
        p.set(ias.time(), x, v, 300);
        double[][] out = new double[samples + 1][];
        long t0 = System.nanoTime();
        double tStart = ias.time(), span = days * 86_400;
        out[0] = new double[]{x[3 * idx] - x[0], x[3 * idx + 1] - x[1], x[3 * idx + 2] - x[2]};
        for (int s = 1; s <= samples; s++) {
            p.integrateTo(tStart + span * s / samples, 0);
            double[] px = p.x();
            out[s] = new double[]{px[3 * idx] - px[0], px[3 * idx + 1] - px[1], px[3 * idx + 2] - px[2]};
            if (budgetNanos > 0 && System.nanoTime() - t0 > budgetNanos) return java.util.Arrays.copyOf(out, s + 1);
        }
        return out;
    }

    // ================================================================== Zwischenstände

    private static final class Snapshot {
        final List<PlayBody> bodies;
        final double t;
        final double[] x, v;
        final List<Debris> debris;
        final int events, guests;
        final double e0;

        Snapshot(List<PlayBody> b, double t, double[] x, double[] v, List<Debris> d, int ev, int guests, double e0) {
            this.bodies = b; this.t = t; this.x = x; this.v = v; this.debris = d; this.events = ev; this.guests = guests; this.e0 = e0;
        }
    }

    /** Zwischenstand ablegen (höchstens 60, die ältesten fallen heraus). */
    public void snapshot() {
        List<PlayBody> copy = new ArrayList<>();
        for (PlayBody b : bodies) copy.add(b.copy());
        undo.push(new Snapshot(copy, ias.time(), ias.x().clone(), ias.v().clone(), debris, events.size(), guests, e0));
        while (undo.size() > MAX_SNAPSHOTS) undo.removeLast();
    }

    /** Zum letzten Zwischenstand zurück; false, wenn es keinen gibt. */
    public boolean undo() {
        Snapshot s = undo.poll();
        if (s == null) return false;
        bodies = new ArrayList<>(s.bodies);
        debris = s.debris;
        while (events.size() > s.events) events.remove(events.size() - 1);
        guests = s.guests;
        rebuild(s.t, s.x, s.v);
        e0 = s.e0;
        return true;
    }

    /** Für Prüfungen: Sonne an/aus ändert das Kraftgesetz. */
    public static Playground withoutSun(Playground p) {
        Playground q = new Playground(p.startJd, false, p.code, p.title, p.description, p.compareEphemeris);
        for (PlayBody b : p.bodies) q.bodies.add(b.copy());
        q.rebuild(p.time(), p.ias.x().clone(), p.ias.v().clone());
        q.e0 = q.energy();
        return q;
    }

    public int integratorSteps() { return ias.steps(); }
}
