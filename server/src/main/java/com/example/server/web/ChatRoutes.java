package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.ClientStore;
import com.example.server.store.ClientStore.PeerResult;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.function.Supplier;

/** 同事訊息與戳一下。 */
public final class ChatRoutes {

    private final AuthService authService;
    private final ClientStore clientStore;
    private final Gson gson;
    private final DashboardBroadcaster broadcaster;
    private final Supplier<Map<String, Object>> statusUpdates;

    public ChatRoutes(AuthService authService, ClientStore clientStore,
               Gson gson, DashboardBroadcaster broadcaster,
               Supplier<Map<String, Object>> statusUpdates) {
        this.authService = authService;
        this.clientStore = clientStore;
        this.gson = gson;
        this.broadcaster = broadcaster;
        this.statusUpdates = statusUpdates;
    }

    public void register(Javalin app) {
        app.post("/api/peer/message", this::peerMessage);
        app.post("/api/peer/poke", this::peerPoke);
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
}
