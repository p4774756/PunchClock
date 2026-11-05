package com.example.pet;

/**
 * 桌面寵物看得到的 App 事件。泡泡只用固定短句，不帶訊息或檔名。
 */
public enum DesktopPetEvent {
    MESSAGE_RECEIVED("有新訊息喔～！"),
    FILE_RECEIVED("檔案收到啦！"),
    FILE_SENT("成功送達～！"),
    TASK_COMPLETED("任務完成！"),
    SCHEDULE_REMINDER("時間到囉～！");

    private final String phrase;

    DesktopPetEvent(String phrase) {
        this.phrase = phrase;
    }

    public String phrase() {
        return phrase;
    }
}
