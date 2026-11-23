package com.example.server.auth;

import com.example.server.store.AccountStore;
import com.example.server.store.AppTokenStore;
import com.example.server.store.DeviceBindingStore;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.HttpStatus;
import io.javalin.http.SameSite;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MS = 15L * 60L * 1000L;
    private static final int COOKIE_MAX_AGE_SEC = 60 * 60 * 24;

    public static final class AuthUser {
        public final String username;
        public final String role;
        public final boolean mustChangePassword;
        public final String sessionId;

        public AuthUser(String username, String role, boolean mustChangePassword, String sessionId) {
            this.username = username;
            this.role = role;
            this.mustChangePassword = mustChangePassword;
            this.sessionId = sessionId;
        }

        public boolean isAdmin() {
            return AccountStore.ROLE_ADMIN.equals(role);
        }
    }

    private final AuthConfig config;
    private final AccountStore accountStore;
    private final AppTokenStore appTokenStore;
    private final DeviceBindingStore deviceBindingStore;
    private final AdminSessionStore sessionStore;
    private final Clock clock;
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    public AuthService() {
        this(AuthConfig.fromEnv(), null);
    }

    public AuthService(AuthConfig config) {
        this(config, null);
    }

    public AuthService(AuthConfig config, Path accountDataDir) {
        this(config, accountDataDir, Clock.systemUTC());
    }

    public AuthService(AuthConfig config, Path accountDataDir, Clock clock) {
        this.config = config;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        Path dir = accountDataDir;
        this.accountStore = new AccountStore(dir, config);
        this.appTokenStore = new AppTokenStore(dir);
        this.deviceBindingStore = new DeviceBindingStore(dir);
        this.sessionStore = new AdminSessionStore(this.clock);
    }

    public AccountStore accountStore() {
        return accountStore;
    }

    public AppTokenStore appTokenStore() {
        return appTokenStore;
    }

    public DeviceBindingStore deviceBindingStore() {
        return deviceBindingStore;
    }

    public AdminSessionStore sessionStore() {
        return sessionStore;
    }

    public String heartbeatSecret() {
        return config.heartbeatSecret();
    }

    /** 僅測試相容：舊版 deterministic cookie 值（生產 session 不再使用）。 */
    @Deprecated
    public String authCookieValue() {
        return sha256HexPrefix("punchclock-admin:" + config.adminPassword(), 32);
    }

    public Optional<AuthUser> currentUser(Context ctx) {
        String cookie = ctx.cookie("auth");
        if (cookie == null) {
            cookie = parseCookieHeader(ctx.header("Cookie")).get("auth");
        }
        return resolveUser(cookie, true);
    }

    public boolean isAuth(Context ctx) {
        return currentUser(ctx).filter(u -> !u.mustChangePassword).isPresent();
    }

    /** 已登入之 Cookie 寫入：同源檢查 + CSRF。允許 {@code mustChangePassword} 改密碼。 */
    public boolean enforceAdminWrite(Context ctx) {
        Optional<AuthUser> user = currentUser(ctx);
        if (user.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入"));
            return false;
        }
        if (!AdminOriginGuard.allowBrowserWrite(ctx, config)) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("success", false, "message", "來源驗證失敗"));
            return false;
        }
        String csrf = ctx.header("X-Csrf-Token");
        if (!sessionStore.verifyCsrf(user.get().sessionId, csrf)) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("success", false, "message", "CSRF 驗證失敗"));
            return false;
        }
        return true;
    }

    /** 登入等尚無 session CSRF 的瀏覽器寫入。 */
    public boolean enforceBrowserWrite(Context ctx) {
        if (!AdminOriginGuard.allowBrowserWrite(ctx, config)) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("success", false, "message", "來源驗證失敗"));
            return false;
        }
        return true;
    }

    public Optional<String> csrfTokenForSession(String sessionId) {
        return sessionStore.csrfTokenFor(sessionId);
    }

    public boolean isAuthFromCookieHeader(String cookieHeader) {
        String cookie = parseCookieHeader(cookieHeader).get("auth");
        return resolveUser(cookie, false).filter(u -> !u.mustChangePassword).isPresent();
    }

    public Optional<AuthUser> resolveUser(String sessionId, boolean touch) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Optional.empty();
        }
        if (!AdminSessionStore.isValidCookieFormat(sessionId)) {
            return Optional.empty();
        }
        Optional<AdminSessionStore.SessionRecord> session = sessionStore.resolve(sessionId, touch);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        Optional<AccountStore.Account> account = accountStore.find(session.get().username);
        if (account.isEmpty() || !account.get().enabled) {
            sessionStore.revoke(sessionId);
            return Optional.empty();
        }
        return Optional.of(new AuthUser(
                account.get().username,
                account.get().role,
                account.get().mustChangePassword,
                sessionId));
    }

    public Optional<AuthUser> login(String username, String password) {
        if (!accountStore.verifyPassword(username, password)) {
            return Optional.empty();
        }
        Optional<AccountStore.Account> account = accountStore.find(username);
        if (account.isEmpty()) {
            return Optional.empty();
        }
        String sessionId = sessionStore.createSession(username, account.get().role);
        return Optional.of(new AuthUser(username, account.get().role, account.get().mustChangePassword, sessionId));
    }

    public void logout(String sessionId) {
        sessionStore.revoke(sessionId);
    }

    public void revokeAllSessionsForUser(String username) {
        sessionStore.revokeAllForUser(username);
        appTokenStore.revokeAllForUser(username);
    }

    public Optional<String> loginApp(String username, String password) {
        if (!accountStore.verifyPassword(username, password)) {
            return Optional.empty();
        }
        Optional<AccountStore.Account> account = accountStore.find(username);
        if (account.isEmpty() || account.get().mustChangePassword) {
            return Optional.empty();
        }
        appTokenStore.revokeAllForUser(username);
        return Optional.of(appTokenStore.issueToken(username));
    }

    public Optional<String> resolveAppUsername(String token) {
        return appTokenStore.resolveUsername(token);
    }

    public boolean isHeartbeatAuthorized(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        return config.heartbeatSecret().equals(authHeader.substring(7));
    }

    public boolean clientMayUseFromClientId(Context ctx, String fromClientId) {
        if (fromClientId == null || fromClientId.isEmpty()) {
            return false;
        }
        if (!deviceBindingStore.isBound(fromClientId)) {
            return true;
        }
        String caller = com.example.server.web.RouteSupport.uploadRequester(ctx);
        return !caller.isEmpty() && fromClientId.equals(caller);
    }

    public void applyAuthCookie(Context ctx, String sessionId) {
        Cookie cookie = new Cookie("auth", sessionId);
        cookie.setMaxAge(COOKIE_MAX_AGE_SEC);
        cookie.setHttpOnly(true);
        cookie.setSameSite(SameSite.LAX);
        if (isHttps(ctx) || cookieSecureEnv()) {
            cookie.setSecure(true);
        }
        cookie.setPath("/");
        ctx.cookie(cookie);
    }

    public void clearAuthCookie(Context ctx) {
        Cookie cookie = new Cookie("auth", "");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSameSite(SameSite.LAX);
        cookie.setPath("/");
        ctx.cookie(cookie);
    }

    public LoginLockStatus getLoginLockStatus(String ip) {
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
    }

    public void clearLoginAttempt(String ip) {
        loginAttempts.remove(ip);
    }

    public boolean checkAdminPassword(String password) {
        return accountStore.verifyPassword("root", password);
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

    private static boolean cookieSecureEnv() {
        String v = System.getenv("COOKIE_SECURE");
        if (v == null) {
            return false;
        }
        return "true".equalsIgnoreCase(v) || "1".equals(v);
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