package com.example.service;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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
