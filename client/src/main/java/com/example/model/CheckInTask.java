package com.example.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.example.model.TaskStatus.*;

/**
 * 打卡任務資料模型
 */
public class CheckInTask {
    private String id;
    private String name;
    private String targetUrl;
    private String buttonId;
    private LocalDateTime targetTime;
    private boolean useRandomOffset;
    private int randomOffsetSeconds;
    private LocalDateTime actualTriggerTime;
    private String browserType;
    private TaskStatus status;
    private String resultMessage;

    public CheckInTask() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.status = PENDING;
        this.useRandomOffset = true;
        this.browserType = "msedge";
        this.resultMessage = "";
    }

    public CheckInTask(String name, String targetUrl, String buttonId, LocalDateTime targetTime, boolean useRandomOffset, String browserType) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.targetUrl = targetUrl;
        this.buttonId = buttonId;
        this.targetTime = targetTime;
        this.useRandomOffset = useRandomOffset;
        this.browserType = browserType;
        this.status = PENDING;
        this.resultMessage = "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getButtonId() {
        return buttonId;
    }

    public void setButtonId(String buttonId) {
        this.buttonId = buttonId;
    }

    public LocalDateTime getTargetTime() {
        return targetTime;
    }

    public void setTargetTime(LocalDateTime targetTime) {
        this.targetTime = targetTime;
    }

    public boolean isUseRandomOffset() {
        return useRandomOffset;
    }

    public void setUseRandomOffset(boolean useRandomOffset) {
        this.useRandomOffset = useRandomOffset;
    }

    public int getRandomOffsetSeconds() {
        return randomOffsetSeconds;
    }

    public void setRandomOffsetSeconds(int randomOffsetSeconds) {
        this.randomOffsetSeconds = randomOffsetSeconds;
    }

    public LocalDateTime getActualTriggerTime() {
        return actualTriggerTime != null ? actualTriggerTime : targetTime;
    }

    public void setActualTriggerTime(LocalDateTime actualTriggerTime) {
        this.actualTriggerTime = actualTriggerTime;
    }

    /**
     * 是否已計算過實際觸發時間（含隨機偏移），用於重啟後還原排程
     */
    public boolean hasComputedSchedule() {
        return actualTriggerTime != null;
    }

    public String getBrowserType() {
        return browserType;
    }

    public void setBrowserType(String browserType) {
        this.browserType = browserType;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public String getFormattedTargetTime() {
        if (targetTime == null) return "";
        return targetTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public String getFormattedActualTime() {
        if (getActualTriggerTime() == null) return "";
        return getActualTriggerTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 等待中任務距離實際觸發的倒數；其他狀態顯示「—」 */
    public String getCountdownLabel() {
        return formatCountdown(status, getActualTriggerTime(), LocalDateTime.now());
    }

    static String formatCountdown(TaskStatus status, LocalDateTime trigger, LocalDateTime now) {
        if (status != SCHEDULED || trigger == null || now == null) {
            return "—";
        }
        long seconds = Duration.between(now, trigger).getSeconds();
        if (seconds <= 0) {
            return "即將觸發";
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) {
            return String.format("%d天 %02d:%02d:%02d", days, hours, minutes, secs);
        }
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
}
