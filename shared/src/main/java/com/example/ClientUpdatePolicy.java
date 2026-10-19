package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** 桌面端是否該向這台 client 廣告更新，以及 jar 的 SHA-256。 */
public final class ClientUpdatePolicy {

    public static final String JAR_PATH = "/api/client-release/jar";

    private ClientUpdatePolicy() {}

    /**
     * 兩邊都有原始碼摘要且不同才廣告。{@code dev}（IDE 直接跑）與舊版沒送摘要的 client 不廣告。
     */
    public static boolean shouldOffer(String clientVersion, String clientDigest, String releaseDigest) {
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
