package com.example.service;

import javax.swing.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 專責處理排程計時與動態倒數服務
 */
public class SchedulerService {

    private ScheduledExecutorService scheduler;
    private Timer countdownTimer;
    private long currentDelaySeconds = 0;

    /**
     * 啟動排程任務
     *
     * @param targetTime        目標觸發時間
     * @param task              預定執行的任務
     * @param countdownCallback 每秒倒數 Callback (提供格式化的倒數標題)
     * @param logConsumer       日誌輸出 Callback
     * @param onComplete        任務完成時的 Callback
     * @return 是否成功啟動排程
     */
    public boolean startSchedule(LocalDateTime targetTime,
                                 Runnable task,
                                 Consumer<String> countdownCallback,
                                 Consumer<String> logConsumer,
                                 Runnable onComplete) {
        cancelSchedule(); // 先防禦性清理舊任務

        LocalDateTime now = LocalDateTime.now();
        long delayInSeconds = Duration.between(now, targetTime).getSeconds();

        if (delayInSeconds <= 0) {
            if (logConsumer != null) {
                logConsumer.accept("❌ 錯誤：選擇的時間已經過去了！");
            }
            return false;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        if (logConsumer != null) {
            logConsumer.accept(String.format("【排程】設定成功！目標打卡時間為：%s", targetTime.format(formatter)));
        }

        currentDelaySeconds = delayInSeconds;

        countdownTimer = new Timer(1000, event -> {
            currentDelaySeconds--;

            if (currentDelaySeconds <= 0) {
                countdownTimer.stop();
                if (countdownCallback != null) {
                    countdownCallback.accept("圖形日曆排程自動打卡控制台 - 任務執行中");
                }
            } else {
                long days = currentDelaySeconds / (24 * 3600);
                long hours = (currentDelaySeconds % (24 * 3600)) / 3600;
                long minutes = (currentDelaySeconds % 3600) / 60;
                long seconds = currentDelaySeconds % 60;

                String titleText = String.format("⏳ 倒數計時：%d天 %d時 %d分 %d秒", days, hours, minutes, seconds);
                if (countdownCallback != null) {
                    countdownCallback.accept(titleText);
                }
            }
        });
        countdownTimer.start();

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            try {
                task.run();
            } finally {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }, delayInSeconds, TimeUnit.SECONDS);

        return true;
    }

    /**
     * 取消定時排程與倒數
     */
    public void cancelSchedule() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
    }

    /**
     * 檢查當前是否正在排程中
     */
    public boolean isScheduled() {
        return scheduler != null && !scheduler.isShutdown();
    }
}
