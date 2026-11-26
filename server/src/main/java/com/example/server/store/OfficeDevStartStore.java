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

/** 辦公室面板：每個 issue 最早觀測到的開發起點（心跳寫入、檔案保留）。 */
public final class OfficeDevStartStore {

    private static final Logger LOG = Logger.getLogger(OfficeDevStartStore.class.getName());
    private static final Path PERSISTENT_ROOT = Paths.get("/var/data");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Long>>() {
    }.getType();

    private final ConcurrentHashMap<Integer, Long> earliestByIssue = new ConcurrentHashMap<>();
    private final Path file;

    public OfficeDevStartStore() {
        this(defaultFile());
    }

    OfficeDevStartStore(Path file) {
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
        return dir.resolve("issue-dev-starts.json");
    }

    public Long earliestDevStart(int issue) {
        if (issue <= 0) {
            return null;
        }
        return earliestByIssue.get(issue);
    }

    public void noteRun(int issue, String role, Long startedAt, Long stageStartedAt) {
        if (issue <= 0) {
            return;
        }
        boolean devRun = role == null || role.isEmpty() || "dev".equals(role);
        if (!devRun) {
            return;
        }
        Long candidate = null;
        if (startedAt != null && startedAt > 0) {
            candidate = normalizeEpochMillis(startedAt);
        }
        if (stageStartedAt != null && stageStartedAt > 0) {
            long stageMs = normalizeEpochMillis(stageStartedAt);
            candidate = candidate == null ? stageMs : Math.min(candidate, stageMs);
        }
        if (candidate == null) {
            return;
        }
        Long prev = earliestByIssue.get(issue);
        if (prev != null && prev <= candidate) {
            return;
        }
        earliestByIssue.merge(issue, candidate, Math::min);
        saveQuietly();
    }

    public void noteMachineIssue(int issue, String role, Long runStartedAt, Long stageStartedAt) {
        noteRun(issue, role, runStartedAt, stageStartedAt);
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
                    long ms = normalizeEpochMillis(entry.getValue());
                    if (issue > 0 && ms > 0) {
                        earliestByIssue.put(issue, ms);
                    }
                } catch (NumberFormatException ignored) {
                    // skip bad key
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "無法讀取辦公室開發起點：" + file, e);
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
            List<Integer> keys = new ArrayList<>(earliestByIssue.keySet());
            Collections.sort(keys);
            for (Integer issue : keys) {
                Long ms = earliestByIssue.get(issue);
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
            LOG.log(Level.WARNING, "無法寫入辦公室開發起點：" + file, e);
        }
    }
}