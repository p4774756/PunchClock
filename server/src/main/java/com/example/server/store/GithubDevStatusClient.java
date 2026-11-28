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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 讀 GitHub issues／PRs／ai/issue-* 分支。有 PUNCHCLOCK_GITHUB_TOKEN 才帶 Authorization。 */
public final class GithubDevStatusClient implements DevStatusService.Fetcher {

    public static final int MAX_PAGES = 2;
    /** 每頁 50：兩頁共 100，比 200 筆少一半下載量，仍夠覆蓋近期票。 */
    public static final int PER_PAGE = 50;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final ExecutorService executor = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r, "dev-status-github");
        t.setDaemon(true);
        return t;
    });

    @Override
    public IssueRow fetchIssue(String repo, int number) throws IOException {
        if (repo == null || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || number <= 0) {
            throw new IOException("dev-status issue");
        }
        String path = "/repos/" + repo + "/issues/" + number;
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
            throw new IOException("dev-status interrupted", ex);
        }
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IOException("dev-status http " + response.statusCode());
        }
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonObject()) {
            throw new IOException("dev-status issue json");
        }
        JsonObject obj = parsed.getAsJsonObject();
        if (obj.has("pull_request")) {
            return null;
        }
        return parseIssueObject(obj);
    }

    @Override
    public Snapshot fetch(String repo) throws IOException {
        if (repo == null || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IOException("dev-status repo");
        }
        // issues／pulls 各頁與 branches 並行，縮短同步等待時間。
        CompletableFuture<JsonArray> issues1 = getArrayAsync(issuesPath(repo, 1));
        CompletableFuture<JsonArray> issues2 = getArrayAsync(issuesPath(repo, 2));
        CompletableFuture<JsonArray> pulls1 = getArrayAsync(pullsPath(repo, 1));
        CompletableFuture<JsonArray> pulls2 = getArrayAsync(pullsPath(repo, 2));
        CompletableFuture<JsonArray> branches = getArrayAsync("/repos/" + repo + "/git/matching-refs/heads/ai/issue-");
        try {
            List<IssueRow> issues = parseIssues(join(issues1), join(issues2));
            List<PullRow> pulls = markConflicts(repo, parsePulls(join(pulls1), join(pulls2)));
            Set<Integer> branchSet = parseBranches(join(branches));
            return new Snapshot(issues, pulls, branchSet);
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("dev-status fetch", cause);
        }
    }

    private static String issuesPath(String repo, int page) {
        return "/repos/" + repo + "/issues?state=all&per_page=" + PER_PAGE
                + "&page=" + page + "&direction=desc";
    }

    private static String pullsPath(String repo, int page) {
        return "/repos/" + repo + "/pulls?state=all&per_page=" + PER_PAGE
                + "&page=" + page + "&direction=desc";
    }

    private List<IssueRow> parseIssues(JsonArray page1, JsonArray page2) throws IOException {
        List<IssueRow> out = new ArrayList<>();
        appendIssues(out, page1);
        if (page1.size() >= PER_PAGE) {
            appendIssues(out, page2);
        }
        return out;
    }

    private void appendIssues(List<IssueRow> out, JsonArray arr) throws IOException {
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("pull_request")) {
                continue;
            }
            out.add(parseIssueObject(obj));
        }
    }

    private IssueRow parseIssueObject(JsonObject obj) throws IOException {
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
        String body = obj.has("body") && !obj.get("body").isJsonNull()
                ? obj.get("body").getAsString()
                : "";
        String secretaryLine = SecretarySpeech.extractFromIssueBody(body);
        return new IssueRow(number, title, open, created, updated, closed, labels, secretaryLine);
    }

    private List<PullRow> parsePulls(JsonArray page1, JsonArray page2) throws IOException {
        List<PullRow> out = new ArrayList<>();
        appendPulls(out, page1);
        if (page1.size() >= PER_PAGE) {
            appendPulls(out, page2);
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
            // 只為解析 Closes／Fixes 暫讀 body，不留進 Snapshot／API。
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

    /** 單張 PR 補讀 mergeable 的上限，避免 open PR 很多時打爆 GitHub API。 */
    static final int MAX_CONFLICT_LOOKUPS = 10;

    /**
     * 列表 API 不含 mergeable；對 open 且非 draft 的 PR 逐張補讀，標出與 base 衝突者。
     * 任何失敗（逾時、HTTP 錯誤、mergeable 還在計算）都當作沒衝突，不影響面板。
     */
    private List<PullRow> markConflicts(String repo, List<PullRow> pulls) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < pulls.size() && indexes.size() < MAX_CONFLICT_LOOKUPS; i++) {
            PullRow p = pulls.get(i);
            if (p.open && !p.draft) {
                indexes.add(i);
            }
        }
        if (indexes.isEmpty()) {
            return pulls;
        }
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (int idx : indexes) {
            int number = pulls.get(idx).number;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return isConflicting(getObject("/repos/" + repo + "/pulls/" + number));
                } catch (IOException | RuntimeException ex) {
                    return Boolean.FALSE;
                }
            }, executor));
        }
        List<PullRow> out = new ArrayList<>(pulls);
        for (int k = 0; k < indexes.size(); k++) {
            boolean conflicting;
            try {
                conflicting = Boolean.TRUE.equals(futures.get(k).join());
            } catch (CompletionException ex) {
                conflicting = false;
            }
            int idx = indexes.get(k);
            out.set(idx, out.get(idx).withConflicting(conflicting));
        }
        return out;
    }

    /** mergeable=false 或 mergeable_state=dirty 視為有衝突；null（GitHub 還在計算）視為沒有。 */
    static boolean isConflicting(JsonObject pr) {
        if (pr == null) {
            return false;
        }
        if ("dirty".equalsIgnoreCase(text(pr, "mergeable_state"))) {
            return true;
        }
        return pr.has("mergeable") && !pr.get("mergeable").isJsonNull()
                && !pr.get("mergeable").getAsBoolean();
    }

    private JsonObject getObject(String path) throws IOException {
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
            throw new IOException("dev-status interrupted", ex);
        }
        if (response.statusCode() != 200) {
            throw new IOException("dev-status http " + response.statusCode());
        }
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonObject()) {
            throw new IOException("dev-status object");
        }
        return parsed.getAsJsonObject();
    }

    private Set<Integer> parseBranches(JsonArray arr) {
        Set<Integer> out = new HashSet<>();
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

    private CompletableFuture<JsonArray> getArrayAsync(String path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getArray(path);
            } catch (IOException ex) {
                throw new CompletionException(ex);
            }
        }, executor);
    }

    private static JsonArray join(CompletableFuture<JsonArray> future) {
        return future.join();
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
