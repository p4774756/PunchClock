package com.example.server;

import com.example.BuildInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** GitHub 最新 Release 上的 SKLbaby.jar 中繼資料。 */
public final class ClientRelease {

    public static final String JAR_ASSET = "SKLbaby.jar";

    public static final ClientRelease NONE = new ClientRelease("", "", "", 0L, "", List.of());

    private final String version;
    /** GitHub {@code published_at}，ISO-8601。 */
    private final String releaseTime;
    private final String sha256;
    private final long size;
    private final String downloadUrl;
    private final List<String> changeLines;

    public ClientRelease(String version, String releaseTime, String sha256, long size, String downloadUrl,
                         List<String> changeLines) {
        this.version = version == null ? "" : version;
        this.releaseTime = releaseTime == null ? "" : releaseTime;
        this.sha256 = sha256 == null ? "" : sha256;
        this.size = size;
        this.downloadUrl = downloadUrl == null ? "" : downloadUrl;
        this.changeLines = changeLines == null ? List.of() : List.copyOf(changeLines);
    }

    public static ClientRelease fromGithub(JsonObject release) {
        if (release == null) {
            return NONE;
        }
        String version = versionFromTag(text(release, "tag_name"));
        if (!version.matches("\\d+\\.\\d+\\.\\d+")) {
            return NONE;
        }
        String published = text(release, "published_at");
        List<String> changes = firstLines(text(release, "body"), 8);
        if (!release.has("assets") || !release.get("assets").isJsonArray()) {
            return NONE;
        }
        JsonArray assets = release.getAsJsonArray("assets");
        for (JsonElement el : assets) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject asset = el.getAsJsonObject();
            if (!JAR_ASSET.equals(text(asset, "name"))) {
                continue;
            }
            long size = asset.has("size") && !asset.get("size").isJsonNull() ? asset.get("size").getAsLong() : 0L;
            String url = text(asset, "browser_download_url");
            String hash = assetSha256(asset);
            if (size <= 0 || url.isEmpty() || hash.isEmpty()) {
                return NONE;
            }
            return new ClientRelease(version, published, hash, size, url, changes);
        }
        return NONE;
    }

    public boolean isAvailable() {
        return version.matches("\\d+\\.\\d+\\.\\d+")
                && !sha256.isEmpty()
                && size > 0
                && !downloadUrl.isEmpty();
    }

    public String version() {
        return version;
    }

    public String releaseTime() {
        return releaseTime;
    }

    public String sha256() {
        return sha256;
    }

    public long size() {
        return size;
    }

    public String downloadUrl() {
        return downloadUrl;
    }

    public List<String> changeLines() {
        return changeLines;
    }

    static String versionFromTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return "";
        }
        String t = tag.trim();
        return t.startsWith("v") || t.startsWith("V") ? t.substring(1) : t;
    }

    static List<String> firstLines(String body, int max) {
        if (body == null || body.isBlank() || max <= 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            out.add(trimmed);
            if (out.size() >= max) {
                break;
            }
        }
        return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
    }

    private static String assetSha256(JsonObject asset) {
        String raw = text(asset, "digest");
        if (raw.startsWith("sha256:")) {
            raw = raw.substring("sha256:".length());
        }
        return BuildInfo.normalizeDigest(raw);
    }

    private static String text(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).getAsString();
    }
}