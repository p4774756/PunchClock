package com.example.service;

import org.junit.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    public void driverDownloadUrlsIncludesOfficialCdnAndMaven() {
        List<URI> urls = PlaywrightNodeSupport.driverDownloadUrls("1.49.0", "mac-arm64");
        assertEquals(
                "https://cdn.playwright.dev/dbazure/download/playwright/builds/driver/"
                        + "playwright-1.49.0-mac-arm64.zip",
                urls.get(0).toString());
        assertEquals(
                "https://playwright.download.prss.microsoft.com/dbazure/download/playwright/builds/driver/"
                        + "playwright-1.49.0-mac-arm64.zip",
                urls.get(1).toString());
        assertTrue(urls.get(urls.size() - 1).toString().contains(
                "repo1.maven.org/maven2/com/microsoft/playwright/driver-bundle/1.49.0/driver-bundle-1.49.0.jar"));
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
    public void rejectsUnsafeDriverDownloadUrls() {
        try {
            PlaywrightNodeSupport.driverDownloadUrls("../secret", "mac");
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }

    @Test
    public void extractsFullDriverPlatformTree() throws Exception {
        Path zip = Files.createTempFile("driver-bundle-", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("driver/mac/package/cli.js"));
            out.write("cli".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("driver/mac/node"));
            byte[] node = new byte[9 * 1024 * 1024];
            out.write(node);
            out.closeEntry();
        }
        Path outDir = Files.createTempDirectory("driver-platform-");
        PlaywrightNodeSupport.extractDriverPlatformFromBundle(
                new PlaywrightNodeSupport.FileRangeSource(zip), "mac", outDir);
        assertTrue(Files.isRegularFile(outDir.resolve("node")));
        assertTrue(Files.isRegularFile(outDir.resolve("package").resolve("cli.js")));
    }

    @Test
    public void readsChromiumRevisionFromBrowsersJsonArray() throws Exception {
        Path dir = Files.createTempDirectory("pw-browsers-json").resolve("package");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("browsers.json"),
                "{\n  \"browsers\": [\n"
                        + "    {\n      \"name\": \"chromium\",\n      \"revision\": \"1148\",\n"
                        + "      \"installByDefault\": true\n    },\n"
                        + "    {\n      \"name\": \"chromium-headless-shell\",\n"
                        + "      \"revision\": \"9999\"\n    }\n  ]\n}\n",
                StandardCharsets.UTF_8);
        assertEquals(1148, PlaywrightNodeSupport.readInstalledChromiumRevision(dir.getParent()));
    }
}
