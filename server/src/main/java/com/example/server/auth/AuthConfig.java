package com.example.server.auth;

import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理員認證設定；production 預設由環境變數載入，測試可注入獨立密碼。
 */
public final class AuthConfig {

    static final String DEFAULT_ADMIN_PASSWORD = "secret";
    static final String DEFAULT_HEARTBEAT_SECRET = "punchclock-dev-secret";

    private final String adminPasswordHash;
    private final String heartbeatSecret;

    private AuthConfig(String adminPasswordHash, String heartbeatSecret) {
        this.adminPasswordHash = adminPasswordHash;
        this.heartbeatSecret = heartbeatSecret;
    }

    /** bcrypt 雜湊字串（非明文）。 */
    String adminPasswordHash() {
        return adminPasswordHash;
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
        String adminRaw = envOrDefault(env, "ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);
        String adminHash = normalizeAdminPassword(adminRaw, isProduction(env));
        return new AuthConfig(
                adminHash,
                envOrDefault(env, "HEARTBEAT_SECRET", DEFAULT_HEARTBEAT_SECRET));
    }

    static boolean isProduction(Map<String, String> env) {
        String render = env.get("RENDER");
        return render != null && !render.isEmpty();
    }

    static boolean looksLikeBcrypt(String value) {
        return value != null && value.length() >= 59
                && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    static String normalizeAdminPassword(String raw, boolean production) {
        if (looksLikeBcrypt(raw)) {
            return raw;
        }
        if (production) {
            throw new IllegalStateException("正式環境 ADMIN_PASSWORD 須為 bcrypt 雜湊");
        }
        return BCrypt.hashpw(raw, BCrypt.gensalt(10));
    }

    private static void rejectDefaultSecretsOnProduction(Map<String, String> env) {
        List<String> invalid = new ArrayList<>();
        String admin = env.get("ADMIN_PASSWORD");
        if (admin == null || admin.isEmpty() || DEFAULT_ADMIN_PASSWORD.equals(admin)) {
            invalid.add("ADMIN_PASSWORD");
        } else if (!looksLikeBcrypt(admin)) {
            System.err.println("Render 啟動已中止：環境變數 ADMIN_PASSWORD 須為 bcrypt 雜湊（請先在本機產生雜湊後寫入 Render）");
            throw new IllegalStateException("正式環境禁止使用明文 ADMIN_PASSWORD");
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