package com.example.service;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PackagedClientUpdateInstallerTest {

    @Test
    public void packagedInstallTargetIsUserDataJar() {
        String previous = System.getProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
        String appPath = System.getProperty(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY);
        String home = System.getProperty("user.home");
        System.setProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY, "/tmp/bundled/SKLbaby.jar");
        System.clearProperty(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY);
        try {
            Path target = ClientUpdateInstaller.installTarget();
            assertTrue(ClientUpdateInstaller.isPackagedInstall());
            assertEquals(Path.of(home, ".sklbaby", "app", "SKLbaby.jar"), target);
            assertEquals(Path.of(home, ".sklbaby", "app", "SKLbaby.jar"), ClientUpdateInstaller.userAppJar());
        } finally {
            restore(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY, previous);
            restore(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY, appPath);
        }
    }

    @Test
    public void macExecutableAppPathResolvesBundledJarAndUserInstallTarget() throws Exception {
        Path root = Files.createTempDirectory("sklbaby-mac-app");
        Path executable = root.resolve("新光小寶貝.app/Contents/MacOS/新光小寶貝");
        Path bundled = root.resolve("新光小寶貝.app/Contents/app/SKLbaby.jar");
        Files.createDirectories(executable.getParent());
        Files.createDirectories(bundled.getParent());
        Files.writeString(executable, "launcher");
        Files.writeString(bundled, "bundled");
        Path home = Files.createTempDirectory("sklbaby-mac-home");
        withImage(executable, home, () -> {
            assertEquals(bundled.toAbsolutePath().normalize(), ClientUpdateInstaller.bundledJar());
            assertTrue(ClientUpdateInstaller.isPackagedInstall());
            Path target = ClientUpdateInstaller.installTarget();
            assertEquals(home.resolve(".sklbaby/app/SKLbaby.jar"), target);
            assertFalse(target.startsWith(root));
        });
    }

    @Test
    public void newMacInstallResolvesBundledJarAndUserInstallTarget() throws Exception {
        Path root = Files.createTempDirectory("sklbaby-mac-new");
        Path executable = root.resolve("小寶貝.app/Contents/MacOS/小寶貝");
        Path bundled = root.resolve("小寶貝.app/Contents/app/SKLbaby.jar");
        Files.createDirectories(executable.getParent());
        Files.createDirectories(bundled.getParent());
        Files.writeString(executable, "launcher");
        Files.writeString(bundled, "bundled");
        Path home = Files.createTempDirectory("sklbaby-mac-new-home");
        withImage(executable, home, () -> {
            assertEquals(bundled.toAbsolutePath().normalize(), ClientUpdateInstaller.bundledJar());
            assertEquals(home.resolve(".sklbaby/app/SKLbaby.jar"), ClientUpdateInstaller.installTarget());
        });
    }

    @Test
    public void newWindowsInstallResolvesJarBesideAppDirectory() throws Exception {
        Path root = Files.createTempDirectory("sklbaby-win-new");
        Path executable = root.resolve("小寶貝.exe");
        Path bundled = root.resolve("app/SKLbaby.jar");
        Files.createDirectories(bundled.getParent());
        Files.writeString(executable, "launcher");
        Files.writeString(bundled, "bundled");
        Path home = Files.createTempDirectory("sklbaby-win-new-home");
        withImage(executable, home, () -> {
            assertEquals(bundled.toAbsolutePath().normalize(), ClientUpdateInstaller.bundledJar());
            assertEquals(home.resolve(".sklbaby/app/SKLbaby.jar"), ClientUpdateInstaller.installTarget());
        });
    }

    @Test
    public void windowsExecutableAppPathResolvesJarBesideAppDirectory() throws Exception {
        Path root = Files.createTempDirectory("sklbaby-win-app");
        Path executable = root.resolve("新光小寶貝.exe");
        Path bundled = root.resolve("app/SKLbaby.jar");
        Files.createDirectories(bundled.getParent());
        Files.writeString(executable, "launcher");
        Files.writeString(bundled, "bundled");
        Path home = Files.createTempDirectory("sklbaby-win-home");
        withImage(executable, home, () -> {
            assertEquals(bundled.toAbsolutePath().normalize(), ClientUpdateInstaller.bundledJar());
            assertEquals(home.resolve(".sklbaby/app/SKLbaby.jar"), ClientUpdateInstaller.installTarget());
        });
    }

    @Test
    public void missingExecutableIsNotAPackagedInstall() {
        String previous = System.getProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
        String appPath = System.getProperty(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY);
        System.clearProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
        System.setProperty(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY, "/tmp/sklbaby-missing-launcher");
        try {
            assertFalse(ClientUpdateInstaller.isPackagedInstall());
            assertNull(ClientUpdateInstaller.bundledJar());
        } finally {
            restore(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY, previous);
            restore(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY, appPath);
        }
    }

    private static void withImage(Path executable, Path home, ThrowingRunnable body) throws Exception {
        String previousHome = System.getProperty("user.home");
        String previousBundled = System.getProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
        String previousApp = System.getProperty(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY);
        System.setProperty("user.home", home.toString());
        System.clearProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
        System.setProperty(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY, executable.toString());
        try {
            body.run();
        } finally {
            restore("user.home", previousHome);
            restore(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY, previousBundled);
            restore(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY, previousApp);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}