package com.example.service;

import com.example.BuildInfo;
import com.example.ClientUpdatePolicy;
import com.example.MarqueeNotice;
import com.example.PeerFileRules;
import com.example.ReleaseNotes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/** 解析心跳回應裡的指令、同事、更新通知與跑馬燈。 */
final class HeartbeatResponseParser {

    private final HeartbeatEvents host;

    HeartbeatResponseParser(HeartbeatEvents host) {
        this.host = host;
    }

    void parse(String body, Consumer<String> logger) {
        if (body == null || body.isBlank()) return;

        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            parseServerActions(json, logger);
            parseServerPeers(json);
            parseServerUpdate(json);
            parseMarquee(json);
        } catch (Exception ex) {
            // 回應非 JSON 或格式異常時靜默忽略
        }
    }

    private void parseServerActions(JsonObject json, Consumer<String> logger) {
        if (host.commandListener() == null) return;

        java.util.LinkedHashSet<String> actions = new java.util.LinkedHashSet<>();

        if (json.has("actions") && json.get("actions").isJsonArray()) {
            for (com.google.gson.JsonElement el : json.getAsJsonArray("actions")) {
                if (el != null && el.isJsonPrimitive()) {
                    String a = el.getAsString();
                    if (a != null && !a.isBlank() && !"NONE".equalsIgnoreCase(a)) {
                        actions.add(a.trim());
                    }
                }
            }
        } else if (json.has("action") && json.get("action").isJsonPrimitive()) {
            String action = json.get("action").getAsString();
            if (action != null && !action.isBlank() && !"NONE".equalsIgnoreCase(action)) {
                actions.add(action.trim());
            }
        }

        for (String action : actions) {
            dispatchServerAction(action, logger);
        }
    }

    private void dispatchServerAction(String action, Consumer<String> logger) {
        if ("CANCEL_SCHEDULE".equals(action)) {
            host.log(logger, "[取消] [HTTP 心跳] 收到伺服器取消排程指令 (CANCEL_SCHEDULE)");
            host.commandListener().accept("CANCEL_SCHEDULE");
        } else if (action.startsWith("CANCEL_TASK:")) {
            host.log(logger, "[取消] [HTTP 心跳] 收到伺服器取消特定任務指令 (" + action + ")");
            host.commandListener().accept(action);
        } else if (action.startsWith("MSG|")) {
            // MSG|fromId|base64text
            // MSG|fromId|base64text|epochMs
            // MSG|fromId|base64text|epochMs|avatar
            String[] parts = action.split("\\|", 5);
            if (parts.length >= 3) {
                String fromId = parts[1];
                String text = decodePeerPayload(parts[2]);
                String sentAtMs = parts.length >= 4 ? parts[3].trim() : "";
                String avatar = parts.length >= 5 ? parts[4].trim() : "";
                host.log(logger, "[訊息] [戳] 收到來自【" + fromId + "】的訊息");
                host.commandListener().accept("MSG|" + fromId + "|" + sentAtMs + "|" + avatar + "|" + text);
            }
        } else if (action.startsWith("MSGI|")) {
            // MSGI|fromId|base64text|epochMs|avatar|image（avatar／text 可為空）
            String[] parts = action.split("\\|", 6);
            if (parts.length == 6) {
                String fromId = parts[1];
                String text = decodePeerPayload(parts[2]);
                String sentAtMs = parts[3].trim();
                String avatar = parts[4].trim();
                String image = parts[5].trim();
                host.log(logger, "[訊息] [戳] 收到來自【" + fromId + "】的圖片訊息");
                // 對 App：MSGI|fromId|sentAtMs|avatar|image|text（text 放最後，可含 |）
                host.commandListener().accept("MSGI|" + fromId + "|" + sentAtMs + "|" + avatar + "|" + image + "|" + text);
            }
        } else if (action.startsWith("POKE|")) {
            // POKE|fromId 或 POKE|fromId|epochMs 或 POKE|fromId|epochMs|avatar
            String[] parts = action.split("\\|", 4);
            String fromId = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "未知";
            String sentAtMs = parts.length >= 3 ? parts[2].trim() : "";
            String avatar = parts.length >= 4 ? parts[3].trim() : "";
            host.log(logger, "[通知] [戳] 【" + fromId + "】戳了你");
            host.commandListener().accept("POKE|" + fromId + "|" + sentAtMs + "|" + avatar);
        } else if (action.startsWith("RELAY|")) {
            // RELAY|base64fromId|relayId|base64name|size|kind|epochMs
            String[] parts = action.split("\\|", -1);
            if (parts.length == 7 && !parts[2].isBlank()) {
                String fromId = PeerFileRules.decodeName(parts[1]);
                String filename = PeerFileRules.decodeName(parts[3]);
                host.log(logger, "[檔案] 【" + fromId + "】要直傳檔案給你：" + filename);
                host.commandListener().accept("RELAY|" + parts[1] + "|" + parts[2] + "|" + parts[4]
                        + "|" + PeerFileRules.normalizeKind(parts[5]) + "|" + parts[6] + "|" + filename);
            }
        } else {
            host.log(logger, "[警告] [HTTP 心跳] 收到未支援的遠端指令: " + action);
        }
    }

    private void parseServerPeers(JsonObject json) {
        if (host.peersListener() == null || !json.has("peers") || !json.get("peers").isJsonArray()) {
            return;
        }
        List<HeartbeatService.PeerInfo> peers = new ArrayList<>();
        for (com.google.gson.JsonElement el : json.getAsJsonArray("peers")) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject p = el.getAsJsonObject();
            String id = p.has("clientId") ? p.get("clientId").getAsString() : "";
            if (id.isBlank()) continue;
            String status = p.has("status") ? p.get("status").getAsString() : "UNKNOWN";
            String appVersion = p.has("appVersion") ? p.get("appVersion").getAsString() : "";
            int taskCount = p.has("taskCount") ? p.get("taskCount").getAsInt() : 0;
            int scheduledCount = p.has("scheduledCount") ? p.get("scheduledCount").getAsInt() : 0;
            String lastSeen = p.has("lastSeen") ? p.get("lastSeen").getAsString() : "";
            peers.add(new HeartbeatService.PeerInfo(id, status, appVersion, taskCount, scheduledCount, lastSeen));
        }
        host.peersListener().accept(peers);
    }

    private void parseMarquee(JsonObject json) {
        if (host.marqueeListener() == null || !json.has("marquee") || !json.get("marquee").isJsonObject()) {
            return;
        }
        JsonObject marquee = json.getAsJsonObject("marquee");
        boolean enabled = false;
        if (marquee.has("enabled") && marquee.get("enabled").isJsonPrimitive()
                && marquee.get("enabled").getAsJsonPrimitive().isBoolean()) {
            enabled = marquee.get("enabled").getAsBoolean();
        }
        String text = "";
        if (marquee.has("text") && marquee.get("text").isJsonPrimitive()) {
            text = marquee.get("text").getAsString();
        }
        host.marqueeListener().accept(MarqueeNotice.normalize(enabled, text));
    }

    /** 每次成功的心跳都通知。沒有新版時傳 null，畫面才知道已經檢查過、目前就是最新。 */
    private void parseServerUpdate(JsonObject json) {
        if (host.updateListener() == null) {
            return;
        }
        host.updateListener().accept(updateNoticeFrom(json));
    }

    /** 格式不符時回 null。{@link HeartbeatService#parseUpdateNotice} 轉呼叫這裡。 */
    static HeartbeatService.UpdateNotice parseUpdateNotice(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return updateNoticeFrom(JsonParser.parseString(body).getAsJsonObject());
        } catch (Exception ex) {
            return null;
        }
    }

    private static HeartbeatService.UpdateNotice updateNoticeFrom(JsonObject json) {
        if (json == null || !json.has("update") || !json.get("update").isJsonObject()) {
            return null;
        }
        JsonObject update = json.getAsJsonObject("update");
        String version = jsonString(update, "version");
        String digest = BuildInfo.normalizeDigest(jsonString(update, "sourceDigest"));
        String sha256 = BuildInfo.normalizeDigest(jsonString(update, "sha256"));
        String path = jsonString(update, "path");
        long size = jsonLong(update, "size");
        if (digest.isEmpty() || sha256.isEmpty() || size <= 0 || !ClientUpdatePolicy.JAR_PATH.equals(path)) {
            return null;
        }
        return new HeartbeatService.UpdateNotice(
                version, digest, jsonString(update, "releaseTime"), sha256, size, path,
                jsonChanges(update), jsonInt(update, "changesMore"));
    }

    private static List<String> jsonChanges(JsonObject update) {
        List<String> changes = new ArrayList<>();
        if (update == null || !update.has("changes") || !update.get("changes").isJsonArray()) {
            return changes;
        }
        for (JsonElement element : update.getAsJsonArray("changes")) {
            if (changes.size() >= ReleaseNotes.MAX_SHOWN || element == null || !element.isJsonPrimitive()) {
                continue;
            }
            String subject = ReleaseNotes.cleanSubject(element.getAsString());
            if (!subject.isEmpty()) {
                changes.add(subject);
            }
        }
        return changes;
    }

    private static int jsonInt(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return 0;
        }
        try {
            return Math.max(0, obj.get(key).getAsInt());
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String jsonString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ex) {
            return String.valueOf(obj.get(key));
        }
    }

    private static long jsonLong(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return 0L;
        }
        try {
            return obj.get(key).getAsLong();
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static String decodePeerPayload(String base64url) {
        if (base64url == null || base64url.isBlank()) return "";
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(base64url);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return base64url;
        }
    }
}
