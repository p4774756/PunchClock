package com.example.service;

import com.example.BuildInfo;
import com.example.ChatProtocol;
import com.example.ClientUpdatePolicy;
import com.example.PeerFileRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/** 解析心跳回應裡的指令、同事、傳檔、聊天室與更新通知。 */
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
            parseServerFiles(json);
            parseServerChatRooms(json);
            parseServerUpdate(json);
        } catch (Exception ex) {
            // 回應非 JSON 或格式異常時靜默忽略
        }
    }

    private void parseServerActions(JsonObject json, Consumer<String> logger) {
        if (host.commandListener() == null && host.roomMessageListener() == null) return;

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
            if (action.startsWith(ChatProtocol.PREFIX)) {
                ChatProtocol.RoomMessage message = ChatProtocol.decodeRoom(action);
                if (message != null && host.roomMessageListener() != null) {
                    host.log(logger, "[訊息] [聊天] 來自【" + message.nick + "】");
                    host.roomMessageListener().accept(message);
                }
                continue;
            }
            if (host.commandListener() != null) {
                dispatchServerAction(action, logger);
            }
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
        } else if (action.startsWith("FILE|")) {
            // FILE|fromId|fileId|base64name|size|mime|epochMs（fromId 可含 |）
            String[] parts = HeartbeatService.splitPeerFileAction(action);
            if (parts.length == 6) {
                String fromId = parts[0];
                String fileId = parts[1];
                String filename = PeerFileRules.decodeName(parts[2]);
                String size = parts[3];
                String mime = parts[4];
                String sentAtMs = parts[5];
                host.log(logger, "[檔案] 收到來自【" + fromId + "】的檔案：" + filename);
                host.commandListener().accept(
                        "FILE|" + PeerFileRules.encodeName(fromId) + "|" + fileId + "|" + size
                                + "|" + mime + "|" + sentAtMs + "|" + filename);
            }
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

    private void parseServerChatRooms(JsonObject json) {
        if (host.chatRoomsListener() == null || !json.has("chatRooms") || !json.get("chatRooms").isJsonArray()) {
            return;
        }
        List<HeartbeatService.ChatRoomInfo> rooms = new ArrayList<>();
        for (com.google.gson.JsonElement el : json.getAsJsonArray("chatRooms")) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject room = el.getAsJsonObject();
            String id = jsonString(room, "id");
            if (!ChatProtocol.isGroupRoom(id)) {
                continue;
            }
            rooms.add(new HeartbeatService.ChatRoomInfo(id, jsonString(room, "name"), jsonString(room, "kind")));
        }
        host.chatRoomsListener().accept(rooms);
    }

    private void parseServerUpdate(JsonObject json) {
        if (host.updateListener() == null) {
            return;
        }
        HeartbeatService.UpdateNotice notice = updateNoticeFrom(json);
        if (notice != null) {
            host.updateListener().accept(notice);
        }
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
        return new HeartbeatService.UpdateNotice(version, digest, jsonString(update, "releaseTime"), sha256, size, path);
    }

    private void parseServerFiles(JsonObject json) {
        if (host.filesListener() == null || !json.has("files") || !json.get("files").isJsonArray()) {
            return;
        }
        List<HeartbeatService.PeerFileInfo> files = new ArrayList<>();
        for (com.google.gson.JsonElement el : json.getAsJsonArray("files")) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject f = el.getAsJsonObject();
            String fileId = jsonString(f, "fileId");
            if (fileId.isBlank()) continue;
            files.add(new HeartbeatService.PeerFileInfo(
                    fileId,
                    jsonString(f, "fromClientId"),
                    jsonString(f, "toClientId"),
                    jsonString(f, "filename"),
                    jsonString(f, "mime"),
                    jsonString(f, "kind"),
                    jsonString(f, "status"),
                    jsonLong(f, "size"),
                    jsonLong(f, "createdAtMs"),
                    jsonLong(f, "expiresAtMs"),
                    (int) jsonLong(f, "downloadCount")
            ));
        }
        host.filesListener().accept(files);
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
