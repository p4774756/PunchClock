package com.example.server.store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GraffitiStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void rejectsNonPng() throws Exception {
        GraffitiStore store = newStore();
        GraffitiStore.Result result = store.add("not-a-png".getBytes(StandardCharsets.UTF_8), "小明", "1.1.1.1", 1L);
        assertFalse(result.ok);
        assertEquals(400, result.status);
        assertEquals(GraffitiStore.IMAGE_MESSAGE, result.message);
        assertEquals(0, store.count());
    }

    @Test
    public void rejectsWrongDimensions() throws Exception {
        GraffitiStore store = newStore();
        GraffitiStore.Result result = store.add(png(100, 100), "小明", "1.1.1.1", 1L);
        assertFalse(result.ok);
        assertEquals(400, result.status);
        assertEquals(GraffitiStore.IMAGE_MESSAGE, result.message);
        assertEquals(0, store.count());
    }

    @Test
    public void rejectsOversizeBeforeDecode() throws Exception {
        GraffitiStore store = newStore();
        byte[] big = new byte[GraffitiStore.MAX_BYTES + 1];
        GraffitiStore.Result result = store.add(big, "小明", "1.1.1.1", 1L);
        assertFalse(result.ok);
        assertEquals(413, result.status);
        assertEquals(GraffitiStore.SIZE_MESSAGE, result.message);
        assertEquals(0, store.count());
    }

    @Test
    public void stripsControlCharactersAndBlankBecomesAnonymous() throws Exception {
        GraffitiStore store = newStore();
        GraffitiStore.Result named = store.add(png(512, 256), "阿\u0007明", "1.1.1.1", 10L);
        assertTrue(named.ok);
        assertEquals("阿明", named.item.signature);

        GraffitiStore.Result blank = store.add(png(512, 256), "  \n\t ", "1.1.1.2", 11L);
        assertTrue(blank.ok);
        assertEquals(GraffitiStore.ANONYMOUS, blank.item.signature);
    }

    @Test
    public void rejectsSignatureLongerThanTwelveCodePoints() throws Exception {
        GraffitiStore store = newStore();
        GraffitiStore.Result result = store.add(png(512, 256), "一二三四五六七八九十甲乙丙", "1.1.1.1", 1L);
        assertFalse(result.ok);
        assertEquals(400, result.status);
        assertTrue(result.message.contains("12"));
        assertEquals(0, store.count());
    }

    @Test
    public void rateLimitReturns429AndDoesNotDropEarlierPieces() throws Exception {
        GraffitiStore store = newStore();
        byte[] image = png(512, 256);
        for (int i = 0; i < GraffitiStore.RATE_MAX; i++) {
            GraffitiStore.Result ok = store.add(image, "旅人", "9.9.9.9", 5_000L + i);
            assertTrue(ok.ok);
        }
        GraffitiStore.Result blocked = store.add(image, "旅人", "9.9.9.9", 5_000L + 10);
        assertFalse(blocked.ok);
        assertEquals(429, blocked.status);
        assertEquals(GraffitiStore.RATE_MESSAGE, blocked.message);
        assertEquals(GraffitiStore.RATE_MAX, store.count());

        GraffitiStore.Result otherIp = store.add(image, "旅人", "8.8.8.8", 5_010L);
        assertTrue(otherIp.ok);
    }

    @Test
    public void fullWallRejectsWithoutDeletingOldPieces() throws Exception {
        Path dir = folder.newFolder().toPath();
        GraffitiStore store = new GraffitiStore(dir, 2);
        assertTrue(store.add(png(512, 256), "甲", "1.1.1.1", 1L).ok);
        assertTrue(store.add(png(512, 256), "乙", "1.1.1.2", 2L).ok);
        GraffitiStore.Result full = store.add(png(512, 256), "丙", "1.1.1.3", 3L);
        assertFalse(full.ok);
        assertEquals(409, full.status);
        assertEquals(GraffitiStore.FULL_MESSAGE, full.message);
        assertEquals(2, store.count());
        assertEquals("乙", store.list(null, 10).get(0).signature);
        assertEquals("甲", store.list(null, 10).get(1).signature);
    }

    @Test
    public void reloadReadsThePieceBackAndDropsSmuggledBytes() throws Exception {
        Path dir = folder.newFolder().toPath();
        GraffitiStore store = new GraffitiStore(dir);
        byte[] raw = png(512, 256);
        byte[] marker = "NOTPNG-TRAILER".getBytes(StandardCharsets.US_ASCII);
        byte[] smuggled = new byte[raw.length + marker.length];
        System.arraycopy(raw, 0, smuggled, 0, raw.length);
        System.arraycopy(marker, 0, smuggled, raw.length, marker.length);

        GraffitiStore.Result added = store.add(smuggled, "  小明  ", "1.1.1.1", 42L);
        assertTrue(added.ok);
        byte[] stored = Files.readAllBytes(dir.resolve(added.item.id + ".png"));
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains("NOTPNG-TRAILER"));
        assertTrue(stored.length < smuggled.length);
        assertArrayEquals(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}, new byte[] {stored[0], stored[1], stored[2], stored[3]});

        GraffitiStore again = new GraffitiStore(dir);
        assertEquals(1, again.count());
        GraffitiStore.Item loaded = again.list(null, 10).get(0);
        assertEquals(added.item.id, loaded.id);
        assertEquals("小明", loaded.signature);
        assertEquals(42L, loaded.createdAt);
        assertNotNull(again.readPng(loaded.id));
    }

    @Test
    public void deleteRemovesIndexAndFile() throws Exception {
        Path dir = folder.newFolder().toPath();
        GraffitiStore store = new GraffitiStore(dir);
        GraffitiStore.Result added = store.add(png(512, 256), "小明", "1.1.1.1", 7L);
        assertTrue(added.ok);
        assertTrue(store.delete(added.item.id));
        assertEquals(0, store.count());
        assertFalse(Files.exists(dir.resolve(added.item.id + ".png")));
        assertNull(store.readPng(added.item.id));

        GraffitiStore again = new GraffitiStore(dir);
        assertEquals(0, again.count());
    }

    private GraffitiStore newStore() throws Exception {
        return new GraffitiStore(folder.newFolder().toPath());
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
