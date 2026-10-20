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
    public void pack_stopsWhenCancelled() throws Exception {
        Path root = Files.createTempDirectory("peer-folder-cancel-");
        Files.write(root.resolve("big.bin"), new byte[1024 * 1024]);

        PeerFolderPacker.PackResult packed = PeerFolderPacker.pack(root, () -> true);
        assertFalse(packed.ok);
        assertTrue(packed.cancelled);
    }

    @Test
    public void pack_rejectsMissingPath() {
        PeerFolderPacker.PackResult packed = PeerFolderPacker.pack(Path.of("definitely-missing-folder-xyz"));
        assertFalse(packed.ok);
        assertTrue(packed.message.contains("資料夾"));
    }

    @Test
    public void packMany_putsFilesAndFoldersIntoOneZip() throws Exception {
        Path base = Files.createTempDirectory("peer-many-");
        Path report = base.resolve("報告.txt");
        Files.writeString(report, "report");
        Path dirA = Files.createDirectories(base.resolve("a").resolve("photos"));
        Files.writeString(dirA.resolve("x.txt"), "in folder");
        Path sameName = Files.createDirectories(base.resolve("b")).resolve("報告.txt");
        Files.writeString(sameName, "second");

        PeerFolderPacker.PackResult packed = PeerFolderPacker.packMany(
                java.util.Arrays.asList(report, dirA, sameName), null);
        assertTrue(packed.message, packed.ok);
        assertEquals("報告等3個項目.zip", packed.filename);

        java.util.Map<String, String> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(packed.path))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(),
                        entry.isDirectory() ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally {
            packed.deleteQuietly();
        }
        assertEquals("report", entries.get("報告.txt"));
        assertEquals("in folder", entries.get("photos/x.txt"));
        assertEquals("同名檔案要自動改名，不能互相覆蓋", "second", entries.get("報告 (2).txt"));
    }

    @Test
    public void packMany_rejectsEmptyAndMissing() {
        assertFalse(PeerFolderPacker.packMany(java.util.Collections.emptyList(), null).ok);
        assertFalse(PeerFolderPacker.packMany(null, null).ok);
        PeerFolderPacker.PackResult missing = PeerFolderPacker.packMany(
                java.util.Collections.singletonList(Path.of("definitely-missing-xyz")), null);
        assertFalse(missing.ok);
        assertTrue(missing.message.contains("找不到"));
    }

    @Test
    public void packMany_stopsWhenCancelled() throws Exception {
        Path file = Files.createTempFile("peer-many-cancel-", ".bin");
        Files.write(file, new byte[1024 * 1024]);
        try {
            PeerFolderPacker.PackResult packed = PeerFolderPacker.packMany(
                    java.util.Collections.singletonList(file), () -> true);
            assertTrue(packed.cancelled);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
