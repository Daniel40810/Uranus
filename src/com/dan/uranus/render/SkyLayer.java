package com.dan.uranus.render;

import com.dan.uranus.fx.Noise;
import com.dan.uranus.physics.Ephemeris;

import java.util.Random;
import java.util.stream.IntStream;

/**
 * Hintergrund: Milchstraße in ihrer echten Lage (galaktische Koordinaten → Welt), Sterne,
 * die ferne Sonne — und die Gravitationslinse, die all das um Uranus verbiegt.
 * <p>
 * Linse: Punktmassen-Linse im Bildraum mit überhöhtem Einstein-Radius θE. Die diffuse
 * Milchstraße wird rückwärts abgebildet (Bildpunkt → Quellpunkt β = θ·(1 − θE²/θ²)),
 * die Sterne vorwärts (je Stern zwei Bilder θ± mit Verstärkung μ±). Hinter Uranus
 * entstehen so Bögen und ein Einstein-Ring.
 */
public final class SkyLayer {

    private static final int TW = 1024, TH = 512, CUBE = 384;
    /** Milchstraße als Würfelkarte in Weltrichtungen: keine Winkelfunktionen je Bildpunkt. */
    private final float[][] cube = new float[6][CUBE * CUBE * 3];
    private final float[] texR = new float[TW * TH], texG = new float[TW * TH], texB = new float[TW * TH];

    private final int starCount;
    private final double[] sx, sy, sz;
    private final float[] sr, sg, sb;

    private float[] halfR = new float[0], halfG = new float[0], halfB = new float[0];
    private int halfW, halfH;
    private int[] colIdx = new int[0];
    private float[] colW = new float[0];
    /** Abtastschritt des diffusen Himmels in Pixeln. */
    private static final int SKY_STEP = 3;

    public SkyLayer(long seed) {
        buildMilkyWay();
        buildCube();
        Random rnd = new Random(seed);
        starCount = 16000;
        sx = new double[starCount]; sy = new double[starCount]; sz = new double[starCount];
        sr = new float[starCount]; sg = new float[starCount]; sb = new float[starCount];
        double[] icrf = new double[3], w = new double[3];
        for (int i = 0; i < starCount; i++) {
            double l = rnd.nextDouble() * Math.PI * 2, b;
            if (rnd.nextDouble() < 0.55) b = rnd.nextGaussian() * 0.16;
            else b = Math.asin(2 * rnd.nextDouble() - 1);
            double cb = Math.cos(b);
            double gx = cb * Math.cos(l), gy = cb * Math.sin(l), gz = Math.sin(b);
            for (int k = 0; k < 3; k++) icrf[k] = gx * Ephemeris.GAL_X[k] + gy * Ephemeris.GAL_Y[k] + gz * Ephemeris.GAL_Z[k];
            Ephemeris.icrfToWorld(icrf, w);
            sx[i] = w[0]; sy[i] = w[1]; sz[i] = w[2];
            double u = rnd.nextDouble();
            float inten = (float) (0.05 + 0.30 * u * u * u + 3.0 * Math.pow(u, 16));
            double t = rnd.nextDouble();
            float cr, cg, cbb;
            if (t < 0.14) { cr = 1.0f; cg = 0.80f; cbb = 0.62f; }
            else if (t < 0.32) { cr = 1.0f; cg = 0.93f; cbb = 0.82f; }
            else if (t < 0.72) { cr = 0.95f; cg = 0.97f; cbb = 1.0f; }
            else { cr = 0.74f; cg = 0.84f; cbb = 1.0f; }
            sr[i] = cr * inten; sg[i] = cg * inten; sb[i] = cbb * inten;
        }
    }

    private void buildMilkyWay() {
        IntStream.range(0, TH).parallel().forEach(j -> {
            double b = Math.PI / 2 - (j + 0.5) / TH * Math.PI;
            for (int i = 0; i < TW; i++) {
                double l = (i + 0.5) / TW * Math.PI * 2 - Math.PI;
                // Rauschen auf der Kugel auswerten, damit die Naht bei l = ±180° verschwindet
                double cb = Math.cos(b), px = cb * Math.cos(l), py = cb * Math.sin(l), pz = Math.sin(b);
                double sigma = 0.085 + 0.05 * Noise.fbm(px * 1.4 + 3, py * 1.4, pz * 1.4, 3);
                double disk = Math.exp(-(b / sigma) * (b / sigma)) * (0.45 + 0.55 * Noise.fbm(px * 5, py * 5, pz * 5 + 1.7, 4));
                double bulge = 1.4 * Math.exp(-(l * l / 0.09 + b * b / 0.026));
                double lane = Math.exp(-Math.pow((b - 0.012 * Math.sin(l * 3)) / 0.02, 2))
                        * Noise.smooth(0.35, 0.7, Noise.fbm(px * 9, py * 9, pz * 9 + 5, 3));
                double v = (disk * 0.9 + bulge) * (1 - 0.8 * lane);
                double halo = 0.12 * Math.exp(-(b / 0.45) * (b / 0.45)) * Noise.fbm(px * 2.5, py * 2.5 + 7, pz * 2.5, 3);
                double neb = Math.max(0, Noise.fbm(px * 3.3 + 9, py * 3.3, pz * 3.3, 4) - 0.58) * Math.exp(-(b / 0.2) * (b / 0.2));
                double warm = bulge / (bulge + disk + 1e-6);
                double k = 0.075;
                int idx = j * TW + i;
                texR[idx] = (float) (k * (v * (0.72 + 0.28 * warm) + halo * 0.7) + neb * 0.05);
                texG[idx] = (float) (k * (v * (0.80 + 0.06 * warm) + halo * 0.75) + neb * 0.018);
                texB[idx] = (float) (k * (v * (1.00 - 0.30 * warm) + halo * 0.95) + neb * 0.026);
            }
        });
    }

    private void buildCube() {
        IntStream.range(0, 6 * CUBE).parallel().forEach(fj -> {
            int face = fj / CUBE, j = fj % CUBE;
            double[] d = new double[3], ic = new double[3];
            for (int i = 0; i < CUBE; i++) {
                double u = (i + 0.5) / CUBE * 2 - 1, v = (j + 0.5) / CUBE * 2 - 1;
                switch (face) {
                    case 0: d[0] = 1; d[1] = -v; d[2] = -u; break;
                    case 1: d[0] = -1; d[1] = -v; d[2] = u; break;
                    case 2: d[0] = u; d[1] = 1; d[2] = v; break;
                    case 3: d[0] = u; d[1] = -1; d[2] = -v; break;
                    case 4: d[0] = u; d[1] = -v; d[2] = 1; break;
                    default: d[0] = -u; d[1] = -v; d[2] = -1;
                }
                double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                Ephemeris.worldToIcrf(d[0] / l, d[1] / l, d[2] / l, ic);
                double gx = Ephemeris.dot(ic, Ephemeris.GAL_X), gy = Ephemeris.dot(ic, Ephemeris.GAL_Y), gz = Ephemeris.dot(ic, Ephemeris.GAL_Z);
                double lo = Math.atan2(gy, gx), la = Math.asin(Math.max(-1, Math.min(1, gz)));
                double tu = (lo + Math.PI) / (2 * Math.PI) * TW - 0.5, tv = (Math.PI / 2 - la) / Math.PI * TH - 0.5;
                int x0 = (int) Math.floor(tu), y0 = (int) Math.floor(tv);
                double fu = tu - x0, fv = tv - y0;
                int xa = ((x0 % TW) + TW) % TW, xb = (xa + 1) % TW;
                int ya = Math.max(0, Math.min(TH - 1, y0)), yb = Math.max(0, Math.min(TH - 1, y0 + 1));
                int o = (j * CUBE + i) * 3;
                cube[face][o] = bil(texR, xa, xb, ya, yb, fu, fv);
                cube[face][o + 1] = bil(texG, xa, xb, ya, yb, fu, fv);
                cube[face][o + 2] = bil(texB, xa, xb, ya, yb, fu, fv);
            }
        });
    }

    /** Liest die Würfelkarte in Richtung (x, y, z), bilinear. */
    private void sampleCube(double x, double y, double z, float[] out) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z), u, v;
        int face;
        if (ax >= ay && ax >= az) { face = x > 0 ? 0 : 1; u = (x > 0 ? -z : z) / ax; v = -y / ax; }
        else if (ay >= az) { face = y > 0 ? 2 : 3; u = x / ay; v = (y > 0 ? z : -z) / ay; }
        else { face = z > 0 ? 4 : 5; u = (z > 0 ? x : -x) / az; v = -y / az; }
        double fu = (u + 1) * 0.5 * CUBE - 0.5, fv = (v + 1) * 0.5 * CUBE - 0.5;
        int i0 = (int) Math.floor(fu), j0 = (int) Math.floor(fv);
        float tu = (float) (fu - i0), tv = (float) (fv - j0);
        int i1 = Math.min(CUBE - 1, i0 + 1), j1 = Math.min(CUBE - 1, j0 + 1);
        i0 = Math.max(0, i0); j0 = Math.max(0, j0);
        float[] c = cube[face];
        int a = (j0 * CUBE + i0) * 3, b = (j0 * CUBE + i1) * 3, cc = (j1 * CUBE + i0) * 3, d = (j1 * CUBE + i1) * 3;
        float wa = (1 - tu) * (1 - tv), wb = tu * (1 - tv), wc = (1 - tu) * tv, wd = tu * tv;
        out[0] = c[a] * wa + c[b] * wb + c[cc] * wc + c[d] * wd;
        out[1] = c[a + 1] * wa + c[b + 1] * wb + c[cc + 1] * wc + c[d + 1] * wd;
        out[2] = c[a + 2] * wa + c[b + 2] * wb + c[cc + 2] * wc + c[d + 2] * wd;
    }

    /**
     * Zeichnet den Hintergrund (überschreibt den Puffer).
     *
     * @param lensX,lensY Bildmitte von Uranus
     * @param thetaE      Einstein-Radius in Pixeln (0 = keine Linse)
     * @param sun         Richtung zur Sonne (Welt)
     */
    public void render(Hdr hdr, Camera cam, double lensX, double lensY, double thetaE, double[] sun) {
        int w = hdr.w, h = hdr.h;
        // Steht die Kamera still und hat sich an Linse und Sonne nichts geändert, ist der Himmel derselbe wie im letzten Bild
        double[] key = {w, h, cam.cx, cam.cy, cam.cz, cam.fx, cam.fy, cam.fz, cam.rx, cam.ry, cam.rz, cam.ux, cam.uy, cam.uz,
                cam.foc, cam.halfW, cam.halfH, lensX, lensY, thetaE, sun[0], sun[1], sun[2]};
        if (!PlanetLayer.exact && java.util.Arrays.equals(key, cacheKey) && cacheR.length == w * h) {
            System.arraycopy(cacheR, 0, hdr.r, 0, w * h);
            System.arraycopy(cacheG, 0, hdr.g, 0, w * h);
            System.arraycopy(cacheB, 0, hdr.b, 0, w * h);
            cacheHits++;
            return;
        }
        final int st = SKY_STEP;
        int hw = w / st + 2, hh = h / st + 2;
        if (hw * hh != halfR.length) { halfR = new float[hw * hh]; halfG = new float[hw * hh]; halfB = new float[hw * hh]; }
        halfW = hw; halfH = hh;
        final double te2 = thetaE * thetaE;
        long q0 = System.nanoTime();
        // 1) diffuser Himmel in reduzierter Auflösung (die Milchstraße ist weich), Linse rückwärts
        IntStream.range(0, hh).parallel().forEach(j -> {
            float[] c = new float[3];
            for (int i = 0; i < hw; i++) {
                double px = i * st, py = j * st;
                if (te2 > 0) {
                    double ox = px - lensX, oy = py - lensY, r2 = ox * ox + oy * oy;
                    if (r2 > 1) { double f = 1 - te2 / r2; px = lensX + ox * f; py = lensY + oy * f; }
                }
                // Richtung wie Camera.rayDir, aber ohne Normieren — die Würfelkarte teilt ohnehin durch die größte Komponente
                if (PlanetLayer.exact) {
                    double[] d = new double[3];
                    cam.rayDir(px, py, d);
                    sampleCube(d[0], d[1], d[2], c);
                } else {
                    double a = (px - cam.halfW) / cam.foc, b = (cam.halfH - py) / cam.foc;
                    sampleCube(cam.fx + a * cam.rx + b * cam.ux, cam.fy + a * cam.ry + b * cam.uy, cam.fz + a * cam.rz + b * cam.uz, c);
                }
                int k = j * hw + i;
                halfR[k] = c[0]; halfG[k] = c[1]; halfB[k] = c[2];
            }
        });
        long q1 = System.nanoTime();
        // 2) auf volle Auflösung (Spaltengewichte einmal je Bild)
        if (colIdx.length != w) { colIdx = new int[w]; colW = new float[w]; }
        for (int x = 0; x < w; x++) { double u = (double) x / st; int i0 = (int) u; colIdx[x] = Math.min(i0, hw - 2); colW[x] = (float) (u - colIdx[x]); }
        final int[] ci = colIdx; final float[] cw = colW;
        IntStream.range(0, h).parallel().forEach(y -> {
            double v = (double) y / st;
            int j0 = Math.min((int) v, hh - 2); float fv = (float) (v - j0);
            int r0 = j0 * hw, r1 = r0 + hw;
            int row = y * w;
            float[] hr = halfR, hg = halfG, hb = halfB;
            for (int x = 0; x < w; x++) {
                int i0 = ci[x]; float fu = cw[x];
                int a = r0 + i0, c = r1 + i0;
                float t0r = hr[a] + (hr[a + 1] - hr[a]) * fu, t1r = hr[c] + (hr[c + 1] - hr[c]) * fu;
                float t0g = hg[a] + (hg[a + 1] - hg[a]) * fu, t1g = hg[c] + (hg[c + 1] - hg[c]) * fu;
                float t0b = hb[a] + (hb[a + 1] - hb[a]) * fu, t1b = hb[c] + (hb[c + 1] - hb[c]) * fu;
                int o = row + x;
                hdr.r[o] = 0.0035f + t0r + (t1r - t0r) * fv;
                hdr.g[o] = 0.0050f + t0g + (t1g - t0g) * fv;
                hdr.b[o] = 0.0095f + t0b + (t1b - t0b) * fv;
            }
        });
        long q2 = System.nanoTime();
        // 3) Sterne, mit Linse vorwärts abgebildet
        double[] p = new double[3];
        double influence = thetaE * 16;
        for (int i = 0; i < starCount; i++) {
            if (!cam.projectDir(sx[i], sy[i], sz[i], p)) continue;
            double x = p[0], y = p[1];
            float cr = sr[i], cg = sg[i], cbb = sb[i];
            if (thetaE > 0.5) {
                double ox = x - lensX, oy = y - lensY, r = Math.sqrt(ox * ox + oy * oy);
                if (r < influence) {
                    double sq = Math.sqrt(r * r + 4 * te2), u = r / thetaE;
                    double mp = (u * u + 2) / (2 * u * Math.sqrt(u * u + 4) + 1e-9) + 0.5, mm = mp - 1;
                    double rp = (r + sq) / 2, rm = (sq - r) / 2, ax = ox / (r + 1e-9), ay = oy / (r + 1e-9);
                    float kp = (float) Math.min(mp, 6), km = (float) Math.min(mm, 6);
                    star(hdr, lensX + ax * rp, lensY + ay * rp, cr * kp, cg * kp, cbb * kp);
                    star(hdr, lensX - ax * rm, lensY - ay * rm, cr * km, cg * km, cbb * km);
                    continue;
                }
            }
            if (x < -3 || y < -3 || x > w + 3 || y > h + 3) continue;
            star(hdr, x, y, cr, cg, cbb);
        }
        long q3 = System.nanoTime();
        // 4) die Sonne — aus 19 AE ein gleißender Stern
        if (cam.projectDir(sun[0], sun[1], sun[2], p)) {
            double x = p[0], y = p[1];
            hdr.glow(x, y, 46, 0.16f, 0.14f, 0.11f);
            hdr.glow(x, y, 10, 1.6f, 1.45f, 1.2f);
            hdr.splat(x, y, 30f, 28f, 25f);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + 0.26, len = 80;
                for (int s = 0; s < 6; s++) {
                    double t0 = s / 6.0 * len, t1 = (s + 1) / 6.0 * len;
                    float f = (float) (0.9 * Math.pow(1 - s / 6.0, 2));
                    hdr.line(x + Math.cos(a) * t0, y + Math.sin(a) * t0, x + Math.cos(a) * t1, y + Math.sin(a) * t1, 1f, 0.95f, 0.85f, f, true);
                }
            }
        }
        // aufheben erst, wenn die Kamera schon im Bild davor stand — bei Bewegung kostet das Kopieren nur
        if (java.util.Arrays.equals(key, lastKey)) {
            if (cacheR.length != w * h) { cacheR = new float[w * h]; cacheG = new float[w * h]; cacheB = new float[w * h]; }
            System.arraycopy(hdr.r, 0, cacheR, 0, w * h);
            System.arraycopy(hdr.g, 0, cacheG, 0, w * h);
            System.arraycopy(hdr.b, 0, cacheB, 0, w * h);
            cacheKey = key;
        }
        lastKey = key;
        if (Renderer.PROFILE) System.out.printf(java.util.Locale.ROOT, "SKY diffus %.1f fuellen %.1f sterne %.1f sonne %.1f%n", (q1-q0)/1e6, (q2-q1)/1e6, (q3-q2)/1e6, (System.nanoTime()-q3)/1e6);
    }

    private double[] cacheKey = new double[0], lastKey = new double[0];
    private float[] cacheR = new float[0], cacheG = new float[0], cacheB = new float[0];
    private long cacheHits;

    /** Wie oft der Himmel unverändert aus dem letzten Bild übernommen wurde (für Prüfungen). */
    public long cacheHits() { return cacheHits; }

    private static void star(Hdr hdr, double x, double y, float r, float g, float b) {
        hdr.splat(x, y, r, g, b);
        float m = Math.max(r, Math.max(g, b));
        if (m > 0.9f) hdr.glow(x, y, 2.2 + Math.min(4, m), r * 0.18f, g * 0.18f, b * 0.18f);
    }

    private static float bil(float[] t, int xa, int xb, int ya, int yb, double fu, double fv) {
        double top = t[ya * TW + xa] * (1 - fu) + t[ya * TW + xb] * fu;
        double bot = t[yb * TW + xa] * (1 - fu) + t[yb * TW + xb] * fu;
        return (float) (top * (1 - fv) + bot * fv);
    }
}
