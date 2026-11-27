package com.example.server.web;

import com.example.server.store.DevMachineStore;
import com.example.server.store.OfficeActiveStage;
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
    /** Unix 秒級時間戳下限（約 2001-09）；低於 {@link #EPOCH_MS_FLOOR} 且大於等於此值時視為秒、乘 1000 轉毫秒。 */
    private static final long EPOCH_SEC_FLOOR = 1_000_000_000L;
    /** 毫秒級 epoch 下限（約 2001-09），低於此視為已為毫秒或測試用小值。 */
    private static final long EPOCH_MS_FLOOR = 1_000_000_000_000L;
    /** 今天完成耗時合理上限（分鐘）。 */
    private static final long MAX_DEV_DURATION_MINUTES = 30L * 24L * 60L;
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
                String status = coarseStatus(id, machines, doc, now, store, github);
                long updatedAtMs = updatedAtMs(id, machines, doc, now);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", status);
                row.put("updatedAt", Instant.ofEpochMilli(updatedAtMs).toString());
                if ("tiger".equals(id)) {
                    DevMachineStore.Snapshot tiger = findSecretaryMachine(machines, now);
                    String machineId = secretaryMachineId(tiger);
                    row.put("active", buildActive(now, store, machineId, tiger, doc, github, repo, devStatusService));
                    row.put("queue", buildTigerQueue(now, store, machineId, tiger, doc, github, repo, devStatusService));
                    row.put("completedToday", buildCompletedToday(now, store, machines, github, repo));
                    appendTigerSystemMetrics(row, tiger, now);
                }
                characters.put(id, row);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("characters", characters);
            return out;
        }

        static String coarseStatus(String charId, List<DevMachineStore.Snapshot> machines,
                                   DevStatusModels.Document doc, long now, DevMachineStore store,
                                   Snapshot github) {
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
                int n = secretaryWorkCount(now, store, machineId, tiger, doc, github);
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
                                                   Snapshot github, String repo, DevStatusService devStatusService) {
            List<Map<String, Object>> out = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            appendRunsToActive(now, effectiveRuns(now, store, machineId, tiger), out, seen, github, repo, tiger,
                    devStatusService);
            if (out.isEmpty() && tiger != null && isOnline(tiger, now)
                    && (tiger.agentRunning || (tiger.issue != null && tiger.issue > 0))) {
                int issue = tiger.issue != null ? tiger.issue : 0;
                if (issue > 0 && seen.add(issue) && !isIssueMerged(github, issue)) {
                    out.add(activeEntryFromMachine(now, issue, tiger, github, repo, devStatusService));
                }
            }
            if (out.isEmpty() && doc != null && doc.now != null && doc.now.current != null) {
                for (DevStatusModels.Item item : doc.now.current) {
                    if (item == null || item.issue <= 0 || !seen.add(item.issue)) {
                        continue;
                    }
                    if (isIssueMerged(github, item.issue)) {
                        continue;
                    }
                    out.add(activeFromGithubItem(now, item, github, repo, devStatusService));
                }
            }
            return Collections.unmodifiableList(out);
        }

        private static void appendRunsToActive(long now, List<DevMachineStore.RunEntry> runs,
                                               List<Map<String, Object>> out, Set<Integer> seen,
                                               Snapshot github, String repo, DevMachineStore.Snapshot tiger,
                                               DevStatusService devStatusService) {
            if (runs == null || runs.isEmpty()) {
                return;
            }
            boolean agentRunning = tiger != null && tiger.agentRunning;
            String machineVerdict = tiger != null ? tiger.verdict : null;
            String machineRole = tiger != null ? tiger.role : null;
            for (DevMachineStore.RunEntry run : runs) {
                if (run == null || run.issue <= 0 || !seen.add(run.issue)) {
                    continue;
                }
                if (isIssueMerged(github, run.issue)) {
                    continue;
                }
                out.add(activeEntry(now, run, agentRunning, machineVerdict, machineRole, github, repo,
                        devStatusService));
            }
        }

        private static Map<String, Object> activeFromGithubItem(long now, DevStatusModels.Item item,
                                                                Snapshot github, String repo,
                                                                DevStatusService devStatusService) {
            IssueRow issueRow = resolveIssue(devStatusService, now, github, item.issue);
            Map<String, Object> row = new LinkedHashMap<>();
            if (item.title != null && !item.title.isEmpty()) {
                String fromItem = SecretarySpeech.sanitizePhrase(item.title.trim());
                if (fromItem != null && !fromItem.isEmpty()) {
                    row.put("phrase", fromItem);
                }
            } else {
                putActivePhrase(row, item.issue, issueRow);
            }
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
            PullRow pull = findPullForIssue(github, item.issue);
            row.put("stage", OfficeActiveStage.forGithubItem(item, pull));
            return row;
        }

        static List<Map<String, Object>> buildTigerQueue(long now, DevMachineStore store, String machineId,
                                                         DevMachineStore.Snapshot tiger, DevStatusModels.Document doc,
                                                         Snapshot github, String repo, DevStatusService devStatusService) {
            List<Map<String, Object>> out = new ArrayList<>();
            Set<Integer> seenIssues = new HashSet<>();
            Set<String> seenTitles = new HashSet<>();
            Set<Integer> activeIssues = activeIssueNumbers(now, store, machineId, tiger);
            if (doc != null && doc.now != null && doc.now.queue != null) {
                for (DevStatusModels.QueueEntry entry : doc.now.queue) {
                    if (entry == null || entry.item == null) {
                        continue;
                    }
                    DevStatusModels.Item item = entry.item;
                    if (item.issue > 0) {
                        if (activeIssues.contains(item.issue) || !seenIssues.add(item.issue)) {
                            continue;
                        }
                        out.add(queueRowFromItem(item));
                        continue;
                    }
                    String title = item.title != null ? SecretarySpeech.sanitizePhrase(item.title.trim()) : null;
                    if (title == null || title.isEmpty() || !seenTitles.add(title)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("title", title);
                    out.add(row);
                }
            }
            for (DevMachineStore.QueuedEntry q : effectiveQueued(now, store, machineId, tiger)) {
                if (q == null) {
                    continue;
                }
                if (q.issue <= 0) {
                    String planned = q.title;
                    if (planned == null || planned.isEmpty() || !seenTitles.add(planned)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("title", planned);
                    out.add(row);
                    continue;
                }
                if (activeIssues.contains(q.issue) || !seenIssues.add(q.issue)) {
                    continue;
                }
                IssueRow issueRow = resolveIssue(devStatusService, now, github, q.issue);
                String title = publicIssueTitle(issueRow);
                if (title == null && issueRow != null && issueRow.title != null) {
                    title = SecretarySpeech.sanitizePhrase(issueRow.title.trim());
                }
                if (q.title != null && !q.title.isEmpty()) {
                    title = q.title;
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

        private static void appendTigerSystemMetrics(Map<String, Object> row, DevMachineStore.Snapshot tiger, long now) {
            if (row == null || tiger == null || !isOnline(tiger, now)) {
                return;
            }
            if (tiger.cpuPercent != null) {
                row.put("cpuPercent", tiger.cpuPercent);
            }
            if (tiger.ramUsedGb != null) {
                row.put("ramUsedGb", tiger.ramUsedGb);
            }
            if (tiger.ramTotalGb != null) {
                row.put("ramTotalGb", tiger.ramTotalGb);
            }
            if (tiger.ramPercent != null) {
                row.put("ramPercent", tiger.ramPercent);
            }
        }

        private static String secretaryMachineId(DevMachineStore.Snapshot tiger) {
            if (tiger != null && tiger.machine != null) {
                return tiger.machine;
            }
            return configuredSecretaryMachine();
        }

        private static int secretaryWorkCount(long now, DevMachineStore store, String machineId,
                                              DevMachineStore.Snapshot tiger, DevStatusModels.Document doc,
                                              Snapshot github) {
            List<DevMachineStore.RunEntry> runs = effectiveRuns(now, store, machineId, tiger);
            int n = 0;
            if (runs != null) {
                for (DevMachineStore.RunEntry run : runs) {
                    if (run != null && run.issue > 0 && !isIssueMerged(github, run.issue)) {
                        n++;
                    }
                }
            }
            if (n > 0) {
                return n;
            }
            if (tiger != null && isOnline(tiger, now)) {
                int loops = loopsOnMachine(tiger, github);
                if (loops > 0) {
                    return loops;
                }
            }
            DevMachineStore.GraceHold grace = store.graceFor(machineId, now);
            if (grace != null && grace.agentRunning) {
                return 1;
            }
            if (doc != null && doc.now != null && doc.now.current != null) {
                int q = 0;
                for (DevStatusModels.Item item : doc.now.current) {
                    if (item != null && item.issue > 0 && !isIssueMerged(github, item.issue)) {
                        q++;
                    }
                }
                return q;
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

        private static Set<Integer> activeIssueNumbers(long now, DevMachineStore store, String machineId,
                                                       DevMachineStore.Snapshot tiger) {
            Set<Integer> out = new HashSet<>();
            for (DevMachineStore.RunEntry run : effectiveRuns(now, store, machineId, tiger)) {
                if (run != null && run.issue > 0) {
                    out.add(run.issue);
                }
            }
            if (tiger != null && isOnline(tiger, now)) {
                if (tiger.issue != null && tiger.issue > 0) {
                    out.add(tiger.issue);
                }
            }
            return out;
        }

        private static void putActivePhrase(Map<String, Object> row, int issue, IssueRow issueRow) {
            String publicTitle = publicIssueTitle(issueRow);
            String phrase = SecretarySpeech.phraseForActive(issueRow);
            if (SecretarySpeech.ACTIVE_FALLBACK.equals(phrase)) {
                if (publicTitle != null && !publicTitle.isEmpty()) {
                    phrase = publicTitle;
                } else if (issue > 0) {
                    return;
                }
            }
            if (phrase != null && !phrase.isEmpty()) {
                row.put("phrase", phrase);
            }
        }

        private static Map<String, Object> activeEntry(long now, DevMachineStore.RunEntry run,
                                                       boolean machineAgentRunning, String machineVerdict,
                                                       String machineRole, Snapshot github, String repo,
                                                       DevStatusService devStatusService) {
            Map<String, Object> row = new LinkedHashMap<>();
            int issue = run.issue;
            IssueRow issueRow = resolveIssue(devStatusService, now, github, issue);
            putActivePhrase(row, issue, issueRow);
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
            if (run.round > 0) {
                row.put("round", run.round);
            }
            Long timingBase = run.stageStartedAt != null ? run.stageStartedAt : run.startedAt;
            long minutes = minutesRunning(now, timingBase);
            if (minutes > 0) {
                row.put("minutes", minutes);
            }
            if (run.model != null && !run.model.isEmpty()) {
                row.put("model", run.model);
            }
            String stage = OfficeActiveStage.forRun(run, machineAgentRunning, machineVerdict, machineRole, pull);
            row.put("stage", stage);
            if (run.chain != null) {
                row.put("chain", run.chain);
            }
            String verdict = run.verdict != null ? run.verdict : machineVerdict;
            if (verdict != null) {
                row.put("verdict", verdict);
            }
            if (ciStageFailed(stage, verdict)) {
                row.put("failed", true);
            }
            return row;
        }

        private static Map<String, Object> activeEntryFromMachine(long now, int issue,
                                                                  DevMachineStore.Snapshot tiger,
                                                                  Snapshot github, String repo,
                                                                  DevStatusService devStatusService) {
            Map<String, Object> row = new LinkedHashMap<>();
            IssueRow issueRow = resolveIssue(devStatusService, now, github, issue);
            putActivePhrase(row, issue, issueRow);
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
            int round = tiger.round != null ? tiger.round : 0;
            if (round > 0) {
                row.put("round", round);
            }
            Long timingBase = tiger.stageStartedAt != null ? tiger.stageStartedAt : tiger.runStartedAt;
            long minutes = minutesRunning(now, timingBase);
            if (minutes > 0) {
                row.put("minutes", minutes);
            }
            if (tiger.stageStartedAt != null) {
                row.put("stageStartedAt", tiger.stageStartedAt);
            }
            if (tiger.model != null && !tiger.model.isEmpty()) {
                row.put("model", tiger.model);
            }
            String stage = tiger.stage != null ? tiger.stage
                    : OfficeActiveStage.forMachineFields(tiger.agentRunning, tiger.verdict,
                    tiger.role, pull);
            row.put("stage", stage);
            if (tiger.chain != null) {
                row.put("chain", tiger.chain);
            }
            String verdict = tiger.verdict;
            if (verdict != null) {
                row.put("verdict", verdict);
            }
            if (ciStageFailed(stage, verdict)) {
                row.put("failed", true);
            }
            return row;
        }

        static List<Map<String, Object>> buildCompletedToday(long now, DevMachineStore store,
                                                             List<DevMachineStore.Snapshot> machines,
                                                             Snapshot github, String repo) {
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
                String publicTitle = publicIssueTitle(issueRow);
                if (publicTitle != null) {
                    row.put("title", publicTitle);
                }
                Long devStart = earliestDevStartMs(machines, issue);
                if (devStart == null && store != null) {
                    Long recorded = store.recordedDevStart(issue);
                    if (recorded != null) {
                        devStart = normalizeOfficeEpochMillis(recorded);
                    }
                }
                if (devStart == null && pull.createdAt > 0) {
                    devStart = normalizeOfficeEpochMillis(pull.createdAt);
                }
                Long durationMinutes = devDurationMinutes(pull.mergedAt, devStart);
                if (durationMinutes != null) {
                    row.put("durationMinutes", durationMinutes);
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

        private static Long earliestDevStartMs(List<DevMachineStore.Snapshot> machines, int issue) {
            if (machines == null || issue <= 0) {
                return null;
            }
            Long earliestStarted = null;
            Long earliestDevStage = null;
            for (DevMachineStore.Snapshot machine : machines) {
                if (machine == null || machine.runs == null) {
                    continue;
                }
                for (DevMachineStore.RunEntry run : machine.runs) {
                    if (run == null || run.issue != issue) {
                        continue;
                    }
                    if (run.startedAt != null && run.startedAt > 0) {
                        long startMs = normalizeOfficeEpochMillis(run.startedAt);
                        earliestStarted = earliestStarted == null
                                ? startMs
                                : Math.min(earliestStarted, startMs);
                    }
                    boolean devRun = run.role == null || run.role.isEmpty() || "dev".equals(run.role);
                    if (devRun && run.stageStartedAt != null && run.stageStartedAt > 0) {
                        long stageMs = normalizeOfficeEpochMillis(run.stageStartedAt);
                        earliestDevStage = earliestDevStage == null
                                ? stageMs
                                : Math.min(earliestDevStage, stageMs);
                    }
                }
            }
            if (earliestStarted != null) {
                return earliestStarted;
            }
            return earliestDevStage;
        }

        static long normalizeOfficeEpochMillis(long value) {
            if (value <= 0) {
                return value;
            }
            if (value >= EPOCH_MS_FLOOR) {
                return value;
            }
            if (value >= EPOCH_SEC_FLOOR) {
                return value * 1000L;
            }
            return value;
        }

        static Long devDurationMinutes(long mergedAt, Long devStart) {
            if (devStart == null || devStart <= 0) {
                return null;
            }
            long startMs = normalizeOfficeEpochMillis(devStart);
            long mergedMs = normalizeOfficeEpochMillis(mergedAt);
            if (mergedMs <= startMs) {
                return null;
            }
            long minutes = (mergedMs - startMs) / 60_000L;
            if (minutes < 0 || minutes > MAX_DEV_DURATION_MINUTES) {
                return null;
            }
            return minutes;
        }

        static boolean isIssueMerged(Snapshot github, int issue) {
            PullRow pull = findPullForIssue(github, issue);
            return pull != null && pull.merged;
        }

        private static long minutesRunning(long now, Long startedAt) {
            if (startedAt == null || startedAt <= 0 || now <= startedAt) {
                return 0;
            }
            return (now - startedAt) / 60_000L;
        }

        private static boolean ciStageFailed(String stage, String verdict) {
            if (!"FAIL".equals(verdict)) {
                return false;
            }
            return OfficeActiveStage.WAIT_CI.equals(stage) || OfficeActiveStage.FIX.equals(stage);
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

        static IssueRow resolveIssue(DevStatusService devStatusService, long now, Snapshot github, int issue) {
            IssueRow row = findIssue(github, issue);
            if (row != null) {
                return row;
            }
            if (devStatusService == null || issue <= 0) {
                return null;
            }
            return devStatusService.issueForOffice(now, github, issue);
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
            return loopsOnMachine(tiger, null);
        }

        private static int loopsOnMachine(DevMachineStore.Snapshot row, Snapshot github) {
            if (row.runs != null && !row.runs.isEmpty()) {
                int n = 0;
                for (DevMachineStore.RunEntry run : row.runs) {
                    if (run != null && run.issue > 0 && !isIssueMerged(github, run.issue)) {
                        n++;
                    }
                }
                return n;
            }
            if (row.agentRunning || (row.issue != null && row.issue > 0)) {
                if (row.issue != null && isIssueMerged(github, row.issue)) {
                    return 0;
                }
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