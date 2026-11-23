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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

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
                HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/clients/" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)))
                        .header("Cookie", cookie)
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}