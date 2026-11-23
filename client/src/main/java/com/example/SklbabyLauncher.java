package com.example;

import com.example.service.ClientUpdateInstaller;
import com.example.service.JarBuildInfo;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * 安裝版入口：比較內建 jar 與使用者資料夾的 jar，載入較新者啟動 {@link App}；
 * 較新的 jar 無法啟動時退回內建版。直接 {@code java -jar SKLbaby.jar} 仍走 {@link App#main}。
 */
public final class SklbabyLauncher {

    private SklbabyLauncher() {}

    public static void main(String[] args) throws Exception {
        LaunchChoice choice = chooseLaunch();
        if (choice.chosen == null) {
            App.main(args);
            return;
        }
        try {
            launchWithJar(choice.chosen, args);
        } catch (Throwable primary) {
            if (choice.userJar != null && choice.chosen.equals(choice.userJar)) {
                quarantineBrokenUserJar(choice.userJar);
                launchWithJar(choice.bundled, args);
                return;
            }
            if (primary instanceof Exception) {
                throw (Exception) primary;
            }
            if (primary instanceof Error) {
                throw (Error) primary;
            }
            throw new Exception(primary);
        }
    }

    /**
     * 安裝版才選 jar：把內建路徑寫進系統屬性，並在使用者資料夾有較新 jar 時選它。
     * 不是安裝版時 {@link LaunchChoice#chosen} 為 null，呼叫端直接走 {@link App#main}。
     */
    static LaunchChoice chooseLaunch() {
        Path bundled = ClientUpdateInstaller.bundledJar();
        if (bundled == null || !Files.isRegularFile(bundled)) {
            return LaunchChoice.plain();
        }
        Path normalized = bundled.toAbsolutePath().normalize();
        System.setProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY, normalized.toString());
        Path userJar = ClientUpdateInstaller.userAppJar();
        return new LaunchChoice(normalized, userJar, pickJar(normalized, userJar));
    }

    static URLClassLoader loaderFor(Path jar) throws Exception {
        URL url = jar.toAbsolutePath().normalize().toUri().toURL();
        return new URLClassLoader(new URL[] {url}, ClassLoader.getPlatformClassLoader());
    }

    static final class LaunchChoice {
        final Path bundled;
        final Path userJar;
        final Path chosen;

        private LaunchChoice(Path bundled, Path userJar, Path chosen) {
            this.bundled = bundled;
            this.userJar = userJar;
            this.chosen = chosen;
        }

        static LaunchChoice plain() {
            return new LaunchChoice(null, null, null);
        }
    }

    static Path pickJar(Path bundled, Path userJar) {
        if (userJar == null || !Files.isRegularFile(userJar)) {
            return bundled;
        }
        JarBuildInfo bundledInfo = JarBuildInfo.fromJar(bundled);
        JarBuildInfo userInfo = JarBuildInfo.fromJar(userJar);
        if (!userInfo.isUsable()) {
            return bundled;
        }
        if (!bundledInfo.isUsable()) {
            return userJar;
        }
        String bundledVersion = bundledInfo.version().isEmpty() ? BuildInfo.DEV_VERSION : bundledInfo.version();
        String userVersion = userInfo.version().isEmpty() ? BuildInfo.DEV_VERSION : userInfo.version();
        if (ClientUpdatePolicy.shouldOffer(bundledVersion, userVersion)) {
            return userJar;
        }
        return bundled;
    }

    static void launchWithJar(Path jar, String[] args) throws Exception {
        URLClassLoader loader = loaderFor(jar);
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> appClass = Class.forName("com.example.App", true, loader);
        Method main = appClass.getMethod("main", String[].class);
        main.invoke(null, new Object[] {args});
    }

    static void quarantineBrokenUserJar(Path userJar) throws IOException {
        Path parent = userJar.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String stamp = Long.toString(Instant.now().toEpochMilli());
        Path bad = parent.resolve("SKLbaby.jar.bad-" + stamp);
        Files.move(userJar, bad, StandardCopyOption.REPLACE_EXISTING);
    }
}