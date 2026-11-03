package com.example;

/**
 * 桌面端上方跑馬燈。後台可關、可改字；空字或關掉時桌面端不顯示。
 */
public final class MarqueeNotice {

    public static final int MAX_CHARS = 120;

    public final boolean enabled;
    public final String text;

    public MarqueeNotice(boolean enabled, String text) {
        this.enabled = enabled;
        this.text = text == null ? "" : text;
    }

    /** 折成一行、去掉控制字元，超過 {@link #MAX_CHARS} 的部分捨棄。空字一律視為關閉。 */
    public static MarqueeNotice normalize(boolean enabled, String raw) {
        String text = sanitize(raw);
        return new MarqueeNotice(enabled && !text.isEmpty(), text);
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(Math.min(raw.length(), MAX_CHARS));
        boolean pendingSpace = false;
        for (int i = 0; i < raw.length() && cleaned.length() < MAX_CHARS; i++) {
            char c = raw.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t' || c < 32 || c == 127) {
                c = ' ';
            }
            if (c == ' ') {
                if (cleaned.length() == 0 || pendingSpace) {
                    continue;
                }
                pendingSpace = true;
                cleaned.append(' ');
                continue;
            }
            pendingSpace = false;
            cleaned.append(c);
        }
        int end = cleaned.length();
        while (end > 0 && cleaned.charAt(end - 1) == ' ') {
            end--;
        }
        return cleaned.substring(0, end);
    }
}
