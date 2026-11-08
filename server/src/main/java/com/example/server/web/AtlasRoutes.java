package com.example.server.web;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 經穴圖靜態站。不必登入。檔案在磁碟，不打進 jar。 */
public final class AtlasRoutes {

    static final String ROOT_PROPERTY = "punchclock.atlas.root";
    private static final Set<String> APPS = Set.of("tcm-3d");
    private static final Map<String, String> TITLES = Map.of("tcm-3d", "中醫人體");

    public AtlasRoutes() {
    }

    public void register(Javalin app) {
        app.get("/atlas/{app}", ctx -> serve(ctx, ""));
        app.get("/atlas/{app}/", ctx -> serve(ctx, ""));
        app.get("/atlas/{app}/<path>", ctx -> serve(ctx, ctx.pathParam("path")));
    }

    private void serve(Context ctx, String relativePath) throws IOException {
        String appId = ctx.pathParam("app");
        if (!APPS.contains(appId)) {
            ctx.status(404).html(page("沒有這份經穴圖"));
            return;
        }
        Path appDir = appDirectory(appId);
        if (appDir == null) {
            ctx.status(404).html(page("沒有這份經穴圖"));
            return;
        }
        if (relativePath.indexOf('\0') >= 0 || relativePath.indexOf('\\') >= 0 || relativePath.contains("..")) {
            ctx.status(404).html(page("找不到這個檔案"));
            return;
        }
        Path file = relativePath.isBlank()
                ? appDir.resolve("index.html")
                : appDir.resolve(relativePath);
        file = file.normalize();
        if (!file.startsWith(appDir)) {
            ctx.status(404).html(page("找不到這個檔案"));
            return;
        }
        if (Files.isDirectory(file)) {
            file = file.resolve("index.html").normalize();
        }
        if (!file.startsWith(appDir) || !Files.isRegularFile(file)) {
            ctx.status(404).html(relativePath.isBlank() ? notBuilt(appId) : page("找不到這個檔案"));
            return;
        }
        ctx.minSizeForCompression(Integer.MAX_VALUE);
        ctx.contentType(contentType(file));
        ctx.header("Content-Length", Long.toString(Files.size(file)));
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("Cache-Control", "private");
        ctx.result(Files.newInputStream(file));
    }

    static Path atlasRoot() {
        Path configured = configuredRoot();
        if (configured != null) {
            return configured;
        }
        return Path.of(System.getProperty("user.home"), ".punchclock", "atlas").toAbsolutePath().normalize();
    }

    /** 有設定目錄時用該目錄；否則優先使用 atlas-src 裡剛建好的 dist。 */
    static Path appDirectory(String appId) {
        Path configured = configuredRoot();
        if (configured != null) {
            Path dir = configured.resolve(appId).normalize();
            return dir.startsWith(configured) ? dir : null;
        }
        Path built = sourceDist(appId);
        if (built != null) {
            return built;
        }
        return atlasRoot().resolve(appId).normalize();
    }

    private static Path configuredRoot() {
        String property = System.getProperty(ROOT_PROPERTY);
        if (property != null && !property.isBlank()) {
            return Path.of(property).toAbsolutePath().normalize();
        }
        String env = System.getenv("ATLAS_ROOT");
        if (env != null && !env.isBlank()) {
            return Path.of(env).toAbsolutePath().normalize();
        }
        return null;
    }

    private static Path sourceDist(String appId) {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 5 && dir != null; i++) {
            Path dist = dir.resolve("atlas-src").resolve(appId).resolve("dist").normalize();
            if (Files.isRegularFile(dist.resolve("index.html"))) {
                return dist;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static String notBuilt(String appId) {
        String title = TITLES.getOrDefault(appId, "經穴圖");
        return page(title + "還沒建置。在 atlas-src 執行建置後重新整理。");
    }

    private static String page(String message) {
        return "<!DOCTYPE html><html lang=\"zh-Hant\"><head><meta charset=\"utf-8\"><title>經穴圖</title></head>"
                + "<body style=\"font-family:sans-serif;padding:2rem\">"
                + "<p>" + escape(message) + "</p>"
                + "<p><a href=\"/\">回店面</a></p></body></html>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js") || name.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".wasm")) return "application/wasm";
        if (name.endsWith(".glb")) return "model/gltf-binary";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".txt") || name.endsWith(".md")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
