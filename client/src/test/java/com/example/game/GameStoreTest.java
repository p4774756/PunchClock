package com.example.game;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameStoreTest {

    @Test
    public void missingFileStartsAtTenThousand() throws Exception {
        Path file = Files.createTempDirectory("punchclock-games").resolve("games.json");
        GameWallet wallet = new GameStore(file).load();
        assertEquals(10_000, wallet.balance());
        assertFalse(Files.exists(file));
    }

    @Test
    public void balanceSurvivesReload() throws Exception {
        Path file = Files.createTempDirectory("punchclock-games").resolve("games.json");
        GameStore store = new GameStore(file);
        GameWallet wallet = store.load();
        wallet.begin("老虎機", 100);
        wallet.finish(300, "紅七");
        store.save(wallet);

        GameWallet loaded = new GameStore(file).load();
        assertEquals(10_200, loaded.balance());
        assertEquals("老虎機", loaded.records().get(0).game);
        assertEquals(200, loaded.records().get(0).delta);
        assertFalse(loaded.roundOpen());
    }

    @Test
    public void unfinishedRoundIsRefundedOnLoad() throws Exception {
        Path file = Files.createTempDirectory("punchclock-games").resolve("games.json");
        GameStore store = new GameStore(file);
        GameWallet wallet = store.load();
        wallet.begin("輪盤", 250);
        store.save(wallet);

        GameWallet loaded = new GameStore(file).load();
        assertEquals(10_000, loaded.balance());
        assertFalse(loaded.roundOpen());
        assertTrue(loaded.records().get(0).detail.contains("退回"));
    }

    @Test
    public void corruptFileDoesNotThrow() throws Exception {
        Path file = Files.createTempDirectory("punchclock-games").resolve("games.json");
        Files.writeString(file, "{not json");
        GameWallet wallet = new GameStore(file).load();
        assertEquals(10_000, wallet.balance());
    }
}
