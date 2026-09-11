package com.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.nio.file.FileVisitOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 將資料夾壓成單一 ZIP，方便走既有單檔暫存通道。
 */
public final class PeerFolderPacker {

    public static final int MAX_ENTRIES = 2000;
    public static final int MAX_DEPTH = 32;

    private PeerFolderPacker() {
    }

    public static PackResult pack(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return PackResult.fail("請選擇要傳送的資料夾");
        }
        Path root = folder.toAbsolutePath().normalize();
        String folderName = PeerFileRules.sanitizeFilename(
                root.getFileName() != null ? root.getFileName().toString() : "folder");
        if (folderName.isEmpty()) {
            folderName = "folder";
        }
        String zipName = folderName.endsWith(".zip") ? folderName : folderName + ".zip";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Counter counter = new Counter();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            final String zipRoot = folderName;
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                                throws IOException {
                            if (dir == null) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (!dir.equals(root) && Files.isSymbolicLink(dir)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            String entryName = zipPath(zipRoot, root, dir, true);
                            if (!entryName.isEmpty()) {
                                addDirectoryEntry(zip, entryName, counter);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                throws IOException {
                            if (file == null || Files.isSymbolicLink(file)
                                    || attrs == null || !attrs.isRegularFile()) {
                                return FileVisitResult.CONTINUE;
                            }
                            String entryName = zipPath(zipRoot, root, file, false);
                            if (entryName.isEmpty()) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (counter.value >= MAX_ENTRIES) {
                                throw new PackLimitException("資料夾檔案過多（最多 " + MAX_ENTRIES + " 個）");
                            }
                            ZipEntry entry = new ZipEntry(entryName);
                            entry.setTime(attrs.lastModifiedTime().toMillis());
                            zip.putNextEntry(entry);
                            Files.copy(file, zip);
                            zip.closeEntry();
                            counter.value++;
                            if (buffer.size() > PeerFileRules.MAX_BYTES) {
                                throw new PackLimitException("壓縮後不可超過 " + PeerFileRules.MAX_SIZE_LABEL);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (PackLimitException ex) {
            return PackResult.fail(ex.getMessage());
        } catch (IOException ex) {
            return PackResult.fail("壓縮資料夾失敗：" + (ex.getMessage() == null ? "IO 錯誤" : ex.getMessage()));
        }

        byte[] bytes = buffer.toByteArray();
        if (bytes.length == 0) {
            return PackResult.fail("資料夾是空的，沒有可傳送的內容");
        }
        if (!PeerFileRules.isAllowedSize(bytes.length)) {
            return PackResult.fail("壓縮後不可超過 " + PeerFileRules.MAX_SIZE_LABEL);
        }
        return PackResult.ok(zipName, bytes, counter.value);
    }

    private static void addDirectoryEntry(ZipOutputStream zip, String entryName, Counter counter)
            throws IOException {
        if (counter.value >= MAX_ENTRIES) {
            throw new PackLimitException("資料夾檔案過多（最多 " + MAX_ENTRIES + " 個）");
        }
        String name = entryName.endsWith("/") ? entryName : entryName + "/";
        zip.putNextEntry(new ZipEntry(name));
        zip.closeEntry();
        counter.value++;
    }

    static String zipPath(String zipRoot, Path root, Path current, boolean directory) {
        if (root == null || current == null) {
            return "";
        }
        Path relative = root.relativize(current);
        StringBuilder sb = new StringBuilder(zipRoot);
        if (relative.getNameCount() == 1 && ".".equals(relative.toString())) {
            return directory ? zipRoot + "/" : "";
        }
        for (int i = 0; i < relative.getNameCount(); i++) {
            String segment = PeerFileRules.sanitizeFilename(relative.getName(i).toString());
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return "";
            }
            sb.append('/').append(segment);
        }
        if (directory) {
            sb.append('/');
        }
        return sb.toString().replace('\\', '/');
    }

    private static final class Counter {
        int value;
    }

    private static final class PackLimitException extends IOException {
        PackLimitException(String message) {
            super(message);
        }
    }

    public static final class PackResult {
        public final boolean ok;
        public final String message;
        public final String filename;
        public final byte[] bytes;
        public final int entryCount;

        private PackResult(boolean ok, String message, String filename, byte[] bytes, int entryCount) {
            this.ok = ok;
            this.message = message;
            this.filename = filename;
            this.bytes = bytes;
            this.entryCount = entryCount;
        }

        public static PackResult ok(String filename, byte[] bytes, int entryCount) {
            return new PackResult(true, "ok", filename, bytes, entryCount);
        }

        public static PackResult fail(String message) {
            return new PackResult(false, message, "", new byte[0], 0);
        }
    }
}
