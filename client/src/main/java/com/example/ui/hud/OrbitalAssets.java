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
 * 軌道觀景窗的可替換素材。缺檔時用窗內預覽，不做出跨框。
 * <p>
 * {@code /hud/orbital/foreground.png}：透明前景。建議 1400×960。
 * 圖高上方 14%（約 y=134）只放跨出頂框的粉髮；圖寬 88% 以右（約 x=1232）只放跨出右框的白手套。
 * 臉的中心約在 (42%, 30%)。面板內最上一段（標題列）留透明，避開標題、透明度滑桿和視窗按鈕。
 * <p>
 * {@code /hud/orbital/scene.png}：地球、星空、太空站，不要含跨框的頭髮和手套。建議 1600×1000。
 * 地平線約在 y=620，太空站中心約在 (1240, 220)。裁進右側舞台，對齊點 (0.72, 0.42)。
 * <p>
 * {@code /hud/orbital/bust.png}：側欄半身，四個功能頁共用。建議 640×860，透明底。
 * 髮頂約在 y=24，腰部裁切，臉放在水平中央。
 * <p>
 * {@code /hud/orbital/preview.jpg}：上述檔案還沒到時的窗內整圖。對齊點 (0.40, 0.34)，裁在舞台內。
 */
final class OrbitalAssets {

    static final String FOREGROUND_PATH = "/hud/orbital/foreground.png";
    static final String SCENE_PATH = "/hud/orbital/scene.png";
    static final String BUST_PATH = "/hud/orbital/bust.png";
    static final String PREVIEW_PATH = "/hud/orbital/preview.jpg";

    /** 只有前景時，右側舞台對準太空站。 */
    static final float SCENE_ANCHOR_X = 0.72f;
    static final float SCENE_ANCHOR_Y = 0.42f;
    /**
     * 整張原畫的對齊點。原畫是橫幅，右側舞台較高，對準偏左才能同時留下太空站和臉。
     */
    static final float PREVIEW_ANCHOR_X = 0.40f;
    static final float PREVIEW_ANCHOR_Y = 0.34f;

    private static final OrbitalAssets SHARED = load();

    private final BufferedImage foreground;
    private final BufferedImage scene;
    private final BufferedImage bust;
    private final BufferedImage preview;
    private final BufferedImage sidebar;
    private final boolean overflow;

    private OrbitalAssets(
            BufferedImage foreground,
            BufferedImage scene,
            BufferedImage bust,
            BufferedImage preview) {
        this.foreground = foreground;
        this.scene = scene;
        this.bust = bust;
        this.preview = preview;
        this.overflow = hasTransparentPixel(foreground);
        BufferedImage source = bust != null ? bust : (preview != null ? preview : foreground);
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

    /** 右側舞台要畫的圖。有透明前景時不再疊上含人物的預覽。 */
    BufferedImage stageImage() {
        if (overflow) {
            return scene;
        }
        return preview != null ? preview : scene;
    }

    float stageAnchorX() {
        return overflow && scene != null ? SCENE_ANCHOR_X : PREVIEW_ANCHOR_X;
    }

    float stageAnchorY() {
        return overflow && scene != null ? SCENE_ANCHOR_Y : PREVIEW_ANCHOR_Y;
    }

    BufferedImage sidebarImage() {
        return sidebar;
    }

    private static OrbitalAssets load() {
        return new OrbitalAssets(
                read(FOREGROUND_PATH),
                read(SCENE_PATH),
                read(BUST_PATH),
                read(PREVIEW_PATH));
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
