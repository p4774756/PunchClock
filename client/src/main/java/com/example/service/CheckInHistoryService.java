package com.example.service;

import com.example.model.CheckInHistoryEntry;
import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 打卡歷史簡易紀錄：保留最近 {@link #MAX_ENTRIES} 筆，寫入 ~/.punchclock/checkin-history.json
 */
public class CheckInHistoryService {

    public static final int MAX_ENTRIES = 10;

    private static final String SAVE_DIR = ".punchclock";
    private static final String SAVE_FILE = "checkin-history.json";

    private final Gson gson;
    private final Path savePath;
    private final List<CheckInHistoryEntry> entries = new CopyOnWriteArrayList<>();

    public CheckInHistoryService() {
        this(defaultHistoryPath());
    }

    /** 供單元測試注入自訂路徑 */
    public CheckInHistoryService(Path savePath) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.savePath = savePath;
        try {
            Path parent = savePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            System.err.println("[警告] 無法建立打卡歷史目錄: " + savePath.getParent());
        }
        load();
    }

    private static Path defaultHistoryPath() {
        String userHome = System.getProperty("user.home", ".");
        return Paths.get(userHome, SAVE_DIR, SAVE_FILE);
    }

    public List<CheckInHistoryEntry> getRecent() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * 任務成功／失敗後寫入一筆；非終態則忽略。
     *
     * @param fromScheduler true 表示排程觸發；false 表示「立即執行」
     */
    public void record(CheckInTask task, boolean fromScheduler) {
        if (task == null) {
            return;
        }
        TaskStatus status = task.getStatus();
        if (status != TaskStatus.SUCCESS && status != TaskStatus.FAILED) {
            return;
        }
        CheckInHistoryEntry entry = new CheckInHistoryEntry(
                LocalDateTime.now(),
                task.getName(),
                status,
                task.getResultMessage(),
                !fromScheduler);
        entries.add(0, entry);
        trimToMax();
        save(null);
    }

    public void clear(Consumer<String> logger) {
        entries.clear();
        save(logger);
    }

    private void trimToMax() {
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    private void load() {
        entries.clear();
        if (!Files.exists(savePath)) {
            return;
        }
        try {
            String json = Files.readString(savePath);
            if (json == null || json.isBlank()) {
                return;
            }
            Type listType = new TypeToken<List<CheckInHistoryEntry>>() {}.getType();
            List<CheckInHistoryEntry> loaded = gson.fromJson(json, listType);
            if (loaded == null) {
                return;
            }
            for (CheckInHistoryEntry entry : loaded) {
                if (entry != null) {
                    entries.add(entry);
                }
            }
            trimToMax();
        } catch (Exception e) {
            System.err.println("[警告] 載入打卡歷史失敗: " + e.getMessage());
        }
    }

    private void save(Consumer<String> logger) {
        try {
            String json = gson.toJson(entries);
            Files.writeString(savePath, json);
        } catch (IOException e) {
            if (logger != null) {
                logger.accept("[警告] 儲存打卡歷史失敗: " + e.getMessage());
            }
        }
    }

    public Path getSavePath() {
        return savePath;
    }
}
