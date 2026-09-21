package com.example;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 將資料夾壓成單一 ZIP（寫到暫存檔），方便走既有單檔暫存通道。
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

        Path zipPath;
        try {
            zipPath = Files.createTempFile("punchclock-folder-", ".zip");
        } catch (IOException ex) {
            return PackResult.fail("無法建立壓縮暫存檔："
                    + (ex.getMessage() == null ? "IO 錯誤" : ex.getMessage()));
        }

        Counter counter = new Counter();
        try (OutputStream fileOut = Files.newOutputStream(zipPath);
             CountingOutputStream counted = new CountingOutputStream(fileOut);
             ZipOutputStream zip = new ZipOutputStream(counted)) {
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
                            if (counted.count > PeerFileRules.MAX_BYTES) {
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
            deleteQuietly(zipPath);
            return PackResult.fail(ex.getMessage());
        } catch (IOException ex) {
            deleteQuietly(zipPath);
            return PackResult.fail("壓縮資料夾失敗：" + (ex.getMessage() == null ? "IO 錯誤" : ex.getMessage()));
        }

        long size;
        try {
            size = Files.size(zipPath);
        } catch (IOException ex) {
            deleteQuietly(zipPath);
            return PackResult.fail("無法讀取壓縮檔大小");
        }
        if (size <= 0) {
            deleteQuietly(zipPath);
            return PackResult.fail("資料夾是空的，沒有可傳送的內容");
        }
        if (!PeerFileRules.isAllowedSize(size)) {
            deleteQuietly(zipPath);
            return PackResult.fail("壓縮後不可超過 " + PeerFileRules.MAX_SIZE_LABEL);
        }
        return PackResult.ok(zipName, zipPath, size, counter.value);
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

    static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private static final class Counter {
        int value;
    }

    private static final class PackLimitException extends IOException {
        PackLimitException(String message) {
            super(message);
        }
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }

    public static final class PackResult {
        public final boolean ok;
        public final String message;
        public final String filename;
        /** 成功時為暫存 ZIP 路徑；呼叫端用完後應 {@link #deleteQuietly()}。 */
        public final Path path;
        public final long size;
        public final int entryCount;

        private PackResult(boolean ok, String message, String filename, Path path, long size, int entryCount) {
            this.ok = ok;
            this.message = message;
            this.filename = filename;
            this.path = path;
            this.size = size;
            this.entryCount = entryCount;
        }

        public static PackResult ok(String filename, Path path, long size, int entryCount) {
            return new PackResult(true, "ok", filename, path, size, entryCount);
        }

        public static PackResult fail(String message) {
            return new PackResult(false, message, "", null, 0L, 0);
        }

        public void deleteQuietly() {
            PeerFolderPacker.deleteQuietly(path);
        }
    }
}
