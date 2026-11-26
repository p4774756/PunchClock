package com.example.pet;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
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
   * 不論螢幕 Graphics 是否已帶縮放，把裝置像素緩衝貼滿視窗的邏輯矩形時，目標寬高（使用者座標）。
   */
  static Rectangle blitDestRect(Graphics2D screen, int logicalWidth, int logicalHeight) {
    AffineTransform tx = screen.getTransform();
    double sx = tx.getScaleX();
    double sy = tx.getScaleY();
    if (!Double.isFinite(sx) || Math.abs(sx) < 1e-6) {
      sx = 1;
    }
    if (!Double.isFinite(sy) || Math.abs(sy) < 1e-6) {
      sy = 1;
    }
    int w = (int) Math.round(logicalWidth * Math.abs(sx));
    int h = (int) Math.round(logicalHeight * Math.abs(sy));
    return new Rectangle(0, 0, Math.max(1, w), Math.max(1, h));
  }

  /**
   * 裝置像素緩衝在螢幕上應佔的邏輯面積（測試用，與 {@link #blitDestRect} 互為倒數關係）。
   */
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
    Rectangle dest = blitDestRect(screen, logicalWidth, logicalHeight);
    Object interpolation =
        miluPixelFallback
            ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            : RenderingHints.VALUE_INTERPOLATION_BILINEAR;
    screen.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
    screen.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    screen.drawImage(frame, dest.x, dest.y, dest.width, dest.height, null);
  }
}