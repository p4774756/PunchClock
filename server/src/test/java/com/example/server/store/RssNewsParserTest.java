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
}