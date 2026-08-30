package com.example.service;

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
        tempDir = Files.createTempDirectory("clickclick-config-test");
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
        assertEquals("clickclick-dev-secret", config.heartbeatToken);
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
        assertEquals("Google Chrome (本機已安裝)", loaded.browserChoice);
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
        assertEquals("clickclick-dev-secret", loaded.heartbeatToken);
        assertTrue(loaded.enableServer);
        assertEquals("https://www.msn.com/zh-tw", loaded.targetUrl);
        assertEquals("finance", loaded.buttonId);
        assertEquals("Microsoft Edge (本機已安裝)", loaded.browserChoice);
        assertEquals(9, loaded.workIn.hour);
        assertEquals(18, loaded.workOut.hour);
    }
}
