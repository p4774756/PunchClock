package com.example.ui.hud;

import com.example.ui.WindowChrome;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;

/**
 * 全身圖畫在「下一個任務」卡片後面，手指再畫一次蓋在卡片前面。
 * 兩次用同一個矩形。頭部若超過標題列，也從這個前景層補上。
 */
public final class HomePortrait implements WindowChrome.FramePortrait {

    private final AnimeShell shell;
    private final HomeCharacterArt art;
    private final Rectangle bust = new Rectangle();
    private final Rectangle sock = new Rectangle();
    private final Rectangle ledge = new Rectangle();
    private boolean tucked;

    public HomePortrait(AnimeShell shell) {
        this.shell = shell;
        this.art = HomeCharacterArt.load();
    }

    @Override
    public Insets overflow(int width, int height, boolean maximized) {
        boolean home = shell == null || shell.isHoldingHome();
        return HomeCharacterLayout.overflow(width, height, maximized, home && art != null);
    }

    @Override
    public void paintBehind(Graphics2D g, JComponent canvas, Rectangle panel) {
        place(canvas, panel);
    }

    @Override
    public void paintInFront(Graphics2D g, JComponent canvas, Rectangle panel) {
        if (bust.width <= 0 || art == null) {
            return;
        }
        drawHeadAndArms(g, canvas);
        drawFingers(g);
    }

    @Override
    public boolean opaqueAt(JComponent canvas, int x, int y) {
        if (art == null || shell == null || !shell.isHoldingHome() || !bust.contains(x, y)) {
            return false;
        }
        int imageX = (x - bust.x) * art.bust.getWidth() / Math.max(1, bust.width);
        int imageY = (y - bust.y) * art.bust.getHeight() / Math.max(1, bust.height);
        return art.opaqueBust(imageX, imageY);
    }

    @Override
    public void pressed() {
        if (shell != null && shell.isHoldingHome()) {
            shell.greet();
        }
    }

    private void place(JComponent canvas, Rectangle panel) {
        bust.setBounds(0, 0, 0, 0);
        sock.setBounds(0, 0, 0, 0);
        ledge.setBounds(0, 0, 0, 0);
        tucked = false;
        if (art == null || shell == null || canvas == null || panel == null || !shell.isHoldingHome()) {
            if (shell != null) {
                shell.presentHomeFigure(null, null, 0, null);
            }
            return;
        }
        boolean maximized = WindowChrome.isMaximizedFrame(canvas);
        Rectangle controls = WindowChrome.chromeControls(canvas);
        int titleH = WindowChrome.titleBarHeight(canvas);
        Dimension bodySize = new Dimension(art.bust.getWidth(), art.bust.getHeight());
        Rectangle canvasBounds = new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight());
        shell.prepareHomeLayout();
        Rectangle stage = stageOnCanvas(canvas);
        HomeCharacterLayout.Box box = HomeCharacterLayout.place(
                canvasBounds, panel, titleH, stage, controls, maximized, bodySize, bodySize);
        bust.setBounds(box.bust);
        sock.setBounds(box.sock);
        ledge.setBounds(box.card);
        tucked = box.tucked;
        if (!shell.isShowing() || bust.height <= 0) {
            return;
        }
        try {
            Point origin = SwingUtilities.convertPoint(shell, 0, 0, canvas);
            Rectangle figure = new Rectangle(
                    box.bust.x - origin.x, box.bust.y - origin.y, box.bust.width, box.bust.height);
            Rectangle card = new Rectangle(
                    box.card.x - origin.x, box.card.y - origin.y, box.card.width, box.card.height);
            shell.presentHomeFigure(figure, card, box.textRight - origin.x, art.bust);
            shell.dropHomeBubbleBelow(card.y + card.height + 8);
        } catch (IllegalArgumentException ex) {
            shell.presentHomeFigure(null, null, 0, null);
        }
    }

    private Rectangle stageOnCanvas(JComponent canvas) {
        Rectangle stage = shell.homeStageBounds();
        if (stage.width < 80 || !shell.isShowing()) {
            return null;
        }
        try {
            Point origin = SwingUtilities.convertPoint(shell, stage.x, stage.y, canvas);
            return new Rectangle(origin.x, origin.y, stage.width, stage.height);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 頭和前臂畫在最前面。只有標題列那一段讓過視窗按鈕，袖子維持原圖的外緣。 */
    private void drawHeadAndArms(Graphics2D g, JComponent canvas) {
        int bottom = bust.y + Math.round(bust.height * HomeCharacterLayout.LEDGE_Y);
        if (bottom - bust.y <= 0) {
            return;
        }
        Shape clip = g.getClip();
        Rectangle controls = WindowChrome.chromeControls(canvas);
        int split = bust.y;
        if (controls != null && controls.height > 0) {
            split = Math.max(bust.y, Math.min(bottom, controls.y + controls.height));
        }
        if (controls != null && controls.width > 0 && controls.x > bust.x && split > bust.y + 2) {
            int right = Math.min(bust.x + bust.width, controls.x - 4);
            if (right - bust.x >= 8) {
                g.clipRect(bust.x, bust.y, right - bust.x, split - bust.y);
                draw(g, art.bust, bust);
                g.setClip(clip);
            }
        } else {
            split = bust.y;
        }
        if (bottom > split) {
            g.clipRect(bust.x, split, bust.width, bottom - split);
            draw(g, art.bust, bust);
            g.setClip(clip);
        }
        int sleeveBottom = bust.y + Math.round(bust.height * 0.336f);
        int sleeveX = bust.x + Math.round(bust.width * 0.60f);
        if (sleeveBottom > bottom && bust.x + bust.width > sleeveX) {
            g.clipRect(sleeveX, bottom, bust.x + bust.width - sleeveX, sleeveBottom - bottom);
            draw(g, art.bust, bust);
            g.setClip(clip);
        }
    }

    /** 卡片頂緣以下只留兩隻手的輪廓，兩手之間和兩側的毛衣不畫。 */
    private void drawFingers(Graphics2D g) {
        if (art.fingers == null) {
            return;
        }
        int top = bust.y + Math.round(bust.height * HomeCharacterLayout.LEDGE_Y);
        int height = bust.y + bust.height - top;
        if (height <= 0) {
            return;
        }
        Shape clip = g.getClip();
        g.clipRect(bust.x, top, bust.width, height);
        draw(g, art.fingers, bust);
        g.setClip(clip);
    }

    private static void draw(Graphics2D g, java.awt.image.BufferedImage image, Rectangle dest) {
        Object previous = g.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, dest.x, dest.y, dest.width, dest.height, null);
        if (previous != null) {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, previous);
        }
    }
}
