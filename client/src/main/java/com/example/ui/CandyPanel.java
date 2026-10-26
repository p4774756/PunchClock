package com.example.ui;

import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

/**
 * 排程頁的圓角卡片。填色是實心的，避免房間背景透出來影響閱讀。
 */
public class CandyPanel extends JPanel {

    public static final String MARK = "candy.panel";

    public CandyPanel() {
        this(null, null, 12, 12, 16, 12);
    }

    public CandyPanel(int top, int left, int bottom, int right) {
        this(null, null, top, left, bottom, right);
    }

    public CandyPanel(String title, Font titleFont) {
        this(title, titleFont, 32, 14, 12, 14);
    }

    private CandyPanel(String title, Font titleFont, int top, int left, int bottom, int right) {
        setOpaque(false);
        setBackground(CandyTheme.PANEL);
        putClientProperty(MARK, Boolean.TRUE);
        setBorder(new Chrome(title, titleFont, top, left, bottom, right));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
        int arc = CandyTheme.RADIUS_PANEL;
        int w = Math.max(1, getWidth() - 1);
        int h = Math.max(1, getHeight() - 1);
        g2.setColor(CandyTheme.PANEL);
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        g2.setColor(new java.awt.Color(16, 8, 28, 40));
        g2.drawRoundRect(1, 2, Math.max(1, w - 2), Math.max(1, h - 3), arc, arc);
        g2.dispose();
    }

    private static final class Chrome extends AbstractBorder {
        private final String title;
        private final Font font;
        private final int top;
        private final int left;
        private final int bottom;
        private final int right;

        private Chrome(String title, Font font, int top, int left, int bottom, int right) {
            this.title = title;
            this.font = font;
            this.top = top;
            this.left = left;
            this.bottom = bottom;
            this.right = right;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(top, left, bottom, right);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.top = top;
            insets.left = left;
            insets.bottom = bottom;
            insets.right = right;
            return insets;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            int arc = CandyTheme.RADIUS_PANEL;
            g2.setColor(CandyTheme.EDGE);
            g2.drawRoundRect(x, y, Math.max(1, width - 1), Math.max(1, height - 1), arc, arc);
            if (title != null && !title.isEmpty()) {
                g2.setFont(font != null ? font : c.getFont());
                g2.setColor(CandyTheme.INK);
                FontMetrics metrics = g2.getFontMetrics();
                g2.drawString(title, x + 16, y + 8 + metrics.getAscent());
            }
            g2.dispose();
        }
    }
}
