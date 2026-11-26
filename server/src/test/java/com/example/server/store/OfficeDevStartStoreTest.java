package com.example.server.store;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OfficeDevStartStoreTest {

    @Test
    public void recordsEarliestDevStartPerIssue() throws Exception {
        Path dir = Files.createTempDirectory("office-dev-start-");
        Path file = dir.resolve("issue-dev-starts.json");
        OfficeDevStartStore store = new OfficeDevStartStore(file);
        store.noteRun(100, "dev", 5_000L, null);
        store.noteRun(100, "dev", 3_000L, null);
        store.noteRun(101, "review", 1_000L, null);
        assertEquals(Long.valueOf(3_000L), store.earliestDevStart(100));
        assertNull(store.earliestDevStart(101));

        OfficeDevStartStore reloaded = new OfficeDevStartStore(file);
        assertEquals(Long.valueOf(3_000L), reloaded.earliestDevStart(100));
        Files.deleteIfExists(file);
        Files.deleteIfExists(dir);
    }
}