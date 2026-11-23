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

    /** 首頁下載浮窗的 jar／安裝檔 placeholder（與 download.html 同一套邏輯）。 */
    public static String applyHomeDownloadTokens(String html, ClientRelease release) {
        if (html == null) {
            return "";
        }
        ClientRelease r = release != null ? release : ClientRelease.NONE;
        String version = r.isAvailable() ? r.version() : "";
        String jarUrl = r.isAvailable() ? r.downloadUrl() : ClientUpdatePolicy.GITHUB_LATEST_JAR_URL;
        String macArmUrl = desktopEnv("DESKTOP_MAC_ARM_DOWNLOAD_URL");
        if (macArmUrl.isEmpty()) {
            macArmUrl = desktopEnv("DESKTOP_MAC_DOWNLOAD_URL");
        }
        String macIntelUrl = desktopEnv("DESKTOP_MAC_INTEL_DOWNLOAD_URL");
        String winUrl = desktopEnv("DESKTOP_WIN_DOWNLOAD_URL");
        return html
                .replace("{{HOME_MAC_ARM_HIDDEN}}", macArmUrl.isEmpty() ? " hidden" : "")
                .replace("{{HOME_MAC_INTEL_HIDDEN}}", macIntelUrl.isEmpty() ? " hidden" : "")
                .replace("{{HOME_WIN_HIDDEN}}", winUrl.isEmpty() ? " hidden" : "")
                .replace("{{HOME_DESKTOP_MISSING_HIDDEN}}",
                        (!macArmUrl.isEmpty() || !macIntelUrl.isEmpty() || !winUrl.isEmpty()) ? " hidden" : "")
                .replace("{{HOME_JAR_HIDDEN}}", "")
                .replace("{{HOME_JAR_MISSING_HIDDEN}}", " hidden")
                .replace("{{HOME_MAC_ARM_URL}}", escapeHtml(macArmUrl))
                .replace("{{HOME_MAC_INTEL_URL}}", escapeHtml(macIntelUrl))
                .replace("{{HOME_WIN_URL}}", escapeHtml(winUrl))
                .replace("{{HOME_JAR_URL}}", escapeHtml(jarUrl))
                .replace("{{HOME_CLIENT_VERSION}}", escapeHtml(version))
                .replace("{{DESKTOP_MAC_ARM_JS}}", jsStringLiteral(macArmUrl))
                .replace("{{DESKTOP_MAC_INTEL_JS}}", jsStringLiteral(macIntelUrl))
                .replace("{{DESKTOP_MAC_JS}}", jsStringLiteral(macArmUrl))
                .replace("{{DESKTOP_WIN_JS}}", jsStringLiteral(winUrl));
    }

    private void downloadPage(Context ctx) {
        ClientRelease release = clientReleaseService.get(Instant.now().toEpochMilli());
        String version = release.isAvailable() ? release.version() : "";
        String jarUrl = release.isAvailable() ? release.downloadUrl() : ClientUpdatePolicy.GITHUB_LATEST_JAR_URL;
        String macArmUrl = desktopEnv("DESKTOP_MAC_ARM_DOWNLOAD_URL");
        if (macArmUrl.isEmpty()) {
            macArmUrl = desktopEnv("DESKTOP_MAC_DOWNLOAD_URL");
        }
        String macIntelUrl = desktopEnv("DESKTOP_MAC_INTEL_DOWNLOAD_URL");
        String winUrl = desktopEnv("DESKTOP_WIN_DOWNLOAD_URL");
        boolean desktopKnown = !macArmUrl.isEmpty() || !macIntelUrl.isEmpty() || !winUrl.isEmpty();
        String html = readResource("/public/download.html")
                .replace("{{JAR_HIDDEN}}", "")
                .replace("{{MISSING_HIDDEN}}", " hidden")
                .replace("{{MAC_ARM_HIDDEN}}", macArmUrl.isEmpty() ? " hidden" : "")
                .replace("{{MAC_INTEL_HIDDEN}}", macIntelUrl.isEmpty() ? " hidden" : "")
                .replace("{{WIN_HIDDEN}}", winUrl.isEmpty() ? " hidden" : "")
                .replace("{{DESKTOP_MISSING_HIDDEN}}", desktopKnown ? " hidden" : "")
                .replace("{{DESKTOP_MAC_ARM_URL}}", escapeHtml(macArmUrl))
                .replace("{{DESKTOP_MAC_INTEL_URL}}", escapeHtml(macIntelUrl))
                .replace("{{DESKTOP_WIN_URL}}", escapeHtml(winUrl))
                .replace("{{DESKTOP_MAC_ARM_JS}}", jsStringLiteral(macArmUrl))
                .replace("{{DESKTOP_MAC_INTEL_JS}}", jsStringLiteral(macIntelUrl))
                .replace("{{DESKTOP_MAC_JS}}", jsStringLiteral(macArmUrl))
                .replace("{{DESKTOP_WIN_JS}}", jsStringLiteral(winUrl))
                .replace("{{CLIENT_VERSION}}", escapeHtml(version))
                .replace("{{JAR_DOWNLOAD_URL}}", escapeHtml(jarUrl))
                .replace("{{JAVA_VERSION}}", "11");
        ctx.header("Cache-Control", "no-cache, must-revalidate");
        ctx.html(html);
    }

    private static String desktopEnv(String key) {
        String raw = System.getenv(key);
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.contains("${")) {
            return "";
        }
        return trimmed;
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