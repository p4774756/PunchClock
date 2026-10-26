package com.example.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** 圓角欄位、勾勾與下拉箭頭。顏色都從 {@link CandyTheme} 取。 */
final class CandyDraw {

    private CandyDraw() {
    }

    static Graphics2D quality(Graphics2D source) {
        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static void field(Graphics2D source, int width, int height, float hover, boolean focused,
                      boolean enabled, boolean pressed) {
        if (width <= 1 || height <= 1) {
            return;
        }
        Graphics2D g = quality(source);
        int arc = CandyTheme.RADIUS_FIELD;
        int w = width - 1;
        int h = height - 1;
        Color top = enabled ? CandyTheme.FIELD_TOP : CandyTheme.fade(CandyTheme.FIELD_TOP);
        Color bottom = enabled ? CandyTheme.FIELD : CandyTheme.fade(CandyTheme.FIELD);
        if (pressed && enabled) {
            top = CandyTheme.mix(top, CandyTheme.NIGHT, 0.18f);
            bottom = CandyTheme.mix(bottom, CandyTheme.NIGHT, 0.18f);
        }
        g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
        g.fillRoundRect(0, 0, w, h, arc, arc);
        if (enabled && !pressed) {
            g.setColor(new Color(255, 255, 255, 36));
            g.fillRoundRect(2, 1, Math.max(1, w - 4), Math.max(4, h / 3), arc, arc);
        }
        Color edge = CandyTheme.EDGE;
        float stroke = 1f;
        if (!enabled) {
            edge = CandyTheme.fade(CandyTheme.EDGE);
        } else if (focused) {
            edge = CandyTheme.PINK;
            stroke = 2f;
        } else if (hover > 0f) {
            edge = CandyTheme.mix(CandyTheme.EDGE, CandyTheme.EDGE_HOT, hover);
        }
        g.setColor(edge);
        g.setStroke(new BasicStroke(stroke));
        int inset = focused ? 1 : 0;
        g.drawRoundRect(inset, inset, Math.max(1, w - inset * 2), Math.max(1, h - inset * 2), arc, arc);
        g.dispose();
    }

    static void chevron(Graphics2D source, int width, int height, Color color) {
        Graphics2D g = quality(source);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = width / 2;
        int cy = height / 2;
        g.drawLine(cx - 4, cy - 2, cx, cy + 2);
        g.drawLine(cx, cy + 2, cx + 4, cy - 2);
        g.dispose();
    }

    static void check(Graphics2D source, int x, int y, int size, Color color) {
        Graphics2D g = quality(source);
        g.setColor(color);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int x0 = x + 3;
        int y0 = y + size / 2;
        g.drawLine(x0, y0, x0 + 3, y0 + 4);
        g.drawLine(x0 + 3, y0 + 4, x + size - 4, y + 4);
        g.dispose();
    }
}
