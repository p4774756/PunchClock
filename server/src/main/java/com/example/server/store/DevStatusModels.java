package com.example.server.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 開發進度中介資料（server 內部用；回應只放消毒後欄位）。 */
public final class DevStatusModels {

    public static final String STATUS_QUEUED = "排隊";
    public static final String STATUS_WORKING = "實作中";
    public static final String STATUS_REVIEW = "審核中";
    public static final String STATUS_MERGED = "已合併";
    public static final String STATUS_ROADMAP = "路線圖";

    public static final int ROADMAP_ISSUE = 36;
    public static final int CLOSED_MIN_NUMBER = 29;

    private DevStatusModels() {
    }

    public static final class IssueRow {
        public final int number;
        public final String title;
        public final boolean open;
        public final long createdAt;
        public final long updatedAt;
        public final Long closedAt;
        public final List<String> labels;
        /** 正文擷取的「秘書說」一句；不含全文。 */
        public final String secretaryLine;

        public IssueRow(int number, String title, boolean open, long createdAt, long updatedAt,
                        Long closedAt, List<String> labels) {
            this(number, title, open, createdAt, updatedAt, closedAt, labels, null);
        }

        public IssueRow(int number, String title, boolean open, long createdAt, long updatedAt,
                        Long closedAt, List<String> labels, String secretaryLine) {
            this.number = number;
            this.title = title == null ? "" : title;
            this.open = open;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.closedAt = closedAt;
            this.labels = labels == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(labels));
            this.secretaryLine = secretaryLine;
        }
    }

    public static final class PullRow {
        public final int number;
        public final String title;
        public final boolean open;
        public final boolean draft;
        public final boolean merged;
        public final long createdAt;
        public final long updatedAt;
        public final Long mergedAt;
        public final Long closedAt;
        public final Set<Integer> closesIssues;
        /** 與 base 分支有衝突（mergeable=false／mergeable_state=dirty）；CI 不會跑。 */
        public final boolean conflicting;
        /** 最新 commit 的所有 CI check 都已完成且通過（success／neutral／skipped）；讀不到時為 false。 */
        public final boolean ciPassed;

        public PullRow(int number, String title, boolean open, boolean draft, boolean merged,
                       long createdAt, long updatedAt, Long mergedAt, Long closedAt,
                       Set<Integer> closesIssues) {
            this(number, title, open, draft, merged, createdAt, updatedAt, mergedAt, closedAt,
                    closesIssues, false);
        }

        public PullRow(int number, String title, boolean open, boolean draft, boolean merged,
                       long createdAt, long updatedAt, Long mergedAt, Long closedAt,
                       Set<Integer> closesIssues, boolean conflicting) {
            this(number, title, open, draft, merged, createdAt, updatedAt, mergedAt, closedAt,
                    closesIssues, conflicting, false);
        }

        public PullRow(int number, String title, boolean open, boolean draft, boolean merged,
                       long createdAt, long updatedAt, Long mergedAt, Long closedAt,
                       Set<Integer> closesIssues, boolean conflicting, boolean ciPassed) {
            this.number = number;
            this.title = title == null ? "" : title;
            this.open = open;
            this.draft = draft;
            this.merged = merged;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.mergedAt = mergedAt;
            this.closedAt = closedAt;
            this.closesIssues = closesIssues == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(closesIssues));
            this.conflicting = conflicting;
            this.ciPassed = ciPassed;
        }

        public PullRow withConflicting(boolean value) {
            if (value == conflicting) {
                return this;
            }
            return new PullRow(number, title, open, draft, merged, createdAt, updatedAt, mergedAt,
                    closedAt, closesIssues, value, ciPassed);
        }

        public PullRow withCiPassed(boolean value) {
            if (value == ciPassed) {
                return this;
            }
            return new PullRow(number, title, open, draft, merged, createdAt, updatedAt, mergedAt,
                    closedAt, closesIssues, conflicting, value);
        }
    }

    public static final class Snapshot {
        public final List<IssueRow> issues;
        public final List<PullRow> pulls;
        public final Set<Integer> activeBranches;

        public Snapshot(List<IssueRow> issues, List<PullRow> pulls, Set<Integer> activeBranches) {
            this.issues = issues == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(issues));
            this.pulls = pulls == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(pulls));
            this.activeBranches = activeBranches == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(activeBranches));
        }
    }

    public static final class Item {
        public final String pair;
        public final int issue;
        public final Integer pr;
        public final String title;
        public final String status;
        public final long updatedAt;
        public final String issueUrl;
        public final String prUrl;

        public Item(String pair, int issue, Integer pr, String title, String status,
                    long updatedAt, String issueUrl, String prUrl) {
            this.pair = pair;
            this.issue = issue;
            this.pr = pr;
            this.title = title;
            this.status = status;
            this.updatedAt = updatedAt;
            this.issueUrl = issueUrl;
            this.prUrl = prUrl;
        }
    }

    public static final class QueueEntry {
        public final int order;
        public final Item item;

        public QueueEntry(int order, Item item) {
            this.order = order;
            this.item = Objects.requireNonNull(item);
        }
    }

    public static final class TimelineEvent {
        public final long at;
        public final String action;
        public final String ref;
        public final String title;
        public final String url;

        public TimelineEvent(long at, String action, String ref, String title, String url) {
            this.at = at;
            this.action = action;
            this.ref = ref;
            this.title = title;
            this.url = url;
        }
    }

    public static final class Stats {
        public final String weekLabel;
        public final int mergedThisWeek;
        public final int openIssues;

        public Stats(String weekLabel, int mergedThisWeek, int openIssues) {
            this.weekLabel = weekLabel;
            this.mergedThisWeek = mergedThisWeek;
            this.openIssues = openIssues;
        }
    }

    public static final class NowBlock {
        /** 全部進行中（實作中或審核中）；可為空，不為 null。 */
        public final List<Item> current;
        public final Item next;
        public final List<QueueEntry> queue;

        public NowBlock(List<Item> current, Item next, List<QueueEntry> queue) {
            this.current = current == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(current));
            this.next = next;
            this.queue = queue == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(queue));
        }
    }

    public static final class Document {
        public final NowBlock now;
        public final List<Item> items;
        public final List<TimelineEvent> timeline;
        public final Stats stats;

        public Document(NowBlock now, List<Item> items, List<TimelineEvent> timeline, Stats stats) {
            this.now = now;
            this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(items));
            this.timeline = timeline == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(timeline));
            this.stats = stats;
        }
    }
}
