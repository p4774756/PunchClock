package com.example.server.store;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RoadmapParserTest {

    private static final String SAMPLE = ""
            + "## 目的\n"
            + "已完成：Phase 1 探索模式、Phase 2 賭場 MVP。\n"
            + "- [x] **Phase 1 不該算**：這行在分期以外\n"
            + "\n"
            + "## 分期\n"
            + "- [x] **Phase 3 群島地基**：建築與碰撞改成清單資料驅動、加橋與三座島外觀、*milu 上島*\n"
            + "- [X] **Phase 9 大小寫**：已勾\n"
            + "- [ ] **Phase 4 進屋系統**：走到門口按 E、三棟先共用\n"
            + "- [ ] 這行沒有粗體，略過\n"
            + "- [ ] **壞掉的沒有冒號** 略過\n"
            + "- [ ] **穿插**：施工告示牌、*跟班寵物*、  \n"
            + "\n"
            + "## 參考\n"
            + "- [ ] **Phase 8 不該算**：參考區的勾選\n";

    @Test
    public void parsesCheckedCaseAndUnnumberedPhases() {
        RoadmapParser.Document doc = RoadmapParser.parse(SAMPLE);
        assertEquals("已完成：Phase 1 探索模式、Phase 2 賭場 MVP。", doc.done);
        assertEquals(Integer.valueOf(4), doc.current);
        assertEquals(4, doc.phases.size());

        RoadmapParser.Phase foundation = doc.phases.get(0);
        assertEquals(Integer.valueOf(3), foundation.phase);
        assertEquals("群島地基", foundation.title);
        assertTrue(foundation.done);
        assertEquals(List.of("建築與碰撞改成清單資料驅動", "加橋與三座島外觀", "milu 上島"), foundation.items);

        RoadmapParser.Phase upper = doc.phases.get(1);
        assertEquals(Integer.valueOf(9), upper.phase);
        assertTrue(upper.done);

        RoadmapParser.Phase current = doc.phases.get(2);
        assertEquals(Integer.valueOf(4), current.phase);
        assertEquals("進屋系統", current.title);
        assertEquals(false, current.done);
        assertEquals(List.of("走到門口按 E", "三棟先共用"), current.items);

        RoadmapParser.Phase extra = doc.phases.get(3);
        assertNull(extra.phase);
        assertEquals("穿插", extra.title);
        assertEquals(false, extra.done);
        assertEquals(List.of("施工告示牌", "跟班寵物"), extra.items);
    }

    @Test
    public void currentIsNullWhenEveryNumberedPhaseIsDone() {
        String body = "## 分期\n"
                + "- [x] **Phase 3 群島地基**：地基\n"
                + "- [ ] **穿插**：告示牌\n";
        RoadmapParser.Document doc = RoadmapParser.parse(body);
        assertNull(doc.current);
        assertEquals(2, doc.phases.size());
        assertNull(doc.done);
    }

    @Test
    public void garbageDoesNotFailTheDocument() {
        RoadmapParser.Document doc = RoadmapParser.parse("不是分期\n- [ ] **Phase 1 幽靈**：不該出現\n");
        assertTrue(doc.phases.isEmpty());
        assertNull(doc.current);
        assertNull(doc.done);

        doc = RoadmapParser.parse(null);
        assertTrue(doc.phases.isEmpty());
    }
}
