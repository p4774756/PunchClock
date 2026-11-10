package com.example;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 同事 21 點經 peer message 傳送的字串協定。
 * 格式：{@code GAME|bj|v1|<kind>|<roomId>[|...]}
 *
 * <p>午餐爭奪戰相容舊版的方式：
 * <ul>
 *   <li>邀請、接受只在尾端多一欄 {@code lunch}；舊版只讀前面幾欄，會當成普通對戰。</li>
 *   <li>接受時新版會回 {@code accept|room|lunch}。邀請方沒收到這欄，就知道對方是舊版，改成普通對戰。</li>
 *   <li>午餐選擇是新的 {@code lunch} 種類；舊版 {@link #parse} 回 null，只會記一行「無法解析」。</li>
 * </ul>
 */
public final class BlackjackProtocol {

    public static final String PREFIX = "GAME|bj|v1|";
    public static final String KIND_INVITE = "invite";
    public static final String KIND_ACCEPT = "accept";
    public static final String KIND_DECLINE = "decline";
    public static final String KIND_HIT = "hit";
    public static final String KIND_STAND = "stand";
    public static final String KIND_ABORT = "abort";
    public static final String KIND_LUNCH = "lunch";

    public static final String MODE_NORMAL = "normal";
    public static final String MODE_LUNCH = "lunch";

    /** 午餐名稱最多幾個字（以 code point 計）。 */
    public static final int MAX_LUNCH_CHARS = 40;
    private static final int MAX_LUNCH_ENCODED = 400;

    private BlackjackProtocol() {
    }

    public static boolean isMessage(String text) {
        return text != null && text.startsWith(PREFIX);
    }

    public static String invite(String roomId, long seed) {
        return PREFIX + KIND_INVITE + "|" + requireRoom(roomId) + "|" + seed;
    }

    /** 普通對戰時與 {@link #invite(String, long)} 完全相同，舊版讀得懂。 */
    public static String invite(String roomId, long seed, String mode) {
        String base = invite(roomId, seed);
        return MODE_LUNCH.equals(mode) ? base + "|" + MODE_LUNCH : base;
    }

    public static String accept(String roomId) {
        return PREFIX + KIND_ACCEPT + "|" + requireRoom(roomId);
    }

    public static String accept(String roomId, String mode) {
        String base = accept(roomId);
        return MODE_LUNCH.equals(mode) ? base + "|" + MODE_LUNCH : base;
    }

    public static String decline(String roomId) {
        return PREFIX + KIND_DECLINE + "|" + requireRoom(roomId);
    }

    public static String hit(String roomId, int expectedIndex) {
        return PREFIX + KIND_HIT + "|" + requireRoom(roomId) + "|" + expectedIndex;
    }

    public static String stand(String roomId) {
        return PREFIX + KIND_STAND + "|" + requireRoom(roomId);
    }

    public static String abort(String roomId) {
        return PREFIX + KIND_ABORT + "|" + requireRoom(roomId);
    }

    /** 勝者送出的午餐選擇。名稱用 Base64 URL 編碼，避免裡面的 | 弄壞欄位。 */
    public static String lunch(String roomId, String choice) {
        String clean = cleanLunchChoice(choice);
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("empty lunch choice");
        }
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(clean.getBytes(StandardCharsets.UTF_8));
        return PREFIX + KIND_LUNCH + "|" + requireRoom(roomId) + "|" + encoded;
    }

    /**
     * 午餐名稱整理：去掉控制字元、頭尾空白，連續空白縮成一格，超過上限就截斷。
     * 回傳空字串代表不能用。
     */
    public static String cleanLunchChoice(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean space = false;
        int count = 0;
        for (int i = 0; i < raw.length() && count < MAX_LUNCH_CHARS; ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                space = sb.length() > 0;
                continue;
            }
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) {
                continue;
            }
            if (space) {
                sb.append(' ');
                count++;
                space = false;
                if (count >= MAX_LUNCH_CHARS) {
                    break;
                }
            }
            sb.appendCodePoint(cp);
            count++;
        }
        return sb.toString().trim();
    }

    public static Message parse(String text) {
        if (!isMessage(text)) {
            return null;
        }
        String[] parts = text.split("\\|", -1);
        // GAME bj v1 kind roomId [extra...]
        if (parts.length < 5) {
            return null;
        }
        String kind = parts[3];
        String roomId = parts[4];
        if (roomId == null || roomId.isBlank() || !isSafeToken(roomId)) {
            return null;
        }
        if (KIND_INVITE.equals(kind)) {
            if (parts.length < 6) {
                return null;
            }
            try {
                long seed = Long.parseLong(parts[5]);
                return Message.invite(roomId, seed, modeAt(parts, 6));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (KIND_HIT.equals(kind)) {
            if (parts.length < 6) {
                return null;
            }
            try {
                int index = Integer.parseInt(parts[5]);
                if (index < 0 || index >= BlackjackRules.DECK_SIZE) {
                    return null;
                }
                return Message.hit(roomId, index);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (KIND_ACCEPT.equals(kind)) {
            return Message.accept(roomId, modeAt(parts, 5));
        }
        if (KIND_DECLINE.equals(kind)) {
            return Message.of(KIND_DECLINE, roomId);
        }
        if (KIND_STAND.equals(kind)) {
            return Message.of(KIND_STAND, roomId);
        }
        if (KIND_ABORT.equals(kind)) {
            return Message.of(KIND_ABORT, roomId);
        }
        if (KIND_LUNCH.equals(kind)) {
            if (parts.length < 6) {
                return null;
            }
            String choice = decodeChoice(parts[5]);
            return choice.isEmpty() ? null : Message.lunch(roomId, choice);
        }
        return null;
    }

    /** 不認得的模式一律當普通對戰，跟舊版看到的一樣。 */
    private static String modeAt(String[] parts, int index) {
        return parts.length > index && MODE_LUNCH.equals(parts[index]) ? MODE_LUNCH : MODE_NORMAL;
    }

    private static String decodeChoice(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_LUNCH_ENCODED
                || !isBase64Url(encoded)) {
            return "";
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            return cleanLunchChoice(new String(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static boolean isBase64Url(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String requireRoom(String roomId) {
        if (roomId == null || roomId.isBlank() || !isSafeToken(roomId)) {
            throw new IllegalArgumentException("invalid roomId");
        }
        return roomId.trim();
    }

    /** roomId 只用安全字元，避免協定欄位被插入 |。 */
    public static boolean isSafeToken(String value) {
        if (value == null || value.isEmpty() || value.length() > 32) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** 已解析的一則 21 點訊息。 */
    public static final class Message {
        public final String kind;
        public final String roomId;
        public final Long seed;
        public final Integer deckIndex;
        /** invite／accept 才有意義：{@link #MODE_NORMAL} 或 {@link #MODE_LUNCH}。 */
        public final String mode;
        /** lunch 才有：已整理過的午餐名稱。 */
        public final String choice;

        private Message(String kind, String roomId, Long seed, Integer deckIndex, String mode, String choice) {
            this.kind = kind;
            this.roomId = roomId;
            this.seed = seed;
            this.deckIndex = deckIndex;
            this.mode = mode;
            this.choice = choice;
        }

        static Message invite(String roomId, long seed, String mode) {
            return new Message(KIND_INVITE, roomId, seed, null, mode, null);
        }

        static Message accept(String roomId, String mode) {
            return new Message(KIND_ACCEPT, roomId, null, null, mode, null);
        }

        static Message hit(String roomId, int deckIndex) {
            return new Message(KIND_HIT, roomId, null, deckIndex, null, null);
        }

        static Message lunch(String roomId, String choice) {
            return new Message(KIND_LUNCH, roomId, null, null, null, choice);
        }

        static Message of(String kind, String roomId) {
            return new Message(kind, roomId, null, null, null, null);
        }

        public boolean isLunchMode() {
            return MODE_LUNCH.equals(mode);
        }
    }
}
