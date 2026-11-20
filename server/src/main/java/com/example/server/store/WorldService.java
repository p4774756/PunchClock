package com.example.server.store;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 組裝 /api/world/now：天氣 20 分、新聞 6 小時快取，失敗用 fallback。 */
public final class WorldService {

    public static final long WEATHER_TTL_MS = 20L * 60L * 1000L;
    public static final long NEWS_TTL_MS = 6L * 60L * 60L * 1000L;
    public static final ZoneId TAIPEI = WorldCalendar.TAIPEI;

    private static final DateTimeFormatter GENERATED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @FunctionalInterface
    public interface WeatherFetcher {
        WeatherSnapshot fetch() throws IOException;
    }

    @FunctionalInterface
    public interface NewsFetcher {
        List<NewsHeadline> fetch() throws IOException;
    }

    public static final class WeatherSnapshot {
        public final String kind;
        public final double tempC;
        public final int humidity;
        public final double windKmh;
        public final double gustKmh;
        public final boolean isDay;
        public final String source;
        public final boolean stale;

        public WeatherSnapshot(String kind, double tempC, int humidity, double windKmh, double gustKmh,
                               boolean isDay, String source, boolean stale) {
            this.kind = kind;
            this.tempC = tempC;
            this.humidity = humidity;
            this.windKmh = windKmh;
            this.gustKmh = gustKmh;
            this.isDay = isDay;
            this.source = source;
            this.stale = stale;
        }

        WeatherSnapshot withStale(boolean value) {
            return new WeatherSnapshot(kind, tempC, humidity, windKmh, gustKmh, isDay, source, value);
        }
    }

    public static final class NewsHeadline {
        public final String title;

        public NewsHeadline(String title) {
            this.title = title;
        }
    }

    public static final class WorldNow {
        public final boolean success;
        public final String generatedAt;
        public final WeatherSnapshot weather;
        public final WorldCalendar.CalendarView calendar;
        public final List<NewsHeadline> news;

        WorldNow(String generatedAt, WeatherSnapshot weather, WorldCalendar.CalendarView calendar,
                 List<NewsHeadline> news) {
            this.success = true;
            this.generatedAt = generatedAt;
            this.weather = weather;
            this.calendar = calendar;
            this.news = news;
        }
    }

    private final WeatherFetcher weatherFetcher;
    private final NewsFetcher newsFetcher;

    private WeatherSnapshot weatherCache;
    private long weatherFetchedAt;

    private List<NewsHeadline> newsCache = Collections.emptyList();
    private long newsFetchedAt = 0L;

    private final Object weatherLock = new Object();
    private volatile boolean weatherRefreshing;
    private final AtomicReference<RuntimeException> weatherRefreshError = new AtomicReference<>();

    private final Object newsLock = new Object();
    private volatile boolean newsRefreshing;

    public WorldService(WeatherFetcher weatherFetcher, NewsFetcher newsFetcher) {
        this.weatherFetcher = weatherFetcher;
        this.newsFetcher = newsFetcher;
    }

    public WorldNow getNow(long epochMillis) {
        String generatedAt = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis), TAIPEI).format(GENERATED_AT);
        WeatherSnapshot weather = loadWeather(epochMillis);
        List<NewsHeadline> news = loadNews(epochMillis);
        WorldCalendar.CalendarView calendar = WorldCalendar.calendarFor(epochMillis);
        return new WorldNow(generatedAt, weather, calendar, news);
    }

    WeatherSnapshot loadWeather(long now) {
        synchronized (weatherLock) {
            if (weatherCache != null && now - weatherFetchedAt < WEATHER_TTL_MS) {
                return weatherCache;
            }
            while (weatherRefreshing) {
                try {
                    weatherLock.wait(250L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (weatherCache != null && now - weatherFetchedAt < WEATHER_TTL_MS) {
                    return weatherCache;
                }
                RuntimeException err = weatherRefreshError.get();
                if (err != null && !weatherRefreshing) {
                    return failedWeather(now);
                }
            }
            if (weatherCache != null && now - weatherFetchedAt < WEATHER_TTL_MS) {
                return weatherCache;
            }
            weatherRefreshing = true;
            weatherRefreshError.set(null);
        }
        try {
            WeatherSnapshot fresh = weatherFetcher.fetch();
            synchronized (weatherLock) {
                weatherCache = fresh;
                weatherFetchedAt = now;
                weatherLock.notifyAll();
            }
            return fresh;
        } catch (IOException | RuntimeException ex) {
            synchronized (weatherLock) {
                weatherRefreshError.set(ex instanceof RuntimeException
                        ? (RuntimeException) ex
                        : new RuntimeException(ex));
                return failedWeather(now);
            }
        } finally {
            synchronized (weatherLock) {
                weatherRefreshing = false;
                weatherLock.notifyAll();
            }
        }
    }

    private WeatherSnapshot failedWeather(long now) {
        if (weatherCache != null) {
            return weatherCache.withStale(true);
        }
        return new WeatherSnapshot(
                WeatherKind.CLEAR, 24.0, 60, 8.0, 12.0, true, "fallback", false);
    }

    List<NewsHeadline> loadNews(long now) {
        synchronized (newsLock) {
            if (newsFetchedAt > 0L && now - newsFetchedAt < NEWS_TTL_MS) {
                return newsCache;
            }
            while (newsRefreshing) {
                try {
                    newsLock.wait(250L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (newsFetchedAt > 0L && now - newsFetchedAt < NEWS_TTL_MS) {
                    return newsCache;
                }
            }
            if (newsFetchedAt > 0L && now - newsFetchedAt < NEWS_TTL_MS) {
                return newsCache;
            }
            newsRefreshing = true;
        }
        try {
            List<NewsHeadline> fresh = newsFetcher.fetch();
            synchronized (newsLock) {
                newsCache = List.copyOf(fresh);
                newsFetchedAt = now;
                newsLock.notifyAll();
            }
            return newsCache;
        } catch (IOException | RuntimeException ex) {
            synchronized (newsLock) {
                newsCache = Collections.emptyList();
                newsFetchedAt = now;
                return Collections.emptyList();
            }
        } finally {
            synchronized (newsLock) {
                newsRefreshing = false;
                newsLock.notifyAll();
            }
        }
    }

    /** 測試：並發只觸發一次天氣抓取。 */
    void runWeatherRefreshTestHarness(AtomicInteger calls, CountDownLatch start) throws Exception {
        calls.incrementAndGet();
        start.await();
    }

    /** 測試用：注入快取。 */
    void seedWeather(WeatherSnapshot snapshot, long at) {
        synchronized (weatherLock) {
            weatherCache = snapshot;
            weatherFetchedAt = at;
        }
    }

    void seedNews(List<NewsHeadline> headlines, long at) {
        synchronized (newsLock) {
            newsCache = headlines == null ? Collections.emptyList() : List.copyOf(headlines);
            newsFetchedAt = at;
        }
    }

    long weatherFetchedAtForTests() {
        return weatherFetchedAt;
    }

    long newsFetchedAtForTests() {
        return newsFetchedAt;
    }
}