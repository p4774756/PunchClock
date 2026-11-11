package com.example.ui.blackjack;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 獎盃與服務生貓：先畫 {@link CasinoAssets#TROPHY}／{@link CasinoAssets#WAITER_CAT} 正式素材，
 * 素材讀不到時才用這裡的 Java2D 備援圖，版面大小兩者一致。
 */
final class StandInArt {

    private StandInArt() {
    }

    /** 獎盃：素材優先；(cx, bottom) 是底座中心，h 是整體高度。 */
    static void trophy(Graphics2D source, double cx, double bottom, double h) {
        if (CasinoPaint.assetFit(source, CasinoAssets.TROPHY, cx, bottom, h, h)) {
            return;
        }
        Graphics2D g = (Graphics2D) source.create();
        double w = h * 0.9;
        double top = bottom - h;
        g.setColor(new Color(20, 4, 12, 90));
        g.fill(new Ellipse2D.Double(cx - w * 0.36, bottom - h * 0.06, w * 0.72, h * 0.12));

        g.setPaint(CasinoTheme.gold((float) top, (float) bottom));
        g.setStroke(new BasicStroke((float) (h * 0.07), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(cx - w * 0.5, top + h * 0.1, w * 0.3, h * 0.28));
        g.draw(new Ellipse2D.Double(cx + w * 0.2, top + h * 0.1, w * 0.3, h * 0.28));

        Path2D cup = new Path2D.Double();
        cup.moveTo(cx - w * 0.34, top + h * 0.04);
        cup.lineTo(cx + w * 0.34, top + h * 0.04);
        cup.curveTo(cx + w * 0.34, top + h * 0.48, cx + w * 0.12, top + h * 0.58, cx, top + h * 0.6);
        cup.curveTo(cx - w * 0.12, top + h * 0.58, cx - w * 0.34, top + h * 0.48, cx - w * 0.34, top + h * 0.04);
        cup.closePath();
        g.setPaint(CasinoTheme.gold((float) top, (float) (top + h * 0.6)));
        g.fill(cup);
        g.setColor(CasinoTheme.GOLD_DARK);
        g.setStroke(new BasicStroke((float) Math.max(1, h * 0.025)));
        g.draw(cup);

        g.setPaint(CasinoTheme.gold((float) (top + h * 0.58), (float) bottom));
        g.fill(new RoundRectangle2D.Double(cx - w * 0.06, top + h * 0.58, w * 0.12, h * 0.18, 2, 2));
        RoundRectangle2D base = new RoundRectangle2D.Double(cx - w * 0.26, top + h * 0.76, w * 0.52, h * 0.2,
                h * 0.06, h * 0.06);
        g.setPaint(new GradientPaint(0, (float) (top + h * 0.76), CasinoTheme.WINE, 0, (float) bottom,
                CasinoTheme.WINE_DARK));
        g.fill(base);
        g.setColor(CasinoTheme.GOLD);
        g.draw(base);

        g.setColor(new Color(255, 255, 255, 230));
        g.fill(CasinoPaint.PixelStar.shape(cx, top + h * 0.26, h * 0.12));
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(new Ellipse2D.Double(cx - w * 0.26, top + h * 0.1, w * 0.1, h * 0.26));
        g.dispose();
    }

    /** 端著餐盤的服務生貓：素材優先；(cx, bottom) 是腳底中心，h 是整體高度。 */
    static void waiterCat(Graphics2D source, double cx, double bottom, double h) {
        if (CasinoPaint.assetFit(source, CasinoAssets.WAITER_CAT, cx, bottom, h, h)) {
            return;
        }
        Graphics2D g = (Graphics2D) source.create();
        double u = h / 100.0;
        double top = bottom - h;
        Color fur = new Color(0xFF, 0xC0, 0x7A);
        Color furDark = new Color(0xE0, 0x8E, 0x48);
        Color line = new Color(0x5A, 0x32, 0x34);

        g.setColor(new Color(20, 4, 12, 80));
        g.fill(new Ellipse2D.Double(cx - 26 * u, bottom - 5 * u, 52 * u, 10 * u));

        RoundRectangle2D body = new RoundRectangle2D.Double(cx - 22 * u, top + 50 * u, 44 * u, 48 * u, 30 * u, 30 * u);
        g.setPaint(new GradientPaint(0, (float) (top + 50 * u), new Color(0x3A, 0x22, 0x3A), 0, (float) bottom,
                new Color(0x1E, 0x10, 0x20)));
        g.fill(body);
        g.setColor(Color.WHITE);
        Path2D shirt = new Path2D.Double();
        shirt.moveTo(cx - 9 * u, top + 52 * u);
        shirt.lineTo(cx + 9 * u, top + 52 * u);
        shirt.lineTo(cx, top + 80 * u);
        shirt.closePath();
        g.fill(shirt);
        g.setColor(new Color(0xD8, 0x2A, 0x45));
        Path2D bow = new Path2D.Double();
        bow.moveTo(cx, top + 56 * u);
        bow.lineTo(cx - 9 * u, top + 51 * u);
        bow.lineTo(cx - 9 * u, top + 61 * u);
        bow.closePath();
        bow.moveTo(cx, top + 56 * u);
        bow.lineTo(cx + 9 * u, top + 51 * u);
        bow.lineTo(cx + 9 * u, top + 61 * u);
        bow.closePath();
        g.fill(bow);

        double headCy = top + 34 * u;
        g.setColor(furDark);
        g.fill(ear(cx - 15 * u, headCy - 12 * u, 13 * u, -1));
        g.fill(ear(cx + 15 * u, headCy - 12 * u, 13 * u, 1));
        g.setColor(new Color(0xFF, 0xB6, 0xC8));
        g.fill(ear(cx - 15 * u, headCy - 9 * u, 7 * u, -1));
        g.fill(ear(cx + 15 * u, headCy - 9 * u, 7 * u, 1));
        g.setColor(fur);
        g.fill(new Ellipse2D.Double(cx - 24 * u, headCy - 18 * u, 48 * u, 38 * u));
        g.setColor(new Color(0xFF, 0xF6, 0xEC));
        g.fill(new Ellipse2D.Double(cx - 12 * u, headCy - 2 * u, 24 * u, 18 * u));
        g.setColor(new Color(0x2E, 0x1C, 0x24));
        g.fill(new Ellipse2D.Double(cx - 12 * u, headCy - 6 * u, 5 * u, 7 * u));
        g.fill(new Ellipse2D.Double(cx + 7 * u, headCy - 6 * u, 5 * u, 7 * u));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(cx - 11 * u, headCy - 5 * u, 2 * u, 2 * u));
        g.fill(new Ellipse2D.Double(cx + 8 * u, headCy - 5 * u, 2 * u, 2 * u));
        g.setColor(new Color(0xFF, 0x8F, 0xA8, 170));
        g.fill(new Ellipse2D.Double(cx - 19 * u, headCy + 2 * u, 7 * u, 4 * u));
        g.fill(new Ellipse2D.Double(cx + 12 * u, headCy + 2 * u, 7 * u, 4 * u));
        g.setColor(line);
        g.setStroke(new BasicStroke((float) Math.max(1, 1.4 * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D mouth = new Path2D.Double();
        mouth.moveTo(cx - 4 * u, headCy + 5 * u);
        mouth.quadTo(cx - 2 * u, headCy + 8 * u, cx, headCy + 5 * u);
        mouth.quadTo(cx + 2 * u, headCy + 8 * u, cx + 4 * u, headCy + 5 * u);
        g.draw(mouth);

        double trayY = top + 58 * u;
        g.setColor(fur);
        g.fill(new Ellipse2D.Double(cx + 16 * u, trayY - 2 * u, 12 * u, 10 * u));
        g.setPaint(CasinoTheme.gold((float) (trayY - 22 * u), (float) trayY));
        Path2D cloche = new Path2D.Double();
        cloche.moveTo(cx + 8 * u, trayY);
        cloche.curveTo(cx + 8 * u, trayY - 20 * u, cx + 42 * u, trayY - 20 * u, cx + 42 * u, trayY);
        cloche.closePath();
        g.fill(cloche);
        g.fill(new Ellipse2D.Double(cx + 23 * u, trayY - 20 * u, 4 * u, 4 * u));
        g.fill(new RoundRectangle2D.Double(cx + 4 * u, trayY - 1 * u, 42 * u, 4 * u, 4 * u, 4 * u));
        g.setColor(new Color(255, 255, 255, 140));
        g.fill(new Ellipse2D.Double(cx + 14 * u, trayY - 14 * u, 5 * u, 7 * u));
        g.dispose();
    }

    private static Path2D ear(double cx, double base, double size, int side) {
        Path2D ear = new Path2D.Double();
        ear.moveTo(cx - size * 0.55, base + size * 0.6);
        ear.lineTo(cx + side * size * 0.15, base - size * 0.55);
        ear.lineTo(cx + size * 0.55, base + size * 0.6);
        ear.closePath();
        return ear;
    }
}
