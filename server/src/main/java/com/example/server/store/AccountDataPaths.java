package com.example.server.store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 帳號／token／設備綁定 JSON 的目錄（與塗鴉牆相同的 StorageLocation 規則）。 */
public final class AccountDataPaths {

    public static final String DIR_PROPERTY = "punchclock.accounts.dir";
    public static final String DIR_ENV = "PUNCHCLOCK_ACCOUNT_DATA_DIR";

    private static final Path PERSISTENT_ROOT = Paths.get("/var/data");

    private AccountDataPaths() {
    }

    public static Path defaultDirectory() {
        String home = System.getProperty("user.home", ".");
        return StorageLocation.resolve(
                System.getProperty(DIR_PROPERTY),
                System.getenv(DIR_ENV),
                Files.isDirectory(PERSISTENT_ROOT),
                PERSISTENT_ROOT.resolve("accounts"),
                Paths.get(home, ".punchclock", "accounts"));
    }
}