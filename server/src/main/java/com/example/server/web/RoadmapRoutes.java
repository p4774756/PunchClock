package com.example.server.web;

import com.example.server.store.RoadmapParser;
import com.example.server.store.RoadmapService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 施工進度。公開讀取，只回解析後的文字，不回 issue 原文。 */
public final class RoadmapRoutes {

    private final RoadmapService service;

    public RoadmapRoutes(RoadmapService service) {
        this.service = service;
    }

    public void register(Javalin app) {
        app.get("/api/roadmap", this::show);
    }

    private void show(Context ctx) {
        RoadmapService.View view = service.get(System.currentTimeMillis());
        List<Map<String, Object>> phases = new ArrayList<>();
        for (int i = 0; i < view.phases.size(); i++) {
            RoadmapParser.Phase phase = view.phases.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("phase", phase.phase);
            row.put("title", phase.title);
            row.put("done", phase.done);
            row.put("items", phase.items);
            phases.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("stale", view.stale);
        body.put("fetchedAt", view.fetchedAt);
        body.put("source", view.source);
        body.put("current", view.current);
        body.put("done", view.done);
        body.put("phases", phases);
        body.put("message", view.message);
        ctx.json(body);
    }
}
