package com.example.server.web;

import com.example.PeerFileRules;
import com.example.server.ServerApp;
import com.example.server.auth.AuthService;
import com.example.server.store.ClientStore;
import com.example.server.store.ClientStore.PeerResult;
import com.example.server.store.FileOfferStore;
import com.example.server.store.FileOfferStore.DeleteResult;
import com.example.server.store.FileOfferStore.GetResult;
import com.example.server.store.FileOfferStore.PutResult;
import com.example.server.store.FileOfferStore.UploadResult;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 同事傳檔：整檔、分段、下載與清除。 */
public final class PeerFileRoutes {

    private final AuthService authService;
    private final ClientStore clientStore;
    private final FileOfferStore fileOfferStore;
    private final Gson gson;
    private final DashboardBroadcaster broadcaster;
    private final Supplier<Map<String, Object>> statusUpdates;

    public PeerFileRoutes(AuthService authService, ClientStore clientStore, FileOfferStore fileOfferStore,
                   Gson gson, DashboardBroadcaster broadcaster,
                   Supplier<Map<String, Object>> statusUpdates) {
        this.authService = authService;
        this.clientStore = clientStore;
        this.fileOfferStore = fileOfferStore;
        this.gson = gson;
        this.broadcaster = broadcaster;
        this.statusUpdates = statusUpdates;
    }

    public void register(Javalin app) {
        app.post("/api/peer/file", this::peerFileUpload);
        app.post("/api/peer/upload", this::peerUploadBegin);
        app.put("/api/peer/upload/{uploadId}", this::peerUploadChunk);
        app.get("/api/peer/upload/{uploadId}", this::peerUploadStatus);
        app.post("/api/peer/upload/{uploadId}/complete", this::peerUploadComplete);
        app.delete("/api/peer/upload/{uploadId}", this::peerUploadAbort);
        app.get("/api/peer/file/{fileId}", this::peerFileDownload);
        app.delete("/api/peer/file/{fileId}", this::peerFileDelete);
        app.delete("/api/peer/files", this::peerFileDeleteAll);
    }

    private void peerFileUpload(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        UploadedFile uploaded;
        PutResult stored;
        try {
            uploaded = ctx.uploadedFile("file");
            if (uploaded == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "請選擇要傳送的檔案"));
                return;
            }
            String filename = RouteSupport.firstNonEmptyForm(
                    ctx.formParam("filename"),
                    uploaded.filename()
            );
            try (InputStream in = uploaded.content()) {
                stored = fileOfferStore.put(
                        RouteSupport.stringOrNull(ctx.formParam("fromClientId")),
                        RouteSupport.stringOrNull(ctx.formParam("toClientId")),
                        filename,
                        in,
                        uploaded.size(),
                        ctx.formParam("kind")
                );
            }
        } catch (Throwable ex) {
            String message = ServerApp.uploadedFileErrorMessage(ex);
            System.err.println("[peer-file] " + message);
            HttpStatus status = ServerApp.isMemoryError(ex) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
            ctx.status(status).json(Map.of("success", false, "message", message));
            return;
        }
        if (!stored.ok) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", stored.message));
            return;
        }
        PeerResult queued = clientStore.queuePeerFile(
                stored.offer.toClientId,
                stored.offer.fromClientId,
                stored.offer
        );
        if (!queued.ok) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", queued.message));
            return;
        }
        broadcaster.broadcast(statusUpdates.get());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", queued.message);
        response.put("fileId", stored.offer.fileId);
        response.put("filename", stored.offer.filename);
        response.put("size", stored.offer.size());
        response.put("kind", stored.offer.kind);
        response.put("expiresAtMs", stored.offer.createdAtMs + FileOfferStore.TTL_MS);
        ctx.json(response);
    }

    private void peerUploadBegin(Context ctx) {
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
        Object rawSize = body.get("size");
        long size = rawSize instanceof Number ? ((Number) rawSize).longValue() : -1L;
        UploadResult result = fileOfferStore.beginUpload(
                RouteSupport.stringOrNull(body.get("fromClientId")),
                RouteSupport.stringOrNull(body.get("toClientId")),
                RouteSupport.stringOrNull(body.get("filename")),
                RouteSupport.stringOrNull(body.get("kind")),
                size
        );
        respondUpload(ctx, result);
    }

    private void peerUploadChunk(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        long offset;
        try {
            offset = Long.parseLong(RouteSupport.firstNonEmptyForm(ctx.queryParam("offset")).trim());
        } catch (NumberFormatException ex) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "缺少 offset"));
            return;
        }
        long declared = ctx.req().getContentLengthLong();
        if (declared > PeerFileRules.MAX_UPLOAD_CHUNK_BYTES) {
            ctx.status(HttpStatus.CONTENT_TOO_LARGE).json(Map.of("success", false, "message", "分段超過上限"));
            return;
        }
        UploadResult result;
        try (InputStream in = ctx.bodyInputStream()) {
            result = fileOfferStore.appendChunk(ctx.pathParam("uploadId"), RouteSupport.uploadRequester(ctx), offset, in);
        } catch (IOException ex) {
            result = fileOfferStore.uploadStatus(ctx.pathParam("uploadId"), RouteSupport.uploadRequester(ctx));
        }
        respondUpload(ctx, result);
    }

    private void peerUploadStatus(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        respondUpload(ctx, fileOfferStore.uploadStatus(ctx.pathParam("uploadId"), RouteSupport.uploadRequester(ctx)));
    }

    private void peerUploadComplete(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        UploadResult result = fileOfferStore.completeUpload(ctx.pathParam("uploadId"), RouteSupport.uploadRequester(ctx));
        if (!result.ok() || result.offer == null) {
            respondUpload(ctx, result);
            return;
        }
        FileOfferStore.Offer offer = result.offer;
        String message = result.message;
        if (!result.alreadyCompleted) {
            PeerResult queued = clientStore.queuePeerFile(offer.toClientId, offer.fromClientId, offer);
            if (!queued.ok) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", queued.message));
                return;
            }
            message = queued.message;
            broadcaster.broadcast(statusUpdates.get());
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("fileId", offer.fileId);
        response.put("filename", offer.filename);
        response.put("size", offer.size());
        response.put("kind", offer.kind);
        response.put("expiresAtMs", offer.createdAtMs + FileOfferStore.TTL_MS);
        ctx.json(response);
    }

    private void peerUploadAbort(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        respondUpload(ctx, fileOfferStore.abortUpload(ctx.pathParam("uploadId"), RouteSupport.uploadRequester(ctx)));
    }

    private static void respondUpload(Context ctx, UploadResult result) {
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
            default:
                status = HttpStatus.BAD_REQUEST;
                break;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.ok());
        response.put("message", result.message);
        if (!result.uploadId.isEmpty()) {
            response.put("uploadId", result.uploadId);
            response.put("received", result.received);
            response.put("size", result.size);
            response.put("chunkSize", PeerFileRules.UPLOAD_CHUNK_BYTES);
        }
        ctx.status(status).json(response);
    }

    private void peerFileDownload(Context ctx) {
        boolean admin = authService.isAuth(ctx);
        boolean worker = authService.isHeartbeatAuthorized(ctx);
        if (!admin && !worker) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        GetResult result = fileOfferStore.getForDownload(
                ctx.pathParam("fileId"),
                RouteSupport.firstNonEmptyForm(
                        ctx.queryParam("clientId"),
                        RouteSupport.decodeHeaderClientId(ctx.header("X-PunchClock-Client"))),
                admin
        );
        if (result.status == GetResult.Status.FORBIDDEN) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("success", false, "message", result.message));
            return;
        }
        if (result.status != GetResult.Status.OK || result.offer == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", result.message));
            return;
        }
        FileOfferStore.Offer offer = result.offer;
        InputStream stream;
        try {
            stream = offer.openStream();
        } catch (Exception ex) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "檔案不存在或已過期"));
            return;
        }
        // Javalin 預設 gzip 不會更新手動設定的 Content-Length；Render 代理會帶 gzip，長度不符就回 502。
        ctx.minSizeForCompression(Integer.MAX_VALUE);
        ctx.contentType("application/octet-stream");
        ctx.header("Content-Disposition", RouteSupport.contentDisposition(offer.filename));
        ctx.header("Content-Length", String.valueOf(offer.size()));
        ctx.header("X-PunchClock-Filename", PeerFileRules.encodeName(offer.filename));
        ctx.header("X-PunchClock-Kind", offer.kind);
        ctx.header("X-PunchClock-Mime", offer.mime == null || offer.mime.isBlank()
                ? "application/octet-stream" : offer.mime);
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.result(stream);
        if (admin || worker) {
            broadcaster.broadcast(statusUpdates.get());
        }
    }

    private void peerFileDelete(Context ctx) {
        boolean admin = authService.isAuth(ctx);
        boolean worker = authService.isHeartbeatAuthorized(ctx);
        if (!admin && !worker) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        DeleteResult result = fileOfferStore.delete(
                ctx.pathParam("fileId"),
                RouteSupport.firstNonEmptyForm(
                        ctx.queryParam("clientId"),
                        RouteSupport.decodeHeaderClientId(ctx.header("X-PunchClock-Client"))),
                admin
        );
        if (result.status == DeleteResult.Status.FORBIDDEN) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("success", false, "message", result.message));
            return;
        }
        if (result.status != DeleteResult.Status.OK) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", result.message));
            return;
        }
        broadcaster.broadcast(statusUpdates.get());
        ctx.json(Map.of("success", true, "message", result.message));
    }

    private void peerFileDeleteAll(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        int removed = fileOfferStore.deleteAll();
        broadcaster.broadcast(statusUpdates.get());
        ctx.json(Map.of("success", true, "message", "已清除 " + removed + " 筆暫存檔案", "removed", removed));
    }
}
