package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.AccountStore;
import com.example.server.store.DeviceBindingStore;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 帳號與設備綁定管理 API。 */
public final class AccountRoutes {

    private final AuthService authService;
    private final DeviceBindingStore deviceBindingStore;

    public AccountRoutes(AuthService authService) {
        this.authService = authService;
        this.deviceBindingStore = authService.deviceBindingStore();
    }

    public void register(Javalin app) {
        app.get("/api/accounts", this::listAccounts);
        app.post("/api/accounts", this::createAccount);
        app.patch("/api/accounts/{username}", this::patchAccount);
        app.delete("/api/accounts/{username}", this::deleteAccount);
        app.post("/api/accounts/change-password", this::changePassword);
        app.post("/api/app/login", this::appLogin);
        app.post("/api/devices/{clientId}/assign", this::assignDevice);
        app.patch("/api/devices/{clientId}/display-name", this::displayName);
    }

    private void listAccounts(Context ctx) {
        Optional<AuthService.AuthUser> user = authService.currentUser(ctx);
        if (user.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入"));
            return;
        }
        if (!user.get().isAdmin()) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("success", false, "message", "權限不足"));
            return;
        }
        ctx.json(Map.of("success", true, "accounts", authService.accountStore().listAccountsCopy()));
    }

    private void createAccount(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        Map<String, Object> body = parseBody(ctx);
        String username = RouteSupport.stringOrNull(body.get("username"));
        String role = RouteSupport.stringOrDefault(body.get("role"), AccountStore.ROLE_MEMBER);
        String password = RouteSupport.stringOrNull(body.get("password"));
        AccountStore.OperationResult result = authService.accountStore()
                .createAccount(username, role, password, true);
        respondAccountOp(ctx, result);
    }

    private void patchAccount(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        String username = ctx.pathParam("username");
        Map<String, Object> body = parseBody(ctx);
        AccountStore.OperationResult result = AccountStore.OperationResult.OK;
        if (body.containsKey("role")) {
            result = authService.accountStore().setRole(username, RouteSupport.stringOrNull(body.get("role")));
        }
        if (result == AccountStore.OperationResult.OK && body.containsKey("enabled")) {
            boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
            result = authService.accountStore().setEnabled(username, enabled);
            if (result == AccountStore.OperationResult.OK && !enabled) {
                authService.revokeAllSessionsForUser(username);
                deviceBindingStore.unbindAllForUser(username);
            }
        }
        if (result == AccountStore.OperationResult.OK && body.containsKey("password")) {
            String password = RouteSupport.stringOrNull(body.get("password"));
            result = authService.accountStore().setPassword(username, password, false);
            if (result == AccountStore.OperationResult.OK) {
                authService.accountStore().find(username).ifPresent(a -> a.mustChangePassword = true);
                authService.revokeAllSessionsForUser(username);
            }
        }
        respondAccountOp(ctx, result);
    }

    private void deleteAccount(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        String username = ctx.pathParam("username");
        if (deviceBindingStore.hasBindingsForUser(username)) {
            ctx.status(HttpStatus.CONFLICT).json(Map.of("success", false, "message", "仍有綁定設備"));
            return;
        }
        AccountStore.OperationResult result = authService.accountStore().deleteAccount(username);
        respondAccountOp(ctx, result);
    }

    private void changePassword(Context ctx) {
        Optional<AuthService.AuthUser> user = authService.currentUser(ctx);
        if (user.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入"));
            return;
        }
        Map<String, Object> body = parseBody(ctx);
        String newPassword = RouteSupport.stringOrNull(body.get("newPassword"));
        AccountStore.OperationResult result = authService.accountStore()
                .setPassword(user.get().username, newPassword, true);
        if (result == AccountStore.OperationResult.OK) {
            authService.revokeAllSessionsForUser(user.get().username);
            Optional<AuthService.AuthUser> fresh = authService.login(user.get().username, newPassword);
            fresh.ifPresent(u -> authService.applyAuthCookie(ctx, u.sessionId));
        }
        respondAccountOp(ctx, result);
    }

    private void appLogin(Context ctx) {
        String ip = com.example.server.util.IpResolver.clientIp(ctx);
        if (authService.getLoginLockStatus(ip).isLocked()) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("success", false, "message", "登入鎖定中"));
            return;
        }
        Map<String, Object> body = parseBody(ctx);
        String username = RouteSupport.stringOrNull(body.get("username"));
        String password = RouteSupport.stringOrNull(body.get("password"));
        Optional<String> token = authService.loginApp(username, password);
        if (token.isEmpty()) {
            authService.recordLoginFailure(ip);
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "登入失敗"));
            return;
        }
        authService.clearLoginAttempt(ip);
        ctx.json(Map.of("success", true, "token", token.get()));
    }

    private void assignDevice(Context ctx) {
        if (!requireAdmin(ctx)) {
            return;
        }
        Map<String, Object> body = parseBody(ctx);
        String username = RouteSupport.stringOrNull(body.get("username"));
        deviceBindingStore.adminAssign(ctx.pathParam("clientId"), username);
        ctx.json(Map.of("success", true));
    }

    private void displayName(Context ctx) {
        Optional<AuthService.AuthUser> user = authService.currentUser(ctx);
        if (user.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入"));
            return;
        }
        String clientId = ctx.pathParam("clientId");
        if (!user.get().isAdmin()) {
            String owner = deviceBindingStore.usernameFor(clientId);
            if (!user.get().username.equals(owner)) {
                ctx.status(HttpStatus.FORBIDDEN).json(Map.of("success", false, "message", "權限不足"));
                return;
            }
        }
        Map<String, Object> body = parseBody(ctx);
        deviceBindingStore.setDisplayName(clientId, RouteSupport.stringOrNull(body.get("displayName")));
        ctx.json(Map.of("success", true));
    }

    private boolean requireAdmin(Context ctx) {
        Optional<AuthService.AuthUser> user = authService.currentUser(ctx);
        if (user.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入"));
            return false;
        }
        if (!user.get().isAdmin()) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("success", false, "message", "權限不足"));
            return false;
        }
        return true;
    }

    private static void respondAccountOp(Context ctx, AccountStore.OperationResult result) {
        switch (result) {
            case OK:
                ctx.json(Map.of("success", true));
                return;
            case LAST_ADMIN:
                ctx.status(HttpStatus.CONFLICT).json(Map.of("success", false, "message", "不可變更最後一位管理員"));
                return;
            case CONFLICT:
                ctx.status(HttpStatus.CONFLICT).json(Map.of("success", false, "message", "帳號已存在"));
                return;
            case NOT_FOUND:
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到帳號"));
                return;
            case BAD_PASSWORD:
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "密碼不符合規則"));
                return;
            case BAD_USERNAME:
            case BAD_ROLE:
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "參數無效"));
                return;
            case NOT_DISABLED:
                ctx.status(HttpStatus.CONFLICT).json(Map.of("success", false, "message", "須先停用帳號"));
                return;
            case HAS_DEVICES:
                ctx.status(HttpStatus.CONFLICT).json(Map.of("success", false, "message", "仍有綁定設備"));
                return;
            default:
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("success", false));
        }
    }

  @SuppressWarnings("unchecked")
    private static Map<String, Object> parseBody(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            return body == null ? Map.of() : body;
        } catch (Exception ex) {
            return Map.of();
        }
    }
}