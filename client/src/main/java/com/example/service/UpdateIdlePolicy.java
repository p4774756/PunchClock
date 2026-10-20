package com.example.service;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 判斷此刻能不能安全地重新啟動套用更新：
 * 沒有打卡正在執行，且最近的排程至少還有一段緩衝時間（重啟約需數秒到十幾秒，重啟期間不會觸發任何打卡，
 * 而且重啟後已過期的排程不會補打）。
 */
public final class UpdateIdlePolicy {

    /** 距離下一次排程觸發不到這麼久就先不重啟。 */
    public static final Duration SCHEDULE_BUFFER = Duration.ofMinutes(10);

    private UpdateIdlePolicy() {
    }

    public static boolean isIdle(Collection<CheckInTask> tasks, LocalDateTime now) {
        if (tasks == null) {
            return true;
        }
        LocalDateTime limit = now.plus(SCHEDULE_BUFFER);
        for (CheckInTask task : tasks) {
            TaskStatus status = task.getStatus();
            if (status == TaskStatus.CHECKING_IN) {
                return false;
            }
            if (status == TaskStatus.SCHEDULED) {
                LocalDateTime trigger = task.getActualTriggerTime() != null
                        ? task.getActualTriggerTime() : task.getTargetTime();
                if (trigger == null || !trigger.isAfter(limit)) {
                    return false;
                }
            }
        }
        return true;
    }
}
