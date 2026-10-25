package com.example.ui.hud;

import com.example.model.CheckInHistoryEntry;
import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.model.WorkSlot;

import java.awt.Image;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 首頁卡片用的真實摘要。沒有任務時數字都是 0。
 */
public final class HomeBrief {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HISTORY_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public final int done;
    public final int waiting;
    public final int failed;
    public final int cancelled;
    public final String nextName;
    public final String nextTime;
    public final String nextLeft;
    public final int onlineFriends;
    public final String recentFriend;
    public final String recentState;
    /** 有照片才有圖；沒有就畫名稱首字。不是好友時為 null。 */
    public final Image recentFace;
    public final boolean recentKnown;
    public final boolean cloudOn;
    public final String cloudLabel;

    private HomeBrief(
            int done, int waiting, int failed, int cancelled,
            String nextName, String nextTime, String nextLeft,
            int onlineFriends, String recentFriend, String recentState,
            Image recentFace, boolean recentKnown,
            boolean cloudOn, String cloudLabel) {
        this.done = done;
        this.waiting = waiting;
        this.failed = failed;
        this.cancelled = cancelled;
        this.nextName = nextName;
        this.nextTime = nextTime;
        this.nextLeft = nextLeft;
        this.onlineFriends = onlineFriends;
        this.recentFriend = recentFriend;
        this.recentState = recentState;
        this.recentFace = recentFace;
        this.recentKnown = recentKnown;
        this.cloudOn = cloudOn;
        this.cloudLabel = cloudLabel;
    }

    public int activeTotal() {
        return done + waiting + failed;
    }

    public boolean hasWork() {
        return activeTotal() > 0 || cancelled > 0 || nextName != null;
    }

    public static HomeBrief empty() {
        return new HomeBrief(0, 0, 0, 0, null, "", "", 0, "還沒有好友", "", null, false, false, "未啟用");
    }

    /**
     * 今日完成與失敗看執行紀錄；待執行只算今天（含以前）還沒跑完的排程。
     * 已取消、停用的不算完成。明天才會跑的不計入今天。
     */
    public static HomeBrief collect(
            List<CheckInTask> tasks,
            List<CheckInHistoryEntry> history,
            int onlineFriends,
            String recentFriend,
            String recentState,
            Image recentFace,
            boolean recentKnown,
            boolean cloudOn,
            String cloudLabel) {
        LocalDate today = LocalDate.now();
        int done = 0;
        int failed = 0;
        int waiting = 0;
        int cancelled = 0;
        if (history != null) {
            for (CheckInHistoryEntry entry : history) {
                if (entry == null || !today.equals(historyDay(entry))) {
                    continue;
                }
                TaskStatus status = entry.statusEnum();
                if (status == TaskStatus.SUCCESS) {
                    done++;
                } else if (status == TaskStatus.FAILED) {
                    failed++;
                }
            }
        }
        CheckInTask next = null;
        if (tasks != null) {
            for (CheckInTask task : tasks) {
                if (task == null || task.getStatus() == null) {
                    continue;
                }
                TaskStatus status = task.getStatus();
                if (status == TaskStatus.CANCELLED) {
                    cancelled++;
                    continue;
                }
                if (status == TaskStatus.CHECKING_IN || status == TaskStatus.SCHEDULED) {
                    if (countsAsWaitingToday(task, today)) {
                        waiting++;
                    }
                    if (sooner(task, next)) {
                        next = task;
                    }
                    continue;
                }
                if (!ranToday(task, today) || alreadyInHistory(history, task, today, status)) {
                    continue;
                }
                if (status == TaskStatus.SUCCESS) {
                    done++;
                } else if (status == TaskStatus.FAILED) {
                    failed++;
                }
            }
        }
        String name = null;
        String time = "";
        String left = "";
        if (next != null) {
            String raw = next.getName();
            name = raw == null || raw.isBlank() ? "任務" : WorkSlot.screenText(raw);
            if (name.isBlank()) {
                name = "任務";
            }
            LocalDateTime when = next.getActualTriggerTime();
            time = when == null ? "時間未定" : when.format(CLOCK);
            if (next.getStatus() == TaskStatus.CHECKING_IN) {
                left = "執行中";
            } else {
                String countdown = next.getCountdownLabel();
                left = countdown == null || "—".equals(countdown) ? "" : countdown;
            }
        }
        return new HomeBrief(
                done, waiting, failed, cancelled,
                name, time, left,
                Math.max(0, onlineFriends),
                recentFriend == null || recentFriend.isBlank() ? "還沒有好友" : recentFriend,
                recentState == null ? "" : recentState,
                recentFace,
                recentKnown && recentFriend != null && !recentFriend.isBlank(),
                cloudOn,
                cloudLabel == null || cloudLabel.isBlank() ? (cloudOn ? "連線中" : "未啟用") : cloudLabel);
    }

    private static boolean countsAsWaitingToday(CheckInTask task, LocalDate today) {
        if (task.getStatus() == TaskStatus.CHECKING_IN) {
            return true;
        }
        LocalDateTime when = task.getActualTriggerTime();
        return when == null || !when.toLocalDate().isAfter(today);
    }

    private static boolean ranToday(CheckInTask task, LocalDate today) {
        LocalDateTime when = task.getActualTriggerTime();
        return when != null && today.equals(when.toLocalDate());
    }

    private static boolean sooner(CheckInTask task, CheckInTask current) {
        if (task.getStatus() == TaskStatus.CHECKING_IN) {
            return current == null || current.getStatus() != TaskStatus.CHECKING_IN;
        }
        if (current == null) {
            return true;
        }
        if (current.getStatus() == TaskStatus.CHECKING_IN) {
            return false;
        }
        LocalDateTime when = task.getActualTriggerTime();
        LocalDateTime other = current.getActualTriggerTime();
        if (when == null) {
            return false;
        }
        return other == null || when.isBefore(other);
    }

    private static boolean alreadyInHistory(
            List<CheckInHistoryEntry> history, CheckInTask task, LocalDate today, TaskStatus status) {
        if (history == null) {
            return false;
        }
        String name = task.getName() == null ? "" : task.getName();
        String shown = WorkSlot.screenText(name);
        for (CheckInHistoryEntry entry : history) {
            if (entry == null || entry.statusEnum() != status || !today.equals(historyDay(entry))) {
                continue;
            }
            String slot = entry.getSlotName();
            if (name.equals(slot) || shown.equals(WorkSlot.screenText(slot))) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate historyDay(CheckInHistoryEntry entry) {
        String at = entry.getAt();
        if (at == null || at.length() < 10) {
            return null;
        }
        try {
            return LocalDateTime.parse(at, HISTORY_AT).toLocalDate();
        } catch (RuntimeException ex) {
            try {
                return LocalDate.parse(at.substring(0, 10));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}
