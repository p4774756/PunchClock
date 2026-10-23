package com.example.service;

import com.example.ChatProtocol;

import java.net.http.HttpClient;
import java.util.List;
import java.util.function.Consumer;

/** 心跳連線當下的位址、憑證與日誌。傳檔與訊息客戶端共用。 */
interface HeartbeatEndpoint {
    HttpClient client();

    String url();

    String workerId();

    String token();

    boolean active();

    String avatar();

    void log(Consumer<String> logger, String message);
}

/** 心跳回應要通知的監聽器。 */
interface HeartbeatEvents extends HeartbeatEndpoint {
    Consumer<String> commandListener();

    Consumer<List<HeartbeatService.PeerInfo>> peersListener();

    Consumer<List<HeartbeatService.PeerFileInfo>> filesListener();

    Consumer<List<HeartbeatService.ChatRoomInfo>> chatRoomsListener();

    Consumer<ChatProtocol.RoomMessage> roomMessageListener();

    Consumer<HeartbeatService.UpdateNotice> updateListener();
}
