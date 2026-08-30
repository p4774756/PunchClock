package com.example.server.web;

import com.google.gson.Gson;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class DashboardBroadcaster {

    private final Set<WsContext> sockets = ConcurrentHashMap.newKeySet();
    private final Gson gson;

    public DashboardBroadcaster(Gson gson) {
        this.gson = gson;
    }

    public void add(WsContext ctx) {
        sockets.add(ctx);
    }

    public void remove(WsContext ctx) {
        sockets.remove(ctx);
    }

    public void broadcast(Map<String, Object> payload) {
        String data = gson.toJson(payload);
        for (WsContext socket : sockets) {
            try {
                socket.send(data);
            } catch (Exception ignored) {
                sockets.remove(socket);
            }
        }
    }

    public void sendInitialSnapshot(WsContext ctx, Supplier<Map<String, Object>> snapshotSupplier) {
        Map<String, Object> payload = snapshotSupplier.get();
        ctx.send(gson.toJson(payload));
    }
}
