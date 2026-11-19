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
        assertEquals(3, foundation.items.size());
        assertEquals("建築與碰撞改成清單資料驅動", foundation.items.get(0).text);
        assertTrue(foundation.items.get(0).done);
        assertEquals(3, foundation.itemsDone);
        assertEquals(3, foundation.itemsTotal);

        RoadmapParser.Phase upper = doc.phases.get(1);
        assertEquals(Integer.valueOf(9), upper.phase);
        assertTrue(upper.done);

        RoadmapParser.Phase current = doc.phases.get(2);
        assertEquals(Integer.valueOf(4), current.phase);
        assertEquals("進屋系統", current.title);
        assertEquals(false, current.done);
        assertEquals(2, current.items.size());
        assertEquals("走到門口按 E", current.items.get(0).text);
        assertEquals(false, current.items.get(0).done);
        assertEquals(0, current.itemsDone);
        assertEquals(2, current.itemsTotal);

        RoadmapParser.Phase extra = doc.phases.get(3);
        assertNull(extra.phase);
        assertEquals("穿插", extra.title);
        assertEquals(false, extra.done);
        assertEquals(2, extra.items.size());
        assertEquals("施工告示牌", extra.items.get(0).text);
    }

    @Test
    public void parsesIndentedSubItemsAndProgressForActivePhase() {
        String body = "## 分期\n"
                + "- [ ] **Phase 5 博弈島室內**：21點、拉霸機、輪盤、幸運轉盤\n"
                + "  - [x] 21點已在大廳可玩\n"
                + "  - [ ] 拉霸機 #120\n"
                + "- [x] **Phase 3 群島地基**：地基\n";
        RoadmapParser.Document doc = RoadmapParser.parse(body);
        assertEquals(Integer.valueOf(5), doc.current);
        RoadmapParser.Phase five = doc.phases.get(0);
        assertEquals(Integer.valueOf(5), five.phase);
        assertEquals(1, five.itemsDone);
        assertEquals(4, five.itemsTotal);
        assertEquals("21點已在大廳可玩。", five.progressNote);
        assertEquals(4, five.items.size());
        assertTrue(five.items.get(0).done);
        assertEquals(Integer.valueOf(120), five.items.get(1).issue);
    }

    @Test
    public void groupDoneWhenAllSubItemsCheckedEvenIfParentUnchecked() {
        String body = "## 分期\n"
                + "- [ ] **穿插**：告示牌、寵物\n"
                + "  - [x] 施工告示牌\n"
                + "  - [x] 跟班寵物\n";
        RoadmapParser.Phase extra = RoadmapParser.parse(body).phases.get(0);
        assertEquals(false, extra.done);
        assertEquals(2, extra.itemsDone);
        assertEquals(2, extra.itemsTotal);
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
