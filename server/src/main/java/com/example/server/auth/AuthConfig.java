package com.example.server.auth;

import java.util.Map;

/**
 * 管理員認證設定；production 預設由環境變數載入，測試可注入獨立密碼。
 */
public final class AuthConfig {

    private final String adminPassword;
    private final String heartbeatSecret;
    private final String publicBaseUrl;
    private final java.util.Set<String> allowedOrigins;

    private AuthConfig(String adminPassword, String heartbeatSecret, String publicBaseUrl,
                       java.util.Set<String> allowedOrigins) {
        this.adminPassword = adminPassword;
        this.heartbeatSecret = heartbeatSecret;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        this.allowedOrigins = allowedOrigins == null ? java.util.Set.of() : allowedOrigins;
    }

    public String adminPassword() {
        return adminPassword;
    }

    public String heartbeatSecret() {
        return heartbeatSecret;
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public java.util.Set<String> allowedOrigins() {
        return allowedOrigins;
    }

    public static AuthConfig fromEnv() {
        return from(System.getenv());
    }

    public static AuthConfig from(Map<String, String> env) {
        return new AuthConfig(
                envOrDefault(env, "ADMIN_PASSWORD", "secret"),
                envOrDefault(env, "HEARTBEAT_SECRET", "punchclock-dev-secret"),
                envOrDefault(env, "PUBLIC_BASE_URL", ""),
                AdminOriginGuard.parseAllowedOriginsList(env.get("ALLOWED_ORIGINS")));
    }

    private static String envOrDefault(Map<String, String> env, String key, String defaultValue) {
        String value = env.get(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}