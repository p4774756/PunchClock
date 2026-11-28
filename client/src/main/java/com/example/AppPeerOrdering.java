package com.example;

import com.example.service.HeartbeatService.PeerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 好友列表排序：在線優先，最愛在同組內置頂。
 */
final class AppPeerOrdering {

    private AppPeerOrdering() {
    }

    /**
     * 在線排前面，離線排後面。最愛只在同一組裡排最上面，組內維持原本順序。
     */
    static List<PeerInfo> orderPeers(List<PeerInfo> peers, Set<String> favorites) {
        List<PeerInfo> onlineFavorite = new ArrayList<>();
        List<PeerInfo> online = new ArrayList<>();
        List<PeerInfo> offlineFavorite = new ArrayList<>();
        List<PeerInfo> offline = new ArrayList<>();
        if (peers != null) {
            for (PeerInfo peer : peers) {
                if (peer == null) {
                    continue;
                }
                boolean on = "ONLINE".equalsIgnoreCase(peer.status);
                boolean favorite = favorites != null && favorites.contains(peer.clientId);
                if (on && favorite) {
                    onlineFavorite.add(peer);
                } else if (on) {
                    online.add(peer);
                } else if (favorite) {
                    offlineFavorite.add(peer);
                } else {
                    offline.add(peer);
                }
            }
        }
        List<PeerInfo> ordered = new ArrayList<>();
        ordered.addAll(onlineFavorite);
        ordered.addAll(online);
        ordered.addAll(offlineFavorite);
        ordered.addAll(offline);
        return ordered;
    }
}