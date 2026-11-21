package com.example.service;

import com.example.BuildInfo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarFile;

/** 從 jar 檔讀取 {@code version.properties}，供安裝版啟動器比較版本。 */
public final class JarBuildInfo {

    private final String version;
    private final String sourceDigest;
    private final String releaseTimeIso;

    private JarBuildInfo(String version, String sourceDigest, String releaseTimeIso) {
        this.version = version == null ? "" : version;
        this.sourceDigest = sourceDigest == null ? "" : sourceDigest;
        this.releaseTimeIso = releaseTimeIso == null ? "" : releaseTimeIso;
    }

    public static JarBuildInfo fromJar(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return empty();
        }
        try (JarFile file = new JarFile(jar.toFile())) {
            if (file.getEntry("version.properties") == null) {
                return empty();
            }
            Properties props = new Properties();
            try (InputStream in = file.getInputStream(file.getEntry("version.properties"))) {
                props.load(in);
            }
            BuildInfo info = BuildInfo.fromProperties(props);
            return new JarBuildInfo(info.version(), info.sourceDigest(), info.releaseTimeIso());
        } catch (Exception ignored) {
            return empty();
        }
    }

    public static JarBuildInfo empty() {
        return new JarBuildInfo("", "", "");
    }

    public String version() {
        return version;
    }

    public String sourceDigest() {
        return sourceDigest;
    }

    public String releaseTimeIso() {
        return releaseTimeIso;
    }

    public boolean isUsable() {
        return !version.isEmpty() || !sourceDigest.isEmpty();
    }
}