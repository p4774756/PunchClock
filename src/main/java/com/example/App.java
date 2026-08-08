package com.example;

import com.example.service.AutomationService;
import com.example.service.HeartbeatService;
import com.example.service.SchedulerService;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 圖形介面主視窗 - 整理優化版
 */
public class App extends JFrame {
    private JTextArea logTextArea;
    private JButton startScheduleButton;
    private JButton cancelScheduleButton;
    private JButton clearLogButton;

    private JTextField urlTextField;
    private JTextField buttonIdTextField;
    private JTextField serverUrlTextField;
    private JComboBox<String> clientIdCombo;
    private JCheckBox enableServerCheckBox;
    private JButton testServerButton;
    private JLabel heartbeatStatusLabel;

    private DatePicker datePicker;
    private JComboBox<String> hourCombo;
    private JComboBox<String> minuteCombo;

    private final SchedulerService schedulerService;
    private final AutomationService automationService;
    private final HeartbeatService heartbeatService;

    public App() {
        this.schedulerService = new SchedulerService();
        this.automationService = new AutomationService();
        this.heartbeatService = new HeartbeatService();
        this.heartbeatService.setTaskDetailsProvider(new HeartbeatService.TaskDetailsProvider() {
            @Override
            public String getTargetUrl() {
                return urlTextField.getText().trim();
            }

            @Override
            public String getButtonId() {
                return buttonIdTextField.getText().trim();
            }
        });

        // --- 1. 初始化 UI 視窗設定 ---
        setTitle("圖形日曆排程自動打卡控制台");
        setSize(720, 680);
        setMinimumSize(new Dimension(680, 640));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // 設定統一字型
        Font mainFont = new Font("微軟正黑體", Font.PLAIN, 13);
        Font boldFont = new Font("微軟正黑體", Font.BOLD, 13);

        // 主面板
        JPanel mainContentPanel = new JPanel();
        mainContentPanel.setLayout(new BoxLayout(mainContentPanel, BoxLayout.Y_AXIS));
        mainContentPanel.setBorder(new EmptyBorder(12, 14, 8, 14));

        // ----------------------------------------------------
        // 分組 1: 🖥️ 裝置與雲端伺服器連線
        // ----------------------------------------------------
        JPanel serverGroup = createGroupPanel("🖥️ 雲端服務與裝置設定", boldFont);
        serverGroup.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 8, 5, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: 裝置 ID (下拉選單)
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        JLabel clientIdLabel = new JLabel("🆔 裝置 ID / Worker ID：");
        clientIdLabel.setFont(mainFont);
        serverGroup.add(clientIdLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        String[] workerOptions = new String[] { "company-worker", "company-worker2", "company-worker3",
                "company-worker4" };
        clientIdCombo = new JComboBox<>(workerOptions);
        clientIdCombo.setFont(mainFont);
        clientIdCombo.setToolTipText("選擇或設定此台打卡裝置在雲端控制台顯示的名稱");
        serverGroup.add(clientIdCombo, gbc);

        // Row 1: Server 網址
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        JLabel serverUrlLabel = new JLabel("📡 Server 雲端網址：");
        serverUrlLabel.setFont(mainFont);
        serverGroup.add(serverUrlLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        serverUrlTextField = new JTextField("http://localhost:3000");
        serverUrlTextField.setFont(mainFont);
        serverUrlTextField.setToolTipText("輸入部署至 Render 的 ping-pong-server 網址");
        serverUrlTextField.setEnabled(false);
        serverGroup.add(serverUrlTextField, gbc);

        // Row 2: 開關 + 狀態 + 測試連線按鈕 (預設關閉)
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        enableServerCheckBox = new JCheckBox("啟用雲端單向狀態回報", false);
        enableServerCheckBox.setFont(boldFont);
        serverGroup.add(enableServerCheckBox, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        heartbeatStatusLabel = new JLabel("⚪ 未連線 (已停用)", SwingConstants.LEFT);
        heartbeatStatusLabel.setFont(boldFont);
        heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
        serverGroup.add(heartbeatStatusLabel, gbc);

        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        testServerButton = new JButton("🧪 測試 Server 連線");
        testServerButton.setFont(mainFont);
        testServerButton.setEnabled(false);
        testServerButton.addActionListener(e -> testServerConnection());
        serverGroup.add(testServerButton, gbc);

        mainContentPanel.add(serverGroup);
        mainContentPanel.add(Box.createVerticalStrut(8));

        // ----------------------------------------------------
        // 分組 2: ⚙️ 目標網址與自動排程設定
        // ----------------------------------------------------
        JPanel taskGroup = createGroupPanel("⚙️ 打卡目標與排程時間設定", boldFont);
        taskGroup.setLayout(new GridBagLayout());

        // Row 0: 打卡網址
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        JLabel urlLabel = new JLabel("🔗 目標打卡網址：");
        urlLabel.setFont(mainFont);
        taskGroup.add(urlLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        urlTextField = new JTextField("https://tw.yahoo.com");
        urlTextField.setFont(mainFont);
        taskGroup.add(urlTextField, gbc);

        // Row 1: 按鈕 ID
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        JLabel buttonIdLabel = new JLabel("🔘 打卡按鈕 ID / Selector：");
        buttonIdLabel.setFont(mainFont);
        taskGroup.add(buttonIdLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        buttonIdTextField = new JTextField("check_in");
        buttonIdTextField.setFont(mainFont);
        taskGroup.add(buttonIdTextField, gbc);

        // Row 2: 排程時間選擇
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        JLabel timeLabel = new JLabel("📆 預定打卡時間：");
        timeLabel.setFont(mainFont);
        taskGroup.add(timeLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        JPanel timeSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        DatePickerSettings dateSettings = new DatePickerSettings();
        dateSettings.setAllowKeyboardEditing(true);
        datePicker = new DatePicker(dateSettings);
        datePicker.setDateToToday();
        dateSettings.setDateRangeLimits(LocalDate.now(), LocalDate.MAX);
        datePicker.getComponentToggleCalendarButton().setVisible(false);
        datePicker.getComponentDateTextField().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                datePicker.openPopup();
            }
        });
        timeSelectionPanel.add(datePicker);

        LocalDateTime now = LocalDateTime.now();
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++)
            hours[i] = String.format("%02d", i);
        hourCombo = new JComboBox<>(hours);
        hourCombo.setFont(mainFont);
        hourCombo.setSelectedIndex(now.getHour());
        timeSelectionPanel.add(hourCombo);
        timeSelectionPanel.add(new JLabel("時"));

        String[] minutes = new String[60];
        for (int i = 0; i < 60; i++)
            minutes[i] = String.format("%02d", i);
        minuteCombo = new JComboBox<>(minutes);
        minuteCombo.setFont(mainFont);
        minuteCombo.setSelectedIndex(now.getMinute());
        timeSelectionPanel.add(minuteCombo);
        timeSelectionPanel.add(new JLabel("分"));

        taskGroup.add(timeSelectionPanel, gbc);

        mainContentPanel.add(taskGroup);
        mainContentPanel.add(Box.createVerticalStrut(10));

        // ----------------------------------------------------
        // 分組 3: 🚀 操作按鈕區
        // ----------------------------------------------------
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        startScheduleButton = new JButton("🔔 啟動排程打卡");
        cancelScheduleButton = new JButton("🛑 取消排程");
        clearLogButton = new JButton("🗑️ 清除 Log");
        cancelScheduleButton.setEnabled(false);

        Font btnFont = new Font("微軟正黑體", Font.BOLD, 13);
        startScheduleButton.setFont(btnFont);
        cancelScheduleButton.setFont(btnFont);
        clearLogButton.setFont(btnFont);

        startScheduleButton.setPreferredSize(new Dimension(150, 36));
        cancelScheduleButton.setPreferredSize(new Dimension(130, 36));
        clearLogButton.setPreferredSize(new Dimension(130, 36));

        buttonPanel.add(startScheduleButton);
        buttonPanel.add(cancelScheduleButton);
        buttonPanel.add(clearLogButton);

        mainContentPanel.add(buttonPanel);
        mainContentPanel.add(Box.createVerticalStrut(10));

        add(mainContentPanel, BorderLayout.NORTH);

        // ----------------------------------------------------
        // 分組 4: 📜 系統 Console Log 區域
        // ----------------------------------------------------
        JPanel logPanel = createGroupPanel("📜 系統日誌 (Console Log)", boldFont);
        logPanel.setLayout(new BorderLayout());

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setBackground(new Color(15, 23, 42)); // 深色質感背景
        logTextArea.setForeground(new Color(56, 189, 248)); // 亮眼天藍字體
        logTextArea.setCaretColor(Color.WHITE);
        logTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTextArea.setMargin(new Insets(8, 10, 8, 10));

        JScrollPane scrollPane = new JScrollPane(logTextArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        logPanel.add(scrollPane, BorderLayout.CENTER);

        add(logPanel, BorderLayout.CENTER);

        // ----------------------------------------------------
        // 事件監聽與即時同步
        // ----------------------------------------------------
        javax.swing.event.DocumentListener realTimeSyncListener = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                sync();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                sync();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                sync();
            }

            private void sync() {
                heartbeatService.sendHeartbeat(null, null);
            }
        };

        urlTextField.getDocument().addDocumentListener(realTimeSyncListener);
        buttonIdTextField.getDocument().addDocumentListener(realTimeSyncListener);

        clientIdCombo.addActionListener(e -> {
            String selected = (String) clientIdCombo.getSelectedItem();
            if (selected != null && !selected.trim().isEmpty()) {
                heartbeatService.setClientId(selected.trim());
            }
        });

        enableServerCheckBox.addActionListener(e -> {
            boolean enabled = enableServerCheckBox.isSelected();
            serverUrlTextField.setEnabled(enabled);
            testServerButton.setEnabled(enabled);

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

        startScheduleButton.addActionListener(e -> startSchedule());
        cancelScheduleButton.addActionListener(e -> cancelSchedule());
        clearLogButton.addActionListener(e -> SwingUtilities.invokeLater(() -> logTextArea.setText("")));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                heartbeatService.stopHeartbeat();
                schedulerService.cancelSchedule();
            }
        });

        startHeartbeatService();
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
        panel.setBorder(new CompoundBorder(titledBorder, new EmptyBorder(6, 10, 8, 10)));
        return panel;
    }

    private void startHeartbeatService() {
        if (enableServerCheckBox != null && !enableServerCheckBox.isSelected()) {
            return;
        }
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
                    JOptionPane.showMessageDialog(this, "✅ 成功連線至 ping-pong-server！", "測試成功",
                            JOptionPane.INFORMATION_MESSAGE);
                    startHeartbeatService();
                } else {
                    heartbeatStatusLabel.setText("🔴 HTTP POST 異常");
                    heartbeatStatusLabel.setForeground(new Color(239, 68, 68));
                    JOptionPane.showMessageDialog(this, "❌ 無法連線至指定 Server，請確認網址或 Server 狀態！", "測試失敗",
                            JOptionPane.ERROR_MESSAGE);
                }
            });
        });
    }

    private void startSchedule() {
        String targetUrl = urlTextField.getText().trim();
        if (targetUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入目標打卡網址！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String buttonId = buttonIdTextField.getText().trim();
        if (buttonId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入打卡按鈕 ID 或 Selector！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate selectedDate = datePicker.getDate();
        if (selectedDate == null) {
            JOptionPane.showMessageDialog(this, "請選擇有效的日期！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int hour = Integer.parseInt((String) hourCombo.getSelectedItem());
        int minute = Integer.parseInt((String) minuteCombo.getSelectedItem());

        boolean scheduled = executeStartSchedule(selectedDate, hour, minute, targetUrl, buttonId);

        if (!scheduled) {
            JOptionPane.showMessageDialog(this, "錯誤：您選擇的時間已經過去了！", "時間設定錯誤", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean executeStartSchedule(LocalDate selectedDate, int hour, int minute, String targetUrl,
            String buttonId) {
        LocalDateTime targetTime = selectedDate.atTime(hour, minute, 0);

        appendLog(String.format("【排程】設定成功！目標網址：%s，按鈕 ID/Selector：%s", targetUrl, buttonId));

        String formattedTargetTime = targetTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        heartbeatService.updateTaskStatus("SCHEDULED", formattedTargetTime);
        heartbeatService.sendHeartbeat(this::appendLog, null);

        boolean scheduled = schedulerService.startSchedule(
                targetTime,
                () -> {
                    heartbeatService.updateTaskStatus("CHECKING_IN", formattedTargetTime);
                    heartbeatService.sendHeartbeat(this::appendLog, null);
                    try {
                        boolean ok = automationService.executeCheckIn(targetUrl, buttonId, this::appendLog);
                        if (ok) {
                            heartbeatService.updateTaskStatus("SUCCESS", null, "✅ 打卡成功完成！");
                        } else {
                            heartbeatService.updateTaskStatus("FAILED", null, "❌ 打卡失敗");
                        }
                    } catch (Exception ex) {
                        appendLog("❌ 排程打卡失敗：" + ex.getMessage());
                        String cleanMsg = sanitizeErrorMessage(ex.getMessage());
                        heartbeatService.updateTaskStatus("FAILED", null, "❌ " + cleanMsg);
                    }
                    heartbeatService.sendHeartbeat(this::appendLog, null);
                },
                titleText -> SwingUtilities.invokeLater(() -> setTitle(titleText)),
                this::appendLog,
                () -> {
                    SwingUtilities.invokeLater(() -> setTitle("圖形日曆排程自動打卡控制台"));
                    toggleUiComponents(true);
                });

        if (scheduled) {
            toggleUiComponents(false);
            return true;
        } else {
            return false;
        }
    }

    private void cancelSchedule() {
        schedulerService.cancelSchedule();
        heartbeatService.updateTaskStatus("ONLINE", null);
        heartbeatService.sendHeartbeat(this::appendLog, null);

        appendLog("🛑 【取消】已成功取消排程打卡任務。");

        setTitle("圖形日曆排程自動打卡控制台");
        toggleUiComponents(true);
    }

    private void toggleUiComponents(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            startScheduleButton.setEnabled(enabled);
            cancelScheduleButton.setEnabled(!enabled);
            datePicker.setEnabled(enabled);
            hourCombo.setEnabled(enabled);
            minuteCombo.setEnabled(enabled);
            urlTextField.setEnabled(enabled);
            buttonIdTextField.setEnabled(enabled);
            serverUrlTextField.setEnabled(enabled);
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
        // 清除所有 HTTP/HTTPS 網址
        String sanitized = rawMsg.replaceAll("https?://[^\\s\"'>]+", "[隱私保護網址]");
        // 若為 Playwright 逾時錯誤，顯示簡短說明
        if (sanitized.contains("Timeout") && sanitized.contains("exceeded")) {
            return "打卡失敗：網頁連線或按鈕點擊逾時 (Timeout 30s)";
        }
        // 切斷 Call log 及過長堆疊
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
