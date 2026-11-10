package com.example.ui.blackjack;

import com.example.ui.CandyTheme;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;

/**
 * 開局前的等待畫面：雙方頭像、VS、邀請橫幅與說明。不畫空的牌位。
 */
public final class WaitingView extends JComponent implements CasinoTheme.Scalable {

    private String myName = "";
    private Image myAvatar;
    private String peerName = "";
    private Image peerAvatar;
    private String title = "";
    private String subtitle = "";
    private boolean pending;
    private float scale = 1f;
    private final Timer timer;

    public WaitingView() {
        setOpaque(false);
        timer = new Timer(60, e -> repaint());
    }

    public void setPlayers(String me, Image meAvatar, String peer, Image peerAvatarImage) {
        myName = me == null ? "" : me;
        myAvatar = meAvatar;
        peerName = peer == null ? "" : peer;
        peerAvatar = peerAvatarImage;
        repaint();
    }

    /**
     * @param pending true 時顯示沙漏與跳動的三個點（還在等對方）
     */
    public void setMessage(String titleText, String subtitleText, boolean pending) {
        title = titleText == null ? "" : titleText;
        subtitle = subtitleText == null ? "" : subtitleText;
        this.pending = pending;
        syncTimer();
        repaint();
    }

    @Override
    public void setUiScale(float value) {
        scale = value;
        revalidate();
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        syncTimer();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    private void syncTimer() {
        if (pending && isDisplayable()) {
            timer.start();
        } else {
            timer.stop();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(CasinoTheme.px(scale, 760), CasinoTheme.px(scale, 420));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
        int w = getWidth();
        int h = getHeight();
        double s = Math.min(scale, Math.min(w / 760.0, h / 400.0) * 1.0);
        float fs = (float) s;

        double contentH = 400 * s;
        double top = Math.max(0, (h - contentH) / 2);
        double d = 150 * s;
        double cy = top + 20 * s + d / 2;
        double gapX = 170 * s;
        double leftX = w / 2.0 - gapX;
        double rightX = w / 2.0 + gapX;

        paintSide(g, leftX, cy, d, myAvatar, CasinoAssets.PLAYER_DEFAULT, myName, myName + "（你）", -1, fs);
        paintSide(g, rightX, cy, d, peerAvatar, CasinoAssets.peerDefault(peerName), peerName, peerName, 1, fs);

        Font vsFont = CasinoTheme.serif(fs, 58, true);
        FontMetrics vm = g.getFontMetrics(vsFont);
        int vsW = vm.stringWidth("VS");
        float vsBase = (float) (cy + vm.getAscent() / 2.6);
        float glowR = (float) (vsW * 1.1);
        g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(w / 2.0, cy), glowR,
                new float[]{0f, 1f}, new Color[]{new Color(255, 228, 150, 120), new Color(255, 228, 150, 0)}));
        g.fill(new Ellipse2D.Double(w / 2.0 - glowR, cy - glowR, glowR * 2, glowR * 2));
        CasinoTheme.goldText(g, "VS", vsFont, w / 2f - vsW / 2f, vsBase, Math.max(1.5f, 2f * fs));
        CasinoPaint.laurel(g, w / 2.0 - vsW / 2.0 - 30 * s, cy, 74 * s, -1);
        CasinoPaint.laurel(g, w / 2.0 + vsW / 2.0 + 30 * s, cy, 74 * s, 1);

        double bannerY = cy + d / 2 + 74 * s;
        double bannerH = 62 * s;
        Font titleFont = CasinoTheme.chinese(true, fs, 26);
        FontMetrics tm = g.getFontMetrics(titleFont);
        int iconSize = pending ? CasinoTheme.px(fs, 32) : 0;
        int iconGap = pending ? CasinoTheme.px(fs, 12) : 0;
        double maxBanner = w - 60 * s;
        double bannerW = Math.min(maxBanner, Math.max(520 * s, tm.stringWidth(title) + iconSize + iconGap + bannerH * 2));
        double bx = (w - bannerW) / 2;
        CasinoPaint.banner(g, bx, bannerY, bannerW, bannerH, fs, 0f);
        String fitted = CandyTheme.fit(title, tm, (int) (bannerW - bannerH * 1.4 - iconSize - iconGap));
        double blockW = iconSize + iconGap + tm.stringWidth(fitted);
        double tx = w / 2.0 - blockW / 2;
        if (pending) {
            CasinoIcons.HOURGLASS.paint(g, (int) Math.round(tx), (int) Math.round(bannerY + (bannerH - iconSize) / 2),
                    iconSize, CasinoTheme.INK);
        }
        g.setFont(titleFont);
        g.setColor(CasinoTheme.INK);
        g.drawString(fitted, (float) (tx + iconSize + iconGap),
                (float) (bannerY + bannerH / 2 + tm.getAscent() / 2.0 - tm.getDescent() / 2.0));

        double y = bannerY + bannerH + 18 * s;
        if (!subtitle.isEmpty()) {
            Font subFont = CasinoTheme.chinese(false, fs, 19);
            FontMetrics sm = g.getFontMetrics(subFont);
            String sub = CandyTheme.fit(subtitle, sm, (int) maxBanner);
            float base = (float) (y + sm.getAscent());
            g.setFont(subFont);
            g.setColor(new Color(0, 0, 0, 90));
            g.drawString(sub, w / 2f - sm.stringWidth(sub) / 2f, base + 1);
            g.setColor(CasinoTheme.TEXT);
            g.drawString(sub, w / 2f - sm.stringWidth(sub) / 2f, base);
            y = base + sm.getDescent() + 16 * s;
        }
        if (pending) {
            double r = 8 * s;
            long now = System.currentTimeMillis();
            for (int i = 0; i < 3; i++) {
                double phase = ((now / 380.0) - i) % 3;
                boolean lit = phase >= 0 && phase < 1.4;
                double cx = w / 2.0 + (i - 1) * r * 3.4;
                g.setPaint(lit ? CasinoTheme.gold((float) y, (float) (y + r * 2))
                        : new Color(0xB8, 0xB0, 0xA4));
                g.fill(new Ellipse2D.Double(cx - r, y, r * 2, r * 2));
                g.setColor(new Color(255, 255, 255, 150));
                g.fill(new Ellipse2D.Double(cx - r * 0.5, y + r * 0.35, r * 0.7, r * 0.6));
            }
        }
        g.dispose();
    }

    private static void paintSide(Graphics2D g, double cx, double cy, double d, Image avatar, String asset,
                                  String name, String label, int side, float s) {
        double cardH = d * 0.92;
        double cardW = cardH / CardPainter.RATIO;
        for (int i = 1; i >= 0; i--) {
            Graphics2D c = (Graphics2D) g.create();
            double ox = cx + side * (d * 0.42 + i * d * 0.08);
            double angle = side * (0.18 + i * 0.12);
            c.transform(AffineTransform.getRotateInstance(angle, ox, cy));
            CardPainter.paintBack(c, (int) Math.round(ox - cardW / 2), (int) Math.round(cy - cardH / 2),
                    (int) Math.round(cardW), (int) Math.round(cardH));
            c.dispose();
        }
        Medallion.paint(g, cx, cy, d, avatar, asset, name, 0f);

        Font font = CasinoTheme.chinese(true, s, 22);
        FontMetrics fm = g.getFontMetrics(font);
        double plateH = 50 * s;
        double plateW = Math.min(d * 1.5, Math.max(d * 1.12, fm.stringWidth(label) + plateH * 1.2));
        double py = cy + d / 2 - 14 * s;
        double px = cx - plateW / 2;
        double tail = 30 * s;
        if (!CasinoPaint.slice3(g, CasinoAssets.NAME_RIBBON, px - tail, py, plateW + tail * 2, plateH, 45)) {
            ribbonTails(g, px, py, plateW, plateH, s);
            CasinoPaint.plate(g, px, py, plateW, plateH, s);
        }
        String fitted = CandyTheme.fit(label, fm, (int) (plateW - plateH * 0.8));
        g.setFont(font);
        g.setColor(CasinoTheme.TEXT);
        g.drawString(fitted, (float) (cx - fm.stringWidth(fitted) / 2.0),
                (float) (py + plateH / 2 + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
    }

    private static void ribbonTails(Graphics2D g, double x, double y, double w, double h, float s) {
        double tail = 22 * s;
        for (int side = -1; side <= 1; side += 2) {
            double edge = side < 0 ? x + h * 0.3 : x + w - h * 0.3;
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            p.moveTo(edge, y + h * 0.2);
            p.lineTo(edge + side * (h * 0.3 + tail), y + h * 0.25);
            p.lineTo(edge + side * (h * 0.3 + tail * 0.55), y + h * 0.6);
            p.lineTo(edge + side * (h * 0.3 + tail), y + h * 0.95);
            p.lineTo(edge, y + h * 0.9);
            p.closePath();
            g.setColor(CasinoTheme.WINE_DARK);
            g.fill(p);
            g.setPaint(CasinoTheme.gold((float) y, (float) (y + h)));
            g.setStroke(new java.awt.BasicStroke(Math.max(1f, 1.6f * s)));
            g.draw(p);
        }
    }
}
