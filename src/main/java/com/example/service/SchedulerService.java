package com.example.service;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;

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
        stopTimer(task.getId()); // 清掉舊計時器，不要標成「已取消」

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetTime = task.getTargetTime();

        if (targetTime == null) {
            if (logConsumer != null) logConsumer.accept("❌ 錯誤：任務設定時間無效！");
            return false;
        }

        // 若已有計算過的觸發時間（例如從持久化還原），直接沿用，避免重啟後重骰隨機偏移
        int randomOffsetSec;
        LocalDateTime actualTriggerTime;

        if (task.hasComputedSchedule()) {
            randomOffsetSec = task.getRandomOffsetSeconds();
            actualTriggerTime = task.getActualTriggerTime();
        } else if (task.isUseRandomOffset()) {
            randomOffsetSec = ThreadLocalRandom.current().nextInt(-300, 301);
            actualTriggerTime = targetTime.plusSeconds(randomOffsetSec);
            task.setRandomOffsetSeconds(randomOffsetSec);
            task.setActualTriggerTime(actualTriggerTime);
        } else {
            randomOffsetSec = 0;
            actualTriggerTime = targetTime;
            task.setRandomOffsetSeconds(0);
            task.setActualTriggerTime(actualTriggerTime);
        }

        long delayInSeconds = Duration.between(now, actualTriggerTime).getSeconds();

        if (delayInSeconds <= 0) {
            // 若因為負向隨機偏移導致時間變為過去，補正為至少 2 秒後觸發，或是直接提示時間已過
            if (delayInSeconds < -30) {
                if (logConsumer != null) {
                    logConsumer.accept(String.format("❌ 錯誤：任務【%s】設定時間 (%s) 加上隨機偏移後已過去！",
                            task.getName(), actualTriggerTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                }
                task.setStatus(TaskStatus.FAILED);
                task.setResultMessage("設定時間加上隨機偏移已為過去時間");
                if (taskConsumer != null) taskConsumer.accept(task);
                return false;
            }
            delayInSeconds = 2;
            actualTriggerTime = now.plusSeconds(2);
            task.setActualTriggerTime(actualTriggerTime);
        }

        task.setStatus(TaskStatus.SCHEDULED);
        tasksMap.put(task.getId(), task);
        if (taskConsumer != null) taskConsumer.accept(task);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String offsetDesc = task.isUseRandomOffset()
                ? String.format(" (🎲 含隨機偏移 %s%d秒)", randomOffsetSec >= 0 ? "+" : "", randomOffsetSec)
                : " (⚡ 測試模式/精準時間)";

        if (logConsumer != null) {
            String url = task.getTargetUrl() != null ? task.getTargetUrl() : "—";
            String selector = task.getButtonId() != null && !task.getButtonId().isBlank()
                    ? task.getButtonId().trim() : "—";
            logConsumer.accept(String.format(
                    "📌 【排程設定】任務【%s】排定成功！網址：%s，Selector：%s，原定：%s，預計實際觸發：%s%s",
                    task.getName(),
                    url,
                    selector,
                    targetTime.format(fmt),
                    actualTriggerTime.format(fmt),
                    offsetDesc));
        }

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                task.setStatus(TaskStatus.CHECKING_IN);
                if (taskConsumer != null) taskConsumer.accept(task);

                if (logConsumer != null) {
                    logConsumer.accept(String.format("⏳ 【觸發執行】任務【%s】開始進行自動打卡...", task.getName()));
                }

                CountDownLatch latch = new CountDownLatch(1);
                executeAction.accept(task, latch::countDown);
                latch.await(3, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                task.setStatus(TaskStatus.CANCELLED);
                task.setResultMessage("任務排程已取消");
                if (taskConsumer != null) taskConsumer.accept(task);
            } finally {
                futuresMap.remove(task.getId());
                if (task.getStatus() != TaskStatus.SUCCESS && task.getStatus() != TaskStatus.FAILED && task.getStatus() != TaskStatus.CANCELLED) {
                    task.setStatus(TaskStatus.FAILED);
                    task.setResultMessage("任務執行異常：未回報最終結果");
                }
                if (taskConsumer != null) taskConsumer.accept(task);
            }
        }, delayInSeconds, TimeUnit.SECONDS);

        futuresMap.put(task.getId(), future);
        ensureCountdownTimer(taskConsumer);
        return true;
    }

    /**
     * 只停止計時器，不改任務狀態（給重排程用）。
     */
    public void stopTimer(String taskId) {
        ScheduledFuture<?> future = futuresMap.remove(taskId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    /**
     * 取消單一打卡任務
     */
    public boolean cancelTask(String taskId) {
        return cancelTask(taskId, "來源未標示的取消");
    }

    /**
     * 取消單一打卡任務，並寫入原因（會出現在後台任務訊息與日誌）。
     */
    public boolean cancelTask(String taskId, String reason) {
        ScheduledFuture<?> future = futuresMap.remove(taskId);
        CheckInTask task = tasksMap.get(taskId);
        boolean cancelled = false;

        if (future != null && !future.isDone()) {
            future.cancel(true);
            cancelled = true;
        }

        if (task != null) {
            task.setStatus(TaskStatus.CANCELLED);
            task.setResultMessage(reason != null && !reason.isBlank() ? reason : "來源未標示的取消");
        }
        return cancelled;
    }

    /**
     * 刪除任務紀錄
     */
    public void removeTask(String taskId) {
        cancelTask(taskId, "任務已刪除");
        tasksMap.remove(taskId);
    }

    /**
     * 取消所有排程任務
     */
    public void cancelAllTasks() {
        cancelAllTasks("來源未標示的取消");
    }

    public void cancelAllTasks(String reason) {
        for (String taskId : new ArrayList<>(tasksMap.keySet())) {
            CheckInTask task = tasksMap.get(taskId);
            if (task == null) continue;
            TaskStatus status = task.getStatus();
            if (status == TaskStatus.SCHEDULED || status == TaskStatus.CHECKING_IN || futuresMap.containsKey(taskId)) {
                cancelTask(taskId, reason);
            }
        }
    }

    /**
     * 刪除所有任務紀錄
     */
    public void removeAllTasks() {
        cancelAllTasks();
        tasksMap.clear();
    }

    public List<CheckInTask> getAllTasks() {
        List<CheckInTask> list = new ArrayList<>(tasksMap.values());
        list.sort((t1, t2) -> {
            LocalDateTime time1 = t1.getActualTriggerTime() != null ? t1.getActualTriggerTime() : t1.getTargetTime();
            LocalDateTime time2 = t2.getActualTriggerTime() != null ? t2.getActualTriggerTime() : t2.getTargetTime();
            if (time1 != null && time2 != null) {
                return time1.compareTo(time2);
            }
            return 0;
        });
        return list;
    }

    public CheckInTask getTask(String taskId) {
        return tasksMap.get(taskId);
    }

    /**
     * 僅將任務紀錄加入列表（不排程），用於載入歷史紀錄
     */
    public void addTaskRecord(CheckInTask task) {
        if (task != null && task.getId() != null) {
            tasksMap.put(task.getId(), task);
        }
    }

    private synchronized void ensureCountdownTimer(Consumer<CheckInTask> taskConsumer) {
        if (countdownTimer == null) {
            countdownTimer = new Timer(1000, e -> {
                boolean hasActive = false;
                for (CheckInTask t : tasksMap.values()) {
                    if (t.getStatus() == TaskStatus.SCHEDULED || t.getStatus() == TaskStatus.CHECKING_IN) {
                        hasActive = true;
                        break;
                    }
                }
                if (hasActive && taskConsumer != null) {
                    // 觸發 Table 重繪 countdown 狀態
                    taskConsumer.accept(null);
                } else if (!hasActive) {
                    // 沒有活躍任務時停止 Timer 避免空轉浪費 CPU
                    countdownTimer.stop();
                    countdownTimer = null;
                }
            });
            countdownTimer.start();
        }
    }

    public void shutdown() {
        cancelAllTasks("應用程式關閉");
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
        scheduler.shutdownNow();
    }
}
