package com.example.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** 以帳號密碼向伺服器換取 App token（心跳綁定用）。 */
public final class AppLoginClient {

    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private AppLoginClient() {
    }

    public static String fetchToken(String serverUrl, String username, String password) throws Exception {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("請先填 Server 雲端網址");
        }
        String base = serverUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String json = "{\"username\":"
                + GSON.toJson(username == null ? "" : username.trim())
                + ",\"password\":"
                + GSON.toJson(password == null ? "" : password)
                + "}";
        HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/app/login"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("登入失敗（HTTP " + response.statusCode() + "）");
        }
        Map<String, Object> body = GSON.fromJson(response.body(), new TypeToken<Map<String, Object>>() { }.getType());
        if (body == null || body.get("token") == null) {
            throw new IllegalStateException("伺服器未回傳 token");
        }
        return String.valueOf(body.get("token")).trim();
    }
}