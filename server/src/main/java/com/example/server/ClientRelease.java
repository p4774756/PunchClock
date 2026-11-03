package com.example.server;

import com.example.BuildInfo;
import com.example.ClientUpdatePolicy;
import com.example.ReleaseNotes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 這次部署附帶的桌面端 jar。Docker image 放在 {@code /app/client/}；
 * 本機可用環境變數 {@code CLIENT_JAR_PATH} 指到 standalone jar。
 */
public final class ClientRelease {

    public static final ClientRelease NONE = new ClientRelease("", "", "", "", 0L, null, "");

    private final String version;
    private final String sourceDigest;
    /** 這份 jar 的建置（或 commit）時間，ISO-8601 UTC；未知為空字串。 */
    private final String releaseTime;
    private final String sha256;
    private final long size;
    private final Path jar;
    /** {@code release-notes.txt} 原文；沒有這份檔時為空字串。 */
    private final String notes;

    private ClientRelease(String version, String sourceDigest, String releaseTime, String sha256, long size, Path jar,
                          String notes) {
        this.version = version == null ? "" : version;
        this.sourceDigest = sourceDigest == null ? "" : sourceDigest;
        this.releaseTime = releaseTime == null ? "" : releaseTime;
        this.sha256 = sha256 == null ? "" : sha256;
        this.size = size;
        this.jar = jar;
        this.notes = notes == null ? "" : notes;
    }

    public static ClientRelease discover() {
        String env = System.getenv("CLIENT_JAR_PATH");
        if (env != null && !env.isBlank()) {
            return readOrNone(Path.of(env.trim()));
        }
        Path dockerPath = Path.of("/app/client/punchclock-client-standalone.jar");
        if (Files.isRegularFile(dockerPath)) {
            return readOrNone(dockerPath);
        }
        return NONE;
    }

    public static ClientRelease fromJar(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IOException("client jar 不存在");
        }
        Properties props = new Properties();
        String notes = "";
        try (JarFile jarFile = new JarFile(path.toFile())) {
            JarEntry entry = jarFile.getJarEntry("version.properties");
            if (entry == null) {
                throw new IOException("jar 沒有 version.properties");
            }
            try (InputStream in = jarFile.getInputStream(entry)) {
                props.load(in);
            }
            JarEntry notesEntry = jarFile.getJarEntry(ReleaseNotes.RESOURCE);
            if (notesEntry != null) {
                try (InputStream in = jarFile.getInputStream(notesEntry)) {
                    notes = new String(in.readNBytes(16 * 1024), StandardCharsets.UTF_8);
                }
            }
        }
        BuildInfo info = BuildInfo.fromProperties(props);
        String hash = ClientUpdatePolicy.sha256(path);
        return new ClientRelease(info.version(), info.sourceDigest(), info.releaseTimeIso(), hash,
                Files.size(path), path, notes);
    }

    public boolean isAvailable() {
        return jar != null
                && Files.isRegularFile(jar)
                && version.matches("\\d+\\.\\d+\\.\\d+")
                && !sourceDigest.isEmpty()
                && !sha256.isEmpty()
                && size > 0;
    }

    public String version() {
        return version;
    }

    public String sourceDigest() {
        return sourceDigest;
    }

    public String releaseTime() {
        return releaseTime;
    }

    public String sha256() {
        return sha256;
    }

    public long size() {
        return size;
    }

    public Path jar() {
        return jar;
    }

    public String notes() {
        return notes;
    }

    private static ClientRelease readOrNone(Path path) {
        try {
            ClientRelease release = fromJar(path);
            if (!release.isAvailable()) {
                System.err.println("client jar 沒有可用的原始碼摘要：" + path);
                return NONE;
            }
            return release;
        } catch (Exception ex) {
            System.err.println("無法讀取 client jar：" + path + "（" + ex.getMessage() + "）");
            return NONE;
        }
    }
}
