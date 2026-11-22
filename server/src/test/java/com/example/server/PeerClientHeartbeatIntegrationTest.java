package com.example.server;

import com.example.service.HeartbeatService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 兩個獨立的桌面端身分（各自一個真正的 HeartbeatService）經真正的 ServerApp 互傳同事訊息
 * 與 Dashboard 取消指令。
 */
public class PeerClientHeartbeatIntegrationTest {

    private static final long WAIT_SECONDS = 10;

    private AdminTestServer server;
    private HttpClient http;
    private String base;
    private Desk alice;
    private Desk bob;

    @Before
    public void setUp() throws Exception {
        server = AdminTestServer.builder().start();
        base = server.baseUrl();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        alice = new Desk("alice");
        bob = new Desk("bob");
        alice.pump();
        bob.pump();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (alice != null) {
            alice.close();
        }
        if (bob != null) {
            bob.close();
        }
    }

    @Test(timeout = 30_000)
    public void plainMessagesArriveInOrderBothWays() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertTrue(alice.send("bob", "午安 " + i));
            assertTrue(bob.send("alice", "你好 " + i + "｜含分隔符號"));
        }
        bob.pump();
        alice.pump();
        assertEquals(List.of("alice>午安 0", "alice>午安 1", "alice>午安 2", "alice>午安 3", "alice>午安 4"), bob.chat());
        assertEquals(5, alice.chat().size());
        assertEquals("bob>你好 4｜含分隔符號", alice.chat().get(4));
        bob.pump();
        assertEquals(5, bob.chat().size());
    }

    @Test(timeout = 30_000)
    public void dashboardCancelReachesClientThroughHeartbeat() throws Exception {
        String cookie = server.loginCookie(http);
        HttpResponse<String> cancel = http.send(HttpRequest.newBuilder(
                        URI.create(base + "/api/clients/bob/cancel-task/t-1"))
                .timeout(Duration.ofSeconds(5))
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cancel.statusCode());
        assertTrue(alice.send("bob", "取消之後的訊息"));
        bob.pump();
        assertEquals(List.of("CANCEL_TASK:t-1"), bob.commands());
        assertEquals(List.of("alice>取消之後的訊息"), bob.chat());
    }

    @Test(timeout = 30_000)
    public void retiredGamePayloadDoesNotAppearAsChat() throws Exception {
        String payload = "GAME|bj|v1|invite|room-test|42";
        assertTrue(alice.send("bob", payload));
        bob.pump();
        assertTrue("舊版 21 點協定不應進入聊天紀錄", bob.chat().isEmpty());
        assertTrue(alice.send("bob", "一般訊息"));
        bob.pump();
        assertEquals(List.of("alice>一般訊息"), bob.chat());
    }

    private final class Desk {
        final String id;
        final HeartbeatService heartbeat = new HeartbeatService();
        private final List<String> chat = Collections.synchronizedList(new ArrayList<>());
        private final List<String> commands = Collections.synchronizedList(new ArrayList<>());

        Desk(String id) {
            this.id = id;
            heartbeat.setClientId(id);
            heartbeat.setCommandListener(this::onCommand);
            heartbeat.startHeartbeat(base, line -> { }, null);
        }

        void pump() throws Exception {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            heartbeat.sendPostHeartbeat(line -> { }, done::complete);
            assertTrue(id + " 心跳失敗", done.get(WAIT_SECONDS, TimeUnit.SECONDS));
        }

        boolean send(String to, String text) throws Exception {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            heartbeat.sendPeerMessage(to, text, line -> { }, done::complete);
            return Boolean.TRUE.equals(done.get(WAIT_SECONDS, TimeUnit.SECONDS));
        }

        List<String> chat() {
            return new ArrayList<>(chat);
        }

        List<String> commands() {
            return new ArrayList<>(commands);
        }

        private void onCommand(String command) {
            if (!command.startsWith("MSG|")) {
                commands.add(command);
                return;
            }
            String[] parts = command.split("\\|", 5);
            String from = parts[1];
            String text = parts.length > 4 ? parts[4] : "";
            if (text.startsWith("GAME|bj|v1|")) {
                return;
            }
            chat.add(from + ">" + text);
        }

        void close() {
            heartbeat.stopHeartbeat();
        }
    }
}