package com.example.server.store;

import com.example.PeerFileRules;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RelayStoreTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final RelayStore store = new RelayStore(now::get);

    @Test
    public void fullTransferReleasesChunksAndCompletes() {
        RelayStore.Result begun = store.begin("worker-a", "worker-b", "notes.txt", "file", 10);
        assertTrue(begun.ok());
        String id = begun.relayId;
        assertEquals(RelayStore.State.WAITING, begun.state);

        assertEquals(RelayStore.Result.Status.CONFLICT, push(id, 0, "abcd").status);
        assertEquals(RelayStore.ChunkResult.Status.ERROR, store.take(id, "worker-b", 0, 0).status);

        assertTrue(store.accept(id, "worker-b").ok());
        assertTrue(push(id, 0, "abcd").ok());
        assertTrue(push(id, 4, "efgh").ok());
        RelayStore.Result full = push(id, 8, "ij");
        assertEquals("window of 2 chunks must be enforced", RelayStore.Result.Status.CONFLICT, full.status);
        assertEquals(Boolean.FALSE, store.await(id, "worker-a", 0, 0).view("worker-a").get("canPush"));
        assertEquals(8L, store.bufferedBytes());

        RelayStore.ChunkResult first = store.take(id, "worker-b", 0, 0);
        assertEquals(RelayStore.ChunkResult.Status.DATA, first.status);
        assertArrayEquals(bytes("abcd"), first.data);

        RelayStore.ChunkResult second = store.take(id, "worker-b", 4, 0);
        assertArrayEquals(bytes("efgh"), second.data);
        assertEquals("taking offset 4 releases the first chunk", 4L, store.bufferedBytes());
        assertEquals(Boolean.TRUE, store.await(id, "worker-a", 0, 0).view("worker-a").get("canPush"));

        assertTrue(push(id, 8, "ij").ok());
        assertArrayEquals(bytes("ij"), store.take(id, "worker-b", 8, 0).data);
        assertTrue(store.complete(id, "worker-b").ok());

        RelayStore.Result done = store.await(id, "worker-a", 0, 0);
        assertEquals(RelayStore.State.COMPLETED, done.state);
        assertEquals(0L, store.bufferedBytes());
        assertEquals(0, store.activeCount());
    }

    @Test
    public void retakingSameOffsetReturnsSameDataAndMidChunkSlices() {
        String id = acceptedRelay(8);
        assertTrue(push(id, 0, "abcdefgh").ok());
        assertArrayEquals(bytes("abcdefgh"), store.take(id, "worker-b", 0, 0).data);
        assertArrayEquals("response lost: recipient asks again", bytes("abcdefgh"), store.take(id, "worker-b", 0, 0).data);
        assertArrayEquals(bytes("efgh"), store.take(id, "worker-b", 4, 0).data);
        assertEquals("released offsets cannot be re-read",
                RelayStore.ChunkResult.Status.ERROR, store.take(id, "worker-b", 0, 0).status);
    }

    @Test
    public void takeWithoutDataTimesOutAsNoData() {
        String id = acceptedRelay(4);
        long started = System.nanoTime();
        RelayStore.ChunkResult result = store.take(id, "worker-b", 0, 150);
        assertEquals(RelayStore.ChunkResult.Status.NO_DATA, result.status);
        assertTrue((System.nanoTime() - started) / 1_000_000L >= 100);
    }

    @Test
    public void longPollWakesWhenChunkArrives() throws Exception {
        String id = acceptedRelay(4);
        Thread pusher = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                // ignore
            }
            push(id, 0, "wxyz");
        });
        pusher.start();
        RelayStore.ChunkResult result = store.take(id, "worker-b", 0, 5_000);
        pusher.join();
        assertEquals(RelayStore.ChunkResult.Status.DATA, result.status);
        assertArrayEquals(bytes("wxyz"), result.data);
    }

    @Test
    public void recipientDeclineBeforeAcceptAndSenderSeesIt() {
        String id = store.begin("worker-a", "worker-b", "a.txt", "file", 3).relayId;
        assertTrue(store.cancel(id, "worker-b").ok());
        RelayStore.Result status = store.await(id, "worker-a", 0, 0);
        assertEquals(RelayStore.State.DECLINED, status.state);
        assertEquals("對方拒絕接收", status.message);
        assertEquals(RelayStore.Result.Status.GONE, store.accept(id, "worker-b").status);
    }

    @Test
    public void cancelDuringTransferFreesBufferedChunks() {
        String id = acceptedRelay(8);
        assertTrue(push(id, 0, "abcd").ok());
        assertTrue(store.cancel(id, "worker-a").ok());
        assertEquals(0L, store.bufferedBytes());
        assertEquals(RelayStore.State.CANCELLED, store.await(id, "worker-b", 0, 0).state);
        assertEquals(RelayStore.Result.Status.GONE, push(id, 4, "efgh").status);
    }

    @Test
    public void unacceptedRelayExpires() {
        String id = store.begin("worker-a", "worker-b", "a.txt", "file", 3).relayId;
        now.addAndGet(PeerFileRules.RELAY_ACCEPT_TIMEOUT_MS + 1);
        RelayStore.Result status = store.await(id, "worker-a", 0, 0);
        assertEquals(RelayStore.State.EXPIRED, status.state);
        assertEquals(RelayStore.Result.Status.GONE, store.accept(id, "worker-b").status);
    }

    @Test
    public void idleRecipientFailsTransfer() {
        String id = acceptedRelay(8);
        assertTrue(push(id, 0, "abcd").ok());
        now.addAndGet(PeerFileRules.RELAY_IDLE_TIMEOUT_MS / 2);
        store.await(id, "worker-a", 0, 0);
        now.addAndGet(PeerFileRules.RELAY_IDLE_TIMEOUT_MS / 2 + 1);
        RelayStore.Result status = store.await(id, "worker-a", 0, 0);
        assertEquals(RelayStore.State.FAILED, status.state);
        assertTrue(status.message.contains("接收端"));
        assertEquals(0L, store.bufferedBytes());
    }

    @Test
    public void finishedRelaysArePurgedAfterRetention() {
        String id = store.begin("worker-a", "worker-b", "a.txt", "file", 3).relayId;
        store.cancel(id, "worker-a");
        assertEquals(1, store.publicSnapshot().size());
        now.addAndGet(RelayStore.FINISHED_RETENTION_MS + 1);
        assertEquals(0, store.publicSnapshot().size());
        assertEquals(RelayStore.Result.Status.NOT_FOUND, store.await(id, "worker-a", 0, 0).status);
    }

    @Test
    public void rolesAndValidationAreEnforced() {
        assertFalse(store.begin("worker-a", "worker-a", "a.txt", "file", 3).ok());
        assertFalse(store.begin("worker-a", "worker-b", "", "file", 3).ok());
        assertFalse(store.begin("worker-a", "worker-b", "a.txt", "file", 0).ok());
        assertFalse(store.begin("worker-a", "worker-b", "a.txt", "file", PeerFileRules.MAX_BYTES + 1).ok());

        String id = store.begin("worker-a", "worker-b", "a.txt", "file", 3).relayId;
        assertEquals(RelayStore.Result.Status.FORBIDDEN, store.accept(id, "worker-a").status);
        assertEquals(RelayStore.Result.Status.FORBIDDEN, store.await(id, "worker-c", 0, 0).status);
        store.accept(id, "worker-b");
        assertEquals(RelayStore.Result.Status.FORBIDDEN,
                store.push(id, "worker-b", 0, new ByteArrayInputStream(bytes("abc"))).status);
        assertEquals("more than declared size is rejected",
                RelayStore.Result.Status.FAILED, push(id, 0, "abcd").status);
        assertTrue(push(id, 0, "abc").ok());
    }

    @Test
    public void activeRelaysAreCapped() {
        for (int i = 0; i < RelayStore.MAX_ACTIVE; i++) {
            assertTrue(store.begin("worker-a", "worker-b", "a" + i + ".txt", "file", 3).ok());
        }
        assertEquals(RelayStore.Result.Status.BUSY,
                store.begin("worker-a", "worker-b", "overflow.txt", "file", 3).status);
    }

    @Test
    public void unicodeClientIdsMatchAcrossNormalization() {
        String nfd = java.text.Normalizer.normalize("café", java.text.Normalizer.Form.NFD);
        String nfc = java.text.Normalizer.normalize("café", java.text.Normalizer.Form.NFC);
        String id = store.begin("worker-a", nfd, "a.txt", "file", 3).relayId;
        assertTrue(store.accept(id, nfc).ok());
    }

    private String acceptedRelay(long size) {
        String id = store.begin("worker-a", "worker-b", "a.bin", "file", size).relayId;
        assertTrue(store.accept(id, "worker-b").ok());
        return id;
    }

    private RelayStore.Result push(String id, long offset, String text) {
        return store.push(id, "worker-a", offset, new ByteArrayInputStream(bytes(text)));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
