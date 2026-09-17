package com.example.service;

import com.example.ui.TaskEditDialog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 雲端連線與打卡槽位設定持久化（~/.punchclock/config.json）
 */
public class ConfigPersistenceService {

    private static final String SAVE_DIR = ".punchclock";
    private static final String SAVE_FILE = "config.json";
    public static final int MAX_RECENT_VALUES = 10;
    /** 視窗透明度上限（再高會點不到控制項）；0 表示完全不透明。 */
    public static final int MAX_WINDOW_TRANSPARENCY_PERCENT = 60;

    public static class SlotSettings {
        public boolean enabled = true;
        public int hour = 9;
        public int minute = 0;
        public boolean useRandomOffset = true;
    }

    public static class CloudConfig {
        public String serverUrl = "http://localhost:3000";
        public String clientId = "company-worker";
        public String heartbeatToken = "punchclock-dev-secret";
        public boolean enableServer = false;
        /** 信任所有 SSL 憑證（僅本機除錯；預設關閉） */
        public boolean trustAllSsl = false;

        /** 共用打卡設定 */
        public String targetUrl = "https://www.msn.com/zh-tw";
        public String buttonId = "finance";
        public List<String> recentTargetUrls = new ArrayList<>();
        public List<String> recentButtonIds = new ArrayList<>();
        public List<String> recentServerUrls = new ArrayList<>();
        public String browserChoice = TaskEditDialog.BROWSER_OPTIONS[0];
        public boolean weekdaysOnly = true;

        public SlotSettings workIn = defaultWorkIn();
        public SlotSettings workOut = defaultWorkOut();

        /** 視窗／分割線（0 或負值表示使用預設） */
        public int windowWidth = 0;
        public int windowHeight = 0;
        public int windowX = -1;
        public int windowY = -1;
        public int splitDividerLocation = -1;
        /**
         * 視窗透明度百分比：0 為不透明，最高 {@link #MAX_WINDOW_TRANSPARENCY_PERCENT}。
         * 實際不透明度為 100% 減去此值（最透明仍保留 40%）。
         */
        public int windowTransparencyPercent = 0;

        /** 是否使用 ~/.punchclock/avatar.jpg 作為訊息／戳一下大頭照 */
        public boolean customAvatar = false;

        /** 是否使用 ~/.punchclock/background.jpg 作為程式視窗背景 */
        public boolean customBackground = false;
        /** 背景圖模糊 0–100（0 為清晰） */
        public int backgroundBlurPercent = 0;
        /** 背景圖不透明度 0–100（100 為最清楚） */
        public int backgroundOpacityPercent = WindowBackground.DEFAULT_OPACITY_PERCENT;

        /** 網路測試分頁（公司 Proxy／封閉網路除錯） */
        public String networkTestUrl = "https://www.google.com";
        public String networkProxyHost = "";
        public int networkProxyPort = 8080;
        public String networkProxyMode = "SYSTEM";
        /** Proxy 帳密（明文寫入 config.json；僅本機使用） */
        public String networkProxyUser = "";
        public String networkProxyPassword = "";
    }

    private static SlotSettings defaultWorkIn() {
        SlotSettings slot = new SlotSettings();
        slot.hour = 9;
        slot.minute = 0;
        return slot;
    }

    private static SlotSettings defaultWorkOut() {
        SlotSettings slot = new SlotSettings();
        slot.hour = 18;
        slot.minute = 0;
        return slot;
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
            System.err.println("[警告] 無法建立設定目錄: " + savePath.getParent());
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
                logger.accept("[警告] 儲存雲端設定失敗: " + e.getMessage());
            }
        }
    }

    public CloudConfig loadConfig(Consumer<String> logger) {
        if (!Files.exists(savePath)) {
            CloudConfig config = new CloudConfig();
            normalize(config);
            return config;
        }
        try {
            String json = Files.readString(savePath);
            if (json == null || json.isBlank()) {
                CloudConfig config = new CloudConfig();
                normalize(config);
                return config;
            }
            CloudConfig config = gson.fromJson(json, CloudConfig.class);
            if (config == null) {
                return new CloudConfig();
            }
            normalize(config);
            if (logger != null) {
                logger.accept("[設定] 已載入本地設定");
            }
            return config;
        } catch (Exception e) {
            if (logger != null) {
                logger.accept("[警告] 載入設定失敗: " + e.getMessage());
            }
            return new CloudConfig();
        }
    }

    static void normalize(CloudConfig config) {
        if (config.serverUrl == null || config.serverUrl.isBlank()) {
            config.serverUrl = "http://localhost:3000";
        }
        if (config.clientId == null || config.clientId.isBlank()) {
            config.clientId = "company-worker";
        }
        if (config.heartbeatToken == null || config.heartbeatToken.isBlank()) {
            config.heartbeatToken = "punchclock-dev-secret";
        }
        if (config.targetUrl == null || config.targetUrl.isBlank()) {
            config.targetUrl = "https://www.msn.com/zh-tw";
        }
        if (config.buttonId == null || config.buttonId.isBlank()) {
            config.buttonId = "finance";
        }
        if (config.recentTargetUrls == null) {
            config.recentTargetUrls = new ArrayList<>();
        }
        if (config.recentButtonIds == null) {
            config.recentButtonIds = new ArrayList<>();
        }
        if (config.recentServerUrls == null) {
            config.recentServerUrls = new ArrayList<>();
        }
        seedRecentIfMissing(config.recentTargetUrls, config.targetUrl);
        seedRecentIfMissing(config.recentButtonIds, config.buttonId);
        seedRecentIfMissing(config.recentServerUrls, config.serverUrl);
        if (config.browserChoice == null || config.browserChoice.isBlank()) {
            config.browserChoice = TaskEditDialog.BROWSER_OPTIONS[0];
        } else {
            config.browserChoice = TaskEditDialog.normalizeBrowserChoice(config.browserChoice);
        }
        config.weekdaysOnly = true; // 上班工具固定週一至週五排程
        if (config.workIn == null) {
            config.workIn = defaultWorkIn();
        }
        if (config.workOut == null) {
            config.workOut = defaultWorkOut();
        }
        normalizeSlot(config.workIn, defaultWorkIn());
        normalizeSlot(config.workOut, defaultWorkOut());
        config.windowTransparencyPercent = clampWindowTransparencyPercent(config.windowTransparencyPercent);
        config.backgroundBlurPercent = WindowBackground.clampBlurPercent(config.backgroundBlurPercent);
        config.backgroundOpacityPercent = WindowBackground.clampOpacityPercent(config.backgroundOpacityPercent);
        if (config.networkTestUrl == null || config.networkTestUrl.isBlank()) {
            config.networkTestUrl = "https://www.google.com";
        }
        if (config.networkProxyHost == null) {
            config.networkProxyHost = "";
        }
        config.networkProxyPort = NetworkProbeService.clampProxyPort(config.networkProxyPort);
        config.networkProxyMode = NetworkProbeService.normalizeProxyMode(config.networkProxyMode);
        if (config.networkProxyUser == null) {
            config.networkProxyUser = "";
        }
        if (config.networkProxyPassword == null) {
            config.networkProxyPassword = "";
        }
    }

    public static int clampWindowTransparencyPercent(int percent) {
        if (percent < 0) {
            return 0;
        }
        if (percent > MAX_WINDOW_TRANSPARENCY_PERCENT) {
            return MAX_WINDOW_TRANSPARENCY_PERCENT;
        }
        return percent;
    }

    private static void normalizeSlot(SlotSettings slot, SlotSettings defaults) {
        if (slot.hour < 0 || slot.hour > 23) {
            slot.hour = defaults.hour;
        }
        if (slot.minute < 0 || slot.minute > 59) {
            slot.minute = defaults.minute;
        }
    }

    public static void pushRecent(List<String> list, String value) {
        pushRecent(list, value, MAX_RECENT_VALUES);
    }

    public static void pushRecent(List<String> list, String value, int max) {
        if (list == null || value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        list.removeIf(trimmed::equals);
        list.add(0, trimmed);
        while (list.size() > max) {
            list.remove(list.size() - 1);
        }
    }

    private static void seedRecentIfMissing(List<String> list, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        if (!list.contains(trimmed)) {
            list.add(0, trimmed);
        }
        while (list.size() > MAX_RECENT_VALUES) {
            list.remove(list.size() - 1);
        }
    }

    public Path getSavePath() {
        return savePath;
    }
}
