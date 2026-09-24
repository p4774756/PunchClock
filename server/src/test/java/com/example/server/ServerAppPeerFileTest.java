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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertEquals("application/octet-stream",
                download.headers().firstValue("Content-Type").orElse("").split(";")[0].trim());

        HttpResponse<String> hbAfter = heartbeat("worker-b");
        JsonObject hbAfterJson = JsonParser.parseString(hbAfter.body()).getAsJsonObject();
        assertTrue(hbAfterJson.has("files"));
        assertEquals(1, hbAfterJson.getAsJsonArray("files").size());
        assertEquals("downloaded", hbAfterJson.getAsJsonArray("files").get(0).getAsJsonObject()
                .get("status").getAsString());
    }

    @Test
    public void unicodeRecipientCanDownloadEvenWhenNormalizationDiffers() throws Exception {
        String nfd = java.text.Normalizer.normalize("café-mac", java.text.Normalizer.Form.NFD);
        String nfc = java.text.Normalizer.normalize("café-mac", java.text.Normalizer.Form.NFC);
        byte[] payload = "from-windows".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> upload = postFile("win-pc", nfd, "notes.txt", payload);
        assertEquals(200, upload.statusCode());
        String fileId = JsonParser.parseString(upload.body()).getAsJsonObject().get("fileId").getAsString();

        HttpResponse<byte[]> download = download(fileId, nfc);
        assertEquals(200, download.statusCode());
        assertEquals("from-windows", new String(download.body(), StandardCharsets.UTF_8));

        HttpResponse<byte[]> encodedHeader = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/peer/file/" + fileId))
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("X-PunchClock-Client",
                                java.net.URLEncoder.encode(nfc, StandardCharsets.UTF_8))
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, encodedHeader.statusCode());
    }

    @Test
    public void senderCanRedownloadAndUnknownExtensionIsAccepted() throws Exception {
        HttpResponse<String> upload = postFile("worker-a", "worker-b", "ok.txt",
                "x".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, upload.statusCode());
        String fileId = JsonParser.parseString(upload.body()).getAsJsonObject().get("fileId").getAsString();

        assertEquals(200, download(fileId, "worker-a").statusCode());
        assertEquals(401, http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/peer/file/" + fileId + "?clientId=worker-b"))
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray()).statusCode());
        assertEquals(403, download(fileId, "worker-c").statusCode());

        HttpResponse<String> exe = postFile("worker-a", "worker-b", "payload.exe",
                "MZ".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, exe.statusCode());
        assertTrue(JsonParser.parseString(exe.body()).getAsJsonObject().get("success").getAsBoolean());

        HttpResponse<String> self = postFile("worker-a", "worker-a", "ok.txt",
                "x".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, self.statusCode());
    }

    @Test
    public void adminCanListDownloadAndDeleteFiles() throws Exception {
        HttpResponse<String> upload = postFile("worker-a", "worker-b", "notes.bin",
                "bin-body".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, upload.statusCode());
        String fileId = JsonParser.parseString(upload.body()).getAsJsonObject().get("fileId").getAsString();
        String cookie = adminCookie();

        HttpResponse<String> status = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/status"))
                        .header("Cookie", cookie)
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, status.statusCode());
        JsonObject payload = JsonParser.parseString(status.body()).getAsJsonObject();
        assertTrue(payload.has("files"));
        assertTrue(payload.has("serverHealth"));
        assertEquals(fileId, payload.getAsJsonArray("files").get(0).getAsJsonObject().get("fileId").getAsString());
        assertTrue(payload.getAsJsonObject("serverHealth").get("heapUsedBytes").getAsLong() > 0);
        assertEquals(1, payload.getAsJsonObject("serverHealth").get("fileOfferCount").getAsInt());

        HttpResponse<byte[]> adminDownload = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/peer/file/" + fileId))
                        .header("Cookie", cookie)
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, adminDownload.statusCode());
        assertEquals("bin-body", new String(adminDownload.body(), StandardCharsets.UTF_8));

        HttpResponse<String> deleted = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/peer/file/" + fileId))
                        .header("Cookie", cookie)
                        .DELETE().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleted.statusCode());
        assertEquals(404, download(fileId, "worker-b").statusCode());
    }

    @Test
    public void workerCanDeleteOwnOffer() throws Exception {
        HttpResponse<String> upload = postFile("worker-a", "worker-b", "temp.txt",
                "z".getBytes(StandardCharsets.UTF_8));
        String fileId = JsonParser.parseString(upload.body()).getAsJsonObject().get("fileId").getAsString();
        HttpResponse<String> deleted = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/peer/file/" + fileId + "?clientId=worker-a"))
                        .header("Authorization", "Bearer " + TOKEN)
                        .DELETE().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleted.statusCode());
        assertEquals(404, download(fileId, "worker-b").statusCode());
    }

    @Test
    public void downloadIsNotGzippedEvenWhenProxyAcceptsGzip() throws Exception {
        StringBuilder md = new StringBuilder();
        while (md.length() < 4800) {
            md.append("## 打卡紀錄 08:59 上班、18:01 下班，狀態 OK\n");
        }
        byte[] payload = md.toString().getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> upload = postFile("worker-a", "worker-b", "notes.md", payload);
        assertEquals(200, upload.statusCode());
        String fileId = JsonParser.parseString(upload.body()).getAsJsonObject().get("fileId").getAsString();

        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/peer/file/" + fileId + "?clientId=worker-b"))
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Accept-Encoding", "gzip")
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, download.statusCode());
        assertFalse(download.headers().firstValue("Content-Encoding").isPresent());
        assertEquals(String.valueOf(payload.length),
                download.headers().firstValue("Content-Length").orElse(""));
        assertArrayEquals(payload, download.body());
    }

    @Test
    public void uploadAcceptsFileLargerThanFormerFiveMegLimit() throws Exception {
        byte[] sixMb = new byte[6 * 1024 * 1024];
        HttpResponse<String> txt = postFile("worker-a", "worker-b", "big.txt", sixMb);
        assertEquals(200, txt.statusCode());
        JsonObject body = JsonParser.parseString(txt.body()).getAsJsonObject();
        assertTrue(body.get("success").getAsBoolean());
        assertEquals(sixMb.length, body.get("size").getAsInt());
    }

    @Test
    public void uploadedFileErrorMessage_explainsSizeLimit() {
        String message = ServerApp.uploadedFileErrorMessage(
                new IllegalStateException("Multipart Mime part file exceeds max filesize"));
        assertTrue(message.contains(PeerFileRules.MAX_SIZE_LABEL));
        assertTrue(message.contains("上限"));
        assertTrue(message.contains("max filesize"));
    }

    @Test
    public void uploadedFileErrorMessage_explainsOutOfMemory() {
        String message = ServerApp.uploadedFileErrorMessage(new OutOfMemoryError("Java heap space"));
        assertTrue(message.contains("記憶體不足"));
        assertTrue(ServerApp.isMemoryError(new IllegalStateException("Java heap space")));
    }

    private HttpResponse<String> postFile(String from, String to, String filename, byte[] bytes) throws Exception {
        String boundary = "TestBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, from, to, filename, bytes);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/peer/file"))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(30))
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
        String encoded = java.net.URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(base + "/api/peer/file/" + fileId + "?clientId=" + encoded))
                .header("Authorization", "Bearer " + TOKEN)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private String adminCookie() throws Exception {
        HttpResponse<String> login = http.send(
                HttpRequest.newBuilder(URI.create(base + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString("password=secret"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String setCookie = login.headers().firstValue("set-cookie")
                .orElse(login.headers().firstValue("Set-Cookie").orElse(""));
        assertTrue("login should set auth cookie, status=" + login.statusCode() + " cookie=" + setCookie,
                setCookie.contains("auth="));
        int start = setCookie.indexOf("auth=");
        int end = setCookie.indexOf(';', start);
        return end > start ? setCookie.substring(start, end) : setCookie.substring(start);
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
