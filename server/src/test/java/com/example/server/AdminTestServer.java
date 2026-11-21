package com.example.server;

import com.example.server.auth.AuthConfig;
import com.example.server.auth.AuthService;
import com.example.server.store.DevStatusService;
import com.example.server.store.RoadmapService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 整合測試用：隨機管理密碼、可注入 {@link AuthService} 的 {@link ServerApp}，
 * 並透過真實 {@code POST /login} 取得 Cookie（不輸出密碼或 Cookie 至 log／assert）。
 */
public final class AdminTestServer {

    private static final String DEFAULT_HEARTBEAT_SECRET = "punchclock-dev-secret";

    private final String adminPassword;
    private final AuthService authService;
    private final Javalin app;
    private final String baseUrl;

    private AdminTestServer(String adminPassword, AuthService authService, Javalin app, String baseUrl) {
        this.adminPassword = adminPassword;
        this.authService = authService;
        this.app = app;
        this.baseUrl = baseUrl;
    }

    public Javalin app() {
        return app;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public AuthService authService() {
        return authService;
    }

    /** {@code Origin} 標頭值，與 {@link #baseUrl()} 同源。 */
    public String originHeaderValue() {
        return baseUrl;
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    /**
     * 以 {@code POST /login} 登入，回傳 {@code Cookie} 標頭用片段（例如 {@code auth=…}）。
     */
    public String loginCookie(HttpClient http) throws Exception {
        return loginCookie(baseUrl, adminPassword, http);
    }

    public static String loginCookie(String baseUrl, String adminPassword, HttpClient http) throws Exception {
        String body = "password=" + URLEncoder.encode(adminPassword, StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(java.time.Duration.ofSeconds(10))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        for (String header : response.headers().allValues("Set-Cookie")) {
            if (header.startsWith("auth=")) {
                return header.split(";", 2)[0];
            }
        }
        throw new IllegalStateException("login failed with status " + response.statusCode());
    }

    /**
     * 在僅註冊部分路由的測試 Javalin 上掛載與 production 相同語意的 {@code POST /login}。
     */
    public static void registerLogin(Javalin app, AuthService authService) {
        app.post("/login", ctx -> loginPost(ctx, authService));
    }

    private static void loginPost(Context ctx, AuthService authService) {
        String password = ctx.formParam("password");
        if (authService.checkAdminPassword(password)) {
            ctx.cookie("auth", authService.authCookieValue(), 60 * 60 * 24);
            ctx.redirect("/");
            return;
        }
        ctx.status(401);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ClientRelease clientRelease = ClientRelease.NONE;
        private DevStatusService devStatusService;
        private RoadmapService roadmapService;
        private String heartbeatSecret = DEFAULT_HEARTBEAT_SECRET;
        private String fixedAdminPassword;

        public Builder clientRelease(ClientRelease clientRelease) {
            this.clientRelease = clientRelease;
            return this;
        }

        public Builder devStatusService(DevStatusService devStatusService) {
            this.devStatusService = devStatusService;
            return this;
        }

        public Builder roadmapService(RoadmapService roadmapService) {
            this.roadmapService = roadmapService;
            return this;
        }

        public Builder heartbeatSecret(String heartbeatSecret) {
            this.heartbeatSecret = heartbeatSecret;
            return this;
        }

        /** 若省略則每次啟動產生新的隨機密碼。 */
        public Builder adminPassword(String adminPassword) {
            this.fixedAdminPassword = adminPassword;
            return this;
        }

        public AdminTestServer start() {
            String password = fixedAdminPassword != null ? fixedAdminPassword : randomAdminPassword();
            Map<String, String> env = new HashMap<>();
            env.put("ADMIN_PASSWORD", password);
            env.put("HEARTBEAT_SECRET", heartbeatSecret);
            AuthService auth = new AuthService(AuthConfig.from(env));
            ServerApp serverApp = new ServerApp(clientRelease, devStatusService, roadmapService, auth);
            Javalin javalin = serverApp.start(0);
            String base = "http://127.0.0.1:" + javalin.port();
            return new AdminTestServer(password, auth, javalin, base);
        }
    }

    private static String randomAdminPassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 單一路由測試：隨機密碼的 {@link AuthService} 與對應密碼（僅存於測試記憶體）。 */
    public static IsolatedAuth isolatedAuth() {
        String password = randomAdminPassword();
        AuthService auth = new AuthService(AuthConfig.from(Map.of(
                "ADMIN_PASSWORD", password,
                "HEARTBEAT_SECRET", DEFAULT_HEARTBEAT_SECRET)));
        return new IsolatedAuth(password, auth);
    }

    public static final class IsolatedAuth {
        private final String adminPassword;
        private final AuthService authService;

        IsolatedAuth(String adminPassword, AuthService authService) {
            this.adminPassword = adminPassword;
            this.authService = authService;
        }

        public String adminPassword() {
            return adminPassword;
        }

        public AuthService authService() {
            return authService;
        }
    }
}