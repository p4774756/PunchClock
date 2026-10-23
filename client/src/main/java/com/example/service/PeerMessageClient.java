package com.example.service;

import com.example.ChatProtocol;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** 同事訊息、戳一下，以及後台聊天室文字。 */
final class PeerMessageClient {

    private final HeartbeatEndpoint endpoint;
    private final Gson gson = new Gson();

    PeerMessageClient(HeartbeatEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 傳送訊息給同事（經伺服器中繼）
     */
    void sendPeerMessage(String toClientId, String text, Consumer<String> logger, Consumer<Boolean> callback) {
        sendPeerMessage(toClientId, text, null, logger, callback);
    }

    /**
     * @param imageEncoded {@link PeerImage#encodeFile} 產生的 Base64 URL JPEG；有圖時文字可為空
     */
    void sendPeerMessage(String toClientId, String text, String imageEncoded,
                                Consumer<String> logger, Consumer<Boolean> callback) {
        boolean hasImage = imageEncoded != null && !imageEncoded.isBlank();
        if (!endpoint.active() || endpoint.url().isBlank()) {
            endpoint.log(logger, "[警告] [戳] 雲端未連線，無法傳送訊息");
            if (callback != null) callback.accept(false);
            return;
        }
        if (toClientId == null || toClientId.isBlank()) {
            endpoint.log(logger, "[警告] [戳] 請選擇收件同事");
            if (callback != null) callback.accept(false);
            return;
        }
        if (!hasImage && (text == null || text.trim().isEmpty())) {
            endpoint.log(logger, "[警告] [戳] 訊息不可為空");
            if (callback != null) callback.accept(false);
            return;
        }

        String trimmed = text == null ? "" : text.trim();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromClientId", endpoint.workerId());
        payload.put("toClientId", toClientId.trim());
        payload.put("text", trimmed);
        if (endpoint.avatar() != null && !endpoint.avatar().isBlank()) {
            payload.put("avatar", endpoint.avatar());
        }
        if (hasImage) {
            payload.put("image", imageEncoded.trim());
        }
        postPeerApi("/api/peer/message", payload, logger, callback,
                (hasImage ? "圖片訊息給【" : "訊息給【") + toClientId.trim() + "】：" + (trimmed.isEmpty() ? "（僅圖片）" : trimmed));
    }

    /** 傳文字給後台私訊或已加入的群組。 */
    void sendRoomMessage(String roomId, String text, Consumer<String> logger, Consumer<Boolean> callback) {
        if (!endpoint.active() || endpoint.url().isBlank()) {
            endpoint.log(logger, "[警告] [聊天] 雲端未連線，無法傳送訊息");
            if (callback != null) callback.accept(false);
            return;
        }
        if (!ChatProtocol.isDeskRoom(roomId)) {
            endpoint.log(logger, "[警告] [聊天] 找不到這段對話");
            if (callback != null) callback.accept(false);
            return;
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            endpoint.log(logger, "[警告] [聊天] 訊息不可為空");
            if (callback != null) callback.accept(false);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromClientId", endpoint.workerId());
        payload.put("roomId", roomId);
        payload.put("text", trimmed);
        postPeerApi("/api/chat/send", payload, logger, callback, "訊息到【" + roomId + "】：" + trimmed);
    }

    /**
     * 戳一下同事（經伺服器中繼）
     */
    void sendPeerPoke(String toClientId, Consumer<String> logger, Consumer<Boolean> callback) {
        if (!endpoint.active() || endpoint.url().isBlank()) {
            endpoint.log(logger, "[警告] [戳] 雲端未連線，無法戳同事");
            if (callback != null) callback.accept(false);
            return;
        }
        if (toClientId == null || toClientId.isBlank()) {
            endpoint.log(logger, "[警告] [戳] 請選擇同事");
            if (callback != null) callback.accept(false);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromClientId", endpoint.workerId());
        payload.put("toClientId", toClientId.trim());
        if (endpoint.avatar() != null && !endpoint.avatar().isBlank()) {
            payload.put("avatar", endpoint.avatar());
        }
        postPeerApi("/api/peer/poke", payload, logger, callback,
                "戳一下給【" + toClientId.trim() + "】");
    }

    private void postPeerApi(String path, Map<String, Object> payload,
                             Consumer<String> logger, Consumer<Boolean> callback, String actionLabel) {
        String target = endpoint.url() + path;
        String jsonBody = gson.toJson(payload);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + endpoint.token())
                    .timeout(HeartbeatService.REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            endpoint.client().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        boolean ok = response.statusCode() == 200;
                        if (ok) {
                            endpoint.log(logger, "[成功] [戳] 已送出" + actionLabel);
                        } else {
                            endpoint.log(logger, "[失敗] [戳] 送出" + actionLabel + "失敗，狀態碼：" + response.statusCode());
                        }
                        if (callback != null) callback.accept(ok);
                    })
                    .exceptionally(ex -> {
                        endpoint.log(logger, "[失敗] [戳] 送出" + actionLabel + "異常：" + ex.getMessage());
                        if (callback != null) callback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            endpoint.log(logger, "[失敗] [戳] 送出" + actionLabel + "異常：" + ex.getMessage());
            if (callback != null) callback.accept(false);
        }
    }
}
