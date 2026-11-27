package com.example;

/**
 * 好友聊天塗鴉訊息：文字欄放模式標記，圖片欄沿用同事訊息 Base64 圖。
 * 舊版客戶端可忽略標記、只顯示圖片。
 */
public final class PeerGraffiti {

    public static final String TAG_DIRECT = "[塗鴉·直接]";
    public static final String TAG_CARD = "[塗鴉·卡片]";

    public enum Mode {
        DIRECT,
        CARD
    }

    private PeerGraffiti() {
    }

    public static String messageText(Mode mode) {
        if (mode == Mode.CARD) {
            return TAG_CARD;
        }
        return TAG_DIRECT;
    }

    public static Mode modeOf(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (TAG_CARD.equals(trimmed) || trimmed.startsWith(TAG_CARD + "\n")
                || trimmed.startsWith(TAG_CARD + "\r")) {
            return Mode.CARD;
        }
        if (TAG_DIRECT.equals(trimmed) || trimmed.startsWith(TAG_DIRECT + "\n")
                || trimmed.startsWith(TAG_DIRECT + "\r")) {
            return Mode.DIRECT;
        }
        return null;
    }

    /** 是否只有塗鴉標記、沒有其它文字可顯示。 */
    public static boolean isTagOnly(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return TAG_DIRECT.equals(trimmed) || TAG_CARD.equals(trimmed);
    }

    public static String cardLabel(boolean outgoing) {
        return outgoing ? "已送出塗鴉卡片，點我預覽" : "收到一張塗鴉卡片，點我打開";
    }
}