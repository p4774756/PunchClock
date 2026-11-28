package com.example.server.store;

import com.example.server.store.DevStatusModels.Item;
import com.example.server.store.DevStatusModels.PullRow;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class OfficeActiveStageTest {

    private static PullRow openPr(int n, int issue) {
        return new PullRow(n, "pr", true, false, false, 0, 0, null, null,
                new HashSet<>(Collections.singletonList(issue)));
    }

    private static PullRow conflictingPr(int n, int issue) {
        return new PullRow(n, "pr", true, false, false, 0, 0, null, null,
                new HashSet<>(Collections.singletonList(issue)), true);
    }

    private static DevMachineStore.RunEntry run(int issue, int round, String role, String model, String verdict,
                                                String stage) {
        return new DevMachineStore.RunEntry(issue, round, "ai/issue-" + issue, 1L, null, null,
                role, model, verdict, stage, null, null);
    }

    @Test
    public void heartbeatStageLiteralWins() {
        DevMachineStore.RunEntry run = run(1, 0, "dev", null, null, OfficeActiveStage.MERGE);
        assertEquals(OfficeActiveStage.MERGE,
                OfficeActiveStage.forRun(run, false, null, null, openPr(9, 1)));
    }

    @Test
    public void reviewRunIsReviewStage() {
        DevMachineStore.RunEntry run = run(2, 2, "review", "claude-opus-5-thinking-high", null, null);
        assertEquals(OfficeActiveStage.REVIEW,
                OfficeActiveStage.forRun(run, false, null, null, openPr(10, 2)));
    }

    @Test
    public void devAgentIsDeveloping() {
        DevMachineStore.RunEntry run = run(3, 2, "dev", "composer-2.5", null, null);
        assertEquals(OfficeActiveStage.DEV,
                OfficeActiveStage.forRun(run, true, null, "dev", null));
    }

    @Test
    public void failVerdictWhileDevAgentIsFixing() {
        DevMachineStore.RunEntry run = run(4, 3, "dev", "composer-2.5", "FAIL", null);
        assertEquals(OfficeActiveStage.FIX,
                OfficeActiveStage.forRun(run, true, "FAIL", "dev", openPr(11, 4)));
    }

    @Test
    public void chainWaitingOpenPrIsWaitCi() {
        DevMachineStore.RunEntry run = run(5, 0, "dev", null, null, null);
        assertEquals(OfficeActiveStage.WAIT_CI,
                OfficeActiveStage.forRun(run, false, null, null, openPr(12, 5)));
    }

    @Test
    public void passVerdictWithPrIsMerging() {
        DevMachineStore.RunEntry run = run(6, 0, "dev", null, "PASS", null);
        assertEquals(OfficeActiveStage.MERGE,
                OfficeActiveStage.forRun(run, false, "PASS", null, openPr(13, 6)));
    }

    @Test
    public void githubItemWithOpenPrIsWaitCi() {
        Item item = new Item("i7", 7, 14, "title", DevStatusModels.STATUS_REVIEW, 1L, "u", "u");
        assertEquals(OfficeActiveStage.WAIT_CI, OfficeActiveStage.forGithubItem(item, openPr(14, 7)));
    }

    @Test
    public void conflictingOpenPrShowsConflictNotWaitCi() {
        DevMachineStore.RunEntry run = run(6, 0, "dev", null, null, null);
        assertEquals(OfficeActiveStage.CONFLICT,
                OfficeActiveStage.forRun(run, false, null, null, conflictingPr(13, 6)));
        assertEquals(OfficeActiveStage.CONFLICT,
                OfficeActiveStage.forMachineFields(false, null, null, conflictingPr(13, 6)));
        assertEquals(OfficeActiveStage.CONFLICT,
                OfficeActiveStage.forGithubItem(null, conflictingPr(13, 6)));
    }

    @Test
    public void conflictBeatsPassVerdictMerge() {
        DevMachineStore.RunEntry run = run(7, 1, "dev", null, "PASS", null);
        assertEquals(OfficeActiveStage.CONFLICT,
                OfficeActiveStage.forRun(run, false, "PASS", null, conflictingPr(14, 7)));
    }

    @Test
    public void conflictOverridesLiteralWaitCiButNotReview() {
        assertEquals(OfficeActiveStage.CONFLICT,
                OfficeActiveStage.forRun(run(8, 0, "dev", null, null, OfficeActiveStage.WAIT_CI),
                        false, null, null, conflictingPr(15, 8)));
        assertEquals(OfficeActiveStage.REVIEW,
                OfficeActiveStage.forRun(run(9, 1, "review", null, null, OfficeActiveStage.REVIEW),
                        false, null, null, conflictingPr(16, 9)));
    }

    @Test
    public void agentFixingConflictStaysDeveloping() {
        DevMachineStore.RunEntry run = run(10, 2, "dev", "composer-2.5", null, null);
        assertEquals(OfficeActiveStage.DEV,
                OfficeActiveStage.forRun(run, true, null, "dev", conflictingPr(17, 10)));
    }

    @Test
    public void nonConflictingOpenPrStillWaitCi() {
        assertEquals(OfficeActiveStage.WAIT_CI,
                OfficeActiveStage.forRun(run(11, 0, "dev", null, null, null), false, null, null, openPr(18, 11)));
    }
}
