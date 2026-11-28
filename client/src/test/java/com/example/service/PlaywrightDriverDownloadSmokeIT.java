package com.example.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/**
 * 需連網；僅由 {@code playwright-driver-smoke.yml} 以 {@code -Dtest=} 執行。
 */
public class PlaywrightDriverDownloadSmokeIT {

    private String priorHome;
    private String priorIgnore;

    @Before
    public void isolateHome() throws IOException {
        priorHome = System.getProperty("user.home");
        priorIgnore = System.getProperty(PlaywrightNodeSupport.IGNORE_EMBEDDED_PROPERTY);
        Path home = Files.createTempDirectory("pw-driver-smoke-home");
        System.setProperty("user.home", home.toString());
        System.setProperty(PlaywrightNodeSupport.IGNORE_EMBEDDED_PROPERTY, "true");
        System.setProperty(PlaywrightNodeSupport.CLI_DIR_PROPERTY, "");
    }

    @After
    public void restoreHome() {
        if (priorHome != null) {
            System.setProperty("user.home", priorHome);
        } else {
            System.clearProperty("user.home");
        }
        if (priorIgnore != null) {
            System.setProperty(PlaywrightNodeSupport.IGNORE_EMBEDDED_PROPERTY, priorIgnore);
        } else {
            System.clearProperty(PlaywrightNodeSupport.IGNORE_EMBEDDED_PROPERTY);
        }
        System.clearProperty(PlaywrightNodeSupport.CLI_DIR_PROPERTY);
    }

    @Test
    public void downloadsDriverToEmptyHomeAndChromiumHostResponds() throws Exception {
        String platform = PlaywrightNodeSupport.platformId();
        String version = PlaywrightNodeSupport.readBundledPlaywrightVersion();
        Path installDir = PlaywrightNodeSupport.driverInstallDir(
                Path.of(System.getProperty("user.home")), version, platform);

        PlaywrightNodeSupport.ensureNodeForPackagedApp(msg -> { });

        assertTrue(PlaywrightNodeSupport.isDriverInstalled(installDir, platform));
        assertTrue(PlaywrightNodeSupport.hasValidMarker(installDir));

        int revision = PlaywrightNodeSupport.readInstalledChromiumRevision(installDir);
        boolean headOk = false;
        for (URI uri : PlaywrightNodeSupport.chromiumBrowserArchiveUrls(revision, platform)) {
            int code = PlaywrightNodeSupport.httpHeadStatusFollowingRedirects(uri);
            if (code >= 200 && code < 400) {
                headOk = true;
                break;
            }
        }
        assertTrue("Chromium 下載位址應可連線", headOk);
    }
}