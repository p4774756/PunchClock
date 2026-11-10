package com.example.ui.blackjack;

import com.example.ui.CandyTheme;
import com.example.ui.UiFonts;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

/**
 * 牌桌中間那條：左邊牌堆、中間回合提示與狀態文字、右邊 VS。回合換人時提示會彈一下。
 */
public final class TableCenter extends JComponent {

    private static final int POP_MS = 420;

    private String status = "";
    private String turn = "";
    private Color turnColor = CandyTheme.PINK_HI;
    private boolean waiting;
    private int deckLeft = 52;
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
     * @param waiting true 時尾端會有跑動的「…」，提示正在等心跳送達
     */
    public void setTurn(String text, Color color, boolean waiting) {
        String next = text == null ? "" : text;
        boolean changed = !next.equals(turn);
        turn = next;
        turnColor = color == null ? CandyTheme.PINK_HI : color;
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
            repaint();
        }
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
        if ((waiting || popping) && isDisplayable()) {
            timer.start();
        } else if (!waiting && !popping) {
            timer.stop();
        }
    }

    private void tick() {
        repaint();
        syncTimer();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(420, 92);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(260, 76);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        g.setColor(new Color(255, 255, 255, 28));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1f, new float[]{2f, 7f}, 0f));
        g.drawLine(18, h / 2, w - 18, h / 2);
        g.setStroke(new BasicStroke(1f));

        int deckH = Math.min(70, h - 14);
        int deckW = (int) Math.round(deckH / CardPainter.RATIO);
        int deckX = 22;
        int deckY = (h - deckH) / 2;
        int layers = deckLeft <= 0 ? 0 : Math.min(3, 1 + deckLeft / 16);
        for (int i = layers - 1; i >= 0; i--) {
            CardPainter.paintBack(g, deckX + i * 3, deckY - i * 3, deckW, deckH);
        }

        int vsSize = Math.min(44, h - 30);
        int vsX = w - 22 - vsSize;
        int vsY = (h - vsSize) / 2;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(vsX + 1, vsY + 3, vsSize, vsSize);
        g.setColor(CandyTheme.PINK);
        g.fillOval(vsX, vsY, vsSize, vsSize);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(vsX + 2, vsY + 2, vsSize - 4, vsSize - 4);
        Font vsFont = UiFonts.latinBold(Math.max(12, vsSize * 2 / 5));
        g.setFont(vsFont);
        FontMetrics vm = g.getFontMetrics();
        g.drawString("VS", vsX + (vsSize - vm.stringWidth("VS")) / 2,
                vsY + (vsSize - vm.getHeight()) / 2 + vm.getAscent());

        int textLeft = deckX + deckW + 18;
        int textRight = vsX - 14;
        int room = Math.max(40, textRight - textLeft);
        int mid = textLeft + room / 2;

        Font turnFont = UiFonts.chineseBold(16);
        Font statusFont = UiFonts.chinesePlain(13);
        g.setFont(turnFont);
        FontMetrics tm = g.getFontMetrics();
        g.setFont(statusFont);
        FontMetrics sm = g.getFontMetrics();
        int pillH = tm.getHeight() + 8;
        int block = (turn.isEmpty() ? 0 : pillH + 6) + sm.getHeight();
        int top = (h - block) / 2;

        if (!turn.isEmpty()) {
            String label = turn + (waiting ? dots() : "");
            String sizing = turn + (waiting ? "…" : "");
            int pillW = Math.min(room, tm.stringWidth(sizing) + 28);
            float pop = popScale();
            AffineTransform saved = g.getTransform();
            g.translate(mid, top + pillH / 2.0);
            g.scale(pop, pop);
            g.setColor(new Color(0, 0, 0, 70));
            g.fillRoundRect(-pillW / 2, -pillH / 2 + 2, pillW, pillH, pillH, pillH);
            g.setColor(turnColor);
            g.fillRoundRect(-pillW / 2, -pillH / 2, pillW, pillH, pillH, pillH);
            g.setColor(new Color(255, 255, 255, 110));
            g.fillRoundRect(-pillW / 2 + 6, -pillH / 2 + 2, pillW - 12, pillH / 3, pillH / 3, pillH / 3);
            g.setFont(turnFont);
            g.setColor(CardPainter.BLACK);
            String fitted = CandyTheme.fit(label, tm, pillW - 20);
            g.drawString(fitted, -tm.stringWidth(CandyTheme.fit(sizing, tm, pillW - 20)) / 2,
                    -tm.getHeight() / 2 + tm.getAscent());
            g.setTransform(saved);
            top += pillH + 6;
        }

        g.setFont(statusFont);
        g.setColor(new Color(240, 255, 245));
        String fitted = CandyTheme.fit(status, sm, room);
        g.drawString(fitted, mid - sm.stringWidth(fitted) / 2, top + sm.getAscent());
        g.dispose();
    }

    private float popScale() {
        long elapsed = System.currentTimeMillis() - popStart;
        if (elapsed >= POP_MS) {
            return 1f;
        }
        float t = elapsed / (float) POP_MS;
        return 1f + 0.22f * (float) Math.sin(t * Math.PI) * (1f - t);
    }

    private String dots() {
        int n = (int) ((System.currentTimeMillis() / 420) % 4);
        return "...".substring(0, n);
    }
}
