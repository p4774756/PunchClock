package com.example;

import com.example.service.ClientUpdateInstaller;
import com.example.service.JarBuildInfo;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SklbabyLauncherTest {

    @Test
    public void pickJarPrefersNewerUserCopy() throws Exception {
        Path bundled = jarWithDigest("4.0.0", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "2026-01-01T00:00:00Z");
        Path user = jarWithDigest("4.0.0", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "2026-02-01T00:00:00Z");
        assertEquals(user, SklbabyLauncher.pickJar(bundled, user));
    }

    @Test
    public void pickJarKeepsBundledWhenUserIsOlder() throws Exception {
        Path bundled = jarWithDigest("4.0.0", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "2026-02-01T00:00:00Z");
        Path user = jarWithDigest("4.0.0", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "2026-01-01T00:00:00Z");
        assertEquals(bundled, SklbabyLauncher.pickJar(bundled, user));
    }

    @Test
    public void quarantineRenamesBrokenUserJar() throws Exception {
        Path home = Files.createTempDirectory("sklbaby-test-home");
        Path user = home.resolve(".sklbaby/app/SKLbaby.jar");
        Files.createDirectories(user.getParent());
        Files.writeString(user, "broken", StandardCharsets.UTF_8);
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            SklbabyLauncher.quarantineBrokenUserJar(ClientUpdateInstaller.userAppJar());
            assertFalse(Files.exists(user));
            try (var stream = Files.list(user.getParent())) {
                assertTrue(stream.anyMatch(p -> p.getFileName().toString().startsWith("SKLbaby.jar.bad-")));
            }
        } finally {
            if (previousHome != null) {
                System.setProperty("user.home", previousHome);
            }
        }
    }

    @Test
    public void jarBuildInfoReadsVersionProperties() throws Exception {
        Path jar = jarWithDigest("4.0.0", "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", "2026-03-01T00:00:00Z");
        JarBuildInfo info = JarBuildInfo.fromJar(jar);
        assertEquals("4.0.0", info.version());
        assertEquals("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", info.sourceDigest());
        assertTrue(info.isUsable());
    }

    private static Path jarWithDigest(String version, String digest, String commitTime) throws IOException {
        Path jar = Files.createTempFile("sklbaby-", ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            String props = "version=" + version + "\n"
                    + "sourceDigest=" + digest + "\n"
                    + "commitTime=" + commitTime + "\n";
            out.putNextEntry(new JarEntry("version.properties"));
            out.write(props.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }
}