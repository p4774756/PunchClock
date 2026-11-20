package com.example.ui;

/** 使用者看得到的產品名稱（jar／套件名可維持 punchclock）。 */
public final class AppBranding {

    public static final String DISPLAY_NAME = "新光小寶貝";

    private AppBranding() {
    }

    public static String windowTitle(String versionLabel) {
        if (versionLabel == null || versionLabel.isBlank()) {
            return DISPLAY_NAME;
        }
        return DISPLAY_NAME + "  " + versionLabel.trim();
    }
}