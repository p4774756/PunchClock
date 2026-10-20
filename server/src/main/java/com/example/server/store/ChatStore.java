package com.example.server.store;

import com.example.ChatProtocol;
import com.example.PeerFileRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 後台與桌面端的文字聊天。只放記憶體，重啟伺服器就清空。
 * 每位收件人要送出的 {@code ROOM|} 動作由呼叫端排進心跳佇列。
 */
public final class ChatStore {

    /** 桌面端若超過這段時間沒心跳，這則動作就不再補送；後台畫面仍留著紀錄。 */
    public static final long DELIVERY_TTL_MS = PeerFileRules.OFFER_TTL_MS;

    public static final class Outcome {
        public final boolean ok;
        public final String message;
        /** 要排進收件人心跳的動作；沒有人要收時是空字串。 */
        public final String action;
        public final List<String> targets;

        private Outcome(boolean ok, String message, String action, List<String> targets) {
            this.ok = ok;
            this.message = message == null ? "" : message;
            this.action = action == null ? "" : action;
            this.targets = targets == null ? List.of() : List.copyOf(targets);
        }

        static Outcome fail(String message) {
            return new Outcome(false, message, "", List.of());
        }

        static Outcome sent(String message, String action, List<String> targets) {
            return new Outcome(true, message, action, targets);
        }
    }

    private static final class Room {
        final String id;
        final String kind;
        String name;
        final Set<String> members = new LinkedHashSet<>();
        final ArrayDeque<Msg> messages = new ArrayDeque<>();

        Room(String id, String kind, String name) {
            this.id = id;
            this.kind = kind;
            this.name = name;
        }
    }

    private static final class Msg {
        final long id;
        final String fromId;
        final String nick;
        final String text;
        final long timeMs;
        final boolean fromAdmin;

        Msg(long id, String fromId, String nick, String text, long timeMs, boolean fromAdmin) {
            this.id = id;
            this.fromId = fromId;
            this.nick = nick;
            this.text = text;
            this.timeMs = timeMs;
            this.fromAdmin = fromAdmin;
        }
    }

    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public synchronized List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Room room : rooms.values()) {
            list.add(roomMap(room, true));
        }
        return list;
    }

    /** 這台桌面端加入的群組（不含私訊；私訊房間 id 可由 Client ID 推出）。 */
    public synchronized List<Map<String, Object>> clientRooms(String clientId) {
        String id = PeerFileRules.normalizeClientId(clientId);
        List<Map<String, Object>> list = new ArrayList<>();
        if (id.isEmpty()) {
            return list;
        }
        for (Room room : rooms.values()) {
            if (!ChatProtocol.KIND_GROUP.equals(room.kind) || !room.members.contains(id)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", room.id);
            item.put("name", room.name);
            item.put("kind", room.kind);
            list.add(item);
        }
        return list;
    }

    public synchronized Outcome sendFromAdmin(String roomId, String nick, String text) {
        String body = ChatProtocol.sanitizeText(text);
        String reject = rejectText(body);
        if (reject != null) {
            return Outcome.fail(reject);
        }
        String display = ChatProtocol.sanitizeNick(nick, ChatProtocol.ADMIN_ID);
        if (ChatProtocol.isDmRoom(roomId)) {
            String clientId = PeerFileRules.normalizeClientId(ChatProtocol.dmClientId(roomId));
            if (clientId.isEmpty() || ChatProtocol.ADMIN_ID.equals(clientId)) {
                return Outcome.fail("找不到這位同事");
            }
            Room room = ensureDm(clientId);
            return append(room, ChatProtocol.ADMIN_ID, display, true, body, "");
        }
        if (ChatProtocol.isGroupRoom(roomId)) {
            Room room = rooms.get(roomId);
            if (room == null || !ChatProtocol.KIND_GROUP.equals(room.kind)) {
                return Outcome.fail("找不到這個群組");
            }
            return append(room, ChatProtocol.ADMIN_ID, display, true, body, "");
        }
        return Outcome.fail("找不到這段對話");
    }

    public synchronized Outcome sendFromClient(String fromClientId, String roomId, String text) {
        String fromId = PeerFileRules.normalizeClientId(fromClientId);
        if (fromId.isEmpty() || ChatProtocol.ADMIN_ID.equals(fromId)) {
            return Outcome.fail("缺少寄件人");
        }
        String body = ChatProtocol.sanitizeText(text);
        String reject = rejectText(body);
        if (reject != null) {
            return Outcome.fail(reject);
        }
        if (ChatProtocol.isDmRoom(roomId)) {
            if (!roomId.equals(ChatProtocol.dmRoomId(fromId))) {
                return Outcome.fail("不能傳進別人的對話");
            }
            Room room = ensureDm(fromId);
            return append(room, fromId, fromId, false, body, fromId);
        }
        if (ChatProtocol.isGroupRoom(roomId)) {
            Room room = rooms.get(roomId);
            if (room == null || !ChatProtocol.KIND_GROUP.equals(room.kind)) {
                return Outcome.fail("找不到這個群組");
            }
            if (!room.members.contains(fromId)) {
                return Outcome.fail("你不在這個群組");
            }
            return append(room, fromId, fromId, false, body, fromId);
        }
        return Outcome.fail("找不到這段對話");
    }

    public synchronized Outcome createGroup(String name, List<String> members) {
        String cleanName = ChatProtocol.sanitizeGroupName(name);
        if (cleanName.isEmpty()) {
            return Outcome.fail("請填群組名稱");
        }
        List<String> cleanMembers = cleanMembers(members);
        if (cleanMembers.isEmpty()) {
            return Outcome.fail("請至少選一位同事");
        }
        if (groupCount() >= ChatProtocol.MAX_GROUPS) {
            return Outcome.fail("群組已達上限 " + ChatProtocol.MAX_GROUPS);
        }
        String id = newGroupId();
        Room room = new Room(id, ChatProtocol.KIND_GROUP, cleanName);
        room.members.addAll(cleanMembers);
        rooms.put(id, room);
        return Outcome.sent("已建立群組", "", List.of());
    }

    public synchronized Outcome updateGroup(String groupId, String name, List<String> members) {
        Room room = rooms.get(groupId);
        if (room == null || !ChatProtocol.KIND_GROUP.equals(room.kind)) {
            return Outcome.fail("找不到這個群組");
        }
        String cleanName = ChatProtocol.sanitizeGroupName(name);
        if (cleanName.isEmpty()) {
            return Outcome.fail("請填群組名稱");
        }
        List<String> cleanMembers = cleanMembers(members);
        if (cleanMembers.isEmpty()) {
            return Outcome.fail("請至少留一位同事");
        }
        room.name = cleanName;
        room.members.clear();
        room.members.addAll(cleanMembers);
        return Outcome.sent("已更新群組", "", List.of());
    }

    public synchronized Outcome deleteGroup(String groupId) {
        Room room = rooms.get(groupId);
        if (room == null || !ChatProtocol.KIND_GROUP.equals(room.kind)) {
            return Outcome.fail("找不到這個群組");
        }
        rooms.remove(groupId);
        return Outcome.sent("已解散群組", "", List.of());
    }

    private Outcome append(Room room, String fromId, String nick, boolean fromAdmin, String text, String excludeClientId) {
        long now = System.currentTimeMillis();
        Msg msg = new Msg(ids.incrementAndGet(), fromId, nick, text, now, fromAdmin);
        room.messages.addLast(msg);
        while (room.messages.size() > ChatProtocol.MAX_MESSAGES) {
            room.messages.removeFirst();
        }
        String action = ChatProtocol.encodeRoom(room.id, room.name, fromId, nick, now, text);
        List<String> targets = new ArrayList<>();
        for (String member : room.members) {
            if (!member.equals(excludeClientId)) {
                targets.add(member);
            }
        }
        return Outcome.sent("訊息已送出，對方約 15 秒內收到", action, targets);
    }

    private Room ensureDm(String clientId) {
        String roomId = ChatProtocol.dmRoomId(clientId);
        Room existing = rooms.get(roomId);
        if (existing != null) {
            return existing;
        }
        Room room = new Room(roomId, ChatProtocol.KIND_DM, clientId);
        room.members.add(clientId);
        rooms.put(roomId, room);
        return room;
    }

    private int groupCount() {
        int count = 0;
        for (Room room : rooms.values()) {
            if (ChatProtocol.KIND_GROUP.equals(room.kind)) {
                count++;
            }
        }
        return count;
    }

    private String newGroupId() {
        for (int i = 0; i < 8; i++) {
            String id = ChatProtocol.GROUP_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (!rooms.containsKey(id)) {
                return id;
            }
        }
        return ChatProtocol.GROUP_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private static List<String> cleanMembers(List<String> members) {
        List<String> clean = new ArrayList<>();
        if (members == null) {
            return clean;
        }
        for (String raw : members) {
            String id = PeerFileRules.normalizeClientId(raw);
            if (id.isEmpty() || ChatProtocol.ADMIN_ID.equals(id) || clean.contains(id)) {
                continue;
            }
            clean.add(id);
            if (clean.size() >= ChatProtocol.MAX_MEMBERS) {
                break;
            }
        }
        return clean;
    }

    private static String rejectText(String body) {
        if (body.isEmpty()) {
            return "訊息不可為空";
        }
        if (body.length() > ChatProtocol.MAX_TEXT_CHARS) {
            return "訊息長度不可超過 " + ChatProtocol.MAX_TEXT_CHARS + " 字";
        }
        return null;
    }

    private static Map<String, Object> roomMap(Room room, boolean withMessages) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", room.id);
        item.put("kind", room.kind);
        item.put("name", room.name);
        item.put("members", new ArrayList<>(room.members));
        if (withMessages) {
            List<Map<String, Object>> messages = new ArrayList<>();
            for (Msg msg : room.messages) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", msg.id);
                row.put("fromId", msg.fromId);
                row.put("nick", msg.nick);
                row.put("text", msg.text);
                row.put("timeMs", msg.timeMs);
                row.put("fromAdmin", msg.fromAdmin);
                messages.add(row);
            }
            item.put("messages", messages);
        }
        return item;
    }
}
