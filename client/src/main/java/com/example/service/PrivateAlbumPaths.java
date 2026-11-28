package com.example.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 私房相簿本機資料夾路徑與圖檔列舉（不讀寫 repo 內資源）。 */
public final class PrivateAlbumPaths {

    private PrivateAlbumPaths() {
    }

    public static Path defaultDirectory() {
        String home = System.getProperty("user.home", ".");
        return Paths.get(home, ".sklbaby", "album");
    }

    public static Path resolveDirectory(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return defaultDirectory();
        }
        return Paths.get(configuredPath.trim());
    }

    public static boolean isImageFileName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp");
    }

    public static List<Path> listImages(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                Path name = entry.getFileName();
                if (name != null && isImageFileName(name.toString())) {
                    out.add(entry);
                }
            }
        }
        out.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        return Collections.unmodifiableList(out);
    }

    public static void ensureDirectory(Path directory) throws IOException {
        if (directory == null) {
            return;
        }
        Files.createDirectories(directory);
    }
}