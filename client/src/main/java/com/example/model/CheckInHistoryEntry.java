package com.example.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 單筆打卡歷史紀錄（成功／失敗完成後寫入）
 */
public class CheckInHistoryEntry {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String at;
    private String slotName;
    private String status;
    private String message;
    private boolean immediate;

    public CheckInHistoryEntry() {
    }

    public CheckInHistoryEntry(
            LocalDateTime at,
            String slotName,
            TaskStatus status,
            String message,
            boolean immediate) {
        this.at = at != null ? at.format(DISPLAY_FMT) : "";
        this.slotName = slotName != null ? slotName : "";
        this.status = status != null ? status.name() : TaskStatus.FAILED.name();
        this.message = message != null ? message : "";
        this.immediate = immediate;
    }

    public String getAt() {
        return at != null ? at : "";
    }

    public void setAt(String at) {
        this.at = at;
    }

    public String getSlotName() {
        return slotName != null ? slotName : "";
    }

    public void setSlotName(String slotName) {
        this.slotName = slotName;
    }

    public String getStatus() {
        return status != null ? status : "";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message != null ? message : "";
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isImmediate() {
        return immediate;
    }

    public void setImmediate(boolean immediate) {
        this.immediate = immediate;
    }

    public TaskStatus statusEnum() {
        try {
            return TaskStatus.valueOf(status);
        } catch (Exception e) {
            return TaskStatus.FAILED;
        }
    }

    /** 列表列顯示文字 */
    public String toDisplayLine() {
        String badge = statusEnum().getBadge();
        String source = immediate ? "立即執行" : "排程";
        String detail = getMessage();
        if (detail.length() > 80) {
            detail = detail.substring(0, 79) + "…";
        }
        return String.format("%s  ｜  %s  ｜  %s  ｜  %s  ｜  %s",
                getAt(), getSlotName(), badge, source, detail);
    }
}
