package com.dan.uranus.render;

/**
 * Lichtbahnen: Sonnenlicht, das an Uranus vorbeiläuft, als leuchtende Linien mit wandernden
 * Photonen. Jede Bahn wird schrittweise integriert (schwaches Feld: Ablenkung ≈ 2K/b), Strahlen
 * mit zu kleinem Stoßparameter enden auf dem Planeten.
 * <p>
 * Die Ablenkung ist stark überhöht: echt sind es am Uranusrand 4GM/(c²R) ≈ 1·10⁻⁸ rad
 * (etwa 2 Millibogensekunden) — sichtbar gemacht wird ein halber Radiant.
 * <p>
 * Die Strahlen kommen aus der Richtung der Sonne, in die Bildebene gedreht: der Fächer liegt
 * quer zum Blick, damit die Krümmung nicht perspektivisch verschwindet (Veranschaulichung).
 */
final class GeodesicLayer {

    /** Stärke der (überhöhten) Anziehung in Welteinheiten: Ablenkung ≈ 2K/b. */
    static final double K = 0.5;
    static final int RAYS = 31;

    /** Ergebnis einer einzelnen Bahn (für Prüfungen). */
    static final class Ray {
        final double[] pts; final int n; final boolean hit;
        Ray(double[] pts, int n, boolean hit) { this.pts = pts; this.n = n; this.hit = hit; }
        /** Ablenkungswinkel zwischen Anfangs- und Endrichtung (rad). */
        double deflection(double[] dir0) {
            int a = (n - 2) * 3, b = (n - 1) * 3;
            double dx = pts[b] - pts[a], dy = pts[b + 1] - pts[a + 1], dz = pts[b + 2] - pts[a + 2];
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double c = (dx * dir0[0] + dy * dir0[1] + dz * dir0[2]) / l;
            return Math.acos(Math.max(-1, Math.min(1, c)));
        }
    }

    /**
     * Integriert einen Lichtstrahl ab p0 in Richtung v0 um eine Masse bei (cx, cy, cz) mit Radius R.
     * Schrittweite passt sich dem Abstand an; Lichtgeschwindigkeit bleibt konstant (Richtung wird normiert).
     */
    static Ray trace(double[] p0, double[] v0, double cx, double cy, double cz, double R, double k, double length) {
        int cap = (int) Math.ceil(length / 0.01) + 2;                     // kleinster Schritt 0,01
        double[] pts = new double[3 * cap];
        double x = p0[0], y = p0[1], z = p0[2], vx = v0[0], vy = v0[1], vz = v0[2];
        pts[0] = x; pts[1] = y; pts[2] = z;
        int n = 1;
        double travelled = 0;
        boolean hit = false;
        while (travelled < length && n < cap) {
            double rx = x - cx, ry = y - cy, rz = z - cz;
            double r = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (r < R) { hit = true; break; }
            double h = Math.max(0.01, Math.min(0.15, 0.04 * r));
            // Mittelpunktsregel: Beschleunigung in der Schrittmitte
            double mx = x + vx * h * 0.5, my = y + vy * h * 0.5, mz = z + vz * h * 0.5;
            double mrx = mx - cx, mry = my - cy, mrz = mz - cz, mr = Math.sqrt(mrx * mrx + mry * mry + mrz * mrz);
            double am = k / (mr * mr * mr);
            vx -= mrx * am * h; vy -= mry * am * h; vz -= mrz * am * h;
            double vl = Math.sqrt(vx * vx + vy * vy + vz * vz); vx /= vl; vy /= vl; vz /= vl;
            x += vx * h; y += vy * h; z += vz * h;
            travelled += h;
            pts[n * 3] = x; pts[n * 3 + 1] = y; pts[n * 3 + 2] = z; n++;
        }
        return new Ray(pts, n, hit);
    }

    void build(Scene3D sc, double radius, double alpha) {
        if (alpha < 0.01) return;
        Camera cam = sc.camera();
        double[] s = sc.sun();
        double ucY = sc.ucY();
        // Fächer in der Bildebene: Einfallsrichtung = Sonnenrichtung, in die Bildebene gedreht.
        // So laufen die Strahlen quer durchs Bild und die Krümmung ist nicht perspektivisch verdeckt.
        double sf = s[0] * cam.fx + s[1] * cam.fy + s[2] * cam.fz;
        double dx = s[0] - cam.fx * sf, dy = s[1] - cam.fy * sf, dz = s[2] - cam.fz * sf;
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-3) { dx = cam.ux; dy = cam.uy; dz = cam.uz; dl = 1; }
        dx /= dl; dy /= dl; dz /= dl;
        double ex = cam.fy * dz - cam.fz * dy, ey = cam.fz * dx - cam.fx * dz, ez = cam.fx * dy - cam.fy * dx;
        double el = Math.sqrt(ex * ex + ey * ey + ez * ez);
        ex /= el; ey /= el; ez /= el;
        final double[] s0 = {dx, dy, dz};
        double L0 = 11;
        double t = sc.time();
        double[] p0 = new double[3], v0 = {-s0[0], -s0[1], -s0[2]};
        for (int i = 0; i < RAYS; i++) {
            double u = (i - (RAYS - 1) * 0.5) / ((RAYS - 1) * 0.5);        // −1..1, zur Mitte dichter
            double b = Math.signum(u) * Math.pow(Math.abs(u), 1.8) * 6.5;
            p0[0] = s0[0] * L0 + ex * b; p0[1] = ucY + s0[1] * L0 + ey * b; p0[2] = s0[2] * L0 + ez * b;
            Ray ray = trace(p0, v0, 0, ucY, 0, radius, K, 2 * L0);
            float a = (float) (0.34 * alpha * (0.45 + 0.55 * Math.exp(-Math.abs(b) / 2.5)));
            double len = 0;
            double[] cum = new double[ray.n];
            for (int k = 1; k < ray.n; k++) {
                int o0 = (k - 1) * 3, o1 = k * 3;
                double qx = ray.pts[o1] - ray.pts[o0], qy = ray.pts[o1 + 1] - ray.pts[o0 + 1], qz = ray.pts[o1 + 2] - ray.pts[o0 + 2];
                len += Math.sqrt(qx * qx + qy * qy + qz * qz);
                cum[k] = len;
                float fade = (float) Math.min(1, Math.min(cum[k] / 2.5, (2 * L0 - cum[k]) / 2.5 + 0.2));
                sc.seg(ray.pts[o0], ray.pts[o0 + 1], ray.pts[o0 + 2], ray.pts[o1], ray.pts[o1 + 1], ray.pts[o1 + 2],
                        1.0f, 0.80f, 0.48f, a * Math.max(0, fade), true);
            }
            // Photonen wandern die Bahn entlang
            for (int ph = 0; ph < 3; ph++) {
                double target = ((t * 2.2 + ph * 7.3 + i * 0.77) % (2 * L0));
                if (target > len) continue;
                int k = 1;
                while (k < ray.n - 1 && cum[k] < target) k++;
                int o = k * 3;
                sc.glow(ray.pts[o], ray.pts[o + 1], ray.pts[o + 2], (float) (1.9 * alpha), (float) (1.55 * alpha), (float) (1.0 * alpha), 3.2);
            }
            if (ray.hit) {
                int o = (ray.n - 1) * 3;
                sc.glow(ray.pts[o], ray.pts[o + 1], ray.pts[o + 2], (float) (0.5 * alpha), (float) (0.4 * alpha), (float) (0.25 * alpha), 2.5);
            }
        }
    }
}
