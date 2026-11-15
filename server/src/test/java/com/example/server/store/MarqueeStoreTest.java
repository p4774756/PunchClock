package com.example.server.store;

import com.example.MarqueeNotice;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    @Test
    public void pathPrefersOverrideThenEnvThenDiskThenHome() {
        Path disk = Paths.get("/var/data/server-marquee.json");
        Path home = Paths.get("/home/dev/.punchclock/server-marquee.json");
        assertEquals(Paths.get("/custom/marquee.json"),
                MarqueeStore.resolvePath("/custom/marquee.json", "/from-env.json", true, disk, home));
        assertEquals(Paths.get("/from-env.json"),
                MarqueeStore.resolvePath("  ", " /from-env.json ", true, disk, home));
        assertEquals(Paths.get("/from-env.json"),
                MarqueeStore.resolvePath(null, "/from-env.json", true, disk, home));
        assertEquals(disk, MarqueeStore.resolvePath(null, " ", true, disk, home));
        assertEquals(disk, MarqueeStore.resolvePath(null, null, true, disk, home));
        assertEquals(home, MarqueeStore.resolvePath(null, null, false, disk, home));
        assertEquals(home, MarqueeStore.resolvePath(null, null, true, null, home));
    }
}
