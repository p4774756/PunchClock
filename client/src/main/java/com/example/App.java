package com.example;

import com.example.model.TaskStatus;
import com.example.service.SpeechService;
import com.example.service.AutomationService;
import com.example.service.ConfigPersistenceService;
import com.example.service.HeartbeatService;
import com.example.service.HeartbeatService.PeerInfo;
import com.example.service.SchedulerService;
import com.example.service.TaskPersistenceService;
import com.example.ui.UiFonts;
import com.example.ui.PanelFactory;
import com.example.ui.PanelFactory.*;
import com.example.ui.RecentValuesHelper;
import com.example.ui.SlotController;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 圖形介面主視窗 - 上班 / 下班雙槽位打卡
 */
public class App extends JFrame {

    private static final String SELF_PEER_SUFFIX = "（本機）";

    private final ServerConfigRefs serverRefs = new ServerConfigRefs();
    private final PeerInteractionRefs peerRefs = new PeerInteractionRefs();
    private final SlotPanelRefs slotRefs = new SlotPanelRefs();
    private final LogPanelRefs logRefs = new LogPanelRefs();

    private final SchedulerService schedulerService;
    private final AutomationService automationService;
    private final HeartbeatService heartbeatService;
    private final TaskPersistenceService persistenceService;
    private final ConfigPersistenceService configPersistenceService;
    private SlotController slotController;
    private boolean suppressConfigSave = false;
    private Timer countdownTimer;
    private JSplitPane mainSplit;
    private JTabbedPane mainTabs;
    private boolean serverHistoryMenuBound;

    public App() {
        this.schedulerService = new SchedulerService();
        this.automationService = new AutomationService();
        this.heartbeatService = new HeartbeatService();
        this.persistenceService = new TaskPersistenceService();
        this.configPersistenceService = new ConfigPersistenceService();

        initHeartbeatService();
        initUI();
        slotController = new SlotController(
                this, slotRefs,
                schedulerService, automationService, heartbeatService,
                persistenceService, configPersistenceService,
                this::appendLog, this::onSlotStateChanged);
        slotController.bindUi();

        loadPersistedConfig();
        startHeartbeatService();
        slotController.initializeFromPersistence();

        countdownTimer = new Timer(1000, e -> slotController.refreshCountdowns());
        countdownTimer.start();
        appendLog("[資訊] 桌面端版本 v" + AppVersion.VERSION);
    }

    private void initHeartbeatService() {
        heartbeatService.setTasksProvider(schedulerService::getAllTasks);
        heartbeatService.setPeersListener(peers -> SwingUtilities.invokeLater(() -> updatePeerTable(peers)));
        heartbeatService.setCommandListener(command -> {
            if ("CANCEL_SCHEDULE".equalsIgnoreCase(command)) {
                SwingUtilities.invokeLater(slotController::handleRemoteCancelAll);
            } else if (command.startsWith("CANCEL_TASK:")) {
                String taskId = command.substring("CANCEL_TASK:".length()).trim();
                SwingUtilities.invokeLater(() -> slotController.handleRemoteCancelTask(taskId));
            } else if (command.startsWith("MSG|")) {
                String[] parts = command.split("\\|", 3);
                if (parts.length >= 3) {
                    String fromId = parts[1];
                    String text = parts[2];
                    SwingUtilities.invokeLater(() -> showPeerMessage(fromId, text));
                }
            } else if (command.startsWith("POKE|")) {
                String fromId = command.length() > 5 ? command.substring(5) : "同事";
                SwingUtilities.invokeLater(() -> showPeerPoke(fromId));
            }
        });
    }

    private void initUI() {
        setTitle("上班打卡工具  v" + AppVersion.VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        Font mainFont = UiFonts.chinesePlain(13);
        Font boldFont = UiFonts.chineseBold(13);
        Font fieldFont = UiFonts.latinPlain(13);

        add(createDailyProverbBanner(mainFont, boldFont), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        this.mainTabs = tabs;
        tabs.setFont(UiFonts.latinBold(13));
        tabs.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        tabs.setUI(new BasicTabbedPaneUI());
        tabs.setBorder(new EmptyBorder(8, 12, 0, 12));

        JPanel slotPanel = PanelFactory.createSlotPanel(slotRefs, mainFont, boldFont, fieldFont);
        JPanel tasksTab = new JPanel(new BorderLayout());
        tasksTab.setBorder(new EmptyBorder(8, 4, 8, 4));
        tasksTab.add(slotPanel, BorderLayout.NORTH);

        JPanel serverBody = PanelFactory.createServerConfigBody(serverRefs, mainFont, boldFont, fieldFont);
        JPanel serverGroup = PanelFactory.createGroupPanel("雲端服務與裝置設定", boldFont);
        serverGroup.setLayout(new BorderLayout());
        serverGroup.add(serverBody, BorderLayout.NORTH);

        JPanel cloudTab = new JPanel(new BorderLayout());
        cloudTab.setBorder(new EmptyBorder(8, 4, 8, 4));
        cloudTab.add(serverGroup, BorderLayout.NORTH);

        JPanel peerBody = PanelFactory.createPeerInteractionPanel(peerRefs, mainFont, boldFont);
        JPanel peerGroup = PanelFactory.createGroupPanel(PanelFactory.PEER_TAB_LABEL, boldFont);
        peerGroup.setLayout(new BorderLayout());
        peerGroup.add(peerBody, BorderLayout.NORTH);
        bindPeerInteractionListeners();
        if (peerRefs.openCloudSettingsButton != null) {
            peerRefs.openCloudSettingsButton.addActionListener(e -> {
                if (mainTabs != null) {
                    mainTabs.setSelectedIndex(1);
                }
            });
        }

        JPanel peerTab = new JPanel(new BorderLayout());
        peerTab.setBorder(new EmptyBorder(8, 4, 8, 4));
        peerTab.add(peerGroup, BorderLayout.NORTH);

        tabs.addTab("打卡任務", tasksTab);
        tabs.addTab("雲端設定", cloudTab);
        tabs.addTab(PanelFactory.PEER_TAB_LABEL, peerTab);
        tabs.addTab("Ping/Pong", PanelFactory.createHelpPanel(mainFont, boldFont, fieldFont));
        tabs.setToolTipTextAt(0, "設定打卡網址、時間，立即測試");
        tabs.setToolTipTextAt(1, "雲端心跳、Client ID、Token");
        tabs.setToolTipTextAt(2, "查看在線裝置、傳訊息、戳一下");
        tabs.setToolTipTextAt(3, "用 curl 測試 Server 的 /ping API 是否回 pong");
        tabs.setSelectedIndex(0);

        JPanel logPanel = PanelFactory.createLogPanel(logRefs, mainFont, boldFont);
        logPanel.setBorder(new CompoundBorder(new EmptyBorder(0, 12, 10, 12), logPanel.getBorder()));
        logPanel.setMinimumSize(new Dimension(400, 90));
        tabs.setMinimumSize(new Dimension(400, 220));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, logPanel);
        this.mainSplit = split;
        split.setResizeWeight(0.55);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setDividerSize(8);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        setMinimumSize(new Dimension(720, 560));
        setSize(720, 780);
        setLocationRelativeTo(null);

        bindWindowLayoutPersistence(split);
        bindCloudEventListeners();
    }

    private void bindWindowLayoutPersistence(JSplitPane split) {
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                rememberWindowLayout();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                rememberWindowLayout();
            }
        });
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> rememberWindowLayout());
    }

    private void rememberWindowLayout() {
        if (suppressConfigSave || slotController == null || !isDisplayable()) return;
        captureWindowLayoutInto(slotController.getConfig());
    }

    private void captureWindowLayoutInto(ConfigPersistenceService.CloudConfig config) {
        if (config == null) return;
        config.windowWidth = getWidth();
        config.windowHeight = getHeight();
        Point loc = getLocation();
        config.windowX = loc.x;
        config.windowY = loc.y;
        if (mainSplit != null) {
            int divider = mainSplit.getDividerLocation();
            if (divider > 0) {
                config.splitDividerLocation = divider;
            }
        }
    }

    private void applyWindowLayout(ConfigPersistenceService.CloudConfig config) {
        Dimension min = getMinimumSize();
        int width = config.windowWidth > 0 ? Math.max(config.windowWidth, min.width) : 720;
        int height = config.windowHeight > 0 ? Math.max(config.windowHeight, min.height) : 780;
        setSize(width, height);

        if (config.windowX >= 0 && config.windowY >= 0 && isLocationOnScreen(config.windowX, config.windowY, width, height)) {
            setLocation(config.windowX, config.windowY);
        } else {
            setLocationRelativeTo(null);
        }

        final int savedDivider = config.splitDividerLocation;
        SwingUtilities.invokeLater(() -> {
            if (mainSplit == null) return;
            if (savedDivider > 0) {
                int max = Math.max(1, mainSplit.getHeight() - mainSplit.getDividerSize());
                mainSplit.setDividerLocation(Math.min(savedDivider, max));
            } else {
                mainSplit.setDividerLocation(0.55);
            }
        });
    }

    private static boolean isLocationOnScreen(int x, int y, int width, int height) {
        Rectangle windowBounds = new Rectangle(x, y, Math.max(width, 1), Math.max(height, 1));
        for (java.awt.GraphicsDevice device : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle screen = device.getDefaultConfiguration().getBounds();
            if (screen.intersects(windowBounds)) {
                return true;
            }
        }
        return false;
    }

    private JPanel createDailyProverbBanner(Font mainFont, Font boldFont) {
        DailyProverb.Entry proverb = DailyProverb.forToday();

        JPanel banner = new JPanel(new BorderLayout(0, 2));
        banner.setBorder(new CompoundBorder(
                new EmptyBorder(10, 12, 0, 12),
                new CompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(12, 107, 107)),
                        new EmptyBorder(8, 12, 8, 12)
                )
        ));
        banner.setBackground(new Color(255, 252, 246));
        banner.setOpaque(true);

        JLabel kicker = new JLabel("今日六人行 · " + proverb.date);
        kicker.setFont(new Font(mainFont.getName(), Font.BOLD, 11));
        kicker.setForeground(new Color(74, 85, 104));

        JLabel en = new JLabel(proverb.en);
        en.setFont(new Font(boldFont.getName(), Font.BOLD, 14));
        en.setForeground(new Color(26, 35, 50));

        JLabel ctx = new JLabel(proverb.context);
        ctx.setFont(new Font(mainFont.getName(), Font.BOLD, 11));
        ctx.setForeground(new Color(9, 101, 151));

        JLabel zh = new JLabel(proverb.zh);
        zh.setFont(mainFont);
        zh.setForeground(new Color(74, 85, 104));

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);
        textCol.add(kicker);
        textCol.add(Box.createVerticalStrut(3));
        textCol.add(en);
        textCol.add(Box.createVerticalStrut(2));
        textCol.add(ctx);
        textCol.add(Box.createVerticalStrut(2));
        textCol.add(zh);

        JButton speakButton = new JButton("發音");
        speakButton.setFont(mainFont);
        speakButton.setToolTipText("朗讀今日英文台詞");
        speakButton.addActionListener(e -> SpeechService.speakEnglish(proverb.en, this::appendLog));

        banner.add(textCol, BorderLayout.CENTER);
        banner.add(speakButton, BorderLayout.EAST);
        return banner;
    }

    private void bindPeerInteractionListeners() {
        if (peerRefs.sendMessageButton != null) {
            peerRefs.sendMessageButton.addActionListener(e -> sendMessageToSelectedPeer());
        }
        if (peerRefs.pokeButton != null) {
            peerRefs.pokeButton.addActionListener(e -> pokeSelectedPeer());
        }
        if (peerRefs.messageField != null) {
            peerRefs.messageField.addActionListener(e -> sendMessageToSelectedPeer());
        }
    }

    private String getSelectedPeerClientId() {
        if (peerRefs.peerTable == null) return null;
        int row = peerRefs.peerTable.getSelectedRow();
        if (row < 0) return null;
        Object value = peerRefs.peerTableModel.getValueAt(row, 0);
        return parsePeerRowId(value != null ? value.toString() : null);
    }

    private void sendMessageToSelectedPeer() {
        String toClientId = getSelectedPeerClientId();
        if (toClientId == null) {
            appendLog("[警告] [戳] 請先在列表中選擇一位同事");
            return;
        }
        if (isSelfClientId(toClientId)) {
            appendLog("[警告] [戳] 無法傳送訊息給本機");
            return;
        }
        String text = peerRefs.messageField != null ? peerRefs.messageField.getText() : "";
        heartbeatService.sendPeerMessage(toClientId, text, this::appendLog, ok -> {
            if (ok && peerRefs.messageField != null) {
                SwingUtilities.invokeLater(() -> peerRefs.messageField.setText(""));
            }
        });
    }

    private void pokeSelectedPeer() {
        String toClientId = getSelectedPeerClientId();
        if (toClientId == null) {
            appendLog("[警告] [戳] 請先在列表中選擇一位同事");
            return;
        }
        if (isSelfClientId(toClientId)) {
            appendLog("[警告] [戳] 無法戳本機");
            return;
        }
        heartbeatService.sendPeerPoke(toClientId, this::appendLog, null);
    }

    private void updatePeerTable(java.util.List<PeerInfo> peers) {
        if (peerRefs.peerTableModel == null || !isCloudEnabled()) {
            return;
        }

        String selectedId = getSelectedPeerClientId();
        String myClientId = heartbeatService.getClientId();
        peerRefs.peerTableModel.setRowCount(0);
        int onlineCount = 0;
        int deviceCount = 0;

        peerRefs.peerTableModel.addRow(new Object[]{
                formatPeerRowId(myClientId, true),
                "在線",
                countLocalScheduledTasks(),
                AppVersion.VERSION
        });
        onlineCount++;
        deviceCount++;

        for (PeerInfo peer : peers) {
            if (myClientId.equals(peer.clientId)) {
                continue;
            }
            boolean online = "ONLINE".equalsIgnoreCase(peer.status);
            if (online) onlineCount++;
            deviceCount++;
            String statusLabel = online ? "在線" : "離線";
            peerRefs.peerTableModel.addRow(new Object[]{
                    peer.clientId,
                    statusLabel,
                    peer.scheduledCount,
                    peer.appVersion.isBlank() ? "—" : peer.appVersion
            });
        }

        if (peerRefs.peerStatusLabel != null) {
            peerRefs.peerStatusLabel.setText("共 " + deviceCount + " 台裝置 · " + onlineCount + " 位在線");
            peerRefs.peerStatusLabel.setForeground(new Color(100, 116, 139));
        }
        setPeerInteractionEnabled(true);
        if (peerRefs.openCloudSettingsButton != null) {
            peerRefs.openCloudSettingsButton.setVisible(false);
        }
        if (peerRefs.peerHintLabel != null) {
            peerRefs.peerHintLabel.setText(
                    "顯示同一伺服器上的裝置（含本機標示，每 15 秒隨心跳更新）");
        }

        if (selectedId != null && peerRefs.peerTable != null) {
            for (int i = 0; i < peerRefs.peerTableModel.getRowCount(); i++) {
                String rowId = parsePeerRowId(String.valueOf(peerRefs.peerTableModel.getValueAt(i, 0)));
                if (selectedId.equals(rowId)) {
                    peerRefs.peerTable.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    private boolean isCloudEnabled() {
        return serverRefs.enableServerCheckBox != null && serverRefs.enableServerCheckBox.isSelected();
    }

    private void refreshPeerInteractionState() {
        if (!isCloudEnabled()) {
            showOfflinePeerView();
            return;
        }
        setPeerInteractionEnabled(true);
        if (peerRefs.openCloudSettingsButton != null) {
            peerRefs.openCloudSettingsButton.setVisible(false);
        }
        if (peerRefs.peerHintLabel != null) {
            peerRefs.peerHintLabel.setText(
                    "顯示同一伺服器上的裝置（含本機標示，每 15 秒隨心跳更新）");
        }
        showPeerWaitingView();
    }

    private void showOfflinePeerView() {
        populateSelfOnlyPeerTable("本機");
        if (peerRefs.peerHintLabel != null) {
            peerRefs.peerHintLabel.setText(
                    "雲端未啟用時僅顯示本機；至「雲端設定」勾選「啟用雲端單向狀態回報」後，可查看同事並互動");
        }
        if (peerRefs.peerStatusLabel != null) {
            peerRefs.peerStatusLabel.setText("雲端未啟用 · 本機獨立運作");
            peerRefs.peerStatusLabel.setForeground(new Color(100, 116, 139));
        }
        setPeerInteractionEnabled(false);
        if (peerRefs.openCloudSettingsButton != null) {
            peerRefs.openCloudSettingsButton.setVisible(true);
        }
        if (peerRefs.peerTable != null) {
            peerRefs.peerTable.clearSelection();
        }
    }

    private void showPeerWaitingView() {
        populateSelfOnlyPeerTable("在線");
        if (peerRefs.peerStatusLabel != null) {
            peerRefs.peerStatusLabel.setText("連線中…（等待伺服器回傳同事列表）");
            peerRefs.peerStatusLabel.setForeground(new Color(100, 116, 139));
        }
    }

    private void populateSelfOnlyPeerTable(String selfStatusLabel) {
        if (peerRefs.peerTableModel == null) {
            return;
        }
        peerRefs.peerTableModel.setRowCount(0);
        peerRefs.peerTableModel.addRow(new Object[]{
                formatPeerRowId(heartbeatService.getClientId(), true),
                selfStatusLabel,
                countLocalScheduledTasks(),
                AppVersion.VERSION
        });
    }

    private void setPeerInteractionEnabled(boolean enabled) {
        if (peerRefs.messageField != null) {
            peerRefs.messageField.setEnabled(enabled);
        }
        if (peerRefs.sendMessageButton != null) {
            peerRefs.sendMessageButton.setEnabled(enabled);
        }
        if (peerRefs.pokeButton != null) {
            peerRefs.pokeButton.setEnabled(enabled);
        }
    }

    private String formatPeerRowId(String clientId, boolean self) {
        return self ? clientId + SELF_PEER_SUFFIX : clientId;
    }

    private String parsePeerRowId(String displayId) {
        if (displayId == null) {
            return null;
        }
        if (displayId.endsWith(SELF_PEER_SUFFIX)) {
            return displayId.substring(0, displayId.length() - SELF_PEER_SUFFIX.length());
        }
        return displayId;
    }

    private boolean isSelfClientId(String clientId) {
        return clientId != null && clientId.equals(heartbeatService.getClientId());
    }

    private int countLocalScheduledTasks() {
        return (int) schedulerService.getAllTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.SCHEDULED)
                .count();
    }

    private void showPeerMessage(String fromId, String text) {
        appendLog("[訊息] 【戳】來自【" + fromId + "】：" + text);
        Toolkit.getDefaultToolkit().beep();
        JOptionPane.showMessageDialog(
                this,
                text,
                "同事訊息 · " + fromId,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showPeerPoke(String fromId) {
        appendLog("[通知] 【戳】【" + fromId + "】戳了你！");
        Toolkit.getDefaultToolkit().beep();
        JOptionPane.showMessageDialog(
                this,
                "【" + fromId + "】戳了你，快看一下打卡狀態吧！",
                "同事戳你",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void bindCloudEventListeners() {
        serverRefs.enableServerCheckBox.addActionListener(e -> {
            boolean enabled = serverRefs.enableServerCheckBox.isSelected();
            syncCloudConnectionFieldsEnabled();
            if (enabled) {
                appendLog("[連線] 已勾選啟用雲端狀態回報，啟動單向心跳中...");
                refreshPeerInteractionState();
                startHeartbeatService();
            } else {
                appendLog("[離線] 已取消勾選，斷開雲端狀態回報（本機獨立運作模式）。");
                heartbeatService.stopHeartbeat();
                serverRefs.heartbeatStatusLabel.setText("[離線] 未連線 (已停用)");
                serverRefs.heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
                refreshPeerInteractionState();
            }
            saveCloudConfig();
        });
        serverRefs.clientIdCombo.addActionListener(e -> applyClientIdFromUI(true));
        java.awt.Component editor = serverRefs.clientIdCombo.getEditor().getEditorComponent();
        editor.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyClientIdFromUI(true);
            }
        });
        if (serverRefs.trustAllSslCheckBox != null) {
            serverRefs.trustAllSslCheckBox.addActionListener(e -> {
                boolean trust = serverRefs.trustAllSslCheckBox.isSelected();
                heartbeatService.setTrustAllSsl(trust);
                appendLog(trust
                        ? "[警告] 已啟用「信任所有 SSL」（僅建議本機除錯）"
                        : "[安全] 已關閉「信任所有 SSL」，使用系統憑證驗證");
                saveCloudConfig();
            });
        }
        if (serverRefs.serverUrlCombo != null) {
            RecentValuesHelper.attachTextChangeListener(serverRefs.serverUrlCombo, this::saveCloudConfig);
            java.awt.Component serverEditor = serverRefs.serverUrlCombo.getEditor().getEditorComponent();
            serverEditor.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    saveCloudConfig();
                }
            });
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveCloudConfig();
                if (slotController != null) {
                    slotController.persistTasks();
                }
                heartbeatService.stopHeartbeat();
                if (countdownTimer != null) {
                    countdownTimer.stop();
                }
                schedulerService.shutdown();
                automationService.shutdown();
            }
        });
    }

    private void loadPersistedConfig() {
        suppressConfigSave = true;
        try {
            ConfigPersistenceService.CloudConfig config = configPersistenceService.loadConfig(this::appendLog);
            applyServerConfig(config);
            slotController.loadConfigToUi(config);
            applyWindowLayout(config);
        } finally {
            suppressConfigSave = false;
        }
    }

    private void applyServerConfig(ConfigPersistenceService.CloudConfig config) {
        if (serverRefs.serverUrlCombo != null && config.serverUrl != null) {
            RecentValuesHelper.applyHistory(serverRefs.serverUrlCombo, config.recentServerUrls, config.serverUrl);
            if (!serverHistoryMenuBound) {
                RecentValuesHelper.bindHistoryMenu(
                        serverRefs.serverUrlCombo, config.recentServerUrls, this::saveCloudConfig);
                serverHistoryMenuBound = true;
            }
        }
        if (serverRefs.clientIdCombo != null && config.clientId != null) {
            ensureClientIdOption(config.clientId);
            serverRefs.clientIdCombo.setSelectedItem(config.clientId);
            heartbeatService.setClientId(config.clientId);
        }
        if (serverRefs.heartbeatTokenField != null && config.heartbeatToken != null) {
            serverRefs.heartbeatTokenField.setText(config.heartbeatToken);
            heartbeatService.setHeartbeatToken(config.heartbeatToken);
        }
        if (serverRefs.enableServerCheckBox != null) {
            serverRefs.enableServerCheckBox.setSelected(config.enableServer);
        }
        if (serverRefs.trustAllSslCheckBox != null) {
            serverRefs.trustAllSslCheckBox.setSelected(config.trustAllSsl);
            heartbeatService.setTrustAllSsl(config.trustAllSsl);
        }
        syncCloudConnectionFieldsEnabled();
        refreshPeerInteractionState();
    }

    /** 雲端連線中鎖定連線參數，避免執行中誤改（與打卡槽位「啟用時鎖定時分」相同邏輯） */
    private void syncCloudConnectionFieldsEnabled() {
        if (serverRefs.enableServerCheckBox == null) return;
        boolean cloudOn = serverRefs.enableServerCheckBox.isSelected();
        if (serverRefs.clientIdCombo != null) {
            serverRefs.clientIdCombo.setEnabled(!cloudOn);
            if (serverRefs.clientIdCombo.isEditable()) {
                java.awt.Component editor = serverRefs.clientIdCombo.getEditor().getEditorComponent();
                if (editor != null) {
                    editor.setEnabled(!cloudOn);
                }
            }
        }
        if (serverRefs.heartbeatTokenField != null) {
            serverRefs.heartbeatTokenField.setEnabled(!cloudOn);
        }
        if (serverRefs.serverUrlCombo != null) {
            RecentValuesHelper.setEnabled(serverRefs.serverUrlCombo, !cloudOn);
        }
        if (serverRefs.trustAllSslCheckBox != null) {
            serverRefs.trustAllSslCheckBox.setEnabled(!cloudOn);
        }
    }

    private void applyClientIdFromUI(boolean persist) {
        if (serverRefs.clientIdCombo == null) return;
        Object item = serverRefs.clientIdCombo.getSelectedItem();
        if (item == null) return;
        String clientId = item.toString().trim();
        if (clientId.isEmpty()) return;

        ensureClientIdOption(clientId);
        heartbeatService.setClientId(clientId);
        if (!isCloudEnabled()) {
            refreshPeerInteractionState();
        }
        if (persist) {
            saveCloudConfig();
        }
    }

    private void ensureClientIdOption(String clientId) {
        if (clientId == null || clientId.isBlank() || serverRefs.clientIdCombo == null) return;
        javax.swing.ComboBoxModel<String> model = serverRefs.clientIdCombo.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            if (clientId.equals(model.getElementAt(i))) {
                return;
            }
        }
        serverRefs.clientIdCombo.addItem(clientId);
    }

    private void saveCloudConfig() {
        if (suppressConfigSave || serverRefs.serverUrlCombo == null || slotController == null) return;

        ConfigPersistenceService.CloudConfig config = slotController.readConfigFromUi();
        config.serverUrl = RecentValuesHelper.getValue(serverRefs.serverUrlCombo);
        ConfigPersistenceService.pushRecent(config.recentServerUrls, config.serverUrl);
        Object clientItem = serverRefs.clientIdCombo.getSelectedItem();
        config.clientId = clientItem != null ? clientItem.toString().trim() : "company-worker";
        if (serverRefs.heartbeatTokenField != null) {
            config.heartbeatToken = new String(serverRefs.heartbeatTokenField.getPassword());
        }
        config.enableServer = serverRefs.enableServerCheckBox != null
                && serverRefs.enableServerCheckBox.isSelected();
        config.trustAllSsl = serverRefs.trustAllSslCheckBox != null
                && serverRefs.trustAllSslCheckBox.isSelected();
        captureWindowLayoutInto(config);
        configPersistenceService.saveConfig(config, null);
    }

    private void onSlotStateChanged() {
        slotController.refreshSlotCards();
        slotController.persistTasks();
        if (!isCloudEnabled()) {
            refreshPeerInteractionState();
        }
    }

    private void applyHeartbeatTokenFromUI() {
        if (serverRefs.heartbeatTokenField != null) {
            char[] tokenChars = serverRefs.heartbeatTokenField.getPassword();
            if (tokenChars.length > 0) {
                heartbeatService.setHeartbeatToken(new String(tokenChars));
            }
        }
    }

    private void startHeartbeatService() {
        if (serverRefs.enableServerCheckBox != null && !serverRefs.enableServerCheckBox.isSelected()) return;
        String serverUrl = RecentValuesHelper.getValue(serverRefs.serverUrlCombo);
        if (!serverUrl.isEmpty()) {
            applyHeartbeatTokenFromUI();
            saveCloudConfig();
            heartbeatService.startHeartbeat(serverUrl, this::appendLog, isOk -> {
                SwingUtilities.invokeLater(() -> {
                    if (serverRefs.enableServerCheckBox != null && !serverRefs.enableServerCheckBox.isSelected()) {
                        serverRefs.heartbeatStatusLabel.setText("[離線] 未連線 (已停用)");
                        serverRefs.heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
                        return;
                    }
                    if (isOk) {
                        serverRefs.heartbeatStatusLabel.setText("[正常] HTTP POST 正常");
                        serverRefs.heartbeatStatusLabel.setForeground(new Color(34, 197, 94));
                    } else {
                        serverRefs.heartbeatStatusLabel.setText("[異常] HTTP POST 異常");
                        serverRefs.heartbeatStatusLabel.setForeground(new Color(239, 68, 68));
                        appendLog("[提示] 若為 401，請先取消「啟用雲端」→ 修改 Token / 網址 → 再重新勾選");
                    }
                });
            });
        }
    }

    private void appendLog(String message) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        String logMessage = "[" + timestamp + "] " + message;
        SwingUtilities.invokeLater(() -> {
            logRefs.logTextArea.append(logMessage + "\n");
            logRefs.logTextArea.setCaretPosition(logRefs.logTextArea.getDocument().getLength());
        });
        System.out.println(logMessage);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            App app = new App();
            app.setVisible(true);
        });
    }
}
