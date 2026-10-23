package com.example.server.web;

import com.example.PeerFileRules;
import com.example.server.auth.AuthService;
import com.example.server.store.ClientStore;
import com.example.server.store.ClientStore.PeerResult;
import com.example.server.store.RelayStore;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 同事直傳。雙方都要在線，檔案只在記憶體轉手。 */
public final class RelayRoutes {

    /** 只對最近 1 分鐘內有心跳的收件人發起（心跳每 15 秒一次）。 */
    static final long RELAY_RECIPIENT_FRESH_MS = 60_000L;

    private final AuthService authService;
    private final ClientStore clientStore;
    private final RelayStore relayStore;
    private final Gson gson;
    private final DashboardBroadcaster broadcaster;
    private final Supplier<Map<String, Object>> statusUpdates;

    public RelayRoutes(AuthService authService, ClientStore clientStore, RelayStore relayStore,
                Gson gson, DashboardBroadcaster broadcaster,
                Supplier<Map<String, Object>> statusUpdates) {
        this.authService = authService;
        this.clientStore = clientStore;
        this.relayStore = relayStore;
        this.gson = gson;
        this.broadcaster = broadcaster;
        this.statusUpdates = statusUpdates;
    }

    public void register(Javalin app) {
        app.post("/api/peer/relay", this::relayBegin);
        app.get("/api/peer/relay/{relayId}", this::relayStatus);
        app.delete("/api/peer/relay/{relayId}", this::relayCancel);
        app.post("/api/peer/relay/{relayId}/accept", this::relayAccept);
        app.put("/api/peer/relay/{relayId}/chunk", this::relayPush);
        app.get("/api/peer/relay/{relayId}/chunk", this::relayTake);
        app.post("/api/peer/relay/{relayId}/complete", this::relayComplete);
    }

    private void relayBegin(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        Map<String, Object> body;
        try {
            body = gson.fromJson(ctx.body(), RouteSupport.MAP_TYPE);
        } catch (Exception ex) {
            body = null;
        }
        if (body == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "請求格式錯誤"));
            return;
        }
        String to = PeerFileRules.normalizeClientId(RouteSupport.stringOrNull(body.get("toClientId")));
        if (!to.isEmpty() && !clientStore.isRecentlySeen(to, RELAY_RECIPIENT_FRESH_MS)) {
            ctx.status(HttpStatus.CONFLICT).json(Map.of("success", false, "code", "RECIPIENT_OFFLINE",
                    "message", "對方目前不在線，無法直傳"));
            return;
        }
        if (!to.isEmpty() && !clientStore.hasCapability(to, PeerFileRules.CAPABILITY_RELAY)) {
            ctx.status(HttpStatus.CONFLICT).json(Map.of("success", false, "code", "RECIPIENT_UNSUPPORTED",
                    "message", "對方的桌面端版本不支援直傳"));
            return;
        }
        Object rawSize = body.get("size");
        long size = rawSize instanceof Number ? ((Number) rawSize).longValue() : -1L;
        RelayStore.Result result = relayStore.begin(
                RouteSupport.stringOrNull(body.get("fromClientId")),
                to,
                RouteSupport.stringOrNull(body.get("filename")),
                RouteSupport.stringOrNull(body.get("kind")),
                size);
        if (result.ok()) {
            PeerResult queued = clientStore.queuePeerRelay(result.toClientId, result.fromClientId,
                    result.relayId, result.filename, result.size, result.kind);
            if (!queued.ok) {
                relayStore.cancel(result.relayId, result.fromClientId);
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", queued.message));
                return;
            }
            broadcaster.broadcast(statusUpdates.get());
            respondRelay(ctx, result, result.fromClientId, queued.message);
            return;
        }
        respondRelay(ctx, result, RouteSupport.stringOrNull(body.get("fromClientId")), null);
    }

    private void relayStatus(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        String requester = RouteSupport.uploadRequester(ctx);
        long since = RouteSupport.longQueryParam(ctx, "since", 0L);
        long waitMs = relayWaitMs(ctx);
        respondRelay(ctx, relayStore.await(ctx.pathParam("relayId"), requester, since, waitMs), requester, null);
    }

    private void relayAccept(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        String requester = RouteSupport.uploadRequester(ctx);
        RelayStore.Result result = relayStore.accept(ctx.pathParam("relayId"), requester);
        if (result.ok()) {
            broadcaster.broadcast(statusUpdates.get());
        }
        respondRelay(ctx, result, requester, null);
    }

    private void relayPush(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        long offset = RouteSupport.longQueryParam(ctx, "offset", -1L);
        if (offset < 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "缺少 offset"));
            return;
        }
        if (ctx.req().getContentLengthLong() > RelayStore.MAX_CHUNK_BYTES) {
            ctx.status(HttpStatus.CONTENT_TOO_LARGE).json(Map.of("success", false, "message", "分段超過上限"));
            return;
        }
        String requester = RouteSupport.uploadRequester(ctx);
        RelayStore.Result result;
        try (InputStream in = ctx.bodyInputStream()) {
            result = relayStore.push(ctx.pathParam("relayId"), requester, offset, in);
        } catch (IOException ex) {
            result = relayStore.await(ctx.pathParam("relayId"), requester, Long.MAX_VALUE, 0L);
        }
        respondRelay(ctx, result, requester, null);
    }

    private void relayTake(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        long offset = RouteSupport.longQueryParam(ctx, "offset", -1L);
        if (offset < 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "缺少 offset"));
            return;
        }
        String requester = RouteSupport.uploadRequester(ctx);
        RelayStore.ChunkResult chunk = relayStore.take(ctx.pathParam("relayId"), requester, offset, relayWaitMs(ctx));
        if (chunk.status == RelayStore.ChunkResult.Status.NO_DATA) {
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }
        if (chunk.status != RelayStore.ChunkResult.Status.DATA) {
            respondRelay(ctx, chunk.result, requester, null);
            return;
        }
        // 同 peerFileDownload：手動 Content-Length 時不可讓 Javalin 再 gzip。
        ctx.minSizeForCompression(Integer.MAX_VALUE);
        ctx.contentType("application/octet-stream");
        ctx.header("Content-Length", String.valueOf(chunk.data.length));
        ctx.header("X-Relay-Offset", String.valueOf(chunk.offset));
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.result(chunk.data);
    }

    private void relayComplete(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        String requester = RouteSupport.uploadRequester(ctx);
        RelayStore.Result result = relayStore.complete(ctx.pathParam("relayId"), requester);
        if (result.ok()) {
            broadcaster.broadcast(statusUpdates.get());
        }
        respondRelay(ctx, result, requester, null);
    }

    private void relayCancel(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        String requester = RouteSupport.uploadRequester(ctx);
        RelayStore.Result result = relayStore.cancel(ctx.pathParam("relayId"), requester);
        if (result.ok()) {
            broadcaster.broadcast(statusUpdates.get());
        }
        respondRelay(ctx, result, requester, null);
    }

    private static void respondRelay(Context ctx, RelayStore.Result result, String requester, String messageOverride) {
        HttpStatus status;
        switch (result.status) {
            case OK:
                status = HttpStatus.OK;
                break;
            case CONFLICT:
                status = HttpStatus.CONFLICT;
                break;
            case NOT_FOUND:
                status = HttpStatus.NOT_FOUND;
                break;
            case FORBIDDEN:
                status = HttpStatus.FORBIDDEN;
                break;
            case GONE:
                status = HttpStatus.GONE;
                break;
            case BUSY:
                status = HttpStatus.SERVICE_UNAVAILABLE;
                break;
            default:
                status = HttpStatus.BAD_REQUEST;
                break;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.ok());
        response.put("message", messageOverride != null ? messageOverride : result.message);
        if (result.status == RelayStore.Result.Status.BUSY) {
            response.put("code", "BUSY");
        }
        response.putAll(result.view(requester));
        ctx.status(status).json(response);
    }

    private static long relayWaitMs(Context ctx) {
        return Math.max(0L, Math.min(PeerFileRules.RELAY_LONG_POLL_MS, RouteSupport.longQueryParam(ctx, "wait", 0L)));
    }
}
