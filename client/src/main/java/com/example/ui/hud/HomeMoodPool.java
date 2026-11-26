package com.example.ui.hud;

import java.util.concurrent.ThreadLocalRandom;

/** 首頁泡泡氛圍／點擊／狀態台詞池（#680，無 emoji）。 */
public final class HomeMoodPool {

    public enum Band {
        MORNING,
        NOON,
        EVENING,
        LATE_NIGHT
    }

    private static final String[][] MOOD = {
            {
                    "早安，今天也要輕鬆出發。",
                    "早上好，慢慢醒過來吧。",
                    "新的一天開始了。"
            },
            {
                    "午安，記得喝口水。",
                    "中午了，別忘休息。",
                    "午間小憩一下也好。"
            },
            {
                    "辛苦了，慢慢收尾吧。",
                    "傍晚了，今天也很努力。",
                    "快收尾了，再加一把勁。"
            },
            {
                    "夜深了，別太累喔。",
                    "加班也要注意休息。",
                    "夜很深了，記得睡覺。"
            }
    };

    private static final String[] CLICK = {
            "嘿嘿，找我有事嗎？",
            "我一直都在這裡喔。",
            "需要幫忙就叫我吧。",
            "嗯？我在聽。"
    };

    private static final String[] UNREAD = {
            "你有新訊息喔，去看看吧。",
            "好友傳來消息了。"
    };

    private HomeMoodPool() {
    }

    public static Band bandForHour(int hour) {
        if (hour >= 5 && hour < 12) {
            return Band.MORNING;
        }
        if (hour >= 12 && hour < 17) {
            return Band.NOON;
        }
        if (hour >= 17 && hour < 23) {
            return Band.EVENING;
        }
        return Band.LATE_NIGHT;
    }

    public static int nextMoodIndex(Band band, int lastIndex) {
        return nextIndex(MOOD[band.ordinal()], lastIndex);
    }

    public static String moodAt(Band band, int index) {
        String[] pool = MOOD[band.ordinal()];
        if (index < 0 || index >= pool.length) {
            return pool[0];
        }
        return pool[index];
    }

    public static String pickMood(Band band, int lastIndex) {
        return pick(MOOD[band.ordinal()], lastIndex);
    }

    public static int nextClickIndex(int lastIndex) {
        return nextIndex(CLICK, lastIndex);
    }

    public static String clickAt(int index) {
        if (index < 0 || index >= CLICK.length) {
            return CLICK[0];
        }
        return CLICK[index];
    }

    public static String pickClick(int lastIndex) {
        return pick(CLICK, lastIndex);
    }

    public static String unreadAt(int index) {
        if (index < 0 || index >= UNREAD.length) {
            return UNREAD[0];
        }
        return UNREAD[index];
    }

    public static String pickUnread(int lastIndex) {
        return pick(UNREAD, lastIndex);
    }

    /** 台詞池全部句子，供單測檢查無 emoji。 */
    static String[] allLines() {
        int n = 2 + CLICK.length + UNREAD.length;
        for (String[] band : MOOD) {
            n += band.length;
        }
        String[] lines = new String[n];
        int i = 0;
        lines[i++] = doneLine();
        lines[i++] = allDoneLine();
        for (String[] band : MOOD) {
            for (String line : band) {
                lines[i++] = line;
            }
        }
        for (String line : CLICK) {
            lines[i++] = line;
        }
        for (String line : UNREAD) {
            lines[i++] = line;
        }
        return lines;
    }

    public static String doneLine() {
        return "完成啦，做得很好。";
    }

    public static String allDoneLine() {
        return "今天的排程都完成了，做得很好。";
    }

    static int nextIndex(String[] pool, int lastIndex) {
        if (pool == null || pool.length == 0) {
            return 0;
        }
        if (pool.length == 1) {
            return 0;
        }
        int index = ThreadLocalRandom.current().nextInt(pool.length);
        if (index == lastIndex) {
            index = (index + 1) % pool.length;
        }
        return index;
    }

    private static String pick(String[] pool, int lastIndex) {
        return pool[nextIndex(pool, lastIndex)];
    }
}
