package com.example.service;

import com.example.ui.TaskEditDialog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ConfigPersistenceServiceTest {

    private Path tempDir;
    private Path configFile;
    private ConfigPersistenceService configService;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("punchclock-config-test");
        configFile = tempDir.resolve("config.json");
        configService = new ConfigPersistenceService(configFile);
    }

    @After
    public void tearDown() throws Exception {
        Files.deleteIfExists(configFile);
        Files.deleteIfExists(tempDir);
    }

    @Test
    public void loadConfig_whenMissing_returnsDefaults() {
        ConfigPersistenceService.CloudConfig config = configService.loadConfig(null);
        assertEquals("http://localhost:3000", config.serverUrl);
        assertEquals("company-worker", config.clientId);
        assertEquals("punchclock-dev-secret", config.heartbeatToken);
        assertFalse(config.enableServer);
        assertEquals(9, config.workIn.hour);
        assertEquals(18, config.workOut.hour);
        assertTrue(config.weekdaysOnly);
    }

    @Test
    public void saveAndLoad_roundTrip() {
        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
        config.serverUrl = "https://example.com";
        config.clientId = "company-worker2";
        config.heartbeatToken = "prod-secret";
        config.enableServer = true;
        config.targetUrl = "https://example.com/checkin";
        config.buttonId = "#btn";
        config.browserChoice = "Google Chrome (本機已安裝)";
        config.weekdaysOnly = false; // 寫入舊值也會在 load 時被正規化為 true
        config.workIn.enabled = true;
        config.workIn.hour = 8;
        config.workIn.minute = 30;
        config.workOut.enabled = false;
        config.workOut.hour = 17;
        config.workOut.minute = 45;

        configService.saveConfig(config, null);
        assertTrue(Files.exists(configFile));

        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);
        assertEquals("https://example.com", loaded.serverUrl);
        assertEquals("company-worker2", loaded.clientId);
        assertEquals("prod-secret", loaded.heartbeatToken);
        assertTrue(loaded.enableServer);
        assertEquals("https://example.com/checkin", loaded.targetUrl);
        assertEquals("#btn", loaded.buttonId);
        assertEquals(TaskEditDialog.BROWSER_OPTIONS[1], loaded.browserChoice);
        assertTrue(loaded.weekdaysOnly);
        assertEquals(8, loaded.workIn.hour);
        assertEquals(30, loaded.workIn.minute);
        assertFalse(loaded.workOut.enabled);
        assertEquals(17, loaded.workOut.hour);
        assertEquals(45, loaded.workOut.minute);
    }

    @Test
    public void loadConfig_fillsBlankFieldsWithDefaults() throws Exception {
        Files.writeString(configFile, "{\"serverUrl\":\"\",\"clientId\":\"\",\"heartbeatToken\":\"\",\"enableServer\":true,\"targetUrl\":\"\",\"buttonId\":\"\",\"browserChoice\":\"\"}");
        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);
        assertEquals("http://localhost:3000", loaded.serverUrl);
        assertEquals("company-worker", loaded.clientId);
        assertEquals("punchclock-dev-secret", loaded.heartbeatToken);
        assertTrue(loaded.enableServer);
        assertEquals("https://www.msn.com/zh-tw", loaded.targetUrl);
        assertEquals("finance", loaded.buttonId);
        assertEquals(TaskEditDialog.BROWSER_OPTIONS[0], loaded.browserChoice);
        assertEquals(9, loaded.workIn.hour);
        assertEquals(18, loaded.workOut.hour);
    }

    @Test
    public void pushRecent_deduplicatesAndCapsSize() {
        java.util.List<String> list = new java.util.ArrayList<>();
        ConfigPersistenceService.pushRecent(list, "a");
        ConfigPersistenceService.pushRecent(list, "b");
        ConfigPersistenceService.pushRecent(list, "a");
        assertEquals(2, list.size());
        assertEquals("a", list.get(0));
        assertEquals("b", list.get(1));

        for (int i = 0; i < 15; i++) {
            ConfigPersistenceService.pushRecent(list, "v" + i);
        }
        assertEquals(ConfigPersistenceService.MAX_RECENT_VALUES, list.size());
        assertEquals("v14", list.get(0));
    }

    @Test
    public void saveAndLoad_persistsRecentLists() {
        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
        config.targetUrl = "https://example.com/checkin";
        config.buttonId = "#btn";
        config.serverUrl = "https://example.com";
        config.recentTargetUrls.add("https://old.example/a");
        config.recentTargetUrls.add("https://example.com/checkin");
        config.recentButtonIds.add("#old");
        config.recentServerUrls.add("http://localhost:3000");

        configService.saveConfig(config, null);
        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);

        assertEquals(2, loaded.recentTargetUrls.size());
        assertTrue(loaded.recentTargetUrls.contains("https://example.com/checkin"));
        assertEquals("#btn", loaded.buttonId);
        assertTrue(loaded.recentServerUrls.contains("http://localhost:3000"));
        assertTrue(loaded.recentServerUrls.contains("https://example.com"));
    }

    @Test
    public void saveAndLoad_persistsCustomAvatarFlag() {
        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
        config.customAvatar = true;
        configService.saveConfig(config, null);
        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);
        assertTrue(loaded.customAvatar);
    }

    @Test
    public void saveAndLoad_persistsCustomBackgroundFlag() {
        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
        config.customBackground = true;
        config.backgroundBlurPercent = 40;
        config.backgroundOpacityPercent = 60;
        configService.saveConfig(config, null);
        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);
        assertTrue(loaded.customBackground);
        assertEquals(40, loaded.backgroundBlurPercent);
        assertEquals(60, loaded.backgroundOpacityPercent);
    }

    @Test
    public void saveAndLoad_persistsWindowTransparencyPercent() {
        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
        config.windowTransparencyPercent = 25;
        configService.saveConfig(config, null);
        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);
        assertEquals(25, loaded.windowTransparencyPercent);
    }

    @Test
    public void loadConfig_clampsWindowTransparencyPercent() throws Exception {
        Files.writeString(configFile, "{\"windowTransparencyPercent\":95}");
        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);
        assertEquals(ConfigPersistenceService.MAX_WINDOW_TRANSPARENCY_PERCENT, loaded.windowTransparencyPercent);

        Files.writeString(configFile, "{\"windowTransparencyPercent\":-4}");
        loaded = configService.loadConfig(null);
        assertEquals(0, loaded.windowTransparencyPercent);
    }

    @Test
    public void loadConfig_missingTransparencyDefaultsToOpaque() {
        ConfigPersistenceService.CloudConfig config = configService.loadConfig(null);
        assertEquals(0, config.windowTransparencyPercent);
    }

    @Test
    public void saveAndLoad_persistsNetworkToolsSettings() {
        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
        config.networkTestUrl = "https://example.com/ping";
        config.networkProxyHost = "proxy.company.com";
        config.networkProxyPort = 3128;
        config.networkProxyMode = "custom";
        config.networkProxyUser = "corp\\alice";
        config.networkProxyPassword = "s3cret";
        config.networkSplitDividerLocation = 220;
        config.selectedTabIndex = 3;
        configService.saveConfig(config, null);

        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);
        assertEquals("https://example.com/ping", loaded.networkTestUrl);
        assertEquals("proxy.company.com", loaded.networkProxyHost);
        assertEquals(3128, loaded.networkProxyPort);
        assertEquals("CUSTOM", loaded.networkProxyMode);
        assertEquals("corp\\alice", loaded.networkProxyUser);
        assertEquals("s3cret", loaded.networkProxyPassword);
        assertEquals(220, loaded.networkSplitDividerLocation);
        assertEquals(3, loaded.selectedTabIndex);
    }

    @Test
    public void loadConfig_normalizesNetworkToolsDefaults() throws Exception {
        Files.writeString(configFile, "{\"networkTestUrl\":\"\",\"networkProxyPort\":0,\"networkProxyMode\":\"nope\"}");
        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);
        assertEquals("https://www.google.com", loaded.networkTestUrl);
        assertEquals("", loaded.networkProxyHost);
        assertEquals(8080, loaded.networkProxyPort);
        assertEquals("SYSTEM", loaded.networkProxyMode);
        assertEquals("", loaded.networkProxyUser);
        assertEquals("", loaded.networkProxyPassword);
    }

    @Test
    public void loadConfig_seedsRecentFromCurrentValues() {
        ConfigPersistenceService.CloudConfig config = configService.loadConfig(null);
        assertFalse(config.recentTargetUrls.isEmpty());
        assertTrue(config.recentTargetUrls.contains(config.targetUrl));
        assertTrue(config.recentButtonIds.contains(config.buttonId));
        assertTrue(config.recentServerUrls.contains(config.serverUrl));
    }
}
