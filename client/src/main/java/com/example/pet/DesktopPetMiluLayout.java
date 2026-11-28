package com.example.pet;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 喵露固定畫布上的顯示尺度和 Retina 貼圖輔助；不含 Swing 視窗本體。
 */
final class DesktopPetMiluLayout {

    /** 待機、坐下之間帽子頂允許的顯示像素差。 */
    static final int HEAD_TOP_TOLERANCE = 32;

    private DesktopPetMiluLayout() {}

    /** 角色在邏輯畫布上的放置與可量測高度。 */
    static final class Placement {
        final int spriteX;
        final int spriteY;
        final int displaySize;
        final int footDisplayY;
        final int headDisplayTop;

        Placement(int spriteX, int spriteY, int displaySize, int footDisplayY, int headDisplayTop) {
            this.spriteX = spriteX;
            this.spriteY = spriteY;
            this.displaySize = displaySize;
            this.footDisplayY = footDisplayY;
            this.headDisplayTop = headDisplayTop;
        }
    }

    /** 源圖內容框經固定縮放與 offset 後，在邏輯畫布上的結果。 */
    static Placement placement(DesktopPetSpriteLoader.Sprite sprite) {
        int spriteX = (DesktopPetWindow.WIDTH - DesktopPetSpriteLoader.DISPLAY) / 2;
        int spriteY = DesktopPetWindow.BUBBLE_SLOT;
        int size = DesktopPetSpriteLoader.DISPLAY;
        double s = DesktopPetSpriteLoader.SCALE;
        int footDisplayY = spriteY + (int) Math.round(sprite.placedY() * s);
        int[] box = DesktopPetSpriteLoader.contentBounds(sprite.image);
        int headSourceY = box[1];
        int headDisplayTop =
                spriteY + (int) Math.round((sprite.offsetY + headSourceY) * s);
        return new Placement(spriteX, spriteY, size, footDisplayY, headDisplayTop);
    }

    static double deviceScale(GraphicsConfiguration configuration) {
        if (configuration == null) {
            return 1;
        }
        double scale = configuration.getDefaultTransform().getScaleX();
        if (!Double.isFinite(scale) || scale < 1) {
            return 1;
        }
        return scale;
    }

    static int bufferPixelWidth(int logicalWidth, double pixelScale) {
        return Math.max(1, (int) Math.ceil(logicalWidth * pixelScale));
    }

    static int bufferPixelHeight(int logicalHeight, double pixelScale) {
        return Math.max(1, (int) Math.ceil(logicalHeight * pixelScale));
    }

    /**
     * 把已依 {@link #deviceScale} 畫滿的裝置像素緩衝貼回 stage。
     * Graphics 已 {@code scale(deviceScale)} 時用邏輯視窗寬高；使用者座標已是裝置像素（單位矩陣）時用緩衝本體大小。
     */
    static Rectangle blitDestRect(
            Graphics2D screen, int logicalWidth, int logicalHeight, BufferedImage frame) {
        int lw = Math.max(1, logicalWidth);
        int lh = Math.max(1, logicalHeight);
        if (frame == null) {
            return new Rectangle(0, 0, lw, lh);
        }
        java.awt.geom.AffineTransform tx = screen.getTransform();
        double sx = tx.getScaleX();
        double sy = tx.getScaleY();
        boolean deviceSpace =
                Math.abs(sx - 1.0) < 0.05
                        && Math.abs(sy - 1.0) < 0.05
                        && Math.abs(tx.getShearX()) < 0.05
                        && Math.abs(tx.getShearY()) < 0.05;
        if (deviceSpace) {
            return new Rectangle(0, 0, frame.getWidth(), frame.getHeight());
        }
        return new Rectangle(0, 0, lw, lh);
    }

    /** 裝置像素緩衝在螢幕上應佔的邏輯面積（測試用）。 */
    static Rectangle logicalCoverage(
            int logicalWidth, int logicalHeight, int bufferWidth, int bufferHeight, double pixelScale) {
        if (pixelScale < 1) {
            pixelScale = 1;
        }
        int coveredW = (int) Math.round(bufferWidth / pixelScale);
        int coveredH = (int) Math.round(bufferHeight / pixelScale);
        return new Rectangle(0, 0, coveredW, coveredH);
    }

    static void drawLiveFrame(
            Graphics2D screen,
            BufferedImage frame,
            int logicalWidth,
            int logicalHeight,
            boolean miluPixelFallback) {
        if (frame == null) {
            return;
        }
        Rectangle dest = blitDestRect(screen, logicalWidth, logicalHeight, frame);
        Object interpolation =
                miluPixelFallback
                        ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR;
        screen.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        screen.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        screen.drawImage(frame, dest.x, dest.y, dest.width, dest.height, null);
    }
}