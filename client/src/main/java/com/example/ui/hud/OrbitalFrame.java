package com.example.ui.hud;

import java.awt.Insets;
import java.awt.Rectangle;

/**
 * 軌道版透明前景相對紫色面板的留白。沒有前景圖時留白是 0，原畫裁在窗內。
 * <p>
 * 有前景圖時依右側舞台比例留白：上方約 14% 給髮絲、右側約 11% 給手套。
 * 前景以等比例 contain、頂右對齊放入此矩形，不放大裁切。
 * <p>
 * {@link #forPanel} 與 {@link #forCanvas} 必須對得上：外層 = 面板 + 留白。
 * 對不齊時換頁 {@code relayout} 會改視窗大小，macOS 透明窗會閃一幀。
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

    /** 由紫色面板尺寸算出留白（給 {@code WindowChrome.relayout}）。 */
    static Insets forPanel(int panelWidth, int panelHeight) {
        if (panelWidth < 80 || panelHeight < 80) {
            return new Insets(0, 0, 0, 0);
        }
        int stageW = Math.max(160, Math.round(panelWidth * 0.48f));
        int stageH = Math.max(160, Math.round(panelHeight * 0.82f));
        return new Insets(topFor(stageH), 0, 0, rightFor(stageW));
    }

    /**
     * 由外層畫布／視窗尺寸算出留白（給 {@code FrameCanvas.doLayout}）。
     * 迭代對齊 {@link #forPanel}，避免面板與外層互推時換頁閃爍。
     */
    static Insets forCanvas(int width, int height, boolean maximized) {
        if (maximized || width < 240 || height < 240) {
            return new Insets(0, 0, 0, 0);
        }
        Insets guess = forPanel(width, height);
        for (int i = 0; i < 6; i++) {
            int panelW = Math.max(80, width - guess.right);
            int panelH = Math.max(80, height - guess.top);
            Insets next = forPanel(panelW, panelH);
            if (next.top == guess.top && next.right == guess.right) {
                return next;
            }
            guess = next;
        }
        return guess;
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
