package com.example.ui;

import javax.swing.JOptionPane;
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

    /** macOS 原生 JOptionPane 混中英時 Latin 字可能不顯示，改用明確字型的 HTML 訊息 */
    public static void showWarning(java.awt.Component parent, String message, String title) {
        String html = "<html><body style='font-family:\"PingFang TC\",\"Helvetica Neue\",sans-serif;"
                + "font-size:13pt;width:320px'>"
                + escapeHtml(message)
                + "</body></html>";
        JOptionPane.showMessageDialog(parent, html, title, JOptionPane.WARNING_MESSAGE);
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
