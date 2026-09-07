package com.example.service;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeartbeatServiceCheckinReportTest {

    private HeartbeatService service;
    private HttpServer httpServer;

    @After
    public void tearDown() {
        if (service != null) {
            service.stopHeartbeat();
        }
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    public void captureKeepsResultAfterTaskIsRescheduled() {
        CheckInTask task = sampleTask();
        task.setStatus(TaskStatus.SUCCESS);
        task.setResultMessage("[成功] 打卡成功");

        service = new HeartbeatService();
        service.setTasksProvider(() -> Collections.singletonList(task));
        service.captureTerminalCheckinReports();

        task.setStatus(TaskStatus.SCHEDULED);
        task.setResultMessage("");

        List<Map<String, Object>> pending = service.pendingCheckinReportsSnapshot();
        assertEquals(1, pending.size());
        assertEquals("SUCCESS", pending.get(0).get("status"));
        assertEquals("[成功] 打卡成功", pending.get(0).get("message"));
        assertEquals(task.getId(), pending.get(0).get("id"));
    }

    @Test
    public void captureWhileCloudOfflineStillRemembersResult() {
        CheckInTask task = sampleTask();
        task.setStatus(TaskStatus.FAILED);
        task.setResultMessage("[失敗] timeout");

        service = new HeartbeatService();
        service.setTasksProvider(() -> Collections.singletonList(task));
        service.sendHeartbeat(msg -> {}, ok -> {});

        assertEquals(1, service.pendingCheckinReportsSnapshot().size());
        assertEquals("FAILED", service.pendingCheckinReportsSnapshot().get(0).get("status"));
    }

    @Test
    public void ackClearsOnlyTheReportsThatWereSent() {
        CheckInTask task = sampleTask();
        task.setStatus(TaskStatus.SUCCESS);
        task.setResultMessage("ok-1");

        service = new HeartbeatService();
        service.setTasksProvider(() -> Collections.singletonList(task));
        service.captureTerminalCheckinReports();
        List<Map<String, Object>> first = service.pendingCheckinReportsSnapshot();
        assertEquals(1, first.size());

        task.setResultMessage("ok-2");
        service.captureTerminalCheckinReports();
        assertEquals(2, service.pendingCheckinReportsSnapshot().size());

        service.ackCheckinReports(first);
        List<Map<String, Object>> remaining = service.pendingCheckinReportsSnapshot();
        assertEquals(1, remaining.size());
        assertEquals("ok-2", remaining.get(0).get("message"));
    }

    @Test
    public void timeoutThenRetryStillSendsCheckinReportsAfterReschedule() throws Exception {
        CheckInTask task = sampleTask();
        task.setStatus(TaskStatus.SUCCESS);
        task.setResultMessage("[成功] 打卡成功");

        AtomicInteger hits = new AtomicInteger();
        List<String> bodies = new CopyOnWriteArrayList<>();
        CountDownLatch secondHit = new CountDownLatch(1);

        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.createContext("/api/heartbeat", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            int n = hits.incrementAndGet();
            if (n == 1) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            byte[] ok = "{\"success\":true,\"action\":\"NONE\",\"actions\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
            secondHit.countDown();
        });
        httpServer.start();

        service = new HeartbeatService();
        service.setTasksProvider(() -> Collections.singletonList(task));
        service.setCheckinRetryDelayMs(80);
        service.startHeartbeat("http://127.0.0.1:" + httpServer.getAddress().getPort(), msg -> {}, ok -> {});

        task.setStatus(TaskStatus.SCHEDULED);
        task.setResultMessage("");

        assertTrue("second heartbeat should retry after 500", secondHit.await(5, TimeUnit.SECONDS));
        assertTrue(bodies.size() >= 2);
        JsonObject retryBody = JsonParser.parseString(bodies.get(1)).getAsJsonObject();
        assertTrue(retryBody.has("checkinReports"));
        assertEquals("SUCCESS", retryBody.getAsJsonArray("checkinReports").get(0).getAsJsonObject().get("status").getAsString());
        assertEquals("[成功] 打卡成功", retryBody.getAsJsonArray("checkinReports").get(0).getAsJsonObject().get("message").getAsString());
        assertTrue("pending reports should clear after HTTP 200", waitUntil(
                () -> service.pendingCheckinReportsSnapshot().isEmpty(), 2000));
    }

    @Test
    public void inFlightHeartbeatStillCarriesCapturedResultAfterReschedule() throws Exception {
        CheckInTask task = sampleTask();
        task.setStatus(TaskStatus.PENDING);

        CountDownLatch firstReceived = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondReceived = new CountDownLatch(1);
        List<String> bodies = new CopyOnWriteArrayList<>();

        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.createContext("/api/heartbeat", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            int n = bodies.size();
            if (n == 1) {
                firstReceived.countDown();
                try {
                    releaseFirst.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                secondReceived.countDown();
            }
            byte[] ok = "{\"success\":true,\"action\":\"NONE\",\"actions\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        httpServer.start();

        service = new HeartbeatService();
        service.setTasksProvider(() -> Collections.singletonList(task));
        service.setRequestTimeout(Duration.ofSeconds(8));
        service.startHeartbeat("http://127.0.0.1:" + httpServer.getAddress().getPort(), msg -> {}, ok -> {});

        assertTrue(firstReceived.await(3, TimeUnit.SECONDS));

        task.setStatus(TaskStatus.SUCCESS);
        task.setResultMessage("[成功] 打卡成功");
        service.sendHeartbeat(msg -> {}, ok -> {});

        task.setStatus(TaskStatus.SCHEDULED);
        task.setResultMessage("");

        releaseFirst.countDown();
        assertTrue("queued heartbeat should fire after in-flight completes", secondReceived.await(5, TimeUnit.SECONDS));

        JsonObject second = JsonParser.parseString(bodies.get(1)).getAsJsonObject();
        assertTrue(second.has("checkinReports"));
        JsonObject report = second.getAsJsonArray("checkinReports").get(0).getAsJsonObject();
        assertEquals("SUCCESS", report.get("status").getAsString());
        assertEquals("[成功] 打卡成功", report.get("message").getAsString());
        assertFalse("current task snapshot already rescheduled",
                "SUCCESS".equals(second.getAsJsonArray("tasks").get(0).getAsJsonObject().get("status").getAsString()));
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    private static CheckInTask sampleTask() {
        CheckInTask task = new CheckInTask(
                "上班打卡",
                "https://example.com/checkin",
                "#btn",
                LocalDateTime.of(2026, 9, 7, 9, 0),
                false,
                "msedge");
        task.setId("work-in");
        return task;
    }
}
