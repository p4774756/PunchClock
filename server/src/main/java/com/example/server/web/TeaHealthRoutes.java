package com.example.server.web;

import com.example.server.health.ServerHealth;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/** 公開製茶機健康快照：僅 CPU／記憶體等安全欄位，不含 clients／diskPath。 */
public final class TeaHealthRoutes {

    private final ServerHealth serverHealth;

    public TeaHealthRoutes(ServerHealth serverHealth) {
        this.serverHealth = serverHealth;
    }

    public void register(Javalin app) {
        app.get("/api/tea-health", this::show);
    }

    private void show(Context ctx) {
        Map<String, Object> snap = serverHealth.snapshot(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("processCpuPercent", snap.get("processCpuPercent"));
        body.put("systemCpuPercent", snap.get("systemCpuPercent"));
        body.put("cpuCount", snap.get("cpuCount"));
        body.put("loadAverage", snap.get("loadAverage"));
        body.put("heapUsedBytes", snap.get("heapUsedBytes"));
        body.put("heapMaxBytes", snap.get("heapMaxBytes"));
        body.put("osTotalMemoryBytes", snap.get("osTotalMemoryBytes"));
        Object totalObj = snap.get("osTotalMemoryBytes");
        Object freeObj = snap.get("osFreeMemoryBytes");
        Long osUsed = null;
        if (totalObj instanceof Number && freeObj instanceof Number) {
            long total = ((Number) totalObj).longValue();
            long free = ((Number) freeObj).longValue();
            if (total > 0 && free >= 0) {
                osUsed = Math.max(0L, total - free);
            }
        }
        body.put("osUsedMemoryBytes", osUsed);
        body.put("uptimeMs", snap.get("uptimeMs"));
        body.put("sampledAt", System.currentTimeMillis());
        ctx.json(body);
    }
}