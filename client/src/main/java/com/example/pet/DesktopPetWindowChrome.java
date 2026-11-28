package com.example.pet;

import javax.swing.JComponent;
import javax.swing.JWindow;

import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

/**
 * 桌面寵物視窗 type 與透明選單視窗設定（macOS POPUP 等）。
 */
final class DesktopPetWindowChrome {

    private DesktopPetWindowChrome() {
    }

    static boolean keepsAppInBackground() {
        String os = System.getProperty("os.name", "");
        return os.startsWith("Mac") || os.startsWith("Darwin");
    }

    static Window.Type backgroundWindowType() {
        return keepsAppInBackground() ? Window.Type.POPUP : Window.Type.UTILITY;
    }

    /** 操控模式要可成為 key window；macOS 上 POPUP 不行，暫改 NORMAL。 */
    static Window.Type controlModeWindowType() {
        return keepsAppInBackground() ? Window.Type.NORMAL : Window.Type.UTILITY;
    }

    /** 進入／離開操控模式時應套用的視窗 type 與焦點旗標（headless 單測用）。 */
    static ControlModeChrome controlModeChrome(boolean controlActive) {
        return new ControlModeChrome(
                controlActive ? controlModeWindowType() : backgroundWindowType(),
                controlActive,
                controlActive && keepsAppInBackground(),
                controlActive);
    }

    static final class ControlModeChrome {
        final Window.Type windowType;
        final boolean focusableWindowState;
        final boolean autoRequestFocus;
        final boolean stageFocusable;

        ControlModeChrome(Window.Type windowType, boolean focusableWindowState, boolean autoRequestFocus,
                boolean stageFocusable) {
            this.windowType = windowType;
            this.focusableWindowState = focusableWindowState;
            this.autoRequestFocus = autoRequestFocus;
            this.stageFocusable = stageFocusable;
        }
    }

    /**
     * 選單是獨立 JWindow。只把 content pane 塗透明不夠，圓角與陰影外的像素仍會露出視窗方底。
     * 與萌寵本體相同：支援 PERPIXEL_TRANSLUCENT 時把視窗底設成全透明，並關掉 root／layered pane 的不透明填色。
     */
    static void prepareMenuWindowTransparency(JWindow window) {
        try {
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                window.setBackground(new Color(0, 0, 0, 0));
            }
        } catch (RuntimeException ex) {
            // 不支援 per-pixel 時不改視窗底色，選單內容仍畫得出來。
        }
        JComponent root = window.getRootPane();
        root.setOpaque(false);
        root.setDoubleBuffered(false);
        root.putClientProperty("Window.shadow", Boolean.FALSE);
        root.putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);
        if (window.getLayeredPane() instanceof JComponent) {
            JComponent layered = (JComponent) window.getLayeredPane();
            layered.setOpaque(false);
            layered.setDoubleBuffered(false);
        }
        window.getContentPane().setBackground(new Color(0, 0, 0, 0));
        if (window.getContentPane() instanceof JComponent) {
            JComponent content = (JComponent) window.getContentPane();
            content.setOpaque(false);
            content.setDoubleBuffered(false);
        }
    }

}
