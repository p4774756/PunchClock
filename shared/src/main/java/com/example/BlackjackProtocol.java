package com.example;

/**
 * 同事 21 點經 peer message 傳送的字串協定。
 * 格式：{@code GAME|bj|v1|<kind>|<roomId>[|<seed>]}
 */
public final class BlackjackProtocol {

    public static final String PREFIX = "GAME|bj|v1|";
    public static final String KIND_INVITE = "invite";
    public static final String KIND_ACCEPT = "accept";
    public static final String KIND_DECLINE = "decline";
    public static final String KIND_HIT = "hit";
    public static final String KIND_STAND = "stand";
    public static final String KIND_ABORT = "abort";

    private BlackjackProtocol() {
    }

    public static boolean isMessage(String text) {
        return text != null && text.startsWith(PREFIX);
    }

    public static String invite(String roomId, long seed) {
        return PREFIX + KIND_INVITE + "|" + requireRoom(roomId) + "|" + seed;
    }

    public static String accept(String roomId) {
        return PREFIX + KIND_ACCEPT + "|" + requireRoom(roomId);
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

    public static Message parse(String text) {
        if (!isMessage(text)) {
            return null;
        }
        String[] parts = text.split("\\|", -1);
        // GAME bj v1 kind roomId [extra]
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
                return Message.invite(roomId, seed);
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
            return Message.of(KIND_ACCEPT, roomId);
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
        return null;
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

        private Message(String kind, String roomId, Long seed, Integer deckIndex) {
            this.kind = kind;
            this.roomId = roomId;
            this.seed = seed;
            this.deckIndex = deckIndex;
        }

        static Message invite(String roomId, long seed) {
            return new Message(KIND_INVITE, roomId, seed, null);
        }

        static Message hit(String roomId, int deckIndex) {
            return new Message(KIND_HIT, roomId, null, deckIndex);
        }

        static Message of(String kind, String roomId) {
            return new Message(kind, roomId, null, null);
        }
    }
}
