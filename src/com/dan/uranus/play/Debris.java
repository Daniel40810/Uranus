package com.dan.uranus.play;

import com.dan.uranus.model.UranusSystem;

import java.util.Random;

/**
 * Trümmer eines zerbrochenen Monds: masselose Teilchen auf Kepler-Ellipsen um Uranus, deren Knoten und
 * Perizentren durch J2 wandern. Analytisch statt integriert — Staub zieht nicht aneinander, und so
 * kosten tausend Teilchen fast nichts. Unveränderlich; Zwischenstände teilen sich dieselben Trümmer.
 * <p>
 * Entstehung: Teilchen gleichmäßig im Mondvolumen, Geschwindigkeit des Monds plus eine Streuung von
 * einem Drittel seiner Fluchtgeschwindigkeit. Weil jedes Teilchen etwas anders weit von Uranus weg ist,
 * hat es eine andere Umlaufzeit — daraus zieht sich über die Umläufe der Ring.
 */
public final class Debris {

    public final String from;
    /** Spielzeit der Entstehung (s). */
    public final double t0;
    public final int count;
    private final double[] a, e, n, m0, dNode, dPeri;
    /** Perizentrums- und Normalenrichtung (Welt) zur Zeit t0, je 3 Werte. */
    private final double[] p, q;
    public final float red, green, blue;

    Debris(String from, double t0, double[] r, double[] v, double gmBody, double radiusKm, int count, long seed,
           float red, float green, float blue) {
        this.from = from; this.t0 = t0; this.red = red; this.green = green; this.blue = blue;
        double mu = UranusSystem.URANUS_GM;
        double vEsc = Math.sqrt(2 * Math.max(gmBody, 1e-9) / radiusKm);
        Random rnd = new Random(seed);
        double[] ta = new double[count], te = new double[count], tn = new double[count], tm = new double[count],
                tdn = new double[count], tdp = new double[count], tp = new double[count * 3], tq = new double[count * 3];
        int k = 0;
        double[] rr = new double[3], vv = new double[3];
        for (int tries = 0; k < count && tries < count * 4; tries++) {
            double ux, uy, uz;
            do { ux = rnd.nextDouble() * 2 - 1; uy = rnd.nextDouble() * 2 - 1; uz = rnd.nextDouble() * 2 - 1; } while (ux * ux + uy * uy + uz * uz > 1);
            rr[0] = r[0] + ux * radiusKm; rr[1] = r[1] + uy * radiusKm; rr[2] = r[2] + uz * radiusKm;
            double s = vEsc / 3;
            vv[0] = v[0] + rnd.nextGaussian() * s; vv[1] = v[1] + rnd.nextGaussian() * s; vv[2] = v[2] + rnd.nextGaussian() * s;
            if (!elements(rr, vv, mu, k, ta, te, tn, tm, tdn, tdp, tp, tq)) continue;
            k++;
        }
        this.count = k;
        a = ta; e = te; n = tn; m0 = tm; dNode = tdn; dPeri = tdp; p = tp; q = tq;
    }

    /** Bahnelemente eines Teilchens; false, wenn es ungebunden ist oder in Uranus stürzt. */
    private static boolean elements(double[] r, double[] v, double mu, int k, double[] ta, double[] te, double[] tn, double[] tm,
                                    double[] tdn, double[] tdp, double[] tp, double[] tq) {
        double rn = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
        double v2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
        double aa = 1 / (2 / rn - v2 / mu);
        if (!(aa > 0)) return false;
        double hx = r[1] * v[2] - r[2] * v[1], hy = r[2] * v[0] - r[0] * v[2], hz = r[0] * v[1] - r[1] * v[0];
        double hn = Math.sqrt(hx * hx + hy * hy + hz * hz);
        double rv = r[0] * v[0] + r[1] * v[1] + r[2] * v[2];
        double ex = (v2 - mu / rn) * r[0] / mu - rv * v[0] / mu, ey = (v2 - mu / rn) * r[1] / mu - rv * v[1] / mu,
                ez = (v2 - mu / rn) * r[2] / mu - rv * v[2] / mu;
        double ee = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (ee >= 0.98 || aa * (1 - ee) < UranusSystem.URANUS_RADIUS_KM * 1.02) return false;
        double px, py, pz;
        if (ee > 1e-9) { px = ex / ee; py = ey / ee; pz = ez / ee; } else { px = r[0] / rn; py = r[1] / rn; pz = r[2] / rn; }
        double wx = hx / hn, wy = hy / hn, wz = hz / hn;                          // Normale
        double qx = wy * pz - wz * py, qy = wz * px - wx * pz, qz = wx * py - wy * px;
        // exzentrische und mittlere Anomalie jetzt
        double cosE = (1 - rn / aa) / Math.max(ee, 1e-12), sinE = rv / (ee * Math.sqrt(mu * aa) + 1e-300);
        double bigE = ee > 1e-9 ? Math.atan2(sinE, cosE) : Math.atan2(r[0] * qx + r[1] * qy + r[2] * qz, r[0] * px + r[1] * py + r[2] * pz);
        double mean = bigE - ee * Math.sin(bigE);
        double nn = Math.sqrt(mu / (aa * aa * aa));
        // J2: Knoten und Perizentrum wandern (Neigung gegen den Äquator = Welt-y)
        double cosI = wy, pp = aa * (1 - ee * ee), f = nn * UranusSystem.J2 * Math.pow(UranusSystem.URANUS_RADIUS_KM / pp, 2);
        ta[k] = aa; te[k] = ee; tn[k] = nn; tm[k] = mean;
        tdn[k] = -1.5 * f * cosI;
        tdp[k] = 0.75 * f * (5 * cosI * cosI - 1);
        tp[3 * k] = px; tp[3 * k + 1] = py; tp[3 * k + 2] = pz;
        tq[3 * k] = qx; tq[3 * k + 1] = qy; tq[3 * k + 2] = qz;
        return true;
    }

    /** Lage des Teilchens k zur Spielzeit t (s), relativ zu Uranus (Welt, km). */
    public void position(int k, double t, double[] out) {
        double dt = t - t0;
        double ee = e[k], mean = m0[k] + n[k] * dt;
        double bigE = mean;
        for (int it = 0; it < 12; it++) {
            double d = (bigE - ee * Math.sin(bigE) - mean) / (1 - ee * Math.cos(bigE));
            bigE -= d;
            if (Math.abs(d) < 1e-10) break;
        }
        double xo = a[k] * (Math.cos(bigE) - ee), yo = a[k] * Math.sqrt(1 - ee * ee) * Math.sin(bigE);
        // Perizentrum in der Ebene drehen, dann die Ebene um die Achse (Welt-y)
        double w = dPeri[k] * dt, cw = Math.cos(w), sw = Math.sin(w);
        double x1 = xo * cw - yo * sw, y1 = xo * sw + yo * cw;
        double px = p[3 * k], py = p[3 * k + 1], pz = p[3 * k + 2], qx = q[3 * k], qy = q[3 * k + 1], qz = q[3 * k + 2];
        double x = x1 * px + y1 * qx, y = x1 * py + y1 * qy, z = x1 * pz + y1 * qz;
        double o = dNode[k] * dt, co = Math.cos(o), so = Math.sin(o);
        out[0] = x * co + z * so;
        out[1] = y;
        out[2] = -x * so + z * co;
    }

    /** Größte und kleinste Halbachse (km) — für Anzeige und Prüfung. */
    public double[] aRange() {
        double lo = Double.MAX_VALUE, hi = 0;
        for (int k = 0; k < count; k++) { lo = Math.min(lo, a[k]); hi = Math.max(hi, a[k]); }
        return new double[]{lo, hi};
    }
}
