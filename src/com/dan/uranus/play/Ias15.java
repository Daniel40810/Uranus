package com.dan.uranus.play;

/**
 * IAS15: Integrator 15. Ordnung mit Gauß-Radau-Stützstellen und adaptiver Schrittweite
 * (Rein und Spiegel 2015, MNRAS 446, 1424; Aufbau wie in REBOUND).
 * <p>
 * Die Beschleunigung über einen Schritt wird als Polynom 7. Grades in der Schrittzeit angesetzt,
 * a(t) = a0 + b0·t + b1·t² + … + b6·t⁷, und die Koeffizienten werden durch Prädiktor-Korrektor-Iteration an
 * den acht Radau-Stellen bestimmt. Die Schrittweite folgt aus dem letzten Koeffizienten b6. Alle
 * Umrechnungskonstanten (Differenzen der Stützstellen, Umrechnung g ↔ b) werden aus den Stützstellen
 * berechnet statt abgeschrieben — so kann sich keine Ziffer vertippen.
 * <p>
 * Die Kräfte dürfen nur vom Ort abhängen (Gravitation), nicht von der Geschwindigkeit.
 * Zeiten in Sekunden, Längen in km.
 */
public final class Ias15 {

    /** Beschleunigungen a für die Lagen x zur Zeit t (beides Länge 3n). */
    public interface Forces { void acc(double t, double[] x, double[] a); }

    /** Gauß-Radau-Stützstellen auf [0, 1] (0 und die Nullstellen von (P7 + P8)(2t − 1) / t). */
    static final double[] H = {0.0, 0.0562625605369221464656521910318, 0.180240691736892364987579942780,
            0.352624717113169637373907769648, 0.547153626330555383001448554766, 0.734210177215410531523210605558,
            0.885320946839095768090359771030, 0.977520613561287501891174488626};

    /** P[j][k]: Koeffizient von t^k in Π_{i=1..j} (t − h_i); damit b_k = Σ_j g_j · P[j][k]. */
    static final double[][] P = new double[7][];
    /** Q = P⁻¹: g_j = Σ_k b_k · Q[k][j]. */
    static final double[][] Q = new double[7][7];

    static {
        double[] poly = {1};
        for (int j = 0; j < 7; j++) {
            P[j] = poly.clone();
            double[] next = new double[poly.length + 1];                  // poly · (t − h_{j+1})
            for (int k = 0; k < poly.length; k++) { next[k + 1] += poly[k]; next[k] -= poly[k] * H[j + 1]; }
            poly = next;
        }
        // M[k][j] = P[j][k] (obere Dreiecksmatrix mit Einsen): rückwärts einsetzen
        for (int col = 0; col < 7; col++) {                              // löse M · q = e_col
            double[] q = new double[7];
            for (int k = 6; k >= 0; k--) {
                double s = k == col ? 1 : 0;
                for (int j = k + 1; j < 7; j++) s -= P[j][k] * q[j];
                q[k] = s;                                                  // M[k][k] = 1
            }
            for (int j = 0; j < 7; j++) Q[col][j] = q[j];
        }
    }

    /** Genauigkeit (wie REBOUND): Schrittweite aus (ε / (max|b6| / max|a|))^(1/7). */
    public double epsilon = 1e-9;
    public double safety = 0.25;
    public double minDt = 1e-3;

    private final Forces forces;
    private int n3;
    private double t, dt, dtLastDone;
    private double[] x, v, csx, csv, a0, at, xs;
    private double[][] b, g, e, br, er;
    private int steps, rejected, evaluations;
    private double lastError;

    public Ias15(Forces forces) { this.forces = forces; }

    /** Setzt den Zustand neu (nach jedem Eingriff). Vorhersagen werden verworfen. */
    public void set(double t, double[] x, double[] v, double dtGuess) {
        n3 = x.length;
        this.t = t;
        this.x = x.clone();
        this.v = v.clone();
        csx = new double[n3]; csv = new double[n3]; a0 = new double[n3]; at = new double[n3]; xs = new double[n3];
        b = new double[7][n3]; g = new double[7][n3]; e = new double[7][n3]; br = new double[7][n3]; er = new double[7][n3];
        dt = dtGuess;
        dtLastDone = 0;
    }

    public double time() { return t; }
    public double dt() { return dt; }
    public double[] x() { return x; }
    public double[] v() { return v; }
    public int steps() { return steps; }
    public int rejected() { return rejected; }
    public int evaluations() { return evaluations; }
    public double lastError() { return lastError; }

    /**
     * Rechnet bis tEnd (vorwärts oder rückwärts), der letzte Schritt trifft tEnd genau.
     * Gibt false zurück, wenn das Zeitbudget (ns) vorher erschöpft war.
     */
    public boolean integrateTo(double tEnd, long budgetNanos) {
        long t0 = System.nanoTime();
        double dir = Math.signum(tEnd - t);
        if (dir == 0) return true;
        if (Math.signum(dt) != dir) dt = -dt;
        while ((tEnd - t) * dir > 1e-9) {
            double rest = tEnd - t;
            boolean clamp = Math.abs(dt) > Math.abs(rest);
            double keep = dt;
            if (clamp) dt = rest;
            step();
            if (clamp && Math.abs(dt) < Math.abs(keep)) dt = keep;      // die Verkürzung war nur fürs Treffen
            if (budgetNanos > 0 && System.nanoTime() - t0 > budgetNanos) return (tEnd - t) * dir <= 1e-9;
        }
        return true;
    }

    /** Ein Schritt in Richtung tEnd, höchstens bis tEnd (für Prüfungen nach jedem Schritt). */
    public void stepTowards(double tEnd) {
        double rest = tEnd - t;
        if (rest == 0) return;
        if (Math.signum(dt) != Math.signum(rest)) dt = -dt;
        boolean clamp = Math.abs(dt) > Math.abs(rest);
        double keep = dt;
        if (clamp) dt = rest;
        step();
        if (clamp && Math.abs(dt) < Math.abs(keep)) dt = keep;
        if (Math.abs(tEnd - t) < 1e-9) t = tEnd;
    }

    /** Ein angenommener Schritt (verworfene werden mit kleinerer Weite wiederholt). */
    public void step() {
        for (int tries = 0; tries < 30; tries++) if (attempt()) return;
        throw new IllegalStateException("IAS15: Schritt konvergiert nicht (dt = " + dt + " s)");
    }

    private boolean attempt() {
        forces.acc(t, x, a0);
        evaluations++;
        // g aus b
        for (int k = 0; k < n3; k++)
            for (int j = 0; j < 7; j++) {
                double s = 0;
                for (int m = j; m < 7; m++) s += b[m][k] * Q[m][j];
                g[j][k] = s;
            }
        double pcErr = 1e300, pcLast = 2;
        for (int it = 0; ; it++) {
            if (pcErr < 1e-16) break;
            if (it > 2 && pcLast <= pcErr) break;                      // wird nicht mehr besser
            if (it >= 12) break;
            pcLast = pcErr;
            double maxDelta = 0, maxA = 0;
            for (int n = 1; n < 8; n++) {
                double s = H[n], sdt = s * dt;
                for (int k = 0; k < n3; k++) {
                    double acc = a0[k] * 0.5, sp = s;
                    for (int j = 0; j < 7; j++) { acc += b[j][k] * sp / ((j + 2) * (j + 3)); sp *= s; }
                    xs[k] = x[k] + sdt * v[k] + sdt * sdt * acc;
                }
                forces.acc(t + sdt, xs, at);
                evaluations++;
                double[] pj = P[n - 1];
                for (int k = 0; k < n3; k++) {
                    double tmp = (at[k] - a0[k]) / (H[n] - H[0]);
                    for (int j = 1; j < n; j++) tmp = (tmp - g[j - 1][k]) / (H[n] - H[j]);
                    double delta = tmp - g[n - 1][k];
                    g[n - 1][k] = tmp;
                    for (int j = 0; j < n; j++) b[j][k] += delta * pj[j];
                    if (n == 7) {
                        double d = Math.abs(delta), aa = Math.abs(at[k]);
                        if (d > maxDelta) maxDelta = d;
                        if (aa > maxA) maxA = aa;
                    }
                }
            }
            pcErr = maxA > 0 ? maxDelta / maxA : 0;
        }
        // Fehler und neue Schrittweite
        double maxB6 = 0, maxA = 0;
        for (int k = 0; k < n3; k++) {
            double b6 = Math.abs(b[6][k]), aa = Math.abs(at[k]);
            if (Double.isFinite(b6) && b6 > maxB6) maxB6 = b6;
            if (Double.isFinite(aa) && aa > maxA) maxA = aa;
        }
        double err = maxA > 0 ? maxB6 / maxA : 0;
        lastError = err;
        double dtDone = dt, dtNew;
        if (err > 0 && Double.isFinite(err)) dtNew = Math.pow(epsilon / err, 1.0 / 7.0) * dtDone;
        else dtNew = dtDone / safety;
        if (Math.abs(dtNew) < minDt) dtNew = Math.copySign(minDt, dtDone);
        if (Math.abs(dtNew / dtDone) < safety && Math.abs(dtDone) > minDt) {
            // verwerfen: kleiner neu versuchen, Vorhersage aus dem letzten angenommenen Schritt
            dt = dtNew;
            if (dtLastDone != 0) predict(dt / dtLastDone, er, br);
            else for (double[] r : b) java.util.Arrays.fill(r, 0);
            rejected++;
            return false;
        }
        if (Math.abs(dtNew / dtDone) > 1 / safety) dtNew = dtDone / safety;
        // annehmen: Lage und Geschwindigkeit am Schrittende (kompensierte Summation)
        for (int k = 0; k < n3; k++) {
            double dx = dtDone * v[k] + dtDone * dtDone * (a0[k] / 2 + b[0][k] / 6 + b[1][k] / 12 + b[2][k] / 20
                    + b[3][k] / 30 + b[4][k] / 42 + b[5][k] / 56 + b[6][k] / 72);
            double dv = dtDone * (a0[k] + b[0][k] / 2 + b[1][k] / 3 + b[2][k] / 4 + b[3][k] / 5 + b[4][k] / 6 + b[5][k] / 7 + b[6][k] / 8);
            double y = dx - csx[k], s = x[k] + y; csx[k] = (s - x[k]) - y; x[k] = s;
            y = dv - csv[k]; s = v[k] + y; csv[k] = (s - v[k]) - y; v[k] = s;
        }
        t += dtDone;
        dtLastDone = dtDone;
        dt = dtNew;
        for (int j = 0; j < 7; j++) { System.arraycopy(e[j], 0, er[j], 0, n3); System.arraycopy(b[j], 0, br[j], 0, n3); }
        predict(dtNew / dtDone, er, br);
        steps++;
        return true;
    }

    /** Vorhersage der b für den nächsten Schritt aus den konvergierten br und der alten Vorhersage er. */
    private void predict(double ratio, double[][] oldE, double[][] conv) {
        if (Math.abs(ratio) > 20) {
            for (int j = 0; j < 7; j++) { java.util.Arrays.fill(e[j], 0); java.util.Arrays.fill(b[j], 0); }
            return;
        }
        double[] q = new double[8];
        q[1] = ratio;
        for (int j = 2; j < 8; j++) q[j] = q[j - 1] * ratio;
        for (int k = 0; k < n3; k++) {
            for (int j = 0; j < 7; j++) {
                double s = 0;
                for (int m = j; m < 7; m++) s += BINOM[m + 1][j + 1] * conv[m][k];
                double be = conv[j][k] - oldE[j][k];
                e[j][k] = q[j + 1] * s;
                b[j][k] = e[j][k] + be;
            }
        }
    }

    private static final double[][] BINOM = new double[9][9];

    static {
        for (int n = 0; n < 9; n++) {
            BINOM[n][0] = 1;
            for (int k = 1; k <= n; k++) BINOM[n][k] = BINOM[n - 1][k - 1] + (k <= n - 1 ? BINOM[n - 1][k] : 0);
        }
    }
}
