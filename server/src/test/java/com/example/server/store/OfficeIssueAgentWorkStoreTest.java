package com.example.server.store;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OfficeIssueAgentWorkStoreTest {

    @Test
    public void sumsMultipleClosedSegments() throws Exception {
        Path dir = Files.createTempDirectory("office-agent-work-");
        Path file = dir.resolve("issue-agent-work.json");
        OfficeIssueAgentWorkStore store = new OfficeIssueAgentWorkStore(file);
        store.addClosedSegment(701, 1_000L, 1_000L + 5 * 60_000L);
        store.addClosedSegment(701, 10_000L, 10_000L + 8 * 60_000L);
        assertEquals(Long.valueOf(13L), store.agentWorkMinutes(701));
        assertNull(store.agentWorkMinutes(702));

        OfficeIssueAgentWorkStore reloaded = new OfficeIssueAgentWorkStore(file);
        assertEquals(Long.valueOf(13L), reloaded.agentWorkMinutes(701));
        Files.deleteIfExists(file);
        Files.deleteIfExists(dir);
    }

    @Test
    public void ignoresSubMinuteTotals() throws Exception {
        OfficeIssueAgentWorkStore store = new OfficeIssueAgentWorkStore(null);
        store.addClosedSegment(100, 1_000L, 1_000L + 30_000L);
        assertNull(store.agentWorkMinutes(100));
    }
}