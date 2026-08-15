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
    }

    @Test
    public void saveAndLoad_roundTrip() {
        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
        config.serverUrl = "https://example.com";
        config.clientId = "company-worker2";
        config.heartbeatToken = "prod-secret";
        config.enableServer = true;

        configService.saveConfig(config, null);
        assertTrue(Files.exists(configFile));

        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);
        assertEquals("https://example.com", loaded.serverUrl);
        assertEquals("company-worker2", loaded.clientId);
        assertEquals("prod-secret", loaded.heartbeatToken);
        assertTrue(loaded.enableServer);
    }

    @Test
    public void loadConfig_fillsBlankFieldsWithDefaults() throws Exception {
        Files.writeString(configFile, "{\"serverUrl\":\"\",\"clientId\":\"\",\"heartbeatToken\":\"\",\"enableServer\":true}");
        ConfigPersistenceService.CloudConfig loaded = configService.loadConfig(null);
        assertEquals("http://localhost:3000", loaded.serverUrl);
        assertEquals("company-worker", loaded.clientId);
        assertEquals("clickclick-dev-secret", loaded.heartbeatToken);
        assertTrue(loaded.enableServer);
    }
}
