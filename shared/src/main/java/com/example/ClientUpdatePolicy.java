package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** 桌面端是否該向這台 client 廣告更新，以及 jar 的 SHA-256。 */
public final class ClientUpdatePolicy {

    public static final String JAR_PATH = "/api/client-release/jar";

    private ClientUpdatePolicy() {}

    /**
     * 兩邊都有原始碼摘要且不同才廣告。{@code dev}（IDE 直接跑）與舊版沒送摘要的 client 不廣告。
     */
    public static boolean shouldOffer(String clientVersion, String clientDigest, String releaseDigest) {
        return shouldOffer(clientVersion, clientDigest, "", releaseDigest, "");
    }

    /**
     * 摘要不同之外，若兩邊的時間（ISO-8601）都能解析且 server 上的版本比 client 舊，就不廣告，避免把新版降回舊版。
     * 任一邊沒有時間或格式不對，就只看摘要。時間相同視為可更新（摘要已不同）。
     */
    public static boolean shouldOffer(String clientVersion, String clientDigest, String clientTime,
                                      String releaseDigest, String releaseTime) {
        if (!digestDiffers(clientVersion, clientDigest, releaseDigest)) {
            return false;
        }
        Instant client = parseInstant(clientTime);
        Instant release = parseInstant(releaseTime);
        return client == null || release == null || !release.isBefore(client);
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean digestDiffers(String clientVersion, String clientDigest, String releaseDigest) {
        String release = BuildInfo.normalizeDigest(releaseDigest);
        if (release.isEmpty()) {
            return false;
        }
        if (clientVersion == null || clientVersion.isBlank()
                || BuildInfo.DEV_VERSION.equals(clientVersion.trim())) {
            return false;
        }
        String client = BuildInfo.normalizeDigest(clientDigest);
        return !client.isEmpty() && !release.equals(client);
    }

    public static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256", e);
        }
        byte[] buf = new byte[8192];
        try (InputStream in = Files.newInputStream(path)) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                digest.update(buf, 0, n);
            }
        }
        return hex(digest.digest());
    }

    public static boolean sha256Matches(Path path, String expected) throws IOException {
        String want = BuildInfo.normalizeDigest(expected);
        return !want.isEmpty() && want.equals(sha256(path));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
