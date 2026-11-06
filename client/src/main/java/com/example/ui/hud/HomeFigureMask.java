package com.example.ui.hud;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 同一張全身圖切出兩個透明遮罩，縮放和原點都跟原圖一樣。
 * 手指遮罩只留垂下來的兩隻手；腿部遮罩只留右下角斜伸的小腿、襪口和襪腳。
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
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        int y0 = Math.round(0.322f * h);
        int y1 = Math.round(0.405f * h);
        int x0 = Math.round(0.22f * w);
        int x1 = Math.round(0.62f * w);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int pixel = body.getRGB(x, y);
                if (alpha(pixel) > 100 && redBias(pixel) > 78 && green(pixel) < 185) {
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
                if (alpha(pixel) < 100 || redBias(pixel) < 68 || cream(pixel)) {
                    continue;
                }
                seeds[ny][nx] = true;
                queue.add(new int[] {nx, ny});
            }
        }
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
        boxes.sort(new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(b[1], a[1]);
            }
        });
        boolean[] keepId = new boolean[count + 1];
        int hands = 0;
        for (int[] box : boxes) {
            if (box[1] > 400 && hands < 2) {
                keepId[box[0]] = true;
                hands++;
            }
        }
        if (hands < 2) {
            return clear(w, h);
        }
        int leftMax = Math.min(boxes.get(0)[3], boxes.get(1)[3]);
        int rightMin = Math.max(boxes.get(0)[2], boxes.get(1)[2]);
        boolean[][] hand = new boolean[h][w];
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (keepId[id[y][x]]) {
                    hand[y][x] = true;
                }
            }
        }
        BufferedImage masked = clear(w, h);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (!hand[y][x]) {
                    continue;
                }
                for (int oy = -2; oy <= 2; oy++) {
                    for (int ox = -2; ox <= 2; ox++) {
                        if (ox * ox + oy * oy > 4) {
                            continue;
                        }
                        int nx = x + ox;
                        int ny = y + oy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        if (nx > leftMax + 3 && nx < rightMin - 3) {
                            continue;
                        }
                        int pixel = body.getRGB(nx, ny);
                        if (alpha(pixel) < 28) {
                            continue;
                        }
                        masked.setRGB(nx, ny, pixel);
                    }
                }
            }
        }
        fillFingerHoles(body, masked, boxes.get(0), boxes.get(1), leftMax, rightMin, y0, y1);
        return masked;
    }

    /** 把每隻手內部的破洞補上原圖像素，兩手之間的空隙不補。 */
    private static void fillFingerHoles(
            BufferedImage body,
            BufferedImage masked,
            int[] handA,
            int[] handB,
            int leftMax,
            int rightMin,
            int y0,
            int y1) {
        int w = body.getWidth();
        int h = body.getHeight();
        int radius = 6;
        boolean[][] on = new boolean[h][w];
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < w; x++) {
                on[y][x] = alpha(masked.getRGB(x, y)) > 16;
            }
        }
        boolean[][] dilated = new boolean[h][w];
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < w; x++) {
                if (!on[y][x]) {
                    continue;
                }
                for (int oy = -radius; oy <= radius; oy++) {
                    for (int ox = -radius; ox <= radius; ox++) {
                        if (ox * ox + oy * oy > radius * radius) {
                            continue;
                        }
                        int nx = x + ox;
                        int ny = y + oy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        if (nx > leftMax + 3 && nx < rightMin - 3) {
                            continue;
                        }
                        if (!inHand(nx, handA) && !inHand(nx, handB)) {
                            continue;
                        }
                        dilated[ny][nx] = true;
                    }
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (y < y0 || y >= y1 || alpha(masked.getRGB(x, y)) == 0) {
                    continue;
                }
                if (!inHand(x, handA) && !inHand(x, handB)) {
                    continue;
                }
                masked.setRGB(x, y, 0);
            }
        }
        int erode = 4;
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < w; x++) {
                if (!inHand(x, handA) && !inHand(x, handB)) {
                    continue;
                }
                int pixel = body.getRGB(x, y);
                boolean interior = dilated[y][x] && !nxBlocked(dilated, x, y, erode, w, h);
                if (alpha(pixel) > 24 && !knit(pixel) && (on[y][x] || interior)) {
                    masked.setRGB(x, y, pixel);
                }
            }
        }
        for (int pass = 0; pass < 3; pass++) {
            boolean[][] filled = new boolean[h][w];
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < w; x++) {
                    if (alpha(masked.getRGB(x, y)) > 16) {
                        filled[y][x] = true;
                    }
                }
            }
            for (int y = y0 + 1; y < y1 - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    if (filled[y][x] || (!inHand(x, handA) && !inHand(x, handB))) {
                        continue;
                    }
                    int pixel = body.getRGB(x, y);
                    if (alpha(pixel) < 40 || knit(pixel)) {
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

    /** 毛衣的亮色，不要把它補進手指遮罩。 */
    private static boolean knit(int pixel) {
        int red = (pixel >>> 16) & 255;
        int green = green(pixel);
        int blue = pixel & 255;
        return green > 175 && blue > 148 && (red - green) < 48;
    }

    private static boolean nxBlocked(boolean[][] dilated, int x, int y, int radius, int w, int h) {
        for (int oy = -radius; oy <= radius; oy++) {
            for (int ox = -radius; ox <= radius; ox++) {
                if (ox * ox + oy * oy > radius * radius) {
                    continue;
                }
                int nx = x + ox;
                int ny = y + oy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || !dilated[ny][nx]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inHand(int x, int[] hand) {
        return x >= hand[2] - 8 && x <= hand[3] + 8;
    }

    private static BufferedImage legOf(BufferedImage body) {
        int w = body.getWidth();
        int h = body.getHeight();
        int split = Math.round(0.733f * h);
        double widthSum = 0;
        int widthCount = 0;
        for (int y = split; y < Math.round(0.80f * h); y++) {
            int[] run = rightRun(body, y);
            if (run == null || run[0] < w / 2) {
                continue;
            }
            widthSum += run[1] - run[0];
            widthCount++;
        }
        int calfWidth = widthCount == 0 ? Math.round(0.12f * w) : (int) Math.round(widthSum / widthCount);
        BufferedImage masked = clear(w, h);
        int top = Math.round(0.655f * h);
        for (int y = top; y < h; y++) {
            int[] run = rightRun(body, y);
            if (run == null) {
                continue;
            }
            int left = run[0] > w / 2 ? run[0] : Math.max(0, run[1] - calfWidth);
            for (int x = left; x <= run[1]; x++) {
                int pixel = body.getRGB(x, y);
                if (alpha(pixel) > 16) {
                    masked.setRGB(x, y, pixel);
                }
            }
        }
        return masked;
    }

    private static int[] rightRun(BufferedImage body, int y) {
        int w = body.getWidth();
        int start = -1;
        int bestStart = -1;
        int bestEnd = -1;
        for (int x = 0; x <= w; x++) {
            boolean opaque = x < w && alpha(body.getRGB(x, y)) > 16;
            if (opaque && start < 0) {
                start = x;
            }
            if (!opaque && start >= 0) {
                bestStart = start;
                bestEnd = x - 1;
                start = -1;
            }
        }
        if (bestStart < 0) {
            return null;
        }
        return new int[] {bestStart, bestEnd};
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
