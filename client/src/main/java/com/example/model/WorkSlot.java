package com.example.model;

/**
 * 固定雙槽位：上班 / 下班。
 */
public final class WorkSlot {

    public static final String WORK_IN_ID = "work-in";
    public static final String WORK_OUT_ID = "work-out";
    public static final String WORK_IN_NAME = "上班打卡";
    public static final String WORK_OUT_NAME = "下班打卡";

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
}
