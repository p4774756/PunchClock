package com.example.ui;

import com.example.service.NetworkProbeService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 桌面端「Ping/Pong」分頁：對 Server {@code GET /ping} 做簡單連線測試。
 */
public final class NetworkToolsPanel extends JPanel {

    public static final String TAB_LABEL = "Ping/Pong";

    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color TITLE = new Color(30, 41, 59);
    private static final Color BORDER = new Color(203, 213, 225, 160);
    private static final Color SURFACE = new Color(248, 250, 252, 140);

    private final NetworkProbeService probeService = new NetworkProbeService();
    private final Supplier<String> cloudServerUrl;
    private final BooleanSupplier trustAllSsl;
    private final Runnable persistSettings;
    private final Consumer<String> logger;

    private final JComboBox<String> targetCombo;
    private final JTextArea helpArea = new JTextArea();
    private final JTextArea resultArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("就緒");
    private final List<JButton> actionButtons = new ArrayList<>();
    private JSplitPane splitPane;

    /** 舊設定仍寫入 config，此分頁不再顯示 Proxy UI。 */
    private String proxyHost = "";
    private int proxyPort = NetworkProbeService.DEFAULT_PROXY_PORT;
    private String proxyMode = NetworkProbeService.MODE_SYSTEM;
    private String proxyUser = "";
    private String proxyPassword = "";
    private boolean busy;

    public NetworkToolsPanel(Font mainFont, Font boldFont, Font fieldFont,
                             Supplier<String> cloudServerUrl,
                             BooleanSupplier trustAllSsl,
                             Runnable refreshHeartbeatClient,
                             Runnable persistSettings,
                             Consumer<String> logger) {
        this.cloudServerUrl = cloudServerUrl;
        this.trustAllSsl = trustAllSsl;
        this.persistSettings = persistSettings;
        this.logger = logger;

        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(8, 4, 8, 4));

        targetCombo = RecentValuesHelper.createCombo(fieldFont, "http://localhost:3000/ping",
                "Server /ping 網址；可用「帶入雲端 Server」一鍵帶入");

        JPanel help = helpPanel(boldFont);
        JPanel test = testPanel(mainFont, boldFont);
        help.setMinimumSize(new Dimension(200, 80));
        test.setMinimumSize(new Dimension(200, 160));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, help, test);
        this.splitPane = split;
        split.setOpaque(false);
        split.setResizeWeight(0.35);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setDividerSize(8);
        split.setBorder(null);
        split.setDividerLocation(160);
        add(split, BorderLayout.CENTER);

        refreshHelp();
    }

    private JPanel helpPanel(Font boldFont) {
        JPanel group = PanelFactory.createGroupPanel("說明（Server /ping）", boldFont);
        group.setLayout(new BorderLayout(0, 4));
        configureArea(helpArea, UiFonts.latinPlain(12), 8);
        group.add(wrapArea(helpArea), BorderLayout.CENTER);
        return group;
    }

    private JPanel testPanel(Font mainFont, Font boldFont) {
        JPanel group = PanelFactory.createGroupPanel("連線測試", boldFont);
        group.setLayout(new BorderLayout(0, 4));

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

        JPanel targetRow = new JPanel(new BorderLayout(8, 4));
        targetRow.setOpaque(false);
        targetRow.setAlignmentX(LEFT_ALIGNMENT);
        JLabel targetLabel = new JLabel("目標：");
        targetLabel.setFont(mainFont);
        targetRow.add(targetLabel, BorderLayout.WEST);
        targetRow.add(targetCombo, BorderLayout.CENTER);
        targetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JPanel actions = new JPanel(new WrapLayout(WrapLayout.LEFT, 6, 4));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);

        actions.add(actionButton(mainFont, "帶入雲端 Server",
                "使用「雲端設定」裡的 Server 網址加上 /ping", e -> fillCloudPingUrl()));
        actions.add(actionButton(boldFont, "測試 Ping/Pong",
                "對目標發 HTTP GET，預期回應 message=pong", e -> runPingPong()));
        actions.add(actionButton(mainFont, "複製 curl",
                "複製目前目標的 curl 指令", e -> copyText(buildCurlSnippet())));
        actions.add(actionButton(mainFont, "複製結果",
                "複製下方測試輸出", e -> copyText(resultArea.getText())));

        statusLabel.setFont(mainFont);
        statusLabel.setForeground(MUTED);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        north.add(targetRow);
        north.add(Box.createVerticalStrut(4));
        north.add(actions);
        north.add(statusLabel);

        configureArea(resultArea, UiFonts.latinPlain(12), 12);
        group.add(north, BorderLayout.NORTH);
        group.add(wrapArea(resultArea), BorderLayout.CENTER);

        targetCombo.addActionListener(e -> {
            persistQuietly();
            refreshHelp();
        });
        return group;
    }

    private JButton actionButton(Font font, String label, String tooltip,
                                 java.awt.event.ActionListener listener) {
        JButton button = new JButton(label);
        button.setFont(font);
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(2, 10, 2, 10));
        button.addActionListener(listener);
        actionButtons.add(button);
        return button;
    }

    public String getTestUrl() {
        return RecentValuesHelper.getValue(targetCombo);
    }

    public String getProxyHost() {
        return proxyHost != null ? proxyHost : "";
    }

    public int getProxyPort() {
        return NetworkProbeService.clampProxyPort(proxyPort);
    }

    public String getProxyMode() {
        return NetworkProbeService.normalizeProxyMode(proxyMode);
    }

    public String getProxyUser() {
        return proxyUser != null ? proxyUser : "";
    }

    public String getProxyPassword() {
        return proxyPassword != null ? proxyPassword : "";
    }

    public int getSplitDividerLocation() {
        if (splitPane == null) {
            return -1;
        }
        int divider = splitPane.getDividerLocation();
        return divider > 0 ? divider : -1;
    }

    public void applySplitDividerLocation(int dividerLocation) {
        if (splitPane == null) {
            return;
        }
        final int saved = dividerLocation;
        SwingUtilities.invokeLater(() -> {
            if (splitPane == null) {
                return;
            }
            if (saved > 0) {
                int max = Math.max(1, splitPane.getHeight() - splitPane.getDividerSize());
                splitPane.setDividerLocation(Math.min(saved, max));
            } else {
                splitPane.setDividerLocation(160);
            }
        });
    }

    public void applySettings(String testUrl, String proxyHost, int proxyPort, String proxyMode) {
        applySettings(testUrl, proxyHost, proxyPort, proxyMode, "", "");
    }

    public void applySettings(String testUrl, String proxyHost, int proxyPort, String proxyMode,
                              String proxyUser, String proxyPassword) {
        if (testUrl != null && !testUrl.isBlank()) {
            targetCombo.setSelectedItem(testUrl.trim());
        } else {
            fillCloudPingUrlQuiet();
        }
        this.proxyHost = proxyHost != null ? proxyHost : "";
        this.proxyPort = NetworkProbeService.clampProxyPort(proxyPort);
        this.proxyMode = NetworkProbeService.normalizeProxyMode(proxyMode);
        this.proxyUser = proxyUser != null ? proxyUser : "";
        this.proxyPassword = proxyPassword != null ? proxyPassword : "";
        refreshHelp();
    }

    private void fillCloudPingUrl() {
        String base = cloudServerUrl != null ? cloudServerUrl.get() : "";
        if (base == null || base.isBlank()) {
            setStatus("請先到「雲端設定」填 Server 網址");
            return;
        }
        targetCombo.setSelectedItem(NetworkProbeService.joinUrl(base.trim(), "/ping"));
        persistQuietly();
        refreshHelp();
        setStatus("已帶入雲端 /ping");
    }

    private void fillCloudPingUrlQuiet() {
        String base = cloudServerUrl != null ? cloudServerUrl.get() : "";
        if (base != null && !base.isBlank()) {
            targetCombo.setSelectedItem(NetworkProbeService.joinUrl(base.trim(), "/ping"));
        }
    }

    private void runPingPong() {
        if (busy) {
            return;
        }
        persistQuietly();
        setBusy(true);
        setStatus("執行中：Ping/Pong");
        final String url = getTestUrl();
        new Thread(() -> {
            try {
                NetworkProbeService.ProbeResult result = probeService.httpGet(
                        url, getProxyMode(), getProxyHost(), getProxyPort(),
                        getProxyUser(), getProxyPassword());
                String output = result.format();
                if (trustAllSsl != null && trustAllSsl.getAsBoolean()) {
                    output = output + "\n\n目前已啟用「信任所有 SSL（除錯）」。";
                }
                if (result.ok && output.toLowerCase().contains("pong")) {
                    output = output + "\n\n✓ Server 有回應 pong（/ping 正常）";
                }
                final String text = output;
                final boolean ok = result.ok;
                SwingUtilities.invokeLater(() -> {
                    resultArea.setText(text);
                    resultArea.setCaretPosition(0);
                    setStatus(ok ? "Ping/Pong 成功" : "Ping/Pong 失敗");
                    appendLog("[Ping/Pong] " + (ok ? "成功" : "失敗") + " → " + url);
                });
            } finally {
                SwingUtilities.invokeLater(() -> setBusy(false));
            }
        }, "ping-pong-probe").start();
    }

    private void refreshHelp() {
        String cloud = cloudServerUrl != null ? cloudServerUrl.get() : "";
        String example = (cloud != null && !cloud.isBlank())
                ? NetworkProbeService.joinUrl(cloud.trim(), "/ping")
                : "http://localhost:3000/ping";
        String current = getTestUrl();
        if (current == null || current.isBlank()) {
            current = example;
        }
        helpArea.setText(""
                + "Server 提供 GET /ping（不需 Token），用來確認桌面端能不能連上雲端。\n"
                + "\n"
                + "預期回應類似：\n"
                + "  {\"message\":\"pong\",\"timestamp\":\"...\"}\n"
                + "\n"
                + "curl 範例：\n"
                + "  curl \"" + current + "\"\n"
                + "\n"
                + "本機預設：\n"
                + "  curl \"http://localhost:3000/ping\"\n");
        helpArea.setCaretPosition(0);
    }

    private String buildCurlSnippet() {
        String url = getTestUrl();
        if (url == null || url.isBlank()) {
            url = "http://localhost:3000/ping";
        }
        return "curl \"" + url + "\"";
    }

    private void persistQuietly() {
        if (persistSettings != null) {
            persistSettings.run();
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        for (JButton button : actionButtons) {
            button.setEnabled(!value);
        }
        if (!value && statusLabel.getText() != null && statusLabel.getText().startsWith("執行中")) {
            setStatus("就緒");
        }
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
        statusLabel.setForeground(MUTED);
    }

    private void copyText(String text) {
        if (text == null || text.isBlank()) {
            setStatus("沒有可複製的內容");
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        setStatus("已複製到剪貼簿");
    }

    private void appendLog(String line) {
        if (logger != null) {
            logger.accept(line);
        }
    }

    private static void configureArea(JTextArea area, Font font, int rows) {
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(font);
        area.setOpaque(true);
        area.setBackground(SURFACE);
        area.setForeground(TITLE);
        area.setRows(rows);
        area.setMargin(new Insets(8, 10, 8, 10));
        area.setCaretPosition(0);
    }

    private static JScrollPane wrapArea(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        return scroll;
    }
}
