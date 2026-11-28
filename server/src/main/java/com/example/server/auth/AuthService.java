package com.example.server.auth;

import io.javalin.http.Context;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int GLOBAL_MAX_ATTEMPTS = 25;
    private static final long LOCK_MS = 15L * 60L * 1000L;
    private static final long SESSION_MS = 24L * 60L * 60L * 1000L;

    private final String adminPasswordHash;
    private final String heartbeatSecret;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SessionRecord> sessionsByTokenHash = new ConcurrentHashMap<>();

    private final Object globalLock = new Object();
    private int globalAttempts;
    private long globalLockUntil;

    public AuthService() {
        this(AuthConfig.fromEnv());
    }

    public AuthService(AuthConfig config) {
        this(config, Clock.systemUTC());
    }

    public AuthService(AuthConfig config, Clock clock) {
        this.adminPasswordHash = config.adminPasswordHash();
        this.heartbeatSecret = config.heartbeatSecret();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** 登入成功後發行的 session 票（明文僅出現在 Set-Cookie，伺服器只存雜湊）。 */
    public String createSessionToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = sha256Hex(token);
        long expiresAt = clock.millis() + SESSION_MS;
        sessionsByTokenHash.put(tokenHash, new SessionRecord(expiresAt));
        purgeExpiredSessions();
        return token;
    }

    public boolean isAuth(Context ctx) {
        String cookie = ctx.cookie("auth");
        if (isValidSessionToken(cookie)) {
            return true;
        }
        return isAuthFromCookieHeader(ctx.header("Cookie"));
    }

    public boolean isAuthFromCookieHeader(String cookieHeader) {
        String token = parseCookieHeader(cookieHeader).get("auth");
        return isValidSessionToken(token);
    }

    public void revokeSession(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        sessionsByTokenHash.remove(sha256Hex(token));
    }

    public void invalidateAllSessions() {
        sessionsByTokenHash.clear();
    }

    public boolean isHeartbeatAuthorized(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        return DevStatusTokenAuth.constantTimeEquals(heartbeatSecret, authHeader.substring(7));
    }

    public LoginLockStatus getLoginLockStatus(String ip) {
        LoginLockStatus global = globalLockStatus();
        if (global.locked) {
            return global;
        }
        LoginAttempt record = loginAttempts.get(ip);
        if (record == null) {
            return new LoginLockStatus(false, 0, 0);
        }
        long now = clock.millis();
        if (record.lockUntil > 0 && record.lockUntil > now) {
            int remainingSec = (int) Math.ceil((record.lockUntil - now) / 1000.0);
            return new LoginLockStatus(true, remainingSec, record.attempts);
        }
        if (record.lockUntil > 0 && record.lockUntil <= now) {
            loginAttempts.remove(ip);
            return new LoginLockStatus(false, 0, 0);
        }
        return new LoginLockStatus(false, 0, record.attempts);
    }

    public void recordLoginFailure(String ip) {
        LoginAttempt record = loginAttempts.getOrDefault(ip, new LoginAttempt());
        record.attempts += 1;
        if (record.attempts >= MAX_ATTEMPTS) {
            record.lockUntil = clock.millis() + LOCK_MS;
        }
        loginAttempts.put(ip, record);
        recordGlobalFailure();
    }

    public void clearLoginAttempt(String ip) {
        loginAttempts.remove(ip);
        synchronized (globalLock) {
            globalAttempts = 0;
            globalLockUntil = 0;
        }
    }

    public boolean checkAdminPassword(String password) {
        if (password == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, adminPasswordHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
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

    private LoginLockStatus globalLockStatus() {
        synchronized (globalLock) {
            long now = clock.millis();
            if (globalLockUntil > 0 && globalLockUntil > now) {
                int remainingSec = (int) Math.ceil((globalLockUntil - now) / 1000.0);
                return new LoginLockStatus(true, remainingSec, globalAttempts);
            }
            if (globalLockUntil > 0 && globalLockUntil <= now) {
                globalAttempts = 0;
                globalLockUntil = 0;
            }
            return new LoginLockStatus(false, 0, globalAttempts);
        }
    }

    private void recordGlobalFailure() {
        synchronized (globalLock) {
            long now = clock.millis();
            if (globalLockUntil > 0 && globalLockUntil <= now) {
                globalAttempts = 0;
                globalLockUntil = 0;
            }
            globalAttempts += 1;
            if (globalAttempts >= GLOBAL_MAX_ATTEMPTS) {
                globalLockUntil = now + LOCK_MS;
            }
        }
    }

    private boolean isValidSessionToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String tokenHash = sha256Hex(token);
        SessionRecord record = sessionsByTokenHash.get(tokenHash);
        if (record == null) {
            return false;
        }
        long now = clock.millis();
        if (record.expiresAt <= now) {
            sessionsByTokenHash.remove(tokenHash);
            return false;
        }
        return true;
    }

    private void purgeExpiredSessions() {
        long now = clock.millis();
        Iterator<Map.Entry<String, SessionRecord>> it = sessionsByTokenHash.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SessionRecord> entry = it.next();
            if (entry.getValue().expiresAt <= now) {
                it.remove();
            }
        }
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

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
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

    private static final class LoginAttempt {
        int attempts;
        long lockUntil;
    }

    private static final class SessionRecord {
        final long expiresAt;

        SessionRecord(long expiresAt) {
            this.expiresAt = expiresAt;
        }
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