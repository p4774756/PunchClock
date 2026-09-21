package com.example.server.health;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 伺服器監控歷史：預設每分鐘取樣，保留約 3 天，並寫入本機 JSON。
 */
public final class HealthHistoryStore {

    public static final long RETENTION_MS = 3L * 24 * 60 * 60 * 1000;
    public static final long DEFAULT_SAMPLE_INTERVAL_MS = 60_000L;

    private static final Type LIST_TYPE = new TypeToken<List<Map<String, Object>>>() {
    }.getType();

    private final List<Map<String, Object>> samples = new CopyOnWriteArrayList<>();
    private final Path savePath;
    private final LongSupplier clock;
    private final Gson gson = new GsonBuilder().create();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public HealthHistoryStore() {
        this(defaultSavePath(), System::currentTimeMillis);
    }

    public HealthHistoryStore(Path savePath) {
        this(savePath, System::currentTimeMillis);
    }

    HealthHistoryStore(Path savePath, LongSupplier clock) {
        this.savePath = savePath;
        this.clock = clock != null ? clock : System::currentTimeMillis;
        loadFromDisk();
    }

    public static Path defaultSavePath() {
        String override = System.getenv("HEALTH_HISTORY_PATH");
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim());
        }
        String home = System.getProperty("user.home", ".");
        return Paths.get(home, ".punchclock", "server-health-history.json");
    }

    public void start(Supplier<Map<String, Object>> snapshotSupplier) {
        start(snapshotSupplier, DEFAULT_SAMPLE_INTERVAL_MS);
    }

    public void start(Supplier<Map<String, Object>> snapshotSupplier, long intervalMs) {
        if (snapshotSupplier == null || !started.compareAndSet(false, true)) {
            return;
        }
        long interval = Math.max(5_000L, intervalMs);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-history");
            t.setDaemon(true);
            return t;
        });
        // 啟動立刻取一次，之後依間隔
        scheduler.execute(() -> sampleSafely(snapshotSupplier));
        scheduler.scheduleAtFixedRate(
                () -> sampleSafely(snapshotSupplier),
                interval,
                interval,
                TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        started.set(false);
    }

    public synchronized void record(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        Map<String, Object> point = toSample(snapshot, clock.getAsLong());
        samples.add(point);
        pruneLocked();
        persistLocked();
    }

    public List<Map<String, Object>> history() {
        pruneLocked();
        return Collections.unmodifiableList(new ArrayList<>(samples));
    }

    public Map<String, Object> summary() {
        pruneLocked();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("retentionMs", RETENTION_MS);
        map.put("sampleCount", samples.size());
        map.put("samples", history());
        return map;
    }

    static Map<String, Object> toSample(Map<String, Object> snapshot, long atMs) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("atMs", atMs);
        point.put("processCpuPercent", asNumber(snapshot.get("processCpuPercent")));
        point.put("systemCpuPercent", asNumber(snapshot.get("systemCpuPercent")));
        point.put("loadAverage", asNumber(snapshot.get("loadAverage")));
        point.put("heapUsedBytes", asLong(snapshot.get("heapUsedBytes")));
        point.put("heapMaxBytes", asLong(snapshot.get("heapMaxBytes")));
        Long osTotal = asLong(snapshot.get("osTotalMemoryBytes"));
        Long osFree = asLong(snapshot.get("osFreeMemoryBytes"));
        point.put("osTotalMemoryBytes", osTotal);
        if (osTotal != null && osFree != null && osTotal > 0) {
            point.put("osUsedMemoryBytes", Math.max(0L, osTotal - osFree));
        } else {
            point.put("osUsedMemoryBytes", null);
        }
        point.put("fileOfferBytes", asLong(snapshot.get("fileOfferBytes")));
        point.put("fileOfferCount", asLong(snapshot.get("fileOfferCount")));
        point.put("diskUsableBytes", asLong(snapshot.get("diskUsableBytes")));
        point.put("diskTotalBytes", asLong(snapshot.get("diskTotalBytes")));
        return point;
    }

    private void sampleSafely(Supplier<Map<String, Object>> snapshotSupplier) {
        try {
            record(snapshotSupplier.get());
        } catch (Exception ignored) {
            // 取樣失敗不影響主服務
        }
    }

    private synchronized void pruneLocked() {
        long cutoff = clock.getAsLong() - RETENTION_MS;
        samples.removeIf(sample -> {
            Long at = asLong(sample.get("atMs"));
            return at == null || at < cutoff;
        });
    }

    private synchronized void persistLocked() {
        if (savePath == null) {
            return;
        }
        try {
            Path parent = savePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(savePath, gson.toJson(samples), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 寫入失敗不影響運行
        }
    }

    private synchronized void loadFromDisk() {
        if (savePath == null || !Files.exists(savePath)) {
            return;
        }
        try {
            String json = Files.readString(savePath, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) {
                return;
            }
            List<Map<String, Object>> loaded = gson.fromJson(json, LIST_TYPE);
            if (loaded == null) {
                return;
            }
            samples.clear();
            for (Map<String, Object> item : loaded) {
                if (item != null && !item.isEmpty()) {
                    samples.add(new LinkedHashMap<>(item));
                }
            }
            pruneLocked();
        } catch (Exception ignored) {
            samples.clear();
        }
    }

    private static Number asNumber(Object value) {
        if (value instanceof Number) {
            return (Number) value;
        }
        return null;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}
