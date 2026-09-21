package com.example;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PeerFolderPackerTest {

    @Test
    public void pack_includesNestedFilesAndEmptyDirs() throws Exception {
        Path root = Files.createTempDirectory("peer-folder-");
        Path nested = root.resolve("docs");
        Files.createDirectories(nested);
        Files.createDirectories(root.resolve("empty"));
        Files.writeString(nested.resolve("readme.txt"), "hello folder");
        Files.write(root.resolve("data.bin"), "MZ".getBytes(StandardCharsets.UTF_8));

        PeerFolderPacker.PackResult packed = PeerFolderPacker.pack(root);
        assertTrue(packed.ok);
        assertTrue(packed.filename.endsWith(".zip"));
        assertTrue(packed.entryCount >= 3);
        assertTrue(packed.size > 0);
        assertNotNull(packed.path);

        boolean sawReadme = false;
        boolean sawBin = false;
        boolean sawEmptyDir = false;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(packed.path))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.endsWith("docs/readme.txt")) {
                    sawReadme = true;
                    assertEquals("hello folder", new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
                if (name.endsWith("data.bin")) {
                    sawBin = true;
                }
                if (name.endsWith("empty/") || name.endsWith("empty")) {
                    sawEmptyDir = true;
                }
            }
        } finally {
            packed.deleteQuietly();
        }
        assertTrue(sawReadme);
        assertTrue(sawBin);
        assertTrue(sawEmptyDir);
    }

    @Test
    public void pack_rejectsMissingPath() {
        PeerFolderPacker.PackResult packed = PeerFolderPacker.pack(Path.of("definitely-missing-folder-xyz"));
        assertFalse(packed.ok);
        assertTrue(packed.message.contains("資料夾"));
    }
}
