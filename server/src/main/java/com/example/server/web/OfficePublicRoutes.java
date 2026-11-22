package com.example.server.web;

import com.example.server.store.DevMachineStore;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 辦公室訪客可見的粗略角色狀態（白名單欄位，無機敏細節）。 */
public final class OfficePublicRoutes {

    private static final List<String> CHARACTER_IDS = List.of("oki", "tiger", "reviewer");
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

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
            String repo = DevStatusService.DEFAULT_REPO;

            Map<String, Object> characters = new LinkedHashMap<>();
            for (String id : CHARACTER_IDS) {
                String status = coarseStatus(id, machines, doc, now);
                long updatedAtMs = updatedAtMs(id, machines, doc, now);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", status);
                row.put("updatedAt", Instant.ofEpochMilli(updatedAtMs).toString());
                if ("tiger".equals(id)) {
                    DevMachineStore.Snapshot tiger = findTigerMachine(machines);
                    row.put("active", buildActive(now, tiger, github, repo));
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
                                   DevStatusModels.Document doc, long now) {
            if ("reviewer".equals(charId)) {
                int n = countReviewActive(machines, now);
                if (n > 0) {
                    return n == 1 ? "審核中" : "審核中 ×" + n;
                }
                return "待機";
            }
            if ("tiger".equals(charId)) {
                DevMachineStore.Snapshot tiger = findTigerMachine(machines);
                if (tiger == null || !isOnline(tiger, now)) {
                    return "休息中";
                }
                int n = loopsOnMachine(tiger);
                if (n > 0) {
                    return n == 1 ? "工作中" : "工作中 ×" + n;
                }
                return "待命中";
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
                DevMachineStore.Snapshot tiger = findTigerMachine(machines);
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

        static List<Map<String, Object>> buildActive(long now, DevMachineStore.Snapshot tiger,
                                                     Snapshot github, String repo) {
            if (tiger == null || !isOnline(tiger, now)) {
                return List.of();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            if (tiger.runs != null && !tiger.runs.isEmpty()) {
                for (DevMachineStore.RunEntry run : tiger.runs) {
                    if (run == null || run.issue <= 0 || "review".equals(run.role)) {
                        continue;
                    }
                    out.add(activeEntry(now, run.issue, run.round, run.startedAt, run.model, github, repo));
                }
            } else if (tiger.agentRunning || (tiger.issue != null && tiger.issue > 0)) {
                int issue = tiger.issue != null ? tiger.issue : 0;
                if (issue > 0) {
                    Long started = tiger.runStartedAt;
                    out.add(activeEntry(now, issue, tiger.round != null ? tiger.round : 0, started,
                            tiger.model, github, repo));
                }
            }
            return Collections.unmodifiableList(out);
        }

        private static Map<String, Object> activeEntry(long now, int issue, int round, Long startedAt,
                                                         String model, Snapshot github, String repo) {
            Map<String, Object> row = new LinkedHashMap<>();
            IssueRow issueRow = findIssue(github, issue);
            row.put("phrase", SecretarySpeech.phraseForActive(issueRow));
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

        static DevMachineStore.Snapshot findTigerMachine(List<DevMachineStore.Snapshot> machines) {
            if (machines == null) {
                return null;
            }
            for (DevMachineStore.Snapshot row : machines) {
                if (isTigerMachine(row)) {
                    return row;
                }
            }
            return null;
        }

        static boolean isTigerMachine(DevMachineStore.Snapshot row) {
            if (row == null) {
                return false;
            }
            if ("tiger".equals(row.machine)) {
                return true;
            }
            return "猛虎".equals(row.displayName);
        }

        private static boolean isOnline(DevMachineStore.Snapshot row, long now) {
            return row != null && row.lastSeen > 0 && now - row.lastSeen <= DevMachineStore.ONLINE_WINDOW_MS;
        }

        static int countActiveLoops(List<DevMachineStore.Snapshot> machines, long now) {
            DevMachineStore.Snapshot tiger = findTigerMachine(machines);
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