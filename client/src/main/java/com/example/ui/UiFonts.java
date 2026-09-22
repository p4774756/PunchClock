package com.example.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.plaf.metal.MetalFileChooserUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Locale;

/**
 * 跨平台字型與對話框入口。
 * <p>
 * 主畫面中文用平台字（Mac：PingFang TC），英數欄位用 SansSerif。
 * 不要改 UIManager 全域字型——會連原本正常的標籤一起缺字。
 * 對話框請走 showMessage／showConfirm／fileChooser／showCopyableMessage，不要直接 new JOptionPane／JFileChooser。
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

    /** URL、Selector、網路測試等以 ASCII 為主的文字 */
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

    /**
     * 可選取、可一鍵複製的訊息對話框（給同事文字訊息用）。
     */
    public static void showCopyableMessage(
            Component parent, String message, String title, int messageType, Icon icon) {
        JOptionPane.showMessageDialog(
                parent, buildCopyablePanel(message), title, messageType, icon);
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

    static JPanel buildCopyablePanel(String message) {
        String text = message == null ? "" : message;
        JTextArea area = new JTextArea(text);
        area.setFont(chinesePlain(13));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        int height = Math.min(280, Math.max(96, 24 + text.length() / 3));
        scroll.setPreferredSize(new Dimension(420, height));
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        JButton copyButton = new JButton("複製文字");
        copyButton.setFont(chinesePlain(12));
        copyButton.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            copyButton.setText("已複製");
        });

        JLabel hint = new JLabel("可直接選取文字，或按「複製文字」");
        hint.setFont(chinesePlain(11));
        hint.setForeground(new Color(100, 116, 139));

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.X_AXIS));
        south.add(hint);
        south.add(Box.createHorizontalGlue());
        south.add(copyButton);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
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
