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

    public static Rectangle imageDrawRect(
            int panelW, int panelH, int imgW, int imgH, boolean preferTopWhenVerticalCrop) {
        if (panelW < 1 || panelH < 1 || imgW < 1 || imgH < 1) {
            return new Rectangle(0, 0, panelW, panelH);
        }
        double scale = Math.max(panelW / (double) imgW, panelH / (double) imgH);
        int dw = (int) Math.ceil(imgW * scale);
        int dh = (int) Math.ceil(imgH * scale);
        int x = (panelW - dw) / 2;
        int y = preferTopWhenVerticalCrop && dh > panelH ? 0 : (panelH - dh) / 2;
        return new Rectangle(x, y, dw, dh);
    }

    public static Point imageNormToPanel(
            double normX, double normY, Rectangle drawRect, int imgW, int imgH) {
        double scale = drawRect.width / (double) imgW;
        int ix = (int) Math.round(normX * imgW);
        int iy = (int) Math.round(normY * imgH);
        int px = drawRect.x + (int) Math.round(ix * scale);
        int py = drawRect.y + (int) Math.round(iy * scale);
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
        boolean preferTop = illustration != null && illustration.preferTopWhenVerticalCrop();
        Rectangle draw = imageDrawRect(stage.width, stage.height, imgW, imgH, preferTop);
        Point mouth = imageNormToPanel(spec.mouthX, spec.mouthY, draw, imgW, imgH);
        Point anchor = imageNormToPanel(spec.bubbleX, spec.bubbleY, draw, imgW, imgH);
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