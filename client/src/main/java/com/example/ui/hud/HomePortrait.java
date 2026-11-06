package com.example.ui.hud;

import com.example.ui.WindowChrome;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;

/**
 * 全身圖畫兩次，兩次用同一個矩形。面板後面的那層露出右框外的一小段襪尖；
 * 面板前面只畫到指尖，讓前臂下緣疊在橫邊上，軀幹和腿仍被面板蓋住。
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
        if (sock.width > 0) {
            draw(g, art.sock, sock);
        }
    }

    @Override
    public void paintInFront(Graphics2D g, JComponent canvas, Rectangle panel) {
        if (!tucked && ledge.width > 0) {
            paintLedge(g, ledge);
        }
        if (bust.width > 0) {
            drawAboveFingers(g, art.bust, bust);
        }
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
            return;
        }
        boolean maximized = WindowChrome.isMaximizedFrame(canvas);
        Rectangle controls = WindowChrome.chromeControls(canvas);
        int titleH = WindowChrome.titleBarHeight(canvas);
        Dimension bustSize = new Dimension(art.bust.getWidth(), art.bust.getHeight());
        Dimension sockSize = new Dimension(art.sock.getWidth(), art.sock.getHeight());
        Rectangle canvasBounds = new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight());
        HomeCharacterLayout.Box box = HomeCharacterLayout.place(
                canvasBounds, panel, titleH, new Rectangle(), controls, maximized, bustSize, sockSize);
        bust.setBounds(box.bust);
        tucked = box.tucked;
        if (shell.isShowing() && bust.height > 0 && !tucked) {
            Point inShell = SwingUtilities.convertPoint(canvas, 0, box.fingersY(), shell);
            shell.dropHomeBubbleBelow(inShell.y + 18);
            box = HomeCharacterLayout.place(
                    canvasBounds, panel, titleH, reminderOn(canvas), controls, maximized, bustSize, sockSize);
            bust.setBounds(box.bust);
        }
        sock.setBounds(box.sock);
        ledge.setBounds(HomeCharacterLayout.armLedge(box, panel));
    }

    private static void paintLedge(Graphics2D g, Rectangle bar) {
        Object previous = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(18, 8, 36));
        g.fillRect(bar.x, bar.y, bar.width, bar.height);
        g.setColor(new Color(132, 104, 176));
        g.fillRect(bar.x, bar.y, bar.width, 2);
        if (previous != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previous);
        }
    }

    private Rectangle reminderOn(JComponent canvas) {
        Rectangle local = shell.homeReminderBounds();
        if (local.width <= 0 || !shell.isShowing()) {
            return new Rectangle();
        }
        return SwingUtilities.convertRectangle(shell, local, canvas);
    }

    /** 只畫到指尖。指尖以下的軀幹和腿留在面板後面。 */
    private static void drawAboveFingers(Graphics2D g, java.awt.image.BufferedImage image, Rectangle dest) {
        int bottom = dest.y + Math.round(dest.height * HomeCharacterLayout.FINGERS_Y);
        int height = bottom - dest.y;
        if (height <= 0) {
            return;
        }
        Shape clip = g.getClip();
        g.clipRect(dest.x, dest.y, dest.width, height);
        draw(g, image, dest);
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
