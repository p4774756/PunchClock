package com.example.server.store;

import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WorldServiceTest {

    private static final WorldService.WeatherSnapshot SAMPLE =
            new WorldService.WeatherSnapshot(
                    WeatherKind.RAIN, 21.0, 80, 15, 22, true, "open-meteo", false);

    @Test
    public void weatherCachedForTwentyMinutes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        WorldService service = new WorldService((() -> {
            calls.incrementAndGet();
            return SAMPLE;
        }), (() -> Collections.emptyList()));

        WorldService.WeatherSnapshot first = service.loadWeather(1_000L);
        assertEquals(WeatherKind.RAIN, first.kind);
        assertEquals(1, calls.get());

        WorldService.WeatherSnapshot second = service.loadWeather(1_000L + WorldService.WEATHER_TTL_MS - 1);
        assertEquals(WeatherKind.RAIN, second.kind);
        assertEquals(1, calls.get());

        service.loadWeather(1_000L + WorldService.WEATHER_TTL_MS);
        assertEquals(2, calls.get());
    }

    @Test
    public void weatherFailureReturnsStale() {
        AtomicInteger calls = new AtomicInteger();
        WorldService service = new WorldService((() -> {
            if (calls.incrementAndGet() == 1) {
                return SAMPLE;
            }
            throw new IOException("down");
        }), (() -> Collections.emptyList()));

        service.loadWeather(5_000L);
        WorldService.WeatherSnapshot stale = service.loadWeather(5_000L + WorldService.WEATHER_TTL_MS);
        assertTrue(stale.stale);
        assertEquals(WeatherKind.RAIN, stale.kind);
        assertEquals(5_000L, service.weatherFetchedAtForTests());
    }

    @Test
    public void weatherFailureWithoutCacheFallsBackClear() throws Exception {
        WorldService service = new WorldService((() -> {
            throw new IOException("down");
        }), (() -> Collections.emptyList()));

        WorldService.WeatherSnapshot snap = service.loadWeather(100L);
        assertEquals(WeatherKind.CLEAR, snap.kind);
        assertEquals("fallback", snap.source);
        assertFalse(snap.stale);
    }

    @Test
    public void newsCachedSixHoursAndFailureEmpty() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        WorldService service = new WorldService(
                (() -> SAMPLE),
                (() -> {
                    if (calls.incrementAndGet() == 1) {
                        return List.of(new WorldService.NewsHeadline("好消息"));
                    }
                    throw new IOException("rss");
                }));

        List<WorldService.NewsHeadline> first = service.loadNews(1_000L);
        assertEquals(1, first.size());
        assertEquals(1, calls.get());

        assertEquals(1, service.loadNews(1_000L + WorldService.NEWS_TTL_MS - 1).size());
        assertEquals(1, calls.get());

        List<WorldService.NewsHeadline> failed = service.loadNews(1_000L + WorldService.NEWS_TTL_MS);
        assertTrue(failed.isEmpty());
        assertEquals(2, calls.get());

        assertTrue(service.loadNews(1_000L + WorldService.NEWS_TTL_MS + 1000L).isEmpty());
    }

    @Test
    public void concurrentWeatherFetchOnlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        WorldService service = new WorldService((() -> {
            calls.incrementAndGet();
            try {
                start.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", ex);
            }
            return SAMPLE;
        }), (() -> Collections.emptyList()));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<?>[] futures = new Future[4];
            for (int i = 0; i < 4; i++) {
                futures[i] = pool.submit(() -> service.loadWeather(9_000L));
            }
            Thread.sleep(80);
            assertEquals(1, calls.get());
            start.countDown();
            for (Future<?> f : futures) {
                WorldService.WeatherSnapshot snap = (WorldService.WeatherSnapshot) f.get();
                assertEquals(WeatherKind.RAIN, snap.kind);
            }
            assertEquals(1, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void concurrentNewsFetchOnlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        WorldService service = new WorldService((() -> SAMPLE), (() -> {
            calls.incrementAndGet();
            try {
                start.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", ex);
            }
            return List.of(new WorldService.NewsHeadline("小水獺出生了"));
        }));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<?>[] futures = new Future[4];
            for (int i = 0; i < 4; i++) {
                futures[i] = pool.submit(() -> service.loadNews(7_000L));
            }
            Thread.sleep(80);
            assertEquals(1, calls.get());
            start.countDown();
            for (Future<?> f : futures) {
                @SuppressWarnings("unchecked")
                List<WorldService.NewsHeadline> got = (List<WorldService.NewsHeadline>) f.get();
                assertEquals(1, got.size());
            }
            assertEquals(1, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void recentSolarTermFallsBackToLastPassedTerm() {
        WorldCalendar.RecentSolarTerm today = WorldCalendar.recentSolarTermForDate("2026-11-07");
        assertEquals("立冬", today.name);
        assertTrue(today.isToday);

        WorldCalendar.RecentSolarTerm after = WorldCalendar.recentSolarTermForDate("2026-11-20");
        assertEquals("立冬", after.name);
        assertEquals("2026-11-07", after.date);
        assertFalse(after.isToday);

        WorldCalendar.RecentSolarTerm acrossYear = WorldCalendar.recentSolarTermForDate("2027-01-02");
        assertEquals("冬至", acrossYear.name);
        assertEquals("2026-12-22", acrossYear.date);
        assertFalse(acrossYear.isToday);

        assertNull(WorldCalendar.recentSolarTermForDate("2025-12-31"));
    }

    @Test
    public void calendarViewCarriesRecentSolarTerm() {
        long ms = java.time.ZonedDateTime.of(2026, 11, 20, 12, 0, 0, 0, WorldCalendar.TAIPEI)
                .toInstant().toEpochMilli();
        WorldCalendar.CalendarView view = WorldCalendar.calendarFor(ms);
        assertEquals("2026-11-20", view.date);
        assertNull(view.solarTerm);
        assertEquals("立冬", view.recentSolarTerm);
        assertFalse(view.solarTermIsToday);
    }

    @Test
    public void calendarMatchesFrontEndSamples() {
        assertEquals("芒種", WorldCalendar.solarTermForDate("2026-06-05"));
        assertEquals("dragon-boat", WorldCalendar.festivalForDate("2026-06-19"));
        assertEquals("mid-autumn", WorldCalendar.festivalForDate("2026-09-25"));
        assertEquals("new-year", WorldCalendar.festivalForDate("2027-02-06"));
        assertEquals("winter-solstice", WorldCalendar.festivalForDate("2028-12-21"));
    }
}