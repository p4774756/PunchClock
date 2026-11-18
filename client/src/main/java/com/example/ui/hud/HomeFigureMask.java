package com.example.ui.hud;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 同一張全身圖切出兩個透明遮罩，縮放和原點都跟原圖一樣。
 * 手指遮罩只留胸前比心的兩隻手（心形空隙保持透明）；腿部遮罩只留右下角伸出的襪腳段。
 */
final class HomeFigureMask {

    private static final int REF_W = 768;
    private static final int REF_H = 1024;

    /** 素材 768×1024 下左手多邊形頂點 (x,y)。 */
    private static final int[] LEFT_HAND_X = {
        334, 394, 395, 393, 388, 380, 376, 372, 367, 361, 357, 354, 349, 345, 341, 337, 335
    };
    private static final int[] LEFT_HAND_Y = {
        396, 396, 412, 426, 434, 432, 436, 447, 453, 453, 447, 451, 452, 447, 441, 428, 412
    };
    private static final int[] RIGHT_HAND_X = {
        397, 441, 441, 439, 436, 431, 426, 423, 417, 413, 410, 405, 401, 399, 397
    };
    private static final int[] RIGHT_HAND_Y = {
        396, 396, 412, 428, 440, 447, 447, 450, 450, 445, 447, 445, 437, 425, 410
    };

    final BufferedImage fingers;
    final BufferedImage extendedLeg;

    private HomeFigureMask(BufferedImage fingers, BufferedImage extendedLeg) {
        this.fingers = fingers;
        this.extendedLeg = extendedLeg;
    }

    static HomeFigureMask build(BufferedImage body) {
        if (body == null) {
            return new HomeFigureMask(null, null);
        }
        return new HomeFigureMask(fingersOf(body), legOf(body));
    }

    private static BufferedImage fingersOf(BufferedImage body) {
        int w = body.getWidth();
        int h = body.getHeight();
        float sx = w / (float) REF_W;
        float sy = h / (float) REF_H;

        float[][] poly = handPolygonMask(w, h, sx, sy);

        boolean[][] keep = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = body.getRGB(x, y);
                int a = alpha(pixel);
                if (a < 8) {
                    continue;
                }
                int r = (pixel >>> 16) & 255;
                int g = green(pixel);
                if (isHair(r, g) || isSweater(r, g)) {
                    continue;
                }
                keep[y][x] = true;
            }
        }

        keep = morphClose(keep, w, h, 2);
        keep = morphOpen(keep, w, h);
        float[][] keepSoft = blur3x3(toFloatMask(keep, w, h), w, h);

        BufferedImage masked = clear(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float polyA = poly[y][x];
                if (polyA <= 0f) {
                    continue;
                }
                int pixel = body.getRGB(x, y);
                int srcA = alpha(pixel);
                if (srcA == 0) {
                    continue;
                }
                float maskA = Math.min(polyA, keepSoft[y][x]);
                int outA = Math.round(maskA * srcA);
                if (outA <= 0) {
                    continue;
                }
                masked.setRGB(x, y, (outA << 24) | (pixel & 0xffffff));
            }
        }
        return masked;
    }

    private static float[][] handPolygonMask(int w, int h, float sx, float sy) {
        BufferedImage raster = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = raster.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fill(scaledPolygon(LEFT_HAND_X, LEFT_HAND_Y, sx, sy));
        g.fill(scaledPolygon(RIGHT_HAND_X, RIGHT_HAND_Y, sx, sy));
        g.dispose();

        float[][] poly = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = alpha(raster.getRGB(x, y));
                poly[y][x] = a / 255f;
            }
        }
        return poly;
    }

    private static Polygon scaledPolygon(int[] xs, int[] ys, float sx, float sy) {
        int n = xs.length;
        int[] px = new int[n];
        int[] py = new int[n];
        for (int i = 0; i < n; i++) {
            px[i] = Math.round(xs[i] * sx);
            py[i] = Math.round(ys[i] * sy);
        }
        return new Polygon(px, py, n);
    }

    private static boolean isHair(int r, int g) {
        if (r < 140) {
            return true;
        }
        if (r == 0) {
            return false;
        }
        return g / (float) r < 0.55f;
    }

    private static boolean isSweater(int r, int g) {
        if (r == 0) {
            return g > 0;
        }
        return g / (float) r > 0.735f;
    }

    private static float[][] toFloatMask(boolean[][] mask, int w, int h) {
        float[][] out = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y][x] = mask[y][x] ? 1f : 0f;
            }
        }
        return out;
    }

    private static boolean[][] morphClose(boolean[][] mask, int w, int h, int passes) {
        boolean[][] cur = mask;
        for (int i = 0; i < passes; i++) {
            cur = dilate3(cur, w, h);
        }
        for (int i = 0; i < passes; i++) {
            cur = erode3(cur, w, h);
        }
        return cur;
    }

    private static boolean[][] morphOpen(boolean[][] mask, int w, int h) {
        boolean[][] cur = erode3(mask, w, h);
        return dilate3(cur, w, h);
    }

    private static boolean[][] dilate3(boolean[][] mask, int w, int h) {
        boolean[][] out = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean any = false;
                for (int oy = -1; oy <= 1 && !any; oy++) {
                    int ny = y + oy;
                    if (ny < 0 || ny >= h) {
                        continue;
                    }
                    for (int ox = -1; ox <= 1; ox++) {
                        int nx = x + ox;
                        if (nx < 0 || nx >= w) {
                            continue;
                        }
                        if (mask[ny][nx]) {
                            any = true;
                            break;
                        }
                    }
                }
                out[y][x] = any;
            }
        }
        return out;
    }

    private static boolean[][] erode3(boolean[][] mask, int w, int h) {
        boolean[][] out = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask[y][x]) {
                    continue;
                }
                boolean all = true;
                for (int oy = -1; oy <= 1 && all; oy++) {
                    int ny = y + oy;
                    if (ny < 0 || ny >= h) {
                        all = false;
                        break;
                    }
                    for (int ox = -1; ox <= 1; ox++) {
                        int nx = x + ox;
                        if (nx < 0 || nx >= w || !mask[ny][nx]) {
                            all = false;
                            break;
                        }
                    }
                }
                out[y][x] = all;
            }
        }
        return out;
    }

    /** 3×3 加權平均，半徑約 0.7px 羽化。 */
    private static float[][] blur3x3(float[][] mask, int w, int h) {
        float[][] out = new float[h][w];
        int[][] k = {
            {1, 2, 1},
            {2, 4, 2},
            {1, 2, 1}
        };
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0f;
                int weight = 0;
                for (int oy = -1; oy <= 1; oy++) {
                    int ny = y + oy;
                    if (ny < 0 || ny >= h) {
                        continue;
                    }
                    for (int ox = -1; ox <= 1; ox++) {
                        int nx = x + ox;
                        if (nx < 0 || nx >= w) {
                            continue;
                        }
                        int wgt = k[oy + 1][ox + 1];
                        sum += mask[ny][nx] * wgt;
                        weight += wgt;
                    }
                }
                out[y][x] = weight == 0 ? 0f : sum / weight;
            }
        }
        return out;
    }

    private static BufferedImage legOf(BufferedImage body) {
        int w = body.getWidth();
        int h = body.getHeight();
        int split = Math.round(0.76f * h);
        double widthSum = 0;
        int widthCount = 0;
        int minLegX = Math.round(0.45f * w);
        int minLegEnd = Math.round(0.55f * w);
        for (int y = split; y < Math.round(0.84f * h); y++) {
            for (int[] run : legRuns(body, y, minLegEnd)) {
                widthSum += Math.min(run[1], w - 1) - Math.max(run[0], minLegX) + 1;
                widthCount++;
            }
        }
        int calfWidth = widthCount == 0 ? Math.round(0.12f * w) : (int) Math.round(widthSum / widthCount);
        BufferedImage masked = clear(w, h);
        int top = Math.round(0.72f * h);
        for (int y = top; y < h; y++) {
            for (int[] run : legRuns(body, y, minLegEnd)) {
                int left = run[0] >= minLegX ? run[0] : Math.max(minLegX, run[1] - calfWidth);
                for (int x = left; x <= run[1]; x++) {
                    int pixel = body.getRGB(x, y);
                    if (alpha(pixel) > 16) {
                        masked.setRGB(x, y, pixel);
                    }
                }
            }
        }
        return masked;
    }

    /** 同一行裡延伸到右下的不透明段（腳尖有時會獨立成一塊）。 */
    private static List<int[]> legRuns(BufferedImage body, int y, int minEndX) {
        int w = body.getWidth();
        List<int[]> runs = new ArrayList<int[]>();
        int start = -1;
        for (int x = 0; x <= w; x++) {
            boolean opaque = x < w && alpha(body.getRGB(x, y)) > 16;
            if (opaque && start < 0) {
                start = x;
            }
            if (!opaque && start >= 0) {
                int end = x - 1;
                if (end >= minEndX) {
                    runs.add(new int[] {start, end});
                }
                start = -1;
            }
        }
        return runs;
    }

    private static BufferedImage clear(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private static int alpha(int pixel) {
        return (pixel >>> 24) & 255;
    }

    private static int green(int pixel) {
        return (pixel >>> 8) & 255;
    }
}