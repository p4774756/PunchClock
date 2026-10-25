package com.example.ui.hud;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * 4 欄 2 列的像素角色。換成同規格的 PNG 就會換人，程式不必改。
 * <p>
 * 圖檔：{@code /hud/pixel-companion.png}。寬高要能被 4 和 2 整除。
 * 第 1 列：睜眼、半眨、閉眼、微笑。第 2 列：揮手低、揮手中、揮手高、說話。
 * 透明底。像素不要做抗鋸齒。
 */
public final class PixelSprite {

    public static final int COLS = 4;
    public static final int ROWS = 2;

    private final BufferedImage sheet;
    private final int frameWidth;
    private final int frameHeight;

    private PixelSprite(BufferedImage sheet) {
        this.sheet = sheet;
        this.frameWidth = sheet.getWidth() / COLS;
        this.frameHeight = sheet.getHeight() / ROWS;
    }

    public static PixelSprite load() {
        try (InputStream in = PixelSprite.class.getResourceAsStream("/hud/pixel-companion.png")) {
            if (in != null) {
                BufferedImage image = ImageIO.read(in);
                if (image != null && image.getWidth() % COLS == 0 && image.getHeight() % ROWS == 0
                        && image.getWidth() / COLS >= 8 && image.getHeight() / ROWS >= 8) {
                    return new PixelSprite(image);
                }
            }
        } catch (IOException ignored) {
            // 圖檔讀不到就用內建的預設角色。
        }
        return new PixelSprite(defaultSheet());
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public void draw(Graphics2D g, int col, int row, int x, int y, int scale) {
        int sx = col * frameWidth;
        int sy = row * frameHeight;
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(sheet, x, y, x + frameWidth * scale, y + frameHeight * scale,
                sx, sy, sx + frameWidth, sy + frameHeight, null);
        if (previous == null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
        }
    }

    /** 預設角色。眼睛、嘴巴、右手會依每一格替換。 */
    static BufferedImage defaultSheet() {
        BufferedImage sheet = new BufferedImage(32 * COLS, 48 * ROWS, BufferedImage.TYPE_INT_ARGB);
        int[][] eyes = {OPEN, HALF, SHUT, OPEN, OPEN, OPEN, OPEN, OPEN};
        int[][] mouths = {FLAT, FLAT, FLAT, SMILE, FLAT, FLAT, SMILE, TALK};
        int[] arms = {0, 0, 0, 0, 1, 2, 3, 1};
        for (int i = 0; i < COLS * ROWS; i++) {
            int[] frame = paintFrame(eyes[i], mouths[i], arms[i]);
            int ox = (i % COLS) * 32;
            int oy = (i / COLS) * 48;
            sheet.setRGB(ox, oy, 32, 48, frame, 0, 32);
        }
        return sheet;
    }

    private static final int HAIR = 0xFF4A3428;
    private static final int HAIR_HI = 0xFF8A6249;
    private static final int SKIN = 0xFFF4C7A6;
    private static final int SKIN_S = 0xFFE2A784;
    private static final int BLUSH = 0xFFF09898;
    private static final int EYE = 0xFF3A2A22;
    private static final int MOUTH = 0xFFC45B62;
    private static final int DRESS = 0xFF7E9A72;
    private static final int DRESS_S = 0xFF5C7A54;
    private static final int CREAM = 0xFFF6E4C4;
    private static final int RIBBON = 0xFFD45D5D;
    private static final int SHOE = 0xFF6B4636;
    private static final int LINE = 0xFF3A2A22;

    private static final int[] OPEN = {0};
    private static final int[] HALF = {1};
    private static final int[] SHUT = {2};
    private static final int[] FLAT = {0};
    private static final int[] SMILE = {1};
    private static final int[] TALK = {2};

    private static int[] paintFrame(int[] eye, int[] mouth, int arm) {
        int[] px = new int[32 * 48];
        // 長髮在臉後面。
        fill(px, 10, 4, 12, 6, HAIR);
        fill(px, 8, 8, 16, 20, HAIR);
        fill(px, 7, 14, 3, 16, HAIR);
        fill(px, 22, 14, 3, 16, HAIR);
        dot(px, 12, 10, HAIR_HI);
        dot(px, 13, 9, HAIR_HI);
        dot(px, 18, 12, HAIR_HI);
        // 臉。
        fill(px, 11, 8, 10, 12, SKIN);
        fill(px, 12, 7, 8, 2, SKIN);
        fill(px, 12, 19, 8, 2, SKIN_S);
        // 瀏海。
        fill(px, 11, 6, 10, 4, HAIR);
        fill(px, 12, 10, 3, 2, HAIR);
        fill(px, 17, 10, 3, 2, HAIR);
        dot(px, 15, 9, HAIR_HI);
        // 腮紅。
        dot(px, 12, 16, BLUSH);
        dot(px, 13, 16, BLUSH);
        dot(px, 18, 16, BLUSH);
        dot(px, 19, 16, BLUSH);
        eyes(px, eye[0]);
        mouth(px, mouth[0]);
        // 脖子與衣服。
        fill(px, 14, 21, 4, 3, SKIN_S);
        fill(px, 13, 23, 6, 2, CREAM);
        dot(px, 15, 23, RIBBON);
        dot(px, 16, 23, RIBBON);
        fill(px, 10, 25, 12, 12, DRESS);
        fill(px, 9, 32, 14, 6, DRESS);
        fill(px, 8, 36, 16, 4, DRESS_S);
        fill(px, 12, 27, 8, 6, CREAM);
        // 左手自然下垂。
        fill(px, 7, 26, 3, 8, CREAM);
        fill(px, 8, 33, 2, 3, SKIN);
        if (arm == 0) {
            fill(px, 22, 26, 3, 8, CREAM);
            fill(px, 22, 33, 2, 3, SKIN);
        } else if (arm == 1) {
            fill(px, 22, 20, 3, 8, CREAM);
            fill(px, 24, 18, 3, 3, SKIN);
        } else if (arm == 2) {
            fill(px, 22, 16, 3, 8, CREAM);
            fill(px, 24, 13, 3, 4, SKIN);
        } else {
            fill(px, 22, 16, 3, 12, CREAM);
            fill(px, 23, 11, 3, 6, CREAM);
            fill(px, 24, 8, 3, 4, SKIN);
        }
        fill(px, 11, 40, 4, 3, SHOE);
        fill(px, 17, 40, 4, 3, SHOE);
        outline(px);
        return px;
    }

    private static void eyes(int[] px, int kind) {
        if (kind == 2) {
            hline(px, 12, 14, 15, EYE);
            hline(px, 17, 19, 15, EYE);
            return;
        }
        if (kind == 1) {
            hline(px, 12, 13, 15, EYE);
            hline(px, 17, 18, 15, EYE);
            return;
        }
        fill(px, 12, 14, 2, 2, EYE);
        fill(px, 17, 14, 2, 2, EYE);
        dot(px, 13, 14, 0xFFFFF8EE);
        dot(px, 18, 14, 0xFFFFF8EE);
    }

    private static void mouth(int[] px, int kind) {
        if (kind == 2) {
            fill(px, 15, 18, 2, 2, MOUTH);
            return;
        }
        if (kind == 1) {
            dot(px, 14, 18, MOUTH);
            hline(px, 15, 16, 17, MOUTH);
            dot(px, 17, 18, MOUTH);
            return;
        }
        hline(px, 14, 17, 18, MOUTH);
    }

    private static void outline(int[] px) {
        int[] copy = px.clone();
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 32; x++) {
                if (copy[y * 32 + x] != 0) {
                    continue;
                }
                if (neighbor(copy, x, y)) {
                    px[y * 32 + x] = LINE;
                }
            }
        }
    }

    private static boolean neighbor(int[] px, int x, int y) {
        return solid(px, x - 1, y) || solid(px, x + 1, y) || solid(px, x, y - 1) || solid(px, x, y + 1);
    }

    private static boolean solid(int[] px, int x, int y) {
        return x >= 0 && y >= 0 && x < 32 && y < 48 && px[y * 32 + x] != 0;
    }

    private static void fill(int[] px, int x, int y, int w, int h, int color) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                dot(px, xx, yy, color);
            }
        }
    }

    private static void hline(int[] px, int x0, int x1, int y, int color) {
        for (int x = x0; x <= x1; x++) {
            dot(px, x, y, color);
        }
    }

    private static void dot(int[] px, int x, int y, int color) {
        if (x >= 0 && y >= 0 && x < 32 && y < 48) {
            px[y * 32 + x] = color;
        }
    }
}
