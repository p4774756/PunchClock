package com.example.server.auth;

import io.javalin.http.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MS = 15L * 60L * 1000L;

    private final String adminPassword;
    private final String authCookieValue;
    private final String heartbeatSecret;
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    public AuthService() {
        adminPassword = envOrDefault("ADMIN_PASSWORD", "secret");
        heartbeatSecret = envOrDefault("HEARTBEAT_SECRET", "clickclick-dev-secret");
        authCookieValue = sha256HexPrefix("clickclick-admin:" + adminPassword, 32);
    }

    public String authCookieValue() {
        return authCookieValue;
    }

    public boolean isAuth(Context ctx) {
        String cookie = ctx.cookie("auth");
        if (authCookieValue.equals(cookie)) {
            return true;
        }
        return isAuthFromCookieHeader(ctx.header("Cookie"));
    }

    public boolean isAuthFromCookieHeader(String cookieHeader) {
        return authCookieValue.equals(parseCookieHeader(cookieHeader).get("auth"));
    }

    public boolean isHeartbeatAuthorized(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        return heartbeatSecret.equals(authHeader.substring(7));
    }

    public LoginLockStatus getLoginLockStatus(String ip) {
        LoginAttempt record = loginAttempts.get(ip);
        if (record == null) {
            return new LoginLockStatus(false, 0, 0);
        }
        if (record.lockUntil > 0 && record.lockUntil > System.currentTimeMillis()) {
            int remainingSec = (int) Math.ceil((record.lockUntil - System.currentTimeMillis()) / 1000.0);
            return new LoginLockStatus(true, remainingSec, record.attempts);
        }
        if (record.lockUntil > 0 && record.lockUntil <= System.currentTimeMillis()) {
            loginAttempts.remove(ip);
            return new LoginLockStatus(false, 0, 0);
        }
        return new LoginLockStatus(false, 0, record.attempts);
    }

    public void recordLoginFailure(String ip) {
        LoginAttempt record = loginAttempts.getOrDefault(ip, new LoginAttempt());
        record.attempts += 1;
        if (record.attempts >= MAX_ATTEMPTS) {
            record.lockUntil = System.currentTimeMillis() + LOCK_MS;
        }
        loginAttempts.put(ip, record);
    }

    public void clearLoginAttempt(String ip) {
        loginAttempts.remove(ip);
    }

    public boolean checkAdminPassword(String password) {
        return adminPassword.equals(password);
    }

    public boolean isHttps(Context ctx) {
        if (ctx.scheme().equalsIgnoreCase("https")) {
            return true;
        }
        String proto = ctx.header("X-Forwarded-Proto");
        if (proto != null) {
            return proto.split(",")[0].trim().equalsIgnoreCase("https");
        }
        return false;
    }

    private static Map<String, String> parseCookieHeader(String cookieHeader) {
        Map<String, String> out = new HashMap<>();
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return out;
        }
        for (String part : cookieHeader.split(";")) {
            int idx = part.indexOf('=');
            if (idx < 0) {
                continue;
            }
            String key = part.substring(0, idx).trim();
            String value = part.substring(idx + 1).trim();
            out.put(key, value);
        }
        return out;
    }

    private static String sha256HexPrefix(String input, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            String hex = bytesToHex(hash);
            return hex.substring(0, Math.min(length, hex.length()));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private static final class LoginAttempt {
        int attempts;
        long lockUntil;
    }

    public static final class LoginLockStatus {
        public final boolean locked;
        public final int remainingSec;
        public final int attempts;

        public LoginLockStatus(boolean locked, int remainingSec, int attempts) {
            this.locked = locked;
            this.remainingSec = remainingSec;
            this.attempts = attempts;
        }

        public boolean isLocked() {
            return locked;
        }
    }
}
