package com.example;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientUpdatePolicyTest {

    private static final String A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void offersOnlyWhenBothDigestsDiffer() {
        assertTrue(ClientUpdatePolicy.shouldOffer("1.8.0", A, B));
        assertFalse(ClientUpdatePolicy.shouldOffer("1.8.0", A, A));
        assertFalse(ClientUpdatePolicy.shouldOffer("1.8.0", A, ""));
        assertFalse(ClientUpdatePolicy.shouldOffer("1.8.0", "", B));
        assertFalse(ClientUpdatePolicy.shouldOffer(BuildInfo.DEV_VERSION, A, B));
        assertFalse(ClientUpdatePolicy.shouldOffer("", A, B));
    }

    @Test
    public void sha256MatchesFileBytes() throws Exception {
        Path file = Files.createTempFile("punchclock-sha", ".bin");
        try {
            Files.writeString(file, "hello", StandardCharsets.UTF_8);
            String hash = ClientUpdatePolicy.sha256(file);
            assertEquals(64, hash.length());
            assertTrue(ClientUpdatePolicy.sha256Matches(file, hash.toUpperCase()));
            assertFalse(ClientUpdatePolicy.sha256Matches(file, A));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
