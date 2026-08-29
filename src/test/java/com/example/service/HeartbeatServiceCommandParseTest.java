package com.example.service;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class HeartbeatServiceCommandParseTest {

    @Test
    public void parseServerCommand_supportsActionsArrayAndLegacyAction() throws Exception {
        HeartbeatService service = new HeartbeatService();
        List<String> received = new ArrayList<>();
        service.setCommandListener(received::add);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);

        String body = "{"
                + "\"success\":true,"
                + "\"action\":\"CANCEL_TASK:abc123\","
                + "\"actions\":[\"CANCEL_TASK:abc123\",\"CANCEL_SCHEDULE\"]"
                + "}";
        method.invoke(service, body, (Consumer<String>) msg -> {});

        assertEquals(2, received.size());
        assertTrue(received.contains("CANCEL_TASK:abc123"));
        assertTrue(received.contains("CANCEL_SCHEDULE"));
    }

    @Test
    public void parseServerCommand_ignoresNone() throws Exception {
        HeartbeatService service = new HeartbeatService();
        AtomicReference<String> received = new AtomicReference<>();
        service.setCommandListener(received::set);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);
        method.invoke(service, "{\"action\":\"NONE\",\"actions\":[]}", (Consumer<String>) msg -> {});

        assertNull(received.get());
    }

    @Test
    public void parseServerCommand_supportsPeerMessageAndPoke() throws Exception {
        HeartbeatService service = new HeartbeatService();
        List<String> received = new ArrayList<>();
        service.setCommandListener(received::add);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);

        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("記得打卡".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String body = "{"
                + "\"actions\":[\"MSG|worker-a|" + encoded + "\",\"POKE|worker-b\"]"
                + "}";
        method.invoke(service, body, (Consumer<String>) msg -> {});

        assertEquals(2, received.size());
        assertTrue(received.contains("MSG|worker-a|記得打卡"));
        assertTrue(received.contains("POKE|worker-b"));
    }

    @Test
    public void parseServerCommand_parsesPeersArray() throws Exception {
        HeartbeatService service = new HeartbeatService();
        List<HeartbeatService.PeerInfo> peers = new ArrayList<>();
        service.setPeersListener(peers::addAll);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);
        String body = "{"
                + "\"peers\":[{\"clientId\":\"worker-b\",\"status\":\"ONLINE\","
                + "\"appVersion\":\"1.0.0\",\"taskCount\":2,\"scheduledCount\":1,"
                + "\"lastSeen\":\"2026-08-29T10:00:00.000Z\"}]"
                + "}";
        method.invoke(service, body, (Consumer<String>) msg -> {});

        assertEquals(1, peers.size());
        assertEquals("worker-b", peers.get(0).clientId);
        assertEquals("ONLINE", peers.get(0).status);
        assertEquals(1, peers.get(0).scheduledCount);
    }
}
