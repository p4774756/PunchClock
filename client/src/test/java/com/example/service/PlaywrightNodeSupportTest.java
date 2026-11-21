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
    public void downloadedNodeGoesUnderUserSkLbaby() {
        Path home = Path.of("/tmp/sklbaby-node-home");
        Path mac = PlaywrightNodeSupport.nodeDestination(home, "1.49.0", "mac");
        assertEquals(home.resolve(".sklbaby/playwright/1.49.0/mac/node"), mac);
        Path win = PlaywrightNodeSupport.nodeDestination(home, "1.49.0", "win32_x64");
        assertEquals("node.exe", win.getFileName().toString());
        assertEquals(".sklbaby", win.getParent().getParent().getParent().getParent().getFileName().toString());
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
}