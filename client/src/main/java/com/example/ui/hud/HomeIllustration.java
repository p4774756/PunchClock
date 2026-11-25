package com.example.ui.hud;

/** 首頁全幅插畫（設定裡可選場景）。 */
public enum HomeIllustration {
    ROOM_B("b", "夜晚房間"),
    POOL_C("c", "頂樓泳池"),
    CHERRY_PARK_D("d", "櫻花公園"),
    NIGHT_MARKET_E("e", "台灣夜市"),
    OVERTIME_F("f", "加班夜景");

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
        return "/hud/anime/home-" + id + ".jpg";
    }

    /** 3:4 圖上下裁切時偏上對齊以保住臉部（頂樓泳池與新場景）。 */
    public boolean preferTopWhenVerticalCrop() {
        return this != ROOM_B;
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

    /** 依列舉順序輪到下一張；最後一張之後回到第一張。 */
    public HomeIllustration next() {
        HomeIllustration[] all = values();
        return all[(ordinal() + 1) % all.length];
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