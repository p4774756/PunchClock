package com.example;

import java.time.ZoneId;

/** 桌面端版本與版本時間，來源為 pom.xml（resources filtering）與最後一次 git commit。 */
public final class AppVersion {

    private static final BuildInfo INFO = BuildInfo.load(AppVersion.class, "/version.properties");

    public static final String VERSION = INFO.version();
    /** 最後一次 commit 時間（沒有 .git 時為建置時間），ISO-8601 UTC；IDE 直接跑時為空字串。 */
    public static final String RELEASE_TIME_ISO = INFO.releaseTimeIso();

    /** 例如 {@code v1.8.0（提交 2026-09-24 15:53）}；沒有時間就只顯示版號。 */
    public static String displayLabel() {
        String time = INFO.releaseTimeLabel(ZoneId.systemDefault());
        if (time.isEmpty()) {
            return "v" + VERSION;
        }
        return "v" + VERSION + "（" + (INFO.hasCommitTime() ? "提交 " : "建置 ") + time + "）";
    }

    private AppVersion() {}
}
