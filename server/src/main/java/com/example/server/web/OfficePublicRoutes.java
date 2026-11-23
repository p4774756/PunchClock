package com.example.server.web;

import com.example.server.store.DevMachineStore;
import com.example.server.store.DevStatusMapper;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusModels.IssueRow;
import com.example.server.store.DevStatusModels.PullRow;
import com.example.server.store.DevStatusModels.Snapshot;
import com.example.server.store.DevStatusService;
import com.example.server.store.SecretarySpeech;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 辦公室訪客可見的粗略角色狀態（白名單欄位，無機敏細節）。 */
public final class OfficePublicRoutes {

    private static final List<String> CHARACTER_IDS = List.of("oki", "tiger", "reviewer");
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final String DEFAULT_SECRETARY_MACHINE = "tiger";

    private final DevMachineStore devMachineStore;
    private final DevStatusService devStatusService;

    public OfficePublicRoutes(DevMachineStore devMachineStore, DevStatusService devStatusService) {
        this.devMachineStore = devMachineStore;
        this.devStatusService = devStatusService;
    }

    public void register(Javalin app) {
        app.get("/api/office/public", this::getPublic);
    }

    void getPublic(Context ctx) {
        long now = devMachineStore.currentTimeMillis();
        Map<String, Object> payload = OfficePublicPayload.build(now, devMachineStore, devStatusService);
        ctx.json(payload);
    }

    static final class OfficePublicPayload {

        private OfficePublicPayload() {
        }

        static Map<String, Object> build(long now, DevMachineStore store, DevStatusService devStatusService) {
            DevStatusService.View view = devStatusService.get(now);
            DevStatusModels.Document doc = view == null ? null : view.document;
            List<DevMachineStore.Snapshot> machines = store.machineSnapshots();
            Snapshot github = devStatusService.githubSnapshot(now);
            String repo = devStatusService.repo();
            if (doc != null) {
                doc = DevStatusMapper.withHeartbeatQueued(doc, machines, github, repo, now);
            }

            Map<String, Object> characters = new LinkedHashMap<>();
            for (String id : CHARACTER_IDS) {
                String status = coarseStatus(id, machines, doc, now, store);
                long updatedAtMs = updatedAtMs(id, machines, doc, now);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", status);
                row.put("updatedAt", Instant.ofEpochMilli(updatedAtMs).toString());
                if ("tiger".equals(id)) {
                    DevMachineStore.Snapshot tiger = findSecretaryMachine(machines, now);
                    String machineId = secretaryMachineId(tiger);
                    row.put("active", buildActive(now, store, machineId, tiger, doc, github, repo));
                    row.put("queue", buildTigerQueue(now, store, machineId, tiger, doc, github, repo));
                    row.put("completedToday", buildCompletedToday(now, github, repo));
                }
                characters.put(id, row);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("characters", characters);
            return out;
        }

        static String coarseStatus(String charId, List<DevMachineStore.Snapshot> machines,
                                   DevStatusModels.Document doc, long now, DevMachineStore store) {
            if ("reviewer".equals(charId)) {
                int n = countReviewActive(machines, now);
                if (n > 0) {
                    return n == 1 ? "審核中" : "審核中 ×" + n;
                }
                return "待機";
            }
            if ("tiger".equals(charId)) {
                DevMachineStore.Snapshot tiger = findSecretaryMachine(machines, now);
                String machineId = secretaryMachineId(tiger);
                int n = secretaryWorkCount(now, store, machineId, tiger, doc);
                if (n > 0) {
                    return n == 1 ? "工作中" : "工作中 ×" + n;
                }
                boolean online = tiger != null && isOnline(tiger, now);
                if (online || hasSecretaryQueue(now, store, machineId, tiger, doc)) {
                    return "待命中";
                }
                return "休息中";
            }
            if ("oki".equals(charId)) {
                if (hasInProgress(doc)) {
                    return "工作中";
                }
                int q = doc != null && doc.now != null && doc.now.queue != null ? doc.now.queue.size() : 0;
                return "佇列 " + q;
            }
            return "待機";
        }

        static long updatedAtMs(String charId, List<DevMachineStore.Snapshot> machines,
                                DevStatusModels.Document doc, long now) {
            if ("tiger".equals(charId)) {
                DevMachineStore.Snapshot tiger = findSecretaryMachine(machines, now);
                if (tiger != null && isOnline(tiger, now) && tiger.lastSeen > 0) {
                    return tiger.lastSeen;
                }
            }
            if ("reviewer".equals(charId)) {
                long best = 0;
                for (DevMachineStore.Snapshot row : machines) {
                    if (row == null || !isOnline(row, now)) {
                        continue;
                    }
                    if (countReviewOnMachine(row) > 0 && row.lastSeen > best) {
                        best = row.lastSeen;
                    }
                }
                if (best > 0) {
                    return best;
                }
            }
            if ("oki".equals(charId) && doc != null && doc.now != null) {
                long updated = latestCurrentUpdatedAt(doc);
                if (updated > 0) {
                    return updated;
                }
            }
            return now;
        }

        static List<Map<String, Object>> buildActive(long now, DevMachineStore store, String machineId,
                                                   DevMachineStore.Snapshot tiger, DevStatusModels.Document doc,
                                                   Snapshot github, String repo) {
            List<Map<String, Object>> out = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            appendRunsToActive(now, effectiveRuns(now, store, machineId, tiger), out, seen, github, repo);
            if (out.isEmpty() && tiger != null && isOnline(tiger, now)
                    && (tiger.agentRunning || (tiger.issue != null && tiger.issue > 0))) {
                int issue = tiger.issue != null ? tiger.issue : 0;
                if (issue > 0 && seen.add(issue)) {
                    out.add(activeEntry(now, issue, tiger.round != null ? tiger.round : 0, tiger.runStartedAt,
                            tiger.model, github, repo));
                }
            }
            if (out.isEmpty() && doc != null && doc.now != null && doc.now.current != null) {
                for (DevStatusModels.Item item : doc.now.current) {
                    if (item == null || item.issue <= 0 || !seen.add(item.issue)) {
                        continue;
                    }
                    out.add(activeFromGithubItem(now, item, github, repo));
                }
            }
            return Collections.unmodifiableList(out);
        }

        private static void appendRunsToActive(long now, List<DevMachineStore.RunEntry> runs,
                                               List<Map<String, Object>> out, Set<Integer> seen,
                                               Snapshot github, String repo) {
            if (runs == null || runs.isEmpty()) {
                return;
            }
            for (DevMachineStore.RunEntry run : runs) {
                if (run == null || run.issue <= 0 || "review".equals(run.role) || !seen.add(run.issue)) {
                    continue;
                }
                out.add(activeEntry(now, run.issue, run.round, run.startedAt, run.model, github, repo));
            }
        }

        private static Map<String, Object> activeFromGithubItem(long now, DevStatusModels.Item item,
                                                                Snapshot github, String repo) {
            IssueRow issueRow = findIssue(github, item.issue);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("phrase", item.title != null && !item.title.isEmpty()
                    ? SecretarySpeech.sanitizePhrase(item.title.trim())
                    : SecretarySpeech.phraseForActive(issueRow));
            String publicTitle = publicIssueTitle(issueRow);
            if (publicTitle != null) {
                row.put("title", publicTitle);
            }
            row.put("issue", item.issue);
            row.put("issueUrl", item.issueUrl != null ? item.issueUrl : issueUrl(repo, item.issue));
            if (item.pr != null && item.pr > 0) {
                row.put("pr", item.pr);
                row.put("prUrl", item.prUrl != null ? item.prUrl : pullUrl(repo, item.pr));
            }
            long minutes = minutesRunning(now, item.updatedAt);
            if (minutes > 0) {
                row.put("minutes", minutes);
            }
            return row;
        }

        static List<Map<String, Object>> buildTigerQueue(long now, DevMachineStore store, String machineId,
                                                         DevMachineStore.Snapshot tiger, DevStatusModels.Document doc,
                                                         Snapshot github, String repo) {
            List<Map<String, Object>> out = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            if (doc != null && doc.now != null && doc.now.queue != null) {
                for (DevStatusModels.QueueEntry entry : doc.now.queue) {
                    if (entry == null || entry.item == null || entry.item.issue <= 0) {
                        continue;
                    }
                    if (seen.add(entry.item.issue)) {
                        out.add(queueRowFromItem(entry.item));
                    }
                }
            }
            for (DevMachineStore.QueuedEntry q : effectiveQueued(now, store, machineId, tiger)) {
                if (q == null || q.issue <= 0 || !seen.add(q.issue)) {
                    continue;
                }
                IssueRow issueRow = findIssue(github, q.issue);
                String title = publicIssueTitle(issueRow);
                if (title == null && issueRow != null && issueRow.title != null) {
                    title = SecretarySpeech.sanitizePhrase(issueRow.title.trim());
                }
                if (title == null || title.isEmpty()) {
                    title = "等待開工 #" + q.issue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("issue", q.issue);
                row.put("title", title);
                row.put("issueUrl", issueUrl(repo, q.issue));
                PullRow pull = findPullForIssue(github, q.issue);
                if (pull != null) {
                    row.put("pr", pull.number);
                    row.put("prUrl", pullUrl(repo, pull.number));
                }
                if (q.afterIssue != null && q.afterIssue > 0) {
                    row.put("waitingFor", q.afterIssue);
                }
                out.add(row);
            }
            return Collections.unmodifiableList(out);
        }

        private static Map<String, Object> queueRowFromItem(DevStatusModels.Item item) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("issue", item.issue);
            if (item.title != null && !item.title.isEmpty()) {
                row.put("title", item.title);
            }
            if (item.issueUrl != null) {
                row.put("issueUrl", item.issueUrl);
            }
            if (item.pr != null && item.pr > 0) {
                row.put("pr", item.pr);
                if (item.prUrl != null) {
                    row.put("prUrl", item.prUrl);
                }
            }
            return row;
        }

        private static String secretaryMachineId(DevMachineStore.Snapshot tiger) {
            if (tiger != null && tiger.machine != null) {
                return tiger.machine;
            }
            return configuredSecretaryMachine();
        }

        private static int secretaryWorkCount(long now, DevMachineStore store, String machineId,
                                              DevMachineStore.Snapshot tiger, DevStatusModels.Document doc) {
            List<DevMachineStore.RunEntry> runs = effectiveRuns(now, store, machineId, tiger);
            int n = 0;
            if (runs != null) {
                for (DevMachineStore.RunEntry run : runs) {
                    if (run != null && run.issue > 0) {
                        n++;
                    }
                }
            }
            if (n > 0) {
                return n;
            }
            if (tiger != null && isOnline(tiger, now)) {
                int loops = loopsOnMachine(tiger);
                if (loops > 0) {
                    return loops;
                }
            }
            DevMachineStore.GraceHold grace = store.graceFor(machineId, now);
            if (grace != null && grace.agentRunning) {
                return 1;
            }
            if (doc != null && doc.now != null && doc.now.current != null) {
                return doc.now.current.size();
            }
            return 0;
        }

        private static List<DevMachineStore.RunEntry> effectiveRuns(long now, DevMachineStore store, String machineId,
                                                                    DevMachineStore.Snapshot tiger) {
            if (tiger != null && isOnline(tiger, now) && tiger.runs != null && !tiger.runs.isEmpty()) {
                return tiger.runs;
            }
            DevMachineStore.GraceHold grace = store.graceFor(machineId, now);
            if (grace != null && !grace.runs.isEmpty()) {
                return grace.runs;
            }
            return List.of();
        }

        private static List<DevMachineStore.QueuedEntry> effectiveQueued(long now, DevMachineStore store,
                                                                         String machineId,
                                                                         DevMachineStore.Snapshot tiger) {
            if (tiger != null && isOnline(tiger, now) && tiger.queued != null && !tiger.queued.isEmpty()) {
                return tiger.queued;
            }
            DevMachineStore.GraceHold grace = store.graceFor(machineId, now);
            if (grace != null && !grace.queued.isEmpty()) {
                return grace.queued;
            }
            return List.of();
        }

        private static boolean hasSecretaryQueue(long now, DevMachineStore store, String machineId,
                                                 DevMachineStore.Snapshot tiger, DevStatusModels.Document doc) {
            if (!effectiveQueued(now, store, machineId, tiger).isEmpty()) {
                return true;
            }
            return doc != null && doc.now != null && doc.now.queue != null && !doc.now.queue.isEmpty();
        }

        private static Map<String, Object> activeEntry(long now, int issue, int round, Long startedAt,
                                                         String model, Snapshot github, String repo) {
            Map<String, Object> row = new LinkedHashMap<>();
            IssueRow issueRow = findIssue(github, issue);
            row.put("phrase", SecretarySpeech.phraseForActive(issueRow));
            String publicTitle = publicIssueTitle(issueRow);
            if (publicTitle != null) {
                row.put("title", publicTitle);
            }
            row.put("issue", issue);
            row.put("issueUrl", issueUrl(repo, issue));
            PullRow pull = findPullForIssue(github, issue);
            if (pull != null) {
                row.put("pr", pull.number);
                row.put("prUrl", pullUrl(repo, pull.number));
            }
            if (round > 0) {
                row.put("round", round);
            }
            long minutes = minutesRunning(now, startedAt);
            if (minutes > 0) {
                row.put("minutes", minutes);
            }
            if (model != null && !model.isEmpty()) {
                row.put("model", model);
            }
            return row;
        }

        static List<Map<String, Object>> buildCompletedToday(long now, Snapshot github, String repo) {
            if (github == null) {
                return List.of();
            }
            long dayStart = Instant.ofEpochMilli(now).atZone(TAIPEI).toLocalDate()
                    .atStartOfDay(TAIPEI).toInstant().toEpochMilli();
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<Integer, PullRow> pullByIssue = new HashMap<>();
            for (PullRow pull : github.pulls) {
                if (pull == null || !pull.merged || pull.mergedAt == null || pull.mergedAt < dayStart) {
                    continue;
                }
                for (Integer issueNum : pull.closesIssues) {
                    if (issueNum == null || issueNum <= 0) {
                        continue;
                    }
                    PullRow prev = pullByIssue.get(issueNum);
                    if (prev == null || pull.mergedAt > prev.mergedAt) {
                        pullByIssue.put(issueNum, pull);
                    }
                }
            }
            for (Map.Entry<Integer, PullRow> entry : pullByIssue.entrySet()) {
                int issue = entry.getKey();
                PullRow pull = entry.getValue();
                IssueRow issueRow = findIssue(github, issue);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("at", Instant.ofEpochMilli(pull.mergedAt).toString());
                row.put("phrase", SecretarySpeech.phraseForCompleted(issueRow));
                String publicTitle = publicIssueTitle(issueRow);
                if (publicTitle != null) {
                    row.put("title", publicTitle);
                }
                row.put("issue", issue);
                row.put("issueUrl", issueUrl(repo, issue));
                row.put("pr", pull.number);
                row.put("prUrl", pullUrl(repo, pull.number));
                rows.add(row);
            }
            rows.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.get("at")).reversed());
            return Collections.unmodifiableList(rows);
        }

        private static long minutesRunning(long now, Long startedAt) {
            if (startedAt == null || startedAt <= 0 || now <= startedAt) {
                return 0;
            }
            return (now - startedAt) / 60_000L;
        }

        static IssueRow findIssue(Snapshot github, int issue) {
            if (github == null || github.issues == null || issue <= 0) {
                return null;
            }
            for (IssueRow row : github.issues) {
                if (row != null && row.number == issue) {
                    return row;
                }
            }
            return null;
        }

        static String publicIssueTitle(IssueRow issueRow) {
            if (issueRow == null || issueRow.title == null) {
                return null;
            }
            String clean = SecretarySpeech.sanitizePhrase(issueRow.title.trim());
            return clean == null || clean.isEmpty() ? null : clean;
        }

        static PullRow findPullForIssue(Snapshot github, int issue) {
            if (github == null || github.pulls == null || issue <= 0) {
                return null;
            }
            PullRow best = null;
            for (PullRow pull : github.pulls) {
                if (pull == null || !pull.closesIssues.contains(issue)) {
                    continue;
                }
                if (best == null || pull.number > best.number) {
                    best = pull;
                }
            }
            return best;
        }

        static String issueUrl(String repo, int issue) {
            return "https://github.com/" + repo + "/issues/" + issue;
        }

        static String pullUrl(String repo, int pr) {
            return "https://github.com/" + repo + "/pull/" + pr;
        }

        static String configuredSecretaryMachine() {
            String raw = System.getenv("OFFICE_SECRETARY_MACHINE");
            if (raw != null) {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty() && DevMachineStore.isAllowedMachine(trimmed)) {
                    return trimmed;
                }
            }
            return DEFAULT_SECRETARY_MACHINE;
        }

        static DevMachineStore.Snapshot findSecretaryMachine(List<DevMachineStore.Snapshot> machines, long now) {
            if (machines == null || machines.isEmpty()) {
                return null;
            }
            String preferred = configuredSecretaryMachine();
            DevMachineStore.Snapshot fallback = null;
            long bestLastSeen = -1L;
            for (DevMachineStore.Snapshot row : machines) {
                if (row == null || !isOnline(row, now)) {
                    continue;
                }
                if (preferred.equals(row.machine)) {
                    return row;
                }
                if (row.lastSeen > bestLastSeen) {
                    bestLastSeen = row.lastSeen;
                    fallback = row;
                }
            }
            return fallback;
        }

        private static boolean isOnline(DevMachineStore.Snapshot row, long now) {
            return row != null && row.lastSeen > 0 && now - row.lastSeen <= DevMachineStore.ONLINE_WINDOW_MS;
        }

        static int countActiveLoops(List<DevMachineStore.Snapshot> machines, long now) {
            DevMachineStore.Snapshot tiger = findSecretaryMachine(machines, now);
            if (tiger == null || !isOnline(tiger, now)) {
                return 0;
            }
            return loopsOnMachine(tiger);
        }

        private static int loopsOnMachine(DevMachineStore.Snapshot row) {
            if (row.runs != null && !row.runs.isEmpty()) {
                int n = 0;
                for (DevMachineStore.RunEntry run : row.runs) {
                    if (run != null && run.issue > 0) {
                        n++;
                    }
                }
                return n;
            }
            if (row.agentRunning || (row.issue != null && row.issue > 0)) {
                return 1;
            }
            return 0;
        }

        private static boolean hasInProgress(DevStatusModels.Document doc) {
            if (doc == null || doc.now == null || doc.now.current == null) {
                return false;
            }
            for (DevStatusModels.Item item : doc.now.current) {
                if (item != null && item.issue > 0) {
                    return true;
                }
            }
            return false;
        }

        private static long latestCurrentUpdatedAt(DevStatusModels.Document doc) {
            if (doc == null || doc.now == null || doc.now.current == null) {
                return 0;
            }
            long best = 0;
            for (DevStatusModels.Item item : doc.now.current) {
                if (item != null && item.updatedAt > best) {
                    best = item.updatedAt;
                }
            }
            return best;
        }

        static int countReviewActive(List<DevMachineStore.Snapshot> machines, long now) {
            if (machines == null) {
                return 0;
            }
            int total = 0;
            for (DevMachineStore.Snapshot row : machines) {
                if (row == null || !isOnline(row, now)) {
                    continue;
                }
                total += countReviewOnMachine(row);
            }
            return total;
        }

        private static int countReviewOnMachine(DevMachineStore.Snapshot row) {
            int n = 0;
            if (row.runs != null) {
                for (DevMachineStore.RunEntry run : row.runs) {
                    if (run != null && "review".equals(run.role)) {
                        n++;
                    }
                }
            }
            if (n == 0 && "review".equals(row.role)) {
                n = 1;
            }
            return n;
        }
    }
}