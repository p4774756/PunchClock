package com.example.ui.hud;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

/** 首頁插畫泡泡錨點（原圖 0–1，#680）。 */
public final class HomeBubbleAnchor {

    public static final class Spec {
        /** 嘴巴／尾巴指向（原圖比例）。 */
        public final double mouthX;
        public final double mouthY;
        /** 泡泡偏好中心（原圖比例）。 */
        public final double bubbleX;
        public final double bubbleY;

        Spec(double mouthX, double mouthY, double bubbleX, double bubbleY) {
            this.mouthX = mouthX;
            this.mouthY = mouthY;
            this.bubbleX = bubbleX;
            this.bubbleY = bubbleY;
        }
    }

    public static final class Placement {
        public final Rectangle box;
        public final Point mouth;

        Placement(Rectangle box, Point mouth) {
            this.box = box;
            this.mouth = mouth;
        }
    }

    private static final Map<String, Spec> BY_ID = new HashMap<>();

    static {
        BY_ID.put("b", new Spec(0.50, 0.30, 0.36, 0.17));
        BY_ID.put("c", new Spec(0.48, 0.24, 0.34, 0.11));
        BY_ID.put("d", new Spec(0.56, 0.26, 0.40, 0.13));
        BY_ID.put("e", new Spec(0.44, 0.27, 0.30, 0.14));
        BY_ID.put("f", new Spec(0.51, 0.25, 0.37, 0.12));
    }

    private HomeBubbleAnchor() {
    }

    public static Spec specFor(HomeIllustration illustration) {
        if (illustration == null) {
            return BY_ID.get("b");
        }
        Spec spec = BY_ID.get(illustration.id());
        return spec == null ? BY_ID.get("b") : spec;
    }

    public static boolean hasSpecForId(String id) {
        return id != null && BY_ID.containsKey(id.trim().toLowerCase());
    }

    /** 多數 3:4 場景圖（d–f）頂端約四分之一為 AI 填補糊帶；頂樓泳池（c）人物靠上，不裁頂；夜晚房間（b）無此帶。 */
    private static final double SCENE_TOP_PADDING_NORM = 0.25;

    public static int sourceTopCropPx(HomeIllustration illustration, int imgH) {
        if (illustration == null || imgH <= 0) {
            return 0;
        }
        if (illustration == HomeIllustration.ROOM_B || illustration == HomeIllustration.POOL_C) {
            return 0;
        }
        return (int) Math.round(imgH * SCENE_TOP_PADDING_NORM);
    }

    public static Rectangle imageDrawRect(int panelW, int panelH, int imgW, int imgH) {
        return imageDrawRect(panelW, panelH, imgW, imgH, 0);
    }

    public static Rectangle imageDrawRect(
            int panelW, int panelH, int imgW, int imgH, int sourceTopPx) {
        if (panelW < 1 || panelH < 1 || imgW < 1 || imgH < 1) {
            return new Rectangle(0, 0, panelW, panelH);
        }
        int crop = Math.max(0, Math.min(sourceTopPx, imgH - 1));
        int contentH = imgH - crop;
        double scale = Math.max(panelW / (double) imgW, panelH / (double) contentH);
        int dw = (int) Math.ceil(imgW * scale);
        int dh = (int) Math.ceil(contentH * scale);
        int x = (panelW - dw) / 2;
        int y = coverOffsetY(panelH, dh, contentH, scale);
        return new Rectangle(x, y, dw, dh);
    }

    /** cover 縮放後上下裁切：裁切時以原圖與面板各約上 1/3 對齊，略過圖檔頂端填補帶。 */
    static int coverOffsetY(int panelH, int dh, int imgH, double scale) {
        if (dh <= panelH) {
            return (panelH - dh) / 2;
        }
        int y = panelH / 3 - (int) Math.round(imgH / 3.0 * scale);
        int minY = panelH - dh;
        return Math.max(minY, Math.min(0, y));
    }

    public static Point imageNormToPanel(
            double normX, double normY, Rectangle drawRect, int imgW, int imgH) {
        return imageNormToPanel(normX, normY, drawRect, imgW, imgH, 0);
    }

    public static Point imageNormToPanel(
            double normX,
            double normY,
            Rectangle drawRect,
            int imgW,
            int imgH,
            int sourceTopPx) {
        double scale = drawRect.width / (double) imgW;
        int ix = (int) Math.round(normX * imgW);
        int iy = (int) Math.round(normY * imgH);
        int px = drawRect.x + (int) Math.round(ix * scale);
        int crop = Math.max(0, Math.min(sourceTopPx, imgH - 1));
        int contentY = Math.max(0, iy - crop);
        int py = drawRect.y + (int) Math.round(contentY * scale);
        return new Point(px, py);
    }

    public static Placement placeBubble(
            Rectangle stage,
            HomeIllustration illustration,
            int imgW,
            int imgH,
            int boxW,
            int boxH,
            Rectangle topRightAvoid) {
        Spec spec = specFor(illustration);
        int sourceTop = sourceTopCropPx(illustration, imgH);
        Rectangle draw = imageDrawRect(stage.width, stage.height, imgW, imgH, sourceTop);
        Point mouth = imageNormToPanel(spec.mouthX, spec.mouthY, draw, imgW, imgH, sourceTop);
        Point anchor = imageNormToPanel(spec.bubbleX, spec.bubbleY, draw, imgW, imgH, sourceTop);
        int x = anchor.x - boxW / 2;
        int y = anchor.y - boxH - 8;
        if (mouth.x < anchor.x) {
            x = Math.min(x, mouth.x - boxW + 18);
        } else {
            x = Math.max(x, mouth.x - 18);
        }
        Rectangle box = new Rectangle(stage.x + x, stage.y + y, boxW, boxH);
        clampToStage(box, stage, 8);
        if (topRightAvoid != null && topRightAvoid.width > 0) {
            nudgeAwayFrom(box, topRightAvoid, stage);
        }
        mouth.translate(stage.x, stage.y);
        return new Placement(box, mouth);
    }

    private static void clampToStage(Rectangle box, Rectangle stage, int margin) {
        int minX = stage.x + margin;
        int minY = stage.y + margin;
        int maxX = stage.x + stage.width - box.width - margin;
        int maxY = stage.y + stage.height - box.height - margin - 10;
        box.x = Math.max(minX, Math.min(box.x, maxX));
        box.y = Math.max(minY, Math.min(box.y, maxY));
    }

    private static void nudgeAwayFrom(Rectangle box, Rectangle avoid, Rectangle stage) {
        Rectangle grown = new Rectangle(avoid);
        grown.grow(6, 6);
        if (!box.intersects(grown)) {
            return;
        }
        int margin = 8;
        box.y = Math.max(stage.y + margin, avoid.y + avoid.height + margin);
        if (box.intersects(grown)) {
            box.x = Math.max(stage.x + margin, avoid.x - box.width - margin);
        }
        clampToStage(box, stage, margin);
    }
}