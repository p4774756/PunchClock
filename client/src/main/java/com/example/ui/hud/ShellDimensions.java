package com.example.ui.hud;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/** 瑞士刀小方塊視窗尺寸（#274）。 */
public final class ShellDimensions {

    public static final int DEFAULT_WIDTH = 380;
    public static final int DEFAULT_HEIGHT = 520;
    public static final int MIN_WIDTH = 340;
    public static final int MIN_HEIGHT = 440;

    public static final int MINI_WIDTH = 300;
    public static final int MINI_HEIGHT = 56;

    public static final int TAB_BAR_HEIGHT = 60;

    private ShellDimensions() {
    }

    public static Dimension defaultSize() {
        return fitScreen(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public static Dimension miniSize() {
        return fitScreen(MINI_WIDTH, MINI_HEIGHT);
    }

    private static Dimension fitScreen(int width, int height) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int w = Math.min(width, Math.max(MIN_WIDTH, screen.width - 24));
        int h = Math.min(height, Math.max(MIN_HEIGHT, screen.height - 24));
        return new Dimension(w, h);
    }
}