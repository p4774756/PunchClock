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
    public void chunkedUpload_appendsInOrderAndCompletesIntoOffer() throws Exception {
        byte[] payload = "hello-chunked-world".getBytes(StandardCharsets.UTF_8);
        FileOfferStore.UploadResult begun = store.beginUpload("a", "b", "notes.txt", "file", payload.length);
        assertTrue(begun.ok());
        String id = begun.uploadId;

        FileOfferStore.UploadResult first = store.appendChunk(id, "a", 0,
                new ByteArrayInputStream(payload, 0, 6));
        assertTrue(first.ok());
        assertEquals(6, first.received);

        FileOfferStore.UploadResult wrongOffset = store.appendChunk(id, "a", 0,
                new ByteArrayInputStream(payload, 0, 6));
        assertEquals(FileOfferStore.UploadResult.Status.CONFLICT, wrongOffset.status);
        assertEquals(6, wrongOffset.received);

        FileOfferStore.UploadResult early = store.completeUpload(id, "a");
        assertEquals(FileOfferStore.UploadResult.Status.CONFLICT, early.status);

        assertTrue(store.appendChunk(id, "a", 6,
                new ByteArrayInputStream(payload, 6, payload.length - 6)).ok());
        assertEquals(payload.length, store.uploadStatus(id, "a").received);

        FileOfferStore.UploadResult done = store.completeUpload(id, "a");
        assertTrue(done.ok());
        assertFalse(done.alreadyCompleted);
        assertNotNull(done.offer);
        assertEquals("text/plain", done.offer.mime);
        assertArrayEquals(payload, done.offer.readAllBytes());
        assertEquals(1, store.size());

        FileOfferStore.UploadResult again = store.completeUpload(id, "a");
        assertTrue(again.ok());
        assertTrue(again.alreadyCompleted);
        assertEquals(done.offer.fileId, again.offer.fileId);
        assertEquals(1, store.size());
        assertEquals(0, store.activeUploadCount());
    }

    @Test
    public void chunkedUpload_rejectsOtherRequesterAndOversizeChunk() {
        FileOfferStore.UploadResult begun = store.beginUpload("a", "b", "x.bin", "file", 4);
        assertTrue(begun.ok());
        assertEquals(FileOfferStore.UploadResult.Status.FORBIDDEN,
                store.appendChunk(begun.uploadId, "c", 0, new ByteArrayInputStream(new byte[2])).status);
        FileOfferStore.UploadResult tooMuch = store.appendChunk(begun.uploadId, "a", 0,
                new ByteArrayInputStream(new byte[5]));
        assertEquals(FileOfferStore.UploadResult.Status.FAILED, tooMuch.status);
        assertEquals(0, store.uploadStatus(begun.uploadId, "a").received);
        assertEquals(FileOfferStore.UploadResult.Status.NOT_FOUND,
                store.appendChunk("missing", "a", 0, new ByteArrayInputStream(new byte[1])).status);
        assertFalse(store.beginUpload("a", "a", "x.bin", "file", 4).ok());
        assertFalse(store.beginUpload("a", "b", "x.bin", "file", PeerFileRules.MAX_BYTES + 1).ok());
    }

    @Test
    public void chunkedUpload_keepsBytesThatLandedBeforeDisconnect() {
        FileOfferStore.UploadResult begun = store.beginUpload("a", "b", "x.bin", "file", 10);
        java.io.InputStream broken = new java.io.InputStream() {
            private int sent;

            @Override
            public int read() throws java.io.IOException {
                if (sent >= 4) {
                    throw new java.io.IOException("connection reset");
                }
                sent++;
                return 7;
            }
        };
        FileOfferStore.UploadResult result = store.appendChunk(begun.uploadId, "a", 0, broken);
        assertEquals(FileOfferStore.UploadResult.Status.FAILED, result.status);
        assertEquals(4, store.uploadStatus(begun.uploadId, "a").received);
        assertTrue(store.appendChunk(begun.uploadId, "a", 4, new ByteArrayInputStream(new byte[6])).ok());
        assertTrue(store.completeUpload(begun.uploadId, "a").ok());
    }

    @Test
    public void chunkedUpload_reservesCapacityAndExpiresWhenIdle() throws Exception {
        assertEquals(PeerFileRules.MAX_BYTES * 2, FileOfferStore.MAX_TOTAL_BYTES);
        FileOfferStore.UploadResult big = store.beginUpload("a", "b", "big.zip", "folder", PeerFileRules.MAX_BYTES);
        FileOfferStore.UploadResult big2 = store.beginUpload("a", "c", "big2.zip", "folder", PeerFileRules.MAX_BYTES);
        assertTrue(big.ok());
        assertTrue(big2.ok());
        assertFalse(store.beginUpload("a", "b", "more.bin", "file", 1).ok());
        assertFalse(store.put("a", "b", "small.txt", new byte[]{1}).ok);

        now.addAndGet(FileOfferStore.UPLOAD_IDLE_TTL_MS + 1);
        assertEquals(FileOfferStore.UploadResult.Status.NOT_FOUND,
                store.uploadStatus(big.uploadId, "a").status);
        assertEquals(0, store.activeUploadCount());
        try (Stream<Path> files = Files.list(storageDir)) {
            assertEquals(0, files.count());
        }
        assertTrue(store.beginUpload("a", "b", "more.bin", "file", 1).ok());
    }

    @Test
    public void chunkedUpload_abortDeletesPartFile() throws Exception {
        FileOfferStore.UploadResult begun = store.beginUpload("a", "b", "x.bin", "file", 10);
        store.appendChunk(begun.uploadId, "a", 0, new ByteArrayInputStream(new byte[3]));
        assertTrue(store.abortUpload(begun.uploadId, "a").ok());
        assertEquals(FileOfferStore.UploadResult.Status.NOT_FOUND,
                store.uploadStatus(begun.uploadId, "a").status);
        try (Stream<Path> files = Files.list(storageDir)) {
            assertEquals(0, files.count());
        }
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
