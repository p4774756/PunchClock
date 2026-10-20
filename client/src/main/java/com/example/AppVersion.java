package com.example;

import java.time.ZoneId;

/** 桌面端版本與版本時間，來源為 pom.xml（resources filtering）與最後一次 git commit。 */
public final class AppVersion {

    private static final BuildInfo INFO = BuildInfo.load(AppVersion.class, "/version.properties");

    public static final String VERSION = INFO.version();
    /** 最後一次 commit 時間（沒有 .git 時為建置時間），ISO-8601 UTC；IDE 直接跑時為空字串。 */
    public static final String RELEASE_TIME_ISO = INFO.releaseTimeIso();
    /** client／shared 原始碼摘要；IDE 直接跑或未經過打包時為空字串。 */
    public static final String SOURCE_DIGEST = INFO.sourceDigest();

    /** 例如 {@code v1.8.0 · a1b2c3d}（版號 + 原始碼摘要前 7 碼）；沒有摘要就只有版號。 */
    public static String buildLabel() {
        return INFO.buildLabel();
    }

    /** 例如 {@code v1.8.0 · a1b2c3d（提交 2026-09-24 15:53）}；沒有時間就不附時間。 */
    public static String displayLabel() {
        String time = INFO.releaseTimeLabel(ZoneId.systemDefault());
        if (time.isEmpty()) {
            return buildLabel();
        }
        return buildLabel() + "（" + (INFO.hasCommitTime() ? "提交 " : "建置 ") + time + "）";
    }

    private AppVersion() {}
}
