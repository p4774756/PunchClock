package com.example.ui.hud;

import com.example.ui.WindowChrome;

import javax.swing.JComponent;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;

/**
 * 小方塊視窗角色畫在殼層內，不再溢出標題列（#274）。
 */
public final class HomePortrait implements WindowChrome.FramePortrait {

    private final AnimeShell shell;

    public HomePortrait(AnimeShell shell) {
        this.shell = shell;
    }

    public void setTheme(CharacterTheme theme) {
    }

    @Override
    public Insets overflow(int width, int height, boolean maximized) {
        return new Insets(0, 0, 0, 0);
    }

    @Override
    public Insets panelOverflow(int panelWidth, int panelHeight) {
        return null;
    }

    @Override
    public void paintBehind(Graphics2D g, JComponent canvas, Rectangle panel) {
        if (shell != null) {
            shell.prepareHomeLayout();
        }
    }

    @Override
    public void paintInFront(Graphics2D g, JComponent canvas, Rectangle panel) {
    }

    @Override
    public boolean opaqueAt(JComponent canvas, int x, int y) {
        return false;
    }

    @Override
    public void pressed() {
        if (shell != null && shell.isHoldingHome() && !shell.isMiniMode()) {
            shell.greet();
        }
    }
}