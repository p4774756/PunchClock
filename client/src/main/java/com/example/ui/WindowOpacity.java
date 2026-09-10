package com.example.ui;

import com.example.service.ConfigPersistenceService;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Window;

/**
 * 桌面端視窗透明度。Java 只能對未加系統標題列的視窗呼叫 {@link Window#setOpacity(float)}，
 * 實際套用由 {@link WindowChrome} 搭配自訂標題列完成。
 */
public final class WindowOpacity {

    /** 最透明時仍保留的不透明度，避免滑桿與按鈕點不到。 */
    public static final float MIN_OPACITY = 0.40f;
    public static final float MAX_OPACITY = 1.00f;
    public static final float DEFAULT_OPACITY = 1.00f;

    public static final int MIN_TRANSPARENCY_PERCENT = 0;
    public static final int MAX_TRANSPARENCY_PERCENT =
            ConfigPersistenceService.MAX_WINDOW_TRANSPARENCY_PERCENT;

    private WindowOpacity() {
    }

    public static int clampTransparencyPercent(int percent) {
        if (percent < MIN_TRANSPARENCY_PERCENT) {
            return MIN_TRANSPARENCY_PERCENT;
        }
        if (percent > MAX_TRANSPARENCY_PERCENT) {
            return MAX_TRANSPARENCY_PERCENT;
        }
        return percent;
    }

    public static float toOpacity(int transparencyPercent) {
        int percent = clampTransparencyPercent(transparencyPercent);
        return clampOpacity(1.0f - (percent / 100.0f));
    }

    public static int toTransparencyPercent(float opacity) {
        float clamped = clampOpacity(opacity);
        int percent = Math.round((1.0f - clamped) * 100.0f);
        return clampTransparencyPercent(percent);
    }

    public static float clampOpacity(float opacity) {
        if (Float.isNaN(opacity) || Float.isInfinite(opacity)) {
            return DEFAULT_OPACITY;
        }
        if (opacity <= 0f) {
            return DEFAULT_OPACITY;
        }
        return Math.max(MIN_OPACITY, Math.min(MAX_OPACITY, opacity));
    }

    public static String formatPercentLabel(int transparencyPercent) {
        int percent = clampTransparencyPercent(transparencyPercent);
        if (percent == 0) {
            return "不透明";
        }
        return percent + "%";
    }

    public static boolean isTranslucencySupported() {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        try {
            GraphicsDevice device = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice();
            return device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 套用視窗不透明度。失敗時回傳 {@code false}（平台不支援、全螢幕、或尚未解除系統標題列）。
     */
    public static boolean apply(Window window, float opacity) {
        if (window == null) {
            return false;
        }
        float clamped = clampOpacity(opacity);
        try {
            window.setOpacity(clamped);
            return Math.abs(window.getOpacity() - clamped) < 0.001f;
        } catch (IllegalArgumentException | IllegalComponentStateException | UnsupportedOperationException ex) {
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public static boolean applyTransparencyPercent(Window window, int transparencyPercent) {
        return apply(window, toOpacity(transparencyPercent));
    }
}
