package com.example;

import com.example.service.HeartbeatService.PeerInfo;
import com.example.ui.NightTheme;
import com.example.ui.PanelFactory;
import com.example.ui.hud.AnimeShell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友表格刷新與雲端停用／等待狀態。
 */
final class AppPeerTableUi {

    private final App app;

    AppPeerTableUi(App app) {
        this.app = app;
    }

    void updatePeerTable(List<PeerInfo> peers) {
        if (app.peerRefs.peerTableModel == null || !app.isCloudEnabled()) {
            return;
        }

        String selectedId = app.getSelectedPeerClientId();
        String myClientId = app.heartbeatService.getClientId();
        app.peerRefs.peerTableModel.setRowCount(0);
        app.onlinePeerIds.clear();
        int onlineCount = 0;
        int friendCount = 0;
        List<PeerInfo> seenPeers = new ArrayList<>();
        Map<String, String> statusById = new LinkedHashMap<>();

        List<PeerInfo> ordered = App.orderPeers(peers, app.favoritePeerIds);

        for (PeerInfo peer : ordered) {
            if (myClientId.equals(peer.clientId)) {
                continue;
            }
            seenPeers.add(peer);
            boolean online = "ONLINE".equalsIgnoreCase(peer.status);
            if (online) {
                onlineCount++;
                app.onlinePeerIds.add(peer.clientId);
            }
            friendCount++;
            String statusText = peer.statusText != null ? peer.statusText : "";
            statusById.put(peer.clientId, statusText);
            app.peerRefs.peerTableModel.addRow(new Object[]{
                    peer.clientId,
                    app.favoritePeerIds.contains(peer.clientId)
                            ? PanelFactory.PEER_FAVORITE_ON
                            : PanelFactory.PEER_FAVORITE_OFF
            });
        }
        app.latestPeers = seenPeers;
        AnimeShell shell = app.shell;
        if (shell != null) {
            for (PeerInfo peer : seenPeers) {
                if (peer == null || peer.clientId == null) {
                    continue;
                }
                if ("ONLINE".equalsIgnoreCase(peer.status) && !app.previousOnlinePeerIds.contains(peer.clientId)) {
                    shell.flashEvent("好友「" + peer.clientId + "」上線了。", 3500L);
                    break;
                }
            }
        }
        app.previousOnlinePeerIds.clear();
        app.previousOnlinePeerIds.addAll(app.onlinePeerIds);
        if (app.peerRefs.peerTable != null) {
            app.peerRefs.peerTable.putClientProperty("peer.avatars", app.peerAvatars);
            Map<String, Boolean> onlineById = new LinkedHashMap<>();
            for (PeerInfo peer : seenPeers) {
                onlineById.put(peer.clientId, "ONLINE".equalsIgnoreCase(peer.status));
            }
            app.peerRefs.peerTable.putClientProperty(PanelFactory.PEER_ONLINE_CLIENT_KEY, onlineById);
            app.peerRefs.peerTable.putClientProperty(PanelFactory.PEER_STATUS_TEXTS_KEY, statusById);
        }

        if (app.peerRefs.peerStatusLabel != null) {
            app.peerRefs.peerStatusLabel.setText(friendCount == 0
                    ? "目前沒有其他好友在同一伺服器"
                    : "共 " + friendCount + " 位好友 · " + onlineCount + " 位在線");
            app.peerRefs.peerStatusLabel.setForeground(NightTheme.MUTED);
        }
        app.refreshMainStatusBar();
        app.setPeerInteractionEnabled(true);
        if (app.peerRefs.openCloudSettingsButton != null) {
            app.peerRefs.openCloudSettingsButton.setVisible(false);
        }
        if (app.peerRefs.peerHintLabel != null) {
            app.peerRefs.peerHintLabel.setText(
                    "只顯示同一伺服器上的好友（不含本機，每 30 秒隨心跳更新）");
        }

        if (selectedId != null && app.peerRefs.peerTable != null) {
            for (int i = 0; i < app.peerRefs.peerTableModel.getRowCount(); i++) {
                String rowId = app.parsePeerRowId(String.valueOf(app.peerRefs.peerTableModel.getValueAt(i, 0)));
                if (selectedId.equals(rowId)) {
                    app.peerRefs.peerTable.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    void refreshPeerInteractionState() {
        if (!app.isCloudEnabled()) {
            showOfflinePeerView();
            return;
        }
        app.setPeerInteractionEnabled(true);
        if (app.peerRefs.openCloudSettingsButton != null) {
            app.peerRefs.openCloudSettingsButton.setVisible(false);
        }
        if (app.peerRefs.peerHintLabel != null) {
            app.peerRefs.peerHintLabel.setText(
                    "只顯示同一伺服器上的好友（不含本機，每 30 秒隨心跳更新）");
        }
        showPeerWaitingView();
    }

    void showOfflinePeerView() {
        clearPeerTable();
        app.onlinePeerIds.clear();
        app.latestPeers = List.of();
        if (app.peerRefs.peerHintLabel != null) {
            app.peerRefs.peerHintLabel.setText(
                    "雲端未啟用；至「設定」勾選「啟用雲端單向狀態回報」後，可查看好友並互動");
        }
        if (app.peerRefs.peerStatusLabel != null) {
            app.peerRefs.peerStatusLabel.setText("雲端未啟用 · 本機獨立運作");
            app.peerRefs.peerStatusLabel.setForeground(NightTheme.MUTED);
        }
        app.refreshMainStatusBar();
        app.setPeerInteractionEnabled(false);
        if (app.peerRefs.openCloudSettingsButton != null) {
            app.peerRefs.openCloudSettingsButton.setVisible(true);
        }
        if (app.peerRefs.peerTable != null) {
            app.peerRefs.peerTable.clearSelection();
        }
    }

    void showPeerWaitingView() {
        clearPeerTable();
        if (app.peerRefs.peerStatusLabel != null) {
            app.peerRefs.peerStatusLabel.setText("連線中…（等待伺服器回傳好友列表）");
            app.peerRefs.peerStatusLabel.setForeground(NightTheme.MUTED);
        }
        app.refreshMainStatusBar();
    }

    void clearPeerTable() {
        if (app.peerRefs.peerTableModel == null) {
            return;
        }
        app.peerRefs.peerTableModel.setRowCount(0);
        if (app.peerRefs.peerTable != null) {
            app.peerRefs.peerTable.putClientProperty(PanelFactory.PEER_ONLINE_CLIENT_KEY, new LinkedHashMap<>());
        }
    }
}