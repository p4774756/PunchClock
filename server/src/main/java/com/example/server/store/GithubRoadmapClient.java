package com.example.server.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 讀 GitHub issue 正文。有 PUNCHCLOCK_GITHUB_TOKEN 才帶 Authorization。 */
public final class GithubRoadmapClient implements RoadmapService.Fetcher {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String fetch(String repo, int issue) throws IOException {
        if (repo == null || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IOException("roadmap repo");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("https://api.github.com/repos/" + repo + "/issues/" + issue))
                .timeout(Duration.ofSeconds(4))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PunchClock")
                .GET();
        String token = System.getenv("PUNCHCLOCK_GITHUB_TOKEN");
        if (token != null && !token.isBlank()) {
            String value = token.trim();
            if (!value.regionMatches(true, 0, "Bearer ", 0, 7)
                    && !value.regionMatches(true, 0, "token ", 0, 6)) {
                value = "Bearer " + value;
            }
            builder.header("Authorization", value);
        }
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("roadmap interrupted", ex);
        }
        if (response.statusCode() != 200) {
            throw new IOException("roadmap http " + response.statusCode());
        }
        JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!obj.has("body") || obj.get("body").isJsonNull()) {
            throw new IOException("roadmap body");
        }
        return obj.get("body").getAsString();
    }
}
