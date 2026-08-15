package com.example.service;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 負責打卡任務的本地持久化（JSON 檔案讀寫）
 */
public class TaskPersistenceService {

    private static final String SAVE_DIR = ".clickClick";
    private static final String SAVE_FILE = "tasks.json";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Gson gson;
    private final Path savePath;

    public TaskPersistenceService() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();

        // 儲存在使用者家目錄下 ~/.clickClick/tasks.json
        String userHome = System.getProperty("user.home", ".");
        Path dir = Paths.get(userHome, SAVE_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("⚠️ 無法建立儲存目錄: " + dir);
        }
        this.savePath = dir.resolve(SAVE_FILE);
    }

    /**
     * 儲存任務清單至 JSON 檔案
     */
    public void saveTasks(List<CheckInTask> tasks, Consumer<String> logger) {
        try {
            String json = gson.toJson(tasks);
            Files.writeString(savePath, json);
        } catch (IOException e) {
            if (logger != null) {
                logger.accept("⚠️ 儲存任務失敗: " + e.getMessage());
            }
        }
    }

    /**
     * 從 JSON 檔案載入任務清單
     */
    public List<CheckInTask> loadTasks(Consumer<String> logger) {
        if (!Files.exists(savePath)) {
            return new ArrayList<>();
        }

        try {
            String json = Files.readString(savePath);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            Type listType = new TypeToken<List<CheckInTask>>() {}.getType();
            List<CheckInTask> tasks = gson.fromJson(json, listType);
            if (tasks == null) {
                return new ArrayList<>();
            }

            // 修正已排定但時間已過的任務狀態
            LocalDateTime now = LocalDateTime.now();
            for (CheckInTask task : tasks) {
                if (task.getStatus() == TaskStatus.SCHEDULED || task.getStatus() == TaskStatus.CHECKING_IN) {
                    LocalDateTime triggerTime = task.getActualTriggerTime();
                    if (triggerTime != null && triggerTime.isBefore(now)) {
                        task.setStatus(TaskStatus.CANCELLED);
                        task.setResultMessage("應用程式重啟，排程已過期");
                    }
                }
            }

            if (logger != null) {
                logger.accept(String.format("📂 已從本地載入 %d 筆歷史任務紀錄", tasks.size()));
            }
            return tasks;
        } catch (Exception e) {
            if (logger != null) {
                logger.accept("⚠️ 載入任務失敗: " + e.getMessage());
            }
            return new ArrayList<>();
        }
    }

    public Path getSavePath() {
        return savePath;
    }

    /**
     * Gson TypeAdapter: LocalDateTime <-> "yyyy-MM-dd HH:mm:ss" 字串
     */
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(FMT));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String str = in.nextString();
            try {
                return LocalDateTime.parse(str, FMT);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
