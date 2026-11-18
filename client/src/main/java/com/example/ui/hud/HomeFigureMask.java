package com.example.ui.hud;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 同一張全身圖切出兩個透明遮罩，縮放和原點都跟原圖一樣。
 * 手指遮罩只留胸前比心的兩隻手（心形空隙保持透明）；腿部遮罩只留右下角伸出的襪腳段。
 */
final class HomeFigureMask {

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
        boolean[][] seeds = new boolean[h][w];
        int y0 = Math.round(0.36f * h);
        int y1 = Math.round(0.44f * h);
        int x0 = Math.round(0.39f * w);
        int x1 = Math.round(0.65f * w);
        int gap0 = Math.round(0.47f * w);
        int gap1 = Math.round(0.52f * w);
        floodFingers(body, seeds, x0, gap0, y0, y1);
        floodFingers(body, seeds, gap1, x1, y0, y1);
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        int[][] id = new int[h][w];
        List<int[]> boxes = new ArrayList<int[]>();
        int count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (!seeds[y][x] || id[y][x] != 0) {
                    continue;
                }
                count++;
                int minX = x;
                int maxX = x;
                int pixels = 0;
                ArrayDeque<int[]> fill = new ArrayDeque<int[]>();
                fill.add(new int[] {x, y});
                id[y][x] = count;
                while (!fill.isEmpty()) {
                    int[] cell = fill.removeFirst();
                    pixels++;
                    minX = Math.min(minX, cell[0]);
                    maxX = Math.max(maxX, cell[0]);
                    for (int k = 0; k < 4; k++) {
                        int nx = cell[0] + dx[k];
                        int ny = cell[1] + dy[k];
                        if (nx < x0 || ny < y0 || nx >= x1 || ny >= y1 || !seeds[ny][nx] || id[ny][nx] != 0) {
                            continue;
                        }
                        id[ny][nx] = count;
                        fill.add(new int[] {nx, ny});
                    }
                }
                boxes.add(new int[] {count, pixels, minX, maxX});
            }
        }
        int mid = (gap0 + gap1) / 2;
        int[] handLeft = null;
        int[] handRight = null;
        for (int[] box : boxes) {
            if (box[1] <= 400) {
                continue;
            }
            if (box[3] <= mid && (handLeft == null || box[1] > handLeft[1])) {
                handLeft = box;
            }
            if (box[2] >= mid && (handRight == null || box[1] > handRight[1])) {
                handRight = box;
            }
        }
        if (handLeft == null || handRight == null) {
            return clear(w, h);
        }
        boolean[] keepId = new boolean[count + 1];
        keepId[handLeft[0]] = true;
        keepId[handRight[0]] = true;
        boolean[][] hand = new boolean[h][w];
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (keepId[id[y][x]]) {
                    hand[y][x] = true;
                }
            }
        }
        int paintY1 = Math.min(h, Math.round(0.46f * h));
        BufferedImage masked = clear(w, h);
        stampSkin(body, masked, hand, gap0, gap1, y0, paintY1);
        growSkin(body, masked, hand, id, keepId, gap0, gap1, y0, paintY1, 2);
        fillSkinHoles(body, masked, id, handLeft[0], handRight[0], gap0, gap1, y0, paintY1);
        featherFingerEdges(masked, y0, paintY1);
        return masked;
    }

    /** 只從手部種子複製膚色像素；心形空隙整段略過。 */
    private static void stampSkin(
            BufferedImage body,
            BufferedImage masked,
            boolean[][] hand,
            int gap0,
            int gap1,
            int y0,
            int y1) {
        int w = body.getWidth();
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < w; x++) {
                if (!hand[y][x] || inHeartGap(x, gap0, gap1)) {
                    continue;
                }
                int pixel = body.getRGB(x, y);
                if (!skin(pixel)) {
                    continue;
                }
                masked.setRGB(x, y, pixel);
            }
        }
    }

    /**
     * 沿手指外緣再長 1～2px，仍限同一隻手的連通塊；不跨心形空隙。
     */
    private static void growSkin(
            BufferedImage body,
            BufferedImage masked,
            boolean[][] hand,
            int[][] id,
            boolean[] keepId,
            int gap0,
            int gap1,
            int y0,
            int y1,
            int steps) {
        int w = body.getWidth();
        int h = body.getHeight();
        boolean[][] grown = new boolean[h][w];
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < w; x++) {
                grown[y][x] = alpha(masked.getRGB(x, y)) > 16;
            }
        }
        int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
        int[] dy = {0, 0, 1, -1, 1, -1, 1, -1};
        for (int step = 0; step < steps; step++) {
            boolean[][] next = new boolean[h][w];
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < w; x++) {
                    if (!grown[y][x]) {
                        continue;
                    }
                    next[y][x] = true;
                    int component = id[y][x];
                    if (component <= 0 || !keepId[component]) {
                        continue;
                    }
                    for (int k = 0; k < dx.length; k++) {
                        int nx = x + dx[k];
                        int ny = y + dy[k];
                        if (nx < 0 || ny < y0 || nx >= w || ny >= y1 || inHeartGap(nx, gap0, gap1)) {
                            continue;
                        }
                        if (id[ny][nx] != component) {
                            continue;
                        }
                        int pixel = body.getRGB(nx, ny);
                        if (!skin(pixel)) {
                            continue;
                        }
                        next[ny][nx] = true;
                        masked.setRGB(nx, ny, pixel);
                    }
                }
            }
            grown = next;
        }
    }

    /** 填手心小破洞，範圍限在該手的連通塊內。 */
    private static void fillSkinHoles(
            BufferedImage body,
            BufferedImage masked,
            int[][] id,
            int leftId,
            int rightId,
            int gap0,
            int gap1,
            int y0,
            int y1) {
        int w = body.getWidth();
        int h = body.getHeight();
        for (int pass = 0; pass < 2; pass++) {
            boolean[][] filled = new boolean[h][w];
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < w; x++) {
                    filled[y][x] = alpha(masked.getRGB(x, y)) > 16;
                }
            }
            for (int y = y0 + 1; y < y1 - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    if (filled[y][x] || inHeartGap(x, gap0, gap1)) {
                        continue;
                    }
                    int component = id[y][x];
                    if (component != leftId && component != rightId) {
                        continue;
                    }
                    int pixel = body.getRGB(x, y);
                    if (!skin(pixel)) {
                        continue;
                    }
                    int around = 0;
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if ((ox != 0 || oy != 0) && filled[y + oy][x + ox]) {
                                around++;
                            }
                        }
                    }
                    if (around >= 5) {
                        masked.setRGB(x, y, pixel);
                    }
                }
            }
        }
    }

    private static boolean inHeartGap(int x, int gap0, int gap1) {
        return x >= gap0 && x < gap1;
    }

    /** 指緣 1～2px 漸進 alpha，避免硬階梯。 */
    private static void featherFingerEdges(BufferedImage masked, int y0, int y1) {
        int w = masked.getWidth();
        int h = masked.getHeight();
        int x0 = Math.max(0, Math.round(0.35f * w));
        int x1 = Math.min(w, Math.round(0.71f * w));
        for (int pass = 0; pass < 2; pass++) {
            int[][] planned = new int[h][w];
            for (int y = Math.max(1, y0); y < Math.min(h - 1, y1); y++) {
                for (int x = Math.max(1, x0); x < Math.min(w - 1, x1); x++) {
                    int pixel = masked.getRGB(x, y);
                    int a = alpha(pixel);
                    if (a < 20) {
                        continue;
                    }
                    int transparent = 0;
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if (alpha(masked.getRGB(x + ox, y + oy)) < 20) {
                                transparent++;
                            }
                        }
                    }
                    if (transparent == 0) {
                        planned[y][x] = pixel;
                        continue;
                    }
                    int scale = Math.max(64, 255 - transparent * 42);
                    int newA = a * scale / 255;
                    planned[y][x] = (newA << 24) | (pixel & 0xffffff);
                }
            }
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    if (planned[y][x] != 0) {
                        masked.setRGB(x, y, planned[y][x]);
                    }
                }
            }
        }
    }

    /** 比心時左右手可能仍連成一塊，分開從兩側泛洪，心形空隙才不會被補滿。 */
    private static void floodFingers(
            BufferedImage body, boolean[][] seeds, int x0, int x1, int y0, int y1) {
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (seeds[y][x]) {
                    continue;
                }
                int pixel = body.getRGB(x, y);
                if (alpha(pixel) > 100 && redBias(pixel) > 36 && green(pixel) < 185) {
                    seeds[y][x] = true;
                    queue.add(new int[] {x, y});
                }
            }
        }
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            for (int k = 0; k < 4; k++) {
                int nx = cell[0] + dx[k];
                int ny = cell[1] + dy[k];
                if (nx < x0 || ny < y0 || nx >= x1 || ny >= y1 || seeds[ny][nx]) {
                    continue;
                }
                int pixel = body.getRGB(nx, ny);
                if (alpha(pixel) < 80 || redBias(pixel) < 10 || cream(pixel) || knit(pixel)) {
                    continue;
                }
                seeds[ny][nx] = true;
                queue.add(new int[] {nx, ny});
            }
        }
    }

    private static boolean skin(int pixel) {
        if (alpha(pixel) < 32) {
            return false;
        }
        if (knit(pixel) || hair(pixel)) {
            return false;
        }
        if (cream(pixel)) {
            return true;
        }
        return redBias(pixel) > 8 && green(pixel) < 210;
    }

    private static boolean hair(int pixel) {
        int red = (pixel >>> 16) & 255;
        int lum = (red + green(pixel) + (pixel & 255)) / 3;
        if (lum < 40) {
            return redBias(pixel) < 10;
        }
        return lum < 95 && redBias(pixel) < 5;
    }

    /** 毛衣的亮色，不要把它補進手指遮罩。 */
    private static boolean knit(int pixel) {
        int red = (pixel >>> 16) & 255;
        int green = green(pixel);
        int blue = pixel & 255;
        return green > 175 && blue > 148 && (red - green) < 48;
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

    private static int redBias(int pixel) {
        return ((pixel >>> 16) & 255) - (pixel & 255);
    }

    private static boolean cream(int pixel) {
        int green = green(pixel);
        int blue = pixel & 255;
        return green > 185 && blue > 150;
    }
}
