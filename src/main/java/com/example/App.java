package com.example;

import com.example.model.CheckInTask;
import com.example.service.AutomationService;
import com.example.service.HeartbeatService;
import com.example.service.SchedulerService;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 圖形介面主視窗 - 多任務與隨機時間打卡版
 */
public class App extends JFrame {
    private JTextArea logTextArea;
    private JButton addTaskButton;
    private JButton cancelTaskButton;
    private JButton deleteTaskButton;
    private JButton executeNowButton;
    private JButton cancelAllButton;
    private JButton deleteAllButton;
    private JButton clearLogButton;

    // Presets
    private JButton presetWorkInButton;
    private JButton presetWorkOutButton;
    private JButton presetTest1MinButton;
    private JButton presetTest3MinButton;

    private JTextField taskNameTextField;
    private JTextField urlTextField;
    private JTextField buttonIdTextField;
    private JTextField serverUrlTextField;
    private JComboBox<String> clientIdCombo;
    private JCheckBox enableServerCheckBox;
    private JCheckBox randomOffsetCheckBox;
    private JButton testServerButton;
    private JLabel heartbeatStatusLabel;

    private DatePicker datePicker;
    private JComboBox<String> hourCombo;
    private JComboBox<String> minuteCombo;
    private JComboBox<String> browserCombo;

    private JCheckBox monCheckBox;
    private JCheckBox tueCheckBox;
    private JCheckBox wedCheckBox;
    private JCheckBox thuCheckBox;
    private JCheckBox friCheckBox;
    private JCheckBox satCheckBox;
    private JCheckBox sunCheckBox;
    private JButton selectWorkdaysButton;
    private JButton clearWorkdaysButton;
    private JButton batchAddButton;

    private JTable taskTable;
    private DefaultTableModel tableModel;

    private final SchedulerService schedulerService;
    private final AutomationService automationService;
    private final HeartbeatService heartbeatService;

    public App() {
        this.schedulerService = new SchedulerService();
        this.automationService = new AutomationService();
        this.heartbeatService = new HeartbeatService();

        this.heartbeatService.setTasksProvider(schedulerService::getAllTasks);
        this.heartbeatService.setCommandListener(command -> {
            if ("CANCEL_SCHEDULE".equalsIgnoreCase(command)) {
                SwingUtilities.invokeLater(() -> {
                    schedulerService.cancelAllTasks();
                    refreshTaskTable();
                    appendLog("🛑 【遠端指令】收到網頁後台取消所有排程指令。");
                });
            } else if (command.startsWith("CANCEL_TASK:")) {
                String taskId = command.substring("CANCEL_TASK:".length()).trim();
                SwingUtilities.invokeLater(() -> {
                    schedulerService.cancelTask(taskId);
                    refreshTaskTable();
                    appendLog("🛑 【遠端指令】收到網頁後台取消任務 [" + taskId + "] 指令。");
                });
            }
        });

        // --- 1. UI 視窗基本設定 ---
        setTitle("圖形日曆多任務排程自動打卡控制台 (含隨機浮動打卡)");
        setSize(860, 840);
        setMinimumSize(new Dimension(820, 760));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        Font mainFont = new Font("微軟正黑體", Font.PLAIN, 13);
        Font boldFont = new Font("微軟正黑體", Font.BOLD, 13);

        JPanel mainContentPanel = new JPanel();
        mainContentPanel.setLayout(new BoxLayout(mainContentPanel, BoxLayout.Y_AXIS));
        mainContentPanel.setBorder(new EmptyBorder(10, 12, 6, 12));

        // ----------------------------------------------------
        // 分組 1: 🖥️ 雲端服務與裝置設定 (可折疊面板)
        // ----------------------------------------------------
        JPanel serverGroupBody = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel clientIdLabel = new JLabel("🆔 裝置 ID / Worker ID：");
        clientIdLabel.setFont(mainFont);
        serverGroupBody.add(clientIdLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; gbc.gridwidth = 2;
        String[] workerOptions = new String[] { "company-worker", "company-worker2", "company-worker3", "company-worker4" };
        clientIdCombo = new JComboBox<>(workerOptions);
        clientIdCombo.setFont(mainFont);
        serverGroupBody.add(clientIdCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel serverUrlLabel = new JLabel("📡 Server 雲端網址：");
        serverUrlLabel.setFont(mainFont);
        serverGroupBody.add(serverUrlLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        serverUrlTextField = new JTextField("http://localhost:3000");
        serverUrlTextField.setFont(mainFont);
        serverGroupBody.add(serverUrlTextField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; gbc.gridwidth = 1;
        enableServerCheckBox = new JCheckBox("啟用雲端單向狀態回報", false);
        enableServerCheckBox.setFont(boldFont);
        serverGroupBody.add(enableServerCheckBox, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0;
        heartbeatStatusLabel = new JLabel("⚪ 未連線 (已停用)", SwingConstants.LEFT);
        heartbeatStatusLabel.setFont(boldFont);
        heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
        serverGroupBody.add(heartbeatStatusLabel, gbc);

        gbc.gridx = 2; gbc.gridy = 2; gbc.weightx = 0.0;
        testServerButton = new JButton("🧪 測試 Server 連線");
        testServerButton.setFont(mainFont);
        testServerButton.addActionListener(e -> testServerConnection());
        serverGroupBody.add(testServerButton, gbc);

        JPanel serverGroupPanel = createCollapsibleGroupPanel("🖥️ 雲端服務與裝置設定", serverGroupBody, boldFont, false);
        mainContentPanel.add(serverGroupPanel);
        mainContentPanel.add(Box.createVerticalStrut(6));

        // ----------------------------------------------------
        // 分組 2: ⚙️ 快捷模板與任務設定 (可折疊面板)
        // ----------------------------------------------------
        JPanel taskGroupBody = new JPanel(new GridBagLayout());

        // Row 0: 快捷鍵按鈕專區
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel presetsLabel = new JLabel("⚡ 快捷一鍵帶入：");
        presetsLabel.setFont(boldFont);
        taskGroupBody.add(presetsLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; gbc.gridwidth = 2;
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        presetWorkInButton = new JButton("📅 預設上班 (09:00)");
        presetWorkOutButton = new JButton("📅 預設下班 (18:00)");
        presetTest1MinButton = new JButton("⚡ +1分測試 (精準)");
        presetTest3MinButton = new JButton("⚡ +3分測試 (精準)");

        presetWorkInButton.setFont(mainFont);
        presetWorkOutButton.setFont(mainFont);
        presetTest1MinButton.setFont(mainFont);
        presetTest3MinButton.setFont(mainFont);

        presetWorkInButton.setToolTipText("快速填入上班 09:00，並自動開啟前後 ±5 分鐘隨機時間");
        presetWorkOutButton.setToolTipText("快速填入下班 18:00，並自動開啟前後 ±5 分鐘隨機時間");
        presetTest1MinButton.setToolTipText("快速填入當前時間 +1 分鐘，並自動關閉隨機時間（精準打卡）");
        presetTest3MinButton.setToolTipText("快速填入當前時間 +3 分鐘，並自動關閉隨機時間（精準打卡）");

        presetPanel.add(presetWorkInButton);
        presetPanel.add(presetWorkOutButton);
        presetPanel.add(presetTest1MinButton);
        presetPanel.add(presetTest3MinButton);
        taskGroupBody.add(presetPanel, gbc);

        // Row 1: 任務名稱
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel nameLabel = new JLabel("📝 任務名稱：");
        nameLabel.setFont(mainFont);
        taskGroupBody.add(nameLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        taskNameTextField = new JTextField("上班打卡");
        taskNameTextField.setFont(mainFont);
        taskGroupBody.add(taskNameTextField, gbc);

        // Row 2: 打卡網址
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel urlLabel = new JLabel("🔗 目標打卡網址：");
        urlLabel.setFont(mainFont);
        taskGroupBody.add(urlLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0; gbc.gridwidth = 2;
        urlTextField = new JTextField("https://tw.yahoo.com");
        urlTextField.setFont(mainFont);
        taskGroupBody.add(urlTextField, gbc);

        // Row 3: 按鈕 ID
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel buttonIdLabel = new JLabel("🔘 打卡按鈕 Selector：");
        buttonIdLabel.setFont(mainFont);
        taskGroupBody.add(buttonIdLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0; gbc.gridwidth = 2;
        buttonIdTextField = new JTextField("check_in");
        buttonIdTextField.setFont(mainFont);
        taskGroupBody.add(buttonIdTextField, gbc);

        // Row 4: 排程時間與隨機浮動
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel timeLabel = new JLabel("📆 預定打卡時間：");
        timeLabel.setFont(mainFont);
        taskGroupBody.add(timeLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 1.0; gbc.gridwidth = 2;
        JPanel timeSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        DatePickerSettings dateSettings = new DatePickerSettings();
        dateSettings.setAllowKeyboardEditing(true);
        datePicker = new DatePicker(dateSettings);
        datePicker.setDateToToday();
        dateSettings.setDateRangeLimits(LocalDate.now(), LocalDate.MAX);
        JButton toggleBtn = datePicker.getComponentToggleCalendarButton();
        toggleBtn.setPreferredSize(new Dimension(0, 0));
        toggleBtn.setBorder(null);

        datePicker.getComponentDateTextField().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                SwingUtilities.invokeLater(() -> datePicker.openPopup());
            }
        });
        timeSelectionPanel.add(datePicker);

        LocalDateTime now = LocalDateTime.now();
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format("%02d", i);
        hourCombo = new JComboBox<>(hours);
        hourCombo.setFont(mainFont);
        hourCombo.setSelectedIndex(now.getHour());
        timeSelectionPanel.add(hourCombo);
        timeSelectionPanel.add(new JLabel("時"));

        String[] minutes = new String[60];
        for (int i = 0; i < 60; i++) minutes[i] = String.format("%02d", i);
        minuteCombo = new JComboBox<>(minutes);
        minuteCombo.setFont(mainFont);
        minuteCombo.setSelectedIndex(now.getMinute());
        timeSelectionPanel.add(minuteCombo);
        timeSelectionPanel.add(new JLabel("分"));

        randomOffsetCheckBox = new JCheckBox("🎲 啟用前後 ±5 分鐘隨機打卡", true);
        randomOffsetCheckBox.setFont(boldFont);
        randomOffsetCheckBox.setForeground(new Color(147, 51, 234));
        timeSelectionPanel.add(randomOffsetCheckBox);

        taskGroupBody.add(timeSelectionPanel, gbc);

        // Row 5: 批量星期選擇
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel weekdayLabel = new JLabel("🗓️ 批量星期選擇：");
        weekdayLabel.setFont(mainFont);
        taskGroupBody.add(weekdayLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 1.0; gbc.gridwidth = 2;
        JPanel weekdayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        monCheckBox = new JCheckBox("週一", true);
        tueCheckBox = new JCheckBox("週二", true);
        wedCheckBox = new JCheckBox("週三", true);
        thuCheckBox = new JCheckBox("週四", true);
        friCheckBox = new JCheckBox("週五", true);
        satCheckBox = new JCheckBox("週六", false);
        sunCheckBox = new JCheckBox("週日", false);

        monCheckBox.setFont(boldFont);
        tueCheckBox.setFont(boldFont);
        wedCheckBox.setFont(boldFont);
        thuCheckBox.setFont(boldFont);
        friCheckBox.setFont(boldFont);
        satCheckBox.setFont(mainFont);
        sunCheckBox.setFont(mainFont);

        selectWorkdaysButton = new JButton("全選週一~週五");
        clearWorkdaysButton = new JButton("清除選取");
        selectWorkdaysButton.setFont(mainFont);
        clearWorkdaysButton.setFont(mainFont);

        selectWorkdaysButton.addActionListener(e -> setWorkdaysSelected(true));
        clearWorkdaysButton.addActionListener(e -> setWorkdaysSelected(false));

        weekdayPanel.add(monCheckBox);
        weekdayPanel.add(tueCheckBox);
        weekdayPanel.add(wedCheckBox);
        weekdayPanel.add(thuCheckBox);
        weekdayPanel.add(friCheckBox);
        weekdayPanel.add(satCheckBox);
        weekdayPanel.add(sunCheckBox);
        weekdayPanel.add(Box.createHorizontalStrut(6));
        weekdayPanel.add(selectWorkdaysButton);
        weekdayPanel.add(clearWorkdaysButton);

        taskGroupBody.add(weekdayPanel, gbc);

        // Row 6: 瀏覽器與新增按鈕
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JLabel browserLabel = new JLabel("🌐 執行瀏覽器：");
        browserLabel.setFont(mainFont);
        taskGroupBody.add(browserLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 6; gbc.weightx = 1.0; gbc.gridwidth = 1;
        browserCombo = new JComboBox<>(new String[]{
                "Microsoft Edge (本機已安裝)",
                "Google Chrome (本機已安裝)",
                "內建 Chromium 瀏覽器",
                "內建 Firefox 瀏覽器",
                "內建 WebKit (Safari核心)"
        });
        browserCombo.setFont(mainFont);
        taskGroupBody.add(browserCombo, gbc);

        gbc.gridx = 2; gbc.gridy = 6; gbc.weightx = 0.0; gbc.gridwidth = 1;
        JPanel actionButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        addTaskButton = new JButton("➕ 新增單日任務");
        batchAddButton = new JButton("🗓️ 批量排定 (週一~週五)");

        addTaskButton.setFont(boldFont);
        batchAddButton.setFont(boldFont);

        addTaskButton.setPreferredSize(new Dimension(130, 32));
        batchAddButton.setPreferredSize(new Dimension(170, 32));

        batchAddButton.setBackground(new Color(16, 185, 129));
        batchAddButton.setForeground(Color.BLACK);

        actionButtonPanel.add(addTaskButton);
        actionButtonPanel.add(batchAddButton);
        taskGroupBody.add(actionButtonPanel, gbc);

        JPanel taskGroupPanel = createCollapsibleGroupPanel("⚙️ 打卡任務設定與快捷模板", taskGroupBody, boldFont, false);
        mainContentPanel.add(taskGroupPanel);
        mainContentPanel.add(Box.createVerticalStrut(6));

        // ----------------------------------------------------
        // 分組 3: 📋 打卡任務列表 JTable 區域
        // ----------------------------------------------------
        JPanel tableGroup = createGroupPanel("📋 排定打卡任務列表 (Task Schedule Table)", boldFont);
        tableGroup.setLayout(new BorderLayout(0, 6));

        String[] columnNames = {"ID", "任務名稱", "預定時間", "實際觸發 (隨機)", "網址", "瀏覽器", "狀態", "訊息/結果"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        taskTable = new JTable(tableModel);
        taskTable.setFont(mainFont);
        taskTable.setRowHeight(24);
        taskTable.getTableHeader().setFont(boldFont);
        taskTable.getTableHeader().setBackground(new Color(241, 245, 249));
        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 欄位寬度與對齊設定
        taskTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        taskTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        taskTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        taskTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        taskTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        taskTable.getColumnModel().getColumn(6).setPreferredWidth(90);
        taskTable.getColumnModel().getColumn(7).setPreferredWidth(150);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        taskTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        taskTable.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);

        JScrollPane tableScrollPane = new JScrollPane(taskTable);
        tableScrollPane.setPreferredSize(new Dimension(780, 110));
        tableGroup.add(tableScrollPane, BorderLayout.CENTER);

        // 下方任務操作按鈕列
        JPanel tableControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        executeNowButton = new JButton("⚡ 立即執行選擇任務");
        cancelTaskButton = new JButton("🛑 取消選擇任務");
        deleteTaskButton = new JButton("🗑️ 刪除選擇任務");
        cancelAllButton = new JButton("🛑 全部取消");
        deleteAllButton = new JButton("🗑️ 全部刪除");

        executeNowButton.setFont(boldFont);
        cancelTaskButton.setFont(boldFont);
        deleteTaskButton.setFont(boldFont);
        cancelAllButton.setFont(boldFont);
        deleteAllButton.setFont(boldFont);

        deleteAllButton.setForeground(new Color(225, 29, 72)); // 醒目紅色標示

        tableControlPanel.add(executeNowButton);
        tableControlPanel.add(cancelTaskButton);
        tableControlPanel.add(deleteTaskButton);
        tableControlPanel.add(cancelAllButton);
        tableControlPanel.add(deleteAllButton);
        tableGroup.add(tableControlPanel, BorderLayout.SOUTH);

        mainContentPanel.add(tableGroup);
        mainContentPanel.add(Box.createVerticalStrut(6));

        add(mainContentPanel, BorderLayout.NORTH);

        // ----------------------------------------------------
        // 分組 4: 📜 系統 Console Log 區域
        // ----------------------------------------------------
        JPanel logPanel = createGroupPanel("📜 系統日誌 (Console Log)", boldFont);
        logPanel.setLayout(new BorderLayout(0, 4));

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(true);
        logTextArea.setWrapStyleWord(true);
        logTextArea.setBackground(new Color(15, 23, 42));
        logTextArea.setForeground(new Color(56, 189, 248));
        logTextArea.setCaretColor(Color.WHITE);
        logTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTextArea.setMargin(new Insets(6, 8, 6, 8));

        JScrollPane scrollPane = new JScrollPane(logTextArea);
        scrollPane.setPreferredSize(new Dimension(780, 180));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        logPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel logActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        clearLogButton = new JButton("🗑️ 清除 Log");
        clearLogButton.setFont(boldFont);
        clearLogButton.addActionListener(e -> logTextArea.setText(""));
        logActionPanel.add(clearLogButton);
        logPanel.add(logActionPanel, BorderLayout.SOUTH);

        add(logPanel, BorderLayout.CENTER);

        // ----------------------------------------------------
        // 事件處理與快捷鍵監聽
        // ----------------------------------------------------
        presetWorkInButton.addActionListener(e -> applyPreset("上班打卡", 9, 0, true));
        presetWorkOutButton.addActionListener(e -> applyPreset("下班打卡", 18, 0, true));
        presetTest1MinButton.addActionListener(e -> applyTestPreset(1));
        presetTest3MinButton.addActionListener(e -> applyTestPreset(3));

        addTaskButton.addActionListener(e -> addNewTaskFromForm());
        batchAddButton.addActionListener(e -> addBatchTasksFromForm());
        cancelTaskButton.addActionListener(e -> cancelSelectedTask());
        deleteTaskButton.addActionListener(e -> deleteSelectedTask());
        executeNowButton.addActionListener(e -> executeSelectedTaskNow());
        cancelAllButton.addActionListener(e -> {
            schedulerService.cancelAllTasks();
            refreshTaskTable();
            heartbeatService.sendHeartbeat(null, null);
            appendLog("🛑 已取消所有排定之打卡任務。");
        });
        deleteAllButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "確定要清空並刪除列表中【所有】打卡任務嗎？",
                    "刪除全部確認",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                schedulerService.removeAllTasks();
                refreshTaskTable();
                heartbeatService.sendHeartbeat(null, null);
                appendLog("🗑️ 已成功刪除並清空所有打卡任務紀錄。");
            }
        });

        enableServerCheckBox.addActionListener(e -> {
            boolean enabled = enableServerCheckBox.isSelected();
            if (enabled) {
                appendLog("🟢 已勾選啟用雲端狀態回報，啟動單向心跳中...");
                startHeartbeatService();
            } else {
                appendLog("🔴 已取消勾選，斷開雲端狀態回報（本機獨立運作模式）。");
                heartbeatService.stopHeartbeat();
                heartbeatStatusLabel.setText("⚪ 未連線 (已停用)");
                heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
            }
        });

        clientIdCombo.addActionListener(e -> {
            String selected = (String) clientIdCombo.getSelectedItem();
            if (selected != null && !selected.trim().isEmpty()) {
                heartbeatService.setClientId(selected.trim());
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                heartbeatService.stopHeartbeat();
                schedulerService.shutdown();
            }
        });

        startHeartbeatService();
    }

    private void setWorkdaysSelected(boolean select) {
        monCheckBox.setSelected(select);
        tueCheckBox.setSelected(select);
        wedCheckBox.setSelected(select);
        thuCheckBox.setSelected(select);
        friCheckBox.setSelected(select);
        satCheckBox.setSelected(false);
        sunCheckBox.setSelected(false);
    }

    private void applyPreset(String taskName, int targetHour, int targetMin, boolean useRandom) {
        taskNameTextField.setText(taskName);
        datePicker.setDateToToday();
        hourCombo.setSelectedIndex(targetHour);
        minuteCombo.setSelectedIndex(targetMin);
        randomOffsetCheckBox.setSelected(useRandom);
        setWorkdaysSelected(true);
        appendLog(String.format("💡 已載入預設模板【%s】(時間 %02d:%02d, 自動勾選週一~週五, 隨機浮動: %s)", taskName, targetHour, targetMin, useRandom ? "開啟" : "關閉"));
    }

    private void applyTestPreset(int minutesFromNow) {
        LocalDateTime testTime = LocalDateTime.now().plusMinutes(minutesFromNow);
        taskNameTextField.setText("⚡ 測試打卡 (+" + minutesFromNow + "分)");
        datePicker.setDate(testTime.toLocalDate());
        hourCombo.setSelectedIndex(testTime.getHour());
        minuteCombo.setSelectedIndex(testTime.getMinute());
        randomOffsetCheckBox.setSelected(false); // 測試預設不啟用隨機時間
        appendLog(String.format("⚡ 已載入測試快捷：當前時間 +%d 分鐘 (%02d:%02d)，自動關閉隨機時間以利精準測試！",
                minutesFromNow, testTime.getHour(), testTime.getMinute()));
    }

    private void addBatchTasksFromForm() {
        String name = taskNameTextField.getText().trim();
        if (name.isEmpty()) name = "打卡任務";

        String targetUrl = urlTextField.getText().trim();
        if (targetUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入目標打卡網址！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String buttonId = buttonIdTextField.getText().trim();
        if (buttonId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入打卡按鈕 Selector！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int hour = Integer.parseInt((String) hourCombo.getSelectedItem());
        int minute = Integer.parseInt((String) minuteCombo.getSelectedItem());
        boolean useRandom = randomOffsetCheckBox.isSelected();
        String selectedBrowserStr = (String) browserCombo.getSelectedItem();
        String browserType = parseBrowserType(selectedBrowserStr);

        List<DayOfWeek> selectedDays = new ArrayList<>();
        if (monCheckBox.isSelected()) selectedDays.add(DayOfWeek.MONDAY);
        if (tueCheckBox.isSelected()) selectedDays.add(DayOfWeek.TUESDAY);
        if (wedCheckBox.isSelected()) selectedDays.add(DayOfWeek.WEDNESDAY);
        if (thuCheckBox.isSelected()) selectedDays.add(DayOfWeek.THURSDAY);
        if (friCheckBox.isSelected()) selectedDays.add(DayOfWeek.FRIDAY);
        if (satCheckBox.isSelected()) selectedDays.add(DayOfWeek.SATURDAY);
        if (sunCheckBox.isSelected()) selectedDays.add(DayOfWeek.SUNDAY);

        if (selectedDays.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請先勾選至少一個星期（例如 週一~週五）！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int addedCount = 0;

        for (DayOfWeek dayOfWeek : selectedDays) {
            LocalDate targetDate = today;
            while (targetDate.getDayOfWeek() != dayOfWeek) {
                targetDate = targetDate.plusDays(1);
            }

            LocalDateTime targetTime = targetDate.atTime(hour, minute, 0);
            if (targetTime.isBefore(now.plusSeconds(5))) {
                targetDate = targetDate.plusWeeks(1);
                targetTime = targetDate.atTime(hour, minute, 0);
            }

            String dayName = getDayOfWeekName(dayOfWeek);
            String taskFullName = name + " (" + dayName + ")";

            CheckInTask task = new CheckInTask(taskFullName, targetUrl, buttonId, targetTime, useRandom, browserType);
            boolean scheduled = schedulerService.scheduleTask(task,
                    t -> SwingUtilities.invokeLater(this::refreshTaskTable),
                    this::appendLog,
                    this::executeCheckInForTask);
            if (scheduled) {
                addedCount++;
            }
        }

        if (addedCount > 0) {
            refreshTaskTable();
            heartbeatService.sendHeartbeat(this::appendLog, null);
            appendLog("🗓️ 【批量排定】成功一次排定 " + addedCount + " 個工作日打卡任務 (含隨機時間浮動)！");
            JOptionPane.showMessageDialog(this, "成功一次排定 " + addedCount + " 個星期的打卡任務！", "批量成功", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "無法排定任務，可能是選擇的時間已過！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private String getDayOfWeekName(DayOfWeek day) {
        switch (day) {
            case MONDAY: return "週一";
            case TUESDAY: return "週二";
            case WEDNESDAY: return "週三";
            case THURSDAY: return "週四";
            case FRIDAY: return "週五";
            case SATURDAY: return "週六";
            case SUNDAY: return "週日";
            default: return "";
        }
    }

    private void addNewTaskFromForm() {
        String name = taskNameTextField.getText().trim();
        if (name.isEmpty()) name = "打卡任務";

        String targetUrl = urlTextField.getText().trim();
        if (targetUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入目標打卡網址！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String buttonId = buttonIdTextField.getText().trim();
        if (buttonId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入打卡按鈕 Selector！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate selectedDate = datePicker.getDate();
        if (selectedDate == null) {
            JOptionPane.showMessageDialog(this, "請選擇有效的日期！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int hour = Integer.parseInt((String) hourCombo.getSelectedItem());
        int minute = Integer.parseInt((String) minuteCombo.getSelectedItem());
        LocalDateTime targetTime = selectedDate.atTime(hour, minute, 0);

        LocalDateTime now = LocalDateTime.now();
        // 防護機制：若是測試任務且設定時間已小於或等於當前時間，自動延後 1 分鐘避免剛建立即過期
        if (targetTime.isBefore(now.plusSeconds(5))) {
            targetTime = now.plusMinutes(1).withSecond(0).withNano(0);
            hourCombo.setSelectedIndex(targetTime.getHour());
            minuteCombo.setSelectedIndex(targetTime.getMinute());
        }

        boolean useRandom = randomOffsetCheckBox.isSelected();

        String selectedBrowserStr = (String) browserCombo.getSelectedItem();
        String browserType = parseBrowserType(selectedBrowserStr);

        CheckInTask task = new CheckInTask(name, targetUrl, buttonId, targetTime, useRandom, browserType);

        boolean scheduled = schedulerService.scheduleTask(task,
                t -> SwingUtilities.invokeLater(this::refreshTaskTable),
                this::appendLog,
                this::executeCheckInForTask);

        if (!scheduled) {
            JOptionPane.showMessageDialog(this, "無法排定任務，可能是選擇的時間與隨機偏移已屬於過去！", "時間錯誤", JOptionPane.ERROR_MESSAGE);
        } else {
            refreshTaskTable();
            heartbeatService.sendHeartbeat(this::appendLog, null);
        }
    }

    private void executeCheckInForTask(CheckInTask task, Runnable onComplete) {
        LocalDateTime triggerTime = LocalDateTime.now();
        String triggerTimeStr = triggerTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        long startTimeMs = System.currentTimeMillis();

        try {
            boolean ok = automationService.executeCheckIn(task.getTargetUrl(), task.getButtonId(), task.getBrowserType(), this::appendLog);
            long durationMs = System.currentTimeMillis() - startTimeMs;
            double durationSec = durationMs / 1000.0;
            String finishTimeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            if (ok) {
                String msg = String.format("✅ 打卡成功！(觸發: %s, 完成: %s, 耗時: %.1f秒)", triggerTimeStr, finishTimeStr, durationSec);
                task.setStatus("SUCCESS");
                task.setResultMessage(msg);
                appendLog("🎉 【" + task.getName() + "】" + msg);
            } else {
                String msg = String.format("❌ 打卡失敗 (觸發: %s, 耗時: %.1f秒)", triggerTimeStr, durationSec);
                task.setStatus("FAILED");
                task.setResultMessage(msg);
                appendLog("❌ 【" + task.getName() + "】" + msg);
            }
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTimeMs;
            double durationSec = durationMs / 1000.0;
            String cleanMsg = sanitizeErrorMessage(ex.getMessage());
            String msg = String.format("❌ 打卡失敗：%s (觸發: %s, 耗時: %.1f秒)", cleanMsg, triggerTimeStr, durationSec);
            task.setStatus("FAILED");
            task.setResultMessage(msg);
            appendLog("❌ 【" + task.getName() + "】" + msg);
        } finally {
            refreshTaskTable();
            heartbeatService.sendHeartbeat(this::appendLog, null);
            if (onComplete != null) onComplete.run();
        }
    }

    private void cancelSelectedTask() {
        int selectedRow = taskTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "請先選取列表中要取消的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskId = (String) tableModel.getValueAt(selectedRow, 0);
        boolean ok = schedulerService.cancelTask(taskId);
        refreshTaskTable();
        heartbeatService.sendHeartbeat(null, null);
        if (ok) {
            appendLog("🛑 已取消任務 ID [" + taskId + "]");
        } else {
            appendLog("⚠️ 任務 ID [" + taskId + "] 不存在或已結束");
        }
    }

    private void deleteSelectedTask() {
        int selectedRow = taskTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "請先選取列表中要刪除的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskId = (String) tableModel.getValueAt(selectedRow, 0);
        schedulerService.removeTask(taskId);
        refreshTaskTable();
        heartbeatService.sendHeartbeat(null, null);
        appendLog("🗑️ 已移除任務紀錄 ID [" + taskId + "]");
    }

    private void executeSelectedTaskNow() {
        int selectedRow = taskTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "請先選取列表中要立即執行的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskId = (String) tableModel.getValueAt(selectedRow, 0);
        CheckInTask task = schedulerService.getTask(taskId);
        if (task != null) {
            appendLog("⚡ 【立即執行】觸發任務【" + task.getName() + "】進行打卡...");
            new Thread(() -> executeCheckInForTask(task, null)).start();
        }
    }

    private void refreshTaskTable() {
        SwingUtilities.invokeLater(() -> {
            int selectedRow = taskTable.getSelectedRow();
            String selectedTaskId = (selectedRow >= 0 && selectedRow < tableModel.getRowCount())
                    ? (String) tableModel.getValueAt(selectedRow, 0)
                    : null;

            tableModel.setRowCount(0);
            List<CheckInTask> tasks = schedulerService.getAllTasks();
            int restoreRow = -1;

            for (int i = 0; i < tasks.size(); i++) {
                CheckInTask t = tasks.get(i);
                if (selectedTaskId != null && selectedTaskId.equals(t.getId())) {
                    restoreRow = i;
                }
                String statusStr = parseStatusBadge(t.getStatus());
                String offsetStr = t.isUseRandomOffset()
                        ? String.format("%s (%s%ds)", t.getFormattedActualTime(), t.getRandomOffsetSeconds() >= 0 ? "+" : "", t.getRandomOffsetSeconds())
                        : t.getFormattedActualTime() + " (精準)";

                tableModel.addRow(new Object[]{
                        t.getId(),
                        t.getName(),
                        t.getFormattedTargetTime(),
                        offsetStr,
                        t.getTargetUrl(),
                        t.getBrowserType(),
                        statusStr,
                        t.getResultMessage()
                });
            }

            if (restoreRow >= 0 && restoreRow < taskTable.getRowCount()) {
                taskTable.setRowSelectionInterval(restoreRow, restoreRow);
            }
        });
    }

    private String parseStatusBadge(String status) {
        if ("SCHEDULED".equals(status)) return "⏳ 等待中";
        if ("CHECKING_IN".equals(status)) return "🚀 執行中";
        if ("SUCCESS".equals(status)) return "✅ 成功";
        if ("FAILED".equals(status)) return "❌ 失敗";
        if ("CANCELLED".equals(status)) return "🛑 取消";
        return status;
    }

    private String parseBrowserType(String selectedBrowserStr) {
        if (selectedBrowserStr == null) return "msedge";
        if (selectedBrowserStr.contains("Chrome")) return "chrome";
        if (selectedBrowserStr.contains("Edge")) return "msedge";
        if (selectedBrowserStr.contains("Firefox")) return "firefox";
        if (selectedBrowserStr.contains("WebKit")) return "webkit";
        if (selectedBrowserStr.contains("Chromium")) return "chromium";
        return "msedge";
    }

    private JPanel createCollapsibleGroupPanel(String title, JPanel contentPanel, Font titleFont, boolean startCollapsed) {
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

    private JPanel createGroupPanel(String title, Font titleFont) {
        JPanel panel = new JPanel();
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                titleFont,
                new Color(30, 41, 59));
        panel.setBorder(new CompoundBorder(titledBorder, new EmptyBorder(4, 8, 6, 8)));
        return panel;
    }

    private void startHeartbeatService() {
        if (enableServerCheckBox != null && !enableServerCheckBox.isSelected()) return;
        String serverUrl = serverUrlTextField.getText().trim();
        if (!serverUrl.isEmpty()) {
            heartbeatService.startHeartbeat(serverUrl, this::appendLog, isOk -> {
                SwingUtilities.invokeLater(() -> {
                    if (enableServerCheckBox != null && !enableServerCheckBox.isSelected()) {
                        heartbeatStatusLabel.setText("⚪ 未連線 (已停用)");
                        heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
                        return;
                    }
                    if (isOk) {
                        heartbeatStatusLabel.setText("💚 HTTP POST 正常");
                        heartbeatStatusLabel.setForeground(new Color(34, 197, 94));
                    } else {
                        heartbeatStatusLabel.setText("🔴 HTTP POST 異常");
                        heartbeatStatusLabel.setForeground(new Color(239, 68, 68));
                    }
                });
            });
        }
    }

    private void testServerConnection() {
        String serverUrl = serverUrlTextField.getText().trim();
        if (serverUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請先輸入有效的 ping-pong-server 網址！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        heartbeatService.testConnection(serverUrl, this::appendLog, isOk -> {
            SwingUtilities.invokeLater(() -> {
                if (isOk) {
                    heartbeatStatusLabel.setText("💚 HTTP POST 正常");
                    heartbeatStatusLabel.setForeground(new Color(34, 197, 94));
                    JOptionPane.showMessageDialog(this, "✅ 成功連線至 ping-pong-server！", "測試成功", JOptionPane.INFORMATION_MESSAGE);
                    startHeartbeatService();
                } else {
                    heartbeatStatusLabel.setText("🔴 HTTP POST 異常");
                    heartbeatStatusLabel.setForeground(new Color(239, 68, 68));
                    JOptionPane.showMessageDialog(this, "❌ 無法連線至指定 Server，請確認網址或 Server 狀態！", "測試失敗", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
    }

    private void appendLog(String message) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        String logMessage = "[" + timestamp + "] " + message;

        SwingUtilities.invokeLater(() -> {
            logTextArea.append(logMessage + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
        System.out.println(logMessage);
    }

    private String sanitizeErrorMessage(String rawMsg) {
        if (rawMsg == null) return "打卡異常";
        String sanitized = rawMsg.replaceAll("https?://[^\\s\"'>]+", "[隱私保護網址]");
        if (sanitized.contains("Timeout") && sanitized.contains("exceeded")) {
            return "打卡失敗：網頁連線或按鈕點擊逾時 (Timeout 30s)";
        }
        int callLogIdx = sanitized.indexOf("Call log:");
        if (callLogIdx != -1) {
            sanitized = sanitized.substring(0, callLogIdx).trim();
        }
        if (sanitized.length() > 60) {
            sanitized = sanitized.substring(0, 60) + "...";
        }
        return sanitized;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            App app = new App();
            app.setVisible(true);
        });
    }
}
