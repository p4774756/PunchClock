package com.example.ui.hud;

import java.awt.Insets;
import java.awt.Rectangle;

/**
 * 軌道版透明前景相對紫色面板的留白。沒有前景圖時留白是 0，原畫裁在窗內。
 * <p>
 * 有前景圖時依右側舞台比例留白：上方約 14% 給髮絲、右側約 11% 給手套。
 * 前景以等比例 contain、頂右對齊放入此矩形，不放大裁切。
 */
final class OrbitalFrame {

    private OrbitalFrame() {
    }

    static int topFor(int stageHeight) {
        return clamp(Math.round(Math.max(120, stageHeight) * 0.14f), 64, 120);
    }

    static int rightFor(int stageWidth) {
        return clamp(Math.round(Math.max(160, stageWidth) * 0.11f), 48, 88);
    }

    static Insets margin(boolean maximized, int width, int height) {
        if (maximized || width < 240 || height < 240) {
            return new Insets(0, 0, 0, 0);
        }
        // width/height 多半是面板或視窗；用它們估舞台比例即可。
        int stageW = Math.max(160, Math.round(width * 0.48f));
        int stageH = Math.max(160, Math.round(height * 0.82f));
        return new Insets(topFor(stageH), 0, 0, rightFor(stageW));
    }

    /** 前景圖可畫區域。{@code stage} 是右側內容區，座標和畫布相同。 */
    static Rectangle foregroundDest(Rectangle stage, boolean maximized) {
        if (stage == null || stage.width < 40 || stage.height < 40) {
            return new Rectangle();
        }
        int top = maximized ? 0 : topFor(stage.height);
        int right = maximized ? 0 : rightFor(stage.width);
        return new Rectangle(stage.x, stage.y - top, stage.width + right, stage.height + top);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
