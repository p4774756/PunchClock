package com.example.service;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransferIoTest {

    @Test
    public void copy_reportsProgressAndWritesAllBytes() throws Exception {
        byte[] payload = new byte[300_000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i & 0xff);
        }
        AtomicLong last = new AtomicLong(-1);
        AtomicLong totalSeen = new AtomicLong(-1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransferIo.copy(new ByteArrayInputStream(payload), out, payload.length, (done, total) -> {
            last.set(done);
            totalSeen.set(total);
        });
        assertArrayEquals(payload, out.toByteArray());
        assertEquals(payload.length, last.get());
        assertEquals(payload.length, totalSeen.get());
    }

    @Test
    public void ofFile_contentLengthMatchesSize() throws Exception {
        Path file = Files.createTempFile("transfer-io-", ".bin");
        try {
            Files.write(file, new byte[12_345]);
            assertEquals(12_345L, TransferIo.ofFile(file, null).contentLength());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test(expected = TransferIo.CancelledException.class)
    public void copy_stopsWhenCancelled() throws Exception {
        TransferCancel cancel = new TransferCancel();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransferIo.copy(new ByteArrayInputStream(new byte[300_000]), out, 300_000, (done, total) -> {
            if (done > 0) {
                cancel.cancel();
            }
        }, cancel);
    }

    @Test
    public void ofFile_signalsErrorWhenCancelled() throws Exception {
        Path file = Files.createTempFile("transfer-io-cancel-", ".bin");
        try {
            Files.write(file, new byte[500_000]);
            TransferCancel cancel = new TransferCancel();
            AtomicReference<Throwable> error = new AtomicReference<>();
            AtomicLong chunks = new AtomicLong();
            AtomicReference<Flow.Subscription> sub = new AtomicReference<>();
            TransferIo.ofFile(file, null, cancel).subscribe(new Flow.Subscriber<ByteBuffer>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    sub.set(subscription);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    chunks.incrementAndGet();
                }

                @Override
                public void onError(Throwable throwable) {
                    error.set(throwable);
                }

                @Override
                public void onComplete() {
                }
            });
            sub.get().request(1);
            assertEquals(1, chunks.get());
            cancel.cancel();
            assertTrue(error.get() instanceof TransferIo.CancelledException);
            sub.get().request(10);
            assertEquals(1, chunks.get());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void cancel_runsListenerOnceEvenWhenRegisteredLate() {
        TransferCancel cancel = new TransferCancel();
        AtomicLong early = new AtomicLong();
        AtomicLong late = new AtomicLong();
        cancel.onCancel(early::incrementAndGet);
        assertTrue(cancel.cancel());
        assertFalse(cancel.cancel());
        cancel.onCancel(late::incrementAndGet);
        assertEquals(1, early.get());
        assertEquals(1, late.get());
        assertTrue(cancel.isCancelled());
    }

    @Test
    public void throttle_skipsTinyUpdatesUntilComplete() {
        AtomicLong reports = new AtomicLong();
        TransferIo.Progress throttled = TransferIo.throttle((done, total) -> reports.incrementAndGet());
        throttled.onProgress(100, 1_000_000);
        long afterFirst = reports.get();
        assertTrue(afterFirst >= 1);
        throttled.onProgress(200, 1_000_000);
        assertEquals(afterFirst, reports.get());
        throttled.onProgress(afterFirst == 1 ? 100_000 : 200_000, 1_000_000);
        assertTrue(reports.get() > afterFirst);
        throttled.onProgress(1_000_000, 1_000_000);
        assertTrue(reports.get() >= 3);
    }
}
