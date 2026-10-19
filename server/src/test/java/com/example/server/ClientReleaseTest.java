package com.example.server;

import org.junit.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientReleaseTest {

    private static final String DIGEST = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    public void readsVersionDigestAndHashesJar() throws Exception {
        Path jar = Files.createTempFile("punchclock-client", ".jar");
        try {
            writeJar(jar, "1.8.0", DIGEST);
            ClientRelease release = ClientRelease.fromJar(jar);
            assertTrue(release.isAvailable());
            assertEquals("1.8.0", release.version());
            assertEquals(DIGEST, release.sourceDigest());
            assertEquals(64, release.sha256().length());
            assertEquals(Files.size(jar), release.size());
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    @Test
    public void placeholderDigestIsNotAvailable() throws Exception {
        Path jar = Files.createTempFile("punchclock-client", ".jar");
        try {
            writeJar(jar, "1.8.0", "${punchclock.sourceDigest}");
            ClientRelease release = ClientRelease.fromJar(jar);
            assertFalse(release.isAvailable());
            assertEquals("", release.sourceDigest());
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    private static void writeJar(Path jar, String version, String digest) throws Exception {
        Properties props = new Properties();
        props.setProperty("version", version);
        props.setProperty("sourceDigest", digest);
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(out)) {
            jos.putNextEntry(new JarEntry("version.properties"));
            props.store(jos, "");
            jos.closeEntry();
        }
    }
}
