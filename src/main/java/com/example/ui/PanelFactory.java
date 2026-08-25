package com.example.ui;

import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * UI 面板工廠 — 負責建構各區塊的 Swing 面板
 * 將 UI 佈局邏輯從 App 主類別中抽離，減少構造函式臃腫度
 */
public class PanelFactory {

    // ==================== 分組 1: 雲端服務與裝置設定 ====================

    /**
     * 建立雲端服務設定面板的內容
     * 回傳建構好的 JPanel，呼叫端需透過 refs 參數取得各元件的引用
     */
    public static JPanel createServerConfigBody(ServerConfigRefs refs, Font mainFont, Font boldFont) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: 裝置 ID
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel clientIdLabel = new JLabel("🆔 裝置 ID / Worker ID：");
        clientIdLabel.setFont(mainFont);
        panel.add(clientIdLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; gbc.gridwidth = 2;
        String[] workerOptions = { "company-worker", "company-worker2", "company-worker3", "company-worker4" };
        refs.clientIdCombo = new JComboBox<>(workerOptions);
        refs.clientIdCombo.setEditable(true);
        refs.clientIdCombo.setFont(mainFont);
        refs.clientIdCombo.setToolTipText("可下拉選擇預設值，或直接輸入自訂 Worker ID");
        panel.add(refs.clientIdCombo, gbc);

        // Row 1: Server 網址
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel serverUrlLabel = new JLabel("📡 Server 雲端網址：");
        serverUrlLabel.setFont(mainFont);
        panel.add(serverUrlLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        refs.serverUrlTextField = new JTextField("http://localhost:3000");
        refs.serverUrlTextField.setFont(mainFont);
        panel.add(refs.serverUrlTextField, gbc);

        // Row 2: 心跳 Token
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel tokenLabel = new JLabel("🔑 心跳 Token：");
        tokenLabel.setFont(mainFont);
        panel.add(tokenLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0; gbc.gridwidth = 2;
        refs.heartbeatTokenField = new JPasswordField("clickclick-dev-secret");
        refs.heartbeatTokenField.setFont(mainFont);
        panel.add(refs.heartbeatTokenField, gbc);

        // Row 3: 啟用 checkbox + SSL 除錯 + 狀態 + 測試按鈕
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JPanel enableRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        refs.enableServerCheckBox = new JCheckBox("啟用雲端單向狀態回報", false);
        refs.enableServerCheckBox.setFont(boldFont);
        refs.trustAllSslCheckBox = new JCheckBox("信任所有 SSL（除錯）", false);
        refs.trustAllSslCheckBox.setFont(mainFont);
        refs.trustAllSslCheckBox.setToolTipText("預設關閉。僅在本機遇到自簽憑證時再開啟，正式環境請勿勾選。");
        enableRow.add(refs.enableServerCheckBox);
        enableRow.add(refs.trustAllSslCheckBox);
        panel.add(enableRow, gbc);

        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0; gbc.gridwidth = 1;
        refs.heartbeatStatusLabel = new JLabel("⚪ 未連線 (已停用)", SwingConstants.LEFT);
        refs.heartbeatStatusLabel.setFont(boldFont);
        refs.heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
        panel.add(refs.heartbeatStatusLabel, gbc);

        gbc.gridx = 2; gbc.gridy = 3; gbc.weightx = 0.0; gbc.gridwidth = 1;
        refs.testServerButton = new JButton("🧪 測試 Server 連線");
        refs.testServerButton.setFont(mainFont);
        refs.testServerButton.setToolTipText("發送 GET /ping，只確認伺服器是否在線，不驗證心跳 Token。");
        panel.add(refs.testServerButton, gbc);

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
        public JButton testServerButton;
    }

    // ==================== 分組 2: 任務設定表單 ====================

    /**
     * 建立任務設定表單面板的內容
     */
    public static JPanel createTaskFormBody(TaskFormRefs refs, Font mainFont, Font boldFont) {
        JPanel container = new JPanel(new BorderLayout(12, 0));
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;

        // Row 0: 任務名稱 + 執行瀏覽器（同一列）
        JLabel nameLabel = new JLabel("📝 任務名稱：");
        nameLabel.setFont(mainFont);
        refs.taskNameTextField = new JTextField("上班打卡");
        refs.taskNameTextField.setFont(mainFont);
        fixFieldWidth(refs.taskNameTextField, 18);

        JLabel browserLabel = new JLabel("🌐 執行瀏覽器：");
        browserLabel.setFont(mainFont);
        refs.browserCombo = new JComboBox<>(new String[]{
                "Microsoft Edge (本機已安裝)", "Google Chrome (本機已安裝)",
                "內建 Chromium 瀏覽器", "內建 Firefox 瀏覽器", "內建 WebKit (Safari核心)"
        });
        refs.browserCombo.setFont(mainFont);
        refs.browserCombo.setPrototypeDisplayValue("Microsoft Edge (本機已安裝)");
        fixComponentWidth(refs.browserCombo);
        addDualFieldRow(panel, gbc, 0, nameLabel, refs.taskNameTextField, browserLabel, refs.browserCombo);

        // Row 1: 目標打卡網址 + 按鈕 Selector（同一列）
        JLabel urlLabel = new JLabel("🔗 目標打卡網址：");
        urlLabel.setFont(mainFont);
        refs.urlTextField = new JTextField("https://tw.yahoo.com");
        refs.urlTextField.setFont(mainFont);
        fixFieldWidth(refs.urlTextField, 32);

        JLabel buttonIdLabel = new JLabel("🔘 Selector：");
        buttonIdLabel.setFont(mainFont);
        refs.buttonIdTextField = new JTextField("check_in");
        refs.buttonIdTextField.setFont(mainFont);
        fixFieldWidth(refs.buttonIdTextField, 14);
        addDualFieldRow(panel, gbc, 1, urlLabel, refs.urlTextField, buttonIdLabel, refs.buttonIdTextField);

        // Row 2: 排程時間（日期、時分、隨機偏移）
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel timeLabel = new JLabel("📆 預定打卡時間：");
        timeLabel.setFont(mainFont);
        panel.add(timeLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.0; gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel timeSelectionPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));

        DatePickerSettings dateSettings = new DatePickerSettings();
        dateSettings.setAllowKeyboardEditing(true);
        refs.datePicker = new DatePicker(dateSettings);
        refs.datePicker.setDateToToday();
        dateSettings.setDateRangeLimits(LocalDate.now(), LocalDate.MAX);
        JButton toggleBtn = refs.datePicker.getComponentToggleCalendarButton();
        toggleBtn.setPreferredSize(new Dimension(0, 0));
        toggleBtn.setBorder(null);
        refs.datePicker.getComponentDateTextField().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                SwingUtilities.invokeLater(refs.datePicker::openPopup);
            }
        });
        timeSelectionPanel.add(refs.datePicker);

        LocalDateTime now = LocalDateTime.now();
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format("%02d", i);
        refs.hourCombo = new JComboBox<>(hours);
        refs.hourCombo.setFont(mainFont);
        refs.hourCombo.setSelectedIndex(now.getHour());
        timeSelectionPanel.add(refs.hourCombo);
        timeSelectionPanel.add(new JLabel("時"));

        String[] minutes = new String[60];
        for (int i = 0; i < 60; i++) minutes[i] = String.format("%02d", i);
        refs.minuteCombo = new JComboBox<>(minutes);
        refs.minuteCombo.setFont(mainFont);
        refs.minuteCombo.setSelectedIndex(now.getMinute());
        timeSelectionPanel.add(refs.minuteCombo);
        timeSelectionPanel.add(new JLabel("分"));

        refs.randomOffsetCheckBox = new JCheckBox("±5 分隨機", true);
        refs.randomOffsetCheckBox.setFont(boldFont);
        refs.randomOffsetCheckBox.setForeground(new Color(147, 51, 234));
        refs.randomOffsetCheckBox.setToolTipText("啟用前後 ±5 分鐘隨機打卡");
        timeSelectionPanel.add(refs.randomOffsetCheckBox);

        panel.add(timeSelectionPanel, gbc);

        // Row 3: 時間快捷按鈕
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        JLabel presetLabel = new JLabel("⏱️ 時間快捷：");
        presetLabel.setFont(mainFont);
        panel.add(presetLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 0.0; gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel presetButtonPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        refs.presetWorkInButton = new JButton("上班 09:00");
        refs.presetWorkOutButton = new JButton("下班 18:00");
        refs.presetNowButton = new JButton("現在時間");
        refs.presetTest1MinButton = new JButton("+1分");
        refs.presetTest3MinButton = new JButton("+3分");
        for (JButton btn : new JButton[]{refs.presetWorkInButton, refs.presetWorkOutButton,
                refs.presetNowButton, refs.presetTest1MinButton, refs.presetTest3MinButton}) {
            btn.setFont(mainFont);
        }
        refs.presetWorkInButton.setToolTipText("快速填入上班 09:00，並開啟 ±5 分鐘隨機");
        refs.presetWorkOutButton.setToolTipText("快速填入下班 18:00，並開啟 ±5 分鐘隨機");
        refs.presetNowButton.setToolTipText("帶入目前系統時間，關閉隨機（精準）");
        refs.presetTest1MinButton.setToolTipText("在目前設定時間上加 1 分鐘，可連續點擊累加");
        refs.presetTest3MinButton.setToolTipText("在目前設定時間上加 3 分鐘，可連續點擊累加");
        presetButtonPanel.add(refs.presetWorkInButton);
        presetButtonPanel.add(refs.presetWorkOutButton);
        presetButtonPanel.add(refs.presetNowButton);
        presetButtonPanel.add(refs.presetTest1MinButton);
        presetButtonPanel.add(refs.presetTest3MinButton);
        panel.add(presetButtonPanel, gbc);

        refs.addTaskButton = createAddTaskButton(boldFont);
        JPanel addButtonPanel = new JPanel(new BorderLayout());
        addButtonPanel.setBorder(new EmptyBorder(0, 4, 10, 4));
        addButtonPanel.add(refs.addTaskButton, BorderLayout.SOUTH);

        container.add(panel, BorderLayout.CENTER);
        container.add(addButtonPanel, BorderLayout.EAST);
        return container;
    }

    /** 任務設定表單的元件引用容器 */
    public static class TaskFormRefs {
        public JButton presetWorkInButton, presetWorkOutButton, presetNowButton, presetTest1MinButton, presetTest3MinButton;
        public JTextField taskNameTextField, urlTextField, buttonIdTextField;
        public DatePicker datePicker;
        public JComboBox<String> hourCombo, minuteCombo, browserCombo;
        public JCheckBox randomOffsetCheckBox;
        public JButton addTaskButton;
    }

    // ==================== 分組 3: 任務列表 ====================

    /**
     * 建立任務列表面板
     */
    public static JPanel createTaskTablePanel(TaskTableRefs refs, Font mainFont, Font boldFont) {
        JPanel tableGroup = createGroupPanel("📋 排定打卡任務列表（點欄位標題可排序）", boldFont);
        tableGroup.setLayout(new BorderLayout(0, 6));

        String[] columnNames = {"ID", "任務名稱", "預定時間", "實際觸發 (隨機)", "倒數", "網址", "瀏覽器", "狀態", "訊息/結果"};
        refs.tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        refs.taskTable = new JTable(refs.tableModel);
        refs.taskTable.setFont(mainFont);
        refs.taskTable.setRowHeight(24);
        refs.taskTable.getTableHeader().setFont(boldFont);
        refs.taskTable.getTableHeader().setBackground(new Color(241, 245, 249));
        refs.taskTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        refs.taskTable.setToolTipText("可多選：Cmd/Ctrl+點選 或 Shift+範圍選取；點欄位標題可排序");
        refs.taskTable.getTableHeader().setToolTipText("點擊欄位標題排序（再點一次切換升/降冪）");

        // 點擊欄位標題排序
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(refs.tableModel);
        Comparator<String> timeComparator = (a, b) -> {
            String left = extractTimePrefix(a);
            String right = extractTimePrefix(b);
            return left.compareTo(right);
        };
        sorter.setComparator(2, timeComparator); // 預定時間
        sorter.setComparator(3, timeComparator); // 實際觸發
        refs.taskTable.setRowSorter(sorter);
        refs.rowSorter = sorter;

        refs.taskTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        refs.taskTable.setFillsViewportHeight(true);

        int[] widths = {80, 120, 160, 170, 110, 240, 110, 80, 200};
        for (int i = 0; i < widths.length; i++) {
            refs.taskTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            refs.taskTable.getColumnModel().getColumn(i).setMinWidth(widths[i]);
        }

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        refs.taskTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        refs.taskTable.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        refs.taskTable.getColumnModel().getColumn(7).setCellRenderer(centerRenderer);

        JScrollPane tableScrollPane = new JScrollPane(refs.taskTable);
        tableScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        tableScrollPane.setPreferredSize(new Dimension(900, 180));
        tableScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        tableScrollPane.getHorizontalScrollBar().setBlockIncrement(120);
        tableGroup.add(tableScrollPane, BorderLayout.CENTER);

        // 操作列：可換行，避免視窗不夠寬時被裁切
        JPanel tableControlPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));

        refs.selectAllTasksButton = new JButton("☑️ 全選");
        refs.clearTaskSelectionButton = new JButton("⬜ 取消選取");
        refs.selectAllTasksButton.setToolTipText("選取列表中全部任務（可用 Cmd/Ctrl+點選 多選）");
        refs.clearTaskSelectionButton.setToolTipText("清除目前選取");

        refs.executeNowButton = new JButton("⚡ 立即執行");
        refs.editTaskButton = new JButton("✏️ 編輯");
        refs.reuseTaskButton = new JButton("🔄 重新排定");
        refs.cancelTaskButton = new JButton("🛑 取消");
        refs.deleteTaskButton = new JButton("🗑️ 刪除");

        refs.executeNowButton.setToolTipText("立即執行選取的任務（可多選）");
        refs.editTaskButton.setToolTipText("編輯任務設定（任何狀態皆可，一次 1 筆）");
        refs.reuseTaskButton.setToolTipText("時間仍在未來時，將同一筆任務再次改為等待中");
        refs.cancelTaskButton.setToolTipText("取消選取任務的排程（可多選；要全部取消請先按全選）");
        refs.deleteTaskButton.setToolTipText("刪除選取任務紀錄（可多選；要全部刪除請先按全選）");

        for (JButton btn : new JButton[]{
                refs.selectAllTasksButton, refs.clearTaskSelectionButton,
                refs.executeNowButton, refs.editTaskButton, refs.reuseTaskButton,
                refs.cancelTaskButton, refs.deleteTaskButton}) {
            btn.setFont(boldFont);
        }
        refs.editTaskButton.setForeground(new Color(37, 99, 235));
        refs.reuseTaskButton.setForeground(new Color(16, 185, 129));
        refs.deleteTaskButton.setForeground(new Color(225, 29, 72));

        JSeparator controlSep = new JSeparator(SwingConstants.VERTICAL);
        controlSep.setPreferredSize(new Dimension(8, 22));

        tableControlPanel.add(refs.selectAllTasksButton);
        tableControlPanel.add(refs.clearTaskSelectionButton);
        tableControlPanel.add(controlSep);
        tableControlPanel.add(refs.executeNowButton);
        tableControlPanel.add(refs.editTaskButton);
        tableControlPanel.add(refs.reuseTaskButton);
        tableControlPanel.add(refs.cancelTaskButton);
        tableControlPanel.add(refs.deleteTaskButton);
        tableGroup.add(tableControlPanel, BorderLayout.SOUTH);

        return tableGroup;
    }

    /** 任務列表面板的元件引用容器 */
    public static class TaskTableRefs {
        public JTable taskTable;
        public DefaultTableModel tableModel;
        public TableRowSorter<DefaultTableModel> rowSorter;
        public JButton selectAllTasksButton, clearTaskSelectionButton;
        public JButton executeNowButton, editTaskButton, reuseTaskButton;
        public JButton cancelTaskButton, deleteTaskButton;
    }

    /** 同一列放兩組：標籤 | 欄位 | 標籤 | 欄位 */
    private static void addDualFieldRow(JPanel panel, GridBagConstraints gbc, int row,
                                        JLabel leftLabel, JComponent leftField,
                                        JLabel rightLabel, JComponent rightField) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(4, 6, 4, 6);
        panel.add(leftLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.0;
        panel.add(leftField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(4, 18, 4, 6);
        panel.add(rightLabel, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(4, 6, 4, 6);
        panel.add(rightField, gbc);
    }

    private static JButton createAddTaskButton(Font boldFont) {
        JButton button = new JButton("➕ 新增單日任務");
        button.setFont(boldFont);
        button.setBackground(new Color(241, 245, 249));
        button.setForeground(new Color(30, 41, 59));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(createRaisedButtonBorder());
        Dimension pref = button.getPreferredSize();
        int width = Math.max(pref.width + 8, 156);
        Dimension size = new Dimension(width, 54);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setToolTipText("依上方設定新增一筆單日打卡任務");

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                button.setBorder(createLoweredButtonBorder());
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                button.setBorder(createRaisedButtonBorder());
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBorder(createRaisedButtonBorder());
            }
        });
        return button;
    }

    private static javax.swing.border.Border createRaisedButtonBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(
                        javax.swing.border.BevelBorder.RAISED,
                        new Color(255, 255, 255),
                        new Color(203, 213, 225),
                        new Color(148, 163, 184),
                        new Color(226, 232, 240)),
                new EmptyBorder(10, 18, 10, 18));
    }

    private static javax.swing.border.Border createLoweredButtonBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(
                        javax.swing.border.BevelBorder.LOWERED,
                        new Color(255, 255, 255),
                        new Color(203, 213, 225),
                        new Color(148, 163, 184),
                        new Color(226, 232, 240)),
                new EmptyBorder(10, 18, 10, 18));
    }

    private static void fixFieldWidth(JTextField field, int columns) {
        field.setColumns(columns);
        fixComponentWidth(field);
    }

    private static void fixComponentWidth(JComponent component) {
        Dimension pref = component.getPreferredSize();
        component.setMinimumSize(new Dimension(pref.width, pref.height));
        component.setPreferredSize(new Dimension(pref.width, pref.height));
    }

    /** 從「yyyy-MM-dd HH:mm:ss …」字串取出時間前綴以便排序 */
    private static String extractTimePrefix(String value) {
        if (value == null || value.isBlank()) return "";
        String trimmed = value.trim();
        // 取前 19 碼時間部分（若有）
        if (trimmed.length() >= 19 && trimmed.charAt(4) == '-' && trimmed.charAt(10) == ' ') {
            return trimmed.substring(0, 19);
        }
        return trimmed;
    }

    // ==================== 分組 4: 系統日誌 ====================

    /**
     * 建立系統日誌面板
     */
    public static JPanel createLogPanel(LogPanelRefs refs, Font boldFont) {
        JPanel logPanel = createGroupPanel("📜 系統日誌 (Console Log)", boldFont);
        logPanel.setLayout(new BorderLayout(0, 4));

        refs.logTextArea = new JTextArea();
        refs.logTextArea.setEditable(false);
        refs.logTextArea.setLineWrap(true);
        refs.logTextArea.setWrapStyleWord(true);
        refs.logTextArea.setBackground(new Color(15, 23, 42));
        refs.logTextArea.setForeground(new Color(56, 189, 248));
        refs.logTextArea.setCaretColor(Color.WHITE);
        refs.logTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        refs.logTextArea.setMargin(new Insets(6, 8, 6, 8));

        JScrollPane scrollPane = new JScrollPane(refs.logTextArea);
        scrollPane.setPreferredSize(new Dimension(780, 140));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        logPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel logActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        refs.clearLogButton = new JButton("🗑️ 清除 Log");
        refs.clearLogButton.setFont(boldFont);
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
        toggleLabel.setFont(new Font("微軟正黑體", Font.BOLD, 12));
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
