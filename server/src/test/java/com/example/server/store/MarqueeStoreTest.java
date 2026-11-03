package com.example.server.store;

import com.example.MarqueeNotice;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarqueeStoreTest {

    @Test
    public void updatePersistsAcrossANewStore() throws Exception {
        Path file = Files.createTempFile("marquee", ".json");
        Files.deleteIfExists(file);
        try {
            MarqueeNotice saved = new MarqueeStore(file).update(true, "  今日提早下班  ");
            assertTrue(saved.enabled);
            assertEquals("今日提早下班", saved.text);

            MarqueeNotice loaded = new MarqueeStore(file).current();
            assertTrue(loaded.enabled);
            assertEquals("今日提早下班", loaded.text);

            MarqueeNotice off = new MarqueeStore(file).update(false, "今日提早下班");
            assertFalse(off.enabled);
            assertEquals("今日提早下班", new MarqueeStore(file).current().text);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
