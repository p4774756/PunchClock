package com.example.pet;

import com.example.ui.CandyTheme;
import com.example.ui.UiFonts;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 喵露視野外框、離屏繪製與不透明像素量測（headless 測試與 Stage 共用）。
 */
final class DesktopPetViewportRenderer {

    private static final Color SPEECH_BUBBLE_FILL = new Color(255, 228, 240);
    private static final Color SPEECH_BUBBLE_EDGE = new Color(220, 140, 170);
    private static final Color SPEECH_BUBBLE_INK = new Color(72, 36, 56);
    private static final int VISIBLE_ALPHA = 16;
    private static final int SHELL_SLACK = 3;
    private static final int CARRY_CLIP = 14;
    private static final List<CachedBody> BODY_CACHE = new ArrayList<>();
    private static volatile Rectangle ESTIMATED_MILU_SHELL;

    private DesktopPetViewportRenderer() {
    }

    static Rectangle viewForPose(
            DesktopPetSpriteLoader.Frames milu,
            Rectangle fittedShell,
            DesktopPetAnimation animation) {
        if (milu == null) {
            return new Rectangle(0, 0, DesktopPetWindow.WIDTH, DesktopPetWindow.HEIGHT);
        }
        return stableShellFor(milu);
    }

    /**
     * 與 {@link Stage#contains(int, int)} 相同：將舞台座標換成邏輯畫布後判斷是否點在角色上（headless 測試用）。
     */
    static boolean hitMiluAtStage(Rectangle view, DesktopPetAnimation animation, int stageX, int stageY) {
        if (view == null || stageX < 0 || stageY < 0 || stageX >= view.width || stageY >= view.height) {
            return false;
        }
        int canvasX = stageX + view.x;
        int canvasY = stageY + view.y;
        return miluSpriteHit(canvasX, canvasY, animation);
    }

    private static boolean miluSpriteHit(int x, int y, DesktopPetAnimation animation) {
        int size = DesktopPetSpriteLoader.DISPLAY;
        int spriteX = (DesktopPetWindow.WIDTH - size) / 2;
        int spriteY = DesktopPetWindow.BUBBLE_SLOT + animation.bob();
        int pad = 6;
        int left = spriteX - pad;
        int right = spriteX + size + pad;
        int top = Math.max(0, spriteY - 16);
        int bottom = spriteY + size + 16;
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    /** 同一動作模式內固定的視野外框（該模式所有影格的 union）。 */
    static Rectangle shellForViewport(DesktopPetSpriteLoader.Frames milu, DesktopPetAnimation animation) {
        Rectangle union = null;
        for (DesktopPetSpriteLoader.Sprite sprite : milu.viewportSprites(animation)) {
            if (sprite == null || sprite.image == null) {
                continue;
            }
            Rectangle raw = cachedBody(sprite.image, sprite.offsetX, sprite.offsetY);
            union = union == null ? raw : unionRect(union, raw);
        }
        if (union == null) {
            return clipToCanvas(shellFor(milu));
        }
        return clipToCanvas(union);
    }

    /**
     * 把喵露與影子畫進與實際視窗同尺寸的緩衝圖（headless 驗收用）。
     *
     * @param drawViewportBorder 為 true 時在視窗外框畫粉線
     */
    static BufferedImage paintMiluViewport(
            Rectangle view,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            boolean drawViewportBorder) {
        return paintMiluViewport(view, milu, animation, drawViewportBorder, 0, 0, 0, 1);
    }

    static BufferedImage paintMiluViewport(
            Rectangle view,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            boolean drawViewportBorder,
            double pivotX,
            double pivotY,
            double radians,
            double scale) {
        if (view == null || view.width < 1 || view.height < 1 || milu == null) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        BufferedImage image = new BufferedImage(view.width, view.height, BufferedImage.TYPE_INT_ARGB);
        paintMiluViewport(image, view, milu, animation, pivotX, pivotY, radians, scale);
        if (drawViewportBorder) {
            Graphics2D border = image.createGraphics();
            try {
                border.setColor(new Color(255, 64, 96, 220));
                border.drawRect(0, 0, Math.max(0, view.width - 1), Math.max(0, view.height - 1));
            } finally {
                border.dispose();
            }
        }
        return image;
    }

    static void paintMiluViewport(
            BufferedImage target,
            Rectangle view,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            double pivotX,
            double pivotY,
            double radians,
            double scale) {
        paintMiluViewport(target, view, milu, animation, pivotX, pivotY, radians, scale, 1);
    }

    static void paintMiluViewport(
            BufferedImage target,
            Rectangle view,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            double pivotX,
            double pivotY,
            double radians,
            double scale,
            double pixelScale) {
        if (target == null || view == null || view.width < 1 || view.height < 1 || milu == null) {
            return;
        }
        Graphics2D g = target.createGraphics();
        try {
            if (pixelScale > 1.0001) {
                g.scale(pixelScale, pixelScale);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            }
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, view.width, view.height);
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(-view.x, -view.y);
            paintMiluShadow(g, DesktopPetWindow.WIDTH);
            paintMiluSprite(g, milu, animation, pivotX, pivotY, radians, scale);
        } finally {
            g.dispose();
        }
    }

    /**
     * 把角色畫進已平移到邏輯畫布的 Graphics。拖曳時繞抓取點旋轉並縮小，避免貼到視窗邊。
     */
    private static void paintMiluSprite(
            Graphics2D g,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            double pivotX,
            double pivotY,
            double radians,
            double scale) {
        Graphics2D layer = g;
        boolean tilted = Math.abs(radians) > 1e-4 && scale > 0 && scale < 1.0001;
        if (tilted) {
            layer = (Graphics2D) g.create();
            layer.translate(pivotX, pivotY);
            layer.rotate(radians);
            layer.scale(scale, scale);
            layer.translate(-pivotX, -pivotY);
        }
        try {
            int x = (DesktopPetWindow.WIDTH - DesktopPetSpriteLoader.DISPLAY) / 2;
            DesktopPetSpriteLoader.Sprite frame = milu.spriteFor(animation);
            DesktopPetSpriteLoader.draw(
                    layer,
                    frame.image,
                    x,
                    DesktopPetWindow.BUBBLE_SLOT,
                    DesktopPetSpriteLoader.DISPLAY,
                    frame.offsetX,
                    frame.offsetY);
        } finally {
            if (layer != g) {
                layer.dispose();
            }
        }
    }

    static void assertOpaqueWithinViewport(BufferedImage viewport, int threshold) {
        Rectangle bounds = opaqueBounds(viewport, threshold);
        if (bounds.width < 1 || bounds.height < 1) {
            throw new AssertionError("no opaque pixels");
        }
        if (bounds.x < 0 || bounds.y < 0
                || bounds.x + bounds.width > viewport.getWidth()
                || bounds.y + bounds.height > viewport.getHeight()) {
            throw new AssertionError(
                    "opaque " + bounds + " outside " + viewport.getWidth() + "x" + viewport.getHeight());
        }
    }

    static Rectangle shellFor(DesktopPetSpriteLoader.Frames frames) {
        if (frames == null) {
            return new Rectangle(0, 0, DesktopPetWindow.WIDTH, DesktopPetWindow.HEIGHT);
        }
        DesktopPetSpriteLoader.Sprite[] sprites = frames.allSprites();
        if (sprites.length == 0 || sprites[0] == null || sprites[0].image == null) {
            return new Rectangle(0, 0, DesktopPetWindow.WIDTH, DesktopPetWindow.HEIGHT);
        }
        Rectangle idle = cachedBody(sprites[0].image, sprites[0].offsetX, sprites[0].offsetY);
        return clipToCanvas(idle);
    }

    static Rectangle estimatedMiluShell() {
        Rectangle cached = ESTIMATED_MILU_SHELL;
        if (cached != null) {
            return new Rectangle(cached);
        }
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        Rectangle shell = frames != null ? stableShellFor(frames) : new Rectangle(0, 0, DesktopPetWindow.WIDTH, DesktopPetWindow.HEIGHT);
        ESTIMATED_MILU_SHELL = new Rectangle(shell);
        return new Rectangle(shell);
    }

    static Rectangle stableShellFor(DesktopPetSpriteLoader.Frames frames) {
        if (frames == null) {
            return new Rectangle(0, 0, DesktopPetWindow.WIDTH, DesktopPetWindow.HEIGHT);
        }
        Rectangle union = null;
        for (DesktopPetSpriteLoader.Sprite sprite : frames.allSprites()) {
            if (sprite == null || sprite.image == null) {
                continue;
            }
            Rectangle raw = cachedBody(sprite.image, sprite.offsetX, sprite.offsetY);
            union = union == null ? raw : unionRect(union, raw);
        }
        if (union == null) {
            return new Rectangle(0, 0, DesktopPetWindow.WIDTH, DesktopPetWindow.HEIGHT);
        }
        Rectangle hit = clipToCanvas(union);
        int top = Math.max(0, hit.y - DesktopPetWindow.BUBBLE_SLOT);
        int extraTop = hit.y - top;
        return clipToCanvas(new Rectangle(hit.x, top, hit.width, hit.height + extraTop));
    }

    private static boolean covered(Rectangle shell, Rectangle raw) {
        if (shell == null || raw == null) {
            return false;
        }
        return raw.x >= shell.x - SHELL_SLACK
                && raw.y >= shell.y - CARRY_CLIP
                && raw.x + raw.width <= shell.x + shell.width + SHELL_SLACK
                && raw.y + raw.height <= shell.y + shell.height + SHELL_SLACK;
    }

    static boolean poseFitsShell(Rectangle shell, Rectangle raw) {
        return covered(shell, raw);
    }

    private static Rectangle unionRect(Rectangle first, Rectangle second) {
        int left = Math.min(first.x, second.x);
        int top = Math.min(first.y, second.y);
        int right = Math.max(first.x + first.width, second.x + second.width);
        int bottom = Math.max(first.y + first.height, second.y + second.height);
        return new Rectangle(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    private static Rectangle clipToCanvas(Rectangle rect) {
        Rectangle hit = rect.intersection(new Rectangle(0, 0, DesktopPetWindow.WIDTH, DesktopPetWindow.HEIGHT));
        if (hit.width < 1 || hit.height < 1) {
            return new Rectangle(0, 0, DesktopPetWindow.WIDTH, DesktopPetWindow.HEIGHT);
        }
        return hit;
    }

    static Rectangle miluBodyOnCanvas(BufferedImage image, int offsetX, int offsetY) {
        return cachedBody(image, offsetX, offsetY);
    }

    static Rectangle paintSpeechBubble(
            Graphics2D g,
            Rectangle view,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            String text) {
        if (text == null || text.isEmpty() || view == null) {
            return null;
        }
        g.setFont(UiFonts.chinesePlain(14));
        FontMetrics metrics = g.getFontMetrics();
        int innerMax = Math.max(96, view.width - 12);
        String fitted = CandyTheme.fit(text, metrics, innerMax);
        if (fitted.isEmpty()) {
            return null;
        }
        int padX = 12;
        int padY = 8;
        int textWidth = metrics.stringWidth(fitted);
        int bubbleW = textWidth + padX * 2;
        int bubbleH = metrics.getHeight() + padY;
        int bx = Math.max(0, (view.width - bubbleW) / 2);
        int headCanvasY = view.y + DesktopPetWindow.BUBBLE_SLOT;
        if (milu != null && animation != null) {
            DesktopPetSpriteLoader.Sprite sprite = milu.spriteFor(animation);
            if (sprite != null) {
                headCanvasY = DesktopPetMiluLayout.placement(sprite).headDisplayTop;
            }
        }
        int by = headCanvasY - view.y - bubbleH - 8;
        if (by < 0) {
            by = 0;
        }
        int anchorX = view.width / 2;
        g.setColor(SPEECH_BUBBLE_FILL);
        g.fillRoundRect(bx, by, bubbleW, bubbleH, 12, 12);
        g.setColor(SPEECH_BUBBLE_EDGE);
        g.drawRoundRect(bx, by, bubbleW, bubbleH, 12, 12);
        int tailX = Math.max(bx + 10, Math.min(anchorX, bx + bubbleW - 10));
        g.setColor(SPEECH_BUBBLE_FILL);
        g.fillPolygon(
                new int[] {tailX - 5, tailX + 5, tailX},
                new int[] {by + bubbleH - 1, by + bubbleH - 1, by + bubbleH + 6},
                3);
        g.setColor(SPEECH_BUBBLE_INK);
        g.drawString(fitted, bx + padX, by + metrics.getAscent() + padY / 2);
        return new Rectangle(bx + view.x, by + view.y, bubbleW, bubbleH + 6);
    }

    private static synchronized Rectangle cachedBody(BufferedImage image, int offsetX, int offsetY) {
        for (CachedBody entry : BODY_CACHE) {
            if (entry.image == image && entry.offsetX == offsetX && entry.offsetY == offsetY) {
                return new Rectangle(entry.bounds);
            }
        }
        Rectangle bounds = rasterBody(image, offsetX, offsetY);
        BODY_CACHE.add(new CachedBody(image, offsetX, offsetY, bounds));
        return new Rectangle(bounds);
    }

    private static Rectangle rasterBody(BufferedImage image, int offsetX, int offsetY) {
        BufferedImage canvas = new BufferedImage(DesktopPetWindow.WIDTH, DesktopPetWindow.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = (DesktopPetWindow.WIDTH - DesktopPetSpriteLoader.DISPLAY) / 2;
            DesktopPetSpriteLoader.draw(
                    g, image, x, DesktopPetWindow.BUBBLE_SLOT, DesktopPetSpriteLoader.DISPLAY, offsetX, offsetY);
            paintMiluShadow(g, DesktopPetWindow.WIDTH);
        } finally {
            g.dispose();
        }
        return opaqueBounds(canvas, VISIBLE_ALPHA);
    }

    static void paintMiluShadow(Graphics2D g, int canvasWidth) {
        int sw = DesktopPetSpriteLoader.DISPLAY;
        int x = (canvasWidth - sw) / 2;
        int y = DesktopPetWindow.BUBBLE_SLOT + DesktopPetSpriteLoader.DISPLAY - 14;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x + sw / 5, y, Math.max(12, sw - sw / 3), 10);
    }

    static Rectangle opaqueBounds(BufferedImage image, int threshold) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) > threshold) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < 0) {
            return new Rectangle(0, 0, image.getWidth(), image.getHeight());
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    static int visibleTopInset(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) > VISIBLE_ALPHA) {
                    return y;
                }
            }
        }
        return image.getHeight();
    }

    private static final class CachedBody {
        final BufferedImage image;
        final int offsetX;
        final int offsetY;
        final Rectangle bounds;

        CachedBody(BufferedImage image, int offsetX, int offsetY, Rectangle bounds) {
            this.image = image;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.bounds = bounds;
        }
    }
}
