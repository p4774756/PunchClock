package com.example;

import com.example.ui.PeerChatWindow;

import java.util.Map;

/** 收到同事直傳 offer：視窗已開可立刻呈現接收提醒，否則只留在 pending、不自動開窗。 */
public final class InboundPeerRelayOffer {

    private InboundPeerRelayOffer() {}

    /** @return 該同事已開啟的聊天視窗；未開則 null */
    public static PeerChatWindow openChatWindow(Map<String, PeerChatWindow> chatWindows, String fromId) {
        if (chatWindows == null || fromId == null || fromId.isBlank()) {
            return null;
        }
        return chatWindows.get(fromId);
    }
}