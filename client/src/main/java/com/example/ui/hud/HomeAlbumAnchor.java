package com.example.ui.hud;

import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 相簿單張泡泡錨點：優先讀同檔名的 {@code .anchor.json}（或 {@code .json}），
 * 否則從圖估臉／嘴位置，再不行才用左上安全帶。
 */
final class HomeAlbumAnchor {

    private static final Gson GSON = new Gson();
    private static final int SAMPLE_WIDTH = 80;
    private static final double SEARCH_BOTTOM = 0.58;

    private HomeAlbumAnchor() {
    }

    static HomeBubbleAnchor.Spec resolve(Path imagePath, BufferedImage image) {
        HomeBubbleAnchor.Spec fromFile = readSidecar(imagePath);
        if (fromFile != null) {
            return fromFile;
        }
        HomeBubbleAnchor.Spec estimated = estimate(image);
        if (estimated != null) {
            return estimated;
        }
        return HomeBubbleAnchor.safeTopFallback();
    }

    static Path sidecarPath(Path imagePath) {
        if (imagePath == null) {
            return null;
        }
        Path anchor = withSuffix(imagePath, ".anchor.json");
        if (anchor != null && Files.isRegularFile(anchor)) {
            return anchor;
        }
        Path plain = withSuffix(imagePath, ".json");
        if (plain != null && Files.isRegularFile(plain)) {
            return plain;
        }
        return anchor;
    }

    static HomeBubbleAnchor.Spec readSidecar(Path imagePath) {
        Path sidecar = sidecarPath(imagePath);
        if (sidecar == null || !Files.isRegularFile(sidecar)) {
            return null;
        }
        try {
            String text = Files.readString(sidecar, StandardCharsets.UTF_8);
            AnchorFile file = GSON.fromJson(text, AnchorFile.class);
            return fromFile(file);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    static HomeBubbleAnchor.Spec fromFile(AnchorFile file) {
        if (file == null || file.mouthX == null || file.mouthY == null) {
            return null;
        }
        double mouthX = clamp01(file.mouthX);
        double mouthY = clamp01(file.mouthY);
        double bubbleX = file.bubbleX != null
                ? clamp01(file.bubbleX)
                : clamp01(mouthX < 0.55 ? Math.max(0.12, mouthX + 0.22) : Math.min(0.88, mouthX - 0.22));
        double bubbleY = file.bubbleY != null
                ? clamp01(file.bubbleY)
                : clamp01(Math.max(0.08, mouthY - 0.16));
        double faceX = file.faceX != null ? clamp01(file.faceX) : clamp01(mouthX - 0.18);
        double faceY = file.faceY != null ? clamp01(file.faceY) : clamp01(mouthY - 0.22);
        double faceW = file.faceW != null ? Math.max(0, file.faceW) : 0.36;
        double faceH = file.faceH != null ? Math.max(0, file.faceH) : 0.34;
        if (faceX + faceW > 1) {
            faceW = 1 - faceX;
        }
        if (faceY + faceH > 1) {
            faceH = 1 - faceY;
        }
        return HomeBubbleAnchor.spec(mouthX, mouthY, bubbleX, bubbleY, faceX, faceY, faceW, faceH);
    }

    static HomeBubbleAnchor.Spec estimate(BufferedImage image) {
        if (image == null || image.getWidth() < 8 || image.getHeight() < 8) {
            return null;
        }
        int tw = SAMPLE_WIDTH;
        int th = Math.max(8, (int) Math.round(image.getHeight() * (tw / (double) image.getWidth())));
        BufferedImage small = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        boolean[] mask = new boolean[tw * th];
        int yMax = Math.max(4, (int) Math.round(th * SEARCH_BOTTOM));
        int skin = 0;
        for (int y = 0; y < yMax; y++) {
            for (int x = 0; x < tw; x++) {
                if (isSkin(small.getRGB(x, y))) {
                    mask[y * tw + x] = true;
                    skin++;
                }
            }
        }
        int minArea = Math.max(10, (tw * yMax) / 80);
        if (skin < minArea) {
            return null;
        }
        Blob face = largestUpperBlob(mask, tw, yMax, minArea);
        if (face == null) {
            return null;
        }
        FaceBox box = refineFace(mask, tw, th, face);
        if (box == null) {
            return null;
        }
        double faceX = box.x;
        double faceY = box.y;
        double faceW = box.w;
        double faceH = box.h;
        double mouthX = box.mouthX;
        double mouthY = box.mouthY;
        double leftGap = faceX;
        double rightGap = 1 - (faceX + faceW);
        // 一律放在頭頂上空的左右角落，不要落到臉下方（胸口／手）約 y 0.4 的位置。
        double bubbleY = Math.max(0.07, Math.min(0.13, faceY * 0.45 + 0.04));
        if (bubbleY + 0.08 > faceY && faceY > 0.10) {
            bubbleY = Math.max(0.06, faceY - 0.10);
        }
        double bubbleX;
        if (leftGap >= rightGap) {
            bubbleX = Math.max(0.10, Math.min(0.22, faceX * 0.42));
        } else {
            bubbleX = Math.min(0.90, Math.max(0.78, faceX + faceW + rightGap * 0.40));
        }
        return HomeBubbleAnchor.spec(mouthX, mouthY, bubbleX, bubbleY, faceX, faceY, faceW, faceH);
    }

    private static Path withSuffix(Path imagePath, String suffix) {
        Path name = imagePath.getFileName();
        if (name == null) {
            return null;
        }
        String file = name.toString();
        int dot = file.lastIndexOf('.');
        String stem = dot <= 0 ? file : file.substring(0, dot);
        Path parent = imagePath.getParent();
        return parent == null ? Path.of(stem + suffix) : parent.resolve(stem + suffix);
    }

    private static Blob largestUpperBlob(boolean[] mask, int w, int h, int minArea) {
        boolean[] seen = new boolean[w * h];
        int[] qx = new int[w * h];
        int[] qy = new int[w * h];
        Blob best = null;
        double bestScore = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!mask[i] || seen[i]) {
                    continue;
                }
                int head = 0;
                int tail = 0;
                qx[head] = x;
                qy[head] = y;
                head++;
                seen[i] = true;
                int minX = x;
                int maxX = x;
                int minY = y;
                int maxY = y;
                int area = 0;
                while (tail < head) {
                    int cx = qx[tail];
                    int cy = qy[tail];
                    tail++;
                    area++;
                    minX = Math.min(minX, cx);
                    maxX = Math.max(maxX, cx);
                    minY = Math.min(minY, cy);
                    maxY = Math.max(maxY, cy);
                    int[][] nbs = {{cx + 1, cy}, {cx - 1, cy}, {cx, cy + 1}, {cx, cy - 1}};
                    for (int[] nb : nbs) {
                        int nx = nb[0];
                        int ny = nb[1];
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        int ni = ny * w + nx;
                        if (!mask[ni] || seen[ni]) {
                            continue;
                        }
                        seen[ni] = true;
                        qx[head] = nx;
                        qy[head] = ny;
                        head++;
                    }
                }
                if (area < minArea) {
                    continue;
                }
                double midY = (minY + maxY) * 0.5 / (double) h;
                double score = area * (1.4 - midY);
                double bw = maxX - minX + 1;
                double bh = maxY - minY + 1;
                if (bh > 0 && bw / bh > 3.2) {
                    score *= 0.4;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = new Blob(minX, minY, maxX, maxY, area);
                }
            }
        }
        return best;
    }


    /**
     * 坐姿全身照時臉、頸、胸會連成一大塊膚色；改從區塊頂端量出臉寬，
     * 再往下取約一個頭高，避免把身體當成要躲開的臉框。
     */
    private static FaceBox refineFace(boolean[] mask, int w, int h, Blob blob) {
        int top = blob.minY;
        int bandH = Math.max(5, Math.min(blob.maxY - top + 1, Math.max(6, Math.round(w * 0.12f))));
        int[] col = new int[w];
        int raw0 = w;
        int raw1 = -1;
        for (int y = top; y < Math.min(h, top + bandH); y++) {
            for (int x = blob.minX; x <= blob.maxX; x++) {
                if (mask[y * w + x]) {
                    col[x]++;
                    raw0 = Math.min(raw0, x);
                    raw1 = Math.max(raw1, x);
                }
            }
        }
        if (raw1 < raw0) {
            return null;
        }
        int thresh = Math.max(2, bandH / 5);
        int x0 = raw0;
        int x1 = raw1;
        while (x0 < x1 && col[x0] < thresh) {
            x0++;
        }
        while (x1 > x0 && col[x1] < thresh) {
            x1--;
        }
        int span = x1 - x0 + 1;
        if (span / (double) w > 0.50) {
            int best0 = x0;
            int best1 = x1;
            int bestScore = -1;
            int runStart = -1;
            int runSum = 0;
            for (int x = x0; x <= x1 + 1; x++) {
                boolean on = x <= x1 && col[x] >= thresh;
                if (on) {
                    if (runStart < 0) {
                        runStart = x;
                        runSum = 0;
                    }
                    runSum += col[x];
                } else if (runStart >= 0) {
                    int runEnd = x - 1;
                    int runW = runEnd - runStart + 1;
                    int score = runSum * 2 + runW;
                    if (score > bestScore) {
                        bestScore = score;
                        best0 = runStart;
                        best1 = runEnd;
                    }
                    runStart = -1;
                    runSum = 0;
                }
            }
            x0 = best0;
            x1 = best1;
            span = x1 - x0 + 1;
            if (span / (double) w > 0.55) {
                int win = Math.max(10, (int) Math.round(w * 0.36));
                int bestAt = x0;
                int bestSum = -1;
                int acc = 0;
                for (int x = x0; x <= x1; x++) {
                    acc += col[x];
                    if (x - x0 >= win) {
                        acc -= col[x - win];
                    }
                    if (x - x0 >= win - 1 && acc > bestSum) {
                        bestSum = acc;
                        bestAt = x - win + 1;
                    }
                }
                x0 = Math.max(0, bestAt);
                x1 = Math.min(w - 1, x0 + win - 1);
                span = x1 - x0 + 1;
            }
        }
        if (span < Math.max(4, w / 16)) {
            return null;
        }
        double faceW = Math.min(0.62, span / (double) w * 1.08);
        double faceX = clamp01((x0 / (double) w) - (faceW - span / (double) w) * 0.5);
        if (faceX + faceW > 1) {
            faceW = 1 - faceX;
        }
        double skinTop = top / (double) h;
        double head = Math.max(0.16, Math.min(0.40, faceW * 1.20));
        double hair = Math.min(skinTop, Math.max(0.07, head * 0.55));
        double faceY = clamp01(skinTop - hair);
        double faceH = Math.min(1 - faceY, head + hair);
        if (faceW < 0.06 || faceH < 0.08) {
            return null;
        }
        double mouthX = faceX + faceW * 0.50;
        double mouthY = faceY + hair + (faceH - hair) * 0.60;
        return new FaceBox(faceX, faceY, faceW, faceH, mouthX, mouthY);
    }

    static boolean isSkin(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        if (r < 80 || g < 40 || b < 18) {
            return false;
        }
        if (r < g - 8 || r < b) {
            return false;
        }
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max - min < 18) {
            return false;
        }
        if (r > 248 && g > 240 && b > 220) {
            return false;
        }
        int rb = r - b;
        int rg = r - g;
        return rb >= 15 && rg >= -12 && rg <= 90 && b < 225;
    }

    private static double clamp01(double value) {
        return HomeBubbleAnchor.clamp01(value);
    }

    static final class AnchorFile {
        Double mouthX;
        Double mouthY;
        Double bubbleX;
        Double bubbleY;
        Double faceX;
        Double faceY;
        Double faceW;
        Double faceH;
    }

    private static final class FaceBox {
        final double x;
        final double y;
        final double w;
        final double h;
        final double mouthX;
        final double mouthY;

        FaceBox(double x, double y, double w, double h, double mouthX, double mouthY) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.mouthX = mouthX;
            this.mouthY = mouthY;
        }
    }

    private static final class Blob {
        final int minX;
        final int minY;
        final int maxX;
        final int maxY;
        final int area;

        Blob(int minX, int minY, int maxX, int maxY, int area) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.area = area;
        }
    }
}
