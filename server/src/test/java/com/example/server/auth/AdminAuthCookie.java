package com.example.server.auth;

/**
 * 測試用：產生與目前程序 {@link AuthService} 相同的 admin {@code auth} Cookie。
 * 依環境變數 {@code ADMIN_PASSWORD}（或預設值）推導，不讀取、不輸出明文密碼。
 */
public final class AdminAuthCookie {

    private AdminAuthCookie() {
    }

    /** {@code Cookie} 標頭用片段，例如 {@code auth=...}。 */
    public static String header() {
        return "auth=" + new AuthService().authCookieValue();
    }
}
