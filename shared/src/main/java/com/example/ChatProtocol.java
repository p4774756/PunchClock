package com.example;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 後台與桌面端的文字聊天協定。私訊房間 id 是 {@code dm:} 加 Client ID，群組是 {@code g:} 加短碼。
 * 送給桌面端的動作字串是 {@code ROOM|}，各欄位用 Base64 URL，避免內容裡的 {@code |} 拆錯。
 * 寄件者 {@link #ADMIN_ID} 必須與伺服器 {@code ClientStore.ADMIN_FROM_ID} 相同。
 */
public final class ChatProtocol {

    public static final String ADMIN_ID = "後台";
    public static final String KIND_DM = "dm";
    public static final String KIND_GROUP = "group";
    public static final String PREFIX = "ROOM|";
    public static final String DM_PREFIX = "dm:";
    public static final String GROUP_PREFIX = "g:";
    public static final int MAX_TEXT_CHARS = 10_000;
    public static final int MAX_NICK_CHARS = 24;
    public static final int MAX_GROUP_NAME_CHARS = 40;
    public static final int MAX_MEMBERS = 40;
    public static final int MAX_GROUPS = 30;
    public static final int MAX_MESSAGES = 100;

    private ChatProtocol() {
    }

    public static final class RoomMessage {
        public final String roomId;
        public final String roomName;
        public final String fromId;
        public final String nick;
        public final long timeMs;
        public final String text;

        public RoomMessage(String roomId, String roomName, String fromId, String nick, long timeMs, String text) {
            this.roomId = roomId;
            this.roomName = roomName == null ? "" : roomName;
            this.fromId = fromId == null ? "" : fromId;
            this.nick = nick == null ? "" : nick;
            this.timeMs = timeMs;
            this.text = text == null ? "" : text;
        }
    }

    public static String dmRoomId(String clientId) {
        String id = PeerFileRules.normalizeClientId(clientId);
        return id.isEmpty() ? "" : DM_PREFIX + id;
    }

    public static boolean isDmRoom(String roomId) {
        return roomId != null && roomId.startsWith(DM_PREFIX) && roomId.length() > DM_PREFIX.length();
    }

    public static boolean isGroupRoom(String roomId) {
        return roomId != null && roomId.startsWith(GROUP_PREFIX) && roomId.length() > GROUP_PREFIX.length();
    }

    public static boolean isDeskRoom(String roomId) {
        return isDmRoom(roomId) || isGroupRoom(roomId);
    }

    public static String dmClientId(String roomId) {
        if (!isDmRoom(roomId)) {
            return "";
        }
        return roomId.substring(DM_PREFIX.length());
    }

    public static String encodeRoom(String roomId, String roomName, String fromId, String nick, long timeMs, String text) {
        return PREFIX + b64(roomId) + "|" + b64(roomName) + "|" + b64(fromId) + "|" + b64(nick)
                + "|" + timeMs + "|" + b64(text);
    }

    /** 不是 ROOM 或欄位壞掉時回傳 null。 */
    public static RoomMessage decodeRoom(String action) {
        if (action == null || !action.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = action.split("\\|", 7);
        if (parts.length != 7) {
            return null;
        }
        String roomId = unb64(parts[1]);
        String roomName = unb64(parts[2]);
        String fromId = unb64(parts[3]);
        String nick = unb64(parts[4]);
        if (roomId == null || roomName == null || fromId == null || nick == null || !isDeskRoom(roomId)) {
            return null;
        }
        long timeMs;
        try {
            timeMs = Long.parseLong(parts[5]);
        } catch (NumberFormatException ex) {
            return null;
        }
        String text = unb64(parts[6]);
        if (text == null || text.isEmpty() || fromId.isEmpty()) {
            return null;
        }
        return new RoomMessage(roomId, roomName, fromId, nick, timeMs, text);
    }

    /** 空字串或清完沒字時用 fallback（fallback 也空就用 {@link #ADMIN_ID}）。 */
    public static String sanitizeNick(String raw, String fallback) {
        String cleaned = stripControls(raw);
        if (cleaned.length() > MAX_NICK_CHARS) {
            cleaned = cleaned.substring(0, MAX_NICK_CHARS);
        }
        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        String alt = stripControls(fallback);
        if (alt.length() > MAX_NICK_CHARS) {
            alt = alt.substring(0, MAX_NICK_CHARS);
        }
        return alt.isEmpty() ? ADMIN_ID : alt;
    }

    /** 空的或清完沒字回傳空字串，呼叫端應拒絕建立。 */
    public static String sanitizeGroupName(String raw) {
        String cleaned = stripControls(raw);
        if (cleaned.length() > MAX_GROUP_NAME_CHARS) {
            cleaned = cleaned.substring(0, MAX_GROUP_NAME_CHARS);
        }
        return cleaned;
    }

    public static String sanitizeText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    private static String stripControls(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= 32 && c != 127) {
                out.append(c);
            }
        }
        return out.toString().trim();
    }

    private static String b64(String raw) {
        String value = raw == null ? "" : raw;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isEmpty()) {
            return "";
        }
        try {
            return new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
