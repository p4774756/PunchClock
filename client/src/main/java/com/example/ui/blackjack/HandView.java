package com.example.ui.blackjack;

import com.example.BlackjackRules.Card;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * 一手牌。新增的牌會從牌堆方向滑進來；換成不相干的一手（新局）時整手重發。
 */
public final class HandView extends JComponent {

    public static final int DEAL_MS = 340;
    public static final int STAGGER_MS = 150;
    private static final int MAX_CARD_H = 118;
    private static final int MIN_CARD_H = 64;

    private final boolean deckAbove;
    private final List<Card> cards = new ArrayList<>();
    private final List<Long> startAt = new ArrayList<>();
    private int slots = 2;
    private boolean dimmed;
    private Timer timer;

    /**
     * @param deckAbove 牌堆在這手牌的上方（自己那手）時為 true，牌會從上面滑下來。
     */
    public HandView(boolean deckAbove) {
        this.deckAbove = deckAbove;
        setOpaque(false);
    }

    /**
     * 換手牌。若新的一手是目前這手往後加牌，只替新牌播動畫；否則整手重發。
     *
     * @param firstDelayMs 第一張新牌延後多久才開始飛（讓兩手牌交錯發）
     * @param stepMs       之後每張新牌再延後多久
     */
    public void setCards(List<Card> next, boolean animate, int firstDelayMs, int stepMs) {
        List<Card> incoming = next == null ? List.of() : next;
        boolean grows = incoming.size() >= cards.size() && incoming.subList(0, cards.size()).equals(cards);
        if (!grows) {
            cards.clear();
            startAt.clear();
        }
        long now = System.currentTimeMillis();
        int added = 0;
        for (int i = cards.size(); i < incoming.size(); i++) {
            cards.add(incoming.get(i));
            startAt.add(animate ? now + firstDelayMs + (long) added * stepMs : 0L);
            added++;
        }
        if (animate && added > 0) {
            startTimer();
        }
        repaint();
    }

    public int cardCount() {
        return cards.size();
    }

    /** 尚未開局時要畫幾個虛線空位。 */
    public void setSlots(int count) {
        slots = Math.max(0, count);
        repaint();
    }

    /** 爆牌時整手淡一點。 */
    public void setDimmed(boolean value) {
        if (dimmed != value) {
            dimmed = value;
            repaint();
        }
    }

    public boolean isAnimating() {
        return timer != null && timer.isRunning();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(380, MAX_CARD_H + 14);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(220, MIN_CARD_H + 10);
    }

    @Override
    public void removeNotify() {
        if (timer != null) {
            timer.stop();
        }
        super.removeNotify();
    }

    private void startTimer() {
        if (timer == null) {
            timer = new Timer(16, e -> tick());
            timer.setRepeats(true);
        }
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean running = false;
        for (Long start : startAt) {
            if (start != 0L && now - start < DEAL_MS) {
                running = true;
                break;
            }
        }
        repaint();
        if (!running) {
            timer.stop();
            for (int i = 0; i < startAt.size(); i++) {
                startAt.set(i, 0L);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int cardH = Math.max(MIN_CARD_H, Math.min(MAX_CARD_H, getHeight() - 12));
        int cardW = (int) Math.round(cardH / CardPainter.RATIO);
        int top = (getHeight() - cardH) / 2;
        int count = cards.isEmpty() ? slots : cards.size();
        if (count == 0) {
            g.dispose();
            return;
        }
        int gap = Math.round(cardW * 0.16f);
        int step = cardW + gap;
        int total = cardW + step * (count - 1);
        int usable = getWidth() - 16;
        if (total > usable && count > 1) {
            step = Math.max(cardW / 4, (usable - cardW) / (count - 1));
            total = cardW + step * (count - 1);
        }
        int left = (getWidth() - total) / 2;

        if (cards.isEmpty()) {
            for (int i = 0; i < count; i++) {
                CardPainter.paintSlot(g, left + i * step, top, cardW, cardH);
            }
            g.dispose();
            return;
        }

        long now = System.currentTimeMillis();
        int travel = Math.max(60, getHeight());
        for (int i = 0; i < cards.size(); i++) {
            long start = startAt.get(i);
            float t = start == 0L ? 1f : (now - start) / (float) DEAL_MS;
            if (t <= 0f) {
                continue;
            }
            t = Math.min(1f, t);
            float e = easeOutBack(t);
            int x = left + i * step;
            int y = top;
            int dx = Math.round((1f - e) * (getWidth() / 2f - x - cardW / 2f) * 0.6f);
            int dy = Math.round((1f - e) * (deckAbove ? -travel : travel));
            Graphics2D cg = (Graphics2D) g.create();
            cg.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, 0.2f + t * 1.4f)));
            double angle = (1f - e) * (deckAbove ? -0.35 : 0.35);
            cg.rotate(angle, x + dx + cardW / 2.0, y + dy + cardH / 2.0);
            CardPainter.paintFace(cg, cards.get(i), x + dx, y + dy, cardW, cardH);
            if (dimmed) {
                cg.setColor(new Color(40, 30, 60, 110));
                cg.fillRoundRect(x + dx, y + dy, cardW, cardH, CardPainter.arc(cardW), CardPainter.arc(cardW));
            }
            cg.dispose();
        }
        g.dispose();
    }

    private static float easeOutBack(float t) {
        float c1 = 1.3f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
