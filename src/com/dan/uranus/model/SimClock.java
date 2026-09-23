package com.dan.uranus.model;

import java.time.Instant;

/**
 * Simulationsuhr: echtes Datum als Julianisches Datum plus Zeitraffer.
 * Thread-sicher genug für einen schreibenden Render-Thread und lesende/steuernde Oberfläche
 * (alle Felder volatile, keine zusammengesetzten Invarianten).
 */
public final class SimClock {

    public static final double JD_UNIX_EPOCH = 2_440_587.5;
    public static final double JD_J2000 = 2_451_545.0;

    private volatile double jd;
    /** Simulierte Tage je echter Sekunde. 1/86400 = Echtzeit. */
    private volatile double rate = 0.25;
    private volatile boolean paused;

    public SimClock() { setNow(); }

    public static double jdOf(Instant t) { return JD_UNIX_EPOCH + t.toEpochMilli() / 86_400_000.0; }

    public static Instant instantOf(double jd) { return Instant.ofEpochMilli(Math.round((jd - JD_UNIX_EPOCH) * 86_400_000.0)); }

    public void setNow() { jd = jdOf(Instant.now()); }

    public double jd() { return jd; }

    public void setJd(double jd) { this.jd = jd; }

    public Instant instant() { return instantOf(jd); }

    /** Tage seit J2000. */
    public double daysSinceJ2000() { return jd - JD_J2000; }

    public double rate() { return rate; }

    public void setRate(double daysPerSecond) { this.rate = daysPerSecond; }

    public boolean isPaused() { return paused; }

    public void setPaused(boolean paused) { this.paused = paused; }

    /** Vom Render-Thread je Bild aufgerufen. */
    public void advance(double realSeconds) { if (!paused) jd += realSeconds * rate; }
}
