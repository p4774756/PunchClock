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
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: 任務名稱
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel nameLabel = new JLabel("📝 任務名稱：");
        nameLabel.setFont(mainFont);
        panel.add(nameLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; gbc.gridwidth = 2;
        refs.taskNameTextField = new JTextField("上班打卡");
        refs.taskNameTextField.setFont(mainFont);
        panel.add(refs.taskNameTextField, gbc);

        // Row 1: 打卡網址
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel urlLabel = new JLabel("🔗 目標打卡網址：");
        urlLabel.setFont(mainFont);
        panel.add(urlLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        refs.urlTextField = new JTextField("https://tw.yahoo.com");
        refs.urlTextField.setFont(mainFont);
        panel.add(refs.urlTextField, gbc);

        // Row 2: 按鈕 Selector
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel buttonIdLabel = new JLabel("🔘 打卡按鈕 Selector：");
        buttonIdLabel.setFont(mainFont);
        panel.add(buttonIdLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0; gbc.gridwidth = 2;
        refs.buttonIdTextField = new JTextField("check_in");
        refs.buttonIdTextField.setFont(mainFont);
        panel.add(refs.buttonIdTextField, gbc);

        // Row 3: 排程時間 + 快捷帶入（同一列）
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel timeLabel = new JLabel("📆 預定打卡時間：");
        timeLabel.setFont(mainFont);
        panel.add(timeLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0; gbc.gridwidth = 2;
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

        timeSelectionPanel.add(Box.createHorizontalStrut(4));
        JSeparator timeSep = new JSeparator(SwingConstants.VERTICAL);
        timeSep.setPreferredSize(new Dimension(8, 22));
        timeSelectionPanel.add(timeSep);

        refs.presetWorkInButton = new JButton("上班 09:00");
        refs.presetWorkOutButton = new JButton("下班 18:00");
        refs.presetTest1MinButton = new JButton("+1分");
        refs.presetTest3MinButton = new JButton("+3分");
        for (JButton btn : new JButton[]{refs.presetWorkInButton, refs.presetWorkOutButton,
                refs.presetTest1MinButton, refs.presetTest3MinButton}) {
            btn.setFont(mainFont);
        }
        refs.presetWorkInButton.setToolTipText("快速填入上班 09:00，並開啟 ±5 分鐘隨機");
        refs.presetWorkOutButton.setToolTipText("快速填入下班 18:00，並開啟 ±5 分鐘隨機");
        refs.presetTest1MinButton.setToolTipText("當前時間 +1 分鐘，關閉隨機（精準）");
        refs.presetTest3MinButton.setToolTipText("當前時間 +3 分鐘，關閉隨機（精準）");
        timeSelectionPanel.add(refs.presetWorkInButton);
        timeSelectionPanel.add(refs.presetWorkOutButton);
        timeSelectionPanel.add(refs.presetTest1MinButton);
        timeSelectionPanel.add(refs.presetTest3MinButton);

        panel.add(timeSelectionPanel, gbc);

        // Row 4: 批量星期選擇
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel weekdayLabel = new JLabel("🗓️ 批量星期選擇：");
        weekdayLabel.setFont(mainFont);
        panel.add(weekdayLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 1.0; gbc.gridwidth = 2;
        JPanel weekdayPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4));
        refs.monCheckBox = new JCheckBox("週一", true);
        refs.tueCheckBox = new JCheckBox("週二", true);
        refs.wedCheckBox = new JCheckBox("週三", true);
        refs.thuCheckBox = new JCheckBox("週四", true);
        refs.friCheckBox = new JCheckBox("週五", true);
        refs.satCheckBox = new JCheckBox("週六", false);
        refs.sunCheckBox = new JCheckBox("週日", false);

        JCheckBox[] weekdayBoxes = {refs.monCheckBox, refs.tueCheckBox, refs.wedCheckBox,
                refs.thuCheckBox, refs.friCheckBox};
        for (JCheckBox cb : weekdayBoxes) cb.setFont(boldFont);
        refs.satCheckBox.setFont(mainFont);
        refs.sunCheckBox.setFont(mainFont);

        refs.selectWorkdaysButton = new JButton("全選週一~週五");
        refs.clearWorkdaysButton = new JButton("清除選取");
        refs.selectWorkdaysButton.setFont(mainFont);
        refs.clearWorkdaysButton.setFont(mainFont);

        weekdayPanel.add(refs.monCheckBox);
        weekdayPanel.add(refs.tueCheckBox);
        weekdayPanel.add(refs.wedCheckBox);
        weekdayPanel.add(refs.thuCheckBox);
        weekdayPanel.add(refs.friCheckBox);
        weekdayPanel.add(refs.satCheckBox);
        weekdayPanel.add(refs.sunCheckBox);
        weekdayPanel.add(Box.createHorizontalStrut(6));
        weekdayPanel.add(refs.selectWorkdaysButton);
        weekdayPanel.add(refs.clearWorkdaysButton);
        panel.add(weekdayPanel, gbc);

        // Row 5: 瀏覽器（佔滿剩餘寬度，避免把右側按鈕擠出框外）
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel browserLabel = new JLabel("🌐 執行瀏覽器：");
        browserLabel.setFont(mainFont);
        panel.add(browserLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 1.0; gbc.gridwidth = 2;
        refs.browserCombo = new JComboBox<>(new String[]{
                "Microsoft Edge (本機已安裝)", "Google Chrome (本機已安裝)",
                "內建 Chromium 瀏覽器", "內建 Firefox 瀏覽器", "內建 WebKit (Safari核心)"
        });
        refs.browserCombo.setFont(mainFont);
        panel.add(refs.browserCombo, gbc);

        // Row 6: 新增／批量按鈕獨立一列，靠右對齊
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 1.0; gbc.gridwidth = 3;
        JPanel actionButtonPanel = new JPanel(new WrapLayout(FlowLayout.RIGHT, 6, 4));
        refs.addTaskButton = new JButton("➕ 新增單日任務");
        refs.batchAddButton = new JButton("🗓️ 批量排定 (週一~週五)");
        refs.addTaskButton.setFont(boldFont);
        refs.batchAddButton.setFont(boldFont);
        refs.batchAddButton.setBackground(new Color(16, 185, 129));
        refs.batchAddButton.setForeground(Color.BLACK);
        actionButtonPanel.add(refs.addTaskButton);
        actionButtonPanel.add(refs.batchAddButton);
        panel.add(actionButtonPanel, gbc);

        return panel;
    }

    /** 任務設定表單的元件引用容器 */
    public static class TaskFormRefs {
        public JButton presetWorkInButton, presetWorkOutButton, presetTest1MinButton, presetTest3MinButton;
        public JTextField taskNameTextField, urlTextField, buttonIdTextField;
        public DatePicker datePicker;
        public JComboBox<String> hourCombo, minuteCombo, browserCombo;
        public JCheckBox randomOffsetCheckBox;
        public JCheckBox monCheckBox, tueCheckBox, wedCheckBox, thuCheckBox, friCheckBox, satCheckBox, sunCheckBox;
        public JButton selectWorkdaysButton, clearWorkdaysButton;
        public JButton addTaskButton, batchAddButton;
    }

    // ==================== 分組 3: 任務列表 ====================

    /**
     * 建立任務列表面板
     */
    public static JPanel createTaskTablePanel(TaskTableRefs refs, Font mainFont, Font boldFont) {
        JPanel tableGroup = createGroupPanel("📋 排定打卡任務列表（點欄位標題可排序）", boldFont);
        tableGroup.setLayout(new BorderLayout(0, 6));

        String[] columnNames = {"ID", "任務名稱", "預定時間", "實際觸發 (隨機)", "網址", "瀏覽器", "狀態", "訊息/結果"};
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

        refs.taskTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        refs.taskTable.setFillsViewportHeight(true);

        int[] widths = {80, 120, 160, 170, 240, 110, 80, 200};
        for (int i = 0; i < widths.length; i++) {
            refs.taskTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        refs.taskTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        refs.taskTable.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);

        JScrollPane tableScrollPane = new JScrollPane(refs.taskTable);
        tableScrollPane.setPreferredSize(new Dimension(1180, 180));
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
