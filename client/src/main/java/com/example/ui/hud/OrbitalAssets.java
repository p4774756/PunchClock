package com.example.ui.hud;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * 軌道觀景窗的可替換素材。缺前景透明圖時不做跨框。
 * <p>
 * {@code /hud/orbital/foreground.png}：透明前景。實際約 1024×702。
 * 等比例放入跨框矩形，頂對齊、右對齊：髮絲越頂框、右手套貼右框，臉留在窗內。
 * <p>
 * {@code /hud/orbital/scene.png}：地球、星空、太空站。實際約 1024×640。
 * 等比例 cover 右側舞台，對齊點偏右上的太空站。
 * <p>
 * {@code /hud/orbital/bust.png}：側欄半身。實際約 609×819，透明底。
 * 等比例完整顯示臉與髮頂，不裁切填滿。
 */
final class OrbitalAssets {

    static final String FOREGROUND_PATH = "/hud/orbital/foreground.png";
    static final String SCENE_PATH = "/hud/orbital/scene.png";
    static final String BUST_PATH = "/hud/orbital/bust.png";

    /** 場景 cover 時對準太空站。 */
    static final float SCENE_ANCHOR_X = 0.70f;
    static final float SCENE_ANCHOR_Y = 0.38f;

    private static final OrbitalAssets SHARED = load();

    private final BufferedImage foreground;
    private final BufferedImage scene;
    private final BufferedImage sidebar;
    private final boolean overflow;
    /** 前景不透明內容右緣（0–1）。手套對齊右框時用這個，而不是整張圖的右邊界。 */
    private final float contentRight;

    private OrbitalAssets(
            BufferedImage foreground,
            BufferedImage scene,
            BufferedImage bust) {
        this.foreground = foreground;
        this.scene = scene;
        this.overflow = hasTransparentPixel(foreground);
        this.contentRight = opaqueRight(foreground);
        BufferedImage source = bust != null ? bust : foreground;
        this.sidebar = bust != null ? bust : cropBust(source);
    }

    static OrbitalAssets shared() {
        return SHARED;
    }

    boolean hasOverflow() {
        return overflow;
    }

    BufferedImage foreground() {
        return overflow ? foreground : null;
    }

    /** 右側舞台場景；缺檔時為 null。 */
    BufferedImage stageImage() {
        return scene;
    }

    float stageAnchorX() {
        return SCENE_ANCHOR_X;
    }

    float stageAnchorY() {
        return SCENE_ANCHOR_Y;
    }

    BufferedImage sidebarImage() {
        return sidebar;
    }

    private static OrbitalAssets load() {
        return new OrbitalAssets(
                read(FOREGROUND_PATH),
                read(SCENE_PATH),
                read(BUST_PATH));
    }

    /** 側欄用的半身。原畫的臉在中上段。 */
    private static BufferedImage cropBust(BufferedImage image) {
        if (image == null) {
            return null;
        }
        int x = Math.round(image.getWidth() * 0.30f);
        int y = Math.round(image.getHeight() * 0.02f);
        int w = Math.round(image.getWidth() * 0.40f);
        int h = Math.round(image.getHeight() * 0.62f);
        if (x + w > image.getWidth()) {
            w = image.getWidth() - x;
        }
        if (y + h > image.getHeight()) {
            h = image.getHeight() - y;
        }
        if (w < 8 || h < 8) {
            return image;
        }
        return image.getSubimage(x, y, w, h);
    }

    private static boolean hasTransparentPixel(BufferedImage image) {
        if (image == null || image.getTransparency() == Transparency.OPAQUE) {
            return false;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int stepX = Math.max(1, w / 24);
        int stepY = Math.max(1, h / 24);
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) < 16) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 等比例 contain：整張圖都進 {@code dest}，不放大裁切。
     * 頂對齊；以不透明右緣（手套）貼齊 {@code dest} 右框。
     */
    static Rectangle fitTopRight(BufferedImage image, Rectangle dest) {
        if (image == null || dest == null || dest.width < 2 || dest.height < 2) {
            return new Rectangle();
        }
        double scale = Math.min(
                dest.width / (double) image.getWidth(),
                dest.height / (double) image.getHeight());
        int dw = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int dh = Math.max(1, (int) Math.round(image.getHeight() * scale));
        float right = SHARED != null && image == SHARED.foreground
                ? SHARED.contentRight
                : opaqueRight(image);
        // 讓不透明右緣落在 dest 右框；圖本身可略超出 dest 右側透明邊。
        int x = dest.x + dest.width - Math.round(dw * right);
        int y = dest.y;
        return new Rectangle(x, y, dw, dh);
    }

    /** 掃描前景不透明像素的最右欄，正規化成 0–1。 */
    private static float opaqueRight(BufferedImage image) {
        if (image == null) {
            return 1f;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int stepY = Math.max(1, h / 64);
        for (int x = w - 1; x >= 0; x--) {
            for (int y = 0; y < h; y += stepY) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) >= 40) {
                    return Math.min(1f, (x + 1) / (float) w);
                }
            }
        }
        return 1f;
    }

    static void drawFitted(Graphics2D g, BufferedImage image, Rectangle drawn) {
        if (g == null || image == null || drawn == null || drawn.width < 2 || drawn.height < 2) {
            return;
        }
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, drawn.x, drawn.y, drawn.width, drawn.height, null);
        if (previous != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
        }
    }

    /** 場景等比例 cover，裁在舞台內；不拉伸變形。 */
    static void drawCover(Graphics2D g, BufferedImage image, Rectangle dest, float anchorX, float anchorY) {
        if (g == null || image == null || dest == null || dest.width < 2 || dest.height < 2) {
            return;
        }
        double scale = Math.max(
                dest.width / (double) image.getWidth(),
                dest.height / (double) image.getHeight());
        int dw = Math.max(1, (int) Math.ceil(image.getWidth() * scale));
        int dh = Math.max(1, (int) Math.ceil(image.getHeight() * scale));
        int x = dest.x + Math.round(dest.width * 0.5f) - Math.round(dw * anchorX);
        int y = dest.y + Math.round(dest.height * 0.42f) - Math.round(dh * anchorY);
        if (x > dest.x) {
            x = dest.x;
        }
        if (y > dest.y) {
            y = dest.y;
        }
        if (x + dw < dest.x + dest.width) {
            x = dest.x + dest.width - dw;
        }
        if (y + dh < dest.y + dest.height) {
            y = dest.y + dest.height - dh;
        }
        Shape clip = g.getClip();
        g.clipRect(dest.x, dest.y, dest.width, dest.height);
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, x, y, dw, dh, null);
        if (previous != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
        }
        g.setClip(clip);
    }

    private static BufferedImage read(String path) {
        try (InputStream in = OrbitalAssets.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException ex) {
            return null;
        }
    }
}
