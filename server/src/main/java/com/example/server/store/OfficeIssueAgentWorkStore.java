package com.example.server.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 辦公室面板：每個 issue 累計的 cursor-agent 實際執行毫秒（不含排隊／CI）。 */
public final class OfficeIssueAgentWorkStore {

    private static final Logger LOG = Logger.getLogger(OfficeIssueAgentWorkStore.class.getName());
    private static final Path PERSISTENT_ROOT = Paths.get("/var/data");
    private static final long MAX_AGENT_WORK_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Long>>() {
    }.getType();

    private final ConcurrentHashMap<Integer, Long> totalMsByIssue = new ConcurrentHashMap<>();
    private final Path file;

    public OfficeIssueAgentWorkStore() {
        this(defaultFile());
    }

    OfficeIssueAgentWorkStore(Path file) {
        this.file = file;
        if (file != null) {
            load();
        }
    }

    public static Path defaultFile() {
        String home = System.getProperty("user.home", ".");
        Path dir = StorageLocation.resolve(
                System.getProperty("punchclock.office.dir"),
                System.getenv("OFFICE_STORAGE_DIR"),
                Files.isDirectory(PERSISTENT_ROOT),
                PERSISTENT_ROOT.resolve("office"),
                Paths.get(home, ".punchclock", "office"));
        return dir.resolve("issue-agent-work.json");
    }

    public void addClosedSegment(int issue, long startMs, long endMs) {
        if (issue <= 0 || startMs <= 0 || endMs <= startMs) {
            return;
        }
        long start = normalizeEpochMillis(startMs);
        long end = normalizeEpochMillis(endMs);
        if (end <= start) {
            return;
        }
        long delta = end - start;
        if (delta > MAX_AGENT_WORK_MS) {
            return;
        }
        totalMsByIssue.merge(issue, delta, Long::sum);
        capTotal(issue);
        saveQuietly();
    }

    public Long agentWorkMinutes(int issue) {
        if (issue <= 0) {
            return null;
        }
        Long ms = totalMsByIssue.get(issue);
        if (ms == null || ms < 60_000L) {
            return null;
        }
        long minutes = ms / 60_000L;
        if (minutes < 1) {
            return null;
        }
        if (minutes > MAX_AGENT_WORK_MS / 60_000L) {
            return null;
        }
        return minutes;
    }

    private void capTotal(int issue) {
        Long ms = totalMsByIssue.get(issue);
        if (ms != null && ms > MAX_AGENT_WORK_MS) {
            totalMsByIssue.put(issue, MAX_AGENT_WORK_MS);
        }
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Long> parsed = GSON.fromJson(raw, MAP_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return;
            }
            for (Map.Entry<String, Long> entry : parsed.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                try {
                    int issue = Integer.parseInt(entry.getKey().trim());
                    long ms = entry.getValue();
                    if (issue > 0 && ms > 0) {
                        totalMsByIssue.put(issue, ms);
                    }
                } catch (NumberFormatException ignored) {
                    // skip bad key
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "無法讀取辦公室 agent 工時：" + file, e);
        }
    }

    private static long normalizeEpochMillis(long value) {
        if (value <= 0) {
            return value;
        }
        if (value >= 1_000_000_000_000L) {
            return value;
        }
        if (value >= 1_000_000_000L) {
            return value * 1000L;
        }
        return value;
    }

    private void saveQuietly() {
        if (file == null) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Long> out = new LinkedHashMap<>();
            List<Integer> keys = new ArrayList<>(totalMsByIssue.keySet());
            Collections.sort(keys);
            for (Integer issue : keys) {
                Long ms = totalMsByIssue.get(issue);
                if (issue != null && issue > 0 && ms != null && ms > 0) {
                    out.put(String.valueOf(issue), ms);
                }
            }
            String json = GSON.toJson(out);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "無法寫入辦公室 agent 工時：" + file, e);
        }
    }
}