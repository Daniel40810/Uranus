package com.dan.uranus.render;

/** Leuchtpunkte eines Bildes (Bildkoordinaten), getrennt nach vor/hinter Uranus wie {@link SegmentList}. */
final class PointList {

    private float[] d = new float[6 * 1024];
    private int n;

    void clear() { n = 0; }

    int size() { return n; }

    void add(double x, double y, double radius, float r, float g, float b) {
        if (n * 6 == d.length) { float[] nd = new float[d.length * 2]; System.arraycopy(d, 0, nd, 0, d.length); d = nd; }
        int o = n * 6;
        d[o] = (float) x; d[o + 1] = (float) y; d[o + 2] = (float) radius; d[o + 3] = r; d[o + 4] = g; d[o + 5] = b;
        n++;
    }

    void draw(Hdr hdr) {
        for (int i = 0; i < n; i++) {
            int o = i * 6;
            float rad = d[o + 2];
            if (rad <= 1.2f) hdr.splat(d[o], d[o + 1], d[o + 3], d[o + 4], d[o + 5]);
            else hdr.glow(d[o], d[o + 1], rad, d[o + 3], d[o + 4], d[o + 5]);
        }
    }
}
