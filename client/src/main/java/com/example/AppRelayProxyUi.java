package com.example;

import com.example.service.ConfigPersistenceService;
import com.example.service.RelayProxyService;
import com.example.ui.NightTheme;
import com.example.ui.PanelFactory;
import com.example.ui.RecentValuesHelper;

import javax.swing.*;
import java.awt.*;

/**
 * 設定頁中繼 Proxy 啟動、狀態與說明文字。
 */
final class AppRelayProxyUi {

    private final App app;

    AppRelayProxyUi(App app) {
        this.app = app;
    }

    void bindRelayProxyListeners() {
        if (app.serverRefs.relayEnabledCheckBox == null) {
            return;
        }
        app.serverRefs.relayEnabledCheckBox.addActionListener(e -> {
            boolean enabled = app.serverRefs.relayEnabledCheckBox.isSelected();
            if (enabled) {
                startRelayProxy();
            } else {
                stopRelayProxy();
            }
            app.saveCloudConfig();
        });
        if (app.serverRefs.relayPortSpinner != null) {
            app.serverRefs.relayPortSpinner.addChangeListener(e -> {
                if (app.relayProxyService.isRunning()) {
                    startRelayProxy();
                }
                app.saveCloudConfig();
            });
        }
    }

    void startRelayProxy() {
        String serverUrl = RecentValuesHelper.getValue(app.serverRefs.serverUrlCombo);
        if (serverUrl == null || serverUrl.isBlank()) {
            app.appendLog("[中繼] 請先設定 Server 雲端網址");
            app.serverRefs.relayEnabledCheckBox.setSelected(false);
            updateRelayStatusLabel(false);
            return;
        }
        int port = RelayProxyService.DEFAULT_PORT;
        if (app.serverRefs.relayPortSpinner != null) {
            Object val = app.serverRefs.relayPortSpinner.getValue();
            if (val instanceof Number) {
                port = ((Number) val).intValue();
            }
        }
        String token = "";
        if (app.serverRefs.heartbeatTokenField != null) {
            token = new String(app.serverRefs.heartbeatTokenField.getPassword());
        }
        boolean ok = app.relayProxyService.start(port, serverUrl, token, app::appendLog);
        updateRelayStatusLabel(ok);
        if (ok) {
            updateRelayHelpWithIp();
        } else {
            app.serverRefs.relayEnabledCheckBox.setSelected(false);
        }
    }

    void stopRelayProxy() {
        app.relayProxyService.stop();
        updateRelayStatusLabel(false);
    }

    void updateRelayStatusLabel(boolean running) {
        if (app.serverRefs.relayStatusLabel == null) {
            return;
        }
        if (running) {
            int port = app.relayProxyService.getPort();
            app.serverRefs.relayStatusLabel.setText("執行中 (埠 " + port + ")");
            app.serverRefs.relayStatusLabel.setForeground(new Color(34, 197, 94));
        } else {
            app.serverRefs.relayStatusLabel.setText("未啟動");
            app.serverRefs.relayStatusLabel.setForeground(NightTheme.MUTED);
        }
    }

    void updateRelayHelpWithIp() {
        if (app.serverRefs.relayHelpArea == null) {
            return;
        }
        int port = app.relayProxyService.getPort();
        String localIps = getLocalIpAddresses();
        String target = app.relayProxyService.getTargetServerUrl();
        app.serverRefs.relayHelpArea.setText(""
                + "中繼 Proxy 已啟動！\n"
                + "你的區網 IP：" + localIps + "\n"
                + "同事的「Server 雲端網址」請改成：http://<你的IP>:" + port + "\n"
                + "轉發目標：" + target);
        PanelFactory.fitRelayHelp(app.serverRefs.relayHelpArea);
        app.serverRefs.relayHelpArea.revalidate();
    }

    static String getLocalIpAddresses() {
        try {
            java.util.List<String> ips = new java.util.ArrayList<>();
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
            return ips.isEmpty() ? "未知" : String.join(", ", ips);
        } catch (Exception ex) {
            return "未知";
        }
    }

    void applyRelayConfig(ConfigPersistenceService.CloudConfig config) {
        if (app.serverRefs.relayPortSpinner != null) {
            app.serverRefs.relayPortSpinner.setValue(config.relayPort);
        }
        if (app.serverRefs.relayEnabledCheckBox != null) {
            app.serverRefs.relayEnabledCheckBox.setSelected(config.relayEnabled);
            if (config.relayEnabled) {
                SwingUtilities.invokeLater(this::startRelayProxy);
            }
        }
    }

    void captureRelayConfigInto(ConfigPersistenceService.CloudConfig config) {
        config.relayEnabled = app.serverRefs.relayEnabledCheckBox != null
                && app.serverRefs.relayEnabledCheckBox.isSelected();
        if (app.serverRefs.relayPortSpinner != null) {
            Object val = app.serverRefs.relayPortSpinner.getValue();
            if (val instanceof Number) {
                config.relayPort = ((Number) val).intValue();
            }
        }
    }
}