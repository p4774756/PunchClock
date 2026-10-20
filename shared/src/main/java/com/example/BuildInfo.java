package com.example;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

/**
 * 版本與建置時間（桌面端與伺服器共用），來源為 Maven resources filtering 產生的 properties。
 * 沒經過 Maven 建置（例如 IDE 直接跑）時版本為 {@code dev}、時間為空。
 */
public final class BuildInfo {

    public static final String DEV_VERSION = "dev";
    public static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String version;
    private final Instant buildTime;
    private final Instant commitTime;
    /** client／shared 原始碼摘要；沒有打包進 jar 時為空字串。 */
    private final String sourceDigest;

    private BuildInfo(String version, Instant buildTime, Instant commitTime, String sourceDigest) {
        this.version = version;
        this.buildTime = buildTime;
        this.commitTime = commitTime;
        this.sourceDigest = sourceDigest == null ? "" : sourceDigest;
    }

    public static BuildInfo load(Class<?> anchor, String resource) {
        try (InputStream in = anchor.getResourceAsStream(resource)) {
            if (in == null) {
                return new BuildInfo(DEV_VERSION, null, null, "");
            }
            Properties props = new Properties();
            props.load(in);
            return fromProperties(props);
        } catch (Exception e) {
            return new BuildInfo(DEV_VERSION, null, null, "");
        }
    }

    public static BuildInfo fromProperties(Properties props) {
        String version = filtered(props.getProperty("version"));
        return new BuildInfo(version.isEmpty() ? DEV_VERSION : version,
                parseInstant(filtered(props.getProperty("buildTime"))),
                parseInstant(filtered(props.getProperty("commitTime"))),
                normalizeDigest(props.getProperty("sourceDigest")));
    }

    /** 64 位小寫 hex；佔位符或格式不符時為空字串。 */
    public static String normalizeDigest(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.contains("${") || !value.matches("[0-9a-f]{64}")) {
            return "";
        }
        return value;
    }

    public String version() {
        return version;
    }

    /** client／shared 原始碼 SHA-256；未知時為空字串。 */
    public String sourceDigest() {
        return sourceDigest;
    }

    /** 建置編號長度：取原始碼摘要前幾碼，同樣的程式碼在任何環境打包都一樣。 */
    public static final int SHORT_DIGEST_LENGTH = 7;

    /** 摘要前 7 碼；摘要不合法或未知時為空字串。 */
    public static String shortDigest(String digest) {
        String normalized = normalizeDigest(digest);
        return normalized.isEmpty() ? "" : normalized.substring(0, SHORT_DIGEST_LENGTH);
    }

    /**
     * 給人看的版本標示，例如 {@code v2.0.2 · a1b2c3d}。
     * 更新比對靠原始碼摘要，版號不一定每次都改，所以一併顯示建置編號才分得出兩份程式的差別。
     */
    public static String buildLabel(String version, String digest) {
        String base = version == null || version.isBlank() ? "新版本" : "v" + version.trim();
        String shortId = shortDigest(digest);
        return shortId.isEmpty() ? base : base + " · " + shortId;
    }

    public String buildLabel() {
        return buildLabel(version, sourceDigest);
    }

    /** ISO-8601 時間轉成本地顯示格式；空字串或格式不符回空字串。 */
    public static String labelFromIso(String iso, ZoneId zone) {
        return label(parseInstant(iso == null ? "" : iso.trim()), zone);
    }

    /** ISO-8601 UTC，例如 {@code 2026-09-24T07:58:00Z}；未知時為空字串。 */
    public String buildTimeIso() {
        return iso(buildTime);
    }

    /** 例如 {@code 2026-09-24 15:58}；未知時為空字串。 */
    public String buildTimeLabel(ZoneId zone) {
        return label(buildTime, zone);
    }

    /** 最後一次 git commit 時間可用時為 true（打包時找得到 .git）。 */
    public boolean hasCommitTime() {
        return commitTime != null;
    }

    /** 版本時間：優先用 git commit 時間，沒有就用建置時間。 */
    public String releaseTimeIso() {
        return iso(releaseTime());
    }

    public String releaseTimeLabel(ZoneId zone) {
        return label(releaseTime(), zone);
    }

    private Instant releaseTime() {
        return commitTime != null ? commitTime : buildTime;
    }

    private static String iso(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String label(Instant instant, ZoneId zone) {
        if (instant == null) {
            return "";
        }
        return LABEL_FORMAT.format(instant.atZone(zone != null ? zone : ZoneId.systemDefault()));
    }

    private static String filtered(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.contains("${") ? "" : value;
    }

    private static Instant parseInstant(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
