package com.example.server.store;

import com.example.PeerFileRules;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FileOfferStoreTest {

    private AtomicLong now;
    private FileOfferStore store;

    @Before
    public void setUp() {
        now = new AtomicLong(1_700_000_000_000L);
        store = new FileOfferStore(now::get);
    }

    @Test
    public void putAndGet_roundTripsBytesForRecipient() {
        byte[] payload = "hello file".getBytes(StandardCharsets.UTF_8);
        FileOfferStore.PutResult put = store.put("a", "b", "notes.txt", payload);
        assertTrue(put.ok);
        assertNotNull(put.offer);
        assertEquals("notes.txt", put.offer.filename);
        assertEquals("text/plain", put.offer.mime);

        FileOfferStore.GetResult get = store.getForRecipient(put.offer.fileId, "b");
        assertEquals(FileOfferStore.GetResult.Status.OK, get.status);
        assertArrayEquals(payload, get.offer.bytes);
    }

    @Test
    public void put_rejectsSelfEmptyUnsupportedAndOversize() {
        byte[] ok = "x".getBytes(StandardCharsets.UTF_8);
        assertFalse(store.put("a", "a", "notes.txt", ok).ok);
        assertFalse(store.put("a", "b", "notes.exe", ok).ok);
        assertFalse(store.put("a", "b", "notes.txt", new byte[0]).ok);
        assertFalse(store.put("a", "b", "notes.txt", new byte[(int) PeerFileRules.MAX_BYTES + 1]).ok);
        assertFalse(store.put("", "b", "notes.txt", ok).ok);
    }

    @Test
    public void get_forbidsNonRecipientAndMissingId() {
        FileOfferStore.PutResult put = store.put("a", "b", "a.pdf", "p".getBytes(StandardCharsets.UTF_8));
        assertEquals(FileOfferStore.GetResult.Status.FORBIDDEN,
                store.getForRecipient(put.offer.fileId, "a").status);
        assertEquals(FileOfferStore.GetResult.Status.NOT_FOUND,
                store.getForRecipient("missing", "b").status);
    }

    @Test
    public void expiredOfferIsRemoved() {
        FileOfferStore.PutResult put = store.put("a", "b", "a.txt", "p".getBytes(StandardCharsets.UTF_8));
        now.addAndGet(FileOfferStore.TTL_MS + 1);
        assertEquals(FileOfferStore.GetResult.Status.NOT_FOUND,
                store.getForRecipient(put.offer.fileId, "b").status);
        assertEquals(0, store.size());
    }

    @Test
    public void sanitizeUsesBasenameOnly() {
        FileOfferStore.PutResult put = store.put("a", "b", "../../etc/passwd.txt",
                "p".getBytes(StandardCharsets.UTF_8));
        assertTrue(put.ok);
        assertEquals("passwd.txt", put.offer.filename);
    }
}
