package com.example.server.web;

import com.google.gson.reflect.TypeToken;
import io.javalin.http.Context;

import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 多個路由共用的 JSON 與表單小工具。 */
public final class RouteSupport {

    public static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    public static Map<String, Object> unauthorized() {
        return Map.of("success", false, "message", "Unauthorized: invalid or missing heartbeat token");
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> tasksFromBody(Object raw) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item instanceof Map) {
                    tasks.add((Map<String, Object>) item);
                }
            }
        }
        return tasks;
    }

    public static String stringOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? defaultValue : text;
    }

    public static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static long numberFrom(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public static List<String> stringList(Object raw) {
        List<String> list = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item != null) {
                    list.add(String.valueOf(item));
                }
            }
        }
        return list;
    }

    public static String firstNonEmptyForm(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public static String decodeHeaderClientId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return java.net.URLDecoder.decode(raw.trim(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return raw.trim();
        }
    }

    public static String contentDisposition(String filename) {
        String safe = filename == null ? "download" : filename.replace("\"", "").replace("\r", "").replace("\n", "");
        // 只放 ASCII filename，避免部分 JDK HttpClient 解析 filename* 失敗而整份下載作廢。
        return "attachment; filename=\"" + asciiFilename(safe) + "\"";
    }

    public static String asciiFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "download";
        }
        StringBuilder sb = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char c = filename.charAt(i);
            if (c >= 0x20 && c < 0x7f && c != '"' && c != '\\') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String ascii = sb.toString().trim();
        return ascii.isEmpty() ? "download" : ascii;
    }

    public static long longQueryParam(Context ctx, String name, long fallback) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static String uploadRequester(Context ctx) {
        return firstNonEmptyForm(
                ctx.queryParam("clientId"),
                decodeHeaderClientId(ctx.header("X-PunchClock-Client")));
    }
}
