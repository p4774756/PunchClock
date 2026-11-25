package com.example.ui;

import javax.swing.border.AbstractBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/** Win95 立體斜角邊框：凸起（按鈕／面板）、凹陷（輸入框／狀態列）。 */
final class ClassicBevel {

    static final Color HI = Color.WHITE;
    static final Color LO = new Color(128, 128, 128);
    static final Color DARK = new Color(64, 64, 64);
    static final Color FACE = new Color(192, 192, 192);
    static final Color TITLE_TOP = new Color(16, 52, 166);
    static final Color TITLE_BOTTOM = new Color(49, 106, 197);

    private ClassicBevel() {
    }

    static void fillRaised(Graphics2D g, int x, int y, int w, int h, Color face) {
        g.setColor(face == null ? FACE : face);
        g.fillRect(x, y, w, h);
        g.setColor(HI);
        g.drawLine(x, y, x + w - 1, y);
        g.drawLine(x, y + 1, x, y + h - 1);
        g.setColor(LO);
        g.drawLine(x + 1, y + h - 1, x + w - 1, y + h - 1);
        g.drawLine(x + w - 1, y + 1, x + w - 1, y + h - 1);
        g.setColor(DARK);
        g.drawLine(x, y + h, x + w, y + h);
        g.drawLine(x + w, y, x + w, y + h);
    }

    static void fillSunken(Graphics2D g, int x, int y, int w, int h, Color fill) {
        g.setColor(fill == null ? Color.WHITE : fill);
        g.fillRect(x + 2, y + 2, Math.max(1, w - 4), Math.max(1, h - 4));
        paintSunkenEdges(g, x, y, w, h);
    }

    /** 只畫凹陷邊線，不覆蓋元件內容（狀態列等文字在 paintComponent 之後畫邊框）。 */
    static void paintSunkenEdges(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(DARK);
        g.drawLine(x, y, x + w - 1, y);
        g.drawLine(x, y + 1, x, y + h - 1);
        g.setColor(LO);
        g.drawLine(x + 1, y + h - 1, x + w - 1, y + h - 1);
        g.drawLine(x + w - 1, y + 1, x + w - 1, y + h - 1);
        g.setColor(HI);
        g.drawRect(x + 1, y + 1, Math.max(0, w - 3), Math.max(0, h - 3));
    }

    static void fillTitleBar(Graphics2D g, int w, int h) {
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setPaint(new GradientPaint(0, 0, TITLE_TOP, 0, Math.max(1, h), TITLE_BOTTOM));
        g.fillRect(0, 0, w, h);
        g.setColor(TITLE_BOTTOM);
        g.drawLine(0, h - 1, w, h - 1);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    static AbstractBorder sunkenBorder(int padding) {
        return new AbstractBorder() {
            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(padding + 2, padding + 2, padding + 2, padding + 2);
            }

            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                paintSunkenEdges((Graphics2D) g, x, y, width, height);
            }
        };
    }
}