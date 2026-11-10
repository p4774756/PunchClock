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
import java.awt.geom.AffineTransform;

/**
 * 牌桌中間那條：左邊牌堆、中間「BLACKJACK」與回合橫幅、下方一行狀態。回合換人時橫幅會彈一下。
 */
public final class TableCenter extends JComponent implements CasinoTheme.Scalable {

    private static final int POP_MS = 420;

    private String status = "";
    private String turn = "";
    private boolean highlight;
    private boolean waiting;
    private int deckLeft = 52;
    private float scale = 1f;
    private long popStart;
    private final Timer timer;

    public TableCenter() {
        setOpaque(false);
        timer = new Timer(30, e -> tick());
        timer.setRepeats(true);
    }

    public void setStatus(String text) {
        String next = text == null ? "" : text;
        if (!next.equals(status)) {
            status = next;
            repaint();
        }
    }

    /**
     * @param highlight 輪到自己時橫幅外圈發光
     * @param waiting   true 時尾端有跑動的「…」，提示正在等心跳送達
     */
    public void setTurn(String text, boolean highlight, boolean waiting) {
        String next = text == null ? "" : text;
        boolean changed = !next.equals(turn);
        turn = next;
        this.highlight = highlight;
        this.waiting = waiting;
        if (changed && !next.isEmpty()) {
            popStart = System.currentTimeMillis();
        }
        syncTimer();
        repaint();
    }

    public void setDeckLeft(int count) {
        if (count != deckLeft) {
            deckLeft = Math.max(0, count);
            setToolTipText("牌堆剩 " + deckLeft + " 張");
            repaint();
        }
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
        boolean popping = System.currentTimeMillis() - popStart < POP_MS;
        boolean animate = waiting || popping || highlight;
        if (animate && isDisplayable()) {
            timer.start();
        } else if (!animate) {
            timer.stop();
        }
    }

    private void tick() {
        repaint();
        syncTimer();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(CasinoTheme.px(scale, 640), CasinoTheme.px(scale, 126));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
        int w = getWidth();
        int h = getHeight();

        int deckH = Math.min(h - CasinoTheme.px(scale, 6), CasinoTheme.px(scale, 118));
        int deckW = (int) Math.round(deckH / CardPainter.RATIO);
        int deckX = CasinoTheme.px(scale, 6);
        int deckY = (h - deckH) / 2;
        int layers = deckLeft <= 0 ? 0 : Math.min(5, 1 + deckLeft / 10);
        int offset = CasinoTheme.px(scale, 4);
        for (int i = layers - 1; i >= 0; i--) {
            CardPainter.paintBack(g, deckX + i * offset, deckY - i * offset, deckW, deckH);
        }

        int left = deckX + deckW + layers * offset + CasinoTheme.px(scale, 20);
        int right = w - left;
        int mid = w / 2;
        int room = Math.max(CasinoTheme.px(scale, 120), right - left);

        Font title = CasinoTheme.serif(scale, 30, false);
        FontMetrics tm = g.getFontMetrics(title);
        int titleBase = tm.getAscent() - CasinoTheme.px(scale, 2);
        int titleW = tm.stringWidth("BLACKJACK");
        CasinoTheme.goldText(g, "BLACKJACK", title, mid - titleW / 2f, titleBase, Math.max(1f, scale));
        double laurelH = tm.getAscent() * 1.2;
        double laurelGap = CasinoTheme.px(scale, 26);
        CasinoPaint.laurel(g, mid - titleW / 2.0 - laurelGap, titleBase - tm.getAscent() / 2.6, laurelH, -1);
        CasinoPaint.laurel(g, mid + titleW / 2.0 + laurelGap, titleBase - tm.getAscent() / 2.6, laurelH, 1);

        int bannerY = titleBase + CasinoTheme.px(scale, 8);
        int bannerH = CasinoTheme.px(scale, 50);
        Font turnFont = CasinoTheme.chinese(true, scale, 24);
        FontMetrics bm = g.getFontMetrics(turnFont);
        if (!turn.isEmpty()) {
            String sizing = turn + (waiting ? "…" : "");
            int bannerW = Math.min(room, Math.max(CasinoTheme.px(scale, 360), bm.stringWidth(sizing) + bannerH * 2));
            float pop = popScale();
            float glow = highlight ? 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 300.0) : 0f;
            AffineTransform saved = g.getTransform();
            g.translate(mid, bannerY + bannerH / 2.0);
            g.scale(pop, pop);
            CasinoPaint.banner(g, -bannerW / 2.0, -bannerH / 2.0, bannerW, bannerH, scale, glow);
            String fitted = CandyTheme.fit(sizing, bm, bannerW - bannerH);
            String label = waiting && fitted.endsWith("…")
                    ? fitted.substring(0, fitted.length() - 1) + dots() : fitted;
            g.setFont(turnFont);
            g.setColor(CasinoTheme.INK);
            g.drawString(label, -bm.stringWidth(fitted) / 2f, bm.getAscent() / 2f - bm.getDescent() / 2f);
            g.setTransform(saved);
        }

        if (!status.isEmpty()) {
            Font statusFont = CasinoTheme.chinese(false, scale, 15);
            FontMetrics sm = g.getFontMetrics(statusFont);
            String fitted = CandyTheme.fit(status, sm, room);
            int sy = bannerY + bannerH + CasinoTheme.px(scale, 5) + sm.getAscent();
            g.setFont(statusFont);
            g.setColor(new Color(0, 0, 0, 90));
            g.drawString(fitted, mid - sm.stringWidth(fitted) / 2f, sy + 1);
            g.setColor(CasinoTheme.TEXT);
            g.drawString(fitted, mid - sm.stringWidth(fitted) / 2f, sy);
        }
        g.dispose();
    }

    private float popScale() {
        long elapsed = System.currentTimeMillis() - popStart;
        if (elapsed >= POP_MS) {
            return 1f;
        }
        float t = elapsed / (float) POP_MS;
        return 1f + 0.12f * (float) Math.sin(t * Math.PI) * (1f - t);
    }

    private String dots() {
        int n = (int) ((System.currentTimeMillis() / 420) % 4);
        return "...".substring(0, n) + "   ".substring(n);
    }
}
