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
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalFileChooserUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    /** 對話框訊息寬度，跟以往 HTML 訊息的 420px 對齊。 */
    private static final int MESSAGE_WIDTH = 420;

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
        JOptionPane.showMessageDialog(parent, messageBody(message), title, messageType);
    }

    public static void showMessage(
            Component parent, String message, String title, int messageType, Icon icon) {
        JOptionPane.showMessageDialog(parent, messageBody(message), title, messageType, icon);
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
            imageLabel.setToolTipText("按右鍵可複製圖片");

            JMenuItem copyItem = new JMenuItem("複製圖片");
            copyItem.setFont(chinesePlain(12));
            copyItem.addActionListener(e -> copyImageToClipboard(image));
            JMenuItem saveItem = new JMenuItem("另存圖片…");
            saveItem.setFont(chinesePlain(12));
            saveItem.addActionListener(e -> saveImageWithDialog(parent, image));
            JPopupMenu popup = new JPopupMenu();
            popup.add(copyItem);
            popup.add(saveItem);
            imageLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShow(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShow(e);
                }

                private void maybeShow(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            });

            JButton copyButton = new JButton("複製圖片");
            copyButton.setFont(chinesePlain(12));
            copyButton.addActionListener(e -> {
                if (copyImageToClipboard(image)) {
                    copyButton.setText("已複製");
                }
            });
            JButton saveButton = new JButton("另存圖片");
            saveButton.setFont(chinesePlain(12));
            saveButton.addActionListener(e -> saveImageWithDialog(parent, image));
            JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            saveRow.setOpaque(false);
            saveRow.add(copyButton);
            saveRow.add(saveButton);

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

    private static boolean copyImageToClipboard(BufferedImage image) {
        try {
            BufferedImage rgb = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{DataFlavor.imageFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return DataFlavor.imageFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return rgb;
                }
            }, null);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static void saveImageWithDialog(Component parent, BufferedImage image) {
        JFileChooser chooser = fileChooser();
        chooser.setDialogTitle("另存圖片");
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        chooser.setSelectedFile(new File("image-" + stamp + ".jpg"));
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
                parent, messageBody(message), title, optionType, messageType);
    }

    /** 讓使用者點一個選項。關掉視窗時回傳 -1。 */
    public static int showChoice(Component parent, String message, String title, String[] options) {
        if (options == null || options.length == 0) {
            return -1;
        }
        // 選項是 JButton 元件時，JOptionPane 不會幫忙接點擊，要自己設定回傳值並關閉視窗。
        JOptionPane[] holder = new JOptionPane[1];
        Object[] buttons = new Object[options.length];
        for (int i = 0; i < options.length; i++) {
            JButton button = new JButton(options[i] == null ? "" : options[i]);
            button.setFont(chinesePlain(13));
            final Integer index = i;
            button.addActionListener(e -> holder[0].setValue(index));
            buttons[i] = button;
        }
        JOptionPane pane = new JOptionPane(
                messageBody(message),
                JOptionPane.QUESTION_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                buttons,
                buttons[0]);
        holder[0] = pane;
        javax.swing.JDialog dialog = pane.createDialog(parent, title);
        dialog.setVisible(true);
        dialog.dispose();
        Object value = pane.getValue();
        return value instanceof Integer ? (Integer) value : -1;
    }

    /**
     * macOS 原生選檔面板會把 Downloads 畫成「D wnlo ads」；改走 Metal，由 Swing 自己畫。
     */
    public static JFileChooser fileChooser() {
        if (isMac()) {
            JFileChooser chooser = new SwingFileChooser();
            applyFontTree(chooser, latinPlain(13));
            return chooser;
        }
        return createWithLightDefaults();
    }

    /**
     * NightTheme 把 Label／List／ComboBox 前景設成淺色，Windows 原生選檔面板底色是白的，
     * 檔名、標籤就會變成白底白字。建立選檔面板的當下暫時換回淺色預設，建完再還原。
     */
    private static JFileChooser createWithLightDefaults() {
        Object[][] light = {
                {"Panel.background", new Color(240, 240, 240)},
                {"Label.foreground", Color.BLACK},
                {"CheckBox.foreground", Color.BLACK},
                {"Button.background", new Color(225, 225, 225)},
                {"Button.foreground", Color.BLACK},
                {"TextField.background", Color.WHITE},
                {"TextField.foreground", Color.BLACK},
                {"TextField.caretForeground", Color.BLACK},
                {"TextField.inactiveForeground", new Color(109, 109, 109)},
                {"ComboBox.background", Color.WHITE},
                {"ComboBox.foreground", Color.BLACK},
                {"ComboBox.selectionBackground", new Color(51, 153, 255)},
                {"ComboBox.selectionForeground", Color.WHITE},
                {"List.background", Color.WHITE},
                {"List.foreground", Color.BLACK},
                {"List.selectionBackground", new Color(51, 153, 255)},
                {"List.selectionForeground", Color.WHITE},
                {"Table.background", Color.WHITE},
                {"Table.foreground", Color.BLACK},
                {"Table.selectionBackground", new Color(51, 153, 255)},
                {"Table.selectionForeground", Color.WHITE},
                {"TableHeader.background", new Color(240, 240, 240)},
                {"TableHeader.foreground", Color.BLACK},
                {"ScrollPane.background", new Color(240, 240, 240)},
                {"Viewport.background", Color.WHITE},
                {"ToggleButton.foreground", Color.BLACK},
                {"ToolBar.foreground", Color.BLACK},
        };
        Object[] saved = new Object[light.length];
        for (int i = 0; i < light.length; i++) {
            String key = (String) light[i][0];
            saved[i] = UIManager.get(key);
            UIManager.put(key, light[i][1]);
        }
        try {
            return new JFileChooser();
        } finally {
            for (int i = 0; i < light.length; i++) {
                UIManager.put((String) light[i][0], saved[i]);
            }
        }
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

    /**
     * 純文字訊息。macOS Aqua 把 JOptionPane 的 HTML（CSS font-family）拿去畫時，
     * 會漏掉一部分英數，例如「IDE」「jar」和路徑裡的字母，版號也會整段空白。
     */
    static JTextArea messageBody(String message) {
        JTextArea area = new JTextArea(message == null ? "" : message);
        area.setFont(chinesePlain(13));
        area.setEditable(false);
        area.setOpaque(false);
        area.setFocusable(false);
        area.setRequestFocusEnabled(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setSize(MESSAGE_WIDTH, Short.MAX_VALUE);
        int height = Math.max(area.getFontMetrics(area.getFont()).getHeight(), area.getPreferredSize().height);
        area.setPreferredSize(new Dimension(MESSAGE_WIDTH, height));
        return area;
    }

    private static void applyFontTree(Component component, Font font) {
        component.setFont(font);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                applyFontTree(child, font);
            }
        }
    }

}
