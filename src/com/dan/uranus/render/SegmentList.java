package com.dan.uranus.render;

/**
 * Sammelt Liniensegmente eines Bildes, getrennt nach "hinter Uranus" und "davor".
 * Die hinteren werden vor dem Planeten gezeichnet (und von ihm verdeckt), die vorderen danach.
 */
final class SegmentList {

    private float[] d = new float[9 * 4096];
    private boolean[] additive = new boolean[4096];
    private int n;

    void clear() { n = 0; }

    int size() { return n; }

    void add(double x0, double y0, double x1, double y1, float r, float g, float b, float a, boolean add) {
        if (a <= 0.002f) return;
        if (n == additive.length) {
            float[] nd = new float[d.length * 2]; System.arraycopy(d, 0, nd, 0, d.length); d = nd;
            boolean[] na = new boolean[additive.length * 2]; System.arraycopy(additive, 0, na, 0, additive.length); additive = na;
        }
        int o = n * 9;
        d[o] = (float) x0; d[o + 1] = (float) y0; d[o + 2] = (float) x1; d[o + 3] = (float) y1;
        d[o + 4] = r; d[o + 5] = g; d[o + 6] = b; d[o + 7] = a;
        additive[n] = add;
        n++;
    }

    void draw(Hdr hdr) {
        for (int i = 0; i < n; i++) {
            int o = i * 9;
            hdr.line(d[o], d[o + 1], d[o + 2], d[o + 3], d[o + 4], d[o + 5], d[o + 6], d[o + 7], additive[i]);
        }
    }
}
