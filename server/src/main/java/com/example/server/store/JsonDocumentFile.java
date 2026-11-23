package com.example.server.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

/** 單一 JSON 檔：原子寫入、寫前備份 .bak。呼叫端負責 synchronized。 */
public final class JsonDocumentFile {

    private final Path file;
    private final Function<String, ?> parser;
    private final Function<Object, String> serializer;

    public JsonDocumentFile(Path file, Function<String, ?> parser, Function<Object, String> serializer) {
        this.file = file;
        this.parser = parser;
        this.serializer = serializer;
    }

    public Path file() {
        return file;
    }

    @SuppressWarnings("unchecked")
    public <T> T readOr(T fallback) {
        if (!Files.isRegularFile(file)) {
            Path bak = backupPath();
            if (Files.isRegularFile(bak)) {
                return readFile(bak, fallback);
            }
            return fallback;
        }
        T value = readFile(file, fallback);
        return value != null ? value : fallback;
    }

    public void write(Object document) throws IOException {
        Files.createDirectories(file.getParent());
        Path dest = file;
        Path bak = backupPath();
        if (Files.isRegularFile(dest)) {
            Files.copy(dest, bak, StandardCopyOption.REPLACE_EXISTING);
        }
        Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
        Files.writeString(tmp, serializer.apply(document), StandardCharsets.UTF_8);
        moveReplace(tmp, dest);
    }

    private Path backupPath() {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    @SuppressWarnings("unchecked")
    private <T> T readFile(Path path, T fallback) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return fallback;
            }
            return (T) parser.apply(json);
        } catch (Exception ex) {
            Path bak = backupPath();
            if (!path.equals(bak) && Files.isRegularFile(bak)) {
                try {
                    String json = Files.readString(bak, StandardCharsets.UTF_8);
                    return (T) parser.apply(json);
                } catch (Exception ignored) {
                    return fallback;
                }
            }
            return fallback;
        }
    }

    private static void moveReplace(Path tmp, Path dest) throws IOException {
        try {
            Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}