package com.example.ui;

import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.plaf.metal.MetalFileChooserUI;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.Locale;

/**
 * 跨平台字型與對話框入口。
 * <p>
 * 主畫面中文用平台字（Mac：PingFang TC），英數欄位用 SansSerif。
 * 不要改 UIManager 全域字型——會連原本正常的標籤一起缺字。
 * 對話框請走 showMessage／showConfirm／fileChooser，不要直接 new JOptionPane／JFileChooser。
 */
public final class UiFonts {

    private static final String DIALOG_CSS_FONT =
            "\"Helvetica Neue\",\"PingFang TC\",\"Lucida Grande\",sans-serif";

    private UiFonts() {
    }

    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
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

    public static void showWarning(Component parent, String message, String title) {
        showMessage(parent, message, title, JOptionPane.WARNING_MESSAGE);
    }

    public static void showMessage(Component parent, String message, String title, int messageType) {
        JOptionPane.showMessageDialog(parent, toHtmlMessage(message), title, messageType);
    }

    public static void showMessage(
            Component parent, String message, String title, int messageType, Icon icon) {
        JOptionPane.showMessageDialog(parent, toHtmlMessage(message), title, messageType, icon);
    }

    public static int showConfirm(
            Component parent, String message, String title, int optionType, int messageType) {
        return JOptionPane.showConfirmDialog(
                parent, toHtmlMessage(message), title, optionType, messageType);
    }

    /**
     * macOS 原生選檔面板會把 Downloads 畫成「D wnlo ads」；改走 Metal，由 Swing 自己畫。
     */
    public static JFileChooser fileChooser() {
        JFileChooser chooser = isMac() ? new SwingFileChooser() : new JFileChooser();
        if (isMac()) {
            applyFontTree(chooser, latinPlain(13));
        }
        return chooser;
    }

    /** JComponent.setUI 是 protected，只能從子類呼叫。 */
    private static final class SwingFileChooser extends JFileChooser {
        @Override
        public void updateUI() {
            setUI(new MetalFileChooserUI(this));
        }
    }

    static String toHtmlMessage(String message) {
        return "<html><body style='font-family:" + DIALOG_CSS_FONT + ";"
                + "font-size:13pt;width:420px'>"
                + escapeHtml(message).replace("\r\n", "<br>").replace("\n", "<br>")
                + "</body></html>";
    }

    private static void applyFontTree(Component component, Font font) {
        component.setFont(font);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                applyFontTree(child, font);
            }
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
