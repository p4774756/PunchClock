package com.example.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/** 欄位勾選與下拉箭頭；Win95 凹陷輸入框。 */
final class CandyDraw {

    private CandyDraw() {
    }

    static Graphics2D quality(Graphics2D source) {
        Graphics2D g = (Graphics2D) source.create();
        return g;
    }

    static void field(Graphics2D source, int width, int height, float hover, boolean focused,
                      boolean enabled, boolean pressed) {
        if (width <= 1 || height <= 1) {
            return;
        }
        Graphics2D g = quality(source);
        Color fill = enabled ? CandyTheme.FIELD : CandyTheme.fade(CandyTheme.FIELD);
        ClassicBevel.fillSunken(g, 0, 0, width, height, fill);
        g.dispose();
    }

    static void chevron(Graphics2D source, int width, int height, Color color) {
        Graphics2D g = quality(source);
        g.setColor(color);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        int cx = width / 2;
        int cy = height / 2;
        g.drawLine(cx - 3, cy - 2, cx, cy + 2);
        g.drawLine(cx, cy + 2, cx + 3, cy - 2);
        g.dispose();
    }

    static void check(Graphics2D source, int x, int y, int size, Color color) {
        Graphics2D g = quality(source);
        g.setColor(color);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        int x0 = x + 3;
        int y0 = y + size / 2;
        g.drawLine(x0, y0, x0 + 3, y0 + 4);
        g.drawLine(x0 + 3, y0 + 4, x + size - 4, y + 4);
        g.dispose();
    }
}