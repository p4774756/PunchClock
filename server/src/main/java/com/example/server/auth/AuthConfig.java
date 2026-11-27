package com.example.server.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理員認證設定；production 預設由環境變數載入，測試可注入獨立密碼。
 */
public final class AuthConfig {

    static final String DEFAULT_ADMIN_PASSWORD = "secret";
    static final String DEFAULT_HEARTBEAT_SECRET = "punchclock-dev-secret";

    private final String adminPassword;
    private final String heartbeatSecret;

    private AuthConfig(String adminPassword, String heartbeatSecret) {
        this.adminPassword = adminPassword;
        this.heartbeatSecret = heartbeatSecret;
    }

    public String adminPassword() {
        return adminPassword;
    }

    public String heartbeatSecret() {
        return heartbeatSecret;
    }

    public static AuthConfig fromEnv() {
        return from(System.getenv());
    }

    public static AuthConfig from(Map<String, String> env) {
        if (isProduction(env)) {
            rejectDefaultSecretsOnProduction(env);
        }
        return new AuthConfig(
                envOrDefault(env, "ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD),
                envOrDefault(env, "HEARTBEAT_SECRET", DEFAULT_HEARTBEAT_SECRET));
    }

    static boolean isProduction(Map<String, String> env) {
        String render = env.get("RENDER");
        return render != null && !render.isEmpty();
    }

    private static void rejectDefaultSecretsOnProduction(Map<String, String> env) {
        List<String> invalid = new ArrayList<>();
        if (isMissingOrDefault(env, "ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD)) {
            invalid.add("ADMIN_PASSWORD");
        }
        if (isMissingOrDefault(env, "HEARTBEAT_SECRET", DEFAULT_HEARTBEAT_SECRET)) {
            invalid.add("HEARTBEAT_SECRET");
        }
        if (invalid.isEmpty()) {
            return;
        }
        for (String key : invalid) {
            System.err.println("Render 啟動已中止：環境變數 " + key + " 未設定、為空，或仍為開發預設值");
        }
        throw new IllegalStateException("正式環境禁止使用預設密鑰");
    }

    private static boolean isMissingOrDefault(Map<String, String> env, String key, String defaultValue) {
        String value = env.get(key);
        return value == null || value.isEmpty() || defaultValue.equals(value);
    }

    private static String envOrDefault(Map<String, String> env, String key, String defaultValue) {
        String value = env.get(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}