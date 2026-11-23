package com.example.server.web;

import com.example.server.auth.AuthService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 經穴圖靜態站。須登入。檔案在磁碟，不打進 jar。 */
public final class AtlasRoutes {

    private final AuthService authService;

    static final String ROOT_PROPERTY = "punchclock.atlas.root";
    private static final Set<String> APPS = Set.of("tcm-3d");
    private static final Map<String, String> TITLES = Map.of("tcm-3d", "中醫人體");
    /** Vite／Rolldown 檔名雜湊是 base64url，含 {@code _} 與 {@code -}。 */
    private static final Pattern FINGERPRINTED_ASSET = Pattern.compile(".+-[0-9A-Za-z_-]{8,}\\.(js|mjs|css)$");
    private static final Pattern CACHE_BUST_QUERY = Pattern.compile("(?:^|&)v=[^&]+");
    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;

    public AtlasRoutes(AuthService authService) {
        this.authService = authService;
    }

    public void register(Javalin app) {
        app.get("/atlas", ctx -> ctx.redirect("/atlas/tcm-3d/"));
        app.get("/atlas/", ctx -> ctx.redirect("/atlas/tcm-3d/"));
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
        if (!authService.isAuth(ctx)) {
            if (isHtmlNavigation(relativePath)) {
                ctx.redirect("/login?next=/atlas/" + appId + "/");
                return;
            }
            ctx.status(HttpStatus.UNAUTHORIZED);
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
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        long size = Files.size(file);
        FileTime modified = Files.getLastModifiedTime(file);
        boolean gzipBody = false;
        Path bodyPath = file;
        long bodySize = size;

        if (fileName.endsWith(".glb")) {
            Path gzPath = Path.of(file.toString() + ".gz");
            if (Files.isRegularFile(gzPath) && acceptsGzip(ctx)) {
                gzipBody = true;
                bodyPath = gzPath;
                bodySize = Files.size(gzPath);
            }
        }

        String etag = etagFor(bodyPath, bodySize, modified, gzipBody);
        String ifNoneMatch = ctx.header("If-None-Match");
        if (matchesEtag(ifNoneMatch, etag)) {
            ctx.status(304);
            ctx.header("ETag", etag);
            ctx.header("Cache-Control", cacheControl(file, ctx));
            if (gzipBody) {
                ctx.header("Vary", "Accept-Encoding");
            }
            ctx.header("Last-Modified", formatHttpDate(modified.toInstant()));
            return;
        }

        ctx.minSizeForCompression(Integer.MAX_VALUE);
        ctx.header("Content-Length", Long.toString(bodySize));
        ctx.contentType(contentType(file));
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("Cache-Control", cacheControl(file, ctx));
        ctx.header("ETag", etag);
        ctx.header("Last-Modified", formatHttpDate(modified.toInstant()));
        if (gzipBody) {
            ctx.header("Content-Encoding", "gzip");
            ctx.header("Vary", "Accept-Encoding");
        }
        if (fileName.endsWith(".glb")) {
            ctx.contentType("model/gltf-binary");
        }
        ctx.result(Files.newInputStream(bodyPath));
    }

    private static boolean acceptsGzip(Context ctx) {
        String accept = ctx.header("Accept-Encoding");
        if (accept == null || accept.isBlank()) {
            return false;
        }
        for (String part : accept.split(",")) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            int semi = token.indexOf(';');
            if (semi >= 0) {
                token = token.substring(0, semi).trim();
            }
            if ("gzip".equals(token)) {
                return true;
            }
        }
        return false;
    }

    static String etagFor(Path file, long size, FileTime modified, boolean gzipVariant) {
        String name = file.getFileName().toString();
        String tag = Long.toHexString(size) + "-" + Long.toHexString(modified.toMillis());
        if (gzipVariant || name.endsWith(".gz")) {
            tag = tag + "-gzip";
        }
        return "\"" + tag + "\"";
    }

    static boolean matchesEtag(String headerValue, String etag) {
        if (headerValue == null || headerValue.isBlank()) {
            return false;
        }
        for (String token : headerValue.split(",")) {
            String trimmed = token.trim();
            if (trimmed.equals(etag) || trimmed.equals("W/" + etag)) {
                return true;
            }
        }
        return false;
    }

    static String formatHttpDate(Instant instant) {
        return HTTP_DATE.format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    /** HTML 短快取；指紋化靜態檔與帶 ?v= 資源可長快取。 */
    static String cacheControl(Path file, Context ctx) {
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) {
            return "private, max-age=0, must-revalidate";
        }
        Path parent = file.getParent();
        if (parent != null && "assets".equals(parent.getFileName().toString()) && FINGERPRINTED_ASSET.matcher(name).matches()) {
            return "private, max-age=31536000, immutable";
        }
        String query = ctx.queryString();
        if (query != null && CACHE_BUST_QUERY.matcher(query).find()) {
            return "private, max-age=31536000, immutable";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return "private, max-age=300";
        }
        if (lower.endsWith(".bin") || lower.endsWith(".wasm")) {
            return "private, max-age=31536000, immutable";
        }
        if (lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".css")) {
            return "private, max-age=86400";
        }
        if (lower.endsWith(".glb") || lower.endsWith(".glb.gz")) {
            return "private, max-age=86400";
        }
        return "private, max-age=300";
    }

    static boolean isHtmlNavigation(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return true;
        }
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) {
            return true;
        }
        int slash = lower.lastIndexOf('/');
        int dot = lower.lastIndexOf('.');
        return dot < 0 || dot < slash;
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
        return page(title + "還沒建置。在 repo 根目錄執行 ./scripts/build-atlas.sh 後重新整理。");
    }

    private static String page(String message) {
        return "<!DOCTYPE html><html lang=\"zh-Hant\"><head><meta charset=\"utf-8\"><title>經穴圖</title></head>"
                + "<body style=\"font-family:sans-serif;padding:2rem\">"
                + "<p>" + escape(message) + "</p>"
                + "<p><a href=\"/\">回辦公室</a></p></body></html>";
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