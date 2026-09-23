package com.dan.uranus.fx;

/** Gitterrauschen (Value Noise) in 3D — reine Funktionen, thread-sicher. */
public final class Noise {

    private Noise() {}

    public static double hash(int i, int j, int k) {
        int h = i * 374_761_393 + j * 668_265_263 + k * 1_440_662_683;
        h = (h ^ (h >>> 13)) * 1_274_126_177;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / 2_147_483_647.0;
    }

    public static double value(double x, double y, double z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double xf = x - xi, yf = y - yi, zf = z - zi;
        xf = xf * xf * (3 - 2 * xf); yf = yf * yf * (3 - 2 * yf); zf = zf * zf * (3 - 2 * zf);
        double a = lerp(hash(xi, yi, zi), hash(xi + 1, yi, zi), xf);
        double b = lerp(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), xf);
        double c = lerp(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), xf);
        double d = lerp(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), xf);
        return lerp(lerp(a, b, yf), lerp(c, d, yf), zf);
    }

    /** Fraktales Rauschen, Wertebereich etwa 0..1. */
    public static double fbm(double x, double y, double z, int octaves) {
        double s = 0, amp = 0.5, norm = 0;
        for (int o = 0; o < octaves; o++) {
            s += amp * value(x, y, z);
            norm += amp;
            x *= 2.03; y *= 2.03; z *= 2.03; amp *= 0.5;
        }
        return s / norm;
    }

    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    public static double smooth(double e0, double e1, double x) {
        double t = (x - e0) / (e1 - e0);
        t = t < 0 ? 0 : t > 1 ? 1 : t;
        return t * t * (3 - 2 * t);
    }
}
