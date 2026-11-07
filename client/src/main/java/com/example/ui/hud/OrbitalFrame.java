package com.example.ui.hud;

import java.awt.Insets;
import java.awt.Rectangle;

/**
 * 軌道版透明前景相對紫色面板的留白。沒有前景圖時留白是 0，原畫裁在窗內。
 * <p>
 * 有前景圖時，上方 120px 給頭髮、右側 84px 給手套。這兩段對應前景圖的上 14% 與右 12%
 * （以右側舞台約 720×640 來對）。標題列那一橫帶不畫，避開標題、滑桿和視窗按鈕。
 */
final class OrbitalFrame {

    static final int TOP = 120;
    static final int RIGHT = 84;

    private OrbitalFrame() {
    }

    static Insets margin(boolean maximized, int width, int height) {
        if (maximized || width < 240 || height < 240) {
            return new Insets(0, 0, 0, 0);
        }
        return new Insets(TOP, 0, 0, RIGHT);
    }

    /** 前景圖在畫布上的矩形。{@code stage} 是右側內容區，座標和畫布相同。 */
    static Rectangle foregroundDest(Rectangle stage, boolean maximized) {
        if (stage == null || stage.width < 40 || stage.height < 40) {
            return new Rectangle();
        }
        int top = maximized ? 0 : TOP;
        int right = maximized ? 0 : RIGHT;
        return new Rectangle(stage.x, stage.y - top, stage.width + right, stage.height + top);
    }
}
