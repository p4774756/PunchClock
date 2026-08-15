package com.example.model;

/**
 * 打卡任務狀態列舉
 */
public enum TaskStatus {
    PENDING("待命中"),
    SCHEDULED("等待中"),
    CHECKING_IN("執行中"),
    SUCCESS("成功"),
    FAILED("失敗"),
    CANCELLED("取消");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 取得帶 Emoji 的顯示文字（用於 UI Table）
     */
    public String getBadge() {
        switch (this) {
            case PENDING:     return "⏸️ 待命中";
            case SCHEDULED:   return "⏳ 等待中";
            case CHECKING_IN: return "🚀 執行中";
            case SUCCESS:     return "✅ 成功";
            case FAILED:      return "❌ 失敗";
            case CANCELLED:   return "🛑 取消";
            default:          return name();
        }
    }
}
