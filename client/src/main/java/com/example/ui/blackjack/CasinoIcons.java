package com.example.ui.blackjack;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 按鈕與橫幅上的小圖示。有 {@code icon/<name>.png} 素材時用素材，否則用 Java2D 畫。
 */
public enum CasinoIcons {
    CARDS("cards"),
    HAND("hand"),
    REFRESH("refresh"),
    CLOSE("close"),
    CHECK("check"),
    HOURGLASS("hourglass"),
    BENTO("bento"),
    SEND("send");

    private final String asset;

    CasinoIcons(String asset) {
        this.asset = asset;
    }

    /** 在 (x, y) 畫一個 size×size 的圖示；{@code ink} 是 Java2D 替代圖的主色。 */
    public void paint(Graphics2D source, int x, int y, int size, Color ink) {
        Image image = CasinoAssets.get(CasinoAssets.icon(asset));
        Graphics2D g = CasinoTheme.quality(source);
        try {
            if (image != null) {
                g.drawImage(image, x, y, size, size, null);
                return;
            }
            g.translate(x, y);
            float s = size / 24f;
            g.scale(s, s);
            draw(g, ink == null ? Color.WHITE : ink);
        } finally {
            g.dispose();
        }
    }

    private void draw(Graphics2D g, Color ink) {
        switch (this) {
            case CARDS:
                card(g, 3, 5, -0.28, new Color(0xD8, 0x2A, 0x45));
                card(g, 9, 3, 0.2, new Color(0xD8, 0x2A, 0x45));
                break;
            case HAND:
                hand(g, ink);
                break;
            case REFRESH: {
                g.setColor(ink);
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(4, 4, 16, 16, 60, 280, Arc2D.OPEN));
                Path2D head = new Path2D.Double();
                head.moveTo(15.5, 1.5);
                head.lineTo(21, 5.5);
                head.lineTo(15, 8.5);
                head.closePath();
                g.fill(head);
                break;
            }
            case CLOSE:
                g.setColor(ink);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(6, 6, 18, 18);
                g.drawLine(18, 6, 6, 18);
                break;
            case CHECK:
                g.setColor(ink);
                g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(4, 13, 10, 19);
                g.drawLine(10, 19, 20, 6);
                break;
            case HOURGLASS:
                hourglass(g);
                break;
            case BENTO:
                PixelArt.draw(g, PixelArt.BENTO, 0, 6, 2);
                break;
            default:
                g.setColor(ink);
                Path2D plane = new Path2D.Double();
                plane.moveTo(3, 11);
                plane.lineTo(21, 3);
                plane.lineTo(14, 21);
                plane.lineTo(11, 13);
                plane.closePath();
                g.fill(plane);
                break;
        }
    }

    private static void card(Graphics2D g, double x, double y, double angle, Color pip) {
        Graphics2D c = (Graphics2D) g.create();
        c.rotate(angle, x + 6, y + 8);
        RoundRectangle2D body = new RoundRectangle2D.Double(x, y, 12, 16, 3, 3);
        c.setColor(Color.WHITE);
        c.fill(body);
        c.setColor(new Color(0xB0, 0x90, 0x60));
        c.setStroke(new BasicStroke(0.8f));
        c.draw(body);
        c.setColor(pip);
        c.fill(CardPainter.suitShape(1, x + 6, y + 9, 7));
        c.dispose();
    }

    private static void hand(Graphics2D g, Color ink) {
        g.setColor(ink);
        double[][] fingers = {{6, 4, 13}, {9.5, 2, 15}, {13, 2.5, 14.5}, {16.5, 5, 12}};
        for (double[] f : fingers) {
            g.fill(new RoundRectangle2D.Double(f[0], f[1], 3.2, f[2], 3.2, 3.2));
        }
        g.fill(new RoundRectangle2D.Double(6, 11, 13.7, 11, 6, 6));
        Path2D thumb = new Path2D.Double();
        thumb.moveTo(6.5, 14);
        thumb.lineTo(2.5, 10.5);
        thumb.quadTo(1.2, 9.6, 2.4, 8.6);
        thumb.quadTo(3.6, 7.8, 5, 9);
        thumb.lineTo(8, 12);
        thumb.closePath();
        g.fill(thumb);
    }

    private static void hourglass(Graphics2D g) {
        g.setColor(CasinoTheme.GOLD_DEEP);
        g.fillRoundRect(4, 1, 16, 3, 2, 2);
        g.fillRoundRect(4, 20, 16, 3, 2, 2);
        Path2D glass = new Path2D.Double();
        glass.moveTo(6, 4);
        glass.lineTo(18, 4);
        glass.lineTo(13, 12);
        glass.lineTo(18, 20);
        glass.lineTo(6, 20);
        glass.lineTo(11, 12);
        glass.closePath();
        g.setColor(new Color(0xEF, 0xF6, 0xFF));
        g.fill(glass);
        g.setColor(new Color(0xF0, 0x9A, 0x3A));
        Path2D sand = new Path2D.Double();
        sand.moveTo(8, 6);
        sand.lineTo(16, 6);
        sand.lineTo(12, 11);
        sand.closePath();
        g.fill(sand);
        g.fillRect(9, 17, 6, 3);
        g.setColor(CasinoTheme.GOLD_DARK);
        g.setStroke(new BasicStroke(1f));
        g.draw(glass);
    }
}
