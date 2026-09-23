package com.dan.uranus.render;

/**
 * Umlaufkamera (Gier, Neigung, Abstand um einen Zielpunkt) mit weichem Nachführen.
 * <p>
 * Die Oberfläche setzt nur Zielwerte (thread-sicher über synchronized); der Render-Thread
 * führt die aktuellen Werte je Bild mit {@link #update(double)} nach und rechnet dann
 * allokationsfrei mit {@link #project}.
 */
public final class Camera {

    // Zielwerte (von der Oberfläche gesetzt)
    private double yawT = 0.55, pitchT = 0.42, distT = 30, txT, tyT, tzT;
    // aktuelle Werte (Render-Thread)
    private double yaw = yawT, pitch = pitchT, dist = distT, tx, ty, tz;
    private double autoSpin = 0.03;
    private long lastUserInput;

    public static final double MIN_PITCH = -1.45, MAX_PITCH = 1.45;
    public double minDist = 2.2, maxDist = 6000;

    // Basis (Render-Thread)
    public double cx, cy, cz;                       // Kameraposition
    public double fx, fy, fz, rx, ry, rz, ux, uy, uz;
    public double foc = 800, halfW = 400, halfH = 300;
    public int width = 800, height = 600;
    /** Nahgrenze; beim Ringflug fast null, sonst 0,05. */
    public double near = 0.05;

    // ------------------------------------------------------------ Oberfläche

    public synchronized void rotate(double dYaw, double dPitch) {
        yawT += dYaw;
        pitchT = clamp(pitchT + dPitch, MIN_PITCH, MAX_PITCH);
        lastUserInput = System.nanoTime();
    }

    public synchronized void zoom(double factor) {
        distT = clamp(distT * factor, minDist, maxDist);
        lastUserInput = System.nanoTime();
    }

    public synchronized void setDistanceTarget(double d) { distT = clamp(d, minDist, maxDist); }

    public synchronized double distanceTarget() { return distT; }

    public synchronized void setTarget(double x, double y, double z) { txT = x; tyT = y; tzT = z; }

    public synchronized void setOrientation(double yaw, double pitch) { yawT = yaw; pitchT = clamp(pitch, MIN_PITCH, MAX_PITCH); }

    public synchronized void setAutoSpin(double radPerSecond) { autoSpin = radPerSecond; }

    /** Sekunden seit der letzten Bedienung (Maus/Rad). */
    public synchronized double idleSeconds() { return (System.nanoTime() - lastUserInput) / 1e9; }

    // ------------------------------------------------------------ Render-Thread

    /** Setzt den Bildausschnitt; die Brennweite folgt der kleineren Seite. */
    public void setViewport(int w, int h) {
        width = w; height = h;
        halfW = w * 0.5; halfH = h * 0.5;
        foc = Math.min(w * 0.92, h * 1.72);
    }

    /** Führt die aktuellen Werte weich nach und baut die Basis. */
    public void update(double dt) {
        double yT, pT, dT, xT, yyT, zT, spin; boolean idle;
        synchronized (this) {
            idle = (System.nanoTime() - lastUserInput) / 1e9 > 3.0;
            if (idle) yawT += autoSpin * dt;
            yT = yawT; pT = pitchT; dT = distT; xT = txT; yyT = tyT; zT = tzT; spin = autoSpin;
        }
        double k = 1 - Math.exp(-dt * 6.0), kd = 1 - Math.exp(-dt * 3.2), kt = 1 - Math.exp(-dt * 4.0);
        yaw += (yT - yaw) * k;
        pitch += (pT - pitch) * k;
        dist *= Math.exp((Math.log(dT) - Math.log(dist)) * kd);   // logarithmisch: gleichmäßiger Zoom
        tx += (xT - tx) * kt; ty += (yyT - ty) * kt; tz += (zT - tz) * kt;
        build();
    }

    /** Setzt Lage und Ziel sofort (Kamerafahrten); die Zielwerte folgen, damit danach nichts zurückspringt. */
    public void setPose(double x, double y, double z, double yaw, double pitch, double dist) {
        synchronized (this) {
            txT = x; tyT = y; tzT = z; yawT = yaw; pitchT = clamp(pitch, MIN_PITCH, MAX_PITCH); distT = dist;
            this.tx = x; this.ty = y; this.tz = z; this.yaw = yaw; this.pitch = pitchT; this.dist = dist;
        }
        build();
    }

    /** Springt ohne Nachführen auf die Zielwerte (für Standbilder und Prüfungen). */
    public void snap() {
        synchronized (this) { yaw = yawT; pitch = pitchT; dist = distT; tx = txT; ty = tyT; tz = tzT; }
        build();
    }

    private void build() {
        double cp = Math.cos(pitch), sp = Math.sin(pitch), cyw = Math.cos(yaw), syw = Math.sin(yaw);
        cx = tx + dist * cp * syw; cy = ty + dist * sp; cz = tz + dist * cp * cyw;
        fx = tx - cx; fy = ty - cy; fz = tz - cz;
        double l = Math.sqrt(fx * fx + fy * fy + fz * fz); fx /= l; fy /= l; fz /= l;
        rx = -fz; ry = 0; rz = fx;
        l = Math.sqrt(rx * rx + rz * rz); if (l < 1e-9) { rx = 1; rz = 0; l = 1; } rx /= l; rz /= l;
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx;
    }

    public double distance() { return dist; }

    public double yaw() { return yaw; }

    public double pitch() { return pitch; }

    /** Projiziert einen Weltpunkt; out = {x, y, Tiefe}. false, wenn hinter der Kamera. */
    public boolean project(double x, double y, double z, double[] out) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double d = dx * fx + dy * fy + dz * fz;
        if (d < near) return false;
        out[0] = halfW + foc * (dx * rx + dy * ry + dz * rz) / d;
        out[1] = halfH - foc * (dx * ux + dy * uy + dz * uz) / d;
        out[2] = d;
        return true;
    }

    /** Projiziert eine Richtung (unendlich fern, z. B. Sterne, Sonne). */
    public boolean projectDir(double x, double y, double z, double[] out) {
        double d = x * fx + y * fy + z * fz;
        if (d < 1e-4) return false;
        out[0] = halfW + foc * (x * rx + y * ry + z * rz) / d;
        out[1] = halfH - foc * (x * ux + y * uy + z * uz) / d;
        out[2] = d;
        return true;
    }

    /** Sehstrahl durch Pixelmitte (px, py), normiert. */
    public void rayDir(double px, double py, double[] out) {
        double a = (px - halfW) / foc, b = (halfH - py) / foc;
        double x = fx + a * rx + b * ux, y = fy + a * ry + b * uy, z = fz + a * rz + b * uz;
        double l = 1.0 / Math.sqrt(x * x + y * y + z * z);
        out[0] = x * l; out[1] = y * l; out[2] = z * l;
    }

    private static double clamp(double v, double a, double b) { return v < a ? a : v > b ? b : v; }
}
