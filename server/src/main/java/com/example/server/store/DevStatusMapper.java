package com.example.server.store;

import com.example.server.store.DevStatusModels.Document;
import com.example.server.store.DevStatusModels.IssueRow;
import com.example.server.store.DevStatusModels.Item;
import com.example.server.store.DevStatusModels.NowBlock;
import com.example.server.store.DevStatusModels.PullRow;
import com.example.server.store.DevStatusModels.QueueEntry;
import com.example.server.store.DevStatusModels.Snapshot;
import com.example.server.store.DevStatusModels.Stats;
import com.example.server.store.DevStatusModels.TimelineEvent;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 把 GitHub snapshot 對照成公開開發進度（不含正文／作者）。 */
public final class DevStatusMapper {

    public static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    public static final long TIMELINE_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L;
    /** 近 7 天時間線最多保留筆數（依時間新→舊截斷）。 */
    public static final int TIMELINE_LIMIT = 25;

    private static final Pattern QUEUE_LABEL = Pattern.compile("^queue:(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOSES = Pattern.compile(
            "(?i)\\b(?:closes|fixes|resolves)\\s+#(\\d+)\\b");
    private static final Pattern BRANCH_ISSUE = Pattern.compile("^refs/heads/ai/issue-(\\d+)$");

    private DevStatusMapper() {
    }

    public static Integer queueOrder(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        Integer best = null;
        for (String label : labels) {
            if (label == null) {
                continue;
            }
            Matcher m = QUEUE_LABEL.matcher(label.trim());
            if (!m.matches()) {
                continue;
            }
            int n = Integer.parseInt(m.group(1));
            if (n <= 0) {
                continue;
            }
            if (best == null || n < best) {
                best = n;
            }
        }
        return best;
    }

    public static Set<Integer> parseCloses(String body) {
        if (body == null || body.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> out = new HashSet<>();
        Matcher m = CLOSES.matcher(body);
        while (m.find()) {
            out.add(Integer.parseInt(m.group(1)));
        }
        return out;
    }

    public static Integer parseBranchIssue(String ref) {
        if (ref == null) {
            return null;
        }
        Matcher m = BRANCH_ISSUE.matcher(ref.trim());
        if (!m.matches()) {
            return null;
        }
        return Integer.parseInt(m.group(1));
    }

    public static String formatPair(int issue, Integer pr) {
        if (pr == null) {
            return "i" + issue;
        }
        return "i" + issue + "（p" + pr + "）";
    }

    public static String issueUrl(String repo, int issue) {
        return "https://github.com/" + repo + "/issues/" + issue;
    }

    public static String prUrl(String repo, int pr) {
        return "https://github.com/" + repo + "/pull/" + pr;
    }

    /**
     * 一張 issue 對應的 PR：已合併優先，否則取編號最大的開放 PR。
     * 已合併票殘留分支不算進行中。
     */
    public static PullRow pickPull(int issueNumber, List<PullRow> pulls, Map<Integer, List<PullRow>> byIssue) {
        List<PullRow> linked = byIssue.get(issueNumber);
        if (linked == null || linked.isEmpty()) {
            return null;
        }
        PullRow mergedBest = null;
        PullRow openBest = null;
        for (PullRow pull : linked) {
            if (pull.merged) {
                if (mergedBest == null || pull.number > mergedBest.number) {
                    mergedBest = pull;
                }
            } else if (pull.open) {
                if (openBest == null || pull.number > openBest.number) {
                    openBest = pull;
                }
            }
        }
        if (mergedBest != null) {
            return mergedBest;
        }
        return openBest;
    }

    public static String statusFor(IssueRow issue, PullRow pull, boolean hasActiveBranch) {
        if (issue.number == DevStatusModels.ROADMAP_ISSUE) {
            return DevStatusModels.STATUS_ROADMAP;
        }
        if (pull != null && pull.merged) {
            return DevStatusModels.STATUS_MERGED;
        }
        if (!issue.open) {
            return DevStatusModels.STATUS_MERGED;
        }
        if (pull != null && pull.open && !pull.draft) {
            return DevStatusModels.STATUS_REVIEW;
        }
        if (pull != null && pull.open && pull.draft) {
            return DevStatusModels.STATUS_WORKING;
        }
        if (issue.open && hasActiveBranch) {
            return DevStatusModels.STATUS_WORKING;
        }
        if (issue.open) {
            return DevStatusModels.STATUS_QUEUED;
        }
        return DevStatusModels.STATUS_MERGED;
    }

    public static boolean includeInTable(IssueRow issue, String status) {
        if (issue.number == DevStatusModels.ROADMAP_ISSUE) {
            return true;
        }
        if (issue.open) {
            return true;
        }
        return issue.number >= DevStatusModels.CLOSED_MIN_NUMBER
                && (DevStatusModels.STATUS_MERGED.equals(status) || !issue.open);
    }

    public static long weekStartTaipei(long nowMs) {
        ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(TAIPEI);
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay(TAIPEI).toInstant().toEpochMilli();
    }

    public static Document map(Snapshot snapshot, String repo, long nowMs) {
        String safeRepo = repo == null || repo.isBlank() ? DevStatusService.DEFAULT_REPO : repo.trim();
        List<IssueRow> issues = snapshot == null ? Collections.emptyList() : snapshot.issues;
        List<PullRow> pulls = snapshot == null ? Collections.emptyList() : snapshot.pulls;
        Set<Integer> branches = snapshot == null ? Collections.emptySet() : snapshot.activeBranches;

        Map<Integer, List<PullRow>> byIssue = new HashMap<>();
        for (PullRow pull : pulls) {
            for (Integer closes : pull.closesIssues) {
                byIssue.computeIfAbsent(closes, k -> new ArrayList<>()).add(pull);
            }
        }

        // 已關閉或已合併票的分支不算進行中
        Set<Integer> branchInactive = new HashSet<>();
        for (IssueRow issue : issues) {
            PullRow pull = pickPull(issue.number, pulls, byIssue);
            if (!issue.open) {
                branchInactive.add(issue.number);
            } else if (pull != null && pull.merged) {
                branchInactive.add(issue.number);
            }
        }
        Set<Integer> activeBranches = new HashSet<>();
        for (Integer n : branches) {
            if (n != null && !branchInactive.contains(n)) {
                activeBranches.add(n);
            }
        }

        Map<Integer, Item> itemsByIssue = new LinkedHashMap<>();
        List<Item> items = new ArrayList<>();
        int openIssues = 0;
        for (IssueRow issue : issues) {
            if (issue.open && issue.number != DevStatusModels.ROADMAP_ISSUE) {
                openIssues += 1;
            }
            PullRow pull = pickPull(issue.number, pulls, byIssue);
            boolean branch = activeBranches.contains(issue.number);
            String status = statusFor(issue, pull, branch);
            if (!includeInTable(issue, status)) {
                continue;
            }
            Integer prNum = pull == null ? null : Integer.valueOf(pull.number);
            long updated = issue.updatedAt;
            if (pull != null && pull.updatedAt > updated) {
                updated = pull.updatedAt;
            }
            Item item = new Item(
                    formatPair(issue.number, prNum),
                    issue.number,
                    prNum,
                    issue.title,
                    status,
                    updated,
                    issueUrl(safeRepo, issue.number),
                    prNum == null ? null : prUrl(safeRepo, prNum)
            );
            items.add(item);
            itemsByIssue.put(issue.number, item);
        }
        items.sort(Comparator.comparingInt((Item i) -> i.issue).reversed());

        Map<Integer, Integer> issueQueueOrder = new HashMap<>();
        for (IssueRow issue : issues) {
            if (!issue.open) {
                continue;
            }
            Integer order = queueOrder(issue.labels);
            if (order != null) {
                issueQueueOrder.put(issue.number, order);
            }
        }

        List<Item> current = new ArrayList<>();
        for (IssueRow issue : issues) {
            if (!issue.open) {
                continue;
            }
            Item item = itemsByIssue.get(issue.number);
            if (item == null) {
                continue;
            }
            String status = item.status;
            if (DevStatusModels.STATUS_WORKING.equals(status) || DevStatusModels.STATUS_REVIEW.equals(status)) {
                current.add(item);
            }
        }
        current.sort(inProgressOrder(issueQueueOrder));

        Set<Integer> inProgressIssues = new HashSet<>();
        for (Item item : current) {
            inProgressIssues.add(item.issue);
        }

        List<QueueEntry> queue = new ArrayList<>();
        for (IssueRow issue : issues) {
            if (!issue.open) {
                continue;
            }
            Integer order = queueOrder(issue.labels);
            if (order == null) {
                continue;
            }
            if (inProgressIssues.contains(issue.number)) {
                continue;
            }
            Item item = itemsByIssue.get(issue.number);
            if (item == null) {
                PullRow pull = pickPull(issue.number, pulls, byIssue);
                boolean branch = activeBranches.contains(issue.number);
                String status = statusFor(issue, pull, branch);
                Integer prNum = pull == null ? null : Integer.valueOf(pull.number);
                item = new Item(
                        formatPair(issue.number, prNum),
                        issue.number,
                        prNum,
                        issue.title,
                        status,
                        issue.updatedAt,
                        issueUrl(safeRepo, issue.number),
                        prNum == null ? null : prUrl(safeRepo, prNum)
                );
            }
            queue.add(new QueueEntry(order, item));
        }
        queue.sort(Comparator.comparingInt((QueueEntry q) -> q.order)
                .thenComparingInt(q -> q.item.issue));

        Item next = null;
        if (!queue.isEmpty()) {
            next = queue.get(0).item;
        }

        long weekStart = weekStartTaipei(nowMs);
        int mergedThisWeek = 0;
        for (PullRow pull : pulls) {
            if (pull.merged && pull.mergedAt != null && pull.mergedAt >= weekStart && pull.mergedAt <= nowMs) {
                mergedThisWeek += 1;
            }
        }
        Stats stats = new Stats("本週（週一 00:00 起，台北）", mergedThisWeek, openIssues);

        long timelineFrom = nowMs - TIMELINE_WINDOW_MS;
        List<TimelineEvent> timeline = new ArrayList<>();
        for (IssueRow issue : issues) {
            if (issue.number == DevStatusModels.ROADMAP_ISSUE) {
                continue;
            }
            Item item = itemsByIssue.get(issue.number);
            String ref = item != null ? item.pair : ("i" + issue.number);
            String url = issueUrl(safeRepo, issue.number);
            if (issue.createdAt >= timelineFrom && issue.createdAt <= nowMs) {
                timeline.add(new TimelineEvent(issue.createdAt, "開了 issue", ref, issue.title, url));
            }
            if (issue.closedAt != null && issue.closedAt >= timelineFrom && issue.closedAt <= nowMs) {
                PullRow pull = pickPull(issue.number, pulls, byIssue);
                if (pull == null || !pull.merged) {
                    timeline.add(new TimelineEvent(issue.closedAt, "關閉了 issue", ref, issue.title, url));
                }
            }
        }
        for (PullRow pull : pulls) {
            String url = prUrl(safeRepo, pull.number);
            String ref = "p" + pull.number;
            Integer linked = primaryClosedIssue(pull);
            if (linked != null) {
                Item item = itemsByIssue.get(linked);
                ref = item != null ? item.pair : formatPair(linked, pull.number);
            }
            if (pull.createdAt >= timelineFrom && pull.createdAt <= nowMs) {
                timeline.add(new TimelineEvent(pull.createdAt, "開了 PR", ref, pull.title, url));
            }
            if (pull.merged && pull.mergedAt != null
                    && pull.mergedAt >= timelineFrom && pull.mergedAt <= nowMs) {
                timeline.add(new TimelineEvent(pull.mergedAt, "合併", ref, pull.title, url));
            }
        }
        timeline.sort(Comparator.comparingLong((TimelineEvent e) -> e.at).reversed());
        if (timeline.size() > TIMELINE_LIMIT) {
            timeline = new ArrayList<>(timeline.subList(0, TIMELINE_LIMIT));
        }

        return new Document(new NowBlock(current, next, queue), items, timeline, stats);
    }

    /** 有 queue 標籤的進行中在前（依 queue 序），其餘依 issue 編號升冪。 */
    private static Comparator<Item> inProgressOrder(Map<Integer, Integer> queueOrders) {
        return (a, b) -> {
            Integer qa = queueOrders.get(a.issue);
            Integer qb = queueOrders.get(b.issue);
            boolean aQueued = qa != null;
            boolean bQueued = qb != null;
            if (aQueued != bQueued) {
                return aQueued ? -1 : 1;
            }
            if (aQueued) {
                int byOrder = Integer.compare(qa, qb);
                if (byOrder != 0) {
                    return byOrder;
                }
            }
            return Integer.compare(a.issue, b.issue);
        };
    }

    private static Integer primaryClosedIssue(PullRow pull) {
        if (pull.closesIssues.isEmpty()) {
            return null;
        }
        int best = Integer.MAX_VALUE;
        for (Integer n : pull.closesIssues) {
            if (n != null && n < best) {
                best = n;
            }
        }
        return best == Integer.MAX_VALUE ? null : best;
    }

    /**
     * 合併約 3 分鐘內開發機心跳的 runs 至「現在在做」；與 GitHub 項目以 issue 去重。
     */
    public static Document withFreshMachineRuns(Document doc, Snapshot snapshot, String repo, long nowMs,
            List<DevMachineStore.Snapshot> machines) {
        if (doc == null || doc.now == null || machines == null || machines.isEmpty()) {
            return doc;
        }
        Map<Integer, DevMachineStore.RunEntry> freshRuns = new LinkedHashMap<>();
        for (DevMachineStore.Snapshot machine : machines) {
            if (machine == null || machine.lastSeen <= 0
                    || nowMs - machine.lastSeen > DevMachineStore.ONLINE_WINDOW_MS) {
                continue;
            }
            if (machine.runs == null || machine.runs.isEmpty()) {
                continue;
            }
            for (DevMachineStore.RunEntry run : machine.runs) {
                if (run == null || run.issue <= 0 || run.issue == DevStatusModels.ROADMAP_ISSUE) {
                    continue;
                }
                freshRuns.putIfAbsent(run.issue, run);
            }
        }
        if (freshRuns.isEmpty()) {
            return doc;
        }
        String safeRepo = repo == null || repo.isBlank() ? DevStatusService.DEFAULT_REPO : repo.trim();
        List<PullRow> pulls = snapshot == null ? Collections.emptyList() : snapshot.pulls;
        Map<Integer, List<PullRow>> byIssue = new HashMap<>();
        for (PullRow pull : pulls) {
            for (Integer closes : pull.closesIssues) {
                byIssue.computeIfAbsent(closes, k -> new ArrayList<>()).add(pull);
            }
        }
        Set<Integer> inCurrent = new HashSet<>();
        List<Item> current = new ArrayList<>();
        for (Item item : doc.now.current) {
            if (item != null && inCurrent.add(item.issue)) {
                current.add(item);
            }
        }
        Map<Integer, Integer> issueQueueOrder = issueQueueOrders(snapshot);
        for (Map.Entry<Integer, DevMachineStore.RunEntry> entry : freshRuns.entrySet()) {
            int issue = entry.getKey();
            if (inCurrent.contains(issue)) {
                continue;
            }
            IssueRow issueRow = findIssueRow(snapshot, issue);
            if (issueRow != null && !issueRow.open) {
                continue;
            }
            DevMachineStore.RunEntry run = entry.getValue();
            current.add(itemForHeartbeatRun(issue, run, safeRepo, nowMs, doc.items, snapshot, byIssue));
            inCurrent.add(issue);
        }
        if (current.size() == doc.now.current.size()) {
            return doc;
        }
        current.sort(inProgressOrder(issueQueueOrder));
        List<QueueEntry> queue = new ArrayList<>();
        for (QueueEntry entry : doc.now.queue) {
            if (entry != null && !inCurrent.contains(entry.item.issue)) {
                queue.add(entry);
            }
        }
        Item next = queue.isEmpty() ? null : queue.get(0).item;
        NowBlock now = new NowBlock(current, next, queue);
        return new Document(now, doc.items, doc.timeline, doc.stats);
    }

    public static String statusFromHeartbeatRun(String role, int round) {
        String kind = "review".equals(role) ? "審核中" : "開發中";
        return kind + " 第 " + round + " 輪";
    }

    private static Map<Integer, Integer> issueQueueOrders(Snapshot snapshot) {
        Map<Integer, Integer> issueQueueOrder = new HashMap<>();
        if (snapshot == null) {
            return issueQueueOrder;
        }
        for (IssueRow issue : snapshot.issues) {
            if (!issue.open) {
                continue;
            }
            Integer order = queueOrder(issue.labels);
            if (order != null) {
                issueQueueOrder.put(issue.number, order);
            }
        }
        return issueQueueOrder;
    }

    private static IssueRow findIssueRow(Snapshot snapshot, int issue) {
        if (snapshot == null || snapshot.issues == null) {
            return null;
        }
        for (IssueRow row : snapshot.issues) {
            if (row != null && row.number == issue) {
                return row;
            }
        }
        return null;
    }

    private static Item itemForHeartbeatRun(int issue, DevMachineStore.RunEntry run, String repo, long nowMs,
            List<Item> items, Snapshot snapshot, Map<Integer, List<PullRow>> byIssue) {
        String status = statusFromHeartbeatRun(run.role, run.round);
        long updated = run.startedAt != null ? run.startedAt : nowMs;
        for (Item existing : items) {
            if (existing != null && existing.issue == issue) {
                return new Item(existing.pair, issue, existing.pr, existing.title, status, updated,
                        existing.issueUrl, existing.prUrl);
            }
        }
        IssueRow issueRow = findIssueRow(snapshot, issue);
        PullRow pull = snapshot == null ? null : pickPull(issue, snapshot.pulls, byIssue);
        Integer prNum = pull == null ? null : Integer.valueOf(pull.number);
        String title = issueRow == null ? "" : issueRow.title;
        return new Item(
                formatPair(issue, prNum),
                issue,
                prNum,
                title,
                status,
                updated,
                issueUrl(repo, issue),
                prNum == null ? null : prUrl(repo, prNum)
        );
    }

    /** 測試用：小寫 locale 無關。 */
    public static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }
}
