package com.example.service;

import com.example.model.CheckInTask;

import javax.swing.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 專責處理多任務排程計時與動態倒數服務
 */
public class SchedulerService {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> futuresMap = new ConcurrentHashMap<>();
    private final Map<String, CheckInTask> tasksMap = new ConcurrentHashMap<>();
    private Timer countdownTimer;

    /**
     * 啟動/新增排程任務
     *
     * @param task          打卡任務物件
     * @param taskConsumer  任務更新 Callback
     * @param logConsumer   日誌輸出 Callback
     * @param executeAction 觸發執行的動作 BiConsumer(CheckInTask, Runnable onComplete)
     * @return 是否成功排定任務
     */
    public boolean scheduleTask(CheckInTask task,
                                Consumer<CheckInTask> taskConsumer,
                                Consumer<String> logConsumer,
                                BiConsumer<CheckInTask, Runnable> executeAction) {
        cancelTask(task.getId()); // 防禦性清理舊同名任務

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetTime = task.getTargetTime();

        if (targetTime == null) {
            if (logConsumer != null) logConsumer.accept("❌ 錯誤：任務設定時間無效！");
            return false;
        }

        // 計算隨機時間偏移 (-300秒 ~ +300秒)
        int randomOffsetSec = 0;
        if (task.isUseRandomOffset()) {
            randomOffsetSec = ThreadLocalRandom.current().nextInt(-300, 301);
        }
        task.setRandomOffsetSeconds(randomOffsetSec);
        LocalDateTime actualTriggerTime = targetTime.plusSeconds(randomOffsetSec);
        task.setActualTriggerTime(actualTriggerTime);

        long delayInSeconds = Duration.between(now, actualTriggerTime).getSeconds();

        if (delayInSeconds <= 0) {
            // 若因為負向隨機偏移導致時間變為過去，補正為至少 2 秒後觸發，或是直接提示時間已過
            if (delayInSeconds < -30) {
                if (logConsumer != null) {
                    logConsumer.accept(String.format("❌ 錯誤：任務【%s】設定時間 (%s) 加上隨機偏移後已過去！",
                            task.getName(), actualTriggerTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                }
                task.setStatus("FAILED");
                task.setResultMessage("設定時間加上隨機偏移已為過去時間");
                if (taskConsumer != null) taskConsumer.accept(task);
                return false;
            }
            delayInSeconds = 2;
            actualTriggerTime = now.plusSeconds(2);
            task.setActualTriggerTime(actualTriggerTime);
        }

        task.setStatus("SCHEDULED");
        tasksMap.put(task.getId(), task);
        if (taskConsumer != null) taskConsumer.accept(task);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String offsetDesc = task.isUseRandomOffset()
                ? String.format(" (🎲 含隨機偏移 %s%d秒)", randomOffsetSec >= 0 ? "+" : "", randomOffsetSec)
                : " (⚡ 測試模式/精準時間)";

        if (logConsumer != null) {
            logConsumer.accept(String.format("📌 【排程設定】任務【%s】排定成功！原定：%s，預計實際觸發：%s%s",
                    task.getName(),
                    targetTime.format(fmt),
                    actualTriggerTime.format(fmt),
                    offsetDesc));
        }

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                task.setStatus("CHECKING_IN");
                if (taskConsumer != null) taskConsumer.accept(task);

                if (logConsumer != null) {
                    logConsumer.accept(String.format("⏳ 【觸發執行】任務【%s】開始進行自動打卡...", task.getName()));
                }

                CountDownLatch latch = new CountDownLatch(1);
                executeAction.accept(task, latch::countDown);
                latch.await(3, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                task.setStatus("CANCELLED");
                task.setResultMessage("任務排程已取消");
                if (taskConsumer != null) taskConsumer.accept(task);
            } finally {
                futuresMap.remove(task.getId());
                if (!"SUCCESS".equals(task.getStatus()) && !"FAILED".equals(task.getStatus()) && !"CANCELLED".equals(task.getStatus())) {
                    task.setStatus("SUCCESS");
                }
                if (taskConsumer != null) taskConsumer.accept(task);
            }
        }, delayInSeconds, TimeUnit.SECONDS);

        futuresMap.put(task.getId(), future);
        ensureCountdownTimer(taskConsumer);
        return true;
    }

    /**
     * 取消單一打卡任務
     */
    public boolean cancelTask(String taskId) {
        ScheduledFuture<?> future = futuresMap.remove(taskId);
        CheckInTask task = tasksMap.get(taskId);
        boolean cancelled = false;

        if (future != null && !future.isDone()) {
            future.cancel(true);
            cancelled = true;
        }

        if (task != null) {
            task.setStatus("CANCELLED");
            task.setResultMessage("使用者手動取消");
        }
        return cancelled;
    }

    /**
     * 刪除任務紀錄
     */
    public void removeTask(String taskId) {
        cancelTask(taskId);
        tasksMap.remove(taskId);
    }

    /**
     * 取消所有排程任務
     */
    public void cancelAllTasks() {
        for (String taskId : new ArrayList<>(futuresMap.keySet())) {
            cancelTask(taskId);
        }
    }

    public List<CheckInTask> getAllTasks() {
        return new ArrayList<>(tasksMap.values());
    }

    public CheckInTask getTask(String taskId) {
        return tasksMap.get(taskId);
    }

    private synchronized void ensureCountdownTimer(Consumer<CheckInTask> taskConsumer) {
        if (countdownTimer == null) {
            countdownTimer = new Timer(1000, e -> {
                boolean hasActive = false;
                for (CheckInTask t : tasksMap.values()) {
                    if ("SCHEDULED".equals(t.getStatus()) || "CHECKING_IN".equals(t.getStatus())) {
                        hasActive = true;
                        break;
                    }
                }
                if (hasActive && taskConsumer != null) {
                    // 觸發 Table 重繪 countdown 狀態
                    taskConsumer.accept(null);
                }
            });
            countdownTimer.start();
        }
    }

    public void shutdown() {
        cancelAllTasks();
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
        scheduler.shutdownNow();
    }
}
