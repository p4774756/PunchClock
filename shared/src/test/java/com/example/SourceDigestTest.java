package com.example;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SourceDigestTest {

    @Test
    public void digestChangesWhenClientSourceChanges() throws Exception {
        Path root = Files.createTempDirectory("punchclock-src");
        try {
            write(root.resolve("client/src/main/App.java"), "class App {}");
            write(root.resolve("shared/src/main/Lib.java"), "class Lib {}");
            write(root.resolve("client/pom.xml"), "<project/>");
            write(root.resolve("shared/pom.xml"), "<project/>");
            write(root.resolve("client/src/main/.DS_Store"), "junk");

            String first = SourceDigest.compute(root);
            assertEquals(64, first.length());
            assertEquals(first, SourceDigest.compute(root));

            write(root.resolve("client/src/main/App.java"), "class App { int x; }");
            assertNotEquals(first, SourceDigest.compute(root));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void missingTreeIsEmpty() throws Exception {
        Path root = Files.createTempDirectory("punchclock-src-missing");
        try {
            assertEquals("", SourceDigest.compute(root));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void repoDigestIsStableHex() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        if (!Files.isDirectory(root.resolve("client/src/main"))) {
            root = Path.of(".").toAbsolutePath().normalize();
        }
        if (!Files.isDirectory(root.resolve("client/src/main"))) {
            return;
        }
        String digest = SourceDigest.compute(root);
        assertTrue(digest.matches("[0-9a-f]{64}"));
        assertEquals(digest, SourceDigest.compute(root));
    }

    private static void write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static void deleteTree(Path root) throws Exception {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // best-effort
                }
            });
        }
    }
}
