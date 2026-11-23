package com.example.server.web;

import com.google.gson.Gson;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public final class DashboardBroadcaster {

    private static final int CLOSE_AUTH_REVOKED = 4401;

    private final Set<WsContext> sockets = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<WsContext, String> sessionKeyBySocket = new ConcurrentHashMap<>();
    private final Gson gson;

    public DashboardBroadcaster(Gson gson) {
        this.gson = gson;
    }

    public void add(WsContext ctx, String sessionKey) {
        sockets.add(ctx);
        if (sessionKey != null) {
            sessionKeyBySocket.put(ctx, sessionKey);
        }
    }

    public void remove(WsContext ctx) {
        sockets.remove(ctx);
        sessionKeyBySocket.remove(ctx);
    }

    public void broadcast(Map<String, Object> payload) {
        broadcastForEach(ignored -> payload);
    }

    public void broadcastForEach(Function<String, Map<String, Object>> payloadFactory) {
        String data;
        for (WsContext socket : sockets) {
            try {
                String sessionKey = sessionKeyBySocket.get(socket);
                Map<String, Object> payload = payloadFactory.apply(sessionKey);
                data = gson.toJson(payload);
                socket.send(data);
            } catch (Exception ignored) {
                sockets.remove(socket);
                sessionKeyBySocket.remove(socket);
            }
        }
    }

    public void closeSession(String sessionKey) {
        if (sessionKey == null) {
            return;
        }
        for (WsContext socket : sockets) {
            if (sessionKey.equals(sessionKeyBySocket.get(socket))) {
                try {
                    socket.closeSession(CLOSE_AUTH_REVOKED, "session revoked");
                } catch (Exception ignored) {
                    // already closed
                }
                remove(socket);
            }
        }
    }

    public void sendInitialSnapshot(WsContext ctx, Supplier<Map<String, Object>> snapshotSupplier) {
        Map<String, Object> payload = snapshotSupplier.get();
        ctx.send(gson.toJson(payload));
    }
}