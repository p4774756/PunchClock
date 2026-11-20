package com.example.ui.hud;

/**
 * 首頁與功能頁的角色主題（僅毛衣女孩）。
 * 舊設定檔的 {@code orbital} 與其它無法辨識的值讀進來時會正規化成 {@code sweater}。
 */
public enum CharacterTheme {
    SWEATER("sweater", "原版・毛衣女孩");

    private final String id;
    private final String label;

    CharacterTheme(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static CharacterTheme fromId(String id) {
        if (id == null) {
            return SWEATER;
        }
        String key = id.trim();
        for (CharacterTheme theme : values()) {
            if (theme.id.equalsIgnoreCase(key)) {
                return theme;
            }
        }
        return SWEATER;
    }

    public static CharacterTheme fromLabel(String label) {
        if (label == null) {
            return SWEATER;
        }
        String key = label.trim();
        for (CharacterTheme theme : values()) {
            if (theme.label.equals(key)) {
                return theme;
            }
        }
        return fromId(key);
    }
}