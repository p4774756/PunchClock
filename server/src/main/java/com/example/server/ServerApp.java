package com.example.server;

import com.example.BuildInfo;
import com.example.ClientUpdatePolicy;
import com.example.MarqueeNotice;
import com.example.PeerFileRules;
import com.example.ReleaseNotes;
import com.example.server.auth.AuthService;
import com.example.server.health.HealthHistoryStore;
import com.example.server.health.ServerHealth;
import com.example.server.store.ClientStore;
import com.example.server.store.MarqueeStore;
import com.example.server.store.PhotoStore;
import com.example.server.store.RelayStore;
import com.example.server.util.IpResolver;
import com.example.server.web.AtlasRoutes;
import com.example.server.web.ChatRoutes;
import com.example.server.web.DashboardBroadcaster;
import com.example.server.web.MarqueeRoutes;
import com.example.server.web.PhotoRoutes;
import com.example.server.web.RelayRoutes;
import com.example.server.web.RouteSupport;
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
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ServerApp {

    private static final BuildInfo BUILD_INFO = BuildInfo.load(ServerApp.class, "/server-build.properties");
    private static final String SERVER_VERSION = BUILD_INFO.version();

    private final AuthService authService = new AuthService();
    private final ClientStore clientStore = new ClientStore();
    private final RelayStore relayStore = new RelayStore();
    private final ServerHealth serverHealth = new ServerHealth();
    private final HealthHistoryStore healthHistoryStore = new HealthHistoryStore();
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final DashboardBroadcaster broadcaster = new DashboardBroadcaster(gson);
    private final RelayRoutes relayRoutes = new RelayRoutes(
            authService, clientStore, relayStore, gson, broadcaster, this::statusUpdatePayload);
    private final ChatRoutes chatRoutes = new ChatRoutes(
            authService, clientStore, gson, broadcaster, this::statusUpdatePayload);
    private final MarqueeStore marqueeStore = new MarqueeStore();
    private final MarqueeRoutes marqueeRoutes = new MarqueeRoutes(authService, marqueeStore);
    private final PhotoRoutes photoRoutes = new PhotoRoutes(authService);
    private final AtlasRoutes atlasRoutes = new AtlasRoutes();
    private final ClientRelease clientRelease;

    public ServerApp() {
        this(ClientRelease.discover());
    }

    ServerApp(ClientRelease clientRelease) {
        this.clientRelease = clientRelease == null ? ClientRelease.NONE : clientRelease;
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
        new ServerApp().start(port);
    }

    public Javalin start(int port) {
        clientStore.startOfflineMonitor(broadcaster::broadcast);
        healthHistoryStore.start(() -> serverHealth.snapshot(relayStore));

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(sf -> {
                sf.hostedPath = "/css";
                sf.directory = "/public/css";
                sf.location = Location.CLASSPATH;
            });
            config.staticFiles.add(sf -> {
                sf.hostedPath = "/js";
                sf.directory = "/public/js";
                sf.location = Location.CLASSPATH;
            });
            config.showJavalinBanner = false;
            long maxRequest = PhotoStore.MAX_INCOMING_REQUEST_BYTES;
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
        System.out.println("- Web Dashboard: http://localhost:" + boundPort + " (login required)");
        System.out.println("- Heartbeat API: POST /api/heartbeat (Bearer token required)");
        System.out.println("- Protocol: HTTP heartbeat for workers; Dashboard WS for status push only");
        System.out.println("- Peer relay: /api/peer/relay (both online, in-memory only, no disk, max "
                + PeerFileRules.MAX_SIZE_LABEL + ")");
        System.out.println("- Health history: GET /api/health/history (login, keep 3 days)");
        if (clientRelease.isAvailable()) {
            System.out.println("- Client release: v" + clientRelease.version()
                    + " " + clientRelease.sourceDigest().substring(0, 8)
                    + " (" + clientRelease.size() + " bytes)");
        } else {
            System.out.println("- Client release: none");
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
        app.get("/api/client-release/jar", this::clientReleaseJar);
        relayRoutes.register(app);
        chatRoutes.register(app);
        photoRoutes.register(app);
        marqueeRoutes.register(app);
        atlasRoutes.register(app);
        app.get("/api/status", this::status);
        app.get("/api/health/history", this::healthHistory);
        app.post("/api/clients/{clientId}/cancel-schedule", this::cancelSchedule);
        app.post("/api/clients/{clientId}/cancel-task/{taskId}", this::cancelTask);
        app.delete("/api/clients/{clientId}", this::deleteClient);
        app.get("/", this::dashboard);
        app.get("/index.html", ctx -> ctx.redirect(authService.isAuth(ctx) ? "/" : "/login"));
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
        if (authService.isAuth(ctx)) {
            ctx.redirect("/");
            return;
        }
        String ip = IpResolver.clientIp(ctx);
        AuthService.LoginLockStatus lockStatus = authService.getLoginLockStatus(ip);
        String alertMessage = "";
        if (lockStatus.isLocked()) {
            int mins = lockStatus.remainingSec / 60;
            int secs = lockStatus.remainingSec % 60;
            String timeDisplay = mins > 0 ? mins + " 分 " + secs + " 秒" : secs + " 秒";
            alertMessage = "<div class=\"error-alert\" id=\"lockAlert\">⛔ 嘗試錯誤過多！帳號已鎖定，請等待 <span id=\"countdown\">"
                    + timeDisplay + "</span>。</div>";
        } else if ("1".equals(ctx.queryParam("error"))) {
            int remainingAttempts = Math.max(0, 5 - lockStatus.attempts);
            alertMessage = "<div class=\"error-alert\">[警告] 密碼錯誤！剩餘嘗試次數：" + remainingAttempts + " 次 (滿5次鎖定15分鐘)</div>";
        }
        ctx.html(LoginPageRenderer.render(alertMessage, lockStatus, SERVER_VERSION));
    }

    private void loginPost(Context ctx) {
        String ip = IpResolver.clientIp(ctx);
        if (authService.getLoginLockStatus(ip).isLocked()) {
            ctx.redirect("/login");
            return;
        }
        String password = ctx.formParam("password");
        if (authService.checkAdminPassword(password)) {
            authService.clearLoginAttempt(ip);
            setAuthCookie(ctx, authService.authCookieValue());
            ctx.redirect("/");
            return;
        }
        authService.recordLoginFailure(ip);
        ctx.redirect("/login?error=1");
    }

    private void logout(Context ctx) {
        clearAuthCookie(ctx);
        ctx.redirect("/login");
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
        MarqueeNotice marquee = marqueeStore.current();
        Map<String, Object> marqueeBody = new LinkedHashMap<>();
        marqueeBody.put("enabled", marquee.enabled);
        marqueeBody.put("text", marquee.text);
        response.put("marquee", marqueeBody);
        if (clientRelease.isAvailable()
                && ClientUpdatePolicy.shouldOffer(appVersion, appSourceDigest, appReleaseTime,
                        clientRelease.sourceDigest(), clientRelease.releaseTime())) {
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("version", clientRelease.version());
            update.put("sourceDigest", clientRelease.sourceDigest());
            update.put("releaseTime", clientRelease.releaseTime());
            update.put("sha256", clientRelease.sha256());
            update.put("size", clientRelease.size());
            update.put("path", ClientUpdatePolicy.JAR_PATH);
            ReleaseNotes.Selection changes = ReleaseNotes.since(clientRelease.notes(), appReleaseTime);
            if (!changes.subjects.isEmpty()) {
                update.put("changes", changes.subjects);
                if (changes.hidden > 0) {
                    update.put("changesMore", changes.hidden);
                }
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

    private void clientReleaseJar(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        if (!clientRelease.isAvailable()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "沒有可下載的桌面端"));
            return;
        }
        InputStream stream;
        try {
            stream = Files.newInputStream(clientRelease.jar());
        } catch (IOException ex) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "桌面端檔案不存在"));
            return;
        }
        ctx.minSizeForCompression(Integer.MAX_VALUE);
        ctx.contentType("application/java-archive");
        ctx.header("Content-Length", String.valueOf(clientRelease.size()));
        ctx.header("ETag", "\"" + clientRelease.sha256() + "\"");
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.result(stream);
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
        payload.put("serverHealth", serverHealth.snapshot(relayStore));
        payload.put("healthHistory", healthHistoryStore.summary());
        ctx.json(payload);
    }

    private void healthHistory(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        ctx.json(healthHistoryStore.summary());
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


    private void dashboard(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.redirect("/login");
            return;
        }
        String html = readResource("/public/index.html")
                .replace("{{SERVER_VERSION}}", SERVER_VERSION)
                .replace("{{SERVER_BUILD_TIME}}", serverBuildLabel());
        ctx.html(html);
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
        payload.put("serverHealth", serverHealth.snapshot(relayStore));
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
