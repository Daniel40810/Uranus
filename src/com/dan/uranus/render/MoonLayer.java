package com.dan.uranus.render;

import com.dan.uranus.fx.Noise;
import com.dan.uranus.model.Body;

/**
 * Monde: als beleuchtete Kugel pro Pixel, sobald sie groß genug im Bild sind, sonst als
 * Lichtpunkt. Gebundene Rotation (dieselbe Seite zeigt immer zu Uranus), Phasen,
 * Uranusschein auf der Nachtseite, Verfinsterung im Planetenschatten.
 */
public final class MoonLayer {

    /** Ein Mond in diesem Bild. */
    public static final class Instance {
        public Body body;
        public double x, y, z, radius;
        /** Winkel um Uranus (für die gebundene Rotation). */
        public double orbitAngle;
        /** 0 = voll beleuchtet, 1 = ganz im Uranusschatten. */
        public double eclipse;
        public float fade = 1f;
        /** Entdeckungsgeschichte: 0 = noch nicht entdeckt, 1 = sichtbar (weich nachgeführt). */
        public float reveal = 1f;
        /** Spielwiese: Nummer des Körpers (−1 = Ephemeride), seine Masse und sein Radius. */
        public int playId = -1;
        public double playMassKg, playRadiusKm;
        // Bildlage (vom Renderer nach der Projektion gesetzt)
        public double sx, sy, depth, sr;
        public boolean onScreen;
    }

    private double sunX, sunY, sunZ, ux, uy, uz;

    public void setSun(double[] s) { sunX = s[0]; sunY = s[1]; sunZ = s[2]; }

    public void setPlanet(double x, double y, double z) { ux = x; uy = y; uz = z; }

    public void render(Hdr hdr, Camera cam, Instance m, boolean highlight) {
        if (!m.onScreen || m.fade <= 0.01f) return;
        double light = 1 - m.eclipse;
        if (m.sr < 1.6) {
            // Lichtpunkt: Helligkeit aus Albedo, Größe und Phase
            double toCamX = cam.cx - m.x, toCamY = cam.cy - m.y, toCamZ = cam.cz - m.z;
            double l = Math.sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ);
            double phase = 0.5 + 0.5 * (toCamX * sunX + toCamY * sunY + toCamZ * sunZ) / l;
            float k = (float) ((0.25 + 0.9 * m.body.albedo) * (0.25 + 0.75 * phase) * light * m.fade * (0.55 + m.sr * 0.6));
            hdr.splat(m.sx, m.sy, m.body.red * k * 1.4f, m.body.green * k * 1.4f, m.body.blue * k * 1.4f);
            hdr.glow(m.sx, m.sy, 3.2, m.body.red * k * 0.25f, m.body.green * k * 0.25f, m.body.blue * k * 0.25f);
        } else {
            sphere(hdr, cam, m, light);
        }
        if (highlight) ring(hdr, m.sx, m.sy, Math.max(7, m.sr + 5));
    }

    private void ring(Hdr hdr, double x, double y, double r) {
        int n = 64;
        for (int i = 0; i < n; i++) {
            if ((i & 3) == 3) continue;
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            hdr.line(x + Math.cos(a0) * r, y + Math.sin(a0) * r, x + Math.cos(a1) * r, y + Math.sin(a1) * r, 0.55f, 0.9f, 0.95f, 0.8f, true);
        }
    }

    private void sphere(Hdr hdr, Camera cam, Instance m, double light) {
        double ocx = cam.cx - m.x, ocy = cam.cy - m.y, ocz = cam.cz - m.z;
        double oc2 = ocx * ocx + ocy * ocy + ocz * ocz, dist = Math.sqrt(oc2), rad = m.radius;
        if (dist <= rad * 1.001) return;
        double pixelWorld = dist / cam.foc;
        double reach = m.sr + 2;
        int x0 = (int) Math.max(0, Math.floor(m.sx - reach)), x1 = (int) Math.min(hdr.w - 1, Math.ceil(m.sx + reach));
        int y0 = (int) Math.max(0, Math.floor(m.sy - reach)), y1 = (int) Math.min(hdr.h - 1, Math.ceil(m.sy + reach));
        // Richtung zu Uranus (Uranusschein)
        double tx = ux - m.x, ty = uy - m.y, tz = uz - m.z, tl = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1e-9;
        tx /= tl; ty /= tl; tz /= tl;
        double ca = Math.cos(-m.orbitAngle), sa = Math.sin(-m.orbitAngle);
        double[] d = new double[3];
        Body.Surface surf = m.body.surface;
        int seed = m.body.name.hashCode() & 0xff;
        for (int y = y0; y <= y1; y++) {
            int row = y * hdr.w;
            for (int x = x0; x <= x1; x++) {
                cam.rayDir(x + 0.5, y + 0.5, d);
                double b = ocx * d[0] + ocy * d[1] + ocz * d[2];
                if (b >= 0) continue;
                double h2 = oc2 - b * b, hh = Math.sqrt(Math.max(0, h2));
                double cov = (rad - hh) / pixelWorld + 0.5;
                if (cov <= 0) continue;
                double t = -b - Math.sqrt(Math.max(0, rad * rad - h2));
                double nx = (ocx + t * d[0]) / rad, ny = (ocy + t * d[1]) / rad, nz = (ocz + t * d[2]) / rad;
                double mu = Math.max(0, -(nx * d[0] + ny * d[1] + nz * d[2]));
                // Körperkoordinaten: gebundene Rotation um y
                double bx = nx * ca - nz * sa, bz = nx * sa + nz * ca, by = ny;
                double alb = albedo(surf, bx, by, bz, seed);
                double ndl = nx * sunX + ny * sunY + nz * sunZ;
                double lit = ndl > 0 ? ndl : 0;
                double ld = 0.72 + 0.28 * mu;
                double ush = nx * tx + ny * ty + nz * tz;
                double us = ush > 0 ? ush * 0.06 : 0;
                double k = alb * lit * ld * light * 1.25;
                float a = (float) Math.min(1, cov) * m.fade;
                int o = row + x;
                float cr = (float) (m.body.red * k + us * 0.45 + 0.004);
                float cg = (float) (m.body.green * k + us * 0.80 + 0.005);
                float cb = (float) (m.body.blue * k + us * 0.88 + 0.007);
                float kk = 1 - a;
                hdr.r[o] = hdr.r[o] * kk + cr * a; hdr.g[o] = hdr.g[o] * kk + cg * a; hdr.b[o] = hdr.b[o] * kk + cb * a;
            }
        }
    }

    /** Prozedurale Helligkeit der Oberfläche im Körpersystem. */
    static double albedo(Body.Surface s, double x, double y, double z, int seed) {
        double n = Noise.value(x * 3.1 + seed, y * 3.1, z * 3.1) * 0.6 + Noise.value(x * 8.3, y * 8.3 + seed, z * 8.3) * 0.4;
        double a = 0.78 + 0.44 * (n - 0.5);
        switch (s) {
            case PATCHWORK: {       // Miranda: Flickenteppich aus Coronae, Verona Rupes
                double reg = Noise.value(x * 1.6, y * 1.6, z * 1.6 + 9);
                if (reg > 0.55) a += 0.17 * Math.sin(x * 26 + y * 9);
                double rupes = Math.abs(x * 0.8 + z * 0.6 - 0.35);
                if (rupes < 0.02 && y < 0.2) a += 0.35;
                break;
            }
            case CANYONS: {         // Ariel: helle Oberfläche, dunkle Grabenbrüche
                double c = Math.sin(x * 7 + y * 2.5 + z * 3);
                a -= 0.30 * Math.exp(-c * c * 60);
                a += 0.08;
                break;
            }
            case DARK_RING: {       // Umbriel: dunkel, heller Ring am Krater Wunda
                double dx = x - 0.1, dy = y - 0.35, dz = z - 0.93;
                double dd = Math.sqrt(dx * dx + dy * dy + dz * dz);
                a += 0.9 * Math.exp(-((dd - 0.13) * (dd - 0.13)) / 0.0012);
                break;
            }
            case CHASMA: {          // Titania: Messina Chasma
                double c = Math.sin(x * 4 - y * 6 + z * 2);
                a -= 0.24 * Math.exp(-c * c * 80);
                break;
            }
            case CRATERED: {        // Oberon: Krater mit hellen Strahlen
                double c = Noise.value(x * 14, y * 14, z * 14);
                if (c > 0.78) a += 0.5 * (c - 0.78) / 0.22;
                double c2 = Noise.value(x * 5 + 3, y * 5, z * 5);
                if (c2 < 0.2) a -= 0.2 * (0.2 - c2) / 0.2;
                break;
            }
            default:
                a += 0.15 * (Noise.value(x * 11, y * 11, z * 11) - 0.5);
        }
        return a < 0.05 ? 0.05 : a;
    }
}
