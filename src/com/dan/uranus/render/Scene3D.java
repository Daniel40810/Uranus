package com.dan.uranus.render;

/**
 * Was die Effekt-Ebenen vom Renderer brauchen: Linien und Leuchtpunkte in Weltkoordinaten
 * ablegen (der Renderer sortiert sie vor/hinter Uranus) und die Eckdaten des Bildes lesen.
 */
interface Scene3D {

    /** Liniensegment in Weltkoordinaten. */
    void seg(double x0, double y0, double z0, double x1, double y1, double z1,
             float r, float g, float b, float a, boolean additive);

    /** Leuchtpunkt in Weltkoordinaten; radiusPx ≤ 1,2 → feiner Punkt, sonst weicher Fleck. */
    void glow(double x, double y, double z, float r, float g, float b, double radiusPx);

    Camera camera();

    /** Höhe des Uranusmittelpunkts (Mulde senkt ihn ab). */
    double ucY();

    /** Maßstab: 0 gestaucht, 1 echt (weich überblendet). */
    double scaleT();

    /** Richtung zur Sonne (Welt, normiert). */
    double[] sun();

    /** Echte Sekunden seit Start (für Animationen, unabhängig vom Zeitraffer). */
    double time();
}
