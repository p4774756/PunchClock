package com.example.model;

/**
 * 固定雙槽位：上班 / 下班。
 */
public final class WorkSlot {

    public static final String WORK_IN_ID = "work-in";
    public static final String WORK_OUT_ID = "work-out";
    public static final String WORK_IN_NAME = "每日排程";
    public static final String WORK_OUT_NAME = "備用排程";

    public enum Kind {
        WORK_IN(WORK_IN_ID, WORK_IN_NAME),
        WORK_OUT(WORK_OUT_ID, WORK_OUT_NAME);

        public final String id;
        public final String displayName;

        Kind(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public static Kind fromId(String id) {
            if (WORK_IN_ID.equals(id)) return WORK_IN;
            if (WORK_OUT_ID.equals(id)) return WORK_OUT;
            return null;
        }
    }

    private WorkSlot() {
    }

    public static boolean isSlotId(String id) {
        return WORK_IN_ID.equals(id) || WORK_OUT_ID.equals(id);
    }

    /** 畫面上不要出現打卡、上下班這類字。舊紀錄也一併換成現在的說法。 */
    public static String screenText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("上班打卡", WORK_IN_NAME)
                .replace("下班打卡", WORK_OUT_NAME)
                .replace("上班排程", WORK_IN_NAME)
                .replace("下班排程", WORK_OUT_NAME)
                .replace("上班任務", "每日任務")
                .replace("不補打", "不補執行")
                .replace("打卡", "執行");
    }
}
