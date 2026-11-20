package com.example.ui.hud;

import com.example.ui.UiFonts;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 首頁：角色舞台與實用泡泡（#274，無排程／好友／快捷卡片）。
 */
final class HomeBoard {

    static final int BUBBLE = 40;

    private static final Color INK = new Color(255, 252, 255);
    private static final Color PANEL = new Color(54, 42, 96, 228);
    private static final Color PANEL_EDGE = new Color(142, 116, 178, 170);
    private static final int FADE_MS = 240;
    private static final long IDLE_HOLD_MS = 12_000L;
    private static final String[] CLICK_LINES = {
            "嘿嘿，找我有事嗎？",
            "我一直都在這裡喔～",
            "需要幫忙就叫我吧 ♡",
            "嗯？我在聽。"
    };

    final Rectangle stage = new Rectangle();
    private final Rectangle bubbleBox = new Rectangle();

    private String line;
    private long lineUntil;
    private int lastClick = -1;
    private boolean reducedMotion;
    private String shown;
    private String outgoing;
    private long fadeStart;
    private HomeBubbleFormat.BubbleLine bubbleLine = new HomeBubbleFormat.BubbleLine("", false);
    private boolean bubbleOpensLog;

    void layout(Rectangle area) {
        stage.setBounds(0, 0, 0, 0);
        bubbleBox.setBounds(0, 0, 0, 0);
        if (area == null || area.width < 40 || area.height < 40) {
            return;
        }
        stage.setBounds(area);
    }

    void setBubble(HomeBubbleFormat.BubbleLine line) {
        bubbleLine = line == null ? new HomeBubbleFormat.BubbleLine("", false) : line;
        bubbleOpensLog = bubbleLine.openLog;
    }

    Rectangle hotArea(int id) {
        if (id == BUBBLE && bubbleBox.width > 0) {
            Rectangle box = new Rectangle(bubbleBox);
            box.grow(4, 4);
            return box;
        }
        return new Rectangle();
    }

    int hit(int x, int y) {
        if (bubbleBox.width > 0 && bubbleBox.contains(x, y)) {
            return BUBBLE;
        }
        return -1;
    }

    void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }

    Rectangle bubbleArea() {
        Rectangle area = new Rectangle(stage);
        if (bubbleBox.width > 0) {
            area = area.union(bubbleBox);
        }
        area.grow(8, 8);
        return area;
    }

    boolean animating() {
        syncLine();
        if (outgoing == null) {
            return false;
        }
        if (System.currentTimeMillis() - fadeStart >= FADE_MS) {
            outgoing = null;
        }
        return true;
    }

    void sayClick() {
        int index = ThreadLocalRandom.current().nextInt(CLICK_LINES.length);
        if (CLICK_LINES.length > 1 && index == lastClick) {
            index = (index + 1) % CLICK_LINES.length;
        }
        lastClick = index;
        line = CLICK_LINES[index];
        lineUntil = System.currentTimeMillis() + 5600L;
    }

    void sayDone() {
        line = "完成啦！做得很好 ♡";
        lineUntil = System.currentTimeMillis() + 5600L;
    }

    void sayTrouble() {
        line = "好像遇到問題了，要不要查看日誌？";
        lineUntil = System.currentTimeMillis() + 5600L;
    }

    boolean bubbleOpensLog() {
        return bubbleOpensLog;
    }

    void paint(Graphics2D g, int hot) {
        paintBubble(g, hot == BUBBLE);
    }

    private void paintBubble(Graphics2D g, boolean hover) {
        if (stage.width < 60) {
            return;
        }
        syncLine();
        String text = bubbleText();
        float alpha = bubbleAlpha();
        if (text == null || text.isEmpty() || alpha < 0.04f) {
            return;
        }
        g.setFont(UiFonts.chinesePlain(13));
        int maxW = Math.min(stage.width - 24, 320);
        List<String> rows = wrap(g, text, maxW - 24);
        FontMetrics metrics = g.getFontMetrics();
        int block = metrics.getHeight();
        int boxH = 14 + block * rows.size();
        int boxW = 24;
        for (String row : rows) {
            boxW = Math.max(boxW, metrics.stringWidth(row) + 24);
        }
        boxW = Math.min(maxW, boxW);
        int x = stage.x + (stage.width - boxW) / 2;
        int y = stage.y + Math.max(8, stage.height / 12);
        Composite previous = g.getComposite();
        if (alpha < 0.99f) {
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        }
        bubbleBox.setBounds(x, y, boxW, boxH);
        if (hover) {
            g.setColor(new Color(255, 255, 255, 24));
            g.fillRoundRect(x - 2, y - 2, boxW + 4, boxH + 4, 18, 18);
        }
        paintBubbleShape(g, new Rectangle(x, y, boxW, boxH));
        g.setColor(INK);
        int textY = y + 8 + metrics.getAscent();
        for (String row : rows) {
            g.drawString(row, x + 12, textY);
            textY += block;
        }
        g.setComposite(previous);
    }

    private void syncLine() {
        String target = activeLine();
        if (reducedMotion || shown == null) {
            shown = target;
            outgoing = null;
            return;
        }
        if (!target.equals(shown)) {
            outgoing = shown;
            shown = target;
            fadeStart = System.currentTimeMillis();
        }
    }

    private String bubbleText() {
        if (outgoing != null && System.currentTimeMillis() - fadeStart < FADE_MS / 2) {
            return outgoing;
        }
        return shown == null ? activeLine() : shown;
    }

    private float bubbleAlpha() {
        if (reducedMotion || outgoing == null) {
            return 1f;
        }
        float t = (System.currentTimeMillis() - fadeStart) / (float) FADE_MS;
        if (t >= 1f) {
            outgoing = null;
            return 1f;
        }
        return t < 0.5f ? 1f - t * 2f : (t - 0.5f) * 2f;
    }

    private String activeLine() {
        if (line != null && System.currentTimeMillis() < lineUntil) {
            return line;
        }
        return bubbleLine == null ? "" : bubbleLine.text;
    }

    private static void paintBubbleShape(Graphics2D g, Rectangle box) {
        g.setColor(new Color(16, 10, 32, 50));
        g.fillRoundRect(box.x + 1, box.y + 2, box.width, box.height, 14, 14);
        g.setColor(PANEL);
        g.fillRoundRect(box.x, box.y, box.width, box.height, 14, 14);
        g.setColor(PANEL_EDGE);
        g.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 14, 14);
        Path2D tail = new Path2D.Float();
        int mid = box.x + box.width / 2;
        tail.moveTo(mid - 5, box.y + box.height - 1);
        tail.lineTo(mid, box.y + box.height + 6);
        tail.lineTo(mid + 5, box.y + box.height - 1);
        tail.closePath();
        g.setColor(PANEL);
        g.fill(tail);
        g.setColor(PANEL_EDGE);
        g.draw(tail);
    }

    private static List<String> wrap(Graphics2D g, String text, int width) {
        List<String> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            rows.add("");
            return rows;
        }
        FontMetrics metrics = g.getFontMetrics();
        String rest = text;
        while (!rest.isEmpty() && rows.size() < 4) {
            if (metrics.stringWidth(rest) <= width) {
                rows.add(rest);
                break;
            }
            int end = rest.length();
            while (end > 1 && metrics.stringWidth(rest.substring(0, end)) > width) {
                end--;
            }
            int split = end;
            for (int i = end; i > Math.max(1, end * 2 / 3); i--) {
                char mark = rest.charAt(i - 1);
                if (mark == '，' || mark == '、' || mark == '・' || mark == ' ' || mark == '）') {
                    split = i;
                    break;
                }
            }
            if (rows.size() == 3) {
                rows.add(fit(g, rest, width));
                break;
            }
            rows.add(rest.substring(0, split));
            rest = rest.substring(split);
        }
        return rows;
    }

    private static String fit(Graphics2D g, String text, int width) {
        FontMetrics metrics = g.getFontMetrics();
        if (text == null || metrics.stringWidth(text) <= width) {
            return text == null ? "" : text;
        }
        String trimmed = text;
        while (trimmed.length() > 1 && metrics.stringWidth(trimmed + "…") > width) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "…";
    }
}