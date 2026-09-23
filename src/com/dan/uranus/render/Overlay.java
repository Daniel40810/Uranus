package com.dan.uranus.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Beschriftung eines Bildes als Liste von Zeichenbefehlen in FENSTER-Koordinaten.
 * <p>
 * Der Render-Thread zeichnet die Szene ggf. in reduzierter Auflösung; Schrift soll aber immer
 * scharf in Fenstergröße erscheinen. Deshalb nimmt der Renderer die Beschriftung nur auf,
 * und die Oberfläche spielt sie beim Zeichnen ab.
 */
public final class Overlay {

    private enum Kind { TEXT, LINE, BOX, RECT, DOT }

    private static final class Op {
        Kind kind; Font font; Color color; float alpha; double x, y, w, h; String text;
    }

    private final List<Op> ops = new ArrayList<>();
    private Font font;
    private Color color = Color.WHITE;
    private float alpha = 1f;
    private static final FontMetricsSource METRICS = new FontMetricsSource();

    public void setFont(Font f) { font = f; }

    public void setColor(Color c) { color = c; }

    public void setAlpha(float a) { alpha = Math.max(0, Math.min(1, a)); }

    public int stringWidth(String s) { return METRICS.of(font).stringWidth(s); }

    public void text(String s, double x, double y) {
        Op o = op(Kind.TEXT); o.text = s; o.x = x; o.y = y;
    }

    public void line(double x0, double y0, double x1, double y1) {
        Op o = op(Kind.LINE); o.x = x0; o.y = y0; o.w = x1; o.h = y1;
    }

    /** Abgerundeter, halbtransparenter Kasten mit Rand in der Akzentfarbe. */
    public void box(double x, double y, double w, double h) {
        Op o = op(Kind.BOX); o.x = x; o.y = y; o.w = w; o.h = h;
    }

    /** Gefülltes Rechteck in der aktuellen Farbe (Streifen, Balken). */
    public void rect(double x, double y, double w, double h) {
        Op o = op(Kind.RECT); o.x = x; o.y = y; o.w = w; o.h = h;
    }

    /** Gefüllter Punkt mit Radius r in der aktuellen Farbe. */
    public void dot(double x, double y, double r) {
        Op o = op(Kind.DOT); o.x = x; o.y = y; o.w = r;
    }

    private Op op(Kind k) {
        Op o = new Op();
        o.kind = k; o.font = font; o.color = color; o.alpha = alpha;
        ops.add(o);
        return o;
    }

    public int size() { return ops.size(); }

    /** Enthält die Beschriftung einen Text mit part (für Prüfungen)? */
    public boolean containsText(String part) {
        for (Op o : ops) if (o.kind == Kind.TEXT && o.text.contains(part)) return true;
        return false;
    }

    /** Spielt die Befehle ab (EDT). */
    public void paint(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1f));
        for (Op o : ops) {
            if (o.alpha <= 0.01f) continue;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, o.alpha));
            g.setColor(o.color);
            switch (o.kind) {
                case TEXT:
                    g.setFont(o.font);
                    g.drawString(o.text, (float) o.x, (float) o.y);
                    break;
                case LINE:
                    g.draw(new java.awt.geom.Line2D.Double(o.x, o.y, o.w, o.h));
                    break;
                case BOX:
                    g.setColor(new Color(9, 16, 26, 205));
                    g.fill(new RoundRectangle2D.Double(o.x, o.y, o.w, o.h, 12, 12));
                    g.setColor(new Color(143, 221, 224, 90));
                    g.draw(new RoundRectangle2D.Double(o.x + 0.5, o.y + 0.5, o.w - 1, o.h - 1, 12, 12));
                    break;
                case RECT:
                    g.fill(new java.awt.geom.Rectangle2D.Double(o.x, o.y, o.w, o.h));
                    break;
                case DOT:
                    g.fill(new java.awt.geom.Ellipse2D.Double(o.x - o.w, o.y - o.w, o.w * 2, o.w * 2));
                    break;
            }
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    /** Schriftmaße ohne Bildschirm (Render-Thread). */
    private static final class FontMetricsSource {
        private final Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        synchronized FontMetrics of(Font f) { return g.getFontMetrics(f); }
    }
}
