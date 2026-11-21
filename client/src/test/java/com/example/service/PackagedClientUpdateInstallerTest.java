package com.example.service;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackagedClientUpdateInstallerTest {

    @Test
    public void packagedInstallTargetIsUserDataJar() {
        String previous = System.getProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
        String home = System.getProperty("user.home");
        System.setProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY, "/tmp/bundled/SKLbaby.jar");
        try {
            Path target = ClientUpdateInstaller.installTarget();
            assertTrue(ClientUpdateInstaller.isPackagedInstall());
            assertEquals(Path.of(home, ".sklbaby", "app", "SKLbaby.jar"), target);
            assertEquals(Path.of(home, ".sklbaby", "app", "SKLbaby.jar"), ClientUpdateInstaller.userAppJar());
        } finally {
            if (previous == null) {
                System.clearProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
            } else {
                System.setProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY, previous);
            }
        }
    }
}