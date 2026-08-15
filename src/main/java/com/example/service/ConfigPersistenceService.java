package com.example.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * 雲端連線設定持久化（~/.clickClick/config.json）
 */
public class ConfigPersistenceService {

    private static final String SAVE_DIR = ".clickClick";
    private static final String SAVE_FILE = "config.json";

    public static class CloudConfig {
        public String serverUrl = "http://localhost:3000";
        public String clientId = "company-worker";
        public String heartbeatToken = "clickclick-dev-secret";
        public boolean enableServer = false;
    }

    private final Gson gson;
    private final Path savePath;

    public ConfigPersistenceService() {
        this(defaultConfigPath());
    }

    /** 供單元測試注入自訂路徑 */
    public ConfigPersistenceService(Path savePath) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.savePath = savePath;
        try {
            Path parent = savePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            System.err.println("⚠️ 無法建立設定目錄: " + savePath.getParent());
        }
    }

    private static Path defaultConfigPath() {
        String userHome = System.getProperty("user.home", ".");
        return Paths.get(userHome, SAVE_DIR, SAVE_FILE);
    }

    public void saveConfig(CloudConfig config, Consumer<String> logger) {
        if (config == null) return;
        try {
            Files.writeString(savePath, gson.toJson(config));
        } catch (IOException e) {
            if (logger != null) {
                logger.accept("⚠️ 儲存雲端設定失敗: " + e.getMessage());
            }
        }
    }

    public CloudConfig loadConfig(Consumer<String> logger) {
        if (!Files.exists(savePath)) {
            return new CloudConfig();
        }
        try {
            String json = Files.readString(savePath);
            if (json == null || json.isBlank()) {
                return new CloudConfig();
            }
            CloudConfig config = gson.fromJson(json, CloudConfig.class);
            if (config == null) {
                return new CloudConfig();
            }
            if (config.serverUrl == null || config.serverUrl.isBlank()) {
                config.serverUrl = "http://localhost:3000";
            }
            if (config.clientId == null || config.clientId.isBlank()) {
                config.clientId = "company-worker";
            }
            if (config.heartbeatToken == null || config.heartbeatToken.isBlank()) {
                config.heartbeatToken = "clickclick-dev-secret";
            }
            if (logger != null) {
                logger.accept("⚙️ 已載入本地雲端設定");
            }
            return config;
        } catch (Exception e) {
            if (logger != null) {
                logger.accept("⚠️ 載入雲端設定失敗: " + e.getMessage());
            }
            return new CloudConfig();
        }
    }

    public Path getSavePath() {
        return savePath;
    }
}
