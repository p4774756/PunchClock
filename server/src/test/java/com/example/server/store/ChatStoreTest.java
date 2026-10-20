package com.example.server.store;

import com.example.ChatProtocol;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ChatStoreTest {

    @Test
    public void adminDm_roundTripsAndClientCanReply() {
        ChatStore store = new ChatStore();
        ChatStore.Outcome sent = store.sendFromAdmin("dm:worker-a", "小王", "記得打卡|換行");
        assertTrue(sent.message, sent.ok);
        assertEquals(List.of("worker-a"), sent.targets);

        ChatProtocol.RoomMessage decoded = ChatProtocol.decodeRoom(sent.action);
        assertNotNull(decoded);
        assertEquals("dm:worker-a", decoded.roomId);
        assertEquals("worker-a", decoded.roomName);
        assertEquals(ChatProtocol.ADMIN_ID, decoded.fromId);
        assertEquals("小王", decoded.nick);
        assertEquals("記得打卡|換行", decoded.text);

        ChatStore.Outcome reply = store.sendFromClient("worker-a", "dm:worker-a", "收到");
        assertTrue(reply.ok);
        assertTrue(reply.targets.isEmpty());

        List<Map<String, Object>> rooms = store.snapshot();
        assertEquals(1, rooms.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) rooms.get(0).get("messages");
        assertEquals(2, messages.size());
        assertEquals("收到", messages.get(1).get("text"));
        assertEquals(Boolean.FALSE, messages.get(1).get("fromAdmin"));
    }

    @Test
    public void clientCannotEnterSomeoneElsesDm() {
        ChatStore store = new ChatStore();
        assertTrue(store.sendFromAdmin("dm:worker-a", "", "hi").ok);
        ChatStore.Outcome stolen = store.sendFromClient("worker-b", "dm:worker-a", "偷看");
        assertFalse(stolen.ok);
    }

    @Test
    public void groupFansOutExceptSender() {
        ChatStore store = new ChatStore();
        ChatStore.Outcome created = store.createGroup("早班", List.of("a", "b", "a", "後台", ""));
        assertTrue(created.message, created.ok);

        String groupId = store.snapshot().get(0).get("id").toString();
        ChatStore.Outcome fromA = store.sendFromClient("a", groupId, "早");
        assertTrue(fromA.ok);
        assertEquals(List.of("b"), fromA.targets);

        ChatStore.Outcome outsider = store.sendFromClient("c", groupId, "我要進來");
        assertFalse(outsider.ok);

        ChatStore.Outcome fromDesk = store.sendFromAdmin(groupId, "店長", "集合");
        assertTrue(fromDesk.ok);
        assertEquals(List.of("a", "b"), fromDesk.targets);

        List<Map<String, Object>> visible = store.clientRooms("b");
        assertEquals(1, visible.size());
        assertEquals("早班", visible.get(0).get("name"));
        assertTrue(store.clientRooms("c").isEmpty());
    }

    @Test
    public void updateAndDeleteGroup() {
        ChatStore store = new ChatStore();
        assertTrue(store.createGroup("早班", List.of("a")).ok);
        String groupId = store.snapshot().get(0).get("id").toString();
        assertTrue(store.updateGroup(groupId, "晚班", List.of("b", "c")).ok);
        assertEquals("晚班", store.snapshot().get(0).get("name"));
        assertEquals(List.of("b", "c"), store.snapshot().get(0).get("members"));
        assertTrue(store.clientRooms("a").isEmpty());
        assertTrue(store.deleteGroup(groupId).ok);
        assertTrue(store.snapshot().isEmpty());
        assertFalse(store.deleteGroup(groupId).ok);
    }

    @Test
    public void rejectsEmptyTextAndBlankGroup() {
        ChatStore store = new ChatStore();
        assertFalse(store.sendFromAdmin("dm:worker-a", "小王", "  ").ok);
        assertFalse(store.createGroup("  ", List.of("a")).ok);
        assertFalse(store.createGroup("早班", List.of()).ok);
    }

    @Test
    public void keepsOnlyLatestMessages() {
        ChatStore store = new ChatStore();
        for (int i = 0; i < ChatProtocol.MAX_MESSAGES + 3; i++) {
            assertTrue(store.sendFromAdmin("dm:worker-a", "小王", "m" + i).ok);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) store.snapshot().get(0).get("messages");
        assertEquals(ChatProtocol.MAX_MESSAGES, messages.size());
        assertEquals("m3", messages.get(0).get("text"));
        assertEquals("m" + (ChatProtocol.MAX_MESSAGES + 2), messages.get(messages.size() - 1).get("text"));
    }
}
