package com.example.server.store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GuestbookStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void normalizesAndRejectsControlChars() {
        assertEquals("小明", GuestbookStore.normalizeNickname("小\u0000明"));
        try {
            GuestbookStore.normalizeNickname("   ");
            org.junit.Assert.fail("expected empty");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("暱稱"));
        }
        String xss = "<script>alert(1)</script>";
        assertEquals(xss, GuestbookStore.normalizeMessage(xss));
    }

    @Test
    public void addWithOptionalDoodleAndRateLimit() throws Exception {
        Path dir = folder.getRoot().toPath();
        GuestbookStore store = new GuestbookStore(dir, 20);
        GuestbookStore.AddRequest req = new GuestbookStore.AddRequest();
        req.nickname = "阿華";
        req.message = "你好";
        req.emoji = "heart";
        long t0 = 1_000_000L;
        GuestbookStore.Result ok = store.add(req, "1.1.1.1", t0);
        assertTrue(ok.ok);
        assertNotNull(ok.item);

        GuestbookStore.Result fast = store.add(req, "1.1.1.1", t0 + 5_000L);
        assertFalse(fast.ok);
        assertEquals(429, fast.status);

        GuestbookStore.Result after = store.add(req, "1.1.1.1", t0 + 70_000L);
        assertTrue(after.ok);

        byte[] bad = "not-a-png".getBytes(StandardCharsets.UTF_8);
        GuestbookStore.AddRequest doodle = new GuestbookStore.AddRequest();
        doodle.nickname = "塗";
        doodle.message = "圖";
        doodle.doodleRaw = bad;
        GuestbookStore.Result reject = store.add(doodle, "1.1.1.2", t0);
        assertFalse(reject.ok);
        assertEquals(400, reject.status);

        byte[] big = png(GuestbookStore.DOODLE_WIDTH, GuestbookStore.DOODLE_HEIGHT);
        doodle.doodleRaw = new byte[GuestbookStore.DOODLE_MAX_BYTES + 1024];
        System.arraycopy(big, 0, doodle.doodleRaw, 0, big.length);
        GuestbookStore.Result tooBig = store.add(doodle, "1.1.1.3", t0);
        assertFalse(tooBig.ok);

        doodle.doodleRaw = png(100, 100);
        GuestbookStore.Result wrongSize = store.add(doodle, "1.1.1.4", t0);
        assertFalse(wrongSize.ok);
    }

    @Test
    public void acceptsWebpAndBlocksHiddenDoodles() throws Exception {
        byte[] webp = loadWebpFixture();
        assertTrue(GuestbookStore.isWebP(webp));
        int[] dim = GuestbookStore.webpDimensions(webp);
        assertNotNull(dim);
        assertEquals(GuestbookStore.DOODLE_WIDTH, dim[0]);
        assertEquals(GuestbookStore.DOODLE_HEIGHT, dim[1]);

        GuestbookStore store = new GuestbookStore(folder.getRoot().toPath(), 20);
        GuestbookStore.AddRequest req = new GuestbookStore.AddRequest();
        req.nickname = "W";
        req.message = "P";
        req.doodleRaw = webp;
        GuestbookStore.Result ok = store.add(req, "3.3.3.3", 10_000_000L);
        assertTrue(ok.ok);
        assertEquals("webp", ok.item.doodleFormat);

        byte[] read = store.readDoodleBytes(ok.item.id, false);
        assertNotNull(read);

        store.hide(ok.item.id);
        assertNull(store.readDoodleBytes(ok.item.id, false));
        assertNotNull(store.readDoodleBytes(ok.item.id, true));
    }

    @Test
    public void hideAndDeleteRemoveFromPublicList() throws Exception {
        GuestbookStore store = new GuestbookStore(folder.getRoot().toPath(), 20);
        GuestbookStore.AddRequest req = new GuestbookStore.AddRequest();
        req.nickname = "小花";
        req.message = "測試";
        GuestbookStore.Result created = store.add(req, "2.2.2.2", 9_000_000L);
        String id = created.item.id;

        assertTrue(store.hide(id));
        GuestbookStore.Page publicPage = store.list(1, 5, false);
        assertTrue(publicPage.items.isEmpty());
        GuestbookStore.Page adminPage = store.list(1, 5, true);
        assertEquals(1, adminPage.items.size());
        assertTrue(adminPage.items.get(0).hidden);

        assertTrue(store.delete(id));
        assertNull(store.readDoodlePng(id));
        adminPage = store.list(1, 5, true);
        assertTrue(adminPage.items.isEmpty());
    }

    private static byte[] loadWebpFixture() throws Exception {
        try (InputStream in = GuestbookStoreTest.class.getResourceAsStream("/guestbook/doodle-240x120.webp")) {
            assertNotNull(in);
            return in.readAllBytes();
        }
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