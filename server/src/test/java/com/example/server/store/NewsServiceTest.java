package com.example.server.store;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NewsServiceTest {

    private static final String SAMPLE_RSS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel>"
                    + "<item><title>股市震盪加劇 - 經濟日報</title><link>https://news.google.com/a</link>"
                    + "<pubDate>Sun, 23 Nov 2025 08:00:00 GMT</pubDate>"
                    + "<source>經濟日報</source></item>"
                    + "<item><title>AI 新工具發表 - 科技新報</title><link>https://news.google.com/b</link>"
                    + "<pubDate>Sun, 23 Nov 2025 07:00:00 GMT</pubDate>"
                    + "<source>科技新報</source></item>"
                    + "</channel></rss>";

  private static NewsModels.Feed feedFromSample() {
        return new NewsModels.Feed(NewsRssParser.parse(SAMPLE_RSS));
    }

    @Test
    public void parserStripsSourceSuffixAndDedupes() {
        String xml = "<?xml version=\"1.0\"?><rss><channel>"
                + "<item><title>股市震盪加劇 - 經濟日報</title><link>https://news.google.com/a</link>"
                + "<source>經濟日報</source></item>"
                + "<item><title>股市震盪加劇 - 經濟日報</title><link>https://news.google.com/c</link>"
                + "<source>經濟日報</source></item>"
                + "<item><title>AI 新工具 - 科技新報</title><link>https://news.google.com/b</link>"
                + "<source>科技新報</source></item>"
                + "</channel></rss>";
        var merged = NewsRssParser.dedupeAndLimit(NewsRssParser.parse(xml), NewsService.MAX_ITEMS);
        assertEquals(2, merged.size());
        assertEquals("股市震盪加劇", merged.get(0).title);
        assertEquals("經濟日報", merged.get(0).source);
        assertEquals("https://news.google.com/a", merged.get(0).url);
        assertFalse(merged.get(0).title.contains(" - "));
    }

    @Test
    public void cachesForFifteenMinutesThenRefetches() {
        AtomicInteger calls = new AtomicInteger();
        NewsService service = new NewsService(() -> {
            calls.incrementAndGet();
            return feedFromSample();
        });

        NewsService.View first = service.get(1_000L);
        assertFalse(first.stale);
        assertEquals(2, first.items.size());
        assertEquals(1_000L, first.fetchedAt);
        assertEquals(1, calls.get());

        NewsService.View cached = service.get(1_000L + NewsService.TTL_MS - 1);
        assertFalse(cached.stale);
        assertEquals(1, calls.get());

        service.get(1_000L + NewsService.TTL_MS);
        assertEquals(2, calls.get());
    }

    @Test
    public void failureKeepsLastSuccessAndMarksStale() {
        AtomicInteger calls = new AtomicInteger();
        NewsService service = new NewsService(() -> {
            if (calls.incrementAndGet() == 1) {
                return feedFromSample();
            }
            throw new IOException("429");
        });

        assertEquals(2, service.get(5_000L).items.size());
        NewsService.View stale = service.get(5_000L + NewsService.TTL_MS);
        assertTrue(stale.stale);
        assertEquals(2, stale.items.size());
        assertEquals(5_000L, stale.fetchedAt);
    }

    @Test
    public void firstFailureReturnsMessage() {
        NewsService service = new NewsService(() -> {
            throw new IOException("down");
        });

        NewsService.View view = service.get(9L);
        assertFalse(view.stale);
        assertTrue(view.items.isEmpty());
        assertEquals(NewsService.UNAVAILABLE, view.message);
    }

    @Test
    public void failureBackoffDoesNotCallFetcherAgain() {
        AtomicInteger calls = new AtomicInteger();
        NewsService service = new NewsService(() -> {
            calls.incrementAndGet();
            throw new IOException("429");
        });

        assertEquals(NewsService.UNAVAILABLE, service.get(1_000L).message);
        assertEquals(1, calls.get());
        service.get(1_000L + NewsService.FAILURE_BACKOFF_MS - 1);
        assertEquals(1, calls.get());
        service.get(1_000L + NewsService.FAILURE_BACKOFF_MS);
        assertEquals(2, calls.get());
    }

    @Test
    public void dedupeRespectsLimit() {
        var items = Arrays.asList(
                new NewsModels.Item("甲", "A", "", "https://a"),
                new NewsModels.Item("乙", "B", "", "https://b"),
                new NewsModels.Item("甲", "A", "", "https://c")
        );
        var out = NewsRssParser.dedupeAndLimit(items, 10);
        assertEquals(2, out.size());
        var capped = NewsRssParser.dedupeAndLimit(items, 1);
        assertEquals(1, capped.size());
    }

    @Test
    public void parseDoesNotExposeDescriptionBody() {
        String xml = "<?xml version=\"1.0\"?><rss><channel><item>"
                + "<title>標題 - 來源</title><link>https://x</link>"
                + "<description><![CDATA[全文不應出現]]></description></item></channel></rss>";
        var items = NewsRssParser.parse(xml);
        assertEquals(1, items.size());
        assertEquals("標題", items.get(0).title);
    }
}