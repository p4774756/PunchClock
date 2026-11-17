package com.example.server.store;

import java.net.http.HttpRequest;

/** 共用：有 PUNCHCLOCK_GITHUB_TOKEN 才加 Authorization。Token 不可寫入 JSON／log。 */
public final class GithubTokenHeaders {

    private GithubTokenHeaders() {
    }

    public static void apply(HttpRequest.Builder builder) {
        if (builder == null) {
            return;
        }
        String token = System.getenv("PUNCHCLOCK_GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            return;
        }
        String value = token.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)
                && !value.regionMatches(true, 0, "token ", 0, 6)) {
            value = "Bearer " + value;
        }
        builder.header("Authorization", value);
    }
}
