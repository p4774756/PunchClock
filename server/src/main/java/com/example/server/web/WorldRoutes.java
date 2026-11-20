package com.example.server.web;

import com.example.server.store.WorldService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 公開：台北天氣、節氣節日、新聞標題。 */
public final class WorldRoutes {

    public static final String CACHE_CONTROL = "public, max-age=300";

    private final WorldService service;

    public WorldRoutes(WorldService service) {
        this.service = service;
    }

    public void register(Javalin app) {
        app.get("/api/world/now", this::now);
    }

    private void now(Context ctx) {
        WorldService.WorldNow view = service.getNow(System.currentTimeMillis());
        Map<String, Object> weather = new LinkedHashMap<>();
        weather.put("kind", view.weather.kind);
        weather.put("tempC", view.weather.tempC);
        weather.put("humidity", view.weather.humidity);
        weather.put("windKmh", view.weather.windKmh);
        weather.put("gustKmh", view.weather.gustKmh);
        weather.put("isDay", view.weather.isDay);
        weather.put("source", view.weather.source);
        weather.put("stale", view.weather.stale);

        Map<String, Object> calendar = new LinkedHashMap<>();
        calendar.put("date", view.calendar.date);
        calendar.put("solarTerm", view.calendar.solarTerm);
        calendar.put("festival", view.calendar.festival);

        List<Map<String, Object>> news = new ArrayList<>();
        for (WorldService.NewsHeadline row : view.news) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("title", row.title);
            news.add(cell);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("generatedAt", view.generatedAt);
        body.put("weather", weather);
        body.put("calendar", calendar);
        body.put("news", news);

        ctx.header("Cache-Control", CACHE_CONTROL);
        ctx.status(200);
        ctx.json(body);
    }
}