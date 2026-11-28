package com.example.ui.hud;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

/** 首頁插畫與相簿泡泡錨點（原圖 0–1，#680／#868）。 */
public final class HomeBubbleAnchor {

    public static final class Spec {
        /** 嘴巴／尾巴指向（原圖比例）。 */
        public final double mouthX;
        public final double mouthY;
        /** 泡泡偏好中心（原圖比例）。 */
        public final double bubbleX;
        public final double bubbleY;
        /** 臉＋髮避開區（原圖比例）；寬高為 0 表示沒有。 */
        public final double faceX;
        public final double faceY;
        public final double faceW;
        public final double faceH;

        Spec(double mouthX, double mouthY, double bubbleX, double bubbleY) {
            this(mouthX, mouthY, bubbleX, bubbleY, 0, 0, 0, 0);
        }

        Spec(
                double mouthX,
                double mouthY,
                double bubbleX,
                double bubbleY,
                double faceX,
                double faceY,
                double faceW,
                double faceH) {
            this.mouthX = clamp01(mouthX);
            this.mouthY = clamp01(mouthY);
            this.bubbleX = clamp01(bubbleX);
            this.bubbleY = clamp01(bubbleY);
            this.faceX = clamp01(faceX);
            this.faceY = clamp01(faceY);
            this.faceW = Math.max(0, Math.min(1, faceW));
            this.faceH = Math.max(0, Math.min(1, faceH));
        }

        public boolean hasFaceBox() {
            return faceW >= 0.05 && faceH >= 0.05;
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
        // g 正面中景：臉在水平中央、上方三分之一；泡泡放左上避開臉與右手。
        BY_ID.put("g", new Spec(0.50, 0.22, 0.22, 0.11, 0.32, 0.07, 0.36, 0.28));
        // h 側身舞蹈中景：臉略偏右；泡泡放左上避開臉與舉手。
        BY_ID.put("h", new Spec(0.56, 0.20, 0.22, 0.10, 0.42, 0.07, 0.36, 0.28));
    }

    private HomeBubbleAnchor() {
    }

    public static Spec spec(double mouthX, double mouthY, double bubbleX, double bubbleY) {
        return new Spec(mouthX, mouthY, bubbleX, bubbleY);
    }

    public static Spec spec(
            double mouthX,
            double mouthY,
            double bubbleX,
            double bubbleY,
            double faceX,
            double faceY,
            double faceW,
            double faceH) {
        return new Spec(mouthX, mouthY, bubbleX, bubbleY, faceX, faceY, faceW, faceH);
    }

    /**
     * 沒有 metadata、也估不到臉時的後備：泡泡放左上安全帶，尾巴指向畫面中上（常見人像臉區），
     * 並帶一個假設臉框供避開。
     */
    public static Spec safeTopFallback() {
        return new Spec(0.50, 0.32, 0.20, 0.11, 0.30, 0.06, 0.42, 0.34);
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

    /** 僅 d–f 場景圖頂端約四分之一為 AI 填補糊帶；b／c 與加油女孩中景人像（g／h）不裁頂。 */
    private static final double SCENE_TOP_PADDING_NORM = 0.25;

    public static int sourceTopCropPx(HomeIllustration illustration, int imgH) {
        if (illustration == null || imgH <= 0) {
            return 0;
        }
        if (illustration == HomeIllustration.CHERRY_PARK_D
                || illustration == HomeIllustration.NIGHT_MARKET_E
                || illustration == HomeIllustration.OVERTIME_F) {
            return (int) Math.round(imgH * SCENE_TOP_PADDING_NORM);
        }
        return 0;
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

    public static Rectangle imageNormRectToPanel(
            double normX,
            double normY,
            double normW,
            double normH,
            Rectangle drawRect,
            int imgW,
            int imgH,
            int sourceTopPx) {
        Point a = imageNormToPanel(normX, normY, drawRect, imgW, imgH, sourceTopPx);
        Point b = imageNormToPanel(normX + normW, normY + normH, drawRect, imgW, imgH, sourceTopPx);
        int x = Math.min(a.x, b.x);
        int y = Math.min(a.y, b.y);
        return new Rectangle(x, y, Math.max(1, Math.abs(b.x - a.x)), Math.max(1, Math.abs(b.y - a.y)));
    }

    public static Placement placeBubble(
            Rectangle stage,
            HomeIllustration illustration,
            int imgW,
            int imgH,
            int boxW,
            int boxH,
            Rectangle topRightAvoid) {
        return placeBubble(
                stage,
                specFor(illustration),
                imgW,
                imgH,
                sourceTopCropPx(illustration, imgH),
                boxW,
                boxH,
                topRightAvoid);
    }

    public static Placement placeBubble(
            Rectangle stage,
            Spec spec,
            int imgW,
            int imgH,
            int sourceTopPx,
            int boxW,
            int boxH,
            Rectangle topRightAvoid) {
        if (spec == null) {
            spec = safeTopFallback();
        }
        if (stage == null || stage.width < 1 || stage.height < 1) {
            return new Placement(new Rectangle(), new Point());
        }
        Rectangle draw = imageDrawRect(stage.width, stage.height, imgW, imgH, sourceTopPx);
        Point mouth = imageNormToPanel(spec.mouthX, spec.mouthY, draw, imgW, imgH, sourceTopPx);
        Point anchor = imageNormToPanel(spec.bubbleX, spec.bubbleY, draw, imgW, imgH, sourceTopPx);
        int x = anchor.x - boxW / 2;
        int y = anchor.y - boxH - 8;
        // 內建插畫的泡泡靠近嘴；相簿若把泡泡放在臉的另一側，不要再把框拉回嘴上。
        if (Math.abs(spec.bubbleX - spec.mouthX) < 0.18) {
            if (mouth.x < anchor.x) {
                x = Math.min(x, mouth.x - boxW + 18);
            } else {
                x = Math.max(x, mouth.x - 18);
            }
        }
        Rectangle box = new Rectangle(stage.x + x, stage.y + y, boxW, boxH);
        clampToStage(box, stage, 8);
        if (spec.hasFaceBox()) {
            Rectangle face = imageNormRectToPanel(
                    spec.faceX, spec.faceY, spec.faceW, spec.faceH,
                    draw, imgW, imgH, sourceTopPx);
            face.translate(stage.x, stage.y);
            nudgeOffFace(box, face, stage, topRightAvoid);
        }
        if (topRightAvoid != null && topRightAvoid.width > 0) {
            nudgeAwayFrom(box, topRightAvoid, stage);
        }
        if (spec.hasFaceBox()) {
            Rectangle face = imageNormRectToPanel(
                    spec.faceX, spec.faceY, spec.faceW, spec.faceH,
                    draw, imgW, imgH, sourceTopPx);
            face.translate(stage.x, stage.y);
            if (box.intersects(grown(face, 2)) && topRightAvoid != null && topRightAvoid.width > 0) {
                nudgeOffFace(box, face, stage, topRightAvoid);
            }
        }
        mouth.translate(stage.x, stage.y);
        return new Placement(box, mouth);
    }

    private static void clampToStage(Rectangle box, Rectangle stage, int margin) {
        int minX = stage.x + margin;
        int minY = stage.y + margin;
        int maxX = stage.x + stage.width - box.width - margin;
        int maxY = stage.y + stage.height - box.height - margin - 10;
        if (maxX < minX) {
            maxX = minX;
        }
        if (maxY < minY) {
            maxY = minY;
        }
        box.x = Math.max(minX, Math.min(box.x, maxX));
        box.y = Math.max(minY, Math.min(box.y, maxY));
    }

    private static void nudgeAwayFrom(Rectangle box, Rectangle avoid, Rectangle stage) {
        Rectangle grown = grown(avoid, 6);
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

    private static void nudgeOffFace(
            Rectangle box, Rectangle face, Rectangle stage, Rectangle topRightAvoid) {
        Rectangle grownFace = grown(face, 4);
        if (!box.intersects(grownFace)) {
            return;
        }
        int margin = 8;
        int origX = box.x;
        int origY = box.y;
        int[][] deltas = {
                {face.x - box.width - margin, origY},
                {face.x + face.width + margin, origY},
                {origX, face.y - box.height - margin},
                {face.x - box.width - margin, face.y - box.height - margin},
                {face.x + face.width + margin, face.y - box.height - margin},
                {origX, face.y + face.height + margin},
                {face.x - box.width - margin, face.y + 8},
                {face.x + face.width + margin, face.y + 8}
        };
        Rectangle best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int[] pos : deltas) {
            Rectangle candidate = new Rectangle(pos[0], pos[1], box.width, box.height);
            clampToStage(candidate, stage, margin);
            int score = scorePlacement(candidate, grownFace, stage, topRightAvoid, origX, origY);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null) {
            box.setBounds(best);
        }
    }

    private static int scorePlacement(
            Rectangle box,
            Rectangle grownFace,
            Rectangle stage,
            Rectangle topRightAvoid,
            int origX,
            int origY) {
        int score = 0;
        if (!box.intersects(grownFace)) {
            score += 1200;
        } else {
            Rectangle hit = box.intersection(grownFace);
            score -= hit.width * hit.height;
        }
        if (topRightAvoid != null && topRightAvoid.width > 0 && box.intersects(grown(topRightAvoid, 6))) {
            score -= 500;
        }
        if (box.y + box.height <= stage.y + (int) (stage.height * 0.48)) {
            score += 80;
        }
        score -= Math.abs(box.x - origX) + Math.abs(box.y - origY);
        return score;
    }

    private static Rectangle grown(Rectangle src, int padding) {
        Rectangle out = new Rectangle(src);
        out.grow(padding, padding);
        return out;
    }

    static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }
}
