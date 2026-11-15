package com.example.server.web;

import com.example.server.store.IslandPresence;
import io.javalin.Javalin;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsMessageContext;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 探索模式公開 WebSocket。不用登入，在線名單只放記憶體。 */
public final class IslandPresenceRoutes {

    private final IslandPresence presence = new IslandPresence();
    private final Map<String, WsContext> sockets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "island-presence");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running;

    public void register(Javalin app) {
        app.ws("/ws/island", ws -> {
            ws.onConnect(this::onConnect);
            ws.onMessage(this::onMessage);
            ws.onClose(this::onClose);
            ws.onError(this::onError);
        });
        if (!running) {
            running = true;
            ticker.scheduleAtFixedRate(this::tick, 125, 125, TimeUnit.MILLISECONDS);
        }
        app.events(events -> events.serverStopping(this::shutdown));
    }

    private void shutdown() {
        running = false;
        ticker.shutdownNow();
    }

    private void onConnect(WsConnectContext ctx) {
        if (ctx.session != null) {
            ctx.session.setMaxTextMessageSize(2048);
            ctx.session.setMaxBinaryMessageSize(1);
        }
        String host = ctx.header("Host");
        if (host == null || host.isBlank()) {
            host = ctx.host();
        }
        if (!IslandPresence.sameOrigin(ctx.header("Origin"), host, secure(ctx))) {
            ctx.closeSession(1008, "origin");
            return;
        }
        IslandPresence.Slot slot = presence.join(remoteIp(ctx), System.currentTimeMillis());
        if (!slot.ok()) {
            ctx.closeSession(1013, slot.reject == null ? "full" : slot.reject);
            return;
        }
        ctx.attribute("islandId", slot.id);
        sockets.put(slot.id, ctx);
    }

    private void onMessage(WsMessageContext ctx) {
        String id = ctx.attribute("islandId");
        if (id == null) {
            return;
        }
        IslandPresence.Result result = presence.handle(id, ctx.message(), System.currentTimeMillis());
        if (result.welcomeJson != null) {
            send(id, result.welcomeJson);
        }
        deliver(result.leaveJson, result.leaveIds);
        if (result.close) {
            sockets.remove(id);
            ctx.closeSession(1008, "rate");
        }
    }

    private void onClose(WsCloseContext ctx) {
        String id = ctx.attribute("islandId");
        if (id == null) {
            return;
        }
        sockets.remove(id, ctx);
        IslandPresence.Gone gone = presence.disconnect(id);
        if (gone != null) {
            deliver(gone.json, gone.ids);
        }
    }

    private void onError(WsErrorContext ctx) {
        String id = ctx.attribute("islandId");
        if (id == null) {
            return;
        }
        sockets.remove(id, ctx);
        IslandPresence.Gone gone = presence.disconnect(id);
        if (gone != null) {
            deliver(gone.json, gone.ids);
        }
        try {
            ctx.closeSession(1011, "error");
        } catch (Exception ignored) {
            // 已經關掉就不用再關
        }
    }

    private void tick() {
        if (!running) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            List<IslandPresence.Gone> idle = presence.sweepIdle(now);
            for (int i = 0; i < idle.size(); i++) {
                IslandPresence.Gone gone = idle.get(i);
                WsContext ctx = sockets.remove(gone.id);
                deliver(gone.json, gone.ids);
                if (ctx != null) {
                    try {
                        ctx.closeSession(1000, "idle");
                    } catch (Exception ignored) {
                        // 客戶端可能已經先斷了
                    }
                }
            }
            List<IslandPresence.Snap> snaps = presence.takeSnaps();
            for (int i = 0; i < snaps.size(); i++) {
                IslandPresence.Snap snap = snaps.get(i);
                deliver(snap.json, snap.ids);
            }
        } catch (Exception ignored) {
            // 快照執行緒不能因為一則送失敗就停掉
        }
    }

    private void deliver(String json, List<String> ids) {
        if (json == null || ids == null) {
            return;
        }
        for (int i = 0; i < ids.size(); i++) {
            send(ids.get(i), json);
        }
    }

    private void send(String id, String json) {
        WsContext ctx = sockets.get(id);
        if (ctx == null) {
            return;
        }
        try {
            synchronized (ctx) {
                ctx.send(json);
            }
        } catch (Exception ex) {
            sockets.remove(id, ctx);
        }
    }

    private static boolean secure(WsContext ctx) {
        if (Boolean.getBoolean("punchclock.trustProxy")) {
            String proto = ctx.header("X-Forwarded-Proto");
            if (proto != null && !proto.isBlank()) {
                String first = proto.split(",")[0].trim();
                return "https".equalsIgnoreCase(first) || "wss".equalsIgnoreCase(first);
            }
        }
        return ctx.session != null && ctx.session.isSecure();
    }

    /** 跟塗鴉牆一樣：只有 punchclock.trustProxy=true 才採用 X-Forwarded-For。 */
    private static String remoteIp(WsContext ctx) {
        String remote = null;
        if (ctx.session != null && ctx.session.getRemoteAddress() instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) ctx.session.getRemoteAddress();
            if (address.getAddress() != null) {
                remote = address.getAddress().getHostAddress();
            } else if (address.getHostString() != null) {
                remote = address.getHostString();
            }
        }
        return IslandPresence.clientAddress(
                Boolean.getBoolean("punchclock.trustProxy"),
                ctx.header("X-Forwarded-For"),
                remote);
    }
}
