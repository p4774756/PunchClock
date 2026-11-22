package com.example.server.web;

import com.example.server.ServerApp;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.Assert.assertEquals;

/** 訪客簽名簿已下架，相關 API 應回 404。 */
public class GuestbookRemovedTest {

    private Javalin app;
    private HttpClient http;
    private String base;

    @Before
    public void setUp() {
        app = new ServerApp().start(0);
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
    public void guestbookApiReturns404() throws Exception {
        assertEquals(404, get("/api/guestbook").statusCode());
        assertEquals(404, get("/api/guestbook?page=1").statusCode());
        assertEquals(404, get("/api/guestbook/stats").statusCode());
        assertEquals(404, get("/api/guestbook/abc.webp").statusCode());
        assertEquals(404, post("/api/guestbook").statusCode());
        assertEquals(404, post("/api/guestbook/some-id/hide").statusCode());
        assertEquals(404, delete("/api/guestbook/some-id").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(5))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .DELETE()
                        .timeout(Duration.ofSeconds(5))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}