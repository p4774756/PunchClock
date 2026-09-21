package com.example.server.health;

import org.junit.After;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HealthHistoryStoreTest {

    private Path tempFile;

    @After
    public void cleanup() throws Exception {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void record_prunesOlderThanThreeDaysAndPersists() throws Exception {
        tempFile = Files.createTempFile("health-history-", ".json");
        AtomicLong clock = new AtomicLong(1_000_000L);
        HealthHistoryStore store = new HealthHistoryStore(tempFile, clock::get);

        Map<String, Object> oldSnap = sample(10.0, 100L, 1000L);
        store.record(oldSnap);

        clock.set(1_000_000L + HealthHistoryStore.RETENTION_MS + 60_000L);
        store.record(sample(20.0, 200L, 1000L));

        List<Map<String, Object>> history = store.history();
        assertEquals(1, history.size());
        assertEquals(20.0, ((Number) history.get(0).get("processCpuPercent")).doubleValue(), 0.001);

        HealthHistoryStore reloaded = new HealthHistoryStore(tempFile, clock::get);
        assertEquals(1, reloaded.history().size());
        assertTrue(reloaded.summary().containsKey("retentionMs"));
    }

    @Test
    public void toSample_computesOsUsed() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("processCpuPercent", 12.5);
        snap.put("osTotalMemoryBytes", 1000L);
        snap.put("osFreeMemoryBytes", 400L);
        snap.put("heapUsedBytes", 50L);
        snap.put("heapMaxBytes", 200L);
        snap.put("diskUsableBytes", 700L);
        snap.put("diskTotalBytes", 1000L);
        Map<String, Object> point = HealthHistoryStore.toSample(snap, 123L);
        assertEquals(123L, ((Number) point.get("atMs")).longValue());
        assertEquals(600L, ((Number) point.get("osUsedMemoryBytes")).longValue());
        assertEquals(700L, ((Number) point.get("diskUsableBytes")).longValue());
        assertEquals(1000L, ((Number) point.get("diskTotalBytes")).longValue());
    }

    private static Map<String, Object> sample(double cpu, long heapUsed, long heapMax) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("processCpuPercent", cpu);
        snap.put("systemCpuPercent", cpu);
        snap.put("heapUsedBytes", heapUsed);
        snap.put("heapMaxBytes", heapMax);
        snap.put("osTotalMemoryBytes", 8_000L);
        snap.put("osFreeMemoryBytes", 3_000L);
        snap.put("fileOfferBytes", 0L);
        snap.put("fileOfferCount", 0);
        return snap;
    }
}
