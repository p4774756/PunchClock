package com.example.ui;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Container;

/** 380px 小方塊分頁：緊湊邊距、僅垂直捲動。 */
public final class CompactShellPanels {

    private CompactShellPanels() {
    }

    public static JScrollPane wrap(JComponent body) {
        tighten(body);
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    private static void tighten(JComponent root) {
        if (root == null) {
            return;
        }
        if (root.getBorder() instanceof EmptyBorder) {
            root.setBorder(new EmptyBorder(4, 6, 4, 6));
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                if (child instanceof JComponent) {
                    tighten((JComponent) child);
                }
            }
        }
    }
}