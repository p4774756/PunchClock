package com.example;

import com.example.service.AutomationService;
import com.example.service.HeartbeatService;
import com.example.service.SchedulerService;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 圖形介面主視窗
 */
public class App extends JFrame {
    private JTextArea logTextArea;
    private JButton startScheduleButton;
    private JButton cancelScheduleButton;
    private JButton clearLogButton;

    private JTextField urlTextField;
    private JTextField buttonIdTextField;
    private JTextField serverUrlTextField;
    private JTextField clientIdTextField;
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
        this.heartbeatService.setRemoteTriggerHandler(this::handleRemoteTriggerCommand);
        this.heartbeatService.setRemoteScheduleHandler(this::handleRemoteScheduleCommand);
        this.heartbeatService.setRemoteCancelHandler(this::handleRemoteCancelCommand);
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
        setSize(640, 620);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- 2. 建立上方控制面板 ---
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 網址設定列
        JPanel urlPanel = new JPanel(new BorderLayout(5, 5));
        urlPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        urlPanel.add(new JLabel("🔗 目標打卡網址："), BorderLayout.WEST);
        urlTextField = new JTextField("https://tw.yahoo.com");
        urlPanel.add(urlTextField, BorderLayout.CENTER);

        // 按鈕 ID 設定列
        JPanel buttonIdPanel = new JPanel(new BorderLayout(5, 5));
        buttonIdPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        buttonIdPanel.add(new JLabel("🔘 打卡按鈕 ID / Selector："), BorderLayout.WEST);
        buttonIdTextField = new JTextField("check_in");
        buttonIdPanel.add(buttonIdTextField, BorderLayout.CENTER);

        // 綁定輸入即時同步至後台的 DocumentListener
        javax.swing.event.DocumentListener realTimeSyncListener = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { sync(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { sync(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { sync(); }

            private void sync() {
                heartbeatService.sendHeartbeat(null, null);
            }
        };

        urlTextField.getDocument().addDocumentListener(realTimeSyncListener);
        buttonIdTextField.getDocument().addDocumentListener(realTimeSyncListener);

        // Server 心跳服務網址設定列
        JPanel serverPanel = new JPanel(new BorderLayout(5, 5));
        serverPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        serverPanel.add(new JLabel("📡 ping-pong-server 網址："), BorderLayout.WEST);
        serverUrlTextField = new JTextField("http://localhost:3000");
        serverUrlTextField.setToolTipText("輸入部署至 Render 的 ping-pong-server 網址 (如 https://xxx.onrender.com)");
        serverPanel.add(serverUrlTextField, BorderLayout.CENTER);

        JPanel serverActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        heartbeatStatusLabel = new JLabel("⚪ 未連線");
        heartbeatStatusLabel.setFont(new Font("微軟正黑體", Font.BOLD, 12));
        testServerButton = new JButton("🧪 測試 Server 連線");
        testServerButton.addActionListener(e -> testServerConnection());
        serverActionPanel.add(heartbeatStatusLabel);
        serverActionPanel.add(testServerButton);
        serverPanel.add(serverActionPanel, BorderLayout.EAST);

        // 日期與時間選擇列
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        timePanel.add(new JLabel("📆 設定打卡時間："));

        DatePickerSettings dateSettings = new DatePickerSettings();
        dateSettings.setAllowKeyboardEditing(false);
        datePicker = new DatePicker(dateSettings);
        datePicker.setDateToToday();
        dateSettings.setDateRangeLimits(LocalDate.now(), LocalDate.MAX);
        timePanel.add(datePicker);

        timePanel.add(new JLabel(" "));

        LocalDateTime now = LocalDateTime.now();
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format("%02d", i);
        hourCombo = new JComboBox<>(hours);
        hourCombo.setSelectedIndex(now.getHour());
        timePanel.add(hourCombo);
        timePanel.add(new JLabel("時"));

        String[] minutes = new String[60];
        for (int i = 0; i < 60; i++) minutes[i] = String.format("%02d", i);
        minuteCombo = new JComboBox<>(minutes);
        minuteCombo.setSelectedIndex(now.getMinute());
        timePanel.add(minuteCombo);
        timePanel.add(new JLabel("分"));

        // 按鈕列
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        startScheduleButton = new JButton("🔔 啟動排程打卡");
        cancelScheduleButton = new JButton("🛑 取消定時");
        clearLogButton = new JButton("清除log");
        cancelScheduleButton.setEnabled(false);

        Font buttonFont = new Font("微軟正黑體", Font.BOLD, 14);
        startScheduleButton.setFont(buttonFont);
        cancelScheduleButton.setFont(buttonFont);
        clearLogButton.setFont(buttonFont);

        buttonPanel.add(startScheduleButton);
        buttonPanel.add(cancelScheduleButton);
        buttonPanel.add(clearLogButton);

        // 裝置 / Worker ID 設定列
        JPanel clientIdPanel = new JPanel(new BorderLayout(5, 5));
        clientIdPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        clientIdPanel.add(new JLabel("🆔 裝置 / Worker ID："), BorderLayout.WEST);
        clientIdTextField = new JTextField(heartbeatService.getClientId());
        clientIdTextField.setToolTipText("設定此台打卡裝置在雲端控制台顯示的名稱 (例如: company-worker-1, pc-office)");
        clientIdPanel.add(clientIdTextField, BorderLayout.CENTER);

        clientIdTextField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }

            private void update() {
                String newId = clientIdTextField.getText().trim();
                if (!newId.isEmpty()) {
                    heartbeatService.setClientId(newId);
                }
            }
        });

        topPanel.add(clientIdPanel);
        topPanel.add(urlPanel);
        topPanel.add(buttonIdPanel);
        topPanel.add(serverPanel);
        topPanel.add(timePanel);
        topPanel.add(buttonPanel);
        add(topPanel, BorderLayout.NORTH);

        // --- 3. 建立中央 Log 顯示區域 ---
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("微軟正黑體", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        add(scrollPane, BorderLayout.CENTER);

        // --- 4. 綁定按鈕與視窗事件 ---
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

        // 預設自動啟動心跳服務 (對應初始網址)
        startHeartbeatService();
    }

    private void startHeartbeatService() {
        String serverUrl = serverUrlTextField.getText().trim();
        if (!serverUrl.isEmpty()) {
            heartbeatService.startHeartbeat(serverUrl, this::appendLog, isOk -> {
                SwingUtilities.invokeLater(() -> {
                    if (isOk) {
                        heartbeatStatusLabel.setText("💚 WebSocket 已連線");
                        heartbeatStatusLabel.setForeground(new Color(34, 197, 94));
                    } else {
                        heartbeatStatusLabel.setText("🔴 WebSocket 斷開");
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
                    heartbeatStatusLabel.setText("💚 WebSocket 已連線");
                    heartbeatStatusLabel.setForeground(new Color(34, 197, 94));
                    JOptionPane.showMessageDialog(this, "✅ 成功連線至 ping-pong-server！", "測試成功", JOptionPane.INFORMATION_MESSAGE);
                    startHeartbeatService();
                } else {
                    heartbeatStatusLabel.setText("🔴 WebSocket 斷開");
                    heartbeatStatusLabel.setForeground(new Color(239, 68, 68));
                    JOptionPane.showMessageDialog(this, "❌ 無法連線至指定 Server，請確認網址或 Server 狀態！", "測試失敗", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
    }

    private void handleRemoteTriggerCommand() {
        appendLog("🚀 【遠端觸發】收到來自 Web 控制台的打卡觸發指令！準備執行...");
        String targetUrl = urlTextField.getText().trim();
        String buttonId = buttonIdTextField.getText().trim();

        if (targetUrl.isEmpty() || buttonId.isEmpty()) {
            heartbeatService.sendCheckinResult(false, "失敗：目標網址或按鈕 ID 未填寫");
            appendLog("❌ 【遠端打卡失敗】目標網址或按鈕 ID 未填寫！");
            return;
        }

        new Thread(() -> {
            try {
                heartbeatService.updateTaskStatus("CHECKING_IN", "REMOTE_TRIGGER");
                automationService.executeCheckIn(targetUrl, buttonId, this::appendLog);
                heartbeatService.sendCheckinResult(true, "✅ 遠端觸發打卡已成功執行完成！");
                heartbeatService.updateTaskStatus("ONLINE", null);
            } catch (Exception ex) {
                heartbeatService.sendCheckinResult(false, "❌ 打卡異常：" + ex.getMessage());
                heartbeatService.updateTaskStatus("ONLINE", null);
            }
        }).start();
    }

    private void handleRemoteCancelCommand() {
        SwingUtilities.invokeLater(() -> {
            cancelSchedule();
            appendLog("🛑 【遠端指令】已成功由 Web 控制台取消排程打卡。");
            heartbeatService.sendCheckinResult(true, "🛑 已成功由遠端 Web 控制台取消排程打卡任務！");
        });
    }

    private void handleRemoteScheduleCommand(String scheduledTimeStr, String targetUrl, String buttonId) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (targetUrl != null && !targetUrl.isBlank()) {
                    urlTextField.setText(targetUrl.trim());
                }
                if (buttonId != null && !buttonId.isBlank()) {
                    buttonIdTextField.setText(buttonId.trim());
                }

                // 時間格式：YYYY-MM-DD HH:mm
                String[] parts = scheduledTimeStr.trim().split(" ");
                if (parts.length < 2) {
                    heartbeatService.sendCheckinResult(false, "❌ 失敗：排程時間格式錯誤 (需為 YYYY-MM-DD HH:mm)");
                    return;
                }

                LocalDate date = LocalDate.parse(parts[0]);
                String[] timeParts = parts[1].split(":");
                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1]);

                datePicker.setDate(date);
                hourCombo.setSelectedItem(String.format("%02d", hour));
                minuteCombo.setSelectedItem(String.format("%02d", minute));

                boolean success = executeStartSchedule(date, hour, minute, urlTextField.getText().trim(), buttonIdTextField.getText().trim());
                if (success) {
                    appendLog("🔔 【遠端指令】已成功由 Web 控制台啟動排程 (預定時間：" + scheduledTimeStr + ")");
                    heartbeatService.sendCheckinResult(true, "🔔 已成功由遠端 Web 控制台啟動排程打卡 (目標時間：" + scheduledTimeStr + ")！");
                } else {
                    heartbeatService.sendCheckinResult(false, "❌ 遠端啟動排程失敗：選擇的時間已經過去！");
                }
            } catch (Exception ex) {
                heartbeatService.sendCheckinResult(false, "❌ 遠端設定排程失敗：" + ex.getMessage());
            }
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

    private boolean executeStartSchedule(LocalDate selectedDate, int hour, int minute, String targetUrl, String buttonId) {
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
                        automationService.executeCheckIn(targetUrl, buttonId, this::appendLog);
                    } catch (Exception ex) {
                        appendLog("❌ 排程打卡失敗：" + ex.getMessage());
                    }
                    heartbeatService.updateTaskStatus("ONLINE", null);
                    heartbeatService.sendHeartbeat(this::appendLog, null);
                },
                titleText -> SwingUtilities.invokeLater(() -> setTitle(titleText)),
                this::appendLog,
                () -> {
                    SwingUtilities.invokeLater(() -> setTitle("圖形日曆排程自動打卡控制台"));
                    toggleUiComponents(true);
                }
        );

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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            App app = new App();
            app.setVisible(true);
        });
    }
}



