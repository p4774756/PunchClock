package com.example.server.store;

import com.example.server.ClientRelease;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 讀 GitHub {@code /releases/latest}。有 PUNCHCLOCK_GITHUB_TOKEN 才帶 Authorization。 */
public final class GithubClientReleaseClient implements ClientReleaseService.Fetcher {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public ClientRelease fetch(String repo) throws IOException {
        if (repo == null || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IOException("client-release repo");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PunchClock")
                .GET();
        GithubTokenHeaders.apply(builder);
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("client-release interrupted", ex);
        }
        if (response.statusCode() != 200) {
            throw new IOException("client-release http " + response.statusCode());
        }
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonObject()) {
            throw new IOException("client-release object");
        }
        ClientRelease release = ClientRelease.fromGithub(parsed.getAsJsonObject());
        if (!release.isAvailable()) {
            throw new IOException("client-release jar asset");
        }
        return release;
    }
}