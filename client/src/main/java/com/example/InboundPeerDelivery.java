package com.example;

import com.example.service.PeerChatStore;
import com.example.ui.PeerChatWindow;

import java.util.Map;

/** 收到同事訊息：只寫紀錄與已開啟的聊天視窗，不自動開窗或搶焦點。 */
public final class InboundPeerDelivery {

    private InboundPeerDelivery() {}

    public static PeerChatStore.Entry deliver(
            Map<String, PeerChatWindow> chatWindows,
            PeerChatStore store,
            String fromId,
            String text,
            String imageEncoded,
            Long sentAtMs) {
        PeerChatStore.Entry entry = store.addMessage(fromId, false, text, imageEncoded, sentAtMs);
        PeerChatWindow window = chatWindows == null ? null : chatWindows.get(fromId);
        if (window != null) {
            window.append(entry);
        }
        return entry;
    }
}