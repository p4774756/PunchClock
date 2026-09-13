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
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 桌面端「網路測試」分頁：給公司封閉網路／Proxy 環境（Windows）與私人 Mac 使用。
 */
public final class NetworkToolsPanel extends JPanel {

    public static final String TAB_LABEL = "網路測試";

    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color TITLE = new Color(30, 41, 59);
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color SURFACE = new Color(248, 250, 252);

    private static final String[] MODE_LABELS = {
            NetworkProbeService.modeLabel(NetworkProbeService.MODE_SYSTEM),
            NetworkProbeService.modeLabel(NetworkProbeService.MODE_DIRECT),
            NetworkProbeService.modeLabel(NetworkProbeService.MODE_CUSTOM)
    };
    private static final String[] MODE_VALUES = {
            NetworkProbeService.MODE_SYSTEM,
            NetworkProbeService.MODE_DIRECT,
            NetworkProbeService.MODE_CUSTOM
    };

    private final NetworkProbeService probeService = new NetworkProbeService();
    private final Supplier<String> cloudServerUrl;
    private final BooleanSupplier trustAllSsl;
    private final Runnable refreshHeartbeatClient;
    private final Runnable persistSettings;
    private final Consumer<String> logger;

    private final JTextArea environmentArea = new JTextArea();
    private final JTextArea resultArea = new JTextArea();
    private final JTextArea cheatSheetArea = new JTextArea();
    private final JComboBox<String> targetCombo;
    private final JTextField proxyHostField = new JTextField();
    private final JSpinner proxyPortSpinner =
            new JSpinner(new SpinnerNumberModel(NetworkProbeService.DEFAULT_PROXY_PORT, 1, 65535, 1));
    private final JComboBox<String> proxyModeCombo = new JComboBox<>(MODE_LABELS);
    private final JLabel statusLabel = new JLabel("就緒");
    private final java.util.List<JButton> actionButtons = new java.util.ArrayList<>();
    private boolean busy;

    public NetworkToolsPanel(Font mainFont, Font boldFont, Font fieldFont,
                             Supplier<String> cloudServerUrl,
                             BooleanSupplier trustAllSsl,
                             Runnable refreshHeartbeatClient,
                             Runnable persistSettings,
                             Consumer<String> logger) {
        this.cloudServerUrl = cloudServerUrl;
        this.trustAllSsl = trustAllSsl;
        this.refreshHeartbeatClient = refreshHeartbeatClient;
        this.persistSettings = persistSettings;
        this.logger = logger;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 4, 8, 4));

        targetCombo = RecentValuesHelper.createCombo(fieldFont, "https://www.google.com",
                "要測試的網址或主機；可填 https://host、host:443 或雲端 Server");

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(hintPanel(mainFont));
        body.add(Box.createVerticalStrut(8));
        body.add(environmentPanel(mainFont, boldFont));
        body.add(Box.createVerticalStrut(8));
        body.add(probePanel(mainFont, boldFont, fieldFont));
        body.add(Box.createVerticalStrut(8));
        body.add(cheatSheetPanel(boldFont));

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        refreshEnvironment();
        refreshCheatSheet();
    }

    public String getTestUrl() {
        return RecentValuesHelper.getValue(targetCombo);
    }

    public String getProxyHost() {
        return proxyHostField.getText() != null ? proxyHostField.getText().trim() : "";
    }

    public int getProxyPort() {
        Object value = proxyPortSpinner.getValue();
        if (value instanceof Number) {
            return NetworkProbeService.clampProxyPort(((Number) value).intValue());
        }
        return NetworkProbeService.DEFAULT_PROXY_PORT;
    }

    public String getProxyMode() {
        int index = proxyModeCombo.getSelectedIndex();
        if (index < 0 || index >= MODE_VALUES.length) {
            return NetworkProbeService.MODE_SYSTEM;
        }
        return MODE_VALUES[index];
    }

    public void applySettings(String testUrl, String proxyHost, int proxyPort, String proxyMode) {
        if (testUrl != null && !testUrl.isBlank()) {
            targetCombo.setSelectedItem(testUrl.trim());
        }
        proxyHostField.setText(proxyHost != null ? proxyHost : "");
        proxyPortSpinner.setValue(NetworkProbeService.clampProxyPort(proxyPort));
        String mode = NetworkProbeService.normalizeProxyMode(proxyMode);
        for (int i = 0; i < MODE_VALUES.length; i++) {
            if (MODE_VALUES[i].equals(mode)) {
                proxyModeCombo.setSelectedIndex(i);
                break;
            }
        }
        refreshCheatSheet();
    }

    private JPanel hintPanel(Font mainFont) {
        JTextArea hint = new JTextArea(
                "公司封閉網路通常要走 Proxy；Windows 公司機與自己的 Mac 設定位置不同。"
                        + " Java 心跳不會自動讀 HTTP_PROXY。先掃描環境，再用直連／自訂 Proxy 各測一次 HTTP。"
                        + " ICMP Ping 常被防火牆丟掉，失敗不代表出不了網。");
        hint.setEditable(false);
        hint.setOpaque(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setFont(mainFont);
        hint.setForeground(MUTED);
        hint.setBorder(null);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(hint, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        return panel;
    }

    private JPanel environmentPanel(Font mainFont, Font boldFont) {
        JPanel group = PanelFactory.createGroupPanel("本機環境（OS / Proxy）", boldFont);
        group.setLayout(new BorderLayout(0, 6));
        group.setAlignmentX(LEFT_ALIGNMENT);

        configureArea(environmentArea, mainFont, 9);
        JScrollPane scroll = wrapArea(environmentArea, 150);

        JButton refresh = actionButton(boldFont, "重新掃描環境",
                "讀取環境變數、JVM Proxy 屬性，以及 Windows netsh / Mac scutil");
        refresh.addActionListener(e -> {
            persistQuietly();
            refreshEnvironment();
            refreshCheatSheet();
            appendLog("[網路測試] 已重新掃描本機環境");
        });

        JPanel north = new JPanel(new BorderLayout());
        JLabel osHint = new JLabel("Windows：netsh winhttp　·　macOS：scutil --proxy");
        osHint.setFont(mainFont);
        osHint.setForeground(MUTED);
        north.add(osHint, BorderLayout.WEST);
        north.add(refresh, BorderLayout.EAST);

        group.add(north, BorderLayout.NORTH);
        group.add(scroll, BorderLayout.CENTER);
        return group;
    }

    private JPanel probePanel(Font mainFont, Font boldFont, Font fieldFont) {
        JPanel group = PanelFactory.createGroupPanel("連線測試", boldFont);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(LEFT_ALIGNMENT);

        JPanel targetRow = labeledRow(mainFont, "目標：", targetCombo);
        JPanel proxyRow = proxyRow(mainFont, fieldFont);

        JPanel quick = new JPanel(new WrapLayout(WrapLayout.LEFT, 6, 4));
        quick.setAlignmentX(LEFT_ALIGNMENT);
        quick.setOpaque(false);
        quick.add(quickButton(mainFont, "Google", "https://www.google.com"));
        quick.add(quickButton(mainFont, "NeverSSL", "http://neverssl.com"));
        quick.add(quickButton(mainFont, "Cloudflare 1.1.1.1", "https://1.1.1.1"));
        JButton cloudBtn = new JButton("雲端 Server /ping");
        cloudBtn.setFont(mainFont);
        cloudBtn.setToolTipText("使用「雲端設定」裡的 Server 網址加上 /ping");
        cloudBtn.addActionListener(e -> {
            String base = cloudServerUrl != null ? cloudServerUrl.get() : "";
            if (base == null || base.isBlank()) {
                setStatus("請先到「雲端設定」填 Server 網址");
                return;
            }
            targetCombo.setSelectedItem(NetworkProbeService.joinUrl(base.trim(), "/ping"));
            persistQuietly();
            refreshCheatSheet();
        });
        quick.add(cloudBtn);

        JPanel actions = new JPanel(new WrapLayout(WrapLayout.LEFT, 6, 4));
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setOpaque(false);
        actions.add(probeButton(boldFont, "DNS", "名稱解析", this::runDns));
        actions.add(probeButton(boldFont, "TCP", "連線目標埠（網址預設 443）", this::runTcp));
        actions.add(probeButton(boldFont, "HTTP GET", "用目前 Proxy 模式發 GET", this::runHttp));
        actions.add(probeButton(boldFont, "Ping", "ICMP；公司網路常封鎖", this::runPing));
        actions.add(probeButton(boldFont, "一鍵診斷", "環境 + DNS + TCP + HTTP + Ping + 雲端 /ping", this::runDiagnose));
        actions.add(probeButton(boldFont, "套用到本程式", "寫入 JVM Proxy 屬性（心跳連線）", this::applyJvmProxy));

        JButton copy = actionButton(mainFont, "複製結果", "複製下方測試輸出");
        copy.addActionListener(e -> copyText(resultArea.getText()));
        JButton copyCheat = actionButton(mainFont, "複製指令", "複製目前 OS 的指令備忘");
        copyCheat.addActionListener(e -> copyText(cheatSheetArea.getText()));
        actions.add(copy);
        actions.add(copyCheat);

        statusLabel.setFont(mainFont);
        statusLabel.setForeground(MUTED);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        configureArea(resultArea, fieldFont, 14);
        JScrollPane resultScroll = wrapArea(resultArea, 220);

        targetCombo.addActionListener(e -> {
            persistQuietly();
            refreshCheatSheet();
        });

        group.add(targetRow);
        group.add(Box.createVerticalStrut(4));
        group.add(proxyRow);
        group.add(quick);
        group.add(actions);
        group.add(statusLabel);
        group.add(Box.createVerticalStrut(4));
        group.add(resultScroll);
        return group;
    }

    private JPanel proxyRow(Font mainFont, Font fieldFont) {
        proxyModeCombo.setFont(mainFont);
        proxyModeCombo.setToolTipText("系統：跟隨 JVM／OS；直連：略過 Proxy；自訂：填右邊主機與埠");
        proxyHostField.setFont(fieldFont);
        proxyHostField.setColumns(16);
        proxyHostField.setToolTipText("公司 HTTP Proxy 主機，例如 proxy.company.com 或 10.0.0.1");
        proxyPortSpinner.setFont(fieldFont);
        proxyPortSpinner.setToolTipText("常見 8080、3128、8888");
        JSpinner.NumberEditor portEditor = new JSpinner.NumberEditor(proxyPortSpinner, "#");
        portEditor.getTextField().setFont(fieldFont);
        proxyPortSpinner.setEditor(portEditor);
        Dimension portSize = new Dimension(80, 26);
        proxyPortSpinner.setPreferredSize(portSize);
        proxyPortSpinner.setMaximumSize(portSize);

        JLabel hostLabel = new JLabel("Proxy 主機：");
        hostLabel.setFont(mainFont);
        JLabel portLabel = new JLabel("埠：");
        portLabel.setFont(mainFont);

        JPanel row = new JPanel(new WrapLayout(WrapLayout.LEFT, 8, 4));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(proxyModeCombo);
        row.add(hostLabel);
        row.add(proxyHostField);
        row.add(portLabel);
        row.add(proxyPortSpinner);

        proxyModeCombo.addActionListener(e -> {
            persistQuietly();
            refreshCheatSheet();
        });
        proxyHostField.addActionListener(e -> {
            persistQuietly();
            refreshCheatSheet();
        });
        proxyPortSpinner.addChangeListener(e -> {
            persistQuietly();
            refreshCheatSheet();
        });
        return row;
    }

    private JPanel cheatSheetPanel(Font boldFont) {
        JPanel group = PanelFactory.createGroupPanel("Windows / Mac 指令備忘", boldFont);
        group.setLayout(new BorderLayout(0, 4));
        group.setAlignmentX(LEFT_ALIGNMENT);
        configureArea(cheatSheetArea, UiFonts.latinPlain(12), 14);
        group.add(wrapArea(cheatSheetArea, 200), BorderLayout.CENTER);
        return group;
    }

    private JPanel labeledRow(Font labelFont, String label, java.awt.Component field) {
        JPanel row = new JPanel(new BorderLayout(8, 4));
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel jLabel = new JLabel(label);
        jLabel.setFont(labelFont);
        row.add(jLabel, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return row;
    }

    private JButton quickButton(Font font, String label, String url) {
        JButton button = new JButton(label);
        button.setFont(font);
        button.setMargin(new Insets(2, 8, 2, 8));
        button.addActionListener(e -> {
            targetCombo.setSelectedItem(url);
            persistQuietly();
            refreshCheatSheet();
        });
        return button;
    }

    private JButton probeButton(Font font, String label, String tooltip, Runnable action) {
        JButton button = actionButton(font, label, tooltip);
        button.addActionListener(e -> runOffEdt(label, action));
        actionButtons.add(button);
        return button;
    }

    private JButton actionButton(Font font, String label, String tooltip) {
        JButton button = new JButton(label);
        button.setFont(font);
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(2, 10, 2, 10));
        return button;
    }

    private void runDns() {
        showResult("DNS", probeService.dnsLookup(getTestUrl()).format());
    }

    private void runTcp() {
        showResult("TCP", probeService.tcpConnect(getTestUrl(), 443).format());
    }

    private void runHttp() {
        showResult("HTTP GET", probeService.httpGet(getTestUrl(), getProxyMode(), getProxyHost(), getProxyPort()).format());
    }

    private void runPing() {
        showResult("Ping", probeService.icmpPing(getTestUrl()).format());
    }

    private void runDiagnose() {
        String cloud = cloudServerUrl != null ? cloudServerUrl.get() : "";
        String report = probeService.diagnose(getTestUrl(), getProxyMode(), getProxyHost(), getProxyPort(), cloud);
        if (trustAllSsl != null && trustAllSsl.getAsBoolean()) {
            report = report + "\n\n目前已啟用「信任所有 SSL（除錯）」。";
        }
        final String output = report;
        SwingUtilities.invokeLater(() -> {
            resultArea.setText(output);
            resultArea.setCaretPosition(0);
            setStatus("一鍵診斷完成");
        });
        appendLog("[網路測試] 一鍵診斷完成");
    }

    private void applyJvmProxy() {
        NetworkProbeService.JvmProxyApplyResult result =
                probeService.applyJvmProxy(getProxyMode(), getProxyHost(), getProxyPort());
        if (refreshHeartbeatClient != null && result.applied) {
            refreshHeartbeatClient.run();
        }
        showResult("套用 JVM Proxy", result.summary);
        appendLog("[網路測試] " + result.summary);
    }

    private void runOffEdt(String label, Runnable action) {
        if (busy) {
            return;
        }
        persistQuietly();
        setBusy(true);
        setStatus("執行中：" + label);
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Exception ex) {
                showResult(label, "發生錯誤：" + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> setBusy(false));
            }
        }, "network-tools-" + label);
        thread.setDaemon(true);
        thread.start();
    }

    private void showResult(String title, String text) {
        SwingUtilities.invokeLater(() -> {
            resultArea.setText(text);
            resultArea.setCaretPosition(0);
            setStatus(title + " 完成");
        });
        appendLog("[網路測試] " + title + " 完成");
    }

    private void refreshEnvironment() {
        NetworkProbeService.EnvironmentSnapshot snapshot = probeService.snapshotEnvironment();
        environmentArea.setText(snapshot.format());
        environmentArea.setCaretPosition(0);
        setStatus("環境已更新（" + snapshot.os.name() + "）");
    }

    private void refreshCheatSheet() {
        cheatSheetArea.setText(probeService.buildCheatSheet(getTestUrl(), getProxyHost(), getProxyPort()));
        cheatSheetArea.setCaretPosition(0);
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
        area.setBackground(SURFACE);
        area.setForeground(TITLE);
        area.setRows(rows);
        area.setMargin(new Insets(8, 10, 8, 10));
        area.setCaretPosition(0);
    }

    private static JScrollPane wrapArea(JTextArea area, int height) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(640, height));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return scroll;
    }
}
