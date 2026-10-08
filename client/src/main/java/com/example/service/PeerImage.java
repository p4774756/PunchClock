package com.example.service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;

/**
 * 同事訊息附圖：縮小、壓成 JPEG（不超過 {@link #MAX_JPEG_BYTES}），編成 Base64 URL。
 */
public final class PeerImage {

    /** 壓縮後 JPEG 位元組上限（Base64 後約 1.07M 字元，低於伺服器 1.2M 上限） */
    public static final int MAX_JPEG_BYTES = 800_000;
    public static final int MAX_EDGE_PIXELS = 1280;
    private static final int MIN_EDGE_PIXELS = 240;

    private PeerImage() {
    }

    public static String encodeFile(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("找不到圖檔");
        }
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) {
            throw new IOException("無法讀取圖檔（請改選 JPG / PNG / GIF）");
        }
        return encode(original);
    }

    public static String encode(Image source) throws IOException {
        if (source == null) {
            throw new IOException("沒有圖片");
        }
        BufferedImage rgb = toRgb(source);
        int edge = MAX_EDGE_PIXELS;
        while (true) {
            BufferedImage scaled = scaleToFit(rgb, edge);
            for (float quality : new float[]{0.85f, 0.7f, 0.55f}) {
                byte[] jpeg = toJpeg(scaled, quality);
                if (jpeg.length <= MAX_JPEG_BYTES) {
                    return Base64.getUrlEncoder().withoutPadding().encodeToString(jpeg);
                }
            }
            if (edge <= MIN_EDGE_PIXELS) {
                throw new IOException("圖片壓縮後仍超過大小上限");
            }
            edge = Math.max(MIN_EDGE_PIXELS, (int) (edge * 0.75));
        }
    }

    public static BufferedImage decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded.trim());
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            return null;
        }
    }

    /** 以最長邊不超過 maxEdge 等比縮小（不放大）。 */
    static BufferedImage scaleToFit(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxEdge) {
            return src;
        }
        double ratio = (double) maxEdge / longest;
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** JPEG 不支援透明；透明區塊鋪白底。 */
    private static BufferedImage toRgb(Image source) {
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

    static byte[] toJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("這個 Java 環境沒有 JPEG 編碼器");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
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
}
