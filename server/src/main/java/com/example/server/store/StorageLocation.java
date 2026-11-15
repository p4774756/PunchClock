package com.example.server.store;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 儲存位置：覆寫值、環境變數、永久磁碟、家目錄。
 * 呼叫端傳入磁碟是否存在，測試不必真的有 {@code /var/data}。
 */
public final class StorageLocation {

    private StorageLocation() {
    }

    public static Path resolve(String override, String env, boolean persistentAvailable, Path persistent, Path home) {
        if (hasText(override)) {
            return Paths.get(override.trim());
        }
        if (hasText(env)) {
            return Paths.get(env.trim());
        }
        if (persistentAvailable && persistent != null) {
            return persistent;
        }
        return home;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
