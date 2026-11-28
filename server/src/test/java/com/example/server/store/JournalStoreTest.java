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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JournalStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

  @Test
    public void persistsAndReloadsNewestFirst() throws Exception {
        Path dir = folder.newFolder().toPath();
        JournalStore store = new JournalStore(dir);
        assertTrue(store.createPost("第一篇", null, null, 100L).ok);
        assertTrue(store.createPost("第二篇", null, null, 200L).ok);
        JournalStore reloaded = new JournalStore(dir);
        assertEquals(2, reloaded.count());
        assertEquals("第二篇", reloaded.list(null, 10).get(0).body);
        assertTrue(Files.isRegularFile(dir.resolve("index.json")));
    }

    @Test
    public void blankNicknameBecomesAnonymous() throws Exception {
        JournalStore store = newStore();
        JournalStore.Result post = store.createPost("內容", null, null, 1L);
        assertTrue(post.ok);
        JournalStore.Result comment = store.addComment(post.post.id, "  ", "哈囉", "", "1.1.1.1", 5L);
        assertTrue(comment.ok);
        assertEquals(JournalStore.ANONYMOUS, comment.comment.nickname);
    }

    @Test
    public void rateLimitReturns429() throws Exception {
        JournalStore store = newStore();
        JournalStore.Result post = store.createPost("內容", null, null, 1L);
        assertTrue(store.addComment(post.post.id, "甲", "一", "", "9.9.9.9", 10L).ok);
        JournalStore.Result fast = store.addComment(post.post.id, "甲", "二", "", "9.9.9.9", 20L);
        assertFalse(fast.ok);
        assertEquals(429, fast.status);
    }

    @Test
    public void rejectsLinksAndSpam() throws Exception {
        JournalStore store = newStore();
        JournalStore.Result post = store.createPost("內容", null, null, 1L);
        JournalStore.Result link = store.addComment(post.post.id, "甲", "看 https://evil.test", "", "1.1.1.1", 1L);
        assertFalse(link.ok);
        assertEquals(JournalStore.LINK_MESSAGE, link.message);
        JournalStore.Result spam = store.addComment(post.post.id, "甲", "免費代購", "", "1.1.1.1", 2L);
        assertFalse(spam.ok);
        assertEquals(JournalStore.SPAM_MESSAGE, spam.message);
    }

    @Test
    public void honeypotFailsQuietly() throws Exception {
        JournalStore store = newStore();
        JournalStore.Result post = store.createPost("內容", null, null, 1L);
        JournalStore.Result bad = store.addComment(post.post.id, "甲", "哈", "filled", "1.1.1.1", 1L);
        assertFalse(bad.ok);
        assertEquals(JournalStore.HONEYPOT_MESSAGE, bad.message);
        assertFalse(bad.message.toLowerCase().contains("honeypot"));
    }

    @Test
    public void imageRoundTripAndPathTraversalBlocked() throws Exception {
        Path dir = folder.newFolder().toPath();
        JournalStore store = new JournalStore(dir);
        byte[] png = png(120, 80);
        JournalStore.Result post = store.createPost("附圖", null, png, 1L);
        assertTrue(post.ok);
        assertNotNull(post.post.imageExt);
        byte[] read = store.readImage(post.post.id, post.post.imageExt);
        assertNotNull(read);
        assertNull(store.readImage("../../../etc/passwd", "png"));
        assertNull(store.readImage(post.post.id, "../png"));
    }

    @Test
    public void deletePostRemovesFromDisk() throws Exception {
        Path dir = folder.newFolder().toPath();
        JournalStore store = new JournalStore(dir);
        JournalStore.Result post = store.createPost("刪我", null, png(40, 40), 1L);
        assertTrue(post.ok);
        assertTrue(store.deletePost(post.post.id));
        JournalStore again = new JournalStore(dir);
        assertEquals(0, again.count());
    }

    private JournalStore newStore() throws Exception {
        return new JournalStore(folder.newFolder().toPath());
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.GREEN);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}