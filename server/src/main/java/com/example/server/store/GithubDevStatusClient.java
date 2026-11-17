package com.example.server.store;

import com.example.server.store.DevStatusModels.IssueRow;
import com.example.server.store.DevStatusModels.PullRow;
import com.example.server.store.DevStatusModels.Snapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 讀 GitHub issues／PRs／ai/issue-* 分支。有 PUNCHCLOCK_GITHUB_TOKEN 才帶 Authorization。 */
public final class GithubDevStatusClient implements DevStatusService.Fetcher {

    public static final int MAX_PAGES = 2;
    public static final int PER_PAGE = 100;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public Snapshot fetch(String repo) throws IOException {
        if (repo == null || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IOException("dev-status repo");
        }
        List<IssueRow> issues = fetchIssues(repo);
        List<PullRow> pulls = fetchPulls(repo);
        Set<Integer> branches = fetchBranches(repo);
        return new Snapshot(issues, pulls, branches);
    }

    private List<IssueRow> fetchIssues(String repo) throws IOException {
        List<IssueRow> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            String path = "/repos/" + repo + "/issues?state=all&per_page=" + PER_PAGE
                    + "&page=" + page + "&direction=desc";
            JsonArray arr = getArray(path);
            if (arr.size() == 0) {
                break;
            }
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("pull_request")) {
                    continue;
                }
                int number = obj.get("number").getAsInt();
                String title = text(obj, "title");
                boolean open = "open".equalsIgnoreCase(text(obj, "state"));
                long created = parseTime(obj, "created_at");
                long updated = parseTime(obj, "updated_at");
                Long closed = parseTimeOrNull(obj, "closed_at");
                List<String> labels = new ArrayList<>();
                if (obj.has("labels") && obj.get("labels").isJsonArray()) {
                    for (JsonElement labelEl : obj.getAsJsonArray("labels")) {
                        if (!labelEl.isJsonObject()) {
                            continue;
                        }
                        String name = text(labelEl.getAsJsonObject(), "name");
                        if (!name.isEmpty()) {
                            labels.add(name);
                        }
                    }
                }
                out.add(new IssueRow(number, title, open, created, updated, closed, labels));
            }
            if (arr.size() < PER_PAGE) {
                break;
            }
        }
        return out;
    }

    private List<PullRow> fetchPulls(String repo) throws IOException {
        List<PullRow> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            String path = "/repos/" + repo + "/pulls?state=all&per_page=" + PER_PAGE
                    + "&page=" + page + "&direction=desc";
            JsonArray arr = getArray(path);
            if (arr.size() == 0) {
                break;
            }
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                int number = obj.get("number").getAsInt();
                String title = text(obj, "title");
                boolean open = "open".equalsIgnoreCase(text(obj, "state"));
                boolean draft = obj.has("draft") && !obj.get("draft").isJsonNull() && obj.get("draft").getAsBoolean();
                boolean merged = obj.has("merged_at") && !obj.get("merged_at").isJsonNull();
                long created = parseTime(obj, "created_at");
                long updated = parseTime(obj, "updated_at");
                Long mergedAt = parseTimeOrNull(obj, "merged_at");
                Long closed = parseTimeOrNull(obj, "closed_at");
                String body = obj.has("body") && !obj.get("body").isJsonNull()
                        ? obj.get("body").getAsString()
                        : "";
                Set<Integer> closes = new LinkedHashSet<>(DevStatusMapper.parseCloses(body));
                // 也從 head.ref 推 ai/issue-N
                if (obj.has("head") && obj.get("head").isJsonObject()) {
                    String ref = text(obj.getAsJsonObject("head"), "ref");
                    Integer fromBranch = DevStatusMapper.parseBranchIssue("refs/heads/" + ref);
                    if (fromBranch != null) {
                        closes.add(fromBranch);
                    }
                }
                out.add(new PullRow(number, title, open, draft, merged, created, updated, mergedAt, closed, closes));
            }
            if (arr.size() < PER_PAGE) {
                break;
            }
        }
        return out;
    }

    private Set<Integer> fetchBranches(String repo) throws IOException {
        Set<Integer> out = new HashSet<>();
        JsonArray arr = getArray("/repos/" + repo + "/git/matching-refs/heads/ai/issue-");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            Integer n = DevStatusMapper.parseBranchIssue(text(el.getAsJsonObject(), "ref"));
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    private JsonArray getArray(String path) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.github.com" + path))
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PunchClock")
                .GET();
        GithubTokenHeaders.apply(builder);
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("dev-status interrupted", ex);
        }
        if (response.statusCode() != 200) {
            throw new IOException("dev-status http " + response.statusCode());
        }
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonArray()) {
            throw new IOException("dev-status array");
        }
        return parsed.getAsJsonArray();
    }

    private static String text(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).getAsString();
    }

    private static long parseTime(JsonObject obj, String key) throws IOException {
        Long value = parseTimeOrNull(obj, key);
        if (value == null) {
            throw new IOException("dev-status time " + key);
        }
        return value;
    }

    private static Long parseTimeOrNull(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return Instant.parse(obj.get(key).getAsString()).toEpochMilli();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
