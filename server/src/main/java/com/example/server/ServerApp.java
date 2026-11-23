package com.example.server;

import com.example.BuildInfo;
import com.example.ClientUpdatePolicy;
import com.example.PeerFileRules;
import com.example.server.auth.AuthConfig;
import com.example.server.auth.AuthService;
import com.example.server.ClientRelease;
import com.example.server.store.ClientReleaseService;
import com.example.server.store.ClientStore;
import com.example.server.store.DevStatusService;
import com.example.server.store.GithubDevStatusClient;
import com.example.server.store.GithubMilestoneClient;
import com.example.server.store.DevStatusService;
import com.example.server.store.GraffitiStore;
import com.example.server.store.RoadmapService;
import com.example.server.store.RelayStore;
import com.example.server.util.IpResolver;
import com.example.server.web.AtlasRoutes;
import com.example.server.web.ChatRoutes;
import com.example.server.web.DashboardBroadcaster;
import com.example.server.web.GraffitiRoutes;
import com.example.server.web.OfficePublicRoutes;
import com.example.server.web.PublicDownloadRoutes;
import com.example.server.web.PublicFaviconRoutes;
import com.example.server.web.RelayRoutes;
import com.example.server.web.DevMachineRoutes;
import com.example.server.web.DevStatusRoutes;
import com.example.server.store.DevMachineStore;
import com.example.server.web.RoadmapRoutes;
import com.example.server.web.RouteSupport;
import com.example.server.web.LoginNextPath;
import com.example.server.web.LoginPageRenderer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.javalin.http.staticfiles.StaticFileConfig;

public final class ServerApp {

    private static final BuildInfo BUILD_INFO = BuildInfo.load(ServerApp.class, "/server-build.properties");
    private static final String SERVER_VERSION = BUILD_INFO.version();
    private static final String STATIC_JS_CSS_CACHE = "no-cache";
    private static final String PAGE_CACHE = "no-cache, must-revalidate";
    private static final long MAX_HTTP_REQUEST_BYTES = 64L * 1024L * 1024L;

    private final AuthService authService;
    private final ClientStore clientStore;
    private final RelayStore relayStore;
    private final Gson gson;
    private final DashboardBroadcaster broadcaster;
    private final RelayRoutes relayRoutes;
    private final ChatRoutes chatRoutes;
    private final GraffitiStore graffitiStore;
    private final GraffitiRoutes graffitiRoutes;
    private final RoadmapRoutes roadmapRoutes;
    private final DevStatusService devStatusService;
    private final DevStatusRoutes devStatusRoutes;
    private final OfficePublicRoutes officePublicRoutes;
    private final DevMachineStore devMachineStore;
    private final DevMachineRoutes devMachineRoutes;
    private final AtlasRoutes atlasRoutes;
    private final ClientReleaseService clientReleaseService;

    public ServerApp() {
        this(ClientReleaseService.live(), null, null, null);
    }

    ServerApp(ClientRelease clientRelease) {
        this(ClientReleaseService.fixed(clientRelease), null, null, null);
    }

    /** 測試可注入假的 DevStatusService，避免打真實 GitHub。 */
    public ServerApp(ClientRelease clientRelease, DevStatusService devStatusService) {
        this(ClientReleaseService.fixed(clientRelease), devStatusService, null, null);
    }

    /** 測試可注入假的 DevStatusService／RoadmapService，避免打真實 GitHub。 */
    public ServerApp(ClientRelease clientRelease, DevStatusService devStatusService, RoadmapService roadmapService) {
        this(ClientReleaseService.fixed(clientRelease), devStatusService, roadmapService, null);
    }

    /** 測試可注入 {@link AuthService}（例如隔離的 {@link AuthConfig}）。 */
    ServerApp(ClientRelease clientRelease, DevStatusService devStatusService, RoadmapService roadmapService,
              AuthService authService) {
        this(ClientReleaseService.fixed(clientRelease), devStatusService, roadmapService, authService);
    }

    ServerApp(ClientReleaseService clientReleaseService, DevStatusService devStatusService, RoadmapService roadmapService,
              AuthService authService) {
        this.clientReleaseService = clientReleaseService != null
                ? clientReleaseService
                : ClientReleaseService.fixed(ClientRelease.NONE);
        this.authService = authService != null ? authService : new AuthService();
        this.clientStore = new ClientStore();
        this.relayStore = new RelayStore();
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
        this.broadcaster = new DashboardBroadcaster(gson);
        this.relayRoutes = new RelayRoutes(
                this.authService, clientStore, relayStore, gson, broadcaster, this::statusUpdatePayload);
        this.chatRoutes = new ChatRoutes(
                this.authService, clientStore, gson, broadcaster, this::statusUpdatePayload);
        this.graffitiStore = new GraffitiStore();
        this.graffitiRoutes = new GraffitiRoutes(this.authService, graffitiStore);
        DevStatusService status = devStatusService != null
                ? devStatusService
                : new DevStatusService(new GithubDevStatusClient());
        this.devStatusService = status;
        this.devMachineStore = new DevMachineStore();
        this.devStatusRoutes = new DevStatusRoutes(status, devMachineStore);
        this.officePublicRoutes = new OfficePublicRoutes(devMachineStore, status);
        this.devMachineRoutes = new DevMachineRoutes(this.authService, devMachineStore, status, gson);
        this.atlasRoutes = new AtlasRoutes(this.authService);
        this.roadmapRoutes = new RoadmapRoutes(roadmapService != null
                ? roadmapService
                : new RoadmapService(new GithubMilestoneClient()));
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
        new ServerApp().start(port);
    }

    public Javalin start(int port) {
        clientStore.startOfflineMonitor(broadcaster::broadcast);
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(sf -> {
                sf.hostedPath = "/css";
                sf.directory = "/public/css";
                sf.location = Location.CLASSPATH;
                applyStaticCache(sf, STATIC_JS_CSS_CACHE);
            });
            config.staticFiles.add(sf -> {
                sf.hostedPath = "/js";
                sf.directory = "/public/js";
                sf.location = Location.CLASSPATH;
                applyStaticCache(sf, STATIC_JS_CSS_CACHE);
            });
            config.staticFiles.add(sf -> {
                sf.hostedPath = "/assets";
                sf.directory = "/public/assets";
                sf.location = Location.CLASSPATH;
                sf.mimeTypes.add("image/webp", "webp");
                applyStaticCache(sf, "public, max-age=86400");
            });
            config.staticFiles.add(sf -> {
                sf.hostedPath = "/models";
                sf.directory = "/public/models";
                sf.location = Location.CLASSPATH;
                sf.mimeTypes.add("model/gltf-binary", "glb");
                applyStaticCache(sf, "public, max-age=86400");
            });
            config.showJavalinBanner = false;
            long maxRequest = MAX_HTTP_REQUEST_BYTES;
            config.http.maxRequestSize = maxRequest;
            config.jetty.multipartConfig.maxFileSize(maxRequest, SizeUnit.BYTES);
            config.jetty.multipartConfig.maxTotalRequestSize(maxRequest, SizeUnit.BYTES);
            config.jetty.multipartConfig.maxInMemoryFileSize(1, SizeUnit.MB);
        });

        registerRoutes(app);
        registerWebSocket(app);

        app.start(port);
        int boundPort = app.port();
        System.out.println("Server v" + SERVER_VERSION + " listening on port " + boundPort
                + " (built " + serverBuildLabel() + ")");
        System.out.println("- Web office: http://localhost:" + boundPort + "/");
        System.out.println("- Admin console: http://localhost:" + boundPort + "/console");
        System.out.println("- Heartbeat API: POST /api/heartbeat (Bearer token required)");
        System.out.println("- Protocol: HTTP heartbeat for workers; Dashboard WS for status push only");
        System.out.println("- Peer relay: /api/peer/relay (both online, in-memory only, no disk, max "
                + PeerFileRules.MAX_SIZE_LABEL + ")");
        ClientRelease clientRelease = clientReleaseService.get(Instant.now().toEpochMilli());
        if (clientRelease.isAvailable()) {
            System.out.println("- Client release: v" + clientRelease.version()
                    + " from GitHub (" + clientRelease.size() + " bytes)");
        } else {
            System.out.println("- Client release: none (GitHub)");
        }
        System.out.println("- Admin password: " + (System.getenv("ADMIN_PASSWORD") != null ? "from ADMIN_PASSWORD env" : "default (secret)"));
        System.out.println("- JVM heap max: " + (Runtime.getRuntime().maxMemory() / (1024L * 1024L)) + " MB");
        return app;
    }

    private void registerRoutes(Javalin app) {
        app.get("/login", this::loginGet);
        app.post("/login", this::loginPost);
        app.get("/logout", this::logout);
        app.get("/ping", ctx -> ctx.json(Map.of(
                "message", "pong",
                "timestamp", Instant.now().toString(),
                "version", SERVER_VERSION,
                "buildTime", BUILD_INFO.buildTimeIso()
        )));
        app.post("/api/heartbeat", this::heartbeat);
        relayRoutes.register(app);
        chatRoutes.register(app);
        graffitiRoutes.register(app);
        officePublicRoutes.register(app);
        new PublicDownloadRoutes(clientReleaseService).register(app);
        roadmapRoutes.register(app);
        devStatusRoutes.register(app);
        devMachineRoutes.register(app);
        atlasRoutes.register(app);
        PublicFaviconRoutes.register(app);
        app.get("/api/status", this::status);
        app.post("/api/clients/{clientId}/cancel-schedule", this::cancelSchedule);
        app.post("/api/clients/{clientId}/cancel-task/{taskId}", this::cancelTask);
        app.delete("/api/clients/{clientId}", this::deleteClient);
        app.get("/", this::officeHome);
        app.get("/console", this::adminConsole);
        app.get("/console.html", ctx -> ctx.redirect("/?open=tasks"));
        app.get("/index.html", ctx -> ctx.redirect("/"));
        app.get("/office", ctx -> ctx.redirect("/"));
        app.get("/office.html", ctx -> ctx.redirect("/"));
    }

    private void registerWebSocket(Javalin app) {
        app.ws("/ws/dashboard", ws -> {
            ws.onConnect(ctx -> {
                if (!authService.isAuthFromCookieHeader(ctx.header("Cookie"))) {
                    ctx.session.close();
                    return;
                }
                broadcaster.add(ctx);
                System.out.println("[WebSocket Dashboard] Dashboard 畫面已連線");
                broadcaster.sendInitialSnapshot(ctx, this::statusUpdatePayload);
            });
            ws.onClose(broadcaster::remove);
        });
    }

    private void loginGet(Context ctx) {
        String nextSafe = LoginNextPath.sanitize(ctx.queryParam("next"));
        if (authService.isAuth(ctx)) {
            ctx.redirect(nextSafe != null ? nextSafe : "/");
            return;
        }
        if (nextSafe != null && nextSafe.startsWith("/atlas/tcm-3d")) {
            ctx.redirect("/?open=atlas-login");
            return;
        }
        String ip = IpResolver.clientIp(ctx);
        AuthService.LoginLockStatus lockStatus = authService.getLoginLockStatus(ip);
        String alertMessage = "";
        if (lockStatus.isLocked()) {
            int mins = lockStatus.remainingSec / 60;
            int secs = lockStatus.remainingSec % 60;
            String timeDisplay = mins > 0 ? mins + " 分 " + secs + " 秒" : secs + " 秒";
            alertMessage = "<div class=\"error-alert\" id=\"lockAlert\">[警告] 嘗試錯誤過多！帳號已鎖定，請等待 <span id=\"countdown\">"
                    + timeDisplay + "</span>。</div>";
        } else if ("1".equals(ctx.queryParam("error"))) {
            int remainingAttempts = Math.max(0, 5 - lockStatus.attempts);
            alertMessage = "<div class=\"error-alert\">[警告] 密碼錯誤！剩餘嘗試次數：" + remainingAttempts + " 次 (滿5次鎖定15分鐘)</div>";
        }
        ctx.html(LoginPageRenderer.render(alertMessage, lockStatus, SERVER_VERSION, nextSafe));
    }

    private void loginPost(Context ctx) {
        String ip = IpResolver.clientIp(ctx);
        String nextSafe = LoginNextPath.sanitize(ctx.formParam("next"));
        boolean json = wantsJsonLogin(ctx);
        AuthService.LoginLockStatus lockBefore = authService.getLoginLockStatus(ip);
        if (lockBefore.isLocked()) {
            if (json) {
                respondLoginJsonFailure(ctx, lockBefore);
                return;
            }
            ctx.redirect(loginUrlWithNext(null));
            return;
        }
        String password = ctx.formParam("password");
        if (authService.checkAdminPassword(password)) {
            authService.clearLoginAttempt(ip);
            setAuthCookie(ctx, authService.authCookieValue());
            if (json) {
                ctx.status(HttpStatus.OK).json(Map.of("success", true));
                return;
            }
            ctx.redirect(nextSafe != null ? nextSafe : "/");
            return;
        }
        authService.recordLoginFailure(ip);
        AuthService.LoginLockStatus lockAfter = authService.getLoginLockStatus(ip);
        if (json) {
            respondLoginJsonFailure(ctx, lockAfter);
            return;
        }
        ctx.redirect(loginUrlWithNext(nextSafe, true));
    }

    private static boolean wantsJsonLogin(Context ctx) {
        String accept = ctx.header("Accept");
        return accept != null && accept.toLowerCase().contains("application/json");
    }

    private static void respondLoginJsonFailure(Context ctx, AuthService.LoginLockStatus lockStatus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("locked", lockStatus.isLocked());
        if (lockStatus.isLocked()) {
            body.put("remainingSec", lockStatus.remainingSec);
            body.put("message", loginLockMessage(lockStatus.remainingSec));
            ctx.status(HttpStatus.FORBIDDEN).json(body);
            return;
        }
        int remainingAttempts = Math.max(0, 5 - lockStatus.attempts);
        body.put("remainingAttempts", remainingAttempts);
        body.put("message", "[警告] 密碼錯誤！剩餘嘗試次數：" + remainingAttempts + " 次 (滿5次鎖定15分鐘)");
        ctx.status(HttpStatus.UNAUTHORIZED).json(body);
    }

    private static String loginLockMessage(int remainingSec) {
        int mins = remainingSec / 60;
        int secs = remainingSec % 60;
        String timeDisplay = mins > 0 ? mins + " 分 " + secs + " 秒" : secs + " 秒";
        return "[警告] 嘗試錯誤過多！帳號已鎖定，請等待 " + timeDisplay + "。";
    }

    private static String loginUrlWithNext(String nextSafe) {
        return loginUrlWithNext(nextSafe, false);
    }

    private static String loginUrlWithNext(String nextSafe, boolean error) {
        String url = error ? "/login?error=1" : "/login";
        if (nextSafe != null && !nextSafe.isEmpty()) {
            url += (error ? "&" : "?") + "next=" + java.net.URLEncoder.encode(nextSafe, java.nio.charset.StandardCharsets.UTF_8);
        }
        return url;
    }

    private void logout(Context ctx) {
        clearAuthCookie(ctx);
        ctx.redirect("/");
    }

    private void heartbeat(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(
                    "success", false,
                    "message", "Unauthorized: invalid or missing heartbeat token"
            ));
            return;
        }

        Map<String, Object> body = gson.fromJson(ctx.body(), RouteSupport.MAP_TYPE);
        if (body == null) {
            body = new LinkedHashMap<>();
        }
        String clientId = RouteSupport.stringOrDefault(body.get("clientId"), "company-worker");
        String status = RouteSupport.stringOrDefault(body.get("status"), "ONLINE");
        String message = RouteSupport.stringOrDefault(body.get("message"), "");
        String appVersion = RouteSupport.stringOrDefault(body.get("appVersion"), "");
        String appReleaseTime = RouteSupport.stringOrDefault(body.get("appReleaseTime"), "");
        String appSourceDigest = RouteSupport.stringOrDefault(body.get("appSourceDigest"), "");
        List<Map<String, Object>> tasks = RouteSupport.tasksFromBody(body.get("tasks"));
        ClientStore.normalizeReportedTaskNames(tasks);

        Map<String, Object> heartbeatBody = body;
        String clientIp = IpResolver.clientIp(ctx);
        boolean stale = clientStore.withMetadataLock(() -> recordHeartbeat(
                heartbeatBody, tasks, clientId, status, message, appVersion, appReleaseTime, appSourceDigest, clientIp));

        broadcaster.broadcast(statusUpdatePayload());

        writeHeartbeatResponse(ctx, clientId, !stale, appVersion, appReleaseTime, appSourceDigest);
    }

    /** 須在 {@link ClientStore#withMetadataLock} 內呼叫；回傳 true 表示是亂序晚到、只更新了 lastSeen。 */
    private boolean recordHeartbeat(Map<String, Object> body, List<Map<String, Object>> tasks, String clientId,
                                    String status, String message, String appVersion, String appReleaseTime,
                                    String appSourceDigest, String clientIp) {
        Map<String, Object> existing = clientStore.getOrCreateClient(clientId);
        long incomingSeq = RouteSupport.numberFrom(body.get("heartbeatSeq"));
        long storedSeq = RouteSupport.numberFrom(existing.get("heartbeatSeq"));

        // 僅擋「同階段亂序晚到」；客戶端重啟後 seq 會重數，不可當成過期而拒收
        if (isOutOfOrderStaleHeartbeat(incomingSeq, storedSeq)) {
            Map<String, Object> clientInfo = new LinkedHashMap<>(existing);
            clientInfo.put("lastSeen", Instant.now().toString());
            // 有心跳就視為在線，避免停在 OFFLINE 卻任務永遠不同步
            clientInfo.put("status", "OFFLINE".equals(status) ? "ONLINE" : status);
            clientStore.setClient(clientId, clientInfo);
            return true;
        }

        List<Map<String, Object>> effectiveTasks = tasks.isEmpty() ? clientStore.getTasks(existing) : tasks;

        clientStore.logTaskTransitions(existing, effectiveTasks);

        Map<String, Object> clientInfo = new LinkedHashMap<>(existing);
        clientInfo.put("clientId", clientId);
        clientInfo.put("status", "OFFLINE".equals(status) ? "ONLINE" : status);
        clientInfo.put("tasks", effectiveTasks);
        clientInfo.put("message", message.isEmpty() ? RouteSupport.stringOrDefault(existing.get("message"), "") : message);
        clientInfo.put("appVersion", appVersion.isEmpty() ? RouteSupport.stringOrDefault(existing.get("appVersion"), "") : appVersion);
        if (!appVersion.isEmpty()) {
            // 舊版 app 不送 appReleaseTime，跟著版號一起更新，避免殘留上一個版本的時間
            clientInfo.put("appReleaseTime", appReleaseTime);
            clientInfo.put("appSourceDigest", appSourceDigest);
        }
        clientInfo.put("lastSeen", Instant.now().toString());
        clientInfo.put("transport", "http");
        clientInfo.put("clientIp", clientIp);
        if (incomingSeq > 0) {
            clientInfo.put("heartbeatSeq", incomingSeq);
        }
        if (body.containsKey("avatar")) {
            String avatar = ClientStore.sanitizeAvatar(body.get("avatar"));
            if (avatar.isEmpty()) {
                clientInfo.remove("avatar");
            } else {
                clientInfo.put("avatar", avatar);
            }
        }
        ClientStore.applyCapabilities(clientInfo, body);
        clientStore.setClient(clientId, clientInfo);
        return false;
    }

    private void writeHeartbeatResponse(Context ctx, String clientId, boolean logDrained,
                                        String appVersion, String appReleaseTime, String appSourceDigest) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Heartbeat acknowledged");
        response.put("action", "NONE");
        response.put("actions", List.of());
        response.put("peers", clientStore.peerSnapshot(clientId));
        response.put("ackTimestamp", Instant.now().toString());
        ClientRelease clientRelease = clientReleaseService.get(Instant.now().toEpochMilli());
        if (clientRelease.isAvailable()
                && ClientUpdatePolicy.shouldOffer(appVersion, clientRelease.version())) {
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("version", clientRelease.version());
            update.put("releaseTime", clientRelease.releaseTime());
            update.put("sha256", clientRelease.sha256());
            update.put("size", clientRelease.size());
            update.put("path", clientRelease.downloadUrl());
            if (!clientRelease.changeLines().isEmpty()) {
                update.put("changes", clientRelease.changeLines());
            }
            response.put("update", update);
        }
        // 取走即從佇列移除，之後若回應沒送到桌面端（逾時、斷線）這批指令就遺失；
        // 所以放在組好其他欄位之後，只剩序列化與寫出這一段無法補救。
        List<String> drainedActions = clientStore.drainPendingActions(clientId);
        response.put("action", drainedActions.isEmpty() ? "NONE" : drainedActions.get(0));
        response.put("actions", drainedActions);
        if (logDrained && !drainedActions.isEmpty()) {
            clientStore.appendClientEvent(clientId, "桌面端心跳已取走指令：" + String.join("、", drainedActions));
        }
        ctx.json(response);
    }








    private void status(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverTimestamp", Instant.now().toString());
        payload.put("serverVersion", SERVER_VERSION);
        payload.put("serverBuildTime", BUILD_INFO.buildTimeIso());
        payload.put("totalClients", clientStore.clients().size());
        payload.put("clients", clientStore.publicClientsSnapshot());
        ctx.json(payload);
    }

    private void cancelSchedule(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String clientId = ctx.pathParam("clientId");
        clientStore.queueClientAction(clientId, "CANCEL_SCHEDULE");
        broadcaster.broadcast(statusUpdatePayload());
        ctx.json(Map.of("success", true, "message", "已成功將【取消所有排程】指令派送至 " + clientId + " 佇列"));
    }

    private void cancelTask(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String clientId = ctx.pathParam("clientId");
        String taskId = ctx.pathParam("taskId");
        clientStore.queueClientAction(clientId, "CANCEL_TASK:" + taskId);
        broadcaster.broadcast(statusUpdatePayload());
        ctx.json(Map.of("success", true, "message", "已成功將【取消任務 " + taskId + "】指令派送至 " + clientId + " 佇列"));
    }

    private void deleteClient(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String clientId = ctx.pathParam("clientId");
        clientStore.deleteClient(clientId);
        broadcaster.broadcast(statusUpdatePayload());
        ctx.json(Map.of("success", true, "message", "已移除設備紀錄：" + clientId));
    }


    private void officeHome(Context ctx) {
        boolean authenticated = authService.isAuth(ctx);
        ClientRelease release = clientReleaseService.get(Instant.now().toEpochMilli());
        String html = applyAuthPageTokens(applyServerBuildTokens(
                PublicDownloadRoutes.applyHomeDownloadTokens(readResource("/public/index.html"), release)),
                authenticated);
        ctx.header("Cache-Control", PAGE_CACHE);
        ctx.html(html);
    }

    private void adminConsole(Context ctx) {
        ctx.redirect("/?open=tasks");
    }

    private static String applyServerBuildTokens(String html) {
        return html
                .replace("{{SERVER_VERSION}}", SERVER_VERSION)
                .replace("{{SERVER_BUILD_TIME}}", serverBuildLabel());
    }

    private static String applyAuthPageTokens(String html, boolean authenticated) {
        return html
                .replace("{{AUTH_BODY_CLASS}}", authenticated ? "is-authenticated" : "is-guest")
                .replace("{{AUTH_JS_BOOL}}", authenticated ? "true" : "false");
    }

    private static void applyStaticCache(StaticFileConfig sf, String cacheControl) {
        if (sf.headers == null) {
            sf.headers = new HashMap<>();
        }
        sf.headers.put("Cache-Control", cacheControl);
    }

    /** Render 容器是 UTC，後台固定以台北時間顯示。 */
    private static String serverBuildLabel() {
        String label = BUILD_INFO.buildTimeLabel(BuildInfo.TAIPEI);
        return label.isEmpty() ? "開發版" : label;
    }

    private Map<String, Object> statusUpdatePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "STATUS_UPDATE");
        payload.put("clients", clientStore.publicClientsSnapshot());
        return payload;
    }


    private void setAuthCookie(Context ctx, String value) {
        ctx.cookie("auth", value, 60 * 60 * 24);
    }

    private void clearAuthCookie(Context ctx) {
        ctx.removeCookie("auth");
    }

    private static String readResource(String path) {
        try (InputStream in = ServerApp.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }


    /**
     * 判斷是否為同階段亂序晚到的舊心跳。
     * 客戶端重啟後 heartbeatSeq 會從 1 重數，此時應接受並覆蓋，否則會一直停在過期狀態：
     * 後台顯示離線／任務不同步，但桌面端仍以為連線正常。
     */
    static boolean isOutOfOrderStaleHeartbeat(long incomingSeq, long storedSeq) {
        if (incomingSeq <= 0 || storedSeq <= 0 || incomingSeq >= storedSeq) {
            return false;
        }
        // 重啟特徵：seq 回到開頭，或大幅倒退（遠超過短時間內的亂序差距）
        if (incomingSeq <= 10 || (storedSeq - incomingSeq) > 20) {
            return false;
        }
        return true;
    }
}
