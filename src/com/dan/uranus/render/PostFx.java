package com.dan.uranus.render;

import java.util.stream.IntStream;

/**
 * Nachbearbeitung: Bloom in Viertelauflösung (helle Stellen glühen nach) und Tonwertkurve
 * vom HDR-Puffer auf 8-Bit-RGB. Beides parallel über die Zeilen.
 */
public final class PostFx {

    private float[] qr = new float[0], qg = new float[0], qb = new float[0], tr = new float[0], tg = new float[0], tb = new float[0];
    private int qw, qh;
    private int[] colA = new int[0], colB = new int[0];
    private float[] colF = new float[0];
    public float threshold = 0.55f, bloomStrength = 0.75f;
    private float exposure = 1.3f;
    /** Tonwertkurve 1 − e^(−c·Belichtung) als Tabelle: c in Schritten von 1/1024 bis 12. */
    private static final int LUT_SCALE = 1024, LUT_MAX = 12 * LUT_SCALE;
    private final float[] lut = new float[LUT_MAX + 1];

    public PostFx() { setExposure(1.3f); }

    public void setExposure(float e) {
        exposure = e;
        for (int i = 0; i <= LUT_MAX; i++) lut[i] = (float) ((1 - Math.exp(-(double) i / LUT_SCALE * e)) * 255.0);
    }

    public void apply(Hdr hdr, int[] out, boolean bloom) {
        int w = hdr.w, h = hdr.h;
        long q0 = System.nanoTime();
        if (bloom) bloomPass(hdr);
        long q1 = System.nanoTime();
        final boolean useBloom = bloom;
        final float bs = bloomStrength;
        final float[] lt = lut;
        if (colA.length != w) { colA = new int[w]; colB = new int[w]; colF = new float[w]; }
        if (useBloom) for (int x = 0; x < w; x++) {
            double u = (x + 0.5) / 4.0 - 0.5; int i0 = (int) Math.floor(u);
            colF[x] = (float) (u - i0); colA[x] = clamp(i0, 0, qw - 1); colB[x] = clamp(i0 + 1, 0, qw - 1);
        }
        final int[] ca = colA, cb = colB; final float[] cf = colF;
        final boolean[] lit = bloomLit;
        IntStream.range(0, h).parallel().forEach(y -> {
            int row = y * w;
            double v = (y + 0.5) / 4.0 - 0.5;
            int j0 = (int) Math.floor(v); float fv = (float) (v - j0);
            int ja = useBloom ? clamp(j0, 0, qh - 1) * qw : 0, jb = useBloom ? clamp(j0 + 1, 0, qh - 1) * qw : 0;
            float[] hr = hdr.r, hg = hdr.g, hb = hdr.b;
            for (int x = 0; x < w; x++) {
                int i = row + x;
                float r = hr[i], g = hg[i], b = hb[i];
                if (useBloom) {
                    int ia = ca[x], ib = cb[x]; float fu = cf[x];
                    int a0 = ja + ia, a1 = ja + ib, a2 = jb + ia, a3 = jb + ib;
                    if (lit[a0] | lit[a1] | lit[a2] | lit[a3] | PlanetLayer.exact) {
                    float tr0 = qr[a0] + (qr[a1] - qr[a0]) * fu, tr1 = qr[a2] + (qr[a3] - qr[a2]) * fu;
                    float tg0 = qg[a0] + (qg[a1] - qg[a0]) * fu, tg1 = qg[a2] + (qg[a3] - qg[a2]) * fu;
                    float tb0 = qb[a0] + (qb[a1] - qb[a0]) * fu, tb1 = qb[a2] + (qb[a3] - qb[a2]) * fu;
                    r += bs * (tr0 + (tr1 - tr0) * fv);
                    g += bs * (tg0 + (tg1 - tg0) * fv);
                    b += bs * (tb0 + (tb1 - tb0) * fv);
                    }
                }
                // leichtes geordnetes Rauschen gegen Stufen in dunklen Verläufen
                float dither = 0.5f + (((x * 7 + y * 13) & 7) - 3.5f) * 0.09f;
                out[i] = (tone(lt, r, dither) << 16) | (tone(lt, g, dither) << 8) | tone(lt, b, dither);
            }
        });
        if (Renderer.PROFILE) System.out.printf(java.util.Locale.ROOT, "POST bloom %.1f ton %.1f%n", (q1-q0)/1e6, (System.nanoTime()-q1)/1e6);
    }

    private static int tone(float[] lut, float c, float dither) {
        if (c <= 0) return 0;
        int i = (int) (c * LUT_SCALE);
        int k = (int) ((i >= LUT_MAX ? lut[LUT_MAX] : lut[i]) + dither);
        return k > 255 ? 255 : k;
    }

    private void bloomPass(Hdr hdr) {
        int w = hdr.w, h = hdr.h;
        qw = (w + 3) / 4; qh = (h + 3) / 4;
        int n = qw * qh;
        if (qr.length != n) { qr = new float[n]; qg = new float[n]; qb = new float[n]; tr = new float[n]; tg = new float[n]; tb = new float[n]; }
        final float th = threshold;
        IntStream.range(0, qh).parallel().forEach(j -> {
            for (int i = 0; i < qw; i++) {
                float r = 0, g = 0, b = 0; int c = 0;
                for (int yy = j * 4; yy < Math.min(h, j * 4 + 4); yy++)
                    for (int xx = i * 4; xx < Math.min(w, i * 4 + 4); xx++) {
                        int o = yy * w + xx;
                        float m = Math.max(hdr.r[o], Math.max(hdr.g[o], hdr.b[o]));
                        if (m > th) { float k = (m - th) / m; r += hdr.r[o] * k; g += hdr.g[o] * k; b += hdr.b[o] * k; }
                        c++;
                    }
                int q = j * qw + i;
                qr[q] = r / c; qg[q] = g / c; qb[q] = b / c;
            }
        });
        blur(qr, qg, qb, 3);
        blur(qr, qg, qb, 7);
        // wo der Schein genau null ist, braucht die Tonwertkurve ihn nicht einzumischen
        if (bloomLit.length != n) bloomLit = new boolean[n];
        for (int q = 0; q < n; q++) bloomLit[q] = qr[q] != 0 || qg[q] != 0 || qb[q] != 0;
    }

    private boolean[] bloomLit = new boolean[0];

    private static final float[] K = {0.0702f, 0.1311f, 0.1907f, 0.2160f, 0.1907f, 0.1311f, 0.0702f};

    /** Separierbarer Weichzeichner, step = Abstand der Stützstellen (breiter Schein bei größerem step). */
    private void blur(float[] r, float[] g, float[] b, int step) {
        final int w = qw, h = qh;
        IntStream.range(0, h).parallel().forEach(j -> {
            for (int i = 0; i < w; i++) {
                float sr = 0, sg = 0, sb = 0;
                for (int k = -3; k <= 3; k++) {
                    int x = clamp(i + k * step / 3, 0, w - 1), o = j * w + x;
                    sr += r[o] * K[k + 3]; sg += g[o] * K[k + 3]; sb += b[o] * K[k + 3];
                }
                int o = j * w + i; tr[o] = sr; tg[o] = sg; tb[o] = sb;
            }
        });
        IntStream.range(0, h).parallel().forEach(j -> {
            for (int i = 0; i < w; i++) {
                float sr = 0, sg = 0, sb = 0;
                for (int k = -3; k <= 3; k++) {
                    int y = clamp(j + k * step / 3, 0, h - 1), o = y * w + i;
                    sr += tr[o] * K[k + 3]; sg += tg[o] * K[k + 3]; sb += tb[o] * K[k + 3];
                }
                int o = j * w + i; r[o] = sr; g[o] = sg; b[o] = sb;
            }
        });
    }

    private static int clamp(int v, int a, int b) { return v < a ? a : v > b ? b : v; }
}
