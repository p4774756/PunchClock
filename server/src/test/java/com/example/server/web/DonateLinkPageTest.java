package com.example.server.web;

import com.example.DonateLink;
import com.example.server.ClientRelease;
import com.example.server.ServerApp;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusService;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DonateLinkPageTest {

    private Javalin app;
    private HttpClient http;
    private String base;
    private String previousDonateProp;

    @Before
    public void setUp() {
        previousDonateProp = System.getProperty("punchclock.donateUrl");
        DevStatusService fakeDevStatus = new DevStatusService(repo -> new DevStatusModels.Snapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptySet()
        ), "p4774756/PunchClock");
        app = new ServerApp(ClientRelease.NONE, fakeDevStatus).start(0);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
        }
        if (previousDonateProp == null) {
            System.clearProperty("punchclock.donateUrl");
        } else {
            System.setProperty("punchclock.donateUrl", previousDonateProp);
        }
    }

    @Test
    public void guestConsoleRedirectsToHomeTasks() throws Exception {
        System.setProperty("punchclock.donateUrl", "https://ko-fi.com/demo?ref=1&src=2");
        HttpResponse<String> response = send("GET", "/console", null);
        assertEquals(302, response.statusCode());
        assertEquals("/?open=tasks", response.headers().firstValue("location").orElse(""));
    }

    @Test
    public void devStatusRedirectsToHome() throws Exception {
        HttpResponse<String> response = send("GET", "/dev-status", null);
        assertEquals(301, response.statusCode());
        assertEquals("/", response.headers().firstValue("location").orElse(""));
    }

    @Test
    public void placeholderMarksUnconfigured() throws Exception {
        System.clearProperty("punchclock.donateUrl");
        HttpResponse<String> response = send("GET", "/console", null);
        assertEquals(302, response.statusCode());
        assertEquals("/?open=tasks", response.headers().firstValue("location").orElse(""));
    }

    private HttpResponse<String> send(String method, String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if ("GET".equals(method)) {
            builder.GET();
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}