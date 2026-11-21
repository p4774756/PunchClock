package com.example.ui;

import javax.imageio.ImageIO;
import java.awt.Image;
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
        List<Image> images = new ArrayList<>();
        for (int size : SIZES) {
            Image img = loadSized(size);
            if (img != null) {
                images.add(img);
            }
        }
        if (images.isEmpty()) {
            Image fallback = loadResource("/app-icon.png");
            if (fallback != null) {
                images.add(fallback);
            }
        }
        return Collections.unmodifiableList(images);
    }

    public static Image primary() {
        List<Image> all = loadAll();
        if (all.isEmpty()) {
            return null;
        }
        return all.get(all.size() - 1);
    }

    private static Image loadSized(int size) {
        Image sized = loadResource("/icons/app-icon-" + size + ".png");
        if (sized != null) {
            return sized;
        }
        return size == 512 ? loadResource("/app-icon.png") : null;
    }

    private static Image loadResource(String path) {
        try (InputStream in = AppIconImages.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (Exception ex) {
            return null;
        }
    }
}