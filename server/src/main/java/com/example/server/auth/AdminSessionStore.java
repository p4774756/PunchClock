package com.example.server.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** 後台 session（記憶體）；只存 session ID 的 SHA-256。 */
public final class AdminSessionStore {

    private static final int SESSION_ID_BYTES = 32;
    private static final int SESSION_ID_CHARS = 43;
    private static final Pattern LEGACY_COOKIE = Pattern.compile("^[0-9a-f]{32}$");

    public static final class SessionRecord {
        public final String username;
        public final String role;
        public final long createdAt;
        final String csrfToken;
        private final AtomicLong lastSeen;

        SessionRecord(String username, String role, long createdAt, long lastSeen, String csrfToken) {
            this.username = username;
            this.role = role;
            this.createdAt = createdAt;
            this.csrfToken = csrfToken;
            this.lastSeen = new AtomicLong(lastSeen);
        }

        long lastSeen() {
            return lastSeen.get();
        }

        void touch(long now) {
            lastSeen.set(now);
        }
    }

    private final long absoluteTtlMs;
    private final long idleTtlMs;
    private final long lastSeenThrottleMs;
    private final Clock clock;
    private final SecureRandom random;
    private final ConcurrentHashMap<String, SessionRecord> byKey = new ConcurrentHashMap<>();

    public AdminSessionStore(Clock clock) {
        this(clock, 24L * 60L * 60L * 1000L, 2L * 60L * 60L * 1000L, 60_000L);
    }

    public AdminSessionStore(Clock clock, long absoluteTtlMs, long idleTtlMs, long lastSeenThrottleMs) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.absoluteTtlMs = absoluteTtlMs;
        this.idleTtlMs = idleTtlMs;
        this.lastSeenThrottleMs = lastSeenThrottleMs;
        this.random = new SecureRandom();
    }

    public static boolean isValidCookieFormat(String cookie) {
        if (cookie == null || cookie.length() != SESSION_ID_CHARS) {
            return false;
        }
        if (LEGACY_COOKIE.matcher(cookie).matches()) {
            return false;
        }
        for (int i = 0; i < cookie.length(); i++) {
            char c = cookie.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public String createSession(String username, String role) {
        byte[] id = new byte[SESSION_ID_BYTES];
        random.nextBytes(id);
        String sessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(id);
        byte[] csrfBytes = new byte[SESSION_ID_BYTES];
        random.nextBytes(csrfBytes);
        String csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(csrfBytes);
        String key = sha256Hex(sessionId);
        long now = clock.millis();
        byKey.put(key, new SessionRecord(username, role, now, now, csrfToken));
        return sessionId;
    }

    public Optional<String> csrfTokenFor(String sessionId) {
        if (!isValidCookieFormat(sessionId)) {
            return Optional.empty();
        }
        SessionRecord record = byKey.get(sha256Hex(sessionId));
        if (record == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(record.csrfToken);
    }

    public boolean verifyCsrf(String sessionId, String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        Optional<String> expected = csrfTokenFor(sessionId);
        if (expected.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.get().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public Optional<SessionRecord> resolve(String sessionId, boolean touch) {
        if (!isValidCookieFormat(sessionId)) {
            return Optional.empty();
        }
        purgeExpired();
        String key = sha256Hex(sessionId);
        SessionRecord record = byKey.get(key);
        if (record == null) {
            return Optional.empty();
        }
        long now = clock.millis();
        if (isExpired(record, now)) {
            byKey.remove(key);
            return Optional.empty();
        }
        if (touch && now - record.lastSeen() >= lastSeenThrottleMs) {
            record.touch(now);
        }
        return Optional.of(record);
    }

    public void revoke(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        byKey.remove(sha256Hex(sessionId));
    }

    public void revokeAllForUser(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<String, SessionRecord>> it = byKey.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, SessionRecord> e = it.next();
            if (username.equals(e.getValue().username)) {
                it.remove();
            }
        }
    }

    public String sessionKey(String sessionId) {
        return sha256Hex(sessionId);
    }

    public Optional<SessionRecord> resolveByKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isEmpty()) {
            return Optional.empty();
        }
        purgeExpired();
        SessionRecord record = byKey.get(sessionKey);
        if (record == null) {
            return Optional.empty();
        }
        long now = clock.millis();
        if (isExpired(record, now)) {
            byKey.remove(sessionKey);
            return Optional.empty();
        }
        return Optional.of(record);
    }

    private void purgeExpired() {
        long now = clock.millis();
        byKey.entrySet().removeIf(e -> isExpired(e.getValue(), now));
    }

    private boolean isExpired(SessionRecord record, long now) {
        if (now - record.createdAt > absoluteTtlMs) {
            return true;
        }
        return now - record.lastSeen() > idleTtlMs;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}