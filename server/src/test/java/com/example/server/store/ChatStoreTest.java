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
    public void rejectsEmptyText() {
        ChatStore store = new ChatStore();
        assertFalse(store.sendFromAdmin("dm:worker-a", "小王", "  ").ok);
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
