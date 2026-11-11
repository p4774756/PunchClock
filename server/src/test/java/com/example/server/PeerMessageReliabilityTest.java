package com.example.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 同事訊息經真正的 ServerApp 與 HTTP 心跳收送，驗證「伺服器回 200 的訊息」不會遺失、重複或亂序。
 * 收件端用高頻心跳收取，刻意放大排入佇列與 drain 交錯的機會。
 */
public class PeerMessageReliabilityTest {

    private static final String TOKEN = "punchclock-dev-secret";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    enum SendStatus { OK, NON_200, TIMEOUT, ERROR }

    private Javalin app;
    private String base;
    private HttpClient http;
    private final AtomicInteger heartbeatFailures = new AtomicInteger();

    @Before
    public void setUp() {
        app = new ServerApp().start(0);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test(timeout = 60_000)
    public void singleSenderWhileRecipientHeartbeats() throws Exception {
        heartbeat("bob", null);
        Inbox bob = new Inbox("bob", null).start();
        Ledger ledger = new Ledger();
        for (int i = 0; i < 400; i++) {
            String text = "solo-" + i;
            ledger.record("alice", text, send("alice", "bob", text));
        }
        ledger.verify("單一發送者高頻送訊息", bob.finish(), true);
    }

    @Test(timeout = 60_000)
    public void concurrentSendersShareOneRecipientQueue() throws Exception {
        heartbeat("bob", null);
        Inbox bob = new Inbox("bob", null).start();
        Ledger ledger = new Ledger();
        int senders = 8;
        ExecutorService pool = Executors.newFixedThreadPool(senders);
        try {
            List<CompletableFuture<Void>> jobs = new ArrayList<>();
            for (int s = 0; s < senders; s++) {
                String from = "sender-" + s;
                jobs.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < 60; i++) {
                        String text = from + "-" + i;
                        ledger.record(from, text, send(from, "bob", text));
                    }
                }, pool));
            }
            CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0])).get(40, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        ledger.verify("多執行緒同時排入同一收件者", bob.finish(), true);
    }

    @Test(timeout = 60_000)
    public void burstQueueInterleavesWithDrain() throws Exception {
        heartbeat("bob", null);
        Inbox bob = new Inbox("bob", null).start();
        Ledger ledger = new Ledger();
        List<CompletableFuture<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            String text = "burst-" + i;
            jobs.add(sendAsync("alice", "bob", text).thenAccept(status -> ledger.record("alice", text, status)));
        }
        CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0])).get(40, TimeUnit.SECONDS);
        // 300 則同時發出，彼此沒有先後關係，只驗不遺失、不重複
        ledger.verify("排入與 drain 同時發生", bob.finish(), false);
    }

    @Test(timeout = 60_000)
    public void clientMetadataUpdatesDoNotDropQueuedMessages() throws Exception {
        heartbeat("bob", null);
        heartbeat("carol", null);
        String cookie = login();
        // 收件者每次心跳都帶不同任務，讓伺服器同時改寫它的 client 資料
        Inbox bob = new Inbox("bob", i -> "[{\"id\":\"t1\",\"name\":\"上班\",\"status\":\""
                + (i % 2 == 0 ? "SCHEDULED" : "PENDING") + "\"}]").start();
        AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService noise = Executors.newFixedThreadPool(3);
        try {
            noise.submit(() -> {
                int i = 0;
                while (!stop.get()) {
                    // 發送者自己的心跳（帶頭像）與其他 client 的心跳
                    safeHeartbeat("alice", "[{\"id\":\"a" + (i++ % 3) + "\",\"name\":\"打卡\",\"status\":\"PENDING\"}]");
                    safeHeartbeat("carol", null);
                }
            });
            noise.submit(() -> {
                while (!stop.get()) {
                    send("alice", "carol", "noise");
                    sendPoke("alice", "bob");
                }
            });
            noise.submit(() -> {
                while (!stop.get()) {
                    getStatus(cookie);
                }
            });
            Ledger ledger = new Ledger();
            for (int i = 0; i < 250; i++) {
                String text = "meta-" + i;
                ledger.record("alice", text, send("alice", "bob", text));
            }
            stop.set(true);
            ledger.verify("其他 client 資料更新與排入同時發生", bob.finish(), true);
        } finally {
            stop.set(true);
            noise.shutdownNow();
            noise.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ---------------------------------------------------------------- helpers

    private SendStatus send(String from, String to, String text) {
        try {
            HttpResponse<String> r = http.send(messageRequest(from, to, text), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 ? SendStatus.OK : SendStatus.NON_200;
        } catch (HttpTimeoutException ex) {
            return SendStatus.TIMEOUT;
        } catch (Exception ex) {
            return SendStatus.ERROR;
        }
    }

    private CompletableFuture<SendStatus> sendAsync(String from, String to, String text) {
        return http.sendAsync(messageRequest(from, to, text), HttpResponse.BodyHandlers.ofString())
                .handle((r, ex) -> {
                    if (ex != null) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        return cause instanceof HttpTimeoutException ? SendStatus.TIMEOUT : SendStatus.ERROR;
                    }
                    return r.statusCode() == 200 ? SendStatus.OK : SendStatus.NON_200;
                });
    }

    private HttpRequest messageRequest(String from, String to, String text) {
        JsonObject body = new JsonObject();
        body.addProperty("fromClientId", from);
        body.addProperty("toClientId", to);
        body.addProperty("text", text);
        body.addProperty("avatar", "avatar" + from.replace("-", ""));
        return HttpRequest.newBuilder(URI.create(base + "/api/peer/message"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
    }

    private void sendPoke(String from, String to) {
        try {
            http.send(HttpRequest.newBuilder(URI.create(base + "/api/peer/poke"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + TOKEN)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"fromClientId\":\"" + from + "\",\"toClientId\":\"" + to + "\"}"))
                    .build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // 背景噪音，失敗不影響判定
        }
    }

    /** 回傳這次心跳取走的 actions；非 200 或例外時丟出。 */
    private List<String> heartbeat(String clientId, String tasksJson) throws Exception {
        String body = "{\"clientId\":\"" + clientId + "\",\"status\":\"ONLINE\",\"avatar\":\"av" + clientId
                + "\",\"tasks\":" + (tasksJson == null ? "[]" : tasksJson) + "}";
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/api/heartbeat"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IllegalStateException("heartbeat " + r.statusCode());
        }
        JsonObject json = JsonParser.parseString(r.body()).getAsJsonObject();
        List<String> actions = new ArrayList<>();
        JsonArray array = json.getAsJsonArray("actions");
        if (array != null) {
            for (JsonElement el : array) {
                actions.add(el.getAsString());
            }
        }
        return actions;
    }

    private void safeHeartbeat(String clientId, String tasksJson) {
        try {
            heartbeat(clientId, tasksJson);
        } catch (Exception ex) {
            heartbeatFailures.incrementAndGet();
        }
    }

    private String login() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("password=secret"))
                .build(), HttpResponse.BodyHandlers.ofString());
        for (String header : r.headers().allValues("Set-Cookie")) {
            if (header.startsWith("auth=")) {
                return header.split(";", 2)[0];
            }
        }
        throw new IllegalStateException("login failed: " + r.statusCode());
    }

    private void getStatus(String cookie) {
        try {
            http.send(HttpRequest.newBuilder(URI.create(base + "/api/status"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Cookie", cookie)
                    .GET().build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // 背景噪音
        }
    }

    /** 收件者：背景高頻心跳，依序記下取到的 MSG（發送者、內容）。 */
    private final class Inbox {
        private final String clientId;
        private final java.util.function.IntFunction<String> tasks;
        private final List<String[]> received = Collections.synchronizedList(new ArrayList<>());
        private final AtomicBoolean stop = new AtomicBoolean(false);
        private final AtomicInteger failures = new AtomicInteger();
        private Thread thread;

        Inbox(String clientId, java.util.function.IntFunction<String> tasks) {
            this.clientId = clientId;
            this.tasks = tasks;
        }

        Inbox start() {
            thread = new Thread(() -> {
                int i = 0;
                while (!stop.get()) {
                    poll(i++);
                }
            }, "inbox-" + clientId);
            thread.start();
            return this;
        }

        private void poll(int i) {
            try {
                for (String action : heartbeat(clientId, tasks == null ? null : tasks.apply(i))) {
                    if (action.startsWith("MSG|")) {
                        String[] parts = action.split("\\|", 5);
                        String text = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
                        received.add(new String[]{parts[1], text});
                    }
                }
            } catch (Exception ex) {
                failures.incrementAndGet();
            }
        }

        /** 停止背景收取，再補收幾次把佇列清乾淨。 */
        List<String[]> finish() throws InterruptedException {
            stop.set(true);
            thread.join(10_000);
            for (int i = 0; i < 3; i++) {
                poll(-1);
            }
            heartbeatFailures.addAndGet(failures.get());
            return new ArrayList<>(received);
        }
    }

    /** 發送端帳本：每則訊息的 HTTP 結果，以及每位發送者成功入列的先後順序。 */
    private final class Ledger {
        private final Map<String, SendStatus> statusByText = Collections.synchronizedMap(new HashMap<>());
        private final Map<String, List<String>> okOrder = Collections.synchronizedMap(new LinkedHashMap<>());

        void record(String from, String text, SendStatus status) {
            statusByText.put(text, status);
            if (status == SendStatus.OK) {
                synchronized (okOrder) {
                    okOrder.computeIfAbsent(from, k -> new ArrayList<>()).add(text);
                }
            }
        }

        void verify(String label, List<String[]> received, boolean checkOrder) {
            Map<SendStatus, Integer> counts = new EnumMap<>(SendStatus.class);
            for (SendStatus s : SendStatus.values()) {
                counts.put(s, 0);
            }
            synchronized (statusByText) {
                for (SendStatus s : statusByText.values()) {
                    counts.merge(s, 1, Integer::sum);
                }
            }
            Set<String> seen = new HashSet<>();
            int duplicates = 0;
            int phantom = 0;
            Map<String, List<String>> receivedOrder = new LinkedHashMap<>();
            for (String[] r : received) {
                String text = r[1];
                SendStatus status = statusByText.get(text);
                if (status == null) {
                    continue;
                }
                if (!seen.add(text)) {
                    duplicates++;
                    continue;
                }
                if (status != SendStatus.OK) {
                    phantom++;
                }
                receivedOrder.computeIfAbsent(r[0], k -> new ArrayList<>()).add(text);
            }
            int lost = 0;
            int outOfOrder = 0;
            for (Map.Entry<String, List<String>> e : okOrder.entrySet()) {
                List<String> got = new ArrayList<>(receivedOrder.getOrDefault(e.getKey(), List.of()));
                got.retainAll(e.getValue());
                for (String text : e.getValue()) {
                    if (!seen.contains(text)) {
                        lost++;
                    }
                }
                if (checkOrder) {
                    List<String> expected = new ArrayList<>(e.getValue());
                    expected.retainAll(got);
                    if (!expected.equals(got)) {
                        outOfOrder++;
                    }
                }
            }
            String summary = String.format("[%s] 送出 %d：HTTP 200=%d 非200=%d 逾時=%d 例外=%d｜"
                            + "收件確認=%d 遺失=%d 重複=%d 非200卻送達=%d 亂序發送者=%d 心跳失敗=%d",
                    label, statusByText.size(), counts.get(SendStatus.OK), counts.get(SendStatus.NON_200),
                    counts.get(SendStatus.TIMEOUT), counts.get(SendStatus.ERROR), seen.size(), lost, duplicates,
                    phantom, outOfOrder, heartbeatFailures.get());
            System.out.println(summary);
            List<String> problems = new ArrayList<>();
            if (lost > 0) problems.add("遺失 " + lost);
            if (duplicates > 0) problems.add("重複 " + duplicates);
            if (outOfOrder > 0) problems.add("亂序 " + outOfOrder);
            int notOk = counts.get(SendStatus.NON_200) + counts.get(SendStatus.TIMEOUT) + counts.get(SendStatus.ERROR);
            if (notOk > 0) problems.add("非 200 回應 " + notOk);
            if (phantom > 0) problems.add("非 200 卻送達 " + phantom);
            if (heartbeatFailures.get() > 0) problems.add("心跳失敗 " + heartbeatFailures.get());
            if (!problems.isEmpty()) {
                fail(summary + " → " + String.join("、", problems));
            }
            assertTrue(counts.get(SendStatus.OK) > 0);
            assertEquals(counts.get(SendStatus.OK).intValue(), seen.size() - phantom);
        }
    }
}
