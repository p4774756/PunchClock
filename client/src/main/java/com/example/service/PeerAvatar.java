package com.example.service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;

/**
 * 同事訊息／戳一下用的大頭照：裁成方形、縮成小圖、編成 Base64 URL。
 */
public final class PeerAvatar {

    public static final String FILE_NAME = "avatar.jpg";
    public static final int PIXEL_SIZE = 96;
    public static final int DIALOG_SIZE = 48;
    public static final int PREVIEW_SIZE = 40;
    public static final int MAX_ENCODED_CHARS = 60_000;

    private PeerAvatar() {
    }

    public static Path fileBeside(Path configFile) {
        if (configFile == null) {
            return Path.of(FILE_NAME);
        }
        Path parent = configFile.getParent();
        return parent != null ? parent.resolve(FILE_NAME) : configFile.resolveSibling(FILE_NAME);
    }

    public static boolean isValidEncoded(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_ENCODED_CHARS) {
            return false;
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public static String sanitizeEncoded(String raw) {
        return isValidEncoded(raw) ? raw.trim() : "";
    }

    /**
     * 讀取使用者選的圖檔，裁成中央方形、縮到 {@link #PIXEL_SIZE}，寫成 JPEG。
     */
    public static String importImage(Path source, Path destination) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("找不到圖檔");
        }
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) {
            throw new IOException("無法讀取圖檔（請改選 JPG / PNG / GIF）");
        }
        BufferedImage square = toSquareRgb(original, PIXEL_SIZE);
        byte[] jpeg = toJpeg(square);
        if (destination != null) {
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(destination, jpeg);
        }
        return encode(jpeg);
    }

    public static String loadEncoded(Path destination) {
        if (destination == null || !Files.isRegularFile(destination)) {
            return "";
        }
        try {
            BufferedImage image = ImageIO.read(destination.toFile());
            if (image == null) {
                return "";
            }
            return encode(toJpeg(toSquareRgb(image, PIXEL_SIZE)));
        } catch (IOException ex) {
            return "";
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

    public static Icon previewIcon(String encoded, Image fallback, int size) {
        BufferedImage image = decode(encoded);
        if (image == null && fallback != null) {
            image = toSquareRgb(fallback, size);
        }
        if (image == null) {
            image = placeholder(size);
        }
        return circleIcon(image, size);
    }

    public static Icon dialogIcon(String encoded, Image fallback) {
        return previewIcon(encoded, fallback, DIALOG_SIZE);
    }

    public static BufferedImage decode(String encoded) {
        String clean = sanitizeEncoded(encoded);
        if (clean.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(clean);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            return null;
        }
    }

    static BufferedImage toSquareRgb(Image source, int size) {
        BufferedImage src = asBuffered(source);
        int w = Math.max(1, src.getWidth());
        int h = Math.max(1, src.getHeight());
        int crop = Math.min(w, h);
        int x = (w - crop) / 2;
        int y = (h - crop) / 2;
        BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = square.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.drawImage(src, 0, 0, size, size, x, y, x + crop, y + crop, null);
        } finally {
            g.dispose();
        }
        return square;
    }

    static byte[] toJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("這個 Java 環境沒有 JPEG 編碼器");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.82f);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    static String encode(byte[] jpeg) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(jpeg);
    }

    private static Icon circleIcon(BufferedImage image, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setClip(new Ellipse2D.Float(1, 1, size - 2, size - 2));
            g.drawImage(image, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return new ImageIcon(out);
    }

    private static BufferedImage placeholder(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(203, 213, 225));
            g.fillRect(0, 0, size, size);
            g.setColor(new Color(100, 116, 139));
            g.fillOval(size / 3, size / 5, size / 3, size / 3);
            g.fillOval(size / 6, size / 2, (size * 2) / 3, size);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage asBuffered(Image source) {
        if (source instanceof BufferedImage) {
            return (BufferedImage) source;
        }
        int w = Math.max(1, source.getWidth(null));
        int h = Math.max(1, source.getHeight(null));
        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }
}
