package com.dan.uranus.render;

import com.dan.uranus.model.SimClock;
import com.dan.uranus.model.Tour;

/**
 * Spielt eine Kamerafahrt ab (nur im Render-Thread).
 * <p>
 * Stationen sind Schlüsselbilder: zur Zeit atS steht die Kamera in der Lage der Station, verweilt,
 * und fährt im letzten Teil der Strecke weich zur nächsten. Am Anfang und nach einem Ringflug fliegt
 * sie aus der Lage ein, die sie WIRKLICH hat — deshalb springt nichts. Ziele dürfen sich bewegen
 * (Sonde, Mond): ihr Ort wird in jedem Bild neu abgefragt.
 * <p>
 * Uhr: {@link Tour.TimeMode#CLOCK} setzt die Simulationsuhr, {@link Tour.TimeMode#STORY} führt nur eine
 * Erzählzeit (Entdeckungen) und lässt die Uhr frei laufen.
 */
final class TourPlayer {

    /** Wo steht ein Ziel gerade (Welt)? null, wenn unbekannt. */
    interface Targets { double[] where(String name); }

    private Tour tour;
    private double t;
    private int seg = -1;
    private boolean paused;
    // Stand vor der Fahrt, zum Zurücksetzen
    private int sSpace, sScale, sMoons;
    private boolean sTrails, sMagneto, sAxis, sLens, sClockPaused;
    private double sRate, sJd;
    // Kamera beim Eintritt in die Strecke
    private double cYaw, cPitch, cDist, cTx, cTy, cTz;
    private boolean captured;
    private double storyJd = Double.NaN;
    private boolean linearTime, lead, flightSet;
    boolean moonsHidden;

    boolean active() { return tour != null; }

    Tour tour() { return tour; }

    double time() { return t; }

    boolean paused() { return paused; }

    void togglePause(SimClock clock) {
        paused = !paused;
        if (tour != null && tour.timeMode == Tour.TimeMode.STORY) clock.setPaused(paused);
    }

    /** Erzählzeit (Entdeckungen) oder NaN. */
    double storyJd() { return storyJd; }

    /** Aktuelle Station (für Titel und Text) oder null. */
    Tour.Step step() { return tour == null || seg < 0 ? null : tour.steps().get(seg); }

    /** Anteil der aktuellen Strecke 0..1. */
    double segmentFraction() {
        if (tour == null || seg < 0) return 0;
        Tour.Step a = tour.steps().get(seg), b = tour.steps().get(Math.min(seg + 1, tour.steps().size() - 1));
        return b.atS > a.atS ? Math.max(0, Math.min(1, (t - a.atS) / (b.atS - a.atS))) : 1;
    }

    /** Sekunden bis zum Ende der aktuellen Strecke. */
    double segmentRemaining() {
        if (tour == null || seg < 0) return 0;
        Tour.Step b = tour.steps().get(Math.min(seg + 1, tour.steps().size() - 1));
        return b.atS - t;
    }

    void start(Tour tr, SceneSettings s, SimClock clock) {
        if (tour != null) stop(s, clock);
        tour = tr;
        t = 0;
        seg = -1;
        paused = false;
        sSpace = s.spaceMode; sScale = s.scaleMode; sMoons = s.moonLevel; sTrails = s.trails; sMagneto = s.magneto;
        sAxis = s.axis; sLens = s.lens; sClockPaused = clock.isPaused(); sRate = clock.rate(); sJd = clock.jd();
        s.focus = null;
        s.ringFlight = false;
        flightSet = false;
        linearTime = false;
        if (tr.timeMode == Tour.TimeMode.CLOCK) clock.setPaused(true);          // die Fahrt führt die Uhr
        else clock.setPaused(false);
        storyJd = Double.NaN;
    }

    void stop(SceneSettings s, SimClock clock) {
        if (tour == null) return;
        s.spaceMode = sSpace; s.scaleMode = sScale; s.moonLevel = sMoons; s.trails = sTrails; s.magneto = sMagneto;
        s.axis = sAxis; s.lens = sLens;
        if (flightSet) s.ringFlight = false;                                  // einen selbst gestarteten Ringflug lassen
        clock.setRate(sRate);
        clock.setPaused(sClockPaused);
        if (tour.timeMode == Tour.TimeMode.CLOCK) clock.setJd(sJd);      // Zeitreise zurück zum Ausgangsdatum
        moonsHidden = false;
        tour = null;
        seg = -1;
        storyJd = Double.NaN;
    }

    /** Ein Bild weiter. Gibt false zurück, wenn die Fahrt gerade zu Ende gegangen ist. */
    boolean update(double dt, SceneSettings s, SimClock clock) {
        if (tour == null) return true;
        if (!paused) t += dt;
        if (t >= tour.duration()) { stop(s, clock); return false; }
        int k = 0;
        while (k + 1 < tour.steps().size() - 1 && t >= tour.steps().get(k + 1).atS) k++;
        if (k != seg) enter(k, s, clock);
        Tour.Step a = tour.steps().get(k), b = tour.steps().get(k + 1);
        double f = segmentFraction();
        // Uhrfahrten laufen durch (die Sonde fliegt weiter), Erzählzeit steht, solange die Karte gelesen wird
        if (tour.timeMode == Tour.TimeMode.STORY) f = Math.max(0, (f - HOLD) / (1 - HOLD));
        double ft = linearTime ? f : smooth(f);
        if (!Double.isNaN(a.jdUtc) && !Double.isNaN(b.jdUtc)) {
            double jd = a.jdUtc + (b.jdUtc - a.jdUtc) * ft;
            if (tour.timeMode == Tour.TimeMode.CLOCK) clock.setJd(jd);
            else storyJd = jd;
        }
        return true;
    }

    private void enter(int k, SceneSettings s, SimClock clock) {
        lead = k == 0 || s.ringFlight;                  // Start oder Ende eines Ringflugs: erst einfliegen
        seg = k;
        captured = false;
        Tour.Step st = tour.steps().get(k);
        String v;
        if ((v = st.layer("moons")) != null) {
            moonsHidden = v.equals("off");
            if (!moonsHidden) s.moonLevel = Integer.parseInt(v);
        }
        if ((v = st.layer("trails")) != null) s.trails = v.equals("1");
        if ((v = st.layer("magneto")) != null) s.magneto = v.equals("1");
        if ((v = st.layer("space")) != null) s.spaceMode = Integer.parseInt(v);
        if ((v = st.layer("scale")) != null) s.scaleMode = Integer.parseInt(v);
        if ((v = st.layer("axis")) != null) s.axis = v.equals("1");
        if ((v = st.layer("lens")) != null) s.lens = v.equals("1");
        if ((v = st.layer("flight")) != null) { s.ringFlight = v.equals("1"); flightSet = true; }
        if ((v = st.layer("rate")) != null) clock.setRate(Double.parseDouble(v));
        if ((v = st.layer("time")) != null) linearTime = v.equals("lin");
    }

    /** Anteil einer Strecke, in dem die Kamera an der Station verweilt, bevor sie weiterfährt. */
    static final double HOLD = 0.4;
    /** Längste Einflugzeit von der Lage des Nutzers (oder aus dem Ringflug) zur ersten Station. */
    static final double LEAD_S = 3.0;

    /**
     * Setzt die Kamera für dieses Bild. Jede Station ist ein Schlüsselbild: zur Zeit atS steht die Kamera
     * in ihrer Lage, verweilt dort (Karte lesen) und fährt im letzten Teil der Strecke zur nächsten.
     * Zu Beginn und nach einem Ringflug fliegt sie zuerst aus der tatsächlichen Lage zur Station ein.
     */
    void applyCamera(Camera cam, Targets where, double[] sun) {
        if (tour == null || seg < 0) return;
        Tour.Step a = tour.steps().get(seg), b = tour.steps().get(seg + 1);
        double[] pa = target(where, a), pb = target(where, b);
        if (!captured) {
            cYaw = cam.yaw(); cPitch = cam.pitch(); cDist = cam.distance();
            cTx = cam.cx + cam.fx * cam.distance(); cTy = cam.cy + cam.fy * cam.distance(); cTz = cam.cz + cam.fz * cam.distance();
            captured = true;
        }
        double[] qa = pose(a, sun), qb = pose(b, sun);
        double len = b.atS - a.atS, tt = t - a.atS;
        // Ausgangslage dieser Strecke: die Station a — oder der Einflug aus der erfassten Lage
        double fy = qa[0], fp = qa[1], fd = qa[2], fx = pa[0], fyy = pa[1], fz = pa[2];
        double hold = HOLD;
        if (lead) {
            double lt = Math.min(LEAD_S, len * 0.4);
            double li = smooth(tt / lt);
            fy = cYaw + Math.IEEEremainder(qa[0] - cYaw, 2 * Math.PI) * li;
            fp = cPitch + (qa[1] - cPitch) * li;
            fd = Math.exp(Math.log(cDist) + (Math.log(qa[2]) - Math.log(cDist)) * li);
            fx = cTx + (pa[0] - cTx) * li; fyy = cTy + (pa[1] - cTy) * li; fz = cTz + (pa[2] - cTz) * li;
            hold = Math.max(HOLD, lt / len + 0.15);
        }
        double e = smooth((tt / len - hold) / (1 - hold));
        double y = fy + Math.IEEEremainder(qb[0] - fy, 2 * Math.PI) * e;
        double pi = fp + (qb[1] - fp) * e;
        double d = Math.exp(Math.log(fd) + (Math.log(qb[2]) - Math.log(fd)) * e);
        // Ziele werden je Bild neu abgefragt: bewegte Ziele (Sonde, Mond) nimmt die Kamera mit
        cam.setPose(fx + (pb[0] - fx) * e, fyy + (pb[1] - fyy) * e, fz + (pb[2] - fz) * e, y, pi, d);
    }

    private static double[] target(Targets where, Tour.Step st) {
        double[] p = where.where(st.target);
        return p != null ? p : where.where("Uranus");
    }

    /** Lage einer Station: Gier, Neigung (Bogenmaß), Abstand; „cam=sun“ misst die Winkel von der Sonnenrichtung. */
    private static double[] pose(Tour.Step st, double[] sun) {
        double yaw = Math.toRadians(st.yawDeg), pitch = Math.toRadians(st.pitchDeg);
        if ("sun".equals(st.layer("cam")) && sun != null) {
            yaw += Math.atan2(sun[0], sun[2]);
            pitch += Math.asin(Math.max(-1, Math.min(1, sun[1])));
        }
        pitch = Math.max(Camera.MIN_PITCH, Math.min(Camera.MAX_PITCH, pitch));
        return new double[]{yaw, pitch, st.dist};
    }

    static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
