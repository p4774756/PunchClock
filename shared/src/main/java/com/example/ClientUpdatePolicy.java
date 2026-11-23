package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/** 桌面端是否該向 client 廣告更新，jar 下載網址與 SHA-256。 */
public final class ClientUpdatePolicy {

    public static final String GITHUB_REPO = "p4774756/PunchClock";
    public static final String GITHUB_JAR_DOWNLOAD_PREFIX =
            "https://github.com/" + GITHUB_REPO + "/releases/download/";
    public static final String GITHUB_LATEST_JAR_URL =
            "https://github.com/" + GITHUB_REPO + "/releases/latest/download/SKLbaby.jar";

  private static final Pattern SEMVER = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private ClientUpdatePolicy() {}

    /**
     * 只有 GitHub Release 的 jar 網址比本機新才廣告。{@code dev}（IDE 直接跑）或版號格式不符者不廣告。
     */
    public static boolean shouldOffer(String clientVersion, String releaseVersion) {
        if (clientVersion == null || clientVersion.isBlank()
                || BuildInfo.DEV_VERSION.equals(clientVersion.trim())) {
            return false;
        }
        if (releaseVersion == null || releaseVersion.isBlank()) {
            return false;
        }
        return compareSemver(clientVersion.trim(), releaseVersion.trim()) < 0;
    }

    /** 語意化版號比較：負數表示 a 較舊、零相同、正數 a 較新。無法解析時回傳 0（視為相同，不更新）。 */
    public static int compareSemver(String a, String b) {
        int[] av = parseSemver(a);
        int[] bv = parseSemver(b);
        if (av == null || bv == null) {
            return 0;
        }
        for (int i = 0; i < 3; i++) {
            int diff = av[i] - bv[i];
            if (diff != 0) {
                return Integer.compare(av[i], bv[i]);
            }
        }
        return 0;
    }

    /**
     * 允許從 GitHub Release 下載的 jar 起始網址，或 302 目標為 GitHub／githubusercontent 主機。
     */
    public static boolean isAllowedClientUpdateUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith(GITHUB_JAR_DOWNLOAD_PREFIX)) {
            return true;
        }
        try {
            return isAllowedGithubHost(URI.create(trimmed).getHost());
        } catch (Exception ex) {
            return false;
        }
    }

    public static boolean isAllowedGithubHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return "github.com".equals(h) || h.endsWith(".githubusercontent.com");
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

    private static int[] parseSemver(String raw) {
        if (raw == null) {
            return null;
        }
        var m = SEMVER.matcher(raw.trim());
        if (!m.matches()) {
            return null;
        }
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}