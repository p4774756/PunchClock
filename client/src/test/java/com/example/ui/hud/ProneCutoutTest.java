package com.example.ui.hud;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * 趴姿去背：沒有要留的白色物件。封閉的不透明近白塊，面積超過 200 就失敗。
 * 不使用內切半徑豁免。
 */
public class ProneCutoutTest {

    @Test
    public void proneHasNoEnclosedNearWhiteBlock() throws Exception {
        BufferedImage image;
        try (InputStream in = ProneCutoutTest.class.getResourceAsStream("/hud/baby/prone.png")) {
            assertNotNull(in);
            image = ImageIO.read(in);
        }
        assertNotNull(image);
        int w = image.getWidth();
        int h = image.getHeight();
        int[] px = image.getRGB(0, 0, w, h, null, 0, w);
        boolean[] near = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                near[i] = nearWhite(px[i]);
                if ((x == 0 || y == 0 || x == w - 1 || y == h - 1) && near[i]) {
                    fail("border near-white at " + x + "," + y);
                }
            }
        }
        assertEquals("armpit gap", 0, alpha(px, w, 206, 555));
        assertEquals("hair gap", 0, alpha(px, w, 489, 255));

        int[] comp = new int[w * h];
        Arrays.fill(comp, -1);
        int[] qx = new int[w * h];
        int[] qy = new int[w * h];
        int id = 0;
        int[] area = new int[8192];
        int[] minX = new int[8192];
        int[] minY = new int[8192];
        int[] maxX = new int[8192];
        int[] maxY = new int[8192];
        boolean[] touchesEdge = new boolean[8192];
        Arrays.fill(minX, w);
        Arrays.fill(minY, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!near[i] || comp[i] >= 0) {
                    continue;
                }
                int head = 0;
                int tail = 0;
                qx[tail] = x;
                qy[tail] = y;
                tail++;
                comp[i] = id;
                while (head < tail) {
                    int cx = qx[head];
                    int cy = qy[head];
                    head++;
                    area[id]++;
                    if (cx < minX[id]) {
                        minX[id] = cx;
                    }
                    if (cx > maxX[id]) {
                        maxX[id] = cx;
                    }
                    if (cy < minY[id]) {
                        minY[id] = cy;
                    }
                    if (cy > maxY[id]) {
                        maxY[id] = cy;
                    }
                    if (cx == 0 || cy == 0 || cx == w - 1 || cy == h - 1) {
                        touchesEdge[id] = true;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) {
                                continue;
                            }
                            int nx = cx + dx;
                            int ny = cy + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                                continue;
                            }
                            int ni = ny * w + nx;
                            if (!near[ni] || comp[ni] >= 0) {
                                continue;
                            }
                            comp[ni] = id;
                            qx[tail] = nx;
                            qy[tail] = ny;
                            tail++;
                        }
                    }
                }
                id++;
            }
        }
        for (int c = 0; c < id; c++) {
            if (touchesEdge[c]) {
                continue;
            }
            if (area[c] > 200) {
                fail("enclosed near-white area " + area[c]
                        + " bbox (" + minX[c] + "," + minY[c] + ")-(" + maxX[c] + "," + maxY[c] + ")");
            }
        }
    }

    private static boolean nearWhite(int pixel) {
        int alpha = (pixel >>> 24) & 255;
        int red = (pixel >> 16) & 255;
        int green = (pixel >> 8) & 255;
        int blue = pixel & 255;
        return alpha >= 240 && red >= 235 && green >= 235 && blue >= 235;
    }

    private static int alpha(int[] px, int w, int x, int y) {
        return (px[y * w + x] >>> 24) & 255;
    }
}
