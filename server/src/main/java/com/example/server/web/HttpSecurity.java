package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.auth.CsrfTokens;
import com.example.server.auth.DevStatusTokenAuth;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Map;

/** 全站安全標頭、正式環境 HTTPS 導向、未認證請求 body 上限。 */
public final class HttpSecurity {

    /** 未登入且非 Bearer 路徑的 body 上限（塗鴉 300KB 仍遠低於此值）。 */
    public static final long UNAUTHENTICATED_MAX_BODY_BYTES = 2L * 1024L * 1024L;

    private static final String CSP = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
            + "font-src 'self' https://fonts.gstatic.com; "
            + "img-src 'self' data: blob:; "
            + "connect-src 'self'; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'";

    private final AuthService authService;
    private final Map<String, String> env;
    private final DevStatusTokenAuth devStatusTokenAuth;

    public HttpSecurity(AuthService authService, Map<String, String> env) {
        this(authService, env, new DevStatusTokenAuth());
    }

    HttpSecurity(AuthService authService, Map<String, String> env, DevStatusTokenAuth devStatusTokenAuth) {
        this.authService = authService;
        this.env = env == null ? Map.of() : env;
        this.devStatusTokenAuth = devStatusTokenAuth;
    }

    public void register(Javalin app) {
        app.before(this::maybeRedirectToHttps);
        app.before(this::maybeRejectOversizedUnauthenticatedBody);
        app.before(this::applySecurityHeaders);
    }

    /**
     * 僅在反向代理明確標示原始請求為 HTTP 時導向 HTTPS。
     * Render 健康檢查從容器內部以 HTTP 打 {@code /ping}，通常沒有
     * {@code X-Forwarded-Proto}，不可把這類請求 301 走，否則服務會被判定不健康。
     * {@code /ping} 一律不導向。
     */
    private void maybeRedirectToHttps(Context ctx) {
        if (!isProductionEnv(env)) {
            return;
        }
        if ("/ping".equals(ctx.path())) {
            return;
        }
        if ("websocket".equalsIgnoreCase(ctx.header("Upgrade"))) {
            return;
        }
        if (!isExplicitHttpForwardedProto(ctx)) {
            return;
        }
        String target = buildHttpsUrl(ctx);
        ctx.status(HttpStatus.MOVED_PERMANENTLY);
        ctx.header("Location", target);
        ctx.skipRemainingHandlers();
    }

    private static boolean isExplicitHttpForwardedProto(Context ctx) {
        String proto = ctx.header("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) {
            return false;
        }
        return proto.split(",")[0].trim().equalsIgnoreCase("http");
    }

    private static String buildHttpsUrl(Context ctx) {
        String host = ctx.host();
        String path = ctx.path();
        String query = ctx.queryString();
        StringBuilder url = new StringBuilder("https://").append(host).append(path);
        if (query != null && !query.isEmpty()) {
            url.append('?').append(query);
        }
        return url.toString();
    }

    private void maybeRejectOversizedUnauthenticatedBody(Context ctx) {
        String method = ctx.method().name();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return;
        }
        if (allowsLargeBody(ctx)) {
            return;
        }
        String lengthHeader = ctx.header("Content-Length");
        if (lengthHeader == null || lengthHeader.isBlank()) {
            return;
        }
        try {
            long length = Long.parseLong(lengthHeader.trim());
            if (length > UNAUTHENTICATED_MAX_BODY_BYTES) {
                ctx.status(413).json(Map.of(
                        "success", false,
                        "message", "請求內容過大"));
                ctx.skipRemainingHandlers();
            }
        } catch (NumberFormatException ignored) {
            // 無法解析長度時交給下游處理
        }
    }

    private boolean allowsLargeBody(Context ctx) {
        if (authService.isAuth(ctx)) {
            return true;
        }
        String path = ctx.path();
        if (authService.isHeartbeatAuthorized(ctx)) {
            return path.startsWith("/api/peer/relay")
                    || "/api/heartbeat".equals(path)
                    || "/api/peer/message".equals(path);
        }
        if ("/api/dev-machines/heartbeat".equals(path)) {
            return devStatusTokenAuth.authorize(ctx);
        }
        return false;
    }

    private void applySecurityHeaders(Context ctx) {
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("X-Frame-Options", "DENY");
        ctx.header("Referrer-Policy", "strict-origin-when-cross-origin");
        ctx.header("Content-Security-Policy", CSP);
        if (isProductionEnv(env) && authService.isHttps(ctx)) {
            ctx.header("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
    }

    /** 站長寫入：須已登入且帶有效 CSRF。 */
    public boolean requireAdminWrite(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(
                    "success", false,
                    "message", "未登入或權限不足"));
            return false;
        }
        if (!validateCsrf(ctx)) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of(
                    "success", false,
                    "message", "CSRF 驗證失敗"));
            return false;
        }
        return true;
    }

    public boolean validateCsrf(Context ctx) {
        String session = ctx.cookie("auth");
        if (session == null || session.isEmpty()) {
            return false;
        }
        String expected = authService.csrfTokenForSession(session);
        if (expected.isEmpty()) {
            return false;
        }
        String submitted = ctx.header(CsrfTokens.HEADER);
        if (submitted == null || submitted.isEmpty()) {
            submitted = ctx.formParam(CsrfTokens.FORM_FIELD);
        }
        return CsrfTokens.constantTimeEquals(expected, submitted);
    }

    private static boolean isProductionEnv(Map<String, String> env) {
        String render = env.get("RENDER");
        return render != null && !render.isEmpty();
    }
}