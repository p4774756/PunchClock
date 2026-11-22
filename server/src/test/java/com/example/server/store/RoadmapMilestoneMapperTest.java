package com.example.server.store;

import com.example.server.store.DevStatusModels.PullRow;
import com.example.server.store.RoadmapMilestoneMapper.IssueRow;
import com.example.server.store.RoadmapMilestoneMapper.MilestoneRow;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RoadmapMilestoneMapperTest {

    @Test
    public void parsePhaseTitle() {
        RoadmapMilestoneMapper.ParsedTitle t = RoadmapMilestoneMapper.parseTitle("第1期 虛擬辦公室");
        assertNotNull(t);
        assertEquals(1, t.phase);
        assertEquals("虛擬辦公室", t.title);
        assertNull(RoadmapMilestoneMapper.parseTitle("Backlog"));
    }

    @Test
    public void buildsPhasesWithPrAndCurrent() {
        List<MilestoneRow> milestones = Arrays.asList(
                new MilestoneRow(1, "第1期 虛擬辦公室", 1, 1),
                new MilestoneRow(2, "第2期 經絡研習室", 2, 0)
        );
        Map<Integer, List<IssueRow>> issues = new HashMap<>();
        issues.put(1, Arrays.asList(
                new IssueRow(36, "路線圖", true),
                new IssueRow(10, "已完成項", false),
                new IssueRow(11, "進行中項", true)
        ));
        issues.put(2, Collections.singletonList(new IssueRow(20, "研習室", true)));
        PullRow merged = new PullRow(
                99, "合併", false, false, true,
                0, 0, 0L, 0L,
                new LinkedHashSet<>(Collections.singletonList(10))
        );
        RoadmapModels.Document doc = RoadmapMilestoneMapper.build(
                "p4774756/PunchClock", milestones, issues, Collections.singletonList(merged));

        assertEquals(Integer.valueOf(1), doc.current);
        assertEquals(2, doc.phases.size());
        RoadmapModels.Phase phase1 = doc.phases.get(0);
        assertEquals(Integer.valueOf(1), phase1.phase);
        assertFalse(phase1.done);
        assertEquals(2, phase1.items.size());
        assertFalse(phase1.items.get(0).done);
        assertEquals(Integer.valueOf(11), phase1.items.get(0).issue);
        assertTrue(phase1.items.get(1).done);
        assertEquals(Integer.valueOf(10), phase1.items.get(1).issue);
        assertEquals(Integer.valueOf(99), phase1.items.get(1).pr);
    }
}