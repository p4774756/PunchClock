package com.example.server.web;

import com.example.server.store.WeatherKind;
import com.example.server.store.WorldService;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorldRoutesTest {

    private Javalin app;
    private HttpClient http;
    private String base;

    @Before
    public void setUp() {
        WorldService service = new WorldService(
                (() -> new WorldService.WeatherSnapshot(
                        WeatherKind.CLEAR, 25.0, 55, 8, 12, true, "mock", false)),
                (() -> Collections.singletonList(new WorldService.NewsHeadline("測試標題"))));
        app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        new WorldRoutes(service).register(app);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    public void guestsGetWorldNow() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/world/now"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"success\":true"));
        assertTrue(response.body().contains("\"weather\""));
        assertTrue(response.body().contains("\"calendar\""));
        assertTrue(response.body().contains("\"news\""));
        assertTrue(response.body().contains("\"kind\":\"clear\""));
        assertTrue(response.body().contains("\"recentSolarTerm\""));
        assertTrue(response.body().contains("\"solarTermIsToday\""));
        String cache = response.headers().firstValue("cache-control").orElse("");
        assertTrue(cache.contains("public"));
        assertTrue(cache.contains("max-age=300"));
    }
}