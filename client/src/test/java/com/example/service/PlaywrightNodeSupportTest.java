package com.example.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaywrightNodeSupportTest {

    @Test
    public void testClasspathAlreadyHasTheDriverNode() throws Exception {
        assertTrue(PlaywrightNodeSupport.embeddedNodePresent());
        assertTrue(PlaywrightNodeSupport.playwrightVersion(PlaywrightNodeSupport.platformId())
                .matches("[0-9]+\\.[0-9]+\\.[0-9]+"));
        assertFalse(PlaywrightNodeSupport.mavenDriverBundle("1.49.0").toString().contains("issue"));
    }

    @Test
    public void driverInstallDirUnderPunchclock() {
        Path home = Path.of("/tmp/punchclock-driver-home");
        Path dir = PlaywrightNodeSupport.driverInstallDir(home, "1.49.0", "mac-arm64");
        assertEquals(home.resolve(".punchclock/playwright-driver/1.49.0/mac-arm64"), dir);
        Path winNode = PlaywrightNodeSupport.nodeDestination(home, "1.49.0", "win32_x64");
        assertEquals("node.exe", winNode.getFileName().toString());
        assertEquals(
                home.resolve(".punchclock/playwright-driver/1.49.0/win32_x64/node.exe"),
                winNode);
    }

    @Test
    public void driverZipUrlUsesOfficialCdn() {
        assertEquals(
                "https://playwright.azureedge.net/builds/driver/playwright-1.49.0-mac-arm64.zip",
                PlaywrightNodeSupport.driverZipUrl("1.49.0", "mac-arm64", false).toString());
        assertEquals(
                "https://cdn.playwright.dev/builds/driver/playwright-1.49.0-win32_x64.zip",
                PlaywrightNodeSupport.driverZipUrl("1.49.0", "win32_x64", true).toString());
    }

    @Test
    public void sha256HexMatchesKnownDigest() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                PlaywrightNodeSupport.sha256Hex(new byte[0]));
        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                PlaywrightNodeSupport.sha256Hex("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void verifySha256RejectsMismatch() throws Exception {
        try {
            PlaywrightNodeSupport.verifySha256Hex("abc", "def");
        } catch (java.io.IOException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }

    @Test
    public void extractsDeflatedDriverEntry() throws Exception {
        byte[] payload = "hello-node-binary".getBytes(StandardCharsets.UTF_8);
        Path zip = Files.createTempFile("driver-range-", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.setComment("pack");
            out.putNextEntry(new ZipEntry("driver/mac/node"));
            out.write(payload);
            out.closeEntry();
        }
        Path dest = Files.createTempDirectory("driver-out").resolve("node");
        PlaywrightNodeSupport.extractZipEntry(
                new PlaywrightNodeSupport.FileRangeSource(zip), "driver/mac/node", dest);
        assertArrayEquals(payload, Files.readAllBytes(dest));
    }

    @Test
    public void rejectsVersionThatCouldEscapeTheDownloadPath() {
        try {
            PlaywrightNodeSupport.mavenDriverBundle("../secret");
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }

    @Test
    public void rejectsUnsafeDriverZipUrl() {
        try {
            PlaywrightNodeSupport.driverZipUrl("../secret", "mac", false);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }
}