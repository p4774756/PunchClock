package com.example.server.store;

import com.example.server.store.DevStatusModels.PullRow;
import com.example.server.store.RoadmapMilestoneMapper.IssueRow;
import com.example.server.store.RoadmapMilestoneMapper.MilestoneRow;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 讀 GitHub milestones／issues／PR。有 PUNCHCLOCK_GITHUB_TOKEN 才帶 Authorization。 */
public final class GithubMilestoneClient implements RoadmapService.Fetcher {

    static final int PER_PAGE = 50;
    static final int MAX_PAGES = 2;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public RoadmapModels.Document fetch(String repo) throws IOException {
        if (repo == null || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IOException("roadmap repo");
        }
        List<MilestoneRow> milestones = parseMilestones(getArray("/repos/" + repo + "/milestones?state=all&per_page=100"));
        List<PullRow> pulls = fetchPulls(repo);
        Map<Integer, List<IssueRow>> byMilestone = new HashMap<>();
        for (MilestoneRow milestone : milestones) {
            JsonArray issues = getArray("/repos/" + repo + "/issues?milestone=" + milestone.number
                    + "&state=all&per_page=100&direction=asc");
            byMilestone.put(milestone.number, parseIssues(issues));
        }
        return RoadmapMilestoneMapper.build(repo, milestones, byMilestone, pulls);
    }

    private List<PullRow> fetchPulls(String repo) throws IOException {
        JsonArray page1 = getArray(pullsPath(repo, 1));
        JsonArray page2 = page1.size() >= PER_PAGE ? getArray(pullsPath(repo, 2)) : new JsonArray();
        List<PullRow> out = new ArrayList<>();
        appendPulls(out, page1);
        if (page1.size() >= PER_PAGE) {
            appendPulls(out, page2);
        }
        return out;
    }

    private static String pullsPath(String repo, int page) {
        return "/repos/" + repo + "/pulls?state=all&per_page=" + PER_PAGE
                + "&page=" + page + "&direction=desc";
    }

    private List<MilestoneRow> parseMilestones(JsonArray arr) {
        List<MilestoneRow> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            int number = obj.get("number").getAsInt();
            String title = text(obj, "title");
            int open = obj.has("open_issues") ? obj.get("open_issues").getAsInt() : 0;
            int closed = obj.has("closed_issues") ? obj.get("closed_issues").getAsInt() : 0;
            out.add(new MilestoneRow(number, title, open, closed));
        }
        return out;
    }

    private List<IssueRow> parseIssues(JsonArray arr) {
        List<IssueRow> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("pull_request") && !obj.get("pull_request").isJsonNull()) {
                continue;
            }
            int number = obj.get("number").getAsInt();
            String title = text(obj, "title");
            boolean open = "open".equalsIgnoreCase(text(obj, "state"));
            out.add(new IssueRow(number, title, open));
        }
        return out;
    }

    private void appendPulls(List<PullRow> out, JsonArray arr) throws IOException {
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
            if (obj.has("head") && obj.get("head").isJsonObject()) {
                String ref = text(obj.getAsJsonObject("head"), "ref");
                Integer fromBranch = DevStatusMapper.parseBranchIssue("refs/heads/" + ref);
                if (fromBranch != null) {
                    closes.add(fromBranch);
                }
            }
            out.add(new PullRow(number, title, open, draft, merged, created, updated, mergedAt, closed, closes));
        }
    }

    private JsonArray getArray(String path) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.github.com" + path))
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
            throw new IOException("roadmap interrupted", ex);
        }
        if (response.statusCode() != 200) {
            throw new IOException("roadmap http " + response.statusCode());
        }
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonArray()) {
            throw new IOException("roadmap array");
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
            throw new IOException("roadmap time " + key);
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