package com.example.ui;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 載入 Dock／視窗標題列用的多尺寸 App 圖示。 */
public final class AppIconImages {

    private static final int[] SIZES = {16, 32, 64, 128, 256, 512};

    private AppIconImages() {
    }

    public static List<Image> loadAll() {
        return loadAll(false);
    }

    /** @param dockIconSwimsuit {@code true} 使用泳裝角色 Dock 圖示 */
    public static List<Image> loadAll(boolean dockIconSwimsuit) {
        List<Image> images = new ArrayList<>();
        for (int size : SIZES) {
            Image img = loadSized(size, dockIconSwimsuit);
            if (img != null) {
                images.add(img);
            }
        }
        if (images.isEmpty()) {
            Image fallback = loadResource(dockIconSwimsuit ? "/dock-icon-b.png" : "/app-icon.png");
            if (fallback != null) {
                images.add(fallback);
            }
        }
        return Collections.unmodifiableList(images);
    }

    public static Image primary() {
        return primary(false);
    }

    public static Image primary(boolean dockIconSwimsuit) {
        List<Image> all = loadAll(dockIconSwimsuit);
        if (all.isEmpty()) {
            return null;
        }
        return all.get(all.size() - 1);
    }

    private static Image loadSized(int size, boolean dockIconSwimsuit) {
        if (dockIconSwimsuit) {
            Image sized = loadResource("/icons/dock-icon-b-" + size + ".png");
            if (sized != null) {
                return sized;
            }
            BufferedImage master = readBuffered("/dock-icon-b.png");
            if (master != null) {
                return scaleNearest(master, size);
            }
            return null;
        }
        Image sized = loadResource("/icons/app-icon-" + size + ".png");
        if (sized != null) {
            return sized;
        }
        return size == 512 ? loadResource("/app-icon.png") : null;
    }

    static BufferedImage scaleNearest(BufferedImage source, int size) {
        if (source == null || size <= 0) {
            return null;
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(source, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage readBuffered(String path) {
        try (InputStream in = AppIconImages.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Image loadResource(String path) {
        BufferedImage img = readBuffered(path);
        return img;
    }
}