package com.example.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;

/** 380px 小方塊分頁：緊湊邊距、寬度跟著視埠、僅垂直捲動。 */
public final class CompactShellPanels {

    private CompactShellPanels() {
    }

    public static JScrollPane wrap(JComponent body) {
        tighten(body);
        JScrollPane scroll = new JScrollPane(new TrackingView(body));
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        ShellWheelForwarder.install(scroll, body);
        return scroll;
    }

    /**
     * 長說明改在約 300px 內換行，避免把分頁撐出小方塊視窗。
     */
    public static final class WrappingLabel extends JLabel {

        private static final int WRAP = 300;
        private int wrapWidth = WRAP;

        public WrappingLabel(String text) {
            super(text);
        }

        /** 同一列被均分時，用這個寬度折行，避免沿用 300px 把高度算成一行。 */
        public void setWrapWidth(int width) {
            int next = width > 20 ? width : WRAP;
            if (next == wrapWidth) {
                return;
            }
            wrapWidth = next;
            View view = (View) getClientProperty(BasicHTML.propertyKey);
            if (view != null) {
                view.setSize(wrapWidth, Integer.MAX_VALUE);
            }
            revalidate();
        }

        @Override
        public void setText(String text) {
            if (text == null || text.startsWith("<html>")) {
                super.setText(text);
                return;
            }
            super.setText("<html>" + escape(text) + "</html>");
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension single = super.getPreferredSize();
            if (single.width <= wrapWidth) {
                return single;
            }
            View view = (View) getClientProperty(BasicHTML.propertyKey);
            if (view == null) {
                return new Dimension(wrapWidth, single.height);
            }
            view.setSize(wrapWidth, Integer.MAX_VALUE);
            int w = (int) Math.ceil(view.getPreferredSpan(View.X_AXIS));
            int h = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
            return new Dimension(Math.min(wrapWidth, Math.max(1, w)), Math.max(single.height, h));
        }

        @Override
        public Dimension getMinimumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(Math.min(40, pref.width), pref.height);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private static String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /** 視埠多寬，內容就排多寬，水平方向不再另長出一截。 */
    private static final class TrackingView extends JPanel implements Scrollable {

        TrackingView(JComponent body) {
            super(new BorderLayout());
            setOpaque(false);
            add(body, BorderLayout.NORTH);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static void tighten(JComponent root) {
        if (root == null) {
            return;
        }
        if (root.getBorder() instanceof EmptyBorder) {
            Insets in = ((EmptyBorder) root.getBorder()).getBorderInsets();
            int top = Math.min(in.top, 4);
            int left = Math.min(in.left, 4);
            int bottom = Math.min(in.bottom, 6);
            int right = Math.min(in.right, 4);
            if (top != in.top || left != in.left || bottom != in.bottom || right != in.right) {
                root.setBorder(new EmptyBorder(top, left, bottom, right));
            }
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
