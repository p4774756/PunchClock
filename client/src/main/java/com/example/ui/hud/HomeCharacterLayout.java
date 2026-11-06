package com.example.ui.hud;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * 首頁角色相對紫色面板的位置。座標都在主視窗內容區裡。
 * <p>
 * 全身圖 {@code /hud/home/home-body.png}：768×1024。上半身和腿腳是同一張圖、
 * 同一個矩形。前臂下緣貼「下一個任務」卡片頂緣；指尖垂在卡片前面。
 * 卡片底緣切在小腿上，右下角只留一小截深紫小腿，接著襪口和襪腳，整段都在視窗內。
 */
public final class HomeCharacterLayout {

    /** 頭髮最高點。 */
    public static final float HAIR_Y = 0.022f;
    /** 臉的中心。 */
    public static final float FACE_X = 0.46f;
    public static final float FACE_Y = 0.21f;
    /** 下巴。放在標題列下面，臉才不會被標題列蓋住。 */
    public static final float CHIN_Y = 0.248f;
    /** 袖子向兩側展開。 */
    public static final float ARM_TOP_Y = 0.227f;
    /** 前臂下緣，對齊卡片頂緣。 */
    public static final float LEDGE_Y = 0.326f;
    /** 指尖，畫在卡片前面。 */
    public static final float FINGERS_Y = 0.378f;
    /** 雙手在圖上的左右緣。卡片文字停在這段左邊。 */
    public static final float HAND_LEFT_X = 0.30f;
    public static final float HAND_RIGHT_X = 0.63f;
    /** 卡片右緣，切過小腿，腳從右下角伸出去。 */
    public static final float CARD_RIGHT_X = 0.70f;
    /** 卡片底緣。再往下只留一小截小腿，接著就是襪口。 */
    public static final float CARD_BOTTOM_Y = 0.68f;
    /** 奶油色襪口開始的高度。 */
    public static final float CUFF_Y = 0.748f;
    /** 襪尖。 */
    public static final float TOE_X = 0.962f;
    /** 鞋底。 */
    public static final float SOLE_Y = 0.956f;
    /** 還原視窗右側透明區的比例。切頁時沿用，避免視窗尺寸重算。 */
    public static final float SOCK_VISIBLE = 0.09f;
    /** 卡片左側留給文字的寬度。 */
    public static final int TEXT_COLUMN = 168;
    private static final float HEAD_RIGHT_X = 0.56f;

    private HomeCharacterLayout() {
    }

    public static final class Box {
        public final Rectangle bust;
        public final Rectangle sock;
        /** 前臂所靠的「下一個任務」卡片，和 bust 用同一套座標。 */
        public final Rectangle card;
        /** 文字右緣。手指從這裡開始，字不要畫過去。 */
        public final int textRight;
        public final boolean tucked;

        Box(Rectangle bust, Rectangle sock, Rectangle card, int textRight, boolean tucked) {
            this.bust = bust;
            this.sock = sock;
            this.card = card;
            this.textRight = textRight;
            this.tucked = tucked;
        }

        public Point face() {
            return new Point(
                    bust.x + Math.round(bust.width * FACE_X),
                    bust.y + Math.round(bust.height * FACE_Y));
        }

        public int armTop() {
            return bust.y + Math.round(bust.height * ARM_TOP_Y);
        }

        public int ledgeY() {
            return bust.y + Math.round(bust.height * LEDGE_Y);
        }

        public int fingersY() {
            return bust.y + Math.round(bust.height * FINGERS_Y);
        }

        public int cuffY() {
            return bust.y + Math.round(bust.height * CUFF_Y);
        }

        public int soleY() {
            return bust.y + Math.round(bust.height * SOLE_Y);
        }

        public int toeX() {
            return bust.x + Math.round(bust.width * TOE_X);
        }
    }

    public static Insets overflow(int width, int height, boolean maximized, boolean home) {
        if (!home || maximized || width < 240 || height < 240) {
            return new Insets(0, 0, 0, 0);
        }
        int top = Math.min(168, Math.max(96, height * 18 / 100));
        int right = Math.round(width * SOCK_VISIBLE / (1f + SOCK_VISIBLE));
        right = Math.min(110, Math.max(48, right));
        return new Insets(top, 0, 0, right);
    }

    /**
     * @param stage 右側內容區。卡片左緣不要蓋住左邊的資訊卡；可以是 null
     */
    public static Box place(
            Rectangle canvas,
            Rectangle panel,
            int titleBarHeight,
            Rectangle stage,
            Rectangle controls,
            boolean maximized,
            Dimension image,
            Dimension ignoredSock) {
        if (canvas == null || panel == null || panel.width < 80 || panel.height < 80
                || image == null || image.width < 1 || image.height < 1) {
            return new Box(new Rectangle(), new Rectangle(), new Rectangle(), 0, maximized);
        }
        float aspect = image.width / (float) image.height;
        int title = Math.max(0, titleBarHeight);
        int topAnchor = panel.y + title + 4;
        float anchorY = maximized ? HAIR_Y : CHIN_Y;
        int floor = Math.min(canvas.y + canvas.height, panel.y + panel.height) - 10;
        int maxH = span(floor - topAnchor, SOLE_Y - anchorY);
        maxH = Math.min(maxH, span(floor - topAnchor, 1f - anchorY));
        maxH = Math.min(maxH, Math.round(200f / Math.max(0.05f, CARD_BOTTOM_Y - LEDGE_Y)));
        if (!maximized) {
            maxH = Math.min(maxH, span(topAnchor - canvas.y, CHIN_Y - HAIR_Y));
        }
        int bodyW = Math.round(maxH * aspect);
        int panelRoom = Math.max(80, panel.width - 28);
        bodyW = Math.min(bodyW, Math.round(panelRoom / TOE_X));
        if (stage != null && stage.width > 120) {
            int span = panel.x + panel.width - stage.x - 24 - TEXT_COLUMN;
            int byStage = Math.round(span / Math.max(0.2f, TOE_X - HAND_LEFT_X));
            bodyW = Math.min(bodyW, Math.max(120, byStage));
        }
        bodyW = Math.max(120, bodyW);
        int bodyH = Math.max(1, Math.round(bodyW / aspect));
        if (bodyH > maxH && maxH > 80) {
            bodyH = maxH;
            bodyW = Math.max(1, Math.round(bodyH * aspect));
        }
        int y = topAnchor - Math.round(bodyH * anchorY);
        int x = panel.x + panel.width - 12 - Math.round(bodyW * TOE_X);
        x = clearTitleButtons(panel, title, controls, x, y, bodyW, bodyH);
        int limit = panel.x + panel.width - 8;
        int edge = maximized ? x + bodyW : x + Math.round(bodyW * TOE_X);
        if (edge > limit) {
            x -= edge - limit;
        }
        if (x < panel.x + 4) {
            x = panel.x + 4;
        }
        int sole = y + Math.round(bodyH * SOLE_Y);
        if (sole > floor) {
            y -= sole - floor;
        }
        if (maximized && y < panel.y) {
            y = panel.y;
        } else if (!maximized && y + Math.round(bodyH * HAIR_Y) < canvas.y) {
            y = canvas.y - Math.round(bodyH * HAIR_Y);
        }
        Rectangle body = new Rectangle(x, y, bodyW, bodyH);
        int textRight = x + Math.round(bodyW * HAND_LEFT_X) - 8;
        int cardRight = x + Math.round(bodyW * CARD_RIGHT_X);
        int cardLeft = textRight - TEXT_COLUMN;
        if (stage != null && stage.width > 80 && cardLeft < stage.x) {
            cardLeft = stage.x;
        }
        if (cardRight > limit) {
            cardRight = limit;
        }
        if (cardRight < cardLeft + 120) {
            cardRight = cardLeft + 120;
        }
        int cardTop = y + Math.round(bodyH * LEDGE_Y);
        int cardBottom = y + Math.round(bodyH * CARD_BOTTOM_Y);
        int cuff = y + Math.round(bodyH * CUFF_Y);
        if (cardBottom < cardTop + 74) {
            cardBottom = cardTop + 74;
        }
        int calfFloor = cuff - 18;
        if (cardBottom > calfFloor && calfFloor > cardTop + 64) {
            cardBottom = calfFloor;
        }
        Rectangle card = new Rectangle(cardLeft, cardTop, cardRight - cardLeft, cardBottom - cardTop);
        return new Box(body, new Rectangle(body), card, textRight, maximized);
    }

    private static int span(int pixels, float fraction) {
        if (fraction <= 0.02f) {
            return 80;
        }
        return Math.max(80, Math.round(Math.max(0, pixels) / fraction));
    }

    /**
     * 頭若壓到標題列上的視窗按鈕，整個人一起往左移。前臂仍貼在同一高度。
     */
    private static int clearTitleButtons(
            Rectangle panel,
            int title,
            Rectangle controls,
            int x,
            int y,
            int bodyW,
            int bodyH) {
        if (controls == null || controls.width <= 0 || controls.x <= panel.x) {
            return x;
        }
        int headTop = y + Math.round(bodyH * 0.08f);
        int headBottom = y + Math.round(bodyH * CHIN_Y);
        int barBottom = panel.y + Math.max(title, controls.y + controls.height - panel.y);
        if (headBottom <= panel.y || headTop >= barBottom) {
            return x;
        }
        int headRight = x + Math.round(bodyW * HEAD_RIGHT_X);
        int limit = controls.x - 8;
        if (headRight > limit) {
            return x - (headRight - limit);
        }
        return x;
    }
}
