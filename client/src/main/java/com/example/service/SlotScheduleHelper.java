package com.example.service;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.model.WorkSlot;
import com.example.ui.TaskEditDialog;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 雙槽位排程時間計算與任務建構。
 */
public final class SlotScheduleHelper {

    private SlotScheduleHelper() {
    }

    /**
     * 任務是不是「載入時發現排程已過期」才被標成已取消的（見 {@link TaskPersistenceService}）。
     * 這種不是使用者或遠端主動取消，槽位仍啟用的話應該排到下一個時間，錯過的那次不補打。
     */
    public static boolean isExpiredOnRestart(CheckInTask task) {
        return task != null
                && task.getStatus() == TaskStatus.CANCELLED
                && TaskPersistenceService.EXPIRED_ON_RESTART_MESSAGE.equals(task.getResultMessage());
    }

    public static LocalDateTime nextTriggerTime(int hour, int minute, boolean weekdaysOnly, LocalDateTime after) {
        return nextTriggerTime(hour, minute, weekdaysOnly, after, null);
    }

    /**
     * @param skipDates 連假等指定不打卡的日期；與週末一併跳過。null 視為沒有。
     */
    public static LocalDateTime nextTriggerTime(
            int hour, int minute, boolean weekdaysOnly, LocalDateTime after, Collection<LocalDate> skipDates) {
        LocalDate date = after.toLocalDate();
        LocalDateTime candidate = date.atTime(hour, minute, 0);
        if (!candidate.isAfter(after)) {
            date = date.plusDays(1);
            candidate = date.atTime(hour, minute, 0);
        }
        int guard = 0;
        while (isBlockedDay(date, weekdaysOnly, skipDates)) {
            date = date.plusDays(1);
            candidate = date.atTime(hour, minute, 0);
            if (++guard > 366) {
                break;
            }
        }
        return candidate;
    }

    /**
     * 今日該時段已打過卡時使用：即使現在還早於設定的時分（例如隨機提前），
     * 也不再排今天同一槽位，避免成功後又立刻重打。
     */
    public static LocalDateTime nextTriggerTimeAfterTodaysSlot(
            int hour, int minute, boolean weekdaysOnly, LocalDateTime now) {
        return nextTriggerTimeAfterTodaysSlot(hour, minute, weekdaysOnly, now, null);
    }

    public static LocalDateTime nextTriggerTimeAfterTodaysSlot(
            int hour, int minute, boolean weekdaysOnly, LocalDateTime now, Collection<LocalDate> skipDates) {
        LocalDateTime nominal = now.toLocalDate().atTime(hour, minute, 0);
        LocalDateTime after = now.isAfter(nominal) ? now : nominal;
        return nextTriggerTime(hour, minute, weekdaysOnly, after, skipDates);
    }

    /** 週末（若啟用只排平日）或跳過日期。 */
    public static boolean isBlockedDay(LocalDate date, boolean weekdaysOnly, Collection<LocalDate> skipDates) {
        if (weekdaysOnly && isWeekend(date)) {
            return true;
        }
        return skipDates != null && skipDates.contains(date);
    }

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public static ConfigPersistenceService.SlotSettings settingsFor(
            WorkSlot.Kind kind, ConfigPersistenceService.CloudConfig config) {
        return kind == WorkSlot.Kind.WORK_IN ? config.workIn : config.workOut;
    }

    public static CheckInTask buildTask(
            WorkSlot.Kind kind,
            ConfigPersistenceService.CloudConfig config,
            LocalDateTime targetTime) {
        ConfigPersistenceService.SlotSettings slot = settingsFor(kind, config);
        CheckInTask task = new CheckInTask();
        task.setId(kind.id);
        task.setName(kind.displayName);
        task.setTargetUrl(config.targetUrl);
        task.setButtonId(config.buttonId);
        task.setTargetTime(targetTime);
        task.setUseRandomOffset(slot.useRandomOffset);
        task.setBrowserType(TaskEditDialog.parseBrowserType(config.browserChoice));
        task.setResultMessage("");
        task.setActualTriggerTime(null);
        task.setRandomOffsetSeconds(0);
        return task;
    }

    public static void applySharedSettings(CheckInTask task, ConfigPersistenceService.CloudConfig config) {
        task.setTargetUrl(config.targetUrl);
        task.setButtonId(config.buttonId);
        task.setBrowserType(TaskEditDialog.parseBrowserType(config.browserChoice));
    }

    public static void applySlotSettings(CheckInTask task, ConfigPersistenceService.SlotSettings slot) {
        task.setUseRandomOffset(slot.useRandomOffset);
    }
}
