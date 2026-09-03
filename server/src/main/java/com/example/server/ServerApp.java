package com.example.server;

import com.example.DailyProverb;
import com.example.server.auth.AuthService;
import com.example.server.store.ClientStore;
import com.example.server.store.ClientStore.PeerResult;
import com.example.server.util.HtmlEscape;
import com.example.server.util.IpResolver;
import com.example.server.web.DashboardBroadcaster;
import com.example.server.web.LoginPageRenderer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class ServerApp {

    private static final String SERVER_VERSION = "1.7.0";
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final AuthService authService = new AuthService();
    private final ClientStore clientStore = new ClientStore();
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final DashboardBroadcaster broadcaster = new DashboardBroadcaster(gson);

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
        new ServerApp().start(port);
    }

    public void start(int port) {
        clientStore.startOfflineMonitor(broadcaster::broadcast);

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
        });

        registerRoutes(app);
        registerWebSocket(app);

        app.start(port);
        System.out.println("Server v" + SERVER_VERSION + " listening on port " + port);
        System.out.println("- Web Dashboard: http://localhost:" + port + " (login required)");
        System.out.println("- Heartbeat API: POST /api/heartbeat (Bearer token required)");
        System.out.println("- Protocol: HTTP heartbeat for workers; Dashboard WS for status push only");
        System.out.println("- Admin password: " + (System.getenv("ADMIN_PASSWORD") != null ? "from ADMIN_PASSWORD env" : "default (secret)"));
    }

    private void registerRoutes(Javalin app) {
        app.get("/login", this::loginGet);
        app.post("/login", this::loginPost);
        app.get("/logout", this::logout);
        app.get("/api/daily-proverb", ctx -> ctx.json(toProverbMap(DailyProverb.forToday())));
        app.get("/ping", ctx -> ctx.json(Map.of(
                "message", "pong",
                "timestamp", Instant.now().toString()
        )));
        app.post("/api/heartbeat", this::heartbeat);
        app.post("/api/peer/message", this::peerMessage);
        app.post("/api/peer/poke", this::peerPoke);
        app.get("/api/status", this::status);
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

        Map<String, Object> body = gson.fromJson(ctx.body(), MAP_TYPE);
        if (body == null) {
            body = new LinkedHashMap<>();
        }
        String clientId = stringOrDefault(body.get("clientId"), "company-worker");
        String status = stringOrDefault(body.get("status"), "ONLINE");
        String message = stringOrDefault(body.get("message"), "");
        String appVersion = stringOrDefault(body.get("appVersion"), "");
        List<Map<String, Object>> tasks = tasksFromBody(body.get("tasks"));

        Map<String, Object> existing = clientStore.getOrCreateClient(clientId);
        long incomingSeq = numberFrom(body.get("heartbeatSeq"));
        long storedSeq = numberFrom(existing.get("heartbeatSeq"));
        List<String> drainedActions = clientStore.drainPendingActions(existing);

        // 僅擋「同階段亂序晚到」；客戶端重啟後 seq 會重數，不可當成過期而拒收
        if (isOutOfOrderStaleHeartbeat(incomingSeq, storedSeq)) {
            Map<String, Object> clientInfo = new LinkedHashMap<>(existing);
            clientInfo.put("lastSeen", Instant.now().toString());
            // 有心跳就視為在線，避免停在 OFFLINE 卻任務永遠不同步
            clientInfo.put("status", "OFFLINE".equals(status) ? "ONLINE" : status);
            clientStore.setClient(clientId, clientInfo);
            broadcaster.broadcast(statusUpdatePayload());
            writeHeartbeatResponse(ctx, drainedActions, clientId);
            return;
        }

        List<Map<String, Object>> effectiveTasks = tasks.isEmpty() ? clientStore.getTasks(existing) : tasks;

        if (!drainedActions.isEmpty()) {
            clientStore.appendClientEvent(existing, "桌面端心跳已取走指令：" + String.join("、", drainedActions));
        }
        clientStore.logTaskTransitions(existing, effectiveTasks);

        Map<String, Object> clientInfo = new LinkedHashMap<>(existing);
        clientInfo.put("clientId", clientId);
        clientInfo.put("status", "OFFLINE".equals(status) ? "ONLINE" : status);
        clientInfo.put("tasks", effectiveTasks);
        clientInfo.put("message", message.isEmpty() ? stringOrDefault(existing.get("message"), "") : message);
        clientInfo.put("appVersion", appVersion.isEmpty() ? stringOrDefault(existing.get("appVersion"), "") : appVersion);
        clientInfo.put("lastSeen", Instant.now().toString());
        clientInfo.put("transport", "http");
        clientInfo.put("clientIp", IpResolver.clientIp(ctx));
        if (incomingSeq > 0) {
            clientInfo.put("heartbeatSeq", incomingSeq);
        }
        clientInfo.remove("pendingActions");
        clientInfo.remove("pendingAction");
        clientInfo.remove("pendingActionTime");

        clientStore.setClient(clientId, clientInfo);

        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            System.out.println("[Checkin Report] 設備 " + clientId + " 上報打卡結果 (" + status + "): " + message);
        }

        broadcaster.broadcast(statusUpdatePayload());

        writeHeartbeatResponse(ctx, drainedActions, clientId);
    }

    private void writeHeartbeatResponse(Context ctx, List<String> drainedActions, String clientId) {
        String actionToSend = drainedActions.isEmpty() ? "NONE" : drainedActions.get(0);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Heartbeat acknowledged");
        response.put("action", actionToSend);
        response.put("actions", drainedActions);
        response.put("peers", clientStore.peerSnapshot(clientId));
        response.put("ackTimestamp", Instant.now().toString());
        ctx.json(response);
    }

    private void peerMessage(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(unauthorized());
            return;
        }
        Map<String, Object> body = gson.fromJson(ctx.body(), MAP_TYPE);
        if (body == null) {
            body = Map.of();
        }
        PeerResult result = clientStore.queuePeerMessage(
                stringOrNull(body.get("toClientId")),
                stringOrNull(body.get("fromClientId")),
                stringOrNull(body.get("text"))
        );
        if (!result.ok) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", result.message));
            return;
        }
        broadcaster.broadcast(statusUpdatePayload());
        ctx.json(Map.of("success", true, "message", result.message));
    }

    private void peerPoke(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(unauthorized());
            return;
        }
        Map<String, Object> body = gson.fromJson(ctx.body(), MAP_TYPE);
        if (body == null) {
            body = Map.of();
        }
        PeerResult result = clientStore.queuePeerPoke(
                stringOrNull(body.get("toClientId")),
                stringOrNull(body.get("fromClientId"))
        );
        if (!result.ok) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", result.message));
            return;
        }
        broadcaster.broadcast(statusUpdatePayload());
        ctx.json(Map.of("success", true, "message", result.message));
    }

    private void status(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverTimestamp", Instant.now().toString());
        payload.put("serverVersion", SERVER_VERSION);
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

    private void dashboard(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.redirect("/login");
            return;
        }
        DailyProverb.Entry proverb = DailyProverb.forToday();
        String html = readResource("/public/index.html")
                .replace("{{SERVER_VERSION}}", SERVER_VERSION)
                .replace("{{DAILY_PROVERB_EN}}", HtmlEscape.escape(proverb.en))
                .replace("{{DAILY_PROVERB_ZH}}", HtmlEscape.escape(proverb.zh))
                .replace("{{DAILY_PROVERB_CONTEXT}}", HtmlEscape.escape(proverb.context))
                .replace("{{DAILY_PROVERB_DATE}}", HtmlEscape.escape(proverb.date));
        ctx.html(html);
    }

    private Map<String, Object> statusUpdatePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "STATUS_UPDATE");
        payload.put("clients", clientStore.publicClientsSnapshot());
        return payload;
    }

    private static Map<String, Object> toProverbMap(DailyProverb.Entry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", entry.date);
        map.put("en", entry.en);
        map.put("zh", entry.zh);
        map.put("context", entry.context);
        map.put("index", entry.index);
        return map;
    }

    private static Map<String, Object> unauthorized() {
        return Map.of("success", false, "message", "Unauthorized: invalid or missing heartbeat token");
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

    private static List<Map<String, Object>> tasksFromBody(Object raw) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item instanceof Map) {
                    tasks.add((Map<String, Object>) item);
                }
            }
        }
        return tasks;
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? defaultValue : text;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long numberFrom(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
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
