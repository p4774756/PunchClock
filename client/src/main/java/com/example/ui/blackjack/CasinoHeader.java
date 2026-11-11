package com.example.ui.blackjack;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 視窗上方：中間「21點 Blackjack」標題、右邊目前模式的牌匾。
 * 標題一律用 Java2D 畫：「點」烙在圖片裡時，出圖字型缺字就會變方框。
 */
public final class CasinoHeader extends JComponent implements CasinoTheme.Scalable {

    private float scale = 1f;
    private boolean lunch;
    private String plaqueTitle = "普通對戰";
    private String plaqueSub = "";

    public CasinoHeader() {
        setOpaque(false);
    }

    public void setMode(boolean lunchMode, String title, String sub) {
        lunch = lunchMode;
        plaqueTitle = title == null ? "" : title;
        plaqueSub = sub == null ? "" : sub;
        setToolTipText(plaqueSub.isEmpty() ? plaqueTitle : plaqueTitle + "：" + plaqueSub);
        repaint();
    }

    @Override
    public void setUiScale(float value) {
        scale = value;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(CasinoTheme.px(scale, CasinoTheme.DESIGN_W), CasinoTheme.px(scale, 150));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
        paintLogo(g, getWidth() / 2.0, getHeight());
        paintPlaque(g);
        g.dispose();
    }

    private void paintLogo(Graphics2D g, double cx, int h) {
        double s = scale;
        double ribbonY = h - 64 * s;
        double ribbonW = 270 * s;
        double ribbonH = 50 * s;

        double cardH = 58 * s;
        double cardW = cardH / CardPainter.RATIO;
        card(g, cx - 112 * s, ribbonY - 26 * s, cardW, cardH, -0.24, 0);
        card(g, cx + 112 * s, ribbonY - 26 * s, cardW, cardH, 0.24, 1);
        CasinoPaint.laurel(g, cx - 172 * s, ribbonY - 6 * s, 76 * s, -1);
        CasinoPaint.laurel(g, cx + 172 * s, ribbonY - 6 * s, 76 * s, 1);

        paintRibbon(g, cx, ribbonY, ribbonW, ribbonH);
        String title = "21點";
        Font big = CasinoTheme.titleChinese(title, scale, 62);
        FontMetrics bm = g.getFontMetrics(big);
        float tw = bm.stringWidth(title);
        CasinoTheme.goldText(g, title, big, (float) (cx - tw / 2), (float) (ribbonY + 6 * s),
                Math.max(2f, 3f * scale));
        Font script = CasinoTheme.serif(scale, 32, true);
        FontMetrics sm = g.getFontMetrics(script);
        String word = "Blackjack";
        float sw = sm.stringWidth(word);
        float base = (float) (ribbonY + ribbonH / 2 + sm.getAscent() / 2.0 - sm.getDescent() / 1.5);
        g.setFont(script);
        g.setColor(new Color(0x4A, 0x06, 0x12));
        g.drawString(word, (float) (cx - sw / 2), base + 2 * scale);
        g.setPaint(new GradientPaint(0, base - sm.getAscent(), Color.WHITE, 0, base, CasinoTheme.GOLD_LIGHT));
        g.drawString(word, (float) (cx - sw / 2), base);
    }

    private void card(Graphics2D g, double cx, double cy, double w, double h, double angle, int suit) {
        Graphics2D c = (Graphics2D) g.create();
        c.rotate(angle, cx, cy);
        double x = cx - w / 2;
        double y = cy - h / 2;
        RoundRectangle2D.Double body = new RoundRectangle2D.Double(x, y, w, h, w * 0.18, w * 0.18);
        c.setColor(new Color(0, 0, 0, 80));
        c.fill(new RoundRectangle2D.Double(x + 2, y + 3, w, h, w * 0.18, w * 0.18));
        c.setColor(CasinoTheme.CREAM);
        c.fill(body);
        c.setColor(CasinoTheme.GOLD_DEEP);
        c.setStroke(new BasicStroke(Math.max(1f, scale * 1.4f)));
        c.draw(body);
        c.setColor(suit == 0 ? new Color(0x2B, 0x21, 0x40) : new Color(0xD8, 0x2A, 0x45));
        c.fill(CardPainter.suitShape(suit, cx, cy + h * 0.05, w * 0.55));
        c.dispose();
    }

    private void paintRibbon(Graphics2D g, double cx, double y, double w, double h) {
        double s = scale;
        double tail = 34 * s;
        double fold = 12 * s;
        for (int side = -1; side <= 1; side += 2) {
            double edge = cx + side * (w / 2 - 6 * s);
            Path2D t = new Path2D.Double();
            t.moveTo(edge, y + fold);
            t.lineTo(edge + side * tail, y + fold);
            t.lineTo(edge + side * (tail - 14 * s), y + fold + h * 0.5);
            t.lineTo(edge + side * tail, y + h + fold);
            t.lineTo(edge, y + h + fold);
            t.closePath();
            g.setColor(new Color(0x8A, 0x0E, 0x24));
            g.fill(t);
            g.setPaint(CasinoTheme.gold((float) y, (float) (y + h + fold)));
            g.setStroke(new BasicStroke(Math.max(1f, 2f * scale)));
            g.draw(t);
        }
        Path2D body = new Path2D.Double();
        body.moveTo(cx - w / 2, y);
        body.quadTo(cx, y - 10 * s, cx + w / 2, y);
        body.lineTo(cx + w / 2, y + h);
        body.quadTo(cx, y + h - 10 * s, cx - w / 2, y + h);
        body.closePath();
        g.setPaint(new GradientPaint(0, (float) y, new Color(0xD8, 0x26, 0x40), 0, (float) (y + h),
                new Color(0x8E, 0x0C, 0x22)));
        g.fill(body);
        CasinoTheme.goldFrame(g, body, Math.max(2f, 3f * scale));
    }

    private void paintPlaque(Graphics2D g) {
        double s = scale;
        double w = 252 * s;
        double h = 88 * s;
        double x = getWidth() - w - 14 * s;
        double y = 14 * s;
        Image frame = CasinoAssets.get(CasinoAssets.MODE_PLAQUE);
        if (frame != null) {
            g.drawImage(frame, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        } else {
            RoundRectangle2D.Double shape = new RoundRectangle2D.Double(x, y, w, h, 16 * s, 16 * s);
            g.setColor(new Color(10, 0, 8, 110));
            g.fill(new RoundRectangle2D.Double(x, y + 4 * s, w, h, 16 * s, 16 * s));
            g.setPaint(new GradientPaint(0, (float) y, CasinoTheme.WINE, 0, (float) (y + h), CasinoTheme.WINE_DARK));
            g.fill(shape);
            CasinoTheme.goldFrame(g, shape, Math.max(2f, 3f * scale));
            g.setColor(new Color(0xE8, 0xC2, 0x6A, 110));
            g.setStroke(new BasicStroke(Math.max(1f, scale)));
            g.draw(new RoundRectangle2D.Double(x + 6 * s, y + 6 * s, w - 12 * s, h - 12 * s, 10 * s, 10 * s));
        }
        double iconSize = 48 * s;
        double ix = x + 14 * s;
        double iy = y + (h - iconSize) / 2;
        if (lunch) {
            Graphics2D ic = (Graphics2D) g.create();
            ic.translate(ix, iy + iconSize * 0.18);
            double unit = iconSize / (PixelArt.width(PixelArt.BENTO));
            ic.scale(unit, unit);
            PixelArt.draw(ic, PixelArt.BENTO, 0, 0, 1);
            ic.dispose();
        } else {
            Graphics2D ic = (Graphics2D) g.create();
            card(ic, ix + iconSize * 0.36, iy + iconSize / 2, iconSize * 0.48, iconSize * 0.68, -0.2, 0);
            card(ic, ix + iconSize * 0.66, iy + iconSize / 2, iconSize * 0.48, iconSize * 0.68, 0.2, 1);
            ic.dispose();
        }
        double tx = ix + iconSize + 12 * s;
        double room = x + w - 14 * s - tx;
        Font titleFont = CasinoTheme.chinese(true, scale, 23);
        Font subFont = CasinoTheme.chinese(false, scale, 15);
        FontMetrics tm = g.getFontMetrics(titleFont);
        FontMetrics sm = g.getFontMetrics(subFont);
        String title = com.example.ui.CandyTheme.fit(plaqueTitle, tm, (int) room);
        float titleBase = (float) (y + h * 0.46);
        g.setFont(titleFont);
        g.setColor(new Color(0x2A, 0x04, 0x10));
        g.drawString(title, (float) tx, titleBase + 2 * scale);
        g.setPaint(new GradientPaint(0, titleBase - tm.getAscent(), Color.WHITE, 0, titleBase, CasinoTheme.GOLD_LIGHT));
        g.drawString(title, (float) tx, titleBase);
        if (!plaqueSub.isEmpty()) {
            double trophy = 16 * s;
            float subBase = (float) (titleBase + sm.getAscent() + 10 * s);
            double sx = tx;
            if (lunch) {
                paintTrophy(g, sx, subBase - trophy, trophy);
                sx += trophy + 6 * s;
            }
            g.setFont(subFont);
            g.setColor(CasinoTheme.TEXT);
            g.drawString(com.example.ui.CandyTheme.fit(plaqueSub, sm, (int) (x + w - 14 * s - sx)), (float) sx, subBase);
        }
    }

    private static void paintTrophy(Graphics2D g, double x, double y, double size) {
        Graphics2D t = (Graphics2D) g.create();
        t.translate(x, y);
        t.scale(size / 24.0, size / 24.0);
        t.setPaint(CasinoTheme.gold(0, 24));
        Path2D cup = new Path2D.Double();
        cup.moveTo(5, 2);
        cup.lineTo(19, 2);
        cup.quadTo(19, 13, 12, 15);
        cup.quadTo(5, 13, 5, 2);
        cup.closePath();
        t.fill(cup);
        t.fillRect(10, 14, 4, 5);
        t.fillRoundRect(6, 19, 12, 4, 2, 2);
        t.setStroke(new BasicStroke(2f));
        t.drawArc(1, 3, 7, 7, 90, 180);
        t.drawArc(16, 3, 7, 7, 270, 180);
        t.dispose();
    }
}
