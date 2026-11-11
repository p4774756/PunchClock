package com.example.ui.blackjack;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 讀 {@code /blackjack/} 底下的美術素材。有 {@code name@2x.png} 時組成多解析度圖，HiDPI 螢幕會自動挑清楚的那張。
 * 素材不在時回 null，呼叫端改用 Java2D 畫的替代圖，畫面不會壞。
 */
public final class CasinoAssets {

    public static final String BASE = "/blackjack/";

    public static final String ROOM = "bg/room.jpg";
    public static final String MODE_PLAQUE = "frame/mode-plaque.png";
    public static final String PLAYER_DEFAULT = "char/player-default.png";
    public static final String WAITER_CAT = "char/waiter-cat.png";
    public static final String LUNCH_EASEL = "deco/lunch-easel.png";
    public static final String CHIPS_LEFT = "deco/chips-left.png";
    public static final String CHIPS_RIGHT = "deco/chips-right.png";
    public static final String FELT_TILE = "table/felt-tile.png";
    public static final String TABLE = "table/table.png";
    public static final String CARD_BACK = "card/back.png";
    public static final String MEDALLION_RING = "frame/medallion-ring.png";
    public static final String BANNER = "frame/banner.png";
    public static final String NAME_PLATE = "frame/name-plate.png";
    public static final String NAME_RIBBON = "frame/name-ribbon.png";
    public static final String FOOTER_BAR = "frame/footer-bar.png";
    public static final String CHIP_PINK = "frame/chip-pink.png";
    public static final String CHIP_LAVENDER = "frame/chip-lavender.png";
    public static final String PILL_PLUM = "frame/pill-plum.png";
    public static final String PILL_RED = "frame/pill-red.png";
    public static final String PILL_PURPLE = "frame/pill-purple.png";
    public static final String PILL_GOLD = "frame/pill-gold.png";

    /** 按鈕底圖，例如 {@code button/large-green.png}。 */
    public static String button(boolean large, String tone) {
        return "button/" + (large ? "large-" : "small-") + tone + ".png";
    }

    private static final Map<String, Optional<Image>> CACHE = new HashMap<>();
    private static final Map<String, Double> OPAQUE_RADIUS = new HashMap<>();

    private CasinoAssets() {
    }

    /** 對手預設頭像依名字挑一張（peer-default-1..3）。 */
    public static String peerDefault(String name) {
        int index = 1 + Math.floorMod(name == null ? 0 : name.hashCode(), 3);
        return "char/peer-default-" + index + ".png";
    }

    public static String courtArt(String rank) {
        return "card/court-" + rank.toLowerCase(java.util.Locale.ROOT) + ".png";
    }

    public static String icon(String name) {
        return "icon/" + name + ".png";
    }

    /** 找不到時回 null。 */
    public static synchronized Image get(String path) {
        return CACHE.computeIfAbsent(path, CasinoAssets::load).orElse(null);
    }

    /** 切片繪製用：實際的點陣圖與它相對 1x 的倍率。 */
    public static final class Sprite {
        final BufferedImage image;
        final int factor;

        Sprite(BufferedImage image, int factor) {
            this.image = image;
            this.factor = factor;
        }

        /** 1x 寬度。 */
        int width() {
            return image.getWidth() / factor;
        }

        /** 1x 高度。 */
        int height() {
            return image.getHeight() / factor;
        }
    }

    /**
     * 依實際放大倍率（設計縮放 × 螢幕倍率）挑 1x 或 @2x。切片時要自己算來源座標，不能交給多解析度圖自動挑。
     */
    public static Sprite sprite(String path, double needed) {
        Image image = get(path);
        if (image == null) {
            return null;
        }
        if (image instanceof BaseMultiResolutionImage) {
            java.util.List<Image> variants = ((BaseMultiResolutionImage) image).getResolutionVariants();
            if (needed > 1.01 && variants.size() > 1) {
                return new Sprite((BufferedImage) variants.get(1), 2);
            }
            return new Sprite((BufferedImage) variants.get(0), 1);
        }
        return new Sprite((BufferedImage) image, 1);
    }

    /**
     * 圓形素材從中心往外、每個方向都還完全不透明的最小半徑（半邊長 = 1）；1x 與 @2x 取較小者。
     * 中心就透明時回 0，找不到素材時回 1。
     */
    public static synchronized double opaqueRadius(String path) {
        return OPAQUE_RADIUS.computeIfAbsent(path, p -> {
            Image image = get(p);
            if (image == null) {
                return 1.0;
            }
            java.util.List<Image> variants = image instanceof BaseMultiResolutionImage
                    ? ((BaseMultiResolutionImage) image).getResolutionVariants()
                    : java.util.Collections.singletonList(image);
            double min = 1.0;
            for (Image variant : variants) {
                if (variant instanceof BufferedImage) {
                    min = Math.min(min, opaqueRadius((BufferedImage) variant));
                }
            }
            return min;
        });
    }

    private static double opaqueRadius(BufferedImage image) {
        double cx = image.getWidth() / 2.0;
        double cy = image.getHeight() / 2.0;
        double half = Math.min(cx, cy);
        double min = half;
        for (int deg = 0; deg < 360; deg += 2) {
            double cos = Math.cos(Math.toRadians(deg));
            double sin = Math.sin(Math.toRadians(deg));
            for (double r = 0; r < min; r += 0.5) {
                if ((image.getRGB((int) (cx + cos * r), (int) (cy + sin * r)) >>> 24) < 250) {
                    min = r;
                    break;
                }
            }
        }
        return min / half;
    }

    /** 直接拿 1x 的 BufferedImage（例如當紋理用）。 */
    public static BufferedImage getBuffered(String path) {
        Image image = get(path);
        if (image instanceof BaseMultiResolutionImage) {
            image = ((BaseMultiResolutionImage) image).getResolutionVariants().get(0);
        }
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }
        if (image == null) {
            return null;
        }
        BufferedImage copy = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static Optional<Image> load(String path) {
        BufferedImage base = read(path);
        if (base == null) {
            return Optional.empty();
        }
        int dot = path.lastIndexOf('.');
        BufferedImage retina = dot < 0 ? null : read(path.substring(0, dot) + "@2x" + path.substring(dot));
        if (retina == null) {
            return Optional.of(base);
        }
        return Optional.of(new BaseMultiResolutionImage(base, retina));
    }

    private static BufferedImage read(String path) {
        try (InputStream in = CasinoAssets.class.getResourceAsStream(BASE + path)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException ex) {
            return null;
        }
    }
}
