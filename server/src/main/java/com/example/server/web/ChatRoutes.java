package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.ChatStore;
import com.example.server.store.ClientStore;
import com.example.server.store.ClientStore.PeerResult;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 同事訊息、戳一下，以及控制台聊天。 */
public final class ChatRoutes {

    private final AuthService authService;
    private final ClientStore clientStore;
    private final ChatStore chatStore;
    private final Gson gson;
    private final DashboardBroadcaster broadcaster;
    private final Supplier<Map<String, Object>> statusUpdates;

    public ChatRoutes(AuthService authService, ClientStore clientStore, ChatStore chatStore,
               Gson gson, DashboardBroadcaster broadcaster,
               Supplier<Map<String, Object>> statusUpdates) {
        this.authService = authService;
        this.clientStore = clientStore;
        this.chatStore = chatStore;
        this.gson = gson;
        this.broadcaster = broadcaster;
        this.statusUpdates = statusUpdates;
    }

    public void register(Javalin app) {
        app.post("/api/peer/message", this::peerMessage);
        app.post("/api/peer/poke", this::peerPoke);
        app.post("/api/clients/{clientId}/message", this::adminMessage);
        app.get("/api/chat", this::chatSnapshot);
        app.post("/api/chat/send", this::chatSend);
    }

    private void peerMessage(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        Map<String, Object> body = gson.fromJson(ctx.body(), RouteSupport.MAP_TYPE);
        if (body == null) {
            body = Map.of();
        }
        PeerResult result = clientStore.queuePeerMessage(
                RouteSupport.stringOrNull(body.get("toClientId")),
                RouteSupport.stringOrNull(body.get("fromClientId")),
                RouteSupport.stringOrNull(body.get("text")),
                RouteSupport.stringOrNull(body.get("avatar")),
                RouteSupport.stringOrNull(body.get("image"))
        );
        if (!result.ok) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", result.message));
            return;
        }
        broadcaster.broadcast(statusUpdates.get());
        ctx.json(Map.of("success", true, "message", result.message));
    }

    private void peerPoke(Context ctx) {
        if (!authService.isHeartbeatAuthorized(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        Map<String, Object> body = gson.fromJson(ctx.body(), RouteSupport.MAP_TYPE);
        if (body == null) {
            body = Map.of();
        }
        PeerResult result = clientStore.queuePeerPoke(
                RouteSupport.stringOrNull(body.get("toClientId")),
                RouteSupport.stringOrNull(body.get("fromClientId")),
                RouteSupport.stringOrNull(body.get("avatar"))
        );
        if (!result.ok) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", result.message));
            return;
        }
        broadcaster.broadcast(statusUpdates.get());
        ctx.json(Map.of("success", true, "message", result.message));
    }

    private void adminMessage(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String clientId = ctx.pathParam("clientId");
        Map<String, Object> body = gson.fromJson(ctx.body(), RouteSupport.MAP_TYPE);
        if (body == null) {
            body = Map.of();
        }
        PeerResult result = clientStore.queueAdminMessage(clientId, RouteSupport.stringOrNull(body.get("text")));
        if (!result.ok) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", result.message));
            return;
        }
        broadcaster.broadcast(statusUpdates.get());
        ctx.json(Map.of("success", true, "message", result.message));
    }

    private void chatSnapshot(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        ctx.json(Map.of("success", true, "rooms", chatStore.snapshot()));
    }

    private void chatSend(Context ctx) {
        boolean admin = authService.isAuth(ctx);
        boolean worker = authService.isHeartbeatAuthorized(ctx);
        if (!admin && !worker) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(RouteSupport.unauthorized());
            return;
        }
        Map<String, Object> body = gson.fromJson(ctx.body(), RouteSupport.MAP_TYPE);
        if (body == null) {
            body = Map.of();
        }
        ChatStore.Outcome outcome = admin
                ? chatStore.sendFromAdmin(
                        RouteSupport.stringOrNull(body.get("roomId")),
                        RouteSupport.stringOrNull(body.get("nick")),
                        RouteSupport.stringOrNull(body.get("text")))
                : chatStore.sendFromClient(
                        RouteSupport.stringOrNull(body.get("fromClientId")),
                        RouteSupport.stringOrNull(body.get("roomId")),
                        RouteSupport.stringOrNull(body.get("text")));
        if (!outcome.ok) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", outcome.message));
            return;
        }
        deliverChat(outcome);
        ctx.json(Map.of("success", true, "message", outcome.message, "rooms", chatStore.snapshot()));
    }

    private void deliverChat(ChatStore.Outcome outcome) {
        if (outcome.action != null && !outcome.action.isEmpty()) {
            for (String clientId : outcome.targets) {
                clientStore.queueClientAction(clientId, outcome.action, ChatStore.DELIVERY_TTL_MS);
            }
        }
        broadcaster.broadcast(chatSnapshotPayload());
        broadcaster.broadcast(statusUpdates.get());
    }

    private Map<String, Object> chatSnapshotPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "CHAT_SNAPSHOT");
        payload.put("rooms", chatStore.snapshot());
        return payload;
    }
}
