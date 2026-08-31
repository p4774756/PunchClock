package com.example.ui;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;

/**
 * UI 面板工廠 — 負責建構各區塊的 Swing 面板
 * 將 UI 佈局邏輯從 App 主類別中抽離，減少構造函式臃腫度
 */
public class PanelFactory {

    /** 裝置互動分頁／面板標題 */
    public static final String PEER_TAB_LABEL = "(o´・ω・`)σ)Д`)";

    // ==================== 分組 1: 雲端服務與裝置設定 ====================

    /**
     * 建立雲端服務設定面板的內容
     * 回傳建構好的 JPanel，呼叫端需透過 refs 參數取得各元件的引用
     */
    public static JPanel createServerConfigBody(ServerConfigRefs refs, Font mainFont, Font boldFont, Font fieldFont) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        String[] workerOptions = { "company-worker", "company-worker2", "company-worker3", "company-worker4" };
        refs.clientIdCombo = new JComboBox<>(workerOptions);
        refs.clientIdCombo.setEditable(true);
        refs.clientIdCombo.setFont(mainFont);
        refs.clientIdCombo.setPrototypeDisplayValue("company-worker4");
        refs.clientIdCombo.setToolTipText("可下拉選擇預設值，或直接輸入自訂 Worker ID；雲端連線中會鎖定，請先取消「啟用雲端」再修改");

        refs.heartbeatTokenField = new JPasswordField("punchclock-dev-secret");
        refs.heartbeatTokenField.setFont(fieldFont);
        refs.heartbeatTokenField.setColumns(18);
        refs.heartbeatTokenField.setToolTipText("與伺服器約定的認證 Token，需與後端設定一致；雲端連線中會鎖定，請先取消「啟用雲端」再修改");

        refs.serverUrlTextField = new JTextField("http://localhost:3000");
        refs.serverUrlTextField.setFont(fieldFont);
        refs.serverUrlTextField.setColumns(48);
        refs.serverUrlTextField.setToolTipText("心跳伺服器網址，例如 https://xxx.onrender.com；雲端連線中會鎖定，請先取消「啟用雲端」再修改");

        lockFieldHeight(refs.clientIdCombo);
        lockFieldHeight(refs.heartbeatTokenField);
        lockFieldHeight(refs.serverUrlTextField);

        JLabel clientIdLabel = new JLabel("裝置 ID / Worker ID：");
        clientIdLabel.setFont(mainFont);
        JLabel tokenLabel = new JLabel("心跳 Token：");
        tokenLabel.setFont(mainFont);
        JLabel serverUrlLabel = new JLabel("Server 雲端網址：");
        serverUrlLabel.setFont(mainFont);

        refs.enableServerCheckBox = new JCheckBox("啟用雲端單向狀態回報", false);
        refs.enableServerCheckBox.setFont(boldFont);
        refs.enableServerCheckBox.setToolTipText("開啟後定期回報本機執行狀態到雲端 Dashboard");
        refs.trustAllSslCheckBox = new JCheckBox("信任所有 SSL（除錯）", false);
        refs.trustAllSslCheckBox.setFont(mainFont);
        refs.trustAllSslCheckBox.setToolTipText("預設關閉。啟用雲端時會鎖定；需先取消雲端才能修改。僅本機自簽憑證除錯用。");
        refs.heartbeatStatusLabel = new JLabel("[離線] 未連線 (已停用)", SwingConstants.LEFT);
        refs.heartbeatStatusLabel.setFont(boldFont);
        refs.heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
        refs.heartbeatStatusLabel.setBorder(new EmptyBorder(0, 12, 0, 12));

        panel.add(formRow(clientIdLabel, refs.clientIdCombo, tokenLabel, refs.heartbeatTokenField));
        panel.add(Box.createVerticalStrut(4));
        panel.add(formRowStretch(serverUrlLabel, refs.serverUrlTextField));
        panel.add(Box.createVerticalStrut(4));
        panel.add(formRow(
                refs.enableServerCheckBox,
                refs.trustAllSslCheckBox,
                refs.heartbeatStatusLabel));

        return panel;
    }

    /** 雲端設定面板的元件引用容器 */
    public static class ServerConfigRefs {
        public JComboBox<String> clientIdCombo;
        public JTextField serverUrlTextField;
        public JPasswordField heartbeatTokenField;
        public JCheckBox enableServerCheckBox;
        public JCheckBox trustAllSslCheckBox;
        public JLabel heartbeatStatusLabel;
    }

    // ==================== 裝置互動 ====================

    public static JPanel createPeerInteractionPanel(PeerInteractionRefs refs, Font mainFont, Font boldFont) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(4, 0, 0, 0));

        JLabel hint = new JLabel("顯示同一伺服器上的裝置（含本機標示，每 15 秒隨心跳更新）");
        hint.setFont(mainFont);
        hint.setForeground(new Color(100, 116, 139));
        refs.peerHintLabel = hint;

        refs.peerTableModel = new javax.swing.table.DefaultTableModel(
                new Object[]{"裝置 ID", "狀態", "等待任務", "版本"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        refs.peerTable = new JTable(refs.peerTableModel);
        refs.peerTable.setFont(mainFont);
        refs.peerTable.setRowHeight(28);
        refs.peerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refs.peerTable.getTableHeader().setFont(boldFont);

        JScrollPane tableScroll = new JScrollPane(refs.peerTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        tableScroll.setPreferredSize(new Dimension(400, 180));
        refs.peerTableScroll = tableScroll;

        refs.peerStatusLabel = new JLabel("尚未取得同事列表（請先啟用雲端狀態回報）");
        refs.peerStatusLabel.setFont(mainFont);
        refs.peerStatusLabel.setForeground(new Color(100, 116, 139));

        refs.openCloudSettingsButton = new JButton("前往雲端設定");
        refs.openCloudSettingsButton.setFont(boldFont);
        refs.openCloudSettingsButton.setToolTipText("切換至「雲端設定」分頁以啟用連線");
        refs.openCloudSettingsButton.setVisible(false);

        refs.messageField = new JTextField();
        refs.messageField.setFont(mainFont);
        refs.messageField.setToolTipText("輸入要傳給選中同事的訊息（最多 500 字）");

        refs.sendMessageButton = new JButton("傳送訊息");
        refs.sendMessageButton.setFont(boldFont);

        refs.pokeButton = new JButton("戳一下");
        refs.pokeButton.setFont(boldFont);
        refs.pokeButton.setToolTipText("發送輕量提醒給選中的同事");

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionRow.setOpaque(false);
        actionRow.add(new JLabel("訊息："));
        actionRow.getComponent(0).setFont(mainFont);
        actionRow.add(refs.messageField);
        refs.messageField.setPreferredSize(new Dimension(280, 28));
        actionRow.add(refs.sendMessageButton);
        actionRow.add(refs.pokeButton);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        north.add(hint);
        north.add(Box.createVerticalStrut(6));
        north.add(refs.peerStatusLabel);
        north.add(Box.createVerticalStrut(6));
        north.add(refs.openCloudSettingsButton);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(tableScroll, BorderLayout.CENTER);
        center.add(actionRow, BorderLayout.SOUTH);

        panel.add(north, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    /** 裝置互動面板元件引用容器 */
    public static class PeerInteractionRefs {
        public JTable peerTable;
        public javax.swing.table.DefaultTableModel peerTableModel;
        public JScrollPane peerTableScroll;
        public JTextField messageField;
        public JButton sendMessageButton;
        public JButton pokeButton;
        public JButton openCloudSettingsButton;
        public JLabel peerHintLabel;
        public JLabel peerStatusLabel;
    }

    // ==================== 說明 ====================

    public static JPanel createHelpPanel(Font mainFont, Font boldFont, Font fieldFont) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(8, 4, 8, 4));

        JTextArea text = new JTextArea(helpText());
        text.setEditable(false);
        text.setLineWrap(false);
        text.setFont(new Font("Menlo", Font.PLAIN, 12));
        text.setBackground(new Color(248, 250, 252));
        text.setForeground(new Color(30, 41, 59));
        text.setMargin(new Insets(10, 12, 10, 12));
        text.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel group = new JPanel(new BorderLayout());
        group.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(4, 8, 6, 8)));
        JLabel titleLabel = new JLabel("Ping/Pong 連線測試");
        titleLabel.setFont(boldFont);
        titleLabel.setForeground(new Color(30, 41, 59));
        titleLabel.setBorder(new EmptyBorder(0, 4, 6, 0));
        group.add(titleLabel, BorderLayout.NORTH);
        group.add(scroll, BorderLayout.CENTER);
        root.add(group, BorderLayout.CENTER);
        return root;
    }

    private static String helpText() {
        return ""
                + "Ping / Pong 連線測試（不需 Token）\n"
                + "────────────────────────────────\n"
                + "把網址換成「雲端設定」裡的 Server 雲端網址即可。\n"
                + "\n"
                + "# 正式 Server\n"
                + "curl -sS \"https://your-punchclock-server.onrender.com/ping\"\n"
                + "\n"
                + "# 本機\n"
                + "curl -sS \"http://localhost:3000/ping\"\n"
                + "\n"
                + "# 預期回應類似：\n"
                + "# {\"message\":\"pong\",\"timestamp\":\"...\"}\n";
    }

    // ==================== 分組 2: 雙槽位打卡 ====================

    public static JPanel createSlotPanel(SlotPanelRefs refs, Font mainFont, Font boldFont, Font fieldFont) {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(new EmptyBorder(2, 2, 4, 2));

        JPanel sharedContent = new JPanel();
        sharedContent.setLayout(new BoxLayout(sharedContent, BoxLayout.Y_AXIS));

        refs.urlTextField = new JTextField("https://www.msn.com/zh-tw");
        refs.urlTextField.setFont(fieldFont);
        refs.urlTextField.setColumns(56);
        refs.urlTextField.setToolTipText("打卡頁面的完整網址，程式會自動開啟此頁面");
        refs.buttonIdTextField = new JTextField("finance");
        refs.buttonIdTextField.setFont(fieldFont);
        refs.buttonIdTextField.setColumns(8);
        refs.buttonIdTextField.setToolTipText("要點擊的按鈕 CSS Selector 或 id，例如 #finance");
        refs.browserCombo = new JComboBox<>(TaskEditDialog.BROWSER_OPTIONS);
        refs.browserCombo.setFont(fieldFont);
        refs.browserCombo.setPrototypeDisplayValue("Chromium（內建）");
        TaskEditDialog.attachBrowserTooltips(refs.browserCombo);
        refs.executeNowButton = new JButton("立即執行");
        refs.executeNowButton.setFont(boldFont);
        refs.executeNowButton.setToolTipText("使用目前共用設定，立即執行一次打卡測試");

        lockFieldHeight(refs.urlTextField);
        sharedContent.add(formRowStretch(labeled(mainFont, "目標打卡網址："), refs.urlTextField));
        sharedContent.add(Box.createVerticalStrut(4));
        sharedContent.add(selectorBrowserActionRow(mainFont, fieldFont,
                refs.buttonIdTextField, refs.browserCombo, refs.executeNowButton));

        JPanel shared = createCollapsibleGroupPanel("共用打卡設定", sharedContent, boldFont, false);

        refs.workIn = createSlotCard("上班打卡", mainFont, boldFont);
        refs.workOut = createSlotCard("下班打卡", mainFont, boldFont);

        JPanel slotRow = new JPanel(new GridLayout(1, 2, 8, 0));
        slotRow.add(refs.workIn.panel);
        slotRow.add(refs.workOut.panel);

        GridBagConstraints rootGbc = new GridBagConstraints();
        rootGbc.gridx = 0;
        rootGbc.weightx = 1.0;
        rootGbc.fill = GridBagConstraints.HORIZONTAL;
        rootGbc.insets = new Insets(0, 0, 6, 0);

        rootGbc.gridy = 0;
        root.add(shared, rootGbc);
        rootGbc.gridy = 1;
        rootGbc.insets = new Insets(0, 0, 4, 0);
        root.add(slotRow, rootGbc);

        return root;
    }

    private static SlotCardRefs createSlotCard(String title, Font mainFont, Font boldFont) {
        SlotCardRefs refs = new SlotCardRefs();
        refs.panel = createCompactGroupPanel(title, boldFont);
        refs.panel.setLayout(new BorderLayout(0, 4));

        JPanel settings = new JPanel(new GridBagLayout());
        GridBagConstraints sgbc = new GridBagConstraints();
        sgbc.anchor = GridBagConstraints.CENTER;
        sgbc.insets = new Insets(0, 0, 0, 4);

        refs.enabledCheckBox = new JCheckBox("啟用", true);
        refs.enabledCheckBox.setFont(boldFont);
        refs.enabledCheckBox.setToolTipText("勾選後開始自動排程，並鎖定時分設定；取消勾選後才能修改時間");

        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format("%02d", i);
        refs.hourCombo = new JComboBox<>(hours);
        refs.hourCombo.setFont(mainFont);
        refs.hourCombo.setPrototypeDisplayValue("00");
        refs.hourCombo.setToolTipText("預定打卡的小時（00–23）");
        lockComboSize(refs.hourCombo);

        String[] minutes = new String[60];
        for (int i = 0; i < 60; i++) minutes[i] = String.format("%02d", i);
        refs.minuteCombo = new JComboBox<>(minutes);
        refs.minuteCombo.setFont(mainFont);
        refs.minuteCombo.setPrototypeDisplayValue("00");
        refs.minuteCombo.setToolTipText("預定打卡的分鐘（00–59）");
        lockComboSize(refs.minuteCombo);

        refs.randomOffsetCheckBox = new JCheckBox("±5 分隨機", true);
        refs.randomOffsetCheckBox.setFont(mainFont);
        refs.randomOffsetCheckBox.setForeground(new Color(147, 51, 234));
        refs.randomOffsetCheckBox.setToolTipText("在設定時間前後隨機 ±5 分鐘，避免每天固定同一秒打卡");

        sgbc.gridx = 0;
        sgbc.gridy = 0;
        sgbc.gridwidth = 1;
        sgbc.weightx = 0.0;
        sgbc.fill = GridBagConstraints.NONE;
        settings.add(refs.enabledCheckBox, sgbc);
        sgbc.gridx = 1;
        settings.add(refs.hourCombo, sgbc);
        sgbc.gridx = 2;
        JLabel hourUnit = new JLabel("時");
        hourUnit.setFont(mainFont);
        settings.add(hourUnit, sgbc);
        sgbc.gridx = 3;
        settings.add(refs.minuteCombo, sgbc);
        sgbc.gridx = 4;
        JLabel minUnit = new JLabel("分");
        minUnit.setFont(mainFont);
        settings.add(minUnit, sgbc);
        sgbc.gridx = 5;
        settings.add(refs.randomOffsetCheckBox, sgbc);
        sgbc.gridx = 6;
        sgbc.weightx = 1.0;
        sgbc.fill = GridBagConstraints.HORIZONTAL;
        settings.add(new JPanel(), sgbc);
        sgbc.weightx = 0.0;
        sgbc.fill = GridBagConstraints.NONE;

        refs.statusLabel = createSlotMetricLabel(mainFont, "—");
        refs.countdownLabel = createSlotMetricLabel(mainFont, "—");
        refs.triggerLabel = createSlotMetricLabel(mainFont, "—");
        refs.resultLabel = createSlotMetricLabel(mainFont, "—");

        JPanel statusGrid = new JPanel(new GridBagLayout());
        GridBagConstraints mgbc = new GridBagConstraints();
        mgbc.anchor = GridBagConstraints.WEST;
        mgbc.fill = GridBagConstraints.HORIZONTAL;
        mgbc.insets = new Insets(2, 0, 2, 6);
        mgbc.weightx = 0.5;
        mgbc.gridy = 0;
        mgbc.gridx = 0;
        mgbc.gridwidth = 1;
        statusGrid.add(createMetricCell("狀態", refs.statusLabel, mainFont), mgbc);
        mgbc.gridx = 1;
        statusGrid.add(createMetricCell("倒數", refs.countdownLabel, mainFont), mgbc);
        mgbc.gridy = 1;
        mgbc.gridx = 0;
        mgbc.gridwidth = 1;
        mgbc.weightx = 0.5;
        statusGrid.add(createMetricCell("預計觸發", refs.triggerLabel, mainFont), mgbc);
        mgbc.gridx = 1;
        statusGrid.add(createMetricCell("結果", refs.resultLabel, mainFont), mgbc);

        JPanel mainCol = new JPanel(new BorderLayout(0, 4));
        mainCol.add(settings, BorderLayout.NORTH);
        mainCol.add(statusGrid, BorderLayout.CENTER);

        refs.panel.add(mainCol, BorderLayout.CENTER);
        return refs;
    }

    private static JLabel createSlotMetricLabel(Font font, String text) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    private static JPanel createMetricCell(String title, JLabel value, Font font) {
        JPanel cell = new JPanel(new BorderLayout(0, 0));
        cell.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        titleLabel.setFont(new Font(font.getName(), Font.BOLD, 10));
        titleLabel.setForeground(new Color(100, 116, 139));
        value.setFont(new Font(font.getName(), Font.PLAIN, 12));
        value.setHorizontalAlignment(SwingConstants.LEFT);
        cell.add(titleLabel, BorderLayout.NORTH);
        cell.add(value, BorderLayout.CENTER);
        return cell;
    }

    private static JPanel createCompactGroupPanel(String title, Font titleFont) {
        JPanel panel = new JPanel();
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                title, TitledBorder.LEFT, TitledBorder.TOP,
                titleFont, new Color(30, 41, 59));
        panel.setBorder(new CompoundBorder(titledBorder, new EmptyBorder(2, 6, 4, 6)));
        return panel;
    }

    public static class SlotPanelRefs {
        public SlotCardRefs workIn;
        public SlotCardRefs workOut;
        public JTextField urlTextField;
        public JTextField buttonIdTextField;
        public JComboBox<String> browserCombo;
        public JButton executeNowButton;
    }

    public static class SlotCardRefs {
        public JPanel panel;
        public JCheckBox enabledCheckBox;
        public JComboBox<String> hourCombo;
        public JComboBox<String> minuteCombo;
        public JCheckBox randomOffsetCheckBox;
        public JLabel statusLabel;
        public JLabel countdownLabel;
        public JLabel triggerLabel;
        public JLabel resultLabel;
    }

    // ==================== 分組 3: 系統日誌 ====================

    /**
     * 建立系統日誌面板
     */
    public static JPanel createLogPanel(LogPanelRefs refs, Font mainFont, Font boldFont) {
        JPanel logPanel = createGroupPanel("系統日誌 (Console Log)", boldFont);
        logPanel.setLayout(new BorderLayout(0, 4));

        refs.logTextArea = new JTextArea();
        refs.logTextArea.setEditable(false);
        refs.logTextArea.setLineWrap(true);
        refs.logTextArea.setWrapStyleWord(true);
        refs.logTextArea.setBackground(new Color(15, 23, 42));
        refs.logTextArea.setForeground(new Color(56, 189, 248));
        refs.logTextArea.setCaretColor(Color.WHITE);
        refs.logTextArea.setFont(UiFonts.latinPlain(12));
        refs.logTextArea.setMargin(new Insets(6, 8, 6, 8));

        JScrollPane scrollPane = new JScrollPane(refs.logTextArea);
        scrollPane.setPreferredSize(new Dimension(780, 240));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        logPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel logActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        refs.clearLogButton = new JButton("清除 Log");
        refs.clearLogButton.setFont(boldFont);
        refs.clearLogButton.setToolTipText("清空下方日誌內容");
        refs.clearLogButton.addActionListener(e -> refs.logTextArea.setText(""));
        logActionPanel.add(refs.clearLogButton);
        logPanel.add(logActionPanel, BorderLayout.SOUTH);

        return logPanel;
    }

    /** 日誌面板的元件引用容器 */
    public static class LogPanelRefs {
        public JTextArea logTextArea;
        public JButton clearLogButton;
    }

    /** FlowLayout 列：只鎖定高度，避免被 BoxLayout 壓扁 */
    private static JPanel formRow(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Component component : components) {
            row.add(component);
        }
        int h = Math.max(row.getPreferredSize().height, 36);
        row.setMinimumSize(new Dimension(0, h));
        row.setPreferredSize(new Dimension(row.getPreferredSize().width, h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return row;
    }

    /** Selector + 瀏覽器 + 立即執行同一列；macOS 原生 ComboBox 需 BasicComboBoxUI 才與 JTextField 對齊 */
    private static JPanel selectorBrowserActionRow(
            Font labelFont, Font fieldFont,
            JComponent selectorField, JComboBox<?> browserCombo, JButton executeButton) {
        final int rowH = 28;
        final int gap = 8;

        selectorField.setFont(fieldFont);
        browserCombo.setFont(fieldFont);
        useTextFieldAlignedCombo(browserCombo);

        Dimension selectorDim = new Dimension(96, rowH);
        lockComponentSize(selectorField, selectorDim);

        int browserW = Math.max(browserCombo.getPreferredSize().width, 148);
        lockComponentSize(browserCombo, new Dimension(browserW, rowH));

        Dimension btnDim = new Dimension(96, rowH);
        lockComponentSize(executeButton, btnDim);
        executeButton.setMargin(new Insets(0, 8, 0, 8));

        JLabel selectorLabel = labeled(labelFont, "Selector：");
        JLabel browserLabel = labeled(labelFont, "瀏覽器：");

        FlowLayout flow = new FlowLayout(FlowLayout.LEFT, gap, 0);
        flow.setAlignOnBaseline(true);
        JPanel content = new JPanel(flow);
        content.setOpaque(false);
        content.add(selectorLabel);
        content.add(selectorField);
        content.add(browserLabel);
        content.add(browserCombo);
        content.add(executeButton);

        JPanel row = new JPanel(new BorderLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setOpaque(false);
        row.add(content, BorderLayout.WEST);

        int h = rowH + 4;
        row.setMinimumSize(new Dimension(0, h));
        row.setPreferredSize(new Dimension(row.getPreferredSize().width, h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return row;
    }

    /** 與 JTextField 同列時，避免 macOS Aqua ComboBox 視覺偏高，同時保留較自然的外觀 */
    private static void useTextFieldAlignedCombo(JComboBox<?> combo) {
        combo.setOpaque(true);
        combo.setBackground(Color.WHITE);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(0, 6, 0, 4)));
        combo.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton button = new JButton("\u25BE");
                button.setFont(UiFonts.latinPlain(11));
                button.setFocusable(false);
                button.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(226, 232, 240)));
                button.setContentAreaFilled(true);
                button.setBackground(new Color(248, 250, 252));
                button.setForeground(new Color(51, 65, 85));
                button.setMargin(new Insets(0, 0, 0, 0));
                return button;
            }
        });
    }

    private static void lockComponentSize(JComponent component, Dimension size) {
        component.setPreferredSize(size);
        component.setMinimumSize(size);
        component.setMaximumSize(size);
    }

    /** 標籤 + 可拉寬輸入欄（網址列） */
    private static JPanel formRowStretch(JComponent label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(8, 4));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        lockFieldHeight(field);
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        int h = Math.max(28, Math.max(label.getPreferredSize().height, field.getPreferredSize().height) + 8);
        row.setMinimumSize(new Dimension(120, h));
        row.setPreferredSize(new Dimension(400, h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return row;
    }

    /** 鎖定下拉選單尺寸，避免窄欄位擠壓時分選單 */
    private static void lockComboSize(JComboBox<?> combo) {
        Dimension size = combo.getPreferredSize();
        combo.setMinimumSize(size);
        combo.setPreferredSize(size);
        combo.setMaximumSize(size);
    }

    /** 只鎖定高度，寬度交給版面配置 */
    private static void lockFieldHeight(JComponent field) {
        int h = Math.max(field.getPreferredSize().height, 26);
        int w = Math.max(field.getPreferredSize().width, 80);
        field.setMinimumSize(new Dimension(80, h));
        field.setPreferredSize(new Dimension(w, h));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    private static JLabel labeled(Font font, String text) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        return label;
    }

    // ==================== 共用元件 ====================

    /**
     * 建立可折疊的分組面板
     */
    public static JPanel createCollapsibleGroupPanel(String title, JPanel contentPanel, Font titleFont, boolean startCollapsed) {
        JPanel outerPanel = new JPanel(new BorderLayout(0, 2));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerPanel.setBackground(new Color(241, 245, 249));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(5, 10, 5, 10)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleFont);
        titleLabel.setForeground(new Color(30, 41, 59));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JLabel toggleLabel = new JLabel(startCollapsed ? "► 點擊展開設定" : "▼ 點擊折疊收起");
        toggleLabel.setFont(UiFonts.chineseBold(12));
        toggleLabel.setForeground(new Color(37, 99, 235));
        headerPanel.add(toggleLabel, BorderLayout.EAST);

        contentPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

        if (startCollapsed) {
            contentPanel.setVisible(false);
        }

        headerPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                boolean visible = !contentPanel.isVisible();
                contentPanel.setVisible(visible);
                toggleLabel.setText(visible ? "▼ 點擊折疊收起" : "► 點擊展開設定");
                SwingUtilities.invokeLater(() -> {
                    outerPanel.revalidate();
                    outerPanel.repaint();
                    Container parent = outerPanel.getParent();
                    if (parent != null) {
                        parent.revalidate();
                        parent.repaint();
                    }
                    Window win = SwingUtilities.getWindowAncestor(outerPanel);
                    if (win != null) {
                        win.revalidate();
                        win.repaint();
                    }
                });
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                headerPanel.setBackground(new Color(226, 232, 240));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                headerPanel.setBackground(new Color(241, 245, 249));
            }
        });

        outerPanel.add(headerPanel, BorderLayout.NORTH);
        outerPanel.add(contentPanel, BorderLayout.CENTER);
        return outerPanel;
    }

    /**
     * 建立帶標題框線的分組面板
     */
    public static JPanel createGroupPanel(String title, Font titleFont) {
        JPanel panel = new JPanel();
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                title, TitledBorder.LEFT, TitledBorder.TOP,
                titleFont, new Color(30, 41, 59));
        panel.setBorder(new CompoundBorder(titledBorder, new EmptyBorder(4, 8, 6, 8)));
        return panel;
    }
}
