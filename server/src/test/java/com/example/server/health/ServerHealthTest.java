package com.example.server.health;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ServerHealthTest {

    @Test
    public void snapshot_includesCpuMemoryAndDisk() {
        Map<String, Object> snap = new ServerHealth().snapshot(null);
        assertNotNull(snap.get("cpuCount"));
        assertTrue(((Number) snap.get("cpuCount")).intValue() >= 1);
        assertTrue(((Number) snap.get("heapUsedBytes")).longValue() > 0);
        assertTrue(((Number) snap.get("heapMaxBytes")).longValue() > 0);
        assertTrue(((Number) snap.get("uptimeMs")).longValue() >= 0);
        assertNotNull(snap.get("diskPath"));
        assertTrue(String.valueOf(snap.get("diskPath")).length() > 0);
        assertTrue(((Number) snap.get("diskTotalBytes")).longValue() > 0);
        assertTrue(((Number) snap.get("diskUsableBytes")).longValue() >= 0);
        assertTrue(((Number) snap.get("diskUsableBytes")).longValue()
                <= ((Number) snap.get("diskTotalBytes")).longValue());
    }

    @Test
    public void percentOrNull_hidesUnavailableSamples() {
        org.junit.Assert.assertNull(ServerHealth.percentOrNull(-1));
        org.junit.Assert.assertEquals(12.5, ServerHealth.percentOrNull(0.125), 0.0001);
    }
}
