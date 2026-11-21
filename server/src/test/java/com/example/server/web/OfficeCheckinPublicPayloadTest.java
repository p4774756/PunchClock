package com.example.server.web;

import com.example.server.store.ClientStore;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfficeCheckinPublicPayloadTest {

    private ClientStore store;

    @Before
    public void setUp() {
        store = new ClientStore();
        store.clients().clear();
    }

    @Test
    public void publicSummaryOmitsSensitiveFields() {
        Map<String, Object> client = store.getOrCreateClient("secret-worker");
        client.put("status", "ONLINE");
        client.put("lastSeen", Instant.now().toString());
        client.put("targetUrl", "https://secret.example/checkin");
        client.put("buttonId", "#btn");
        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", "morning");
        task.put("name", "上班打卡");
        task.put("status", "SUCCESS");
        task.put("actualTime", Instant.now().toString());
        task.put("lastResultMessage", "[成功] 路徑 /Users/foo/log");
        tasks.add(task);
        client.put("tasks", tasks);

        Map<String, Object> payload = OfficeCheckinPublicPayload.build(store);
        assertEquals(Boolean.TRUE, payload.get("success"));
        assertFalse(String.valueOf(payload).contains("secret-worker"));
        assertFalse(String.valueOf(payload).contains("targetUrl"));
        assertFalse(String.valueOf(payload).contains("/Users"));
        assertTrue(payload.get("detailRequiresLogin").equals(Boolean.TRUE));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("tasks");
        assertEquals(1, rows.size());
        assertEquals("上班打卡", rows.get(0).get("name"));
        assertEquals("成功", rows.get(0).get("status"));
    }

    @Test
    public void lastResultOverridesScheduledStatus() {
        Map<String, Object> client = store.getOrCreateClient("w");
        client.put("lastSeen", Instant.now().toString());
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("name", "下班打卡");
        task.put("status", "SCHEDULED");
        task.put("lastResultStatus", "FAILED");
        client.put("tasks", new ArrayList<>(List.of(task)));
        assertEquals("FAILED", OfficeCheckinPublicPayload.effectiveStatusCode(task));
    }
}