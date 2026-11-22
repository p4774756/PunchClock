package com.example.server.store;

import com.example.server.store.DevStatusModels.PullRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 把 GitHub milestone／issue／PR 對照成公開路線圖。 */
public final class RoadmapMilestoneMapper {

    private static final Pattern PHASE_TITLE = Pattern.compile("^第\\s*(\\d+)\\s*期\\s*(.*)$");
    private static final int ROADMAP_ISSUE = 36;

    private RoadmapMilestoneMapper() {
    }

    public static final class MilestoneRow {
        public final int number;
        public final String title;
        public final int openCount;
        public final int closedCount;

        public MilestoneRow(int number, String title, int openCount, int closedCount) {
            this.number = number;
            this.title = title == null ? "" : title.trim();
            this.openCount = openCount;
            this.closedCount = closedCount;
        }
    }

    public static final class IssueRow {
        public final int number;
        public final String title;
        public final boolean open;

        public IssueRow(int number, String title, boolean open) {
            this.number = number;
            this.title = title == null ? "" : title;
            this.open = open;
        }
    }

    public static RoadmapModels.Document build(String repo, List<MilestoneRow> milestones,
                                                Map<Integer, List<IssueRow>> issuesByMilestone,
                                                List<PullRow> pulls) {
        Map<Integer, List<PullRow>> byIssue = indexPullsByIssue(pulls);
        List<PhaseDraft> drafts = new ArrayList<>();
        for (MilestoneRow milestone : milestones) {
            ParsedTitle parsed = parseTitle(milestone.title);
            if (parsed == null) {
                continue;
            }
            List<IssueRow> issues = issuesByMilestone.getOrDefault(milestone.number, Collections.emptyList());
            List<RoadmapModels.Item> items = new ArrayList<>();
            int done = 0;
            for (IssueRow issue : issues) {
                if (issue.number == ROADMAP_ISSUE) {
                    continue;
                }
                PullRow pull = DevStatusMapper.pickPull(issue.number, pulls, byIssue);
                Integer pr = pull == null ? null : pull.number;
                boolean closed = !issue.open;
                if (closed) {
                    done++;
                }
                items.add(new RoadmapModels.Item(issue.title, closed, issue.number, pr));
            }
            items.sort(ITEM_ORDER);
            int total = items.size();
            boolean phaseDone = total > 0 && done >= total;
            drafts.add(new PhaseDraft(
                    parsed.phase,
                    parsed.title,
                    phaseDone,
                    Collections.unmodifiableList(items),
                    done,
                    total
            ));
        }
        drafts.sort(Comparator.comparingInt(a -> a.phase));
        List<RoadmapModels.Phase> phases = new ArrayList<>();
        for (PhaseDraft draft : drafts) {
            phases.add(new RoadmapModels.Phase(
                    draft.phase,
                    draft.title,
                    draft.done,
                    draft.items,
                    draft.itemsDone,
                    draft.itemsTotal,
                    null
            ));
        }
        Integer current = null;
        for (RoadmapModels.Phase phase : phases) {
            if (phase.phase != null && !phase.done) {
                current = phase.phase;
                break;
            }
        }
        return new RoadmapModels.Document(current, Collections.unmodifiableList(phases), null);
    }

    private static final Comparator<RoadmapModels.Item> ITEM_ORDER = (a, b) -> {
        if (a.done != b.done) {
            return a.done ? 1 : -1;
        }
        int ai = a.issue == null ? Integer.MAX_VALUE : a.issue;
        int bi = b.issue == null ? Integer.MAX_VALUE : b.issue;
        return Integer.compare(ai, bi);
    };

    private static Map<Integer, List<PullRow>> indexPullsByIssue(List<PullRow> pulls) {
        Map<Integer, List<PullRow>> byIssue = new HashMap<>();
        if (pulls == null) {
            return byIssue;
        }
        for (PullRow pull : pulls) {
            if (pull.closesIssues == null) {
                continue;
            }
            for (Integer closes : pull.closesIssues) {
                byIssue.computeIfAbsent(closes, k -> new ArrayList<>()).add(pull);
            }
        }
        return byIssue;
    }

    static ParsedTitle parseTitle(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        Matcher m = PHASE_TITLE.matcher(title.trim());
        if (!m.matches()) {
            return null;
        }
        int phase = Integer.parseInt(m.group(1));
        String name = m.group(2) == null ? "" : m.group(2).trim();
        return new ParsedTitle(phase, name);
    }

    static final class ParsedTitle {
        final int phase;
        final String title;

        ParsedTitle(int phase, String title) {
            this.phase = phase;
            this.title = title;
        }
    }

    private static final class PhaseDraft {
        final int phase;
        final String title;
        final boolean done;
        final List<RoadmapModels.Item> items;
        final int itemsDone;
        final int itemsTotal;

        PhaseDraft(int phase, String title, boolean done, List<RoadmapModels.Item> items,
                   int itemsDone, int itemsTotal) {
            this.phase = phase;
            this.title = title;
            this.done = done;
            this.items = items;
            this.itemsDone = itemsDone;
            this.itemsTotal = itemsTotal;
        }
    }
}