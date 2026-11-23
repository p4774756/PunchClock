package com.example.ui.hud;

/** 首頁全幅插畫（設定裡可選 B 夜晚房間或 C 頂樓泳池）。 */
public enum HomeIllustration {
    ROOM_B("b", "夜晚房間"),
    POOL_C("c", "頂樓泳池");

    private final String id;
    private final String label;

    HomeIllustration(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String resourcePath() {
        return this == POOL_C ? "/hud/anime/home-c.jpg" : "/hud/anime/home-b.jpg";
    }

    /** 頂樓泳池 3:4 時，上下裁切時偏上對齊以保住臉部。 */
    public boolean preferTopWhenVerticalCrop() {
        return this == POOL_C;
    }

    public static HomeIllustration fromId(String id) {
        if (id == null || id.isBlank()) {
            return ROOM_B;
        }
        String key = id.trim();
        for (HomeIllustration value : values()) {
            if (value.id.equalsIgnoreCase(key)) {
                return value;
            }
        }
        return ROOM_B;
    }

    public static HomeIllustration fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return ROOM_B;
        }
        String key = label.trim();
        for (HomeIllustration value : values()) {
            if (value.label.equals(key)) {
                return value;
            }
        }
        return fromId(key);
    }
}