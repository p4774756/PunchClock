package com.example.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * 排程頁專用捲軸。不顯示上下箭頭，也不改單位捲動量。
 */
public final class CandyScrollBar {

    public static final String MARK = "candy.scroll";

    private CandyScrollBar() {
    }

    public static void install(JScrollPane scroll) {
        // 下拉清單會拿掉橫向捲軸，這裡不能假設兩根都在。
        install(scroll.getVerticalScrollBar(), true);
        install(scroll.getHorizontalScrollBar(), false);
    }

    private static void install(JScrollBar bar, boolean vertical) {
        if (bar == null) {
            return;
        }
        bar.setUI(new BarUI());
        bar.setOpaque(false);
        bar.setPreferredSize(vertical
                ? new Dimension(CandyTheme.SCROLL_WIDTH, 0)
                : new Dimension(0, CandyTheme.SCROLL_WIDTH));
        keepSpeed(bar);
    }

    /** 與 {@link NightTheme} 目前的一格 18px 相同，避免這次換皮把滾輪變快或變慢。 */
    private static void keepSpeed(JScrollBar bar) {
        if (bar.getUnitIncrement() <= 1) {
            bar.setUnitIncrement(18);
        }
    }

    static final class BarUI extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return empty();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return empty();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle track) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            g2.setColor(new java.awt.Color(0x24, 0x18, 0x3F, 120));
            int arc = Math.min(CandyTheme.RADIUS_SCROLL, Math.min(track.width, track.height));
            g2.fillRoundRect(track.x, track.y, track.width, track.height, arc, arc);
            g2.dispose();
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumb) {
            if (thumb.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            boolean hot = isDragging || isThumbRollover();
            java.awt.Color top = hot ? CandyTheme.PINK_HI : CandyTheme.LAVENDER;
            java.awt.Color bottom = hot ? CandyTheme.PINK : CandyTheme.PINK_DEEP;
            int x = thumb.x + 2;
            int y = thumb.y + 2;
            int w = Math.max(1, thumb.width - 4);
            int h = Math.max(1, thumb.height - 4);
            g2.setPaint(new GradientPaint(x, y, top, x, y + h, bottom));
            int arc = Math.min(CandyTheme.RADIUS_SCROLL, Math.min(w, h));
            g2.fillRoundRect(x, y, w, h, arc, arc);
            g2.dispose();
        }

        private static JButton empty() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setFocusable(false);
            button.setOpaque(false);
            button.setBorder(javax.swing.BorderFactory.createEmptyBorder());
            button.putClientProperty(CandyButton.MARK, Boolean.TRUE);
            return button;
        }
    }
}
