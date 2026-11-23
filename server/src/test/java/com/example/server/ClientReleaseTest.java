package com.example.server;

import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientReleaseTest {

    private static final String DIGEST =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    public void parsesGithubReleaseAsset() {
        String json = "{"
                + "\"tag_name\":\"v4.0.2\","
                + "\"published_at\":\"2026-11-01T12:00:00Z\","
                + "\"body\":\"第一行\\n\\n第二行\","
                + "\"assets\":[{\"name\":\"SKLbaby.jar\",\"size\":12345,"
                + "\"browser_download_url\":\"https://github.com/p4774756/PunchClock/releases/download/v4.0.2/SKLbaby.jar\","
                + "\"digest\":\"sha256:" + DIGEST + "\"}]"
                + "}";
        ClientRelease release = ClientRelease.fromGithub(JsonParser.parseString(json).getAsJsonObject());
        assertTrue(release.isAvailable());
        assertEquals("4.0.2", release.version());
        assertEquals(DIGEST, release.sha256());
        assertEquals(12345L, release.size());
        assertEquals(2, release.changeLines().size());
        assertEquals("第一行", release.changeLines().get(0));
    }

    @Test
    public void fallsBackToLegacyXiaoBaobeiJarAsset() {
        String json = "{"
                + "\"tag_name\":\"v4.0.2\","
                + "\"published_at\":\"2026-11-01T12:00:00Z\","
                + "\"body\":\"\","
                + "\"assets\":[{\"name\":\"XiaoBaobei.jar\",\"size\":99,"
                + "\"browser_download_url\":\"https://github.com/p4774756/PunchClock/releases/download/v4.0.2/XiaoBaobei.jar\","
                + "\"digest\":\"sha256:" + DIGEST + "\"}]"
                + "}";
        ClientRelease release = ClientRelease.fromGithub(JsonParser.parseString(json).getAsJsonObject());
        assertTrue(release.isAvailable());
        assertEquals("4.0.2", release.version());
    }

    @Test
    public void prefersSklbabyWhenBothJarAssetsPresent() {
        String json = "{"
                + "\"tag_name\":\"v4.0.3\","
                + "\"published_at\":\"2026-11-01T12:00:00Z\","
                + "\"body\":\"\","
                + "\"assets\":["
                + "{\"name\":\"XiaoBaobei.jar\",\"size\":1,"
                + "\"browser_download_url\":\"https://github.com/p4774756/PunchClock/releases/download/v4.0.3/XiaoBaobei.jar\","
                + "\"digest\":\"sha256:" + DIGEST + "\"},"
                + "{\"name\":\"SKLbaby.jar\",\"size\":20000,"
                + "\"browser_download_url\":\"https://github.com/p4774756/PunchClock/releases/download/v4.0.3/SKLbaby.jar\","
                + "\"digest\":\"sha256:" + DIGEST + "\"}"
                + "]"
                + "}";
        ClientRelease release = ClientRelease.fromGithub(JsonParser.parseString(json).getAsJsonObject());
        assertTrue(release.isAvailable());
        assertEquals("4.0.3", release.version());
        assertEquals(20000L, release.size());
        assertTrue(release.downloadUrl().endsWith("/SKLbaby.jar"));
    }

    @Test
    public void missingJarAssetIsNotAvailable() {
        String json = "{\"tag_name\":\"v4.0.2\",\"assets\":[{\"name\":\"other.zip\",\"size\":1}]}";
        ClientRelease release = ClientRelease.fromGithub(JsonParser.parseString(json).getAsJsonObject());
        assertFalse(release.isAvailable());
    }

    @Test
    public void firstLinesSkipsBlanks() {
        assertEquals(1, ClientRelease.firstLines("  hello \n\n", 3).size());
        assertEquals("hello", ClientRelease.firstLines("  hello \n\n", 3).get(0));
    }

    @Test
    public void versionFromTagStripsV() {
        assertEquals("4.0.3", ClientRelease.versionFromTag("v4.0.3"));
        assertEquals("4.0.3", ClientRelease.versionFromTag("V4.0.3"));
    }
}