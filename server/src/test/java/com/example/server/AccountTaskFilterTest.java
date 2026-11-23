package com.example.server;

import com.example.server.auth.AuthConfig;
import com.example.server.auth.AuthService;
import com.example.server.store.AccountDataPaths;
import com.example.server.store.AccountStore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountTaskFilterTest {

    private static final String HB = "filter-test-hb-secret";
    private static final Gson GSON = new Gson();

    private Path accountDir;
    private AdminTestServer server;
    private HttpClient http;

    @Before
    public void setUp() throws Exception {
        accountDir = Files.createTempDirectory("account-filter");
        System.setProperty(AccountDataPaths.DIR_PROPERTY, accountDir.toString());
        String adminPw = "AdminPass9!";
        server = AdminTestServer.builder()
                .heartbeatSecret(HB)
                .adminPassword(adminPw)
                .accountDataDir(accountDir)
                .start();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        assertEquals(com.example.server.store.AccountStore.OperationResult.OK,
                server.authService().accountStore().createAccount("membera", AccountStore.ROLE_MEMBER, "MemberA9!!", false));
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (accountDir != null) {
            Files.walk(accountDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    public void memberSeesOnlyOwnDevices_adminSeesUnbound() throws Exception {
        heartbeat("legacy-unbound", null);
        String token = appLogin("membera", "MemberA9!!");
        heartbeat("device-a", token);

        String adminCookie = server.loginCookie(http);
        String memberCookie = AdminTestServer.loginCookie(server.baseUrl(), "membera", "MemberA9!!", http);

        List<Map<String, Object>> adminClients = statusClients(adminCookie);
        assertEquals(2, adminClients.size());

        List<Map<String, Object>> memberClients = statusClients(memberCookie);
        assertEquals(1, memberClients.size());
        assertEquals("device-a", memberClients.get(0).get("clientId"));

        HttpResponse<String> denied = deleteClient(memberCookie, "legacy-unbound");
        assertEquals(403, denied.statusCode());

        HttpResponse<String> ok = deleteClient(adminCookie, "legacy-unbound");
        assertEquals(200, ok.statusCode());
    }

    @Test
    public void websocketPayloadFiltersByRole() throws Exception {
        heartbeat("ws-unbound", null);
        String token = appLogin("membera", "MemberA9!!");
        heartbeat("ws-device-a", token);

        String adminCookie = server.loginCookie(http);
        String memberCookie = AdminTestServer.loginCookie(server.baseUrl(), "membera", "MemberA9!!", http);

        int adminCount = dashboardClientCount(adminCookie);
        int memberCount = dashboardClientCount(memberCookie);
        assertEquals(2, adminCount);
        assertEquals(1, memberCount);
    }

    @Test
    public void heartbeatPeersSpanAccounts() throws Exception {
        assertEquals(com.example.server.store.AccountStore.OperationResult.OK,
                server.authService().accountStore().createAccount("memberb", AccountStore.ROLE_MEMBER, "MemberB9!!", false));
        String tokenA = appLogin("membera", "MemberA9!!");
        String tokenB = appLogin("memberb", "MemberB9!!");
        heartbeat("peer-dev-a", tokenA);
        heartbeat("peer-dev-b", tokenB);

        Map<String, Object> hb = heartbeatJson("peer-dev-a", tokenA);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> peers = (List<Map<String, Object>>) hb.get("peers");
        assertTrue(peers.stream().anyMatch(p -> "peer-dev-b".equals(String.valueOf(p.get("clientId")))));
    }

    @Test
    public void adminAssignMakesMemberSeeDevice() throws Exception {
        heartbeat("assign-dev", null);
        String adminCookie = server.loginCookie(http);
        String body = "{\"username\":\"membera\"}";
        HttpResponse<String> assign = http.send(
                server.writeRequest(adminCookie, HttpRequest.newBuilder(
                        URI.create(server.baseUrl() + "/api/devices/assign-dev/assign")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, assign.statusCode());

        String memberCookie = AdminTestServer.loginCookie(server.baseUrl(), "membera", "MemberA9!!", http);
        List<Map<String, Object>> memberClients = statusClients(memberCookie);
        assertEquals(1, memberClients.size());
        assertEquals("assign-dev", memberClients.get(0).get("clientId"));
    }

    @Test
    public void disableAccountRevokesAppToken() throws Exception {
        String token = appLogin("membera", "MemberA9!!");
        heartbeat("off-dev", token);
        String adminCookie = server.loginCookie(http);
        HttpResponse<String> patch = http.send(
                server.writeRequest(adminCookie, HttpRequest.newBuilder(
                        URI.create(server.baseUrl() + "/api/accounts/membera")))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"enabled\":false}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, patch.statusCode());

        HttpResponse<String> appAgain = http.send(
                HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/app/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"membera\",\"password\":\"MemberA9!!\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(401, appAgain.statusCode());
    }

    @Test
    public void logoutClosesDashboardSocketWith4401() throws Exception {
        String cookie = server.loginCookie(http);
        CompletableFuture<Integer> closed = new CompletableFuture<>();
        URI uri = URI.create(server.baseUrl().replace("http://", "ws://") + "/ws/dashboard");
        WebSocket socket = http.newWebSocketBuilder()
                .header("Cookie", cookie)
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, new WebSocket.Listener() {
                    @Override
                    public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        closed.complete(statusCode);
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);

        HttpResponse<String> logout = http.send(
                server.writeRequest(cookie, HttpRequest.newBuilder(URI.create(server.baseUrl() + "/logout")))
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, logout.statusCode());
        assertEquals(4401, (int) closed.get(5, TimeUnit.SECONDS));
        socket.abort();
    }

    @Test
    public void crossOriginWriteRejected() throws Exception {
        String adminCookie = server.loginCookie(http);
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/clients/nope"))
                        .DELETE()
                        .header("Cookie", adminCookie)
                        .header("Origin", "http://evil.example")
                        .header("X-Csrf-Token", server.csrfForAuthCookie(adminCookie))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(403, res.statusCode());
    }

  @Test
    public void boundClientCannotSpoofFromClientId() throws Exception {
        String token = appLogin("membera", "MemberA9!!");
        heartbeat("device-a", token);
        heartbeat("device-b", token);

        HttpResponse<String> spoof = http.send(
                HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/peer/message"))
                        .header("Authorization", "Bearer " + HB)
                        .header("X-PunchClock-Client", "device-a")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"fromClientId\":\"device-b\",\"toClientId\":\"device-a\",\"text\":\"hi\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(403, spoof.statusCode());
    }

    private List<Map<String, Object>> statusClients(String cookie) throws Exception {
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/status"))
                        .header("Cookie", cookie)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, res.statusCode());
        Map<String, Object> body = GSON.fromJson(res.body(), new TypeToken<Map<String, Object>>() { }.getType());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clients = (List<Map<String, Object>>) body.get("clients");
        return clients;
    }

    private String appLogin(String username, String password) throws Exception {
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/app/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, res.statusCode());
        Map<String, Object> body = GSON.fromJson(res.body(), new TypeToken<Map<String, Object>>() { }.getType());
        return String.valueOf(body.get("token"));
    }

    private void heartbeat(String clientId, String appToken) throws Exception {
        String json = "{\"clientId\":\"" + clientId + "\",\"status\":\"ONLINE\",\"tasks\":[]";
        if (appToken != null) {
            json += ",\"appToken\":\"" + appToken + "\"";
        }
        json += "}";
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/heartbeat"))
                        .header("Authorization", "Bearer " + HB)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertTrue(res.statusCode() == 200);
    }

    private HttpResponse<String> deleteClient(String cookie, String clientId) throws Exception {
        return http.send(
                server.writeRequest(cookie, HttpRequest.newBuilder(
                        URI.create(server.baseUrl() + "/api/clients/" + URLEncoder.encode(clientId, StandardCharsets.UTF_8))))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private int dashboardClientCount(String cookie) throws Exception {
        CompletableFuture<String> first = new CompletableFuture<>();
        URI uri = URI.create(server.baseUrl().replace("http://", "ws://") + "/ws/dashboard");
        WebSocket socket = http.newWebSocketBuilder()
                .header("Cookie", cookie)
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, new WebSocket.Listener() {
                    @Override
                    public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        first.complete(data.toString());
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);
        String json = first.get(5, TimeUnit.SECONDS);
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        Map<String, Object> body = GSON.fromJson(json, new TypeToken<Map<String, Object>>() { }.getType());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clients = (List<Map<String, Object>>) body.get("clients");
        return clients == null ? 0 : clients.size();
    }

    private Map<String, Object> heartbeatJson(String clientId, String appToken) throws Exception {
        String json = "{\"clientId\":\"" + clientId + "\",\"status\":\"ONLINE\",\"tasks\":[]";
        if (appToken != null) {
            json += ",\"appToken\":\"" + appToken + "\"";
        }
        json += "}";
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/heartbeat"))
                        .header("Authorization", "Bearer " + HB)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, res.statusCode());
        return GSON.fromJson(res.body(), new TypeToken<Map<String, Object>>() { }.getType());
    }
}