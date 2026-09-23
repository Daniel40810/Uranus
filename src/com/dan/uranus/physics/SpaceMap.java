package com.dan.uranus.physics;

import com.dan.uranus.model.UranusSystem;

/**
 * Abbildung wahrer Abstände (Uranusradien) auf Weltabstände — gestaucht oder maßstabsgetreu,
 * mit weicher Überblendung (s = 0 gestaucht, s = 1 echt).
 * <p>
 * Gestaucht bleibt innen (bis 3,9 R, also Ringe und innere Monde) linear, darüber wächst der
 * Abstand mit der Wurzel, ab 30 R nur noch logarithmisch. So passen Hauptmonde und die
 * irregulären Monde bis 820 R in dasselbe Bild. Die Abbildung ist streng monoton — die
 * Reihenfolge der Monde bleibt immer erhalten.
 */
public final class SpaceMap {

    private SpaceMap() {}

    /** Darstellungsradius von Uranus in Welteinheiten (1 R). */
    public static final double PR = 1.55;

    private static final double R_LIN = 3.9, R_LOG = 30.0, K_SQRT = 1.9, K_LOG = 5.0;
    private static final double G_LIN = PR * R_LIN;
    private static final double G_LOG = G_LIN + K_SQRT * Math.sqrt(R_LOG - R_LIN);

    public static double compressed(double rRu) {
        if (rRu <= R_LIN) return PR * rRu;
        if (rRu <= R_LOG) return G_LIN + K_SQRT * Math.sqrt(rRu - R_LIN);
        return G_LOG + K_LOG * Math.log(rRu / R_LOG);
    }

    public static double real(double rRu) { return PR * rRu; }

    public static double map(double rRu, double s) {
        double c = compressed(rRu);
        return c + (real(rRu) - c) * s;
    }

    /** Umkehrung von {@link #map}: Weltabstand → wahrer Abstand in Uranusradien (Bisektion, streng monoton). */
    public static double inverse(double world, double s) {
        double lo = 0, hi = 1;
        while (map(hi, s) < world && hi < 1e9) hi *= 2;
        for (int i = 0; i < 80; i++) {
            double mid = (lo + hi) * 0.5;
            if (map(mid, s) < world) lo = mid; else hi = mid;
        }
        return (lo + hi) * 0.5;
    }

    /** Wandelt eine Lage in km (relativ zu Uranus) in Weltkoordinaten. */
    public static void toWorld(double[] km, double s, double[] out) {
        double r = Math.sqrt(km[0] * km[0] + km[1] * km[1] + km[2] * km[2]);
        if (r < 1e-9) { out[0] = out[1] = out[2] = 0; return; }
        double rRu = r / UranusSystem.URANUS_RADIUS_KM;
        double f = map(rRu, s) / r;
        out[0] = km[0] * f; out[1] = km[1] * f; out[2] = km[2] * f;
    }

    /** Darstellungsradius eines Mondes: gestaucht vergrößert, echt maßstabsgetreu. */
    public static double moonRadius(double radiusKm, double s) {
        double c = 0.55 * Math.pow(radiusKm / 790.0, 0.6);
        double r = PR * radiusKm / UranusSystem.URANUS_RADIUS_KM;
        return c + (r - c) * s;
    }
}
