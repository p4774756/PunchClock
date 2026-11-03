package com.example.service;

import com.example.MarqueeNotice;
import com.example.PeerFileRules;
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
                + "\"actions\":[\"MSG|worker-a|" + encoded + "|1693728000000\",\"POKE|worker-b|1693728001000\"]"
                + "}";
        method.invoke(service, body, (Consumer<String>) msg -> {});

        assertEquals(2, received.size());
        assertTrue(received.contains("MSG|worker-a|1693728000000||記得打卡"));
        assertTrue(received.contains("POKE|worker-b|1693728001000|"));
    }

    @Test
    public void parseServerCommand_peerPokeWithoutTimestamp_stillWorks() throws Exception {
        HeartbeatService service = new HeartbeatService();
        List<String> received = new ArrayList<>();
        service.setCommandListener(received::add);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);
        method.invoke(service, "{\"actions\":[\"POKE|worker-b\"]}", (Consumer<String>) msg -> {});

        assertEquals(1, received.size());
        assertEquals("POKE|worker-b||", received.get(0));
    }

    @Test
    public void parseServerCommand_peerMessageWithoutTimestamp_stillWorks() throws Exception {
        HeartbeatService service = new HeartbeatService();
        List<String> received = new ArrayList<>();
        service.setCommandListener(received::add);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);

        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("hi".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        method.invoke(service, "{\"actions\":[\"MSG|worker-a|" + encoded + "\"]}",
                (Consumer<String>) msg -> {});

        assertEquals(1, received.size());
        assertEquals("MSG|worker-a|||hi", received.get(0));
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

    @Test
    public void parseServerCommand_forwardsPeerAvatar() throws Exception {
        HeartbeatService service = new HeartbeatService();
        List<String> received = new ArrayList<>();
        service.setCommandListener(received::add);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);

        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("記得打卡".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        method.invoke(service,
                "{\"actions\":[\"MSG|worker-a|" + encoded + "|1693728000000|abcXYZ012\","
                        + "\"POKE|worker-b|1693728001000|abcXYZ012\"]}",
                (Consumer<String>) msg -> {});

        assertEquals(2, received.size());
        assertTrue(received.contains("MSG|worker-a|1693728000000|abcXYZ012|記得打卡"));
        assertTrue(received.contains("POKE|worker-b|1693728001000|abcXYZ012"));
    }

    @Test
    public void parseServerCommand_forwardsPeerImageMessage() throws Exception {
        HeartbeatService service = new HeartbeatService();
        List<String> received = new ArrayList<>();
        service.setCommandListener(received::add);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);

        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("看這張|圖".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        method.invoke(service,
                "{\"actions\":[\"MSGI|worker-a|" + encoded + "|1693728000000|abcXYZ012|imgABC-_\","
                        + "\"MSGI|worker-b||1693728001000||imgOnly\"]}",
                (Consumer<String>) msg -> {});

        assertEquals(2, received.size());
        assertEquals("MSGI|worker-a|1693728000000|abcXYZ012|imgABC-_|看這張|圖", received.get(0));
        assertEquals("MSGI|worker-b|1693728001000||imgOnly|", received.get(1));
    }

    @Test
    public void parseServerCommand_forwardsPeerRelayOffer() throws Exception {
        HeartbeatService service = new HeartbeatService();
        List<String> received = new ArrayList<>();
        service.setCommandListener(received::add);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);

        String from = PeerFileRules.encodeName("WIN|LAB");
        String name = PeerFileRules.encodeName("報告|v2.zip");
        method.invoke(service,
                "{\"actions\":[\"RELAY|" + from + "|relay42|" + name + "|2048|folder|1693728000000\"]}",
                (Consumer<String>) msg -> {});

        assertEquals(1, received.size());
        assertEquals("RELAY|" + from + "|relay42|2048|folder|1693728000000|報告|v2.zip", received.get(0));
    }

    @Test
    public void parseServerCommand_readsMarqueeAndHidesWhenOff() throws Exception {
        HeartbeatService service = new HeartbeatService();
        AtomicReference<MarqueeNotice> received = new AtomicReference<>();
        service.setMarqueeListener(received::set);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);
        method.invoke(service,
                "{\"marquee\":{\"enabled\":true,\"text\":\"  下午三點\\n集合  \"}}",
                (Consumer<String>) msg -> {});
        assertTrue(received.get().enabled);
        assertEquals("下午三點 集合", received.get().text);

        method.invoke(service,
                "{\"marquee\":{\"enabled\":false,\"text\":\"下午三點 集合\"}}",
                (Consumer<String>) msg -> {});
        assertFalse(received.get().enabled);
        assertEquals("下午三點 集合", received.get().text);
    }

    @Test
    public void parseUpdateNotice_requiresDigestHashSizeAndFixedPath() {
        String digest = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String sha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String body = "{\"update\":{\"version\":\"1.8.0\",\"sourceDigest\":\"" + digest
                + "\",\"sha256\":\"" + sha + "\",\"size\":12,\"path\":\"/api/client-release/jar\"}}";
        HeartbeatService.UpdateNotice notice = HeartbeatService.parseUpdateNotice(body);
        assertNotNull(notice);
        assertEquals("1.8.0", notice.version);
        assertEquals(digest, notice.sourceDigest);
        assertEquals(sha, notice.sha256);
        assertEquals(12L, notice.size);
        assertEquals("舊 server 沒帶 releaseTime", "", notice.releaseTime);

        String withTime = "{\"update\":{\"version\":\"1.8.0\",\"sourceDigest\":\"" + digest
                + "\",\"releaseTime\":\"2026-10-20T07:58:00Z\",\"sha256\":\"" + sha
                + "\",\"size\":12,\"path\":\"/api/client-release/jar\"}}";
        assertEquals("2026-10-20T07:58:00Z", HeartbeatService.parseUpdateNotice(withTime).releaseTime);
        assertTrue(HeartbeatService.parseUpdateNotice(withTime).changes.isEmpty());

        String withChanges = "{\"update\":{\"version\":\"1.8.0\",\"sourceDigest\":\"" + digest
                + "\",\"sha256\":\"" + sha + "\",\"size\":12,\"path\":\"/api/client-release/jar\","
                + "\"changes\":[\"  聊天視窗只晃一下  \",\"\",\"bad\\nline\"],\"changesMore\":2}}";
        HeartbeatService.UpdateNotice detailed = HeartbeatService.parseUpdateNotice(withChanges);
        assertEquals(List.of("聊天視窗只晃一下", "bad line"), detailed.changes);
        assertEquals(2, detailed.changesMore);

        assertNull(HeartbeatService.parseUpdateNotice(
                "{\"update\":{\"version\":\"1.8.0\",\"sourceDigest\":\"" + digest
                        + "\",\"sha256\":\"" + sha + "\",\"size\":12,\"path\":\"https://evil.example/jar\"}}"));
        assertNull(HeartbeatService.parseUpdateNotice("{\"success\":true}"));
    }
}
