package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.ClientStore;
import com.example.server.store.ClientStore.PeerResult;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Map;

/** 同事訊息。 */
public final class ChatRoutes {

    private final AuthService authService;
    private final ClientStore clientStore;
    private final Gson gson;
    private final DashboardBroadcaster broadcaster;
    private final Runnable statusBroadcast;

    public ChatRoutes(AuthService authService, ClientStore clientStore,
               Gson gson, DashboardBroadcaster broadcaster,
               Runnable statusBroadcast) {
        this.authService = authService;
        this.clientStore = clientStore;
        this.gson = gson;
        this.broadcaster = broadcaster;
        this.statusBroadcast = statusBroadcast;
    }

    public void register(Javalin app) {
        app.post("/api/peer/message", this::peerMessage);
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
        String fromClientId = RouteSupport.stringOrNull(body.get("fromClientId"));
        if (!authService.clientMayUseFromClientId(ctx, fromClientId)) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("success", false, "message", "fromClientId 不符"));
            return;
        }
        PeerResult result = clientStore.queuePeerMessage(
                RouteSupport.stringOrNull(body.get("toClientId")),
                fromClientId,
                RouteSupport.stringOrNull(body.get("text")),
                RouteSupport.stringOrNull(body.get("avatar")),
                RouteSupport.stringOrNull(body.get("image"))
        );
        if (!result.ok) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", result.message));
            return;
        }
        statusBroadcast.run();
        ctx.json(Map.of("success", true, "message", result.message));
    }
}
