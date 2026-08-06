package com.example;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;
import javax.swing.*;
import java.awt.*;

/**
 * Hello world!
 *
 */
public class App extends JFrame {
    private JTextArea logTextArea;
    private JButton startScheduleButton;
    private JButton cancelScheduleButton;
    private JButton clearLogButton;
    
    // 新增網址輸入文字欄位
    private JTextField urlTextField; 
    
    // 第三方套件的日曆元件
    private DatePicker datePicker;
    private JComboBox<String> hourCombo;
    private JComboBox<String> minuteCombo;
    private ScheduledExecutorService scheduler;

    private long currentDelaySeconds = 0; 
    private Timer countdownTimer;       

    public App() {
        // --- 1. 初始化 UI 視窗設定 ---
        setTitle("圖形日曆排程自動打卡控制台");
        setSize(600, 500); // 稍微調高視窗高度以容納網址列
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- 2. 建立上方控制面板 ---
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 新增：網址設定列
        JPanel urlPanel = new JPanel(new BorderLayout(5, 5));
        urlPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        urlPanel.add(new JLabel("🔗 目標打卡網址："), BorderLayout.WEST);
        urlTextField = new JTextField("https://tw.yahoo.com"); // 預設網址
        urlPanel.add(urlTextField, BorderLayout.CENTER);

        // 日期與時間選擇列
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        timePanel.add(new JLabel("📆 設定打卡時間："));

        // 設定日曆參數（防呆：限制不能選今天以前的日期）
        DatePickerSettings dateSettings = new DatePickerSettings();
        dateSettings.setAllowKeyboardEditing(false); // 只能用點選的，不讓使用者亂打字
        datePicker = new DatePicker(dateSettings);
        datePicker.setDateToToday(); // 預設選取今天
        dateSettings.setDateRangeLimits(LocalDate.now(), LocalDate.MAX); // 限制過去日期反灰無法點選
        timePanel.add(datePicker);

        // 間隔空白
        timePanel.add(new JLabel(" "));

        // 00-23 時
        LocalDateTime now = LocalDateTime.now();
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format("%02d", i);
        hourCombo = new JComboBox<>(hours);
        hourCombo.setSelectedIndex(now.getHour());
        timePanel.add(hourCombo);
        timePanel.add(new JLabel("時"));

        // 00-59 分
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

        startScheduleButton.setFont(new Font("微軟正黑體", Font.BOLD, 14));
        cancelScheduleButton.setFont(new Font("微軟正黑體", Font.BOLD, 14));
        clearLogButton.setFont(new Font("微軟正黑體", Font.BOLD, 14));

        buttonPanel.add(startScheduleButton);
        buttonPanel.add(cancelScheduleButton);
        buttonPanel.add(clearLogButton);

        // 將所有元件加入上方控制面板
        topPanel.add(urlPanel); // 加入網址列
        topPanel.add(timePanel);
        topPanel.add(buttonPanel);
        add(topPanel, BorderLayout.NORTH);

        // --- 3. 建立中央 Log 顯示區域 ---
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("微軟正黑體", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        add(scrollPane, BorderLayout.CENTER);

        // --- 4. 綁定按鈕事件 ---
        startScheduleButton.addActionListener(e -> startSchedule());
        cancelScheduleButton.addActionListener(e -> cancelSchedule());
        clearLogButton.addActionListener(e -> {
            // 使用 SwingUtilities.invokeLater 確保在事件派發執行緒（EDT）中更新 UI
            SwingUtilities.invokeLater(() -> {
                logTextArea.setText(""); // 將文字區域清空
            });
        });
    }

    /**
     * 計算精準時間差並啟動排程
     */
    private void startSchedule() {
        // 防呆驗證網址是否為空
        String targetUrl = urlTextField.getText().trim();
        if (targetUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入目標打卡網址！", "提示", JOptionPane.WARNING_MESSAGE);
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

        // 計算總延遲秒數
        long delayInSeconds = Duration.between(now, targetTime).getSeconds();

        if (delayInSeconds <= 0) {
            JOptionPane.showMessageDialog(this, "錯誤：您選擇的時間已經過去了！", "時間設定錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        appendLog(String.format("【排程】設定成功！目標網址：%s", targetUrl));
        appendLog(String.format("【排程】目標打卡時間為：%s", targetTime.format(formatter)));

        toggleUiComponents(false);

        // --- 核心修改：啟動每秒動態倒數計時 ---
        currentDelaySeconds = delayInSeconds;
        
        countdownTimer = new Timer(1000, event -> {
            currentDelaySeconds--;
            
            if (currentDelaySeconds <= 0) {
                countdownTimer.stop();
                setTitle("圖形日曆排程自動打卡控制台 - 任務執行中");
            } else {
                // 解析剩餘時間
                long days = currentDelaySeconds / (24 * 3600);
                long hours = (currentDelaySeconds % (24 * 3600)) / 3600;
                long minutes = (currentDelaySeconds % 3600) / 60;
                long seconds = currentDelaySeconds % 60;
                
                // 做法 A：更新在視窗標題上（最推薦，醒目且不洗版 Log）
                setTitle(String.format("⏳ 倒數計時：%d天 %d時 %d分 %d秒", days, hours, minutes, seconds));
                
                // 做法 B：如果你希望每秒印在 Log 區（注意：這會讓 Log 快速滾動洗版）
                // appendLog(String.format("【倒數】剩餘 %d天 %d時 %d分 %d秒", days, hours, minutes, seconds));
            }
        });
        countdownTimer.start();
        // ------------------------------------

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(this::executeCheckInTask, delayInSeconds, TimeUnit.SECONDS);
    }

    private void cancelSchedule() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            appendLog("🛑 【取消】已成功取消排程打卡任務。");
        }
        // --- 新增：取消時停止計時器並還原標題 ---
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
        setTitle("圖形日曆排程自動打卡控制台");
        // ------------------------------------
        toggleUiComponents(true);
    }

    private void toggleUiComponents(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            startScheduleButton.setEnabled(enabled);
            cancelScheduleButton.setEnabled(!enabled);
            datePicker.setEnabled(enabled);
            hourCombo.setEnabled(enabled);
            minuteCombo.setEnabled(enabled);
            urlTextField.setEnabled(enabled); // 同步啟用/禁用網址輸入框
        });
    }

    private void appendLog(String message) {
        // 1. 定義時間格式（例如：2026-08-04 16:46:23）
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // 2. 取得目前時間並格式化為字串
        String timestamp = LocalDateTime.now().format(formatter);
        
        // 3. 將時間戳記與原本的訊息組合成新的字串
        String logMessage = "[" + timestamp + "] " + message;

        // 4. 更新 UI 與控制台輸出
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(logMessage + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
        System.out.println(logMessage);
    }

    /**
     * Selenium 打卡主程式
     */
    private void executeCheckInTask() {
        // 從 GUI 控制台動態獲取網址
        String targetUrl = urlTextField.getText().trim();
        
        appendLog("⏰ 【觸發】排程時間已到，啟動 Selenium 瀏覽器...");
        System.setProperty("webdriver.edge.driver", "C:\\Users\\13V009\\20260714\\edgedriver_win64\\msedgedriver.exe");

        EdgeOptions options = new EdgeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--allow-running-insecure-content");

        WebDriver driver = new EdgeDriver(options);

        try {
            driver.manage().window().maximize();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            appendLog("網頁導向中：" + targetUrl);
            driver.get(targetUrl);

            WebElement checkInButton = driver.findElement(By.id("check_in"));
            appendLog("成功找到打卡按鈕，準備點擊...");
            checkInButton.click();
            appendLog("✅ 已成功點擊打卡按鈕！");

            Thread.sleep(5000);
        } catch (Exception ex) {
            appendLog("❌ 打卡過程中發生錯誤：" + ex.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
                appendLog("瀏覽器已關閉，指定日期打卡任務結束。");
            } else {
                appendLog("⚠️ 瀏覽器未成功啟動，任務結束。");
            }
            // --- 新增：任務結束後還原視窗標題 ---
            SwingUtilities.invokeLater(() -> setTitle("圖形日曆排程自動打卡控制台"));
            // ----------------------------------
            toggleUiComponents(true);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            App app = new App();
            app.setVisible(true);
        });
    }
}
