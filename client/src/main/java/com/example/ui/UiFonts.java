package com.example.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
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
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
        showCopyableMessage(parent, message, message, title, messageType, icon);
    }

    /**
     * @param copyText 按「複製文字」時放進剪貼簿的內容（可與顯示文字不同，例如不含時間）
     */
    public static void showCopyableMessage(
            Component parent, String message, String copyText, String title, int messageType, Icon icon) {
        JOptionPane.showMessageDialog(
                parent, buildCopyablePanel(message, copyText), title, messageType, icon);
    }

    /**
     * 同事圖文訊息：上方顯示縮圖（可另存原圖），下方是可複製的文字。
     *
     * @param copyText 空白時只顯示 message（純圖片、沒有文字可複製）
     */
    public static void showCopyableMessageWithImage(
            Component parent, String message, String copyText, String title, int messageType,
            Icon icon, BufferedImage image) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        if (image != null) {
            JLabel imageLabel = new JLabel(new ImageIcon(fitForDisplay(image, 420, 320)));
            imageLabel.setHorizontalAlignment(JLabel.CENTER);
            imageLabel.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));

            JButton saveButton = new JButton("另存圖片");
            saveButton.setFont(chinesePlain(12));
            saveButton.addActionListener(e -> saveImageWithDialog(parent, image));
            JPanel saveRow = new JPanel(new BorderLayout());
            saveRow.setOpaque(false);
            saveRow.add(saveButton, BorderLayout.EAST);

            JPanel imageBox = new JPanel(new BorderLayout(0, 4));
            imageBox.setOpaque(false);
            imageBox.add(imageLabel, BorderLayout.CENTER);
            imageBox.add(saveRow, BorderLayout.SOUTH);
            panel.add(imageBox, BorderLayout.NORTH);
        }

        if (copyText != null && !copyText.isBlank()) {
            panel.add(buildCopyablePanel(message, copyText), BorderLayout.CENTER);
        } else {
            JLabel label = new JLabel(message == null ? "" : message);
            label.setFont(chinesePlain(13));
            panel.add(label, BorderLayout.CENTER);
        }
        JOptionPane.showMessageDialog(parent, panel, title, messageType, icon);
    }

    private static Image fitForDisplay(BufferedImage image, int maxW, int maxH) {
        int w = image.getWidth();
        int h = image.getHeight();
        double ratio = Math.min(1.0, Math.min((double) maxW / w, (double) maxH / h));
        if (ratio >= 1.0) {
            return image;
        }
        return image.getScaledInstance(
                Math.max(1, (int) Math.round(w * ratio)),
                Math.max(1, (int) Math.round(h * ratio)),
                Image.SCALE_SMOOTH);
    }

    private static void saveImageWithDialog(Component parent, BufferedImage image) {
        JFileChooser chooser = fileChooser();
        chooser.setDialogTitle("另存圖片");
        chooser.setSelectedFile(new File("image.jpg"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith(".jpg")
                && !target.getName().toLowerCase(Locale.ROOT).endsWith(".jpeg")) {
            target = new File(target.getParentFile(), target.getName() + ".jpg");
        }
        if (target.exists()
                && showConfirm(parent, "檔案已存在，要覆蓋嗎？\n" + target.getName(), "另存圖片",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            if (!ImageIO.write(image, "jpg", target)) {
                showWarning(parent, "無法儲存圖片。", "另存圖片");
            }
        } catch (IOException ex) {
            showWarning(parent, "無法儲存圖片：" + ex.getMessage(), "另存圖片");
        }
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
        return buildCopyablePanel(message, message);
    }

    static JPanel buildCopyablePanel(String message, String copyText) {
        String text = message == null ? "" : message;
        String clipboardText = copyText == null ? "" : copyText;
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
                    .setContents(new StringSelection(clipboardText), null);
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
