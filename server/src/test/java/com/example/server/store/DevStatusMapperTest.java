package com.example.server.store;

import com.example.server.store.DevStatusModels.Document;
import com.example.server.store.DevStatusModels.IssueRow;
import com.example.server.store.DevStatusModels.PullRow;
import com.example.server.store.DevStatusModels.Snapshot;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DevStatusMapperTest {

    @Test
    public void queueOrderTakesSmallestPositive() {
        assertEquals(Integer.valueOf(2), DevStatusMapper.queueOrder(Arrays.asList("bug", "queue:5", "queue:2")));
        assertNull(DevStatusMapper.queueOrder(Arrays.asList("queue:0", "ready")));
        assertNull(DevStatusMapper.queueOrder(Collections.emptyList()));
    }

    @Test
    public void parseClosesAndBranch() {
        Set<Integer> closes = DevStatusMapper.parseCloses("Closes #87\nfixes #12\nResolves #3");
        assertTrue(closes.contains(87));
        assertTrue(closes.contains(12));
        assertTrue(closes.contains(3));
        assertEquals(Integer.valueOf(78), DevStatusMapper.parseBranchIssue("refs/heads/ai/issue-78"));
        assertNull(DevStatusMapper.parseBranchIssue("refs/heads/cursor/foo"));
    }

    @Test
    public void statusMapping() {
        IssueRow open = issue(50, true, Collections.emptyList());
        PullRow review = pull(90, true, false, false, set(50));
        PullRow draft = pull(91, true, true, false, set(50));
        PullRow merged = pull(92, false, false, true, set(50));

        assertEquals(DevStatusModels.STATUS_REVIEW, DevStatusMapper.statusFor(open, review, false));
        assertEquals(DevStatusModels.STATUS_WORKING, DevStatusMapper.statusFor(open, draft, false));
        assertEquals(DevStatusModels.STATUS_WORKING, DevStatusMapper.statusFor(open, null, true));
        assertEquals(DevStatusModels.STATUS_QUEUED, DevStatusMapper.statusFor(open, null, false));
        assertEquals(DevStatusModels.STATUS_MERGED, DevStatusMapper.statusFor(issue(50, false, Collections.emptyList()), merged, true));
        assertEquals(DevStatusModels.STATUS_ROADMAP, DevStatusMapper.statusFor(issue(36, true, Collections.emptyList()), null, false));
    }

    @Test
    public void closedIssueDraftPrIsNotWorking() {
        IssueRow closed = issue(307, false, Collections.emptyList());
        PullRow draft = pull(400, true, true, false, set(307));
        assertEquals(DevStatusModels.STATUS_MERGED, DevStatusMapper.statusFor(closed, draft, true));
    }

    @Test
    public void queueCurrentAndNextPreferWorkingOverBareQueued() {
        IssueRow q1 = issue(10, true, Arrays.asList("queue:1"));
        IssueRow q2 = issue(20, true, Arrays.asList("queue:2"));
        IssueRow q3 = issue(30, true, Arrays.asList("queue:3"));
        PullRow openPr = pull(200, true, false, false, set(20));
        Snapshot snap = new Snapshot(
                Arrays.asList(q1, q2, q3),
                Collections.singletonList(openPr),
                Collections.emptySet()
        );
        Document doc = DevStatusMapper.map(snap, "p4774756/PunchClock", 1_700_000_000_000L);
        assertEquals(1, doc.now.current.size());
        assertEquals(20, doc.now.current.get(0).issue);
        assertEquals(DevStatusModels.STATUS_REVIEW, doc.now.current.get(0).status);
        assertEquals(10, doc.now.next.issue);
        assertEquals(2, doc.now.queue.size());
        assertEquals(10, doc.now.queue.get(0).item.issue);
        assertEquals(30, doc.now.queue.get(1).item.issue);
    }

    @Test
    public void twoParallelInProgressNextIsFirstQueuedOnly() {
        IssueRow q1 = issue(10, true, Arrays.asList("queue:1"));
        IssueRow q2 = issue(20, true, Arrays.asList("queue:2"));
        IssueRow q3 = issue(30, true, Arrays.asList("queue:3"));
        PullRow draft = pull(201, true, true, false, set(10));
        PullRow review = pull(202, true, false, false, set(20));
        Snapshot snap = new Snapshot(
                Arrays.asList(q1, q2, q3),
                Arrays.asList(draft, review),
                set(10)
        );
        Document doc = DevStatusMapper.map(snap, "p4774756/PunchClock", 1_700_000_000_000L);
        assertEquals(Arrays.asList(10, 20), doc.now.current.stream().map(i -> i.issue).collect(Collectors.toList()));
        assertEquals(30, doc.now.next.issue);
        assertEquals(1, doc.now.queue.size());
        assertEquals(30, doc.now.queue.get(0).item.issue);
    }

    @Test
    public void inProgressWithoutQueueLabelAppearsInCurrent() {
        IssueRow q1 = issue(10, true, Arrays.asList("queue:1"));
        IssueRow branchOnly = issue(40, true, Collections.emptyList());
        Snapshot snap = new Snapshot(
                Arrays.asList(q1, branchOnly),
                Collections.emptyList(),
                set(40)
        );
        Document doc = DevStatusMapper.map(snap, "p4774756/PunchClock", 1_700_000_000_000L);
        assertEquals(Collections.singletonList(40), doc.now.current.stream().map(i -> i.issue).collect(Collectors.toList()));
        assertEquals(10, doc.now.next.issue);
        assertEquals(1, doc.now.queue.size());
    }

    @Test
    public void queuedWithBranchIsWorkingInCurrentNotQueue() {
        IssueRow q341 = issue(341, true, Arrays.asList("queue:9"));
        Snapshot snap = new Snapshot(
                Collections.singletonList(q341),
                Collections.emptyList(),
                set(341)
        );
        Document doc = DevStatusMapper.map(snap, "p4774756/PunchClock", 1_700_000_000_000L);
        assertEquals(1, doc.now.current.size());
        assertEquals(341, doc.now.current.get(0).issue);
        assertEquals(DevStatusModels.STATUS_WORKING, doc.now.current.get(0).status);
        assertTrue(doc.now.queue.isEmpty());
        assertNull(doc.now.next);
    }

    @Test
    public void closedLeftoverBranchWithoutMergedPrIsNotWorking() {
        IssueRow closed = issue(307, false, Collections.emptyList());
        Snapshot snap = new Snapshot(
                Collections.singletonList(closed),
                Collections.emptyList(),
                set(307)
        );
        Document doc = DevStatusMapper.map(snap, "p4774756/PunchClock", 1_700_000_000_000L);
        assertEquals(1, doc.items.size());
        assertEquals(DevStatusModels.STATUS_MERGED, doc.items.get(0).status);
        assertTrue(doc.now.queue.isEmpty());
        assertTrue(doc.now.current.isEmpty());
    }

    @Test
    public void inProgressSortsQueuedFirstThenIssueNumber() {
        IssueRow q50 = issue(50, true, Arrays.asList("queue:2"));
        IssueRow q80 = issue(80, true, Collections.emptyList());
        IssueRow q40 = issue(40, true, Collections.emptyList());
        IssueRow q10 = issue(10, true, Arrays.asList("queue:1"));
        Snapshot snap = new Snapshot(
                Arrays.asList(q50, q80, q40, q10),
                Collections.emptyList(),
                set(50, 80, 40)
        );
        Document doc = DevStatusMapper.map(snap, "p4774756/PunchClock", 1_700_000_000_000L);
        assertEquals(Arrays.asList(50, 40, 80), doc.now.current.stream().map(i -> i.issue).collect(Collectors.toList()));
        assertEquals(10, doc.now.next.issue);
        assertEquals(1, doc.now.queue.size());
    }

    @Test
    public void queueNextIsSmallestWhenNothingInProgress() {
        IssueRow q1 = issue(10, true, Arrays.asList("queue:1"));
        IssueRow q2 = issue(20, true, Arrays.asList("queue:2"));
        Snapshot snap = new Snapshot(Arrays.asList(q1, q2), Collections.emptyList(), Collections.emptySet());
        Document doc = DevStatusMapper.map(snap, "p4774756/PunchClock", 1_700_000_000_000L);
        assertTrue(doc.now.current.isEmpty());
        assertEquals(10, doc.now.next.issue);
        assertEquals(DevStatusModels.STATUS_QUEUED, doc.now.next.status);
    }

    @Test
    public void mergedIssueLeftoverBranchIsNotWorking() {
        IssueRow closed = issue(81, false, Arrays.asList("queue:1"));
        PullRow merged = pull(86, false, false, true, set(81));
        Snapshot snap = new Snapshot(
                Collections.singletonList(closed),
                Collections.singletonList(merged),
                set(81)
        );
        Document doc = DevStatusMapper.map(snap, "p4774756/PunchClock", 1_700_000_000_000L);
        assertEquals(1, doc.items.size());
        assertEquals(DevStatusModels.STATUS_MERGED, doc.items.get(0).status);
        assertTrue(doc.now.queue.isEmpty());
        assertTrue(doc.now.current.isEmpty());
    }

    @Test
    public void pickLargestOpenPrOtherwiseMergedWins() {
        List<PullRow> pulls = Arrays.asList(
                pull(10, true, false, false, set(5)),
                pull(12, true, false, false, set(5)),
                pull(8, false, false, true, set(5))
        );
        assertEquals(8, DevStatusMapper.pickPull(5, pulls, index(pulls)).number);

        List<PullRow> openOnly = Arrays.asList(
                pull(10, true, false, false, set(5)),
                pull(12, true, false, false, set(5))
        );
        assertEquals(12, DevStatusMapper.pickPull(5, openOnly, index(openOnly)).number);
    }

    @Test
    public void weekStatsAndTimelineUseTaipeiWeek() {
        long weekStart = DevStatusMapper.weekStartTaipei(1_731_600_000_000L);
        IssueRow open = issue(40, true, Collections.emptyList());
        PullRow merged = new PullRow(99, "done", false, false, true,
                weekStart - 1000, weekStart + 1000, weekStart + 1000, weekStart + 1000, set(40));
        Snapshot snap = new Snapshot(
                Collections.singletonList(open),
                Collections.singletonList(merged),
                Collections.emptySet()
        );
        Document doc = DevStatusMapper.map(snap, "p4774756/PunchClock", weekStart + 86_400_000L);
        assertEquals(1, doc.stats.mergedThisWeek);
        assertEquals(1, doc.stats.openIssues);
        assertTrue(doc.timeline.stream().anyMatch(e -> "合併".equals(e.action)));
    }

    @Test
    public void timelineIsCapped() {
        long now = 2_000_000_000_000L;
        List<IssueRow> issues = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long at = now - i * 60_000L;
            issues.add(new IssueRow(100 + i, "t" + i, true, at, at, null, Collections.emptyList()));
        }
        Document doc = DevStatusMapper.map(
                new Snapshot(issues, Collections.emptyList(), Collections.emptySet()),
                "p4774756/PunchClock",
                now
        );
        assertEquals(DevStatusMapper.TIMELINE_LIMIT, doc.timeline.size());
        assertTrue(doc.timeline.get(0).at >= doc.timeline.get(doc.timeline.size() - 1).at);
    }

    private static IssueRow issue(int n, boolean open, List<String> labels) {
        return new IssueRow(n, "t" + n, open, 1_000L, 2_000L, open ? null : 3_000L, labels);
    }

    private static PullRow pull(int n, boolean open, boolean draft, boolean merged, Set<Integer> closes) {
        Long mergedAt = merged ? 4_000L : null;
        return new PullRow(n, "p" + n, open, draft, merged, 1_000L, 2_000L, mergedAt, open ? null : 5_000L, closes);
    }

    private static Set<Integer> set(Integer... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static java.util.Map<Integer, List<PullRow>> index(List<PullRow> pulls) {
        java.util.Map<Integer, List<PullRow>> map = new java.util.HashMap<>();
        for (PullRow pull : pulls) {
            for (Integer closes : pull.closesIssues) {
                map.computeIfAbsent(closes, k -> new java.util.ArrayList<>()).add(pull);
            }
        }
        return map;
    }
}