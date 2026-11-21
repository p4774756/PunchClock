package com.example.pet;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/** 桌寵右鍵選單的粉彩小圖示（1x／2x）。 */
final class PetMenuIcons {

    enum Kind {
        CONTROL("control"),
        FEED("feed"),
        PAT("pat"),
        PLAY("play"),
        DEBUG("debug");

        final String base;

        Kind(String base) {
            this.base = base;
        }
    }

    private static final Map<Kind, BufferedImage> ICONS_1X = new EnumMap<>(Kind.class);
    private static final Map<Kind, BufferedImage> ICONS_2X = new EnumMap<>(Kind.class);
    private static final int DISPLAY = 22;
    private static volatile boolean loaded;

    private PetMenuIcons() {
    }

    static int displaySize() {
        ensureLoaded();
        return DISPLAY;
    }

    static Image iconFor(Kind kind, double scale) {
        ensureLoaded();
        if (kind == null) {
            return null;
        }
        BufferedImage img = scale >= 1.5 ? ICONS_2X.get(kind) : ICONS_1X.get(kind);
        return img;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (PetMenuIcons.class) {
            if (loaded) {
                return;
            }
            for (Kind kind : Kind.values()) {
                ICONS_1X.put(kind, load(kind.base + "-1x.png"));
                ICONS_2X.put(kind, load(kind.base + "-2x.png"));
            }
            loaded = true;
        }
    }

    private static BufferedImage load(String file) {
        try (var in = PetMenuIcons.class.getResourceAsStream("/pet/menu/" + file)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (Exception ex) {
            return null;
        }
    }
}