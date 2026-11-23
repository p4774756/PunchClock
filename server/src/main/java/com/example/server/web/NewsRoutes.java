package com.example.server.web;

import com.example.server.store.NewsModels;
import com.example.server.store.NewsService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 即時新聞頭條。公開讀取，只回標題／來源／時間／連結。 */
public final class NewsRoutes {

    private final NewsService service;

    public NewsRoutes(NewsService service) {
        this.service = service;
    }

    public void register(Javalin app) {
        app.get("/api/news", this::show);
    }

    private void show(Context ctx) {
        NewsService.View view = service.get(System.currentTimeMillis());
        List<Map<String, Object>> items = new ArrayList<>();
        for (NewsModels.Item item : view.items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", item.title);
            row.put("source", item.source);
            row.put("publishedAt", item.publishedAt);
            row.put("url", item.url);
            items.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("stale", view.stale);
        body.put("fetchedAt", view.fetchedAt);
        body.put("items", items);
        body.put("message", view.message);
        ctx.json(body);
    }
}