package com.example.ui;

/** 使用者看得到的產品名稱（jar／套件名可維持 punchclock）。 */
public final class AppBranding {

    public static final String DISPLAY_NAME = "小寶貝";

    private AppBranding() {
    }

    /**
     * 選單列、關於與 Dock 顯示名稱。必須在 AWT 工具箱建立前呼叫。
     */
    public static void installPlatformName() {
        System.setProperty("apple.awt.application.name", DISPLAY_NAME);
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", DISPLAY_NAME);
    }

    public static String windowTitle(String versionLabel) {
        if (versionLabel == null || versionLabel.isBlank()) {
            return DISPLAY_NAME;
        }
        return DISPLAY_NAME + "  " + versionLabel.trim();
    }
}