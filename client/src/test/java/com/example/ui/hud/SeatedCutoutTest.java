package com.example.ui.hud;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 坐姿去背：外緣白底已清掉之後，馬尾與背之間、折凳腳架之間不能再留實心近白塊。
 * 座面是白色物件，用 bbox 排除。腳架與髮絲高光是細的物件白（內切半徑小），不是這次要清的封閉底。
 */
public class SeatedCutoutTest {

    @Test
    public void seatedHasNoLargeEnclosedNearWhiteBlock() throws Exception {
        BufferedImage image;
        try (InputStream in = SeatedCutoutTest.class.getResourceAsStream("/hud/baby/seated.png")) {
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
                if (seat(x, y)) {
                    continue;
                }
                int i = y * w + x;
                near[i] = nearWhite(px[i]);
                if ((x == 0 || y == 0 || x == w - 1 || y == h - 1) && near[i]) {
                    fail("border near-white at " + x + "," + y);
                }
            }
        }
        assertTrue("hair gap", alpha(px, w, 285, 486) == 0);
        assertTrue("ponytail gap", alpha(px, w, 176, 558) == 0);
        assertTrue("upper ponytail gap", alpha(px, w, 125, 336) == 0);
        assertTrue("between stool legs", alpha(px, w, 330, 780) == 0);
        assertTrue("seat stays", nearWhite(px[670 * w + 500]));
        assertTrue("seat stays", nearWhite(px[640 * w + 200]));

        int[] comp = new int[w * h];
        Arrays.fill(comp, -1);
        int[] qx = new int[w * h];
        int[] qy = new int[w * h];
        int id = 0;
        int[] area = new int[4096];
        boolean[] touchesEdge = new boolean[4096];
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
                int count = 0;
                boolean edge = false;
                while (head < tail) {
                    int cx = qx[head];
                    int cy = qy[head];
                    head++;
                    count++;
                    if (cx == 0 || cy == 0 || cx == w - 1 || cy == h - 1) {
                        edge = true;
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
                area[id] = count;
                touchesEdge[id] = edge;
                id++;
            }
        }
        int[] dist = new int[w * h];
        Arrays.fill(dist, -1);
        int qh = 0;
        int qt = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (near[i]) {
                    continue;
                }
                dist[i] = 0;
                qx[qt] = x;
                qy[qt] = y;
                qt++;
            }
        }
        int[] dx4 = {1, -1, 0, 0};
        int[] dy4 = {0, 0, 1, -1};
        while (qh < qt) {
            int cx = qx[qh];
            int cy = qy[qh];
            qh++;
            int cd = dist[cy * w + cx];
            for (int k = 0; k < 4; k++) {
                int nx = cx + dx4[k];
                int ny = cy + dy4[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                int ni = ny * w + nx;
                if (dist[ni] >= 0) {
                    continue;
                }
                dist[ni] = cd + 1;
                qx[qt] = nx;
                qy[qt] = ny;
                qt++;
            }
        }
        int[] maxRadius = new int[id];
        for (int i = 0; i < near.length; i++) {
            if (comp[i] >= 0 && dist[i] > maxRadius[comp[i]]) {
                maxRadius[comp[i]] = dist[i];
            }
        }
        for (int c = 0; c < id; c++) {
            if (touchesEdge[c]) {
                continue;
            }
            // 實心封閉底：面積 > 500 且內切半徑夠大。細腳架／高光半徑不超過 8。
            if (area[c] > 500 && maxRadius[c] >= 12) {
                fail("enclosed near-white area " + area[c] + " radius " + maxRadius[c]);
            }
        }
    }

    /** 折凳座面。座面下方、兩腳之間的背景不在這兩個矩形裡。 */
    private static boolean seat(int x, int y) {
        if (x >= 155 && x <= 340 && y >= 618 && y <= 685) {
            return true;
        }
        return x >= 460 && x <= 540 && y >= 640 && y <= 720;
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
