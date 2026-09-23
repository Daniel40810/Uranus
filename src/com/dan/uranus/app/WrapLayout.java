package com.dan.uranus.app;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * FlowLayout, das beim Umbrechen auch die nötige Höhe meldet.
 * <p>
 * Das normale FlowLayout bricht eine zu lange Reihe zwar um, meldet aber nur die Höhe einer
 * Zeile — die zweite Zeile wird dann ohne Fehlermeldung abgeschnitten (bekannter Fallstrick aus
 * FCurvedField). Hier rechnet die Vorzugsgröße mit der tatsächlichen Breite des Elternelements.
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

    @Override
    public Dimension preferredLayoutSize(Container target) { return layoutSize(target, true); }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = layoutSize(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            Container c = target;
            while (c.getSize().width == 0 && c.getParent() != null) c = c.getParent();
            int targetWidth = c.getSize().width;
            if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
            int hgap = getHgap(), vgap = getVgap();
            Insets in = target.getInsets();
            int maxWidth = targetWidth - (in.left + in.right + hgap * 2);
            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0, rowHeight = 0;
            for (Component m : target.getComponents()) {
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth + d.width > maxWidth) { addRow(dim, rowWidth, rowHeight); rowWidth = 0; rowHeight = 0; }
                if (rowWidth != 0) rowWidth += hgap;
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);
            dim.width += in.left + in.right + hgap * 2;
            dim.height += in.top + in.bottom + vgap * 2;
            Container scroll = (Container) SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scroll != null && target.isValid()) dim.width -= hgap + 1;
            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) dim.height += getVgap();
        dim.height += rowHeight;
    }
}
