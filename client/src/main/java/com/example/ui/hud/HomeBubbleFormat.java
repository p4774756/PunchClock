package com.example.ui.hud;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.model.WorkSlot;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 首頁泡泡實用文案：任務失敗 &gt; 下一個任務 &gt; 今日完成 &gt; 事件 &gt; 氣氛話。
 */
public final class HomeBubbleFormat {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");
    private static final String[] WEEKDAY = {"週日", "週一", "週二", "週三", "週四", "週五", "週六"};

    private HomeBubbleFormat() {
    }

    public static final class BubbleLine {
        public final String text;
        /** 點泡泡要開日誌時為 true。 */
        public final boolean openLog;

        BubbleLine(String text, boolean openLog) {
            this.text = text;
            this.openLog = openLog;
        }
    }

    public static BubbleLine resolve(
            HomeBrief brief,
            List<CheckInTask> tasks,
            String eventFlash,
            String moodLine) {
        if (brief != null && brief.failed > 0) {
            return new BubbleLine("有任務失敗了，點我查看日誌。", true);
        }
        CheckInTask next = pickNext(tasks);
        if (next != null) {
            return new BubbleLine(formatNext(next), false);
        }
        if (brief != null && brief.hasWork() && brief.waiting == 0 && brief.failed == 0
                && brief.done > 0) {
            return new BubbleLine("今天的排程都完成了，做得好 ♡", false);
        }
        if (eventFlash != null && !eventFlash.isBlank()) {
            return new BubbleLine(eventFlash.trim(), false);
        }
        if (moodLine != null && !moodLine.isBlank()) {
            return new BubbleLine(moodLine.trim(), false);
        }
        return new BubbleLine("我在這裡陪著你。", false);
    }

    static String dayLabel(LocalDateTime when) {
        LocalDate today = LocalDate.now();
        LocalDate day = when.toLocalDate();
        if (day.equals(today)) {
            return "今天";
        }
        if (day.equals(today.plusDays(1))) {
            return "明天";
        }
        if (day.equals(today.minusDays(1))) {
            return "昨天";
        }
        int dow = day.getDayOfWeek().getValue() % 7;
        String week = WEEKDAY[dow];
        if (day.getYear() == today.getYear() && Math.abs(day.toEpochDay() - today.toEpochDay()) < 7) {
            return week;
        }
        return MONTH_DAY.format(day) + " " + week;
    }

    static String formatNext(CheckInTask task) {
        String name = task.getName();
        String shown = name == null || name.isBlank() ? "任務" : WorkSlot.screenText(name);
        if (shown.isBlank()) {
            shown = "任務";
        }
        LocalDateTime when = task.getActualTriggerTime();
        String prefix;
        String clock = "";
        if (when != null) {
            prefix = dayLabel(when);
            clock = when.format(CLOCK);
        } else if (task.getStatus() == TaskStatus.CHECKING_IN) {
            prefix = "現在";
            clock = "執行中";
        } else {
            prefix = "排程";
            clock = "時間未定";
        }
        String left = "";
        if (task.getStatus() == TaskStatus.CHECKING_IN) {
            left = "執行中";
        } else {
            String countdown = task.getCountdownLabel();
            if (countdown != null && !countdown.isBlank() && !"—".equals(countdown)) {
                left = countdown;
            } else if (when != null) {
                Duration d = Duration.between(LocalDateTime.now(), when);
                if (!d.isNegative()) {
                    long hours = d.toHours();
                    long mins = d.toMinutesPart();
                    if (hours > 0) {
                        left = "還有 " + hours + " 小時";
                    } else if (mins > 0) {
                        left = "還有 " + mins + " 分鐘";
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        if (!clock.isEmpty() && !"執行中".equals(clock)) {
            sb.append(' ').append(clock);
        } else if ("執行中".equals(clock)) {
            sb.append(' ').append(clock);
        }
        sb.append(' ').append(shown);
        if (!left.isEmpty()) {
            sb.append('・').append(left);
        }
        return sb.toString().trim().replaceAll(" +", " ");
    }

    private static CheckInTask pickNext(List<CheckInTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        CheckInTask best = null;
        for (CheckInTask task : tasks) {
            if (task == null || task.getStatus() == null) {
                continue;
            }
            TaskStatus status = task.getStatus();
            if (status != TaskStatus.SCHEDULED && status != TaskStatus.CHECKING_IN) {
                continue;
            }
            if (best == null) {
                best = task;
                continue;
            }
            if (status == TaskStatus.CHECKING_IN && best.getStatus() != TaskStatus.CHECKING_IN) {
                best = task;
                continue;
            }
            LocalDateTime when = task.getActualTriggerTime();
            LocalDateTime other = best.getActualTriggerTime();
            if (when == null) {
                continue;
            }
            if (other == null || when.isBefore(other)) {
                best = task;
            }
        }
        return best;
    }
}