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
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerAppPeerFileTest {

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
    public void uploadQueuesFileActionAndRecipientCanDownload() throws Exception {
        byte[] payload = "peer-file-body".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> upload = postFile("worker-a", "worker-b", "備忘.txt", payload);
        assertEquals(200, upload.statusCode());
        JsonObject uploaded = JsonParser.parseString(upload.body()).getAsJsonObject();
        assertTrue(uploaded.get("success").getAsBoolean());
        String fileId = uploaded.get("fileId").getAsString();
        assertEquals("備忘.txt", uploaded.get("filename").getAsString());

        HttpResponse<String> heartbeat = heartbeat("worker-b");
        assertEquals(200, heartbeat.statusCode());
        JsonObject hb = JsonParser.parseString(heartbeat.body()).getAsJsonObject();
        String action = hb.getAsJsonArray("actions").get(0).getAsString();
        assertTrue(action.startsWith("FILE|worker-a|" + fileId + "|"));
        assertTrue(action.contains(PeerFileRules.encodeName("備忘.txt")));

        HttpResponse<byte[]> download = download(fileId, "worker-b");
        assertEquals(200, download.statusCode());
        assertEquals("peer-file-body", new String(download.body(), StandardCharsets.UTF_8));
        assertTrue(download.headers().firstValue("Content-Disposition").orElse("").contains("filename"));
    }

    @Test
    public void senderCannotDownloadAndUnknownTypeIsRejected() throws Exception {
        HttpResponse<String> upload = postFile("worker-a", "worker-b", "ok.txt",
                "x".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, upload.statusCode());
        String fileId = JsonParser.parseString(upload.body()).getAsJsonObject().get("fileId").getAsString();

        assertEquals(403, download(fileId, "worker-a").statusCode());
        assertEquals(401, http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/peer/file/" + fileId + "?clientId=worker-b"))
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray()).statusCode());

        HttpResponse<String> exe = postFile("worker-a", "worker-b", "payload.exe",
                "MZ".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, exe.statusCode());
        assertTrue(JsonParser.parseString(exe.body()).getAsJsonObject().get("message").getAsString()
                .contains("不支援"));

        HttpResponse<String> self = postFile("worker-a", "worker-a", "ok.txt",
                "x".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, self.statusCode());
    }

    private HttpResponse<String> postFile(String from, String to, String filename, byte[] bytes) throws Exception {
        String boundary = "TestBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, from, to, filename, bytes);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/peer/file"))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> heartbeat(String clientId) throws Exception {
        String json = "{\"clientId\":\"" + clientId + "\",\"status\":\"ONLINE\",\"tasks\":[],\"heartbeatSeq\":1}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/heartbeat"))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> download(String fileId, String clientId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(base + "/api/peer/file/" + fileId + "?clientId=" + clientId))
                .header("Authorization", "Bearer " + TOKEN)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static byte[] multipart(String boundary, String from, String to, String filename, byte[] bytes)
            throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        writeField(out, boundary, crlf, "fromClientId", from);
        writeField(out, boundary, crlf, "toClientId", to);
        writeField(out, boundary, crlf, "filename", filename);
        out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"")
                .getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
        out.write("Content-Type: application/octet-stream".getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
        out.write(crlf);
        out.write(bytes);
        out.write(crlf);
        out.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
        return out.toByteArray();
    }

    private static void writeField(java.io.ByteArrayOutputStream out, String boundary, byte[] crlf,
                                   String name, String value) throws Exception {
        out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"").getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
        out.write(crlf);
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
    }
}
