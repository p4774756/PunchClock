package com.example.ui.blackjack;

import com.example.BlackjackRules.Card;
import com.example.ui.UiFonts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 用 Java2D 畫撲克牌。花色是向量圖形，不靠字型裡的 ♠♥♦♣（部分 Windows 字型缺字）。
 */
final class CardPainter {

    /** 牌高 ÷ 牌寬。 */
    static final double RATIO = 1.4;

    static final Color RED = new Color(0xE2, 0x41, 0x6E);
    static final Color BLACK = new Color(0x2B, 0x21, 0x40);
    private static final Color FACE_TOP = Color.WHITE;
    private static final Color FACE_BOTTOM = new Color(0xFF, 0xF4, 0xF9);
    private static final Color FACE_EDGE = new Color(0xE4, 0xD3, 0xEE);
    private static final Color BACK_TOP = new Color(0xC8, 0x1E, 0x34);
    private static final Color BACK_BOTTOM = new Color(0x86, 0x0C, 0x20);
    private static final Color COURT = new Color(0xFF, 0xF0, 0xC8);

    private CardPainter() {
    }

    static int arc(int width) {
        return Math.max(6, Math.round(width * 0.16f));
    }

    static void paintShadow(Graphics2D g, int x, int y, int w, int h, float alpha) {
        int arc = arc(w);
        g.setColor(new Color(8, 24, 16, Math.round(90 * alpha)));
        g.fillRoundRect(x + 2, y + 4, w, h, arc, arc);
        g.setColor(new Color(8, 24, 16, Math.round(40 * alpha)));
        g.fillRoundRect(x + 1, y + 2, w + 2, h + 2, arc, arc);
    }

    static void paintFace(Graphics2D g, Card card, int x, int y, int w, int h) {
        int arc = arc(w);
        paintShadow(g, x, y, w, h, 1f);
        g.setPaint(new GradientPaint(x, y, FACE_TOP, x, y + h, FACE_BOTTOM));
        g.fillRoundRect(x, y, w, h, arc, arc);
        g.setColor(FACE_EDGE);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, w - 1, h - 1, arc, arc);

        Color ink = card.isRed() ? RED : BLACK;
        int suit = card.index / 13;
        String rank = card.rank();

        paintCorner(g, rank, suit, ink, x, y, w);
        AffineTransform saved = g.getTransform();
        g.rotate(Math.PI, x + w / 2.0, y + h / 2.0);
        paintCorner(g, rank, suit, ink, x, y, w);
        g.setTransform(saved);

        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        if (isCourt(rank)) {
            paintCourt(g, rank, suit, ink, x, y, w, h);
        } else if ("A".equals(rank)) {
            g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 28));
            double ring = w * 0.62;
            g.fill(new Ellipse2D.Double(cx - ring / 2, cy - ring / 2, ring, ring));
            g.setColor(ink);
            g.fill(suitShape(suit, cx, cy, w * 0.46));
        } else {
            g.setColor(ink);
            g.fill(suitShape(suit, cx, cy, w * 0.36));
        }
    }

    /** 紅色牌背：白邊、金色細框、菱格紋與中央金色黑桃。 */
    static void paintBack(Graphics2D g, int x, int y, int w, int h) {
        int arc = arc(w);
        paintShadow(g, x, y, w, h, 1f);
        java.awt.Image art = CasinoAssets.get(CasinoAssets.CARD_BACK);
        if (art != null) {
            g.drawImage(art, x, y, w, h, null);
            return;
        }
        g.setColor(new Color(0xFF, 0xF8, 0xEE));
        g.fillRoundRect(x, y, w, h, arc, arc);
        int inset = Math.max(3, w / 13);
        int iw = w - inset * 2;
        int ih = h - inset * 2;
        Shape inner = new RoundRectangle2D.Float(x + inset, y + inset, iw, ih, arc * 0.6f, arc * 0.6f);
        g.setPaint(new GradientPaint(x, y, BACK_TOP, x, y + h, BACK_BOTTOM));
        g.fill(inner);
        Shape clip = g.getClip();
        g.clip(inner);
        g.setColor(new Color(0x5E, 0x06, 0x14, 120));
        int step = Math.max(6, w / 6);
        for (int d = -h; d < w + h; d += step) {
            g.drawLine(x + d, y, x + d + h, y + h);
            g.drawLine(x + d + h, y, x + d, y + h);
        }
        g.setClip(clip);
        g.setColor(new Color(0xE9, 0xC0, 0x6A));
        g.setStroke(new BasicStroke(Math.max(1f, w / 60f)));
        int frame = inset + Math.max(2, w / 20);
        g.drawRoundRect(x + frame, y + frame, w - frame * 2, h - frame * 2, arc / 2, arc / 2);
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        g.setColor(new Color(0x8E, 0x0C, 0x20));
        g.fill(new Ellipse2D.Double(cx - w * 0.22, cy - w * 0.22, w * 0.44, w * 0.44));
        g.setPaint(CasinoTheme.gold((float) (cy - w * 0.16), (float) (cy + w * 0.16)));
        g.fill(suitShape(0, cx, cy, w * 0.3));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0xD9, 0xC2, 0xA0));
        g.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
    }

    /** 還沒發牌時的虛線空位。 */
    static void paintSlot(Graphics2D g, int x, int y, int w, int h) {
        int arc = arc(w);
        g.setColor(new Color(255, 255, 255, 26));
        g.fillRoundRect(x, y, w, h, arc, arc);
        g.setColor(new Color(255, 255, 255, 80));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1f, new float[]{5f, 5f}, 0f));
        g.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
        g.setStroke(new BasicStroke(1f));
    }

    private static boolean isCourt(String rank) {
        return "J".equals(rank) || "Q".equals(rank) || "K".equals(rank);
    }

    private static void paintCorner(Graphics2D g, String rank, int suit, Color ink, int x, int y, int w) {
        Font font = UiFonts.latinBold(Math.max(10, Math.round(w * 0.24f)));
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int left = x + Math.max(4, Math.round(w * 0.09f));
        int top = y + Math.max(3, Math.round(w * 0.06f));
        int textW = metrics.stringWidth(rank);
        g.setColor(ink);
        g.drawString(rank, left, top + metrics.getAscent() - metrics.getDescent() / 2);
        double size = w * 0.15;
        double sx = left + textW / 2.0;
        double sy = top + metrics.getAscent() + size * 0.45;
        g.fill(suitShape(suit, sx, sy, size));
    }

    private static void paintCourt(Graphics2D g, String rank, int suit, Color ink, int x, int y, int w, int h) {
        int fx = x + Math.round(w * 0.3f);
        int fy = y + Math.round(h * 0.18f);
        int fw = w - Math.round(w * 0.6f);
        int fh = h - Math.round(h * 0.36f);
        int arc = Math.max(4, fw / 4);
        java.awt.Image art = CasinoAssets.get(CasinoAssets.courtArt(rank));
        if (art != null) {
            int ax = x + Math.round(w * 0.2f);
            int aw = w - Math.round(w * 0.4f);
            int ah = Math.round(aw * art.getHeight(null) / (float) Math.max(1, art.getWidth(null)));
            g.drawImage(art, ax, y + (h - ah) / 2, aw, ah, null);
            return;
        }
        g.setColor(COURT);
        g.fillRoundRect(fx, fy, fw, fh, arc, arc);
        g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 90));
        g.drawRoundRect(fx, fy, fw - 1, fh - 1, arc, arc);

        int scale = Math.max(2, fw / 12);
        int cw = PixelArt.width(PixelArt.CROWN) * scale;
        int crownY = fy + Math.max(3, fh / 10);
        PixelArt.draw(g, PixelArt.CROWN, fx + (fw - cw) / 2, crownY, scale);

        Font font = UiFonts.latinBold(Math.max(12, Math.round(fw * 0.62f)));
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int textY = crownY + PixelArt.height(PixelArt.CROWN) * scale + metrics.getAscent() - 2;
        g.setColor(ink);
        g.drawString(rank, fx + (fw - metrics.stringWidth(rank)) / 2, textY);
        double size = fw * 0.32;
        g.fill(suitShape(suit, fx + fw / 2.0, Math.min(fy + fh - size * 0.7, textY + size * 0.75), size));
    }

    /** 0=♠ 1=♥ 2=♦ 3=♣，以 (cx, cy) 為中心、邊長約 size。 */
    static Shape suitShape(int suit, double cx, double cy, double size) {
        Area unit;
        switch (suit) {
            case 1:
                unit = heart();
                break;
            case 2:
                unit = diamond();
                break;
            case 3:
                unit = club();
                break;
            default:
                unit = spade();
                break;
        }
        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.scale(size, size);
        return at.createTransformedShape(unit);
    }

    private static Area heart() {
        Area area = new Area(circle(-0.24, -0.16, 0.26));
        area.add(new Area(circle(0.24, -0.16, 0.26)));
        area.add(new Area(polygon(-0.49, -0.08, 0.49, -0.08, 0, 0.48)));
        return area;
    }

    private static Area diamond() {
        return new Area(polygon(0, -0.5, 0.38, 0, 0, 0.5, -0.38, 0));
    }

    private static Area spade() {
        Area area = new Area(circle(-0.23, 0.1, 0.25));
        area.add(new Area(circle(0.23, 0.1, 0.25)));
        area.add(new Area(polygon(0, -0.5, -0.47, 0.06, 0.47, 0.06)));
        area.add(new Area(polygon(0, 0.08, -0.19, 0.5, 0.19, 0.5)));
        return area;
    }

    private static Area club() {
        Area area = new Area(circle(0, -0.24, 0.22));
        area.add(new Area(circle(-0.24, 0.08, 0.22)));
        area.add(new Area(circle(0.24, 0.08, 0.22)));
        area.add(new Area(circle(0, 0.02, 0.12)));
        area.add(new Area(polygon(0, 0.02, -0.18, 0.5, 0.18, 0.5)));
        return area;
    }

    private static Shape circle(double cx, double cy, double r) {
        return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
    }

    private static Shape polygon(double... xy) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            path.lineTo(xy[i], xy[i + 1]);
        }
        path.closePath();
        return path;
    }
}
