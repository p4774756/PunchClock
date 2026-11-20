package com.example.server.store;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 路人閒聊用新聞標題過濾關鍵字（小寫比對）。 */
public final class NewsFilterConstants {

    public static final int TITLE_MAX_CHARS = 28;
    public static final int MAX_HEADLINES = 5;
    public static final int MAX_RESPONSE_BYTES = 512 * 1024;

    public static final List<String> BLOCKED_KEYWORDS = Collections.unmodifiableList(Arrays.asList(
            "選舉", "總統", "立委", "政黨", "國民黨", "民進黨", "共產黨", "習近平", "蔡英文",
            "戰爭", "俄烏", "以巴", "飛彈", "轟炸", "軍演", "核武",
            "命案", "殺人", "槍擊", "爆炸", "縱火", "性侵", "自殺",
            "地震", "海嘯", "颱風災", "罹難", "死亡", "罹難者", "傷亡", "失聯",
            "墜機", "車禍死亡", "中毒", "恐怖攻擊"
    ));

    private NewsFilterConstants() {
    }

    public static boolean isBlockedTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String normalized = title.toLowerCase(Locale.ROOT);
        for (String key : BLOCKED_KEYWORDS) {
            if (normalized.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}