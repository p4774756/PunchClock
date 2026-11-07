package com.example.ui.hud;

/**
 * 首頁與功能頁共用的角色主題。只換素材和呈現，頁面與控制項不變。
 * 舊設定沒有這個欄位、或寫入無法辨識的值時，回到原版。
 */
public enum CharacterTheme {
    SWEATER("sweater", "原版・毛衣女孩"),
    ORBITAL("orbital", "軌道觀景窗・粉髮女駕駛");

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

    public static String[] labels() {
        String[] labels = new String[values().length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = values()[i].label;
        }
        return labels;
    }
}
