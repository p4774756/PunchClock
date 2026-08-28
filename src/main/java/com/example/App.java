package com.example;

import com.example.model.CheckInTask;
import com.example.service.SpeechService;
import com.example.service.AutomationService;
import com.example.service.ConfigPersistenceService;
import com.example.service.HeartbeatService;
import com.example.service.SchedulerService;
import com.example.service.TaskPersistenceService;
import com.example.ui.PanelFactory;
import com.example.ui.PanelFactory.*;
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

    private final ServerConfigRefs serverRefs = new ServerConfigRefs();
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
        appendLog("📦 桌面端版本 v" + AppVersion.VERSION);
    }

    private void initHeartbeatService() {
        heartbeatService.setTasksProvider(schedulerService::getAllTasks);
        heartbeatService.setCommandListener(command -> {
            if ("CANCEL_SCHEDULE".equalsIgnoreCase(command)) {
                SwingUtilities.invokeLater(() -> {
                    schedulerService.cancelAllTasks("網頁後台遠端取消全部任務");
                    onSlotStateChanged();
                    appendLog("🛑 【遠端指令】收到網頁後台【取消全部任務】，已停止所有等待中的排程。");
                    heartbeatService.sendHeartbeat(this::appendLog, null);
                });
            } else if (command.startsWith("CANCEL_TASK:")) {
                String taskId = command.substring("CANCEL_TASK:".length()).trim();
                SwingUtilities.invokeLater(() -> {
                    CheckInTask task = schedulerService.getTask(taskId);
                    String name = task != null && task.getName() != null ? task.getName() : taskId;
                    String prev = task != null && task.getStatus() != null
                            ? task.getStatus().getDisplayName() : "未知";
                    boolean timerStopped = schedulerService.cancelTask(taskId, "網頁後台遠端取消");
                    onSlotStateChanged();
                    appendLog(String.format(
                            "🛑 【遠端指令】取消【%s】(%s)，取消前狀態：%s，計時器：%s",
                            name,
                            taskId,
                            prev,
                            timerStopped ? "已停止" : "當時沒有在跑（可能已執行完、已過期，或本來就不是等待中）"));
                    heartbeatService.sendHeartbeat(this::appendLog, null);
                });
            }
        });
    }

    private void initUI() {
        setTitle("上班打卡工具  v" + AppVersion.VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        Font mainFont = new Font("微軟正黑體", Font.PLAIN, 13);
        Font boldFont = new Font("微軟正黑體", Font.BOLD, 13);

        add(createDailyProverbBanner(mainFont, boldFont), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(boldFont);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) {
            tabs.setUI(new BasicTabbedPaneUI());
        }
        tabs.setBorder(new EmptyBorder(8, 12, 0, 12));

        JPanel slotPanel = PanelFactory.createSlotPanel(slotRefs, mainFont, boldFont);
        JPanel tasksTab = new JPanel(new BorderLayout());
        tasksTab.setBorder(new EmptyBorder(8, 4, 8, 4));
        tasksTab.add(slotPanel, BorderLayout.NORTH);

        JPanel serverBody = PanelFactory.createServerConfigBody(serverRefs, mainFont, boldFont);
        JPanel serverGroup = PanelFactory.createGroupPanel("🖥️ 雲端服務與裝置設定", boldFont);
        serverGroup.setLayout(new BorderLayout());
        serverGroup.add(serverBody, BorderLayout.NORTH);

        JPanel cloudTab = new JPanel(new BorderLayout());
        cloudTab.setBorder(new EmptyBorder(8, 4, 8, 4));
        cloudTab.add(serverGroup, BorderLayout.NORTH);

        tabs.addTab("📋 打卡任務", tasksTab);
        tabs.addTab("🖥️ 雲端設定", cloudTab);
        tabs.addTab("📡 ping/pong", PanelFactory.createHelpPanel(mainFont, boldFont));
        tabs.setSelectedIndex(0);

        JPanel logPanel = PanelFactory.createLogPanel(logRefs, boldFont);
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

        JButton speakButton = new JButton("🔊 發音");
        speakButton.setFont(mainFont);
        speakButton.setToolTipText("朗讀今日英文台詞");
        speakButton.addActionListener(e -> SpeechService.speakEnglish(proverb.en, this::appendLog));

        banner.add(textCol, BorderLayout.CENTER);
        banner.add(speakButton, BorderLayout.EAST);
        return banner;
    }

    private void bindCloudEventListeners() {
        serverRefs.enableServerCheckBox.addActionListener(e -> {
            boolean enabled = serverRefs.enableServerCheckBox.isSelected();
            syncTrustSslEnabled();
            if (enabled) {
                appendLog("🟢 已勾選啟用雲端狀態回報，啟動單向心跳中...");
                startHeartbeatService();
            } else {
                appendLog("🔴 已取消勾選，斷開雲端狀態回報（本機獨立運作模式）。");
                heartbeatService.stopHeartbeat();
                serverRefs.heartbeatStatusLabel.setText("⚪ 未連線 (已停用)");
                serverRefs.heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
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
                        ? "⚠️ 已啟用「信任所有 SSL」（僅建議本機除錯）"
                        : "🔒 已關閉「信任所有 SSL」，使用系統憑證驗證");
                saveCloudConfig();
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
        if (serverRefs.serverUrlTextField != null && config.serverUrl != null) {
            serverRefs.serverUrlTextField.setText(config.serverUrl);
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
        syncTrustSslEnabled();
    }

    /** 啟用雲端時鎖定 SSL 除錯選項，避免執行中誤改 */
    private void syncTrustSslEnabled() {
        if (serverRefs.trustAllSslCheckBox == null || serverRefs.enableServerCheckBox == null) return;
        boolean cloudOn = serverRefs.enableServerCheckBox.isSelected();
        serverRefs.trustAllSslCheckBox.setEnabled(!cloudOn);
    }

    private void applyClientIdFromUI(boolean persist) {
        if (serverRefs.clientIdCombo == null) return;
        Object item = serverRefs.clientIdCombo.getSelectedItem();
        if (item == null) return;
        String clientId = item.toString().trim();
        if (clientId.isEmpty()) return;

        ensureClientIdOption(clientId);
        heartbeatService.setClientId(clientId);
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
        if (suppressConfigSave || serverRefs.serverUrlTextField == null || slotController == null) return;

        ConfigPersistenceService.CloudConfig config = slotController.readConfigFromUi();
        config.serverUrl = serverRefs.serverUrlTextField.getText().trim();
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
        String serverUrl = serverRefs.serverUrlTextField.getText().trim();
        if (!serverUrl.isEmpty()) {
            applyHeartbeatTokenFromUI();
            saveCloudConfig();
            heartbeatService.startHeartbeat(serverUrl, this::appendLog, isOk -> {
                SwingUtilities.invokeLater(() -> {
                    if (serverRefs.enableServerCheckBox != null && !serverRefs.enableServerCheckBox.isSelected()) {
                        serverRefs.heartbeatStatusLabel.setText("⚪ 未連線 (已停用)");
                        serverRefs.heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
                        return;
                    }
                    if (isOk) {
                        serverRefs.heartbeatStatusLabel.setText("💚 HTTP POST 正常");
                        serverRefs.heartbeatStatusLabel.setForeground(new Color(34, 197, 94));
                    } else {
                        serverRefs.heartbeatStatusLabel.setText("🔴 HTTP POST 異常");
                        serverRefs.heartbeatStatusLabel.setForeground(new Color(239, 68, 68));
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
