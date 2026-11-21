package com.example.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClientUpdateInstallerTest {

    @Test
    public void unixScriptClearsQuarantineBeforeReplacingJar() {
        String script = ClientUpdateInstaller.unixScript();
        assertTrue(script.contains("xattr -d com.apple.quarantine"));
        assertTrue(script.indexOf("xattr -d com.apple.quarantine") < script.indexOf("mv -f"));
        assertTrue(script.contains("kill -0"));
    }

    @Test
    public void shQuoteKeepsSpacesAndQuotes() {
        assertTrue(ClientUpdateInstaller.shQuote("/tmp/my jar.jar").contains("/tmp/my jar.jar"));
        assertFalse(ClientUpdateInstaller.shQuote("a'b").contains("a'b"));
        assertTrue(ClientUpdateInstaller.shQuote("a'b").contains("a'\\''b"));
    }

    @Test
    public void unitTestsAreNotRunningFromTheShadedJar() {
        assertNull(ClientUpdateInstaller.runningJar());
    }

    @Test
    public void ideLaunchInstallsToDefaultJarUnderPunchclockDir() {
        String bundled = System.getProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
        System.clearProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
        try {
            assertEquals(ClientUpdateInstaller.defaultJar(), ClientUpdateInstaller.installTarget());
            assertEquals("SKLbaby.jar", ClientUpdateInstaller.defaultJar().getFileName().toString());
            assertEquals(".punchclock", ClientUpdateInstaller.defaultJar().getParent().getFileName().toString());
        } finally {
            if (bundled == null) {
                System.clearProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
            } else {
                System.setProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY, bundled);
            }
        }
    }
}
