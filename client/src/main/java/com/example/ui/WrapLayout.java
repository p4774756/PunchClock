package com.example.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.BoxLayout;

/**
 * FlowLayout that wraps and reports the correct preferred height after wrapping.
 * Standard Swing FlowLayout preferred size is always a single row, which clips
 * trailing buttons when the parent is narrower than that row.
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true, targetWidth(target));
    }

    public Dimension preferredLayoutSize(Container target, int width) {
        return layoutSize(target, true, width);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false, targetWidth(target));
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    /**
     * 直向 Box／Border 在問 preferred 時，子元件寬度還是上一輪的。
     * 用父層目前的內寬，這一輪才排得出換行後的高度。
     */
    public int targetWidth(Container target) {
        int width = target.getWidth();
        Container parent = target.getParent();
        if (parent != null && parent.getWidth() > 0) {
            LayoutManager layout = parent.getLayout();
            if (layout instanceof BoxLayout || layout instanceof BorderLayout) {
                Insets parentInsets = parent.getInsets();
                int inner = parent.getWidth() - parentInsets.left - parentInsets.right;
                if (inner > 0) {
                    return inner;
                }
            }
        }
        return width > 0 ? width : Integer.MAX_VALUE;
    }

    private Dimension layoutSize(Container target, boolean preferred, int targetWidth) {
        synchronized (target.getTreeLock()) {
            if (targetWidth <= 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int maxWidth = targetWidth - insets.left - insets.right - hgap * 2;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            for (Component m : target.getComponents()) {
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) rowWidth += hgap;
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);

            dim.width += insets.left + insets.right + hgap * 2;
            dim.height += insets.top + insets.bottom + vgap * 2;
            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) dim.height += getVgap();
        dim.height += rowHeight;
    }
}
