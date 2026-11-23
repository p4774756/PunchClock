package com.example;

import com.example.service.ClientUpdateInstaller;
import com.example.service.JarBuildInfo;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SklbabyLauncherTest {

    @Test
    public void pickJarPrefersNewerUserCopy() throws Exception {
        Path bundled = jarWithDigest("4.0.0", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "2026-01-01T00:00:00Z");
        Path user = jarWithDigest("4.0.1", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "2026-02-01T00:00:00Z");
        assertEquals(user, SklbabyLauncher.pickJar(bundled, user));
    }

    @Test
    public void pickJarKeepsBundledWhenUserIsOlder() throws Exception {
        Path bundled = jarWithDigest("4.0.2", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "2026-02-01T00:00:00Z");
        Path user = jarWithDigest("4.0.0", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "2026-01-01T00:00:00Z");
        assertEquals(bundled, SklbabyLauncher.pickJar(bundled, user));
    }

    @Test
    public void quarantineRenamesBrokenUserJar() throws Exception {
        Path home = Files.createTempDirectory("sklbaby-test-home");
        Path user = home.resolve(".sklbaby/app/SKLbaby.jar");
        Files.createDirectories(user.getParent());
        Files.writeString(user, "broken", StandardCharsets.UTF_8);
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            SklbabyLauncher.quarantineBrokenUserJar(ClientUpdateInstaller.userAppJar());
            assertFalse(Files.exists(user));
            try (var stream = Files.list(user.getParent())) {
                assertTrue(stream.anyMatch(p -> p.getFileName().toString().startsWith("SKLbaby.jar.bad-")));
            }
        } finally {
            if (previousHome != null) {
                System.setProperty("user.home", previousHome);
            }
        }
    }

    @Test
    public void macExecutableChoosesNewerUserJarForLaunch() throws Exception {
        Path home = Files.createTempDirectory("sklbaby-launch-home");
        Path root = Files.createTempDirectory("sklbaby-launch-app");
        Path executable = root.resolve("新光小寶貝.app/Contents/MacOS/新光小寶貝");
        Path bundled = root.resolve("新光小寶貝.app/Contents/app/SKLbaby.jar");
        Files.createDirectories(executable.getParent());
        Files.createDirectories(bundled.getParent());
        Files.writeString(executable, "launcher");
        Files.copy(
                jarWithDigest("4.0.0", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "2026-01-01T00:00:00Z"),
                bundled,
                StandardCopyOption.REPLACE_EXISTING);
        Path user = home.resolve(".sklbaby/app/SKLbaby.jar");
        Files.createDirectories(user.getParent());
        Files.copy(
                jarWithDigest("4.0.1", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "2026-02-01T00:00:00Z"),
                user,
                StandardCopyOption.REPLACE_EXISTING);
        String previousHome = System.getProperty("user.home");
        String previousBundled = System.getProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
        String previousApp = System.getProperty(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY);
        System.setProperty("user.home", home.toString());
        System.clearProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY);
        System.setProperty(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY, executable.toString());
        try {
            SklbabyLauncher.LaunchChoice choice = SklbabyLauncher.chooseLaunch();
            assertEquals(user.toAbsolutePath().normalize(), choice.chosen.toAbsolutePath().normalize());
            assertEquals(bundled.toAbsolutePath().normalize(), choice.bundled);
            assertEquals(bundled.toAbsolutePath().normalize().toString(),
                    System.getProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY));
            assertEquals(ClientUpdateInstaller.userAppJar(), ClientUpdateInstaller.installTarget());
            assertFalse(ClientUpdateInstaller.installTarget().startsWith(root));
        } finally {
            restore("user.home", previousHome);
            restore(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY, previousBundled);
            restore(ClientUpdateInstaller.JPACKAGE_APP_PATH_PROPERTY, previousApp);
        }
    }

    @Test
    public void settingsLabelComesFromTheLoadedJar() throws Exception {
        Path jar = Files.createTempFile("sklbaby-version-", ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            copyClass(out, AppVersion.class);
            copyClass(out, BuildInfo.class);
            String props = "version=9.9.9\n"
                    + "sourceDigest=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\n"
                    + "commitTime=2026-06-01T00:00:00Z\n";
            out.putNextEntry(new JarEntry("version.properties"));
            out.write(props.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (URLClassLoader loader = SklbabyLauncher.loaderFor(jar)) {
            Class<?> version = Class.forName("com.example.AppVersion", true, loader);
            String label = (String) version.getMethod("displayLabel").invoke(null);
            assertTrue(label.startsWith("v9.9.9 · ccccccc"));
            assertTrue(label.contains("提交"));
        }
    }

    @Test
    public void jarBuildInfoReadsVersionProperties() throws Exception {
        Path jar = jarWithDigest("4.0.0", "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", "2026-03-01T00:00:00Z");
        JarBuildInfo info = JarBuildInfo.fromJar(jar);
        assertEquals("4.0.0", info.version());
        assertEquals("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", info.sourceDigest());
        assertTrue(info.isUsable());
    }

    private static void copyClass(JarOutputStream out, Class<?> type) throws IOException {
        String name = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(name)) {
            out.putNextEntry(new JarEntry(name));
            out.write(in.readAllBytes());
            out.closeEntry();
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static Path jarWithDigest(String version, String digest, String commitTime) throws IOException {
        Path jar = Files.createTempFile("sklbaby-", ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            String props = "version=" + version + "\n"
                    + "sourceDigest=" + digest + "\n"
                    + "commitTime=" + commitTime + "\n";
            out.putNextEntry(new JarEntry("version.properties"));
            out.write(props.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }
}