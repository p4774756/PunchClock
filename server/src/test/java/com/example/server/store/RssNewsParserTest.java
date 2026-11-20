package com.example.server.store;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RssNewsParserTest {

    private static final String SAMPLE = "<?xml version=\"1.0\"?><rss><channel>"
            + "<item><title>科技新創<strong>發表</strong>新產品 - 中央社</title></item>"
            + "<item><title>選舉民調公布</title></item>"
            + "<item><title>明日天氣預報</title></item>"
            + "</channel></rss>";

    @Test
    public void parsesStripsAndFilters() throws IOException {
        List<String> lines = RssNewsParser.parseHeadlinesFromString(SAMPLE, 5);
        assertEquals(2, lines.size());
        assertEquals("科技新創發表新產品", lines.get(0));
        assertEquals("明日天氣預報", lines.get(1));
    }

    @Test
    public void truncatesLongTitles() throws IOException {
        String longTitle = "這是一條超級無敵霹靂長的新聞標題用來測試截斷與省略號還要更長一點才會超過二十八字";
        String xml = "<?xml version=\"1.0\"?><rss><channel><item><title>" + longTitle + "</title></item></channel></rss>";
        List<String> lines = RssNewsParser.parseHeadlinesFromString(xml, 1);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).length() <= 28);
        assertTrue(lines.get(0).endsWith("…"));
    }

    @Test
    public void blocksMaliciousXmlWithDoctype() {
        String evil = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<rss><channel><item><title>&xxe;</title></item></channel></rss>";
        try {
            RssNewsParser.parseHeadlines(evil.getBytes(StandardCharsets.UTF_8), 1);
            assertTrue("DOCTYPE should be rejected", false);
        } catch (IOException ex) {
            assertTrue(ex.getMessage().contains("rss"));
        }
    }

    @Test
    public void truncateBodyCapsSize() {
        byte[] huge = new byte[NewsFilterConstants.MAX_RESPONSE_BYTES + 1024];
        byte[] cut = RssNewsParser.truncateBody(huge);
        assertEquals(NewsFilterConstants.MAX_RESPONSE_BYTES, cut.length);
    }

    @Test
    public void cleanTitleStripsEntities() {
        assertEquals("A&B", RssNewsParser.cleanTitle("A&amp;B"));
    }

    @Test
    public void blockedKeywords() {
        assertTrue(NewsFilterConstants.isBlockedTitle("今日選舉開票"));
        assertFalse(NewsFilterConstants.isBlockedTitle("可愛貓咪影片"));
    }

    /** 第 1 輪審核實際漏網的政治標題，以及同類語彙。 */
    @Test
    public void blocksPoliticalHeadlinesFromReview() {
        String[] blocked = {
            "普發2萬協商破局 白憂違反財政紀律堅持反對 選前恐難三讀",
            "馬政府時期檔案重新公開",
            "蔣萬安視察捷運工程",
            "立法院黨團協商破局",
            "行政院通過明年度預算案",
            "藍白合作再談 綠營批評",
            "市長補選民調出爐",
            "罷免案投開票結果揭曉",
            "兩岸情勢升溫 國台辦回應",
            "朝野協商修法卡關",
            "造謠故宮設光電板！經部還原普查公文：「文資」一律排除",
            "陳亭妃為光電板議題提告中天記者 檢警火速偵辦 謝龍介說",
            "沈伯洋直播7.5萬人在線！為考生公開唸書訣竅超實用　網讚",
            "APEC記者會 中國媒體追問「台灣有事」 高市早苗：日方立場不變",
            "視障婦挺洋流遭復康巴士司機辱腦殘 伊甸基金會道歉祭重懲",
            "黃復興發訊給秦慧珠：多次勸告未見改善不支持參與競選",
            "亞股恐創2020年來最慘1週！台股暴跌近18％ 創35年紀錄",
            "美出口管制新規正式生效！台積電出手封殺中國晶片",
            "台股跌到懷疑人生！張錫逆勢喊「甜甜價」：外資明年會回頭"
        };
        for (String title : blocked) {
            assertTrue("應被擋下：" + title, NewsFilterConstants.isBlockedTitle(title));
        }
    }

    @Test
    public void keepsNeutralLifestyleHeadlines() {
        String[] allowed = {
            "台北市立動物園迎接新生小水獺",
            "科技新創發表新產品",
            "明日天氣預報",
            "社區貓咪咖啡廳開幕",
            "台北捷運新增夜間班次",
            "乾冷空氣抵達 氣象專家：明晨更凍",
            "高雄演唱會8.7萬人潮90分鐘完成疏散",
            "航海家一號距地球「一光日」 升空近半世紀仍持續觀測",
            "聯名耳機登場！Sony 復刻初代隨身聽"
        };
        for (String title : allowed) {
            assertFalse("不該被擋：" + title, NewsFilterConstants.isBlockedTitle(title));
        }
    }

    /** 關鍵字落在 28 字之後也要擋掉，不能因為先截斷而漏網。 */
    @Test
    public void blocksKeywordBeyondTruncationLength() throws IOException {
        String xml = "<?xml version=\"1.0\"?><rss><channel><item><title>"
                + "社區活動中心今天舉辦熱鬧的園遊會吸引許多親子家庭前往同樂現場還有市長到場致詞"
                + "</title></item></channel></rss>";
        assertTrue(RssNewsParser.parseHeadlinesFromString(xml, 5).isEmpty());
    }

    @Test
    public void politicalHeadlinesAreDroppedDuringParse() throws IOException {
        String xml = "<?xml version=\"1.0\"?><rss><channel>"
                + "<item><title>普發2萬協商破局 白憂違反財政紀律堅持反對</title></item>"
                + "<item><title>蔣萬安出席典禮</title></item>"
                + "<item><title>社區貓咪咖啡廳開幕</title></item>"
                + "</channel></rss>";
        List<String> lines = RssNewsParser.parseHeadlinesFromString(xml, 5);
        assertEquals(1, lines.size());
        assertEquals("社區貓咪咖啡廳開幕", lines.get(0));
    }
}