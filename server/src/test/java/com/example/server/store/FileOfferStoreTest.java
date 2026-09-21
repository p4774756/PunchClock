package com.example.server.store;

import com.example.PeerFileRules;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FileOfferStoreTest {

    private AtomicLong now;
    private Path storageDir;
    private FileOfferStore store;

    @Before
    public void setUp() throws Exception {
        now = new AtomicLong(1_700_000_000_000L);
        storageDir = Files.createTempDirectory("peer-offer-");
        store = new FileOfferStore(storageDir, now::get);
    }

    @After
    public void tearDown() throws Exception {
        if (store != null) {
            store.deleteAll();
        }
        if (storageDir != null && Files.isDirectory(storageDir)) {
            try (Stream<Path> walk = Files.walk(storageDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // cleanup best-effort
                    }
                });
            }
        }
    }

    @Test
    public void putAndGet_roundTripsBytesForRecipient() throws Exception {
        byte[] payload = "hello file".getBytes(StandardCharsets.UTF_8);
        FileOfferStore.PutResult put = store.put("a", "b", "notes.txt", payload);
        assertTrue(put.ok);
        assertNotNull(put.offer);
        assertEquals("notes.txt", put.offer.filename);
        assertEquals("text/plain", put.offer.mime);
        assertTrue(Files.isRegularFile(put.offer.path));

        FileOfferStore.GetResult get = store.getForRecipient(put.offer.fileId, "b");
        assertEquals(FileOfferStore.GetResult.Status.OK, get.status);
        assertArrayEquals(payload, get.offer.readAllBytes());
        assertEquals(1, get.offer.downloadCount());
    }

    @Test
    public void put_rejectsSelfEmptyAndOversizeButAllowsAnyExtension() {
        byte[] ok = "x".getBytes(StandardCharsets.UTF_8);
        assertFalse(store.put("a", "a", "notes.txt", ok).ok);
        assertTrue(store.put("a", "b", "notes.exe", ok).ok);
        assertTrue(store.put("a", "c", "noext", ok).ok);
        assertFalse(store.put("a", "b", "notes.txt", new byte[0]).ok);
        assertFalse(store.put("a", "b", "notes.txt",
                new ByteArrayInputStream(new byte[]{1}), PeerFileRules.MAX_BYTES + 1,
                PeerFileRules.KIND_FILE).ok);
        assertFalse(store.put("", "b", "notes.txt", ok).ok);
    }

    @Test
    public void put_acceptsFileLargerThanFormerFiveMegLimit() {
        assertTrue(FileOfferStore.MAX_TOTAL_BYTES >= PeerFileRules.MAX_BYTES);
        byte[] sixMb = new byte[6 * 1024 * 1024];
        FileOfferStore.PutResult put = store.put("a", "b", "notes.txt", sixMb);
        assertTrue(put.ok);
        assertEquals(sixMb.length, put.offer.size());
    }

    @Test
    public void get_allowsSenderAndForbidsThirdParty() {
        FileOfferStore.PutResult put = store.put("a", "b", "a.pdf", "p".getBytes(StandardCharsets.UTF_8));
        assertEquals(FileOfferStore.GetResult.Status.OK,
                store.getForDownload(put.offer.fileId, "a", false).status);
        assertEquals(0, put.offer.downloadCount());
        assertEquals(FileOfferStore.GetResult.Status.FORBIDDEN,
                store.getForDownload(put.offer.fileId, "c", false).status);
        assertEquals(FileOfferStore.GetResult.Status.NOT_FOUND,
                store.getForRecipient("missing", "b").status);
        assertEquals(FileOfferStore.GetResult.Status.OK,
                store.getForDownload(put.offer.fileId, "ignored", true).status);
    }

    @Test
    public void expiredOfferIsRemoved() {
        FileOfferStore.PutResult put = store.put("a", "b", "a.txt", "p".getBytes(StandardCharsets.UTF_8));
        Path path = put.offer.path;
        now.addAndGet(FileOfferStore.TTL_MS + 1);
        assertEquals(FileOfferStore.GetResult.Status.NOT_FOUND,
                store.getForRecipient(put.offer.fileId, "b").status);
        assertEquals(0, store.size());
        assertFalse(Files.exists(path));
    }

    @Test
    public void ttlIsSixHours() {
        assertEquals(PeerFileRules.OFFER_TTL_MS, FileOfferStore.TTL_MS);
        assertEquals(6L * 60L * 60L * 1000L, FileOfferStore.TTL_MS);
        assertEquals(FileOfferStore.TTL_MS, FileOfferStore.FILE_ACTION_TTL_MS);
    }

    @Test
    public void get_matchesMacNfdAndWindowsNfcClientIds() {
        String nfd = java.text.Normalizer.normalize("café-mac", java.text.Normalizer.Form.NFD);
        String nfc = java.text.Normalizer.normalize("café-mac", java.text.Normalizer.Form.NFC);
        assertFalse(nfd.equals(nfc));
        FileOfferStore.PutResult put = store.put("win", nfd, "a.txt", "p".getBytes(StandardCharsets.UTF_8));
        assertTrue(put.ok);
        assertEquals(nfc, put.offer.toClientId);
        assertEquals(FileOfferStore.GetResult.Status.OK,
                store.getForRecipient(put.offer.fileId, nfc).status);
        assertEquals(FileOfferStore.GetResult.Status.OK,
                store.getForRecipient(put.offer.fileId, nfd).status);
    }

    @Test
    public void sanitizeUsesBasenameOnly() {
        FileOfferStore.PutResult put = store.put("a", "b", "../../etc/passwd.txt",
                "p".getBytes(StandardCharsets.UTF_8));
        assertTrue(put.ok);
        assertEquals("passwd.txt", put.offer.filename);
    }

    @Test
    public void snapshotAndDelete_areVisibleUntilCleared() {
        FileOfferStore.PutResult put = store.put("a", "b", "notes.exe", "hi".getBytes(StandardCharsets.UTF_8),
                PeerFileRules.KIND_FILE);
        assertTrue(put.ok);
        Path path = put.offer.path;
        List<Map<String, Object>> all = store.publicSnapshot();
        assertEquals(1, all.size());
        assertEquals("waiting", all.get(0).get("status"));
        assertEquals("notes.exe", all.get(0).get("filename"));
        assertEquals(put.offer.fileId, store.snapshotForClient("b").get(0).get("fileId"));
        assertEquals(1, store.snapshotForClient("a").size());
        assertEquals(0, store.snapshotForClient("c").size());

        store.getForRecipient(put.offer.fileId, "b");
        assertEquals("downloaded", store.publicSnapshot().get(0).get("status"));

        FileOfferStore.DeleteResult third = store.delete(put.offer.fileId, "c", false);
        assertEquals(FileOfferStore.DeleteResult.Status.FORBIDDEN, third.status);
        assertEquals(1, store.size());

        FileOfferStore.DeleteResult cleared = store.delete(put.offer.fileId, "a", false);
        assertEquals(FileOfferStore.DeleteResult.Status.OK, cleared.status);
        assertEquals(0, store.size());
        assertFalse(Files.exists(path));
    }

    @Test
    public void folderKind_usesZipMime() {
        FileOfferStore.PutResult put = store.put("a", "b", "專案.zip", "zip".getBytes(StandardCharsets.UTF_8),
                "folder");
        assertTrue(put.ok);
        assertEquals(PeerFileRules.KIND_FOLDER, put.offer.kind);
        assertEquals("application/zip", put.offer.mime);
    }
}
