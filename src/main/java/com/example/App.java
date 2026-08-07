package com.example;

import com.example.service.AutomationService;
import com.example.service.DiscordWebhookService;
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
    private JTextField webhookUrlTextField;
    private JButton testWebhookButton;

    private DatePicker datePicker;
    private JComboBox<String> hourCombo;
    private JComboBox<String> minuteCombo;

    private final SchedulerService schedulerService;
    private final AutomationService automationService;
    private final DiscordWebhookService webhookService;

    public App() {
        this.schedulerService = new SchedulerService();
        this.automationService = new AutomationService();
        this.webhookService = new DiscordWebhookService();

        // --- 1. 初始化 UI 視窗設定 ---
        setTitle("圖形日曆排程自動打卡控制台");
        setSize(640, 580);
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

        // Discord Webhook 設定列
        JPanel webhookPanel = new JPanel(new BorderLayout(5, 5));
        webhookPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        webhookPanel.add(new JLabel("🔗 Discord Webhook 網址："), BorderLayout.WEST);
        webhookUrlTextField = new JTextField();
        webhookUrlTextField.setToolTipText("貼上 Discord 頻道的 Webhook URL (選填)");
        webhookPanel.add(webhookUrlTextField, BorderLayout.CENTER);

        testWebhookButton = new JButton("🧪 測試推播");
        testWebhookButton.addActionListener(e -> testWebhook());
        webhookPanel.add(testWebhookButton, BorderLayout.EAST);

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

        topPanel.add(urlPanel);
        topPanel.add(buttonIdPanel);
        topPanel.add(webhookPanel);
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
                schedulerService.cancelSchedule();
            }
        });
    }

    private void testWebhook() {
        String webhookUrl = webhookUrlTextField.getText().trim();
        if (webhookUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請先輸入有效的 Discord Webhook 網址！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        webhookService.sendEmbedNotification(
                webhookUrl,
                "🔔 Discord Webhook 連線測試",
                "• **測試狀態**：✅ 成功收到測試卡片推播！\n• **目前時間**：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                0x3B82F6, // 藍色
                this::appendLog
        );
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

        String webhookUrl = webhookUrlTextField.getText().trim();

        // 發送設定排程推播
        if (!webhookUrl.isEmpty()) {
            webhookService.sendEmbedNotification(
                    webhookUrl,
                    "📆 定時打卡排程已設定！",
                    "• **目標時間**：" + targetTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n" +
                            "• **目標網址**：" + targetUrl + "\n" +
                            "• **按鈕 ID**：" + buttonId,
                    0x3B82F6, // 藍色
                    this::appendLog
            );
        }

        boolean scheduled = schedulerService.startSchedule(
                targetTime,
                () -> automationService.executeCheckIn(targetUrl, buttonId, webhookUrl, this::appendLog),
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
        appendLog("🛑 【取消】已成功取消排程打卡任務。");

        String webhookUrl = webhookUrlTextField.getText().trim();
        if (!webhookUrl.isEmpty()) {
            webhookService.sendEmbedNotification(
                    webhookUrl,
                    "🛑 定時打卡排程已取消",
                    "已取消當前的排程任務。",
                    0xF97316, // 橘色
                    this::appendLog
            );
        }

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
            webhookUrlTextField.setEnabled(enabled);
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


