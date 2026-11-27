package com.example.server.web;

import com.example.ClientUpdatePolicy;
import com.example.server.ClientRelease;
import com.example.server.store.ClientReleaseService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

/** 訪客下載桌面端 jar；檔案導向 GitHub 最新 Release。 */
public final class PublicDownloadRoutes {

    /** 公開下載的檔名（URL 仍為 /download/app.jar）。 */
    static final String PUBLIC_JAR_FILENAME = ClientUpdatePolicy.RELEASE_JAR_FILENAME;

    private final ClientReleaseService clientReleaseService;

    public PublicDownloadRoutes(ClientReleaseService clientReleaseService) {
        this.clientReleaseService = clientReleaseService == null
                ? ClientReleaseService.fixed(ClientRelease.NONE)
                : clientReleaseService;
    }

    public void register(Javalin app) {
        app.get("/download", this::downloadPage);
        app.get("/download/app.jar", this::downloadJar);
        app.get("/download/XiaoBaobei.jar", this::downloadJar);
        app.get("/download/SKLbaby.jar", this::downloadJar);
    }

    /** 首頁下載浮窗的 jar placeholder（與 download.html 同一套邏輯）。 */
    public static String applyHomeDownloadTokens(String html, ClientRelease release) {
        if (html == null) {
            return "";
        }
        ClientRelease r = release != null ? release : ClientRelease.NONE;
        String version = r.isAvailable() ? r.version() : "";
        String jarUrl = r.isAvailable() ? r.downloadUrl() : ClientUpdatePolicy.GITHUB_LATEST_JAR_URL;
        String sizeLabel = r.isAvailable() ? formatClientDownloadSizeMb(r.size()) : "";
        return html
                .replace("{{HOME_JAR_HIDDEN}}", "")
                .replace("{{HOME_JAR_MISSING_HIDDEN}}", " hidden")
                .replace("{{HOME_JAR_URL}}", escapeHtml(jarUrl))
                .replace("{{HOME_CLIENT_VERSION}}", escapeHtml(version))
                .replace("{{HOME_CLIENT_VERSION_JS}}", jsStringLiteral(version))
                .replace("{{HOME_VERSION_HIDDEN}}", version.isEmpty() ? " hidden" : "")
                .replace("{{HOME_CLIENT_SIZE}}", escapeHtml(sizeLabel))
                .replace("{{HOME_SIZE_HIDDEN}}", sizeLabel.isEmpty() ? " hidden" : "")
                .replace("{{HOME_JAVA_VERSION}}", "11");
    }

    /** Release jar 大小（MB），整數或一位小數；無效時回傳空字串。 */
    public static String formatClientDownloadSizeMb(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (Math.abs(mb - Math.rint(mb)) < 1e-9) {
            return String.format(Locale.ROOT, "%.0f MB", mb);
        }
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }

    private void downloadPage(Context ctx) {
        ClientRelease release = clientReleaseService.get(Instant.now().toEpochMilli());
        String version = release.isAvailable() ? release.version() : "";
        String jarUrl = release.isAvailable() ? release.downloadUrl() : ClientUpdatePolicy.GITHUB_LATEST_JAR_URL;
        String sizeLabel = release.isAvailable() ? formatClientDownloadSizeMb(release.size()) : "";
        String html = readResource("/public/download.html")
                .replace("{{JAR_HIDDEN}}", "")
                .replace("{{MISSING_HIDDEN}}", " hidden")
                .replace("{{VERSION_HIDDEN}}", version.isEmpty() ? " hidden" : "")
                .replace("{{CLIENT_VERSION}}", escapeHtml(version))
                .replace("{{CLIENT_SIZE}}", escapeHtml(sizeLabel))
                .replace("{{SIZE_HIDDEN}}", sizeLabel.isEmpty() ? " hidden" : "")
                .replace("{{JAR_DOWNLOAD_URL}}", escapeHtml(jarUrl))
                .replace("{{JAVA_VERSION}}", "11");
        ctx.header("Cache-Control", "no-cache, must-revalidate");
        ctx.html(html);
    }

    private static String jsStringLiteral(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "''";
        }
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private void downloadJar(Context ctx) {
        ClientRelease release = clientReleaseService.get(Instant.now().toEpochMilli());
        String target = release.isAvailable() ? release.downloadUrl() : ClientUpdatePolicy.GITHUB_LATEST_JAR_URL;
        ctx.redirect(target);
    }

    private static String readResource(String path) {
        try (InputStream in = PublicDownloadRoutes.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }

    private static String escapeHtml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}