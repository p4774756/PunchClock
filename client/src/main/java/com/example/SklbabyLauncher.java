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
        Path bundled = ClientUpdateInstaller.bundledJar();
        if (bundled == null || !Files.isRegularFile(bundled)) {
            App.main(args);
            return;
        }
        System.setProperty(ClientUpdateInstaller.BUNDLED_JAR_PROPERTY, bundled.toAbsolutePath().normalize().toString());
        Path userJar = ClientUpdateInstaller.userAppJar();
        Path chosen = pickJar(bundled, userJar);
        try {
            launchWithJar(chosen, args);
        } catch (Throwable primary) {
            if (userJar != null && chosen.equals(userJar)) {
                quarantineBrokenUserJar(userJar);
                launchWithJar(bundled, args);
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
        if (ClientUpdatePolicy.shouldOffer(
                bundledVersion,
                bundledInfo.sourceDigest(),
                bundledInfo.releaseTimeIso(),
                userInfo.sourceDigest(),
                userInfo.releaseTimeIso())) {
            return userJar;
        }
        return bundled;
    }

    static void launchWithJar(Path jar, String[] args) throws Exception {
        URL url = jar.toAbsolutePath().normalize().toUri().toURL();
        URLClassLoader loader = new URLClassLoader(new URL[] {url}, ClassLoader.getPlatformClassLoader());
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