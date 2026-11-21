package com.example.server.web;

import com.example.server.store.ClientStore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 訪客可見的今日打卡摘要（無設備名稱、網址、按鈕或原始 log）。 */
public final class OfficeCheckinPublicPayload {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<String, String> STATUS_LABEL = Map.of(
            "PENDING", "待命中",
            "SCHEDULED", "等待中",
            "CHECKING_IN", "執行中",
            "SUCCESS", "成功",
            "FAILED", "失敗",
            "CANCELLED", "已取消"
    );

    private static final Map<String, Integer> STATUS_PRIORITY = Map.of(
            "CHECKING_IN", 6,
            "FAILED", 5,
            "SUCCESS", 4,
            "SCHEDULED", 3,
            "PENDING", 2,
            "CANCELLED", 1
    );

    private OfficeCheckinPublicPayload() {
    }

    public static Map<String, Object> build(ClientStore store) {
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now(TAIPEI);
        int onlineCount = 0;
        Map<String, RowAgg> byName = new LinkedHashMap<>();
        List<Map<String, Object>> clients = store.publicClientsSnapshot();
        for (Map<String, Object> client : clients) {
            if (client == null) {
                continue;
            }
            if (isOnline(client, now)) {
                onlineCount++;
            }
            List<Map<String, Object>> tasks = tasksFrom(client);
            for (Map<String, Object> task : tasks) {
                if (task == null) {
                    continue;
                }
                String name = ClientStore.displayTaskName(task.get("name"));
                String code = effectiveStatusCode(task);
                String label = labelFor(code);
                String time = formatTimeToday(task, today);
                merge(byName, name, code, label, time);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RowAgg agg : byName.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", agg.name);
            row.put("status", agg.label);
            if (!agg.time.isEmpty()) {
                row.put("time", agg.time);
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("date", today.toString());
        out.put("onlineDevices", onlineCount);
        out.put("tasks", rows);
        out.put("detailRequiresLogin", true);
        out.put("updatedAt", Instant.now().toString());
        return out;
    }

    private static boolean isOnline(Map<String, Object> client, long now) {
        long lastSeenMs = parseInstantMillis(client.get("lastSeen"));
        if (lastSeenMs < 0) {
            return false;
        }
        return now - lastSeenMs <= ClientStore.HEARTBEAT_TIMEOUT_MS;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tasksFrom(Map<String, Object> client) {
        Object raw = client.get("tasks");
        if (!(raw instanceof List)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item instanceof Map) {
                out.add((Map<String, Object>) item);
            }
        }
        return out;
    }

    static String effectiveStatusCode(Map<String, Object> task) {
        String last = firstNonEmpty(task.get("lastResultStatus"));
        if ("SUCCESS".equals(last) || "FAILED".equals(last)) {
            return last;
        }
        return String.valueOf(task.getOrDefault("status", "PENDING"));
    }

    private static String labelFor(String code) {
        return STATUS_LABEL.getOrDefault(code, "未知");
    }

    private static void merge(Map<String, RowAgg> byName, String name, String code, String label, String time) {
        RowAgg prev = byName.get(name);
        if (prev == null) {
            byName.put(name, new RowAgg(name, code, label, time));
            return;
        }
        int nextPri = STATUS_PRIORITY.getOrDefault(code, 0);
        int prevPri = STATUS_PRIORITY.getOrDefault(prev.code, 0);
        if (nextPri > prevPri) {
            prev.code = code;
            prev.label = label;
        }
        if (prev.time.isEmpty() && !time.isEmpty()) {
            prev.time = time;
        }
    }

    private static String formatTimeToday(Map<String, Object> task, LocalDate today) {
        String raw = firstNonEmpty(task.get("actualTime"), task.get("targetTime"));
        if (raw.isEmpty()) {
            return "";
        }
        try {
            Instant inst = Instant.parse(raw);
            LocalDate day = inst.atZone(TAIPEI).toLocalDate();
            if (!day.equals(today)) {
                return "";
            }
            return TIME_FMT.format(inst.atZone(TAIPEI));
        } catch (Exception ignored) {
            String trimmed = raw.trim();
            if (trimmed.matches("\\d{1,2}:\\d{2}")) {
                return trimmed;
            }
        }
        return "";
    }

    private static long parseInstantMillis(Object value) {
        if (value == null) {
            return -1;
        }
        try {
            return Instant.parse(String.valueOf(value)).toEpochMilli();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String firstNonEmpty(Object... values) {
        for (Object value : values) {
            if (value != null) {
                String s = String.valueOf(value).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return "";
    }

    private static final class RowAgg {
        final String name;
        String code;
        String label;
        String time;

        RowAgg(String name, String code, String label, String time) {
            this.name = name;
            this.code = code;
            this.label = label;
            this.time = time == null ? "" : time;
        }
    }
}