package com.example.service;

import com.example.BuildInfo;
import com.example.ui.TaskEditDialog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
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
        /**
         * 上班／下班共用的跳過日期（yyyy-MM-dd）。這些日子不排程，時分維持原設定。
         * 早於今天的日期會在載入或重算時清掉。
         */
        public List<String> skipDates = new ArrayList<>();

        public SlotSettings workIn = defaultWorkIn();
        public SlotSettings workOut = defaultWorkOut();

        /** 視窗位置與大小（0 或負值表示使用預設） */
        public int windowWidth = 0;
        public int windowHeight = 0;
        public int windowX = -1;
        public int windowY = -1;
        /** 舊版主視窗上下分割線；系統日誌改為分頁後不再使用，保留以免舊 config 遺失欄位。 */
        public int splitDividerLocation = -1;
        /** 上次開啟的主分頁 index（負值表示使用預設） */
        public int selectedTabIndex = -1;
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

        /** 中繼 Proxy：讓同事透過你連到 Server */
        public boolean relayEnabled = false;
        public int relayPort = 8888;

        /** 使用者對這一版桌面端按過「否」；摘要相同就不再問。 */
        public String ignoredUpdateDigest = "";
        /** 心跳時是否處理伺服器上的新版（預設開啟；關閉則忽略更新通知） */
        public boolean checkForUpdates = true;
        /** 有新版時，閒置自動下載並重啟更新（預設開啟；關閉則改為詢問） */
        public boolean autoApplyUpdate = true;
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
                logger.accept("[警告] 儲存設定失敗: " + e.getMessage());
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
        normalizeSkipDates(config, LocalDate.now());
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
        config.relayPort = RelayProxyService.clampPort(config.relayPort);
        config.ignoredUpdateDigest = BuildInfo.normalizeDigest(config.ignoredUpdateDigest);
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

    /**
     * 跳過日期正規化：去掉空白與無法解析的值、去掉今天以前的日期、去重並由舊到新排序。
     */
    public static void normalizeSkipDates(CloudConfig config, LocalDate today) {
        TreeSet<LocalDate> kept = new TreeSet<>();
        if (config.skipDates != null) {
            for (String value : config.skipDates) {
                LocalDate date = parseSkipDate(value);
                if (date != null && !date.isBefore(today)) {
                    kept.add(date);
                }
            }
        }
        config.skipDates = new ArrayList<>();
        for (LocalDate date : kept) {
            config.skipDates.add(date.toString());
        }
    }

    public static List<LocalDate> skipDates(CloudConfig config) {
        List<LocalDate> dates = new ArrayList<>();
        if (config == null || config.skipDates == null) {
            return dates;
        }
        for (String value : config.skipDates) {
            LocalDate date = parseSkipDate(value);
            if (date != null) {
                dates.add(date);
            }
        }
        return dates;
    }

    private static LocalDate parseSkipDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
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
