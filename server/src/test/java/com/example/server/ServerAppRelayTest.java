package com.example.server;

import com.example.PeerFileRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerAppRelayTest {

    private static final String TOKEN = "punchclock-dev-secret";

    private Javalin app;
    private HttpClient http;
    private String base;

    @Before
    public void setUp() {
        app = new ServerApp().start(0);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    public void relayPassesChunksThroughMemoryAndNotifiesRecipient() throws Exception {
        heartbeat("worker-a", true);
        heartbeat("worker-b", true);

        HttpResponse<String> begin = beginRelay("worker-a", "worker-b", "報告.pdf", 7);
        assertEquals(200, begin.statusCode());
        JsonObject begun = json(begin.body());
        String relayId = begun.get("relayId").getAsString();
        assertEquals("WAITING", begun.get("state").getAsString());

        JsonObject hb = json(heartbeat("worker-b", true).body());
        String action = hb.getAsJsonArray("actions").get(0).getAsString();
        assertTrue(action.startsWith("RELAY|" + PeerFileRules.encodeName("worker-a") + "|" + relayId + "|"
                + PeerFileRules.encodeName("報告.pdf") + "|7|file|"));
        assertEquals("relay must not show up as a stored file", 0, hb.getAsJsonArray("files").size());

        assertEquals(200, send("POST", "/api/peer/relay/" + relayId + "/accept?clientId=worker-b", null).statusCode());
        JsonObject senderView = json(send("GET", "/api/peer/relay/" + relayId + "?clientId=worker-a", null).body());
        assertEquals("ACCEPTED", senderView.get("state").getAsString());
        assertTrue(senderView.get("canPush").getAsBoolean());

        assertEquals(200, send("PUT", "/api/peer/relay/" + relayId + "/chunk?clientId=worker-a&offset=0",
                "abcd".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(200, send("PUT", "/api/peer/relay/" + relayId + "/chunk?clientId=worker-a&offset=4",
                "efg".getBytes(StandardCharsets.UTF_8)).statusCode());

        HttpResponse<byte[]> first = take(relayId, 0);
        assertEquals(200, first.statusCode());
        assertEquals("0", first.headers().firstValue("X-Relay-Offset").orElse(""));
        assertArrayEquals("abcd".getBytes(StandardCharsets.UTF_8), first.body());
        HttpResponse<byte[]> second = take(relayId, 4);
        assertArrayEquals("efg".getBytes(StandardCharsets.UTF_8), second.body());

        assertEquals(200, send("POST", "/api/peer/relay/" + relayId + "/complete?clientId=worker-b", null).statusCode());
        JsonObject done = json(send("GET", "/api/peer/relay/" + relayId + "?clientId=worker-a&since=0&wait=1000",
                null).body());
        assertEquals("COMPLETED", done.get("state").getAsString());
        assertEquals(410, take(relayId, 7).statusCode());
    }

    @Test
    public void takeLongPollReturnsNoContentWhenNothingArrives() throws Exception {
        heartbeat("worker-b", true);
        String relayId = json(beginRelay("worker-a", "worker-b", "a.txt", 3).body()).get("relayId").getAsString();
        send("POST", "/api/peer/relay/" + relayId + "/accept?clientId=worker-b", null);
        HttpResponse<byte[]> empty = http.send(HttpRequest.newBuilder(URI.create(base + "/api/peer/relay/" + relayId
                        + "/chunk?clientId=worker-b&offset=0&wait=200"))
                        .header("Authorization", "Bearer " + TOKEN)
                        .timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(204, empty.statusCode());
    }

    @Test
    public void recipientDeclineIsVisibleToSender() throws Exception {
        heartbeat("worker-b", true);
        String relayId = json(beginRelay("worker-a", "worker-b", "a.txt", 3).body()).get("relayId").getAsString();
        assertEquals(200, send("DELETE", "/api/peer/relay/" + relayId + "?clientId=worker-b", null).statusCode());
        JsonObject status = json(send("GET", "/api/peer/relay/" + relayId + "?clientId=worker-a", null).body());
        assertEquals("DECLINED", status.get("state").getAsString());
        assertEquals(410, send("POST", "/api/peer/relay/" + relayId + "/accept?clientId=worker-b", null).statusCode());
    }

    @Test
    public void beginRejectsOfflineOrOldRecipients() throws Exception {
        HttpResponse<String> offline = beginRelay("worker-a", "nobody", "a.txt", 3);
        assertEquals(409, offline.statusCode());
        assertEquals("RECIPIENT_OFFLINE", json(offline.body()).get("code").getAsString());

        heartbeat("old-worker", false);
        HttpResponse<String> old = beginRelay("worker-a", "old-worker", "a.txt", 3);
        assertEquals(409, old.statusCode());
        assertEquals("RECIPIENT_UNSUPPORTED", json(old.body()).get("code").getAsString());

        heartbeat("worker-b", true);
        heartbeat("worker-b", false);
        assertEquals("capability is dropped when a downgraded client stops advertising it",
                409, beginRelay("worker-a", "worker-b", "a.txt", 3).statusCode());
    }

    @Test
    public void otherClientsCannotTouchRelay() throws Exception {
        heartbeat("worker-b", true);
        String relayId = json(beginRelay("worker-a", "worker-b", "a.txt", 3).body()).get("relayId").getAsString();
        assertEquals(403, send("POST", "/api/peer/relay/" + relayId + "/accept?clientId=worker-c", null).statusCode());
        assertEquals(403, send("GET", "/api/peer/relay/" + relayId + "?clientId=worker-c", null).statusCode());
        assertEquals(401, http.send(HttpRequest.newBuilder(URI.create(base + "/api/peer/relay/" + relayId
                        + "?clientId=worker-a")).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private HttpResponse<String> beginRelay(String from, String to, String filename, long size) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("fromClientId", from);
        body.addProperty("toClientId", to);
        body.addProperty("filename", filename);
        body.addProperty("kind", "file");
        body.addProperty("size", size);
        return send("POST", "/api/peer/relay", body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<byte[]> take(String relayId, long offset) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + "/api/peer/relay/" + relayId
                        + "/chunk?clientId=worker-b&offset=" + offset + "&wait=1000"))
                        .header("Authorization", "Bearer " + TOKEN)
                        .timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> send(String method, String path, byte[] body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        return http.send(HttpRequest.newBuilder(URI.create(base + path))
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Content-Type", "application/octet-stream")
                        .timeout(Duration.ofSeconds(10))
                        .method(method, publisher)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> heartbeat(String clientId, boolean relayCapable) throws Exception {
        String json = "{\"clientId\":\"" + clientId + "\",\"status\":\"ONLINE\",\"tasks\":[],\"heartbeatSeq\":1"
                + (relayCapable ? ",\"capabilities\":[\"relay\"]" : "") + "}";
        return http.send(HttpRequest.newBuilder(URI.create(base + "/api/heartbeat"))
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(String body) {
        return JsonParser.parseString(body).getAsJsonObject();
    }
}
