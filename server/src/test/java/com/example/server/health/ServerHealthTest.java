package com.example.server.health;

import com.example.server.store.FileOfferStore;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ServerHealthTest {

    @Test
    public void snapshot_includesCpuMemoryAndFileUsage() {
        FileOfferStore files = new FileOfferStore();
        files.put("a", "b", "notes.txt", "hi".getBytes());
        Map<String, Object> snap = new ServerHealth().snapshot(files);
        assertNotNull(snap.get("cpuCount"));
        assertTrue(((Number) snap.get("cpuCount")).intValue() >= 1);
        assertTrue(((Number) snap.get("heapUsedBytes")).longValue() > 0);
        assertTrue(((Number) snap.get("heapMaxBytes")).longValue() > 0);
        assertEqualsLong(1, snap.get("fileOfferCount"));
        assertTrue(((Number) snap.get("fileOfferBytes")).longValue() >= 2);
        assertTrue(((Number) snap.get("uptimeMs")).longValue() >= 0);
    }

    @Test
    public void percentOrNull_hidesUnavailableSamples() {
        org.junit.Assert.assertNull(ServerHealth.percentOrNull(-1));
        org.junit.Assert.assertEquals(12.5, ServerHealth.percentOrNull(0.125), 0.0001);
    }

    private static void assertEqualsLong(long expected, Object actual) {
        org.junit.Assert.assertEquals(expected, ((Number) actual).longValue());
    }
}
