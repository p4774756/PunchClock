package com.example.ui.hud;

import com.example.ui.CandyTheme;
import com.example.ui.UiFonts;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 首頁：角色舞台與實用泡泡（#274，無排程／好友／快捷卡片）。
 */
final class HomeBoard {

    static final int BUBBLE = 40;

    private static final Color INK = CandyTheme.INK;
    private static final Color PANEL = new Color(248, 248, 244, 240);
    private static final Color PANEL_EDGE = CandyTheme.EDGE;
    private static final int CHAR_MS = 84;
    private static final int HOLD_MS = 4200;
    private static final int FADE_OUT_MS = 680;
    private static final int GAP_MS = 2400;
    private static final int CROSS_FADE_MS = 240;

    final Rectangle stage = new Rectangle();
    private final Rectangle bubbleBox = new Rectangle();
    private Point tailTarget = new Point();

    private String line;
    private long lineUntil;
    private int lastClick = -1;
    private int lastMood = -1;
    private boolean reducedMotion;
    private String shown;
    private String outgoing;
    private long fadeStart;
    private HomeBubbleFormat.BubbleLine bubbleLine = new HomeBubbleFormat.BubbleLine("", false);
    private boolean bubbleOpensLog;

    private HomeIllustration illustration = HomeIllustration.ROOM_B;
    private HomeBubbleAnchor.Spec anchorOverride;
    private int imageWidth;
    private int imageHeight;
    private Rectangle avoidTopRight = new Rectangle();

    private IntFunction<String> moodPicker = i -> "";
    private Supplier<HomeMoodPool.Band> moodBand = () -> HomeMoodPool.Band.MORNING;
    private IntFunction<String> clickPicker = i -> HomeMoodPool.pickClick(i);

    private String cycleText = "";
    private int typedChars;
    private long phaseStart;
    private byte phase;
    private static final byte PHASE_TYPE = 0;
    private static final byte PHASE_HOLD = 1;
    private static final byte PHASE_FADE = 2;
    private static final byte PHASE_GAP = 3;

    void layout(Rectangle area) {
        stage.setBounds(0, 0, 0, 0);
        bubbleBox.setBounds(0, 0, 0, 0);
        if (area == null || area.width < 40 || area.height < 40) {
            return;
        }
        stage.setBounds(area);
    }

    void setBubble(HomeBubbleFormat.BubbleLine line) {
        boolean wasAmbient = bubbleLine != null && bubbleLine.ambientMood;
        bubbleLine = line == null ? new HomeBubbleFormat.BubbleLine("", false) : line;
        bubbleOpensLog = bubbleLine.openLog;
        if (!bubbleLine.ambientMood) {
            resetCycle(bubbleLine.text);
        } else if (!wasAmbient && (cycleText == null || cycleText.isEmpty())) {
            beginAmbientLine();
        }
    }

    void setIllustrationContext(
            HomeIllustration ill, int imgW, int imgH, Rectangle swapButtonBounds) {
        setIllustrationContext(ill, imgW, imgH, swapButtonBounds, null);
    }

    void setIllustrationContext(
            HomeIllustration ill,
            int imgW,
            int imgH,
            Rectangle swapButtonBounds,
            HomeBubbleAnchor.Spec override) {
        illustration = ill == null ? HomeIllustration.ROOM_B : ill;
        imageWidth = Math.max(1, imgW);
        imageHeight = Math.max(1, imgH);
        avoidTopRight = swapButtonBounds == null ? new Rectangle() : new Rectangle(swapButtonBounds);
        anchorOverride = override;
    }

    Rectangle bubbleBoxForTests() {
        return new Rectangle(bubbleBox);
    }

    void setMoodPicker(IntFunction<String> picker, Supplier<HomeMoodPool.Band> band) {
        moodPicker = picker == null ? i -> "" : picker;
        moodBand = band == null ? () -> HomeMoodPool.Band.MORNING : band;
    }

    void setClickPicker(IntFunction<String> picker) {
        clickPicker = picker == null ? HomeMoodPool::pickClick : picker;
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
        tickCycle();
        syncLine();
        if (outgoing == null) {
            return phase == PHASE_TYPE
                    || phase == PHASE_FADE
                    || phase == PHASE_GAP
                    || (phase == PHASE_HOLD && isAmbientActive());
        }
        if (System.currentTimeMillis() - fadeStart >= CROSS_FADE_MS) {
            outgoing = null;
        }
        return true;
    }

    void sayClick() {
        lastClick = HomeMoodPool.nextClickIndex(lastClick);
        line = clickPicker.apply(lastClick);
        lineUntil = System.currentTimeMillis() + 5600L;
        resetCycle(line);
    }

    void sayDone() {
        line = HomeMoodPool.doneLine();
        lineUntil = System.currentTimeMillis() + 5600L;
        resetCycle(line);
    }

    void sayTrouble() {
        line = "好像遇到問題了，要不要查看日誌？";
        lineUntil = System.currentTimeMillis() + 5600L;
        resetCycle(line);
    }

    void sayUnread(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        line = text.trim();
        lineUntil = System.currentTimeMillis() + 5600L;
        resetCycle(line);
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
        String text = visibleText();
        float alpha = bubbleAlpha();
        if (text == null || text.isEmpty() || alpha < 0.04f) {
            return;
        }
        g.setFont(UiFonts.chinesePlain(13));
        int maxW = Math.min(stage.width - 24, 300);
        List<String> rows = wrap(g, text, maxW - 24);
        FontMetrics metrics = g.getFontMetrics();
        int block = metrics.getHeight();
        int boxH = 14 + block * rows.size();
        int boxW = 24;
        for (String row : rows) {
            boxW = Math.max(boxW, metrics.stringWidth(row) + 24);
        }
        boxW = Math.min(maxW, boxW);
        HomeBubbleAnchor.Placement placement;
        if (anchorOverride != null) {
            placement = HomeBubbleAnchor.placeBubble(
                    stage, anchorOverride, imageWidth, imageHeight, 0, boxW, boxH, avoidTopRight);
        } else {
            placement = HomeBubbleAnchor.placeBubble(
                    stage, illustration, imageWidth, imageHeight, boxW, boxH, avoidTopRight);
        }
        int x = placement.box.x;
        int y = placement.box.y;
        tailTarget = placement.mouth;
        Composite previous = g.getComposite();
        if (alpha < 0.99f) {
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        }
        bubbleBox.setBounds(x, y, boxW, boxH);
        if (hover) {
            g.setColor(new Color(0, 0, 0, 16));
            g.fillRoundRect(x - 2, y - 2, boxW + 4, boxH + 4, 20, 20);
        }
        paintBubbleShape(g, new Rectangle(x, y, boxW, boxH), tailTarget);
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

    private String visibleText() {
        if (outgoing != null && System.currentTimeMillis() - fadeStart < CROSS_FADE_MS / 2) {
            return outgoing;
        }
        String full = shown == null ? activeLine() : shown;
        if (reducedMotion) {
            return full;
        }
        if (phase == PHASE_TYPE) {
            return sliceByCodePoints(full, typedChars);
        }
        return full;
    }

    private float bubbleAlpha() {
        if (reducedMotion) {
            return 1f;
        }
        if (phase == PHASE_FADE) {
            long t = System.currentTimeMillis() - phaseStart;
            if (t >= FADE_OUT_MS) {
                return 0f;
            }
            return 1f - t / (float) FADE_OUT_MS;
        }
        if (phase == PHASE_GAP) {
            return 0f;
        }
        if (outgoing == null) {
            return 1f;
        }
        float t = (System.currentTimeMillis() - fadeStart) / (float) CROSS_FADE_MS;
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
        if (bubbleLine != null && bubbleLine.ambientMood && isAmbientActive()) {
            return cycleText;
        }
        return bubbleLine == null ? "" : bubbleLine.text;
    }

    private boolean isAmbientActive() {
        return bubbleLine != null && bubbleLine.ambientMood && (line == null || System.currentTimeMillis() >= lineUntil);
    }

    private void tickCycle() {
        syncCycleText();
        if (reducedMotion && isAmbientActive()) {
            if (cycleText == null || cycleText.isEmpty()) {
                beginAmbientLine();
            } else if (System.currentTimeMillis() - phaseStart >= HOLD_MS) {
                beginAmbientLine();
            }
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - phaseStart;
        if (phase == PHASE_TYPE) {
            String full = cycleText == null ? "" : cycleText;
            int len = full.codePointCount(0, full.length());
            int expected = Math.min(len, Math.max(typedChars, (int) (elapsed / CHAR_MS) + 1));
            typedChars = expected;
            if (typedChars >= len) {
                phase = PHASE_HOLD;
                phaseStart = now;
            }
            return;
        }
        if (phase == PHASE_HOLD) {
            if (!isAmbientActive()) {
                return;
            }
            if (elapsed >= HOLD_MS) {
                phase = PHASE_FADE;
                phaseStart = now;
            }
            return;
        }
        if (!isAmbientActive()) {
            return;
        }
        if (phase == PHASE_FADE) {
            if (elapsed >= FADE_OUT_MS) {
                phase = PHASE_GAP;
                phaseStart = now;
            }
            return;
        }
        if (phase == PHASE_GAP && elapsed >= GAP_MS) {
            beginAmbientLine();
        }
    }

    private void syncCycleText() {
        if (line != null && System.currentTimeMillis() < lineUntil) {
            if (!line.equals(cycleText)) {
                resetCycle(line);
            }
            return;
        }
        if (bubbleLine != null && bubbleLine.ambientMood) {
            if (cycleText == null || cycleText.isEmpty()) {
                beginAmbientLine();
            }
            return;
        }
        String target = bubbleLine == null ? "" : bubbleLine.text;
        if (!target.equals(cycleText)) {
            resetCycle(target);
        }
    }

    private void beginAmbientLine() {
        HomeMoodPool.Band band = moodBand.get();
        lastMood = HomeMoodPool.nextMoodIndex(band, lastMood);
        String picked = moodPicker.apply(lastMood);
        cycleText = picked == null || picked.isBlank() ? HomeMoodPool.moodAt(band, lastMood) : picked;
        typedChars = reducedMotion ? cycleText.codePointCount(0, cycleText.length()) : 0;
        phase = reducedMotion ? PHASE_HOLD : PHASE_TYPE;
        phaseStart = System.currentTimeMillis();
        shown = cycleText;
        outgoing = null;
    }

    private void resetCycle(String text) {
        cycleText = text == null ? "" : text;
        typedChars = reducedMotion ? cycleText.codePointCount(0, cycleText.length()) : 0;
        phase = reducedMotion ? PHASE_HOLD : PHASE_TYPE;
        phaseStart = System.currentTimeMillis();
        shown = cycleText;
        outgoing = null;
    }

    private static String sliceByCodePoints(String text, int count) {
        if (text == null || text.isEmpty() || count <= 0) {
            return "";
        }
        int end = text.offsetByCodePoints(0, Math.min(count, text.codePointCount(0, text.length())));
        return text.substring(0, end);
    }

    static void paintBubbleShape(Graphics2D g, Rectangle box, Point mouth) {
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            int radius = 18;
            g.setColor(new Color(0, 0, 0, 28));
            g.fillRoundRect(box.x + 1, box.y + 2, box.width, box.height, radius, radius);
            Area outline =
                    new Area(new RoundRectangle2D.Float(box.x, box.y, box.width, box.height, radius, radius));
            if (mouth != null) {
                appendTailTowardMouth(outline, box, mouth);
            }
            g.setColor(PANEL);
            g.fill(outline);
            g.setColor(PANEL_EDGE);
            g.setStroke(new BasicStroke(1f));
            g.draw(outline);
        } finally {
            if (aa != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
            }
        }
    }


    /**
     * 尾巴從泡泡最靠近臉的那一邊長出，尖角沿指向嘴／臉中心的方向，不要固定垂直朝下。
     */
    static void appendTailTowardMouth(Area outline, Rectangle box, Point mouth) {
        if (outline == null || box == null || mouth == null) {
            return;
        }
        double cx = box.x + box.width / 2.0;
        double cy = box.y + box.height / 2.0;
        double dx = mouth.x - cx;
        double dy = mouth.y - cy;
        double len = Math.hypot(dx, dy);
        if (len < 1) {
            dx = 0;
            dy = 1;
            len = 1;
        }
        double ux = dx / len;
        double uy = dy / len;
        Point base = tailBaseOnBubbleEdge(box, ux, uy);
        double reach = Math.hypot(mouth.x - base.x, mouth.y - base.y);
        double tipLen = Math.max(18, Math.min(26, reach * 0.5));
        if (reach < tipLen + 4) {
            tipLen = Math.max(14, reach * 0.7);
        }
        int tipX = (int) Math.round(base.x + ux * tipLen);
        int tipY = (int) Math.round(base.y + uy * tipLen);
        if (mouth.y > box.y + box.height && tipY < box.y + box.height + 16) {
            tipY = box.y + box.height + 16;
            if (Math.abs(uy) > 1e-6) {
                tipX = (int) Math.round(base.x + ux * ((tipY - base.y) / uy));
            }
        }
        double px = -uy;
        double py = ux;
        Path2D tail = new Path2D.Float();
        tail.moveTo(base.x + px * 8, base.y + py * 8);
        tail.lineTo(base.x - px * 9, base.y - py * 9);
        tail.lineTo(tipX, tipY);
        tail.closePath();
        outline.add(new Area(tail));
    }

    /** 從泡泡中心沿方向向量碰到圓角矩形邊的點，當尾巴根部。 */
    static Point tailBaseOnBubbleEdge(Rectangle box, double ux, double uy) {
        int inset = 2;
        double left = box.x + inset;
        double right = box.x + box.width - inset;
        double top = box.y + inset;
        double bottom = box.y + box.height - inset;
        double cx = box.x + box.width / 2.0;
        double cy = box.y + box.height / 2.0;
        double bestT = Double.POSITIVE_INFINITY;
        if (Math.abs(ux) > 1e-6) {
            double edgeX = ux > 0 ? right : left;
            double t = (edgeX - cx) / ux;
            if (t > 0) {
                double y = cy + t * uy;
                if (y >= top - 0.5 && y <= bottom + 0.5) {
                    bestT = Math.min(bestT, t);
                }
            }
        }
        if (Math.abs(uy) > 1e-6) {
            double edgeY = uy > 0 ? bottom : top;
            double t = (edgeY - cy) / uy;
            if (t > 0) {
                double x = cx + t * ux;
                if (x >= left - 0.5 && x <= right + 0.5) {
                    bestT = Math.min(bestT, t);
                }
            }
        }
        if (!Double.isFinite(bestT)) {
            return new Point((int) Math.round(cx), (int) Math.round(bottom));
        }
        return new Point(
                (int) Math.round(cx + ux * bestT),
                (int) Math.round(cy + uy * bestT));
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