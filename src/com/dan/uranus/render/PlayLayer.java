package com.dan.uranus.render;

import com.dan.uranus.model.UranusSystem;
import com.dan.uranus.physics.Kepler;
import com.dan.uranus.physics.TimeScale;
import com.dan.uranus.play.Debris;
import com.dan.uranus.play.PlayBody;
import com.dan.uranus.play.PlayEvent;
import com.dan.uranus.play.Playground;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Zeichnet die Spielwiese in die Szene: Spuren (wo die Körper wirklich waren), die Bahn des gewählten
 * Körpers, wie sie gerade ist, die Kreise der Ephemeride („Heute, frei“), Vorausschau und Wurfpfeil,
 * Trümmer und das Aufblitzen bei Zusammenstößen. Nur im Render-Thread.
 */
final class PlayLayer {

    /** km relativ zu Uranus → Welt (mit Mulde). */
    interface Mapper { void world(double[] km, double radius, double[] out); }

    private static final int TRAIL = 320;

    private static final class Trail {
        final double[] km = new double[TRAIL * 3];
        int n, head;
        void add(double[] p) {
            System.arraycopy(p, 0, km, head * 3, 3);
            head = (head + 1) % TRAIL;
            if (n < TRAIL) n++;
        }
        /** k-ter Punkt ab dem neuesten (0 = neuester). */
        void get(int k, double[] out) { int i = ((head - 1 - k) % TRAIL + TRAIL) % TRAIL; System.arraycopy(km, i * 3, out, 0, 3); }
    }

    private static final class Flash {
        final double[] km; final double t0, strength; final PlayEvent.Kind kind;
        Flash(double[] km, double t0, double strength, PlayEvent.Kind kind) { this.km = km; this.t0 = t0; this.strength = strength; this.kind = kind; }
    }

    private final Map<Integer, Trail> trails = new HashMap<>();
    private final List<Flash> flashes = new ArrayList<>();
    private int eventsSeen;
    private double lastSample = Double.NaN;

    // Eingriffe (vom Renderer gesetzt)
    double[][] prediction;
    double[] arrowFrom, arrowTo;
    int selectedIndex = -1;

    void reset() { trails.clear(); flashes.clear(); eventsSeen = 0; lastSample = Double.NaN; prediction = null; arrowFrom = arrowTo = null; }

    /** Nach Rückgängig: Spuren passen nicht mehr, Ereignisse neu zählen. */
    void afterUndo(Playground p) { trails.clear(); eventsSeen = p.events().size(); lastSample = Double.NaN; }

    /** Nach jedem Integratorschritt: Spuren fortschreiben (so bleiben sie rund, auch bei hohem Tempo). */
    void sample(Playground p) {
        double t = p.time();
        if (!Double.isNaN(lastSample) && Math.abs(t - lastSample) < 1) return;
        double[] r = new double[3];
        for (int i = 1; i < p.size(); i++) {
            p.position(i, r);
            trails.computeIfAbsent(p.body(i).id, k -> new Trail()).add(r);
        }
        lastSample = t;
    }

    /** Je Bild: neue Ereignisse aufblitzen lassen, Spuren verschwundener Körper aufräumen. */
    void record(Playground p, double realTime) {
        if (Double.isNaN(lastSample)) sample(p);
        List<PlayEvent> ev = p.events();
        if (eventsSeen > ev.size()) eventsSeen = ev.size();
        for (int k = eventsSeen; k < ev.size(); k++) {
            PlayEvent e = ev.get(k);
            double s = Math.min(1.6, 0.6 + 0.1 * Math.log10(Math.max(1e15, e.massKg) / 1e15));
            flashes.add(new Flash(e.km, realTime, s, e.kind));
        }
        eventsSeen = ev.size();
        // Spuren verschwundener Körper aufräumen
        if (trails.size() > p.size() + 8) {
            java.util.Set<Integer> alive = new java.util.HashSet<>();
            for (PlayBody b : p.bodies()) alive.add(b.id);
            trails.keySet().retainAll(alive);
        }
    }

    void build(Scene3D s, Mapper map, Playground p, double trailsAlpha, boolean compare, double realTime) {
        Camera cam = s.camera();
        double[] a = new double[3], b = new double[3], km = new double[3], w = new double[3];
        // 1) Spuren
        if (trailsAlpha > 0.01) {
            for (int i = 1; i < p.size(); i++) {
                PlayBody pb = p.body(i);
                Trail tr = trails.get(pb.id);
                if (tr == null || tr.n < 2) continue;
                float cr, cg, cb, ca;
                switch (pb.role) {
                    case GUEST: cr = 1.0f; cg = 0.72f; cb = 0.42f; ca = 0.75f; break;
                    case TEST: cr = 0.62f; cg = 0.70f; cb = 0.85f; ca = 0.28f; break;
                    default: cr = 0.80f; cg = 0.94f; cb = 0.98f; ca = 0.55f;
                }
                if (i == selectedIndex) ca = Math.min(1, ca * 1.6f);
                tr.get(0, km);
                map.world(km, 0.02, a);
                for (int k = 1; k < tr.n; k++) {
                    tr.get(k, km);
                    map.world(km, 0.02, b);
                    float al = (float) (ca * (1 - (double) k / tr.n) * trailsAlpha);
                    s.seg(a[0], a[1], a[2], b[0], b[1], b[2], cr, cg, cb, al, true);
                    a[0] = b[0]; a[1] = b[1]; a[2] = b[2];
                }
            }
        }
        // 2) Bahn des gewählten Körpers, wie sie gerade ist (Kepler aus Lage und Geschwindigkeit)
        if (selectedIndex > 0 && selectedIndex < p.size()) {
            double[] r = new double[3], v = new double[3];
            p.position(selectedIndex, r);
            p.velocity(selectedIndex, v);
            conic(s, map, r, v, UranusSystem.URANUS_GM + p.body(selectedIndex).gm, 0.60f, 0.92f, 0.95f, 0.5f);
        }
        // 3) Ephemeride zum Vergleich: kleiner Kreis und Verbindung
        if (compare) {
            double tdb = TimeScale.tdb(p.jdUtc());
            for (int i = 1; i < p.size(); i++) {
                PlayBody pb = p.body(i);
                if (pb.source == null || pb.role == PlayBody.Role.TEST) continue;
                Kepler.position(pb.source.orbit, tdb, km);
                map.world(km, 0.05, a);
                p.position(i, km);
                map.world(km, 0.05, b);
                if (!cam.project(a[0], a[1], a[2], w)) continue;
                double rad = w[2] * 7 / cam.foc;
                ring(s, cam, a, rad, 1f, 0.82f, 0.55f, 0.8f, 20);
                s.seg(a[0], a[1], a[2], b[0], b[1], b[2], 1f, 0.82f, 0.55f, 0.35f, true);
            }
        }
        // 4) Vorausschau (gestrichelt) und Wurfpfeil
        if (prediction != null && prediction.length > 1) {
            map.world(prediction[0], 0.02, a);
            for (int k = 1; k < prediction.length; k++) {
                map.world(prediction[k], 0.02, b);
                if (k % 2 == 1) s.seg(a[0], a[1], a[2], b[0], b[1], b[2], 1f, 0.9f, 0.6f, 0.75f, true);
                a[0] = b[0]; a[1] = b[1]; a[2] = b[2];
            }
        }
        if (arrowFrom != null && arrowTo != null) {
            map.world(arrowFrom, 0.02, a);
            map.world(arrowTo, 0.02, b);
            s.seg(a[0], a[1], a[2], b[0], b[1], b[2], 1f, 0.78f, 0.45f, 0.95f, true);
            double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (l > 1e-9 && cam.project(b[0], b[1], b[2], w)) {
                double h = Math.min(l * 0.3, w[2] * 14 / cam.foc);
                double ux = dx / l, uy = dy / l, uz = dz / l;
                double px = uy * cam.fz - uz * cam.fy, py = uz * cam.fx - ux * cam.fz, pz = ux * cam.fy - uy * cam.fx;   // quer zum Blick
                double pl = Math.sqrt(px * px + py * py + pz * pz) + 1e-12;
                px /= pl; py /= pl; pz /= pl;
                for (int sgn = -1; sgn <= 1; sgn += 2)
                    s.seg(b[0], b[1], b[2], b[0] - ux * h + sgn * px * h * 0.5, b[1] - uy * h + sgn * py * h * 0.5,
                            b[2] - uz * h + sgn * pz * h * 0.5, 1f, 0.78f, 0.45f, 0.95f, true);
            }
        }
        // 5) Trümmer
        double t = p.time();
        for (Debris d : p.debris()) {
            float br = (float) (1.6 + 1.4 * Math.exp(-(t - d.t0) / 86_400.0));   // frisch gebrochen: heller Staub
            for (int k = 0; k < d.count; k++) {
                d.position(k, t, km);
                map.world(km, 0.005, a);
                s.glow(a[0], a[1], a[2], d.red * br, d.green * br, d.blue * br, 1.1);
            }
        }
        // 6) Aufblitzen
        for (Iterator<Flash> it = flashes.iterator(); it.hasNext(); ) {
            Flash f = it.next();
            double age = realTime - f.t0;
            if (age > 1.6) { it.remove(); continue; }
            map.world(f.km, 0.05, a);
            if (!cam.project(a[0], a[1], a[2], w)) continue;
            double k = age / 1.6, fade = 1 - k;
            float r = f.kind == PlayEvent.Kind.ESCAPE ? 0.6f : 1f, g = f.kind == PlayEvent.Kind.ESCAPE ? 0.85f : 0.86f, bb = f.kind == PlayEvent.Kind.ESCAPE ? 1f : 0.62f;
            s.glow(a[0], a[1], a[2], (float) (r * fade * 3 * f.strength), (float) (g * fade * 3 * f.strength), (float) (bb * fade * 3 * f.strength),
                    (4 + 26 * Math.sqrt(k)) * f.strength);
            ring(s, cam, a, w[2] * (8 + 70 * k) * f.strength / cam.foc, r, g, bb, (float) (fade * 0.9), 40);
        }
    }

    /** Kegelschnitt aus Lage und Geschwindigkeit (Welt-km), gebunden als Ellipse, sonst ein Stück Hyperbel. */
    private static void conic(Scene3D s, Mapper map, double[] r, double[] v, double mu, float cr, float cg, float cb, float ca) {
        double rn = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]), v2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
        double hx = r[1] * v[2] - r[2] * v[1], hy = r[2] * v[0] - r[0] * v[2], hz = r[0] * v[1] - r[1] * v[0];
        double h2 = hx * hx + hy * hy + hz * hz, hn = Math.sqrt(h2);
        if (hn == 0) return;
        double rv = r[0] * v[0] + r[1] * v[1] + r[2] * v[2];
        double[] e = {(v2 - mu / rn) * r[0] / mu - rv * v[0] / mu, (v2 - mu / rn) * r[1] / mu - rv * v[1] / mu, (v2 - mu / rn) * r[2] / mu - rv * v[2] / mu};
        double ee = Math.sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2]), pp = h2 / mu;
        double[] P = ee > 1e-9 ? new double[]{e[0] / ee, e[1] / ee, e[2] / ee} : new double[]{r[0] / rn, r[1] / rn, r[2] / rn};
        double[] W = {hx / hn, hy / hn, hz / hn};
        double[] Q = {W[1] * P[2] - W[2] * P[1], W[2] * P[0] - W[0] * P[2], W[0] * P[1] - W[1] * P[0]};
        double nuMax = ee < 1 ? Math.PI : Math.acos(Math.max(-1, Math.min(1, (pp / (3 * rn) - 1) / ee)));
        int n = 200;
        double[] km = new double[3], a = new double[3], b = new double[3];
        boolean have = false;
        for (int k = 0; k <= n; k++) {
            double nu = -nuMax + 2 * nuMax * k / n;
            double den = 1 + ee * Math.cos(nu);
            if (den <= 1e-6) { have = false; continue; }
            double rr = pp / den;
            for (int c = 0; c < 3; c++) km[c] = rr * (Math.cos(nu) * P[c] + Math.sin(nu) * Q[c]);
            map.world(km, 0.02, b);
            if (have) s.seg(a[0], a[1], a[2], b[0], b[1], b[2], cr, cg, cb, ca, true);
            a[0] = b[0]; a[1] = b[1]; a[2] = b[2];
            have = true;
        }
    }

    /** Kreis um einen Weltpunkt, zur Kamera gedreht. */
    private static void ring(Scene3D s, Camera cam, double[] c, double rad, float r, float g, float b, float a, int n) {
        double px = 0, py = 0, pz = 0;
        for (int k = 0; k <= n; k++) {
            double t = 2 * Math.PI * k / n, co = Math.cos(t) * rad, si = Math.sin(t) * rad;
            double x = c[0] + cam.rx * co + cam.ux * si, y = c[1] + cam.ry * co + cam.uy * si, z = c[2] + cam.rz * co + cam.uz * si;
            if (k > 0) s.seg(px, py, pz, x, y, z, r, g, b, a, true);
            px = x; py = y; pz = z;
        }
    }
}
