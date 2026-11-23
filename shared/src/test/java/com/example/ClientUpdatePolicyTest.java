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

    @Test
    public void offersOnlyWhenReleaseIsNewer() {
        assertTrue(ClientUpdatePolicy.shouldOffer("1.8.0", "1.8.1"));
        assertTrue(ClientUpdatePolicy.shouldOffer("1.8.0", "2.0.0"));
        assertFalse(ClientUpdatePolicy.shouldOffer("1.8.0", "1.8.0"));
        assertFalse(ClientUpdatePolicy.shouldOffer("1.8.1", "1.8.0"));
        assertFalse(ClientUpdatePolicy.shouldOffer(BuildInfo.DEV_VERSION, "1.8.1"));
        assertFalse(ClientUpdatePolicy.shouldOffer("", "1.8.1"));
    }

    @Test
    public void compareSemverOrdersMajorMinorPatch() {
        assertEquals(-1, ClientUpdatePolicy.compareSemver("1.0.0", "2.0.0"));
        assertEquals(1, ClientUpdatePolicy.compareSemver("1.2.0", "1.1.9"));
        assertEquals(0, ClientUpdatePolicy.compareSemver("4.0.2", "4.0.2"));
        assertEquals(0, ClientUpdatePolicy.compareSemver("bad", "1.0.0"));
    }

    @Test
    public void allowedDownloadUrlsAcceptGithubReleaseAndRedirectHosts() {
        String url = ClientUpdatePolicy.GITHUB_JAR_DOWNLOAD_PREFIX + "v4.0.2/SKLbaby.jar";
        assertTrue(ClientUpdatePolicy.isAllowedClientUpdateUrl(url));
        assertTrue(ClientUpdatePolicy.isAllowedClientUpdateUrl(
                ClientUpdatePolicy.GITHUB_JAR_DOWNLOAD_PREFIX + "v4.0.2/XiaoBaobei.jar"));
        assertTrue(ClientUpdatePolicy.isAllowedClientUpdateUrl(
                "https://objects.githubusercontent.com/github-production-release-asset-2e65be/123/456"));
        assertTrue(ClientUpdatePolicy.isAllowedGithubHost("github.com"));
        assertTrue(ClientUpdatePolicy.isAllowedGithubHost("release-assets.githubusercontent.com"));
        assertFalse(ClientUpdatePolicy.isAllowedClientUpdateUrl("https://evil.example/SKLbaby.jar"));
        assertFalse(ClientUpdatePolicy.isAllowedClientUpdateUrl("/api/client-release/jar"));
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