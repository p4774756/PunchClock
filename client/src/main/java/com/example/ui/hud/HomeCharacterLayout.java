package com.example.ui.hud;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * 首頁角色相對紫色面板的位置。座標都在主視窗內容區裡，不使用螢幕絕對座標。
 * <p>
 * 全身圖 {@code /hud/home/home-body.png}：768×1024。上半身和襪腳是同一張圖，
 * 共用同一個縮放比例和同一個矩形原點，襪腳不再另外平移。
 * 頭髮最高點在高 2.2%；臉中心在寬 46%、高 21%；袖子向兩側展開在高 22.7%；
 * 前臂下緣在高 32.6%，對齊橫邊；指尖在高 37.8%，畫在橫邊前面。
 * 軀幹和大部分腿部落在面板裡。襪尖在寬 96.2%，右框外只留一小段。
 */
public final class HomeCharacterLayout {

    /** 頭髮最高點，相對全身圖高。 */
    public static final float HAIR_Y = 0.022f;
    /** 臉的中心，相對全身圖寬高。 */
    public static final float FACE_X = 0.46f;
    public static final float FACE_Y = 0.21f;
    /** 袖子開始向兩側展開。這條線放在標題列下緣，滑桿和視窗按鈕留在上面。 */
    public static final float ARM_TOP_Y = 0.227f;
    /** 前臂下緣，橫邊頂線對齊這裡。 */
    public static final float LEDGE_Y = 0.326f;
    /** 指尖。這條線以上畫在橫邊前面，以下的軀幹由面板蓋住。 */
    public static final float FINGERS_Y = 0.378f;
    /** 前臂在圖上的左右緣，橫邊只蓋這一段。 */
    public static final float ARM_LEFT_X = 0.04f;
    public static final float ARM_RIGHT_X = 0.82f;
    /** 襪尖。右框切在它左側，只露出一小段。 */
    public static final float TOE_X = 0.962f;
    /** 鞋底。 */
    public static final float SOLE_Y = 0.956f;
    /**
     * 還原視窗時，右側透明區相對面板的比例。視窗溢位沿用這個值。
     * 實際露出的襪尖更短，見 {@link #SOCK_PEEK}。
     */
    public static final float SOCK_VISIBLE = 0.09f;
    /** 右框外最多露出的襪尖寬度。再長就整個人一起往面板裡收。 */
    public static final int SOCK_PEEK = 36;
    /** 頭髮右側，用來避開標題列上的視窗按鈕。 */
    private static final float HEAD_RIGHT_X = 0.56f;

    private HomeCharacterLayout() {
    }

    public static final class Box {
        public final Rectangle bust;
        public final Rectangle sock;
        /** 最大化後整個人收進面板，襪腳不再探出右框。 */
        public final boolean tucked;

        Box(Rectangle bust, Rectangle sock, boolean tucked) {
            this.bust = bust;
            this.sock = sock;
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

        public int soleY() {
            return bust.y + Math.round(bust.height * SOLE_Y);
        }

        /** 襪尖的 x。兩個圖層共用 bust 這個矩形。 */
        public int toeX() {
            return bust.x + Math.round(bust.width * TOE_X);
        }
    }

    /**
     * 右側內容區的短橫邊。頂線對齊前臂下緣，雙手畫在它前面。
     */
    public static Rectangle armLedge(Box box, Rectangle panel) {
        if (box == null || box.tucked || box.bust == null || box.bust.width < 40 || panel == null) {
            return new Rectangle();
        }
        int x = box.bust.x + Math.round(box.bust.width * ARM_LEFT_X);
        int w = Math.round(box.bust.width * (ARM_RIGHT_X - ARM_LEFT_X));
        int left = panel.x + Math.round(panel.width * 0.46f);
        if (x < left) {
            w -= left - x;
            x = left;
        }
        int right = panel.x + panel.width - 4;
        if (x + w > right) {
            w = right - x;
        }
        if (w < 48) {
            return new Rectangle();
        }
        return new Rectangle(x, box.ledgeY(), w, 11);
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
     * @param canvas  主視窗內容區
     * @param panel   紫色面板，含標題列
     * @param titleBarHeight 標題列高度。袖子展開的那條線放在它下緣
     * @param reminder 不再單獨移動襪腳；保留參數是為了版面呼叫點不變
     * @param controls 最小化、最大化、關閉那一排；頭不能蓋住
     */
    public static Box place(
            Rectangle canvas,
            Rectangle panel,
            int titleBarHeight,
            Rectangle reminder,
            Rectangle controls,
            boolean maximized,
            Dimension bustImage,
            Dimension sockImage) {
        if (canvas == null || panel == null || panel.width < 80 || panel.height < 80
                || bustImage == null || bustImage.width < 1 || bustImage.height < 1) {
            return new Box(new Rectangle(), new Rectangle(), maximized);
        }
        Rectangle body = figure(canvas, panel, titleBarHeight, controls, bustImage, maximized);
        return new Box(new Rectangle(body), new Rectangle(body), maximized);
    }

    /**
     * 全身圖的目的矩形。袖子上緣貼標題列下緣，前臂下緣就是橫邊。
     * 右框只留一小段襪尖；若襪尖超出視窗或離身體太遠，整張圖一起往左收，比例不變。
     */
    private static Rectangle figure(
            Rectangle canvas,
            Rectangle panel,
            int titleBarHeight,
            Rectangle controls,
            Dimension image,
            boolean maximized) {
        float aspect = image.width / (float) image.height;
        int title = Math.max(0, titleBarHeight);
        int armLine = panel.y + title;
        int bodyW;
        int bodyH;
        if (maximized) {
            bodyW = Math.max(1, Math.round(panel.width * 0.42f));
            bodyH = Math.max(1, Math.round(bodyW / aspect));
            int room = Math.max(1, panel.height - title - 20);
            int capH = Math.max(1, Math.round(room / Math.max(0.2f, SOLE_Y - ARM_TOP_Y)));
            if (bodyH > capH) {
                bodyH = capH;
                bodyW = Math.max(1, Math.round(bodyH * aspect));
            }
        } else {
            // 先用能避開視窗按鈕的最小尺寸，襪腳才不會被拉到視窗底部。
            // 太大時先把襪尖收進面板，再整個人一起縮小。
            bodyW = restoredWidth(canvas, panel, title, controls, aspect);
            bodyH = Math.max(1, Math.round(bodyW / aspect));
        }
        int y = armLine - Math.round(bodyH * ARM_TOP_Y);
        if (maximized) {
            int hair = y + Math.round(bodyH * HAIR_Y);
            int minHair = panel.y + title;
            if (hair < minHair) {
                y += minHair - hair;
            }
            int sole = y + Math.round(bodyH * SOLE_Y);
            int floor = panel.y + panel.height - 8;
            if (sole > floor) {
                y -= sole - floor;
            }
            if (y < panel.y) {
                y = panel.y;
            }
        } else if (y < canvas.y) {
            y = canvas.y;
        }
        int peek = maximized ? 0 : peekWidth(canvas, panel);
        int x = panel.x + panel.width + peek - Math.round(bodyW * TOE_X);
        x = clearTitleButtons(panel, title, controls, x, y, bodyW, bodyH);
        int toe = x + Math.round(bodyW * TOE_X);
        if (maximized) {
            int limit = panel.x + panel.width - 8;
            int imageRight = x + bodyW;
            if (imageRight > limit) {
                x -= imageRight - limit;
            }
            if (x < panel.x + 4) {
                x = panel.x + 4;
            }
        } else {
            int canvasRight = canvas.x + canvas.width - 2;
            if (toe > canvasRight) {
                x -= toe - canvasRight;
            }
        }
        return new Rectangle(x, y, bodyW, bodyH);
    }

    /**
     * 還原視窗的寬度。先收到能避開視窗按鈕的尺寸，襪尖就留在身體旁邊，
     * 不會被放大後甩到視窗底部。頭髮和鞋底仍要落在視窗裡。
     */
    private static int restoredWidth(
            Rectangle canvas,
            Rectangle panel,
            int title,
            Rectangle controls,
            float aspect) {
        int peek = peekWidth(canvas, panel);
        int buttonLimit = panel.x + panel.width - 148;
        if (controls != null && controls.width > 0 && controls.x > panel.x) {
            buttonLimit = Math.min(buttonLimit, controls.x - 8);
        }
        float headToToe = Math.max(0.2f, TOE_X - HEAD_RIGHT_X);
        int minW = (int) Math.ceil((panel.x + panel.width + peek - buttonLimit) / headToToe);
        int armLine = panel.y + title;
        int roomBelow = Math.max(1, panel.y + panel.height - 12 - armLine);
        int above = Math.max(1, armLine - canvas.y);
        int maxH = Math.min(
                Math.round(above / Math.max(0.05f, ARM_TOP_Y - HAIR_Y)),
                Math.round(roomBelow / Math.max(0.2f, SOLE_Y - ARM_TOP_Y)));
        int maxW = Math.max(1, Math.round(maxH * aspect));
        int armRoom = Math.max(1, panel.x + panel.width - (panel.x + Math.round(panel.width * 0.46f)));
        int maxWByArm = Math.max(1, Math.round(armRoom / Math.max(0.2f, TOE_X - ARM_LEFT_X)));
        maxW = Math.min(maxW, maxWByArm);
        if (minW > maxW) {
            return maxW;
        }
        return Math.max(120, minW);
    }

    /** 右框外的襪尖寬度。透明區不夠時先縮短，讓襪腳收進面板。 */
    private static int peekWidth(Rectangle canvas, Rectangle panel) {
        int margin = canvas.x + canvas.width - (panel.x + panel.width);
        if (margin <= 8) {
            return 0;
        }
        return Math.min(SOCK_PEEK, margin - 6);
    }

    /**
     * 頭若壓到標題列上的視窗按鈕，整個人一起往左移。襪尖因此更收進面板，前臂仍在原來的高度。
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
        int headBottom = y + Math.round(bodyH * 0.25f);
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
