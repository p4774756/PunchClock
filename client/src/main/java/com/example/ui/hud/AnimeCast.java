package com.example.ui.hud;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * 角色表。5 格橫排：待機、眨眼、微笑、揮手、收到訊息。
 * 換成同規格的 {@code /hud/anime/hero-sheet.png} 就會換人。
 */
public final class AnimeCast {

    public static final int FRAMES = 5;
    public static final int IDLE = 0;
    public static final int BLINK = 1;
    public static final int SMILE = 2;
    public static final int WAVE = 3;
    public static final int REACT = 4;

    private final BufferedImage sheet;
    private final int frameWidth;
    private final int frameHeight;

    private AnimeCast(BufferedImage sheet) {
        this.sheet = sheet;
        this.frameWidth = sheet.getWidth() / FRAMES;
        this.frameHeight = sheet.getHeight();
    }

    public static AnimeCast load() {
        try (InputStream in = AnimeCast.class.getResourceAsStream("/hud/anime/hero-sheet.png")) {
            if (in != null) {
                BufferedImage image = ImageIO.read(in);
                if (image != null && image.getWidth() % FRAMES == 0 && image.getHeight() > 32) {
                    return new AnimeCast(image);
                }
            }
        } catch (IOException ignored) {
            // 沒有圖就讓畫面顯示缺素材，不用形狀湊一個人物。
        }
        return null;
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public void draw(Graphics2D g, int frame, int x, int y, int width, int height) {
        int index = Math.max(0, Math.min(FRAMES - 1, frame));
        int sx = index * frameWidth;
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(sheet, x, y, x + width, y + height, sx, 0, sx + frameWidth, frameHeight, null);
        if (previous != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
        }
    }

    /**
     * 把洋紅底的單張圖去背、對齊後拼成角色表。
     * 參數：輸出 png、待機、眨眼、微笑、揮手、收到訊息。
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException("需要輸出路徑和 5 張圖");
        }
        BufferedImage[] keyed = new BufferedImage[FRAMES];
        int maxW = 0;
        int targetH = 860;
        for (int i = 0; i < FRAMES; i++) {
            BufferedImage raw = ImageIO.read(new File(args[i + 1]));
            BufferedImage cut = cutPink(raw);
            int[] bounds = bounds(cut);
            BufferedImage trimmed = cut.getSubimage(bounds[0], bounds[1], bounds[2], bounds[3]);
            int scaledW = Math.max(1, trimmed.getWidth() * targetH / trimmed.getHeight());
            BufferedImage scaled = new BufferedImage(scaledW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(trimmed, 0, 0, scaledW, targetH, null);
            g.dispose();
            keyed[i] = scaled;
            maxW = Math.max(maxW, scaledW);
        }
        BufferedImage sheet = new BufferedImage(maxW * FRAMES, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < FRAMES; i++) {
            int x = i * maxW + (maxW - keyed[i].getWidth()) / 2;
            g.drawImage(keyed[i], x, targetH - keyed[i].getHeight(), null);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(args[0]));
    }

    private static BufferedImage cutPink(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int key = src.getRGB(1, 1);
        int kr = (key >> 16) & 255;
        int kg = (key >> 8) & 255;
        int kb = key & 255;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = src.getRGB(x, y);
                int r = (pixel >> 16) & 255;
                int gch = (pixel >> 8) & 255;
                int b = pixel & 255;
                int dist = Math.abs(r - kr) + Math.abs(gch - kg) + Math.abs(b - kb);
                int alpha = 255;
                if (dist < 80 || (r > 210 && b > 120 && gch < 130 && r - gch > 70)) {
                    alpha = 0;
                } else if (dist < 150) {
                    alpha = (dist - 80) * 255 / 70;
                }
                if (alpha == 0) {
                    continue;
                }
                if (alpha < 255) {
                    r = Math.max(0, r - Math.max(0, r - gch) / 2);
                    b = Math.max(0, b - Math.max(0, b - gch) / 2);
                }
                out.setRGB(x, y, (alpha << 24) | (r << 16) | (gch << 8) | b);
            }
        }
        return out;
    }

    private static int[] bounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = 0;
        int maxY = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 255) > 16) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        int pad = 8;
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(image.getWidth() - 1, maxX + pad);
        maxY = Math.min(image.getHeight() - 1, maxY + pad);
        return new int[] {minX, minY, maxX - minX + 1, maxY - minY + 1};
    }
}
