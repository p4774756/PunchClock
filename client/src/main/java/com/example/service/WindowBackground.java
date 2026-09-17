package com.example.service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * 程式視窗背景圖：縮圖後存成 JPEG（~/.punchclock/background.jpg）。
 */
public final class WindowBackground {

    public static final String FILE_NAME = "background.jpg";
    /** 匯入時最長邊上限，避免超大圖佔用記憶體 */
    public static final int MAX_EDGE = 1920;
    public static final int PREVIEW_WIDTH = 96;
    public static final int PREVIEW_HEIGHT = 54;
    public static final int MAX_BLUR_PERCENT = 100;
    public static final int MAX_OPACITY_PERCENT = 100;
    public static final int DEFAULT_OPACITY_PERCENT = 85;
    /** 模糊百分比對應的最大半徑（像素） */
    public static final int MAX_BLUR_RADIUS = 12;

    private WindowBackground() {
    }

    public static Path fileBeside(Path configFile) {
        if (configFile == null) {
            return Path.of(FILE_NAME);
        }
        Path parent = configFile.getParent();
        return parent != null ? parent.resolve(FILE_NAME) : configFile.resolveSibling(FILE_NAME);
    }

    /**
     * 讀取使用者選的圖檔、必要時縮小，寫成 JPEG 並回傳記憶體中的圖。
     */
    public static BufferedImage importImage(Path source, Path destination) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("找不到圖檔");
        }
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) {
            throw new IOException("無法讀取圖檔（請改選 JPG / PNG / GIF）");
        }
        BufferedImage scaled = downscaleIfNeeded(original, MAX_EDGE);
        byte[] jpeg = toJpeg(scaled);
        if (destination != null) {
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(destination, jpeg);
        }
        return scaled;
    }

    public static BufferedImage load(Path destination) {
        if (destination == null || !Files.isRegularFile(destination)) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(destination.toFile());
            return image == null ? null : downscaleIfNeeded(image, MAX_EDGE);
        } catch (IOException ex) {
            return null;
        }
    }

    public static void deleteFile(Path destination) {
        if (destination == null) {
            return;
        }
        try {
            Files.deleteIfExists(destination);
        } catch (IOException ignored) {
            // 還原預設時檔案刪不掉也不阻擋
        }
    }

    public static Icon previewIcon(Image image) {
        if (image == null) {
            BufferedImage placeholder = new BufferedImage(
                    PREVIEW_WIDTH, PREVIEW_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = placeholder.createGraphics();
            g.setColor(new Color(226, 232, 240));
            g.fillRect(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT);
            g.setColor(new Color(148, 163, 184));
            g.drawRect(0, 0, PREVIEW_WIDTH - 1, PREVIEW_HEIGHT - 1);
            g.setColor(new Color(100, 116, 139));
            g.drawString("預設", PREVIEW_WIDTH / 2 - 14, PREVIEW_HEIGHT / 2 + 4);
            g.dispose();
            return new ImageIcon(placeholder);
        }
        BufferedImage thumb = fitContain(image, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        return new ImageIcon(thumb);
    }

    /**
     * Cover 縮放：填滿區域並置中裁切，不變形。
     */
    public static void paintCover(Graphics g, Image image, int width, int height) {
        if (g == null || image == null || width <= 0 || height <= 0) {
            return;
        }
        int iw = image.getWidth(null);
        int ih = image.getHeight(null);
        if (iw <= 0 || ih <= 0) {
            return;
        }
        double scale = Math.max(width / (double) iw, height / (double) ih);
        int dw = (int) Math.ceil(iw * scale);
        int dh = (int) Math.ceil(ih * scale);
        int x = (width - dw) / 2;
        int y = (height - dh) / 2;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(image, x, y, dw, dh, null);
        g2.dispose();
    }

    public static int clampBlurPercent(int percent) {
        if (percent < 0) {
            return 0;
        }
        if (percent > MAX_BLUR_PERCENT) {
            return MAX_BLUR_PERCENT;
        }
        return percent;
    }

    public static int clampOpacityPercent(int percent) {
        if (percent < 0) {
            return 0;
        }
        if (percent > MAX_OPACITY_PERCENT) {
            return MAX_OPACITY_PERCENT;
        }
        return percent;
    }

    public static int blurRadiusForPercent(int blurPercent) {
        int p = clampBlurPercent(blurPercent);
        if (p <= 0) {
            return 0;
        }
        return Math.max(1, (p * MAX_BLUR_RADIUS + 99) / 100);
    }

    /**
     * 依模糊百分比產出處理後的圖；0 回傳原圖（必要時轉 RGB）。
     */
    public static BufferedImage applyEffects(BufferedImage source, int blurPercent) {
        if (source == null) {
            return null;
        }
        BufferedImage rgb = asRgb(source);
        int radius = blurRadiusForPercent(blurPercent);
        if (radius <= 0) {
            return rgb;
        }
        return boxBlur(rgb, radius);
    }

    /**
     * 可分離盒狀模糊（水平＋垂直），半徑越大越糊。
     */
    static BufferedImage boxBlur(BufferedImage source, int radius) {
        if (source == null || radius <= 0) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        int[] src = source.getRGB(0, 0, w, h, null, 0, w);
        int[] tmp = new int[src.length];
        int[] dst = new int[src.length];
        blurAxis(src, tmp, w, h, radius, true);
        blurAxis(tmp, dst, w, h, radius, false);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, dst, 0, w);
        return out;
    }

    private static void blurAxis(int[] src, int[] dst, int w, int h, int radius, boolean horizontal) {
        int span = radius * 2 + 1;
        if (horizontal) {
            for (int y = 0; y < h; y++) {
                int row = y * w;
                long r = 0;
                long g = 0;
                long b = 0;
                for (int i = -radius; i <= radius; i++) {
                    int px = src[row + clamp(i, 0, w - 1)];
                    r += (px >> 16) & 0xff;
                    g += (px >> 8) & 0xff;
                    b += px & 0xff;
                }
                for (int x = 0; x < w; x++) {
                    dst[row + x] = (int) (((r / span) << 16) | ((g / span) << 8) | (b / span));
                    int leave = src[row + clamp(x - radius, 0, w - 1)];
                    int enter = src[row + clamp(x + radius + 1, 0, w - 1)];
                    r += ((enter >> 16) & 0xff) - ((leave >> 16) & 0xff);
                    g += ((enter >> 8) & 0xff) - ((leave >> 8) & 0xff);
                    b += (enter & 0xff) - (leave & 0xff);
                }
            }
        } else {
            for (int x = 0; x < w; x++) {
                long r = 0;
                long g = 0;
                long b = 0;
                for (int i = -radius; i <= radius; i++) {
                    int px = src[clamp(i, 0, h - 1) * w + x];
                    r += (px >> 16) & 0xff;
                    g += (px >> 8) & 0xff;
                    b += px & 0xff;
                }
                for (int y = 0; y < h; y++) {
                    dst[y * w + x] = (int) (((r / span) << 16) | ((g / span) << 8) | (b / span));
                    int leave = src[clamp(y - radius, 0, h - 1) * w + x];
                    int enter = src[clamp(y + radius + 1, 0, h - 1) * w + x];
                    r += ((enter >> 16) & 0xff) - ((leave >> 16) & 0xff);
                    g += ((enter >> 8) & 0xff) - ((leave >> 8) & 0xff);
                    b += (enter & 0xff) - (leave & 0xff);
                }
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    static BufferedImage downscaleIfNeeded(BufferedImage source, int maxEdge) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= maxEdge && h <= maxEdge) {
            return asRgb(source);
        }
        double scale = Math.min(maxEdge / (double) w, maxEdge / (double) h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.drawImage(source, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    static BufferedImage fitContain(Image source, int boxW, int boxH) {
        int iw = source.getWidth(null);
        int ih = source.getHeight(null);
        BufferedImage out = new BufferedImage(boxW, boxH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(241, 245, 249));
        g.fillRect(0, 0, boxW, boxH);
        if (iw > 0 && ih > 0) {
            double scale = Math.min(boxW / (double) iw, boxH / (double) ih);
            int dw = Math.max(1, (int) Math.round(iw * scale));
            int dh = Math.max(1, (int) Math.round(ih * scale));
            int x = (boxW - dw) / 2;
            int y = (boxH - dh) / 2;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, x, y, dw, dh, null);
        }
        g.setColor(new Color(203, 213, 225));
        g.drawRect(0, 0, boxW - 1, boxH - 1);
        g.dispose();
        return out;
    }

    static byte[] toJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("此環境不支援 JPEG 寫出");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
        }
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static BufferedImage asRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }
}
