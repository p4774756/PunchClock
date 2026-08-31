package com.example.ui;

import java.awt.Font;
import java.util.Locale;

/** 跨平台字型：中文標籤 vs URL／英文輸入欄 */
public final class UiFonts {

    private UiFonts() {
    }

    public static Font chinesePlain(int size) {
        return chinese(Font.PLAIN, size);
    }

    public static Font chineseBold(int size) {
        return chinese(Font.BOLD, size);
    }

    public static Font chinese(int style, int size) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new Font("微軟正黑體", style, size);
        }
        if (os.contains("mac")) {
            return new Font("PingFang TC", style, size);
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    /** URL、Selector、Ping/Pong 分頁等以 ASCII 為主的文字 */
    public static Font latinPlain(int size) {
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    public static Font latinBold(int size) {
        return new Font(Font.SANS_SERIF, Font.BOLD, size);
    }
}
