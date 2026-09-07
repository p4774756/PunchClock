package com.example;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 同事互傳檔案的共用規則（桌面端與伺服器必須一致）。
 */
public final class PeerFileRules {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final String MAX_SIZE_LABEL = "5 MB";

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif", "webp",
            "txt", "csv", "md", "json", "zip"
    );

    private static final Map<String, String> MIME_BY_EXT = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("md", "text/markdown"),
            Map.entry("json", "application/json"),
            Map.entry("zip", "application/zip")
    );

    private PeerFileRules() {
    }

    public static String sanitizeFilename(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder cleaned = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || c == 127 || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                cleaned.append('_');
            } else {
                cleaned.append(c);
            }
        }
        name = cleaned.toString().trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return "";
        }
        if (name.length() > 180) {
            String ext = extensionOf(name);
            int keep = ext.isEmpty() ? 180 : Math.max(1, 180 - ext.length() - 1);
            String base = name.substring(0, Math.min(keep, name.length()));
            name = ext.isEmpty() ? base : base + "." + ext;
        }
        return name;
    }

    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        String name = filename.trim();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isAllowedFilename(String filename) {
        return ALLOWED_EXTENSIONS.contains(extensionOf(filename));
    }

    public static String mimeFor(String filename) {
        String mime = MIME_BY_EXT.get(extensionOf(filename));
        return mime != null ? mime : "application/octet-stream";
    }

    public static boolean isAllowedSize(long bytes) {
        return bytes > 0 && bytes <= MAX_BYTES;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static String allowedTypesHint() {
        return "PDF、PNG、JPG、GIF、WEBP、TXT、CSV、MD、JSON、ZIP（最大 " + MAX_SIZE_LABEL + "）";
    }

    public static String encodeName(String filename) {
        String value = filename == null ? "" : filename;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeName(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return encoded;
        }
    }
}
