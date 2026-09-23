package com.dan.uranus.render;

/**
 * Bildpuffer mit hohem Dynamikumfang (float je Kanal). Alle Ebenen zeichnen hier hinein;
 * erst {@link PostFx} bildet ihn mit Bloom und Tonwertkurve auf 8 Bit ab.
 * <p>
 * Linien mit Kantenglättung nach Xiaolin Wu, wahlweise additiv (Leuchten) oder deckend
 * (Ringe vor dem Planeten).
 */
public final class Hdr {

    public final int w, h;
    public final float[] r, g, b;

    public Hdr(int w, int h) {
        this.w = w; this.h = h;
        r = new float[w * h]; g = new float[w * h]; b = new float[w * h];
    }

    public void add(int x, int y, float cr, float cg, float cb) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        int i = y * w + x;
        r[i] += cr; g[i] += cg; b[i] += cb;
    }

    public void over(int x, int y, float cr, float cg, float cb, float a) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0) return;
        int i = y * w + x;
        float k = 1 - a;
        r[i] = r[i] * k + cr * a; g[i] = g[i] * k + cg * a; b[i] = b[i] * k + cb * a;
    }

    /** Bilineares Einsprenkeln eines Punktes (Sterne, kleine Monde). */
    public void splat(double x, double y, float cr, float cg, float cb) {
        int x0 = (int) Math.floor(x - 0.5), y0 = (int) Math.floor(y - 0.5);
        float fx = (float) (x - 0.5 - x0), fy = (float) (y - 0.5 - y0);
        float a00 = (1 - fx) * (1 - fy), a10 = fx * (1 - fy), a01 = (1 - fx) * fy, a11 = fx * fy;
        add(x0, y0, cr * a00, cg * a00, cb * a00);
        add(x0 + 1, y0, cr * a10, cg * a10, cb * a10);
        add(x0, y0 + 1, cr * a01, cg * a01, cb * a01);
        add(x0 + 1, y0 + 1, cr * a11, cg * a11, cb * a11);
    }

    /** Weicher Leuchtfleck, additiv, Abfall ~ (1 − d/r)². */
    public void glow(double x, double y, double radius, float cr, float cg, float cb) {
        int x0 = (int) Math.floor(x - radius), x1 = (int) Math.ceil(x + radius);
        int y0 = (int) Math.floor(y - radius), y1 = (int) Math.ceil(y + radius);
        if (x1 < 0 || y1 < 0 || x0 >= w || y0 >= h) return;
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(w - 1, x1); y1 = Math.min(h - 1, y1);
        double inv = 1.0 / radius;
        for (int yy = y0; yy <= y1; yy++) {
            double dy = (yy + 0.5 - y) * inv;
            int row = yy * w;
            for (int xx = x0; xx <= x1; xx++) {
                double dx = (xx + 0.5 - x) * inv, d = dx * dx + dy * dy;
                if (d >= 1) continue;
                float f = (float) ((1 - Math.sqrt(d)) * (1 - Math.sqrt(d)));
                int i = row + xx;
                r[i] += cr * f; g[i] += cg * f; b[i] += cb * f;
            }
        }
    }

    // ------------------------------------------------------------------ Linien

    private final double[] clip = new double[4];

    /** Liang–Barsky gegen den Bildrand (mit 2 px Rand), ohne Allokation. */
    private boolean clip(double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double[] t = {0, 1};
        if (!edge(-dx, x0 + 2, t) || !edge(dx, w + 1 - x0, t) || !edge(-dy, y0 + 2, t) || !edge(dy, h + 1 - y0, t)) return false;
        clip[0] = x0 + t[0] * dx; clip[1] = y0 + t[0] * dy; clip[2] = x0 + t[1] * dx; clip[3] = y0 + t[1] * dy;
        return true;
    }

    private static boolean edge(double p, double q, double[] t) {
        if (p == 0) return q >= 0;
        double r = q / p;
        if (p < 0) { if (r > t[1]) return false; if (r > t[0]) t[0] = r; }
        else { if (r < t[0]) return false; if (r < t[1]) t[1] = r; }
        return true;
    }

    /** Kantengeglättete Linie. additive = true: Leuchten; sonst deckend mit Deckkraft a. */
    public void line(double x0, double y0, double x1, double y1, float cr, float cg, float cb, float a, boolean additive) {
        if (a <= 0.002f || !clip(x0, y0, x1, y1)) return;
        x0 = clip[0]; y0 = clip[1]; x1 = clip[2]; y1 = clip[3];
        boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
        if (steep) { double t = x0; x0 = y0; y0 = t; t = x1; x1 = y1; y1 = t; }
        if (x0 > x1) { double t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }
        double dx = x1 - x0, dy = y1 - y0;
        double grad = dx < 1e-9 ? 1 : dy / dx;
        // Längenausgleich: diagonale Linien sollen nicht dunkler wirken
        float lenK = (float) Math.sqrt(1 + grad * grad);
        int xs = (int) Math.round(x0), xe = (int) Math.round(x1);
        double yy = y0 + grad * (xs - x0);
        for (int x = xs; x <= xe; x++) {
            int iy = (int) Math.floor(yy);
            float f = (float) (yy - iy);
            float cov = a * lenK;
            float c1 = cov * (1 - f), c2 = cov * f;
            if (steep) { put(iy, x, cr, cg, cb, c1, additive); put(iy + 1, x, cr, cg, cb, c2, additive); }
            else { put(x, iy, cr, cg, cb, c1, additive); put(x, iy + 1, cr, cg, cb, c2, additive); }
            yy += grad;
        }
    }

    private void put(int x, int y, float cr, float cg, float cb, float a, boolean additive) {
        if (a <= 0) return;
        if (additive) add(x, y, cr * a, cg * a, cb * a);
        else over(x, y, cr, cg, cb, a > 1 ? 1 : a);
    }
}
