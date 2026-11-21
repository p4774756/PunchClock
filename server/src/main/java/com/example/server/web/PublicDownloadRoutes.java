package com.example.server.web;

import com.example.server.ClientRelease;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** 訪客可下載桌面端 jar（無心跳密鑰；與 /api/client-release 授權分開）。 */
public final class PublicDownloadRoutes {

    /** 公開下載的檔名（URL 仍為 /download/app.jar）。 */
    static final String PUBLIC_JAR_FILENAME = "SKLbaby.jar";

    private final ClientRelease clientRelease;

    public PublicDownloadRoutes(ClientRelease clientRelease) {
        this.clientRelease = clientRelease == null ? ClientRelease.NONE : clientRelease;
    }

    public void register(Javalin app) {
        app.get("/download", this::downloadPage);
        app.get("/download/app.jar", this::downloadJar);
        app.get("/download/SKLbaby.jar", this::downloadJar);
    }

    private void downloadPage(Context ctx) {
        boolean available = clientRelease.isAvailable();
        String version = available ? clientRelease.version() : "";
        String html = readResource("/public/download.html")
                .replace("{{JAR_HIDDEN}}", available ? "" : " hidden")
                .replace("{{MISSING_HIDDEN}}", available ? " hidden" : "")
                .replace("{{CLIENT_VERSION}}", escapeHtml(version))
                .replace("{{JAVA_VERSION}}", "11");
        ctx.header("Cache-Control", "no-cache, must-revalidate");
        ctx.html(html);
    }

    private void downloadJar(Context ctx) {
        if (!clientRelease.isAvailable()) {
            ctx.status(HttpStatus.NOT_FOUND).json(java.util.Map.of(
                    "success", false,
                    "message", "目前沒有可下載的桌面端"
            ));
            return;
        }
        InputStream stream;
        try {
            stream = Files.newInputStream(clientRelease.jar());
        } catch (IOException ex) {
            ctx.status(HttpStatus.NOT_FOUND).json(java.util.Map.of(
                    "success", false,
                    "message", "桌面端檔案不存在"
            ));
            return;
        }
        ctx.minSizeForCompression(Integer.MAX_VALUE);
        ctx.contentType("application/java-archive");
        ctx.header("Content-Disposition", "attachment; filename=\"" + PUBLIC_JAR_FILENAME + "\"");
        ctx.header("Content-Length", String.valueOf(clientRelease.size()));
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.result(stream);
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