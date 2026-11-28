package com.example.server.util;

import io.javalin.Javalin;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class IpResolverTest {

    @Test
    public void prefersCfConnectingIp() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("CF-Connecting-IP", "203.0.113.77");
        headers.put("X-Forwarded-For", "198.51.100.9");
        assertEquals("203.0.113.77", requestIp(headers));
    }

    @Test
    public void ignoresForwardedForWhenTrustProxyOff() throws Exception {
        String previous = System.getProperty("punchclock.trustProxy");
        System.clearProperty("punchclock.trustProxy");
        try {
            Map<String, String> aHeaders = Map.of("X-Forwarded-For", "198.51.100.1");
            Map<String, String> bHeaders = Map.of("X-Forwarded-For", "198.51.100.2");
            assertEquals(requestIp(aHeaders), requestIp(bHeaders));
        } finally {
            if (previous == null) {
                System.clearProperty("punchclock.trustProxy");
            } else {
                System.setProperty("punchclock.trustProxy", previous);
            }
        }
    }

    private static String requestIp(Map<String, String> headers) throws Exception {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        try {
            app.get("/ip", ctx -> ctx.result(IpResolver.clientIp(ctx)));
            String base = "http://127.0.0.1:" + app.port();
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/ip"))
                    .GET()
                    .timeout(Duration.ofSeconds(5));
            headers.forEach(builder::header);
            HttpResponse<String> response = http.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.body();
        } finally {
            app.stop();
        }
    }
}