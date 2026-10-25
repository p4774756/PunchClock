package com.example.ui.hud;

import com.example.ui.UiFonts;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 首頁中間的資訊卡、快捷按鈕，以及角色旁邊的對話氣泡。
 * 位置跟著視窗內容區算，不寫死在畫面上。
 */
final class HomeBoard {

    static final int SCHEDULE = 30;
    static final int FRIENDS = 31;
    static final int RUN = 32;
    static final int CHAT = 33;
    static final int FILE = 34;

    private static final Color INK = new Color(255, 252, 255);
    private static final Color MUTED = new Color(209, 193, 225);
    private static final Color PINK = new Color(244, 109, 167);
    private static final Color GREEN = new Color(117, 214, 162);
    private static final Color ERROR = new Color(255, 118, 139);
    private static final Color PANEL = new Color(54, 42, 96, 228);
    private static final Color PANEL_EDGE = new Color(142, 116, 178, 170);
    private static final Color SOFT = new Color(65, 46, 103, 209);
    private static final Color SOFT_HOT = new Color(86, 59, 124, 240);
    private static final int FADE_MS = 240;
    private static final String[] CLICK_LINES = {
            "嘿嘿，找我有事嗎？",
            "我一直都在這裡喔～",
            "今天也要順順利利！",
            "需要幫忙就叫我吧 ♡",
            "記得偶爾起來活動一下喔！"
    };

    final Rectangle schedule = new Rectangle();
    final Rectangle friends = new Rectangle();
    final Rectangle actions = new Rectangle();
    final Rectangle run = new Rectangle();
    final Rectangle chat = new Rectangle();
    final Rectangle file = new Rectangle();
    final Rectangle stage = new Rectangle();
    final Rectangle reminder = new Rectangle();

    private String line;
    private long lineUntil;
    private int lastClick = -1;
    private boolean reducedMotion;
    private String shown;
    private String outgoing;
    private long fadeStart;

    void layout(Rectangle area) {
        schedule.setBounds(0, 0, 0, 0);
        friends.setBounds(0, 0, 0, 0);
        actions.setBounds(0, 0, 0, 0);
        run.setBounds(0, 0, 0, 0);
        chat.setBounds(0, 0, 0, 0);
        file.setBounds(0, 0, 0, 0);
        stage.setBounds(0, 0, 0, 0);
        reminder.setBounds(0, 0, 0, 0);
        if (area == null || area.width < 80 || area.height < 80) {
            return;
        }
        int gap = 12;
        boolean stacked = area.width < 640;
        int infoW = stacked ? area.width : Math.min(400, Math.max(250, area.width * 40 / 100));
        int infoH = stacked ? Math.min(area.height - 160, Math.max(210, area.height * 46 / 100)) : area.height;
        int scheduleH = 92;
        int friendH = 124;
        int actionH = 122;
        int used = scheduleH + friendH + actionH + gap * 2;
        if (used > infoH) {
            int overflow = used - infoH;
            int cut = Math.min(overflow, Math.max(0, scheduleH - 84));
            scheduleH -= cut;
            overflow -= cut;
            cut = Math.min(overflow, Math.max(0, friendH - 100));
            friendH -= cut;
            overflow -= cut;
            actionH = Math.max(90, actionH - overflow);
        }
        int x = area.x;
        int y = area.y;
        schedule.setBounds(x, y, infoW, scheduleH);
        y += scheduleH + gap;
        friends.setBounds(x, y, infoW, friendH);
        y += friendH + gap;
        actions.setBounds(x, y, infoW, actionH);
        int pad = 12;
        int buttonGap = 10;
        int buttonH = Math.min(72, Math.max(48, actionH - 46));
        int buttonY = y + actionH - 12 - buttonH;
        if (buttonY < y + 34) {
            buttonY = y + 34;
        }
        int buttonW = Math.max(36, (infoW - pad * 2 - buttonGap * 2) / 3);
        run.setBounds(x + pad, buttonY, buttonW, buttonH);
        chat.setBounds(x + pad + buttonW + buttonGap, buttonY, buttonW, buttonH);
        file.setBounds(x + pad + (buttonW + buttonGap) * 2, buttonY,
                Math.max(36, infoW - pad * 2 - (buttonW + buttonGap) * 2), buttonH);
        if (stacked) {
            int top = y + actionH + gap;
            stage.setBounds(area.x, top, area.width, Math.max(0, area.y + area.height - top));
        } else {
            int left = area.x + infoW + gap;
            stage.setBounds(left, area.y, Math.max(0, area.x + area.width - left), area.height);
        }
        int boxW = Math.min(280, Math.max(0, stage.width - 16));
        if (stage.height >= 150 && boxW >= 120) {
            reminder.setBounds(stage.x + 8, stage.y + stage.height - 86, boxW, 74);
        }
    }

    int hit(int x, int y) {
        if (run.contains(x, y)) {
            return RUN;
        }
        if (chat.contains(x, y)) {
            return CHAT;
        }
        if (file.contains(x, y)) {
            return FILE;
        }
        if (schedule.contains(x, y)) {
            return SCHEDULE;
        }
        if (friends.contains(x, y)) {
            return FRIENDS;
        }
        return -1;
    }

    void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }

    /** 台詞切換的那一小段需要重畫，平時不必。 */
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

    void say(String text) {
        line = text;
        lineUntil = System.currentTimeMillis() + 5600L;
    }

    void sayClick() {
        int index = ThreadLocalRandom.current().nextInt(CLICK_LINES.length);
        if (CLICK_LINES.length > 1 && index == lastClick) {
            index = (index + 1) % CLICK_LINES.length;
        }
        lastClick = index;
        say(CLICK_LINES[index]);
    }

    void paint(Graphics2D g, HomeBrief brief, int hot) {
        HomeBrief data = brief == null ? HomeBrief.empty() : brief;
        paintSchedule(g, data, hot == SCHEDULE);
        paintFriends(g, data, hot == FRIENDS);
        paintActions(g, hot);
        paintReminder(g, data);
    }

    void paintBubble(Graphics2D g, Rectangle character) {
        if (stage.width < 80 || character == null || character.width < 40) {
            return;
        }
        syncLine();
        String text = bubbleText();
        float alpha = bubbleAlpha();
        if (text == null || text.isEmpty() || alpha < 0.04f) {
            return;
        }
        g.setFont(UiFonts.chinesePlain(14));
        int lane = Math.max(0, character.x - stage.x - 16);
        int maxW = lane >= 120
                ? Math.min(250, lane)
                : Math.min(250, Math.max(120, stage.width - 24));
        List<String> rows = wrap(g, text, maxW - 28);
        int block = g.getFontMetrics().getHeight();
        int boxH = 16 + block * rows.size();
        int boxW = 28;
        FontMetrics metrics = g.getFontMetrics();
        for (String row : rows) {
            boxW = Math.max(boxW, metrics.stringWidth(row) + 28);
        }
        boxW = Math.min(maxW, boxW);
        int x = character.x - boxW - 14;
        int y = character.y + Math.max(8, character.height / 8);
        boolean above = false;
        if (x < stage.x + 4) {
            x = stage.x + 8;
            y = character.y - boxH - 14;
            above = true;
            if (y < stage.y) {
                y = stage.y;
                above = false;
            }
        }
        if (reminder.width > 0 && y + boxH > reminder.y - 6 && x < reminder.x + reminder.width) {
            y = Math.max(stage.y, reminder.y - boxH - 8);
        }
        Composite previous = g.getComposite();
        if (alpha < 0.99f) {
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        }
        Rectangle box = new Rectangle(x, y, boxW, boxH);
        paintBubbleShape(g, box, above);
        g.setColor(INK);
        int textY = box.y + 8 + metrics.getAscent();
        for (String row : rows) {
            g.drawString(row, box.x + 14, textY);
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
        int hour = LocalTime.now().getHour();
        if (hour >= 5 && hour < 12) {
            return "早安～今天也一起加油吧！";
        }
        if (hour >= 12 && hour < 17) {
            return "忙了一陣子了，要不要休息一下？";
        }
        if (hour >= 17 && hour < 23) {
            return "今天辛苦了～還有事情要忙嗎？";
        }
        return "已經很晚了，記得早點休息喔 ♡";
    }

    private static void paintBubbleShape(Graphics2D g, Rectangle box, boolean above) {
        g.setColor(new Color(16, 10, 32, 50));
        g.fillRoundRect(box.x + 2, box.y + 3, box.width, box.height, 16, 16);
        g.setColor(PANEL);
        g.fillRoundRect(box.x, box.y, box.width, box.height, 16, 16);
        Path2D tail = new Path2D.Float();
        int coverX;
        int coverY;
        int coverW;
        int coverH;
        if (above) {
            int mid = box.x + box.width / 2;
            tail.moveTo(mid - 6, box.y + box.height - 2);
            tail.lineTo(mid, box.y + box.height + 7);
            tail.lineTo(mid + 6, box.y + box.height - 2);
            tail.closePath();
            coverX = mid - 5;
            coverY = box.y + box.height - 3;
            coverW = 10;
            coverH = 4;
        } else {
            int mid = box.y + box.height / 2;
            tail.moveTo(box.x + box.width - 2, mid - 6);
            tail.lineTo(box.x + box.width + 7, mid);
            tail.lineTo(box.x + box.width - 2, mid + 6);
            tail.closePath();
            coverX = box.x + box.width - 4;
            coverY = mid - 5;
            coverW = 4;
            coverH = 10;
        }
        g.fill(tail);
        g.setColor(PANEL_EDGE);
        g.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 16, 16);
        g.draw(tail);
        g.setColor(PANEL);
        g.fillRect(coverX, coverY, coverW, coverH);
    }

    private void paintSchedule(Graphics2D g, HomeBrief brief, boolean hover) {
        if (schedule.width < 40) {
            return;
        }
        panel(g, schedule);
        if (hover) {
            g.setColor(new Color(255, 255, 255, 28));
            g.fillRoundRect(schedule.x, schedule.y, schedule.width, schedule.height, 18, 18);
        }
        g.setFont(UiFonts.chineseBold(16));
        g.setColor(INK);
        g.drawString("今日排程", schedule.x + 16, schedule.y + 26);
        if (!brief.hasWork()) {
            g.setFont(UiFonts.chinesePlain(14));
            g.setColor(MUTED);
            g.drawString("今天還沒有排程。", schedule.x + 16, schedule.y + 56);
            g.drawString("到排程任務裡設定一個吧。", schedule.x + 16, schedule.y + 78);
            return;
        }
        int baseline = paintCounts(g, brief);
        if (schedule.height < 90) {
            return;
        }
        int barX = schedule.x + 16;
        int barY = Math.min(schedule.y + schedule.height - 18, baseline + 14);
        int barW = Math.max(20, schedule.width - 32);
        g.setColor(new Color(32, 24, 58));
        g.fillRoundRect(barX, barY, barW, 8, 8, 8);
        int active = brief.activeTotal();
        if (active > 0 && brief.done > 0) {
            int fill = Math.max(8, barW * brief.done / active);
            g.setColor(PINK);
            g.fillRoundRect(barX, barY, Math.min(barW, fill), 8, 8, 8);
        }
    }

    private int paintCounts(Graphics2D g, HomeBrief brief) {
        String[] labels = {"完成", "待執行", "失敗", "取消"};
        int[] values = {brief.done, brief.waiting, brief.failed, brief.cancelled};
        Color[] colors = {GREEN, PINK, ERROR, MUTED};
        int gap = 16;
        int[] widths = new int[4];
        int total = 0;
        g.setFont(UiFonts.chinesePlain(13));
        FontMetrics plain = g.getFontMetrics();
        g.setFont(UiFonts.chineseBold(13));
        FontMetrics bold = g.getFontMetrics();
        for (int i = 0; i < 4; i++) {
            widths[i] = plain.stringWidth(labels[i]) + bold.stringWidth(" " + values[i]);
            total += widths[i];
        }
        total += gap * 3;
        int max = schedule.width - 32;
        boolean wrap = total > max;
        int x = schedule.x + 16;
        int y = schedule.y + 52;
        for (int i = 0; i < 4; i++) {
            if (wrap && i == 2) {
                x = schedule.x + 16;
                y += 20;
            }
            g.setFont(UiFonts.chinesePlain(13));
            g.setColor(MUTED);
            g.drawString(labels[i], x, y);
            int labelW = g.getFontMetrics().stringWidth(labels[i]);
            g.setFont(UiFonts.chineseBold(13));
            g.setColor(colors[i]);
            g.drawString(" " + values[i], x + labelW, y);
            x += widths[i] + gap;
        }
        return y;
    }

    private void paintFriends(Graphics2D g, HomeBrief brief, boolean hover) {
        if (friends.width < 40) {
            return;
        }
        panel(g, friends);
        if (hover) {
            g.setColor(new Color(255, 255, 255, 28));
            g.fillRoundRect(friends.x, friends.y, friends.width, friends.height, 18, 18);
        }
        g.setFont(UiFonts.chineseBold(16));
        g.setColor(INK);
        g.drawString("好友動態", friends.x + 16, friends.y + 26);
        g.setFont(UiFonts.chinesePlain(13));
        String online = "在線 " + brief.onlineFriends;
        int onlineW = g.getFontMetrics().stringWidth(online);
        g.setColor(brief.onlineFriends > 0 ? GREEN : MUTED);
        g.drawString(online, friends.x + friends.width - 16 - onlineW, friends.y + 26);
        if (brief.recentKnown && friends.height >= 96) {
            int size = 28;
            int ax = friends.x + 16;
            int ay = friends.y + 42;
            paintFace(g, brief, ax, ay, size);
            int textX = ax + size + 10;
            int textW = Math.max(20, friends.width - (textX - friends.x) - 16);
            g.setFont(UiFonts.chineseBold(14));
            g.setColor(INK);
            g.drawString(fit(g, brief.recentFriend, textW), textX, ay + 13);
            g.setFont(UiFonts.chinesePlain(12));
            g.setColor("在線".equals(brief.recentState) ? GREEN : MUTED);
            g.drawString(fit(g, brief.recentState == null ? "" : brief.recentState, textW), textX, ay + 28);
        } else {
            g.setFont(UiFonts.chinesePlain(13));
            g.setColor(MUTED);
            g.drawString(fit(g, brief.recentFriend, friends.width - 32), friends.x + 16, friends.y + 54);
        }
        if (friends.height >= 112) {
            g.setFont(UiFonts.chinesePlain(13));
            g.setColor(brief.cloudOn ? PINK : MUTED);
            g.drawString(fit(g, "連線  " + brief.cloudLabel, friends.width - 32), friends.x + 16, friends.y + friends.height - 16);
        }
    }

    private static void paintFace(Graphics2D g, HomeBrief brief, int x, int y, int size) {
        Image face = brief.recentFace;
        if (face != null) {
            java.awt.Shape old = g.getClip();
            g.clip(new Ellipse2D.Float(x + 1, y + 1, size - 2, size - 2));
            g.drawImage(face, x, y, size, size, null);
            g.setClip(old);
        } else {
            g.setColor(faceColor(brief.recentFriend));
            g.fillOval(x + 1, y + 1, size - 2, size - 2);
            g.setColor(INK);
            g.setFont(UiFonts.chineseBold(13));
            String letter = faceLetter(brief.recentFriend);
            FontMetrics metrics = g.getFontMetrics();
            int tx = x + (size - metrics.stringWidth(letter)) / 2;
            int ty = y + (size - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(letter, tx, ty);
        }
        boolean online = "在線".equals(brief.recentState);
        g.setColor(new Color(28, 20, 48));
        g.fillOval(x + size - 11, y + size - 11, 10, 10);
        g.setColor(online ? GREEN : new Color(120, 108, 148));
        g.fillOval(x + size - 9, y + size - 9, 6, 6);
    }

    private static Color faceColor(String id) {
        int n = Math.floorMod(id == null ? 0 : id.hashCode(), 4);
        if (n == 0) {
            return new Color(186, 112, 164);
        }
        if (n == 1) {
            return new Color(132, 104, 196);
        }
        if (n == 2) {
            return new Color(206, 132, 168);
        }
        return new Color(112, 92, 176);
    }

    private static String faceLetter(String id) {
        if (id == null) {
            return "?";
        }
        String clean = id.trim();
        if (clean.isEmpty()) {
            return "?";
        }
        return clean.substring(0, clean.offsetByCodePoints(0, 1));
    }

    private void paintReminder(Graphics2D g, HomeBrief brief) {
        if (reminder.width < 80) {
            return;
        }
        panel(g, reminder);
        g.setFont(UiFonts.chinesePlain(12));
        g.setColor(MUTED);
        g.drawString("下一個任務", reminder.x + 14, reminder.y + 22);
        g.setFont(UiFonts.chineseBold(15));
        g.setColor(INK);
        String title = brief.nextName == null ? "還沒排程" : brief.nextName;
        g.drawString(fit(g, title, reminder.width - 28), reminder.x + 14, reminder.y + 44);
        g.setFont(UiFonts.chinesePlain(13));
        g.setColor(PINK);
        String when;
        if (brief.nextName == null) {
            when = "到排程任務裡設定";
        } else if (brief.nextTime != null && !brief.nextTime.isBlank()) {
            when = brief.nextTime;
        } else if ("執行中".equals(brief.nextLeft)) {
            when = "執行中";
        } else {
            when = "時間未定";
        }
        g.drawString(fit(g, when, reminder.width - 28), reminder.x + 14, reminder.y + 64);
    }

    private void paintActions(Graphics2D g, int hot) {
        if (actions.width < 80) {
            return;
        }
        panel(g, actions);
        g.setFont(UiFonts.chineseBold(16));
        g.setColor(INK);
        g.drawString("快捷操作", actions.x + 16, actions.y + 26);
        paintAction(g, run, "執行任務", hot == RUN, "play");
        paintAction(g, chat, "開啟聊天", hot == CHAT, "chat");
        paintAction(g, file, "傳送檔案", hot == FILE, "send-file");
    }

    private static void paintAction(Graphics2D g, Rectangle box, String text, boolean hover, String icon) {
        if (box.width < 36 || box.height < 36) {
            return;
        }
        g.setColor(hover ? SOFT_HOT : SOFT);
        g.fillRoundRect(box.x, box.y, box.width, box.height, 12, 12);
        g.setColor(hover ? PINK : PANEL_EDGE);
        g.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 12, 12);
        int iconSize = Math.min(22, Math.max(16, box.height - 36));
        int iconX = box.x + (box.width - iconSize) / 2;
        int iconY = box.y + Math.max(8, (box.height - 18 - iconSize) / 2);
        KitIcon.draw(g, icon, iconX, iconY, iconSize, PINK);
        g.setFont(UiFonts.chinesePlain(13));
        g.setColor(INK);
        FontMetrics metrics = g.getFontMetrics();
        String label = fit(g, text, box.width - 8);
        int tx = box.x + Math.max(4, (box.width - metrics.stringWidth(label)) / 2);
        int ty = box.y + box.height - 12;
        g.drawString(label, tx, ty);
    }

    private static void panel(Graphics2D g, Rectangle rect) {
        g.setColor(new Color(16, 10, 32, 50));
        g.fillRoundRect(rect.x + 2, rect.y + 3, rect.width, rect.height, 18, 18);
        g.setColor(PANEL);
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 18, 18);
        g.setColor(PANEL_EDGE);
        g.drawRoundRect(rect.x, rect.y, rect.width - 1, rect.height - 1, 18, 18);
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

    private static List<String> wrap(Graphics2D g, String text, int width) {
        List<String> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            rows.add("");
            return rows;
        }
        FontMetrics metrics = g.getFontMetrics();
        String rest = text;
        while (!rest.isEmpty() && rows.size() < 3) {
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
                if (mark == '，' || mark == '、' || mark == '～' || mark == '？' || mark == '！' || mark == ' ') {
                    split = i;
                    break;
                }
            }
            if (rows.size() == 2) {
                rows.add(fit(g, rest, width));
                break;
            }
            rows.add(rest.substring(0, split));
            rest = rest.substring(split);
        }
        return rows;
    }
}
