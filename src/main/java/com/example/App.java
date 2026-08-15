package com.example;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.service.AutomationService;
import com.example.service.ConfigPersistenceService;
import com.example.service.HeartbeatService;
import com.example.service.SchedulerService;
import com.example.service.TaskPersistenceService;
import com.example.ui.PanelFactory;
import com.example.ui.PanelFactory.*;
import com.example.ui.TaskController;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 圖形介面主視窗 - 多任務與隨機時間打卡版
 * 負責組裝 UI 面板、綁定事件、協調 Service 層
 */
public class App extends JFrame {

    // --- UI 元件引用（透過 PanelFactory Refs 取得） ---
    private final ServerConfigRefs serverRefs = new ServerConfigRefs();
    private final TaskFormRefs formRefs = new TaskFormRefs();
    private final TaskTableRefs tableRefs = new TaskTableRefs();
    private final LogPanelRefs logRefs = new LogPanelRefs();

    // --- Service 層 ---
    private final SchedulerService schedulerService;
    private final AutomationService automationService;
    private final HeartbeatService heartbeatService;
    private final TaskPersistenceService persistenceService;
    private final ConfigPersistenceService configPersistenceService;
    private TaskController taskController;
    private boolean suppressConfigSave = false;

    public App() {
        this.schedulerService = new SchedulerService();
        this.automationService = new AutomationService();
        this.heartbeatService = new HeartbeatService();
        this.persistenceService = new TaskPersistenceService();
        this.configPersistenceService = new ConfigPersistenceService();

        initHeartbeatService();
        initUI();
        taskController = new TaskController(
                this, formRefs, tableRefs,
                schedulerService, automationService, heartbeatService,
                this::appendLog, this::onTaskStateChanged);
        bindEventListeners();

        loadPersistedCloudConfig();
        startHeartbeatService();
        loadPersistedTasks();
        appendLog("📦 桌面端版本 v" + AppVersion.VERSION);
    }

    // ==================== 初始化 ====================

    private void initHeartbeatService() {
        heartbeatService.setTasksProvider(schedulerService::getAllTasks);
        heartbeatService.setCommandListener(command -> {
            if ("CANCEL_SCHEDULE".equalsIgnoreCase(command)) {
                SwingUtilities.invokeLater(() -> {
                    schedulerService.cancelAllTasks();
                    onTaskStateChanged();
                    appendLog("🛑 【遠端指令】收到網頁後台取消所有排程指令。");
                });
            } else if (command.startsWith("CANCEL_TASK:")) {
                String taskId = command.substring("CANCEL_TASK:".length()).trim();
                SwingUtilities.invokeLater(() -> {
                    schedulerService.cancelTask(taskId);
                    onTaskStateChanged();
                    appendLog("🛑 【遠端指令】收到網頁後台取消任務 [" + taskId + "] 指令。");
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

        // 分頁：打卡任務（預設） / 雲端設定；日誌固定底部
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(boldFont);
        tabs.setBorder(new EmptyBorder(8, 12, 0, 12));

        JPanel taskFormBody = PanelFactory.createTaskFormBody(formRefs, mainFont, boldFont);
        JPanel taskFormGroup = PanelFactory.createCollapsibleGroupPanel(
                "⚙️ 打卡任務設定", taskFormBody, boldFont, false);

        JPanel tableGroup = PanelFactory.createTaskTablePanel(tableRefs, mainFont, boldFont);

        JPanel tasksTab = new JPanel(new BorderLayout(0, 8));
        tasksTab.setBorder(new EmptyBorder(8, 4, 8, 4));
        tasksTab.add(taskFormGroup, BorderLayout.NORTH);
        tasksTab.add(tableGroup, BorderLayout.CENTER);

        JPanel serverBody = PanelFactory.createServerConfigBody(serverRefs, mainFont, boldFont);
        JPanel serverGroup = PanelFactory.createGroupPanel("🖥️ 雲端服務與裝置設定", boldFont);
        serverGroup.setLayout(new BorderLayout());
        serverGroup.add(serverBody, BorderLayout.NORTH);

        JPanel cloudTab = new JPanel(new BorderLayout());
        cloudTab.setBorder(new EmptyBorder(8, 4, 8, 4));
        cloudTab.add(serverGroup, BorderLayout.NORTH);

        tabs.addTab("📋 打卡任務", tasksTab);
        tabs.addTab("🖥️ 雲端設定", cloudTab);
        tabs.setSelectedIndex(0);

        JPanel logPanel = PanelFactory.createLogPanel(logRefs, boldFont);
        logPanel.setBorder(new CompoundBorder(new EmptyBorder(0, 12, 10, 12), logPanel.getBorder()));
        logPanel.setMinimumSize(new Dimension(400, 90));
        tabs.setMinimumSize(new Dimension(400, 220));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, logPanel);
        split.setResizeWeight(0.72);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setDividerSize(8);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        setMinimumSize(new Dimension(1180, 740));
        setSize(1280, 840);
        setLocationRelativeTo(null);
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.72));
    }

    private void bindEventListeners() {
        // 快捷模板
        formRefs.presetWorkInButton.addActionListener(e -> taskController.applyPreset("上班打卡", 9, 0, true));
        formRefs.presetWorkOutButton.addActionListener(e -> taskController.applyPreset("下班打卡", 18, 0, true));
        formRefs.presetTest1MinButton.addActionListener(e -> taskController.applyTestPreset(1));
        formRefs.presetTest3MinButton.addActionListener(e -> taskController.applyTestPreset(3));

        // 表單操作
        formRefs.addTaskButton.addActionListener(e -> taskController.addNewTaskFromForm());
        formRefs.batchAddButton.addActionListener(e -> taskController.addBatchTasksFromForm());
        formRefs.selectWorkdaysButton.addActionListener(e -> taskController.setWorkdaysSelected(true));
        formRefs.clearWorkdaysButton.addActionListener(e -> taskController.clearAllWeekdaySelection());

        // 任務列表操作
        tableRefs.selectAllTasksButton.addActionListener(e -> {
            if (tableRefs.taskTable.getRowCount() > 0) {
                tableRefs.taskTable.selectAll();
            }
        });
        tableRefs.clearTaskSelectionButton.addActionListener(e -> tableRefs.taskTable.clearSelection());
        tableRefs.cancelTaskButton.addActionListener(e -> taskController.cancelSelectedTasks());
        tableRefs.deleteTaskButton.addActionListener(e -> taskController.deleteSelectedTasks());
        tableRefs.executeNowButton.addActionListener(e -> taskController.executeSelectedTasksNow());
        tableRefs.editTaskButton.addActionListener(e -> taskController.editSelectedTask());
        tableRefs.reuseTaskButton.addActionListener(e -> taskController.reuseSelectedTask());
        // 雲端設定
        serverRefs.enableServerCheckBox.addActionListener(e -> {
            boolean enabled = serverRefs.enableServerCheckBox.isSelected();
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
        // 可編輯 Combo：輸入後失焦也要套用
        java.awt.Component editor = serverRefs.clientIdCombo.getEditor().getEditorComponent();
        editor.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyClientIdFromUI(true);
            }
        });
        serverRefs.testServerButton.addActionListener(e -> testServerConnection());
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

        // 視窗關閉
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveCloudConfig();
                persistenceService.saveTasks(schedulerService.getAllTasks(), null);
                heartbeatService.stopHeartbeat();
                schedulerService.shutdown();
                automationService.shutdown();
            }
        });
    }

    // ==================== 雲端設定持久化 ====================

    private void loadPersistedCloudConfig() {
        suppressConfigSave = true;
        try {
            ConfigPersistenceService.CloudConfig config = configPersistenceService.loadConfig(this::appendLog);
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
        } finally {
            suppressConfigSave = false;
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
        if (persist) {
            saveCloudConfig();
        }
    }

    /** 若自訂 Worker ID 不在預設清單中，加入選項以便下次下拉可見 */
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
        if (suppressConfigSave || serverRefs.serverUrlTextField == null) return;

        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
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
        configPersistenceService.saveConfig(config, null);
    }

    // ==================== 任務持久化 ====================

    private void loadPersistedTasks() {
        List<CheckInTask> loaded = persistenceService.loadTasks(this::appendLog);
        int rescheduled = 0;
        for (CheckInTask task : loaded) {
            if (task.getStatus() == TaskStatus.SCHEDULED) {
                boolean ok = schedulerService.scheduleTask(task,
                        t -> SwingUtilities.invokeLater(this::onTaskStateChanged),
                        this::appendLog, taskController::executeCheckInForTask);
                if (ok) rescheduled++;
            } else {
                schedulerService.addTaskRecord(task);
            }
        }
        if (rescheduled > 0) {
            appendLog(String.format("✅ 已自動重新排定 %d 個尚未過期的任務", rescheduled));
        }
        taskController.refreshTaskTable();
    }

    private void onTaskStateChanged() {
        taskController.refreshTaskTable();
        persistenceService.saveTasks(schedulerService.getAllTasks(), null);
    }

    // ==================== 心跳服務 ====================

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

    private void testServerConnection() {
        String serverUrl = serverRefs.serverUrlTextField.getText().trim();
        if (serverUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請先輸入有效的 ping-pong-server 網址！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        applyHeartbeatTokenFromUI();
        heartbeatService.testConnection(serverUrl, this::appendLog, isOk -> {
            SwingUtilities.invokeLater(() -> {
                if (isOk) {
                    saveCloudConfig();
                    serverRefs.heartbeatStatusLabel.setText("💚 HTTP POST 正常");
                    serverRefs.heartbeatStatusLabel.setForeground(new Color(34, 197, 94));
                    JOptionPane.showMessageDialog(this, "✅ 成功連線至 ping-pong-server！", "測試成功", JOptionPane.INFORMATION_MESSAGE);
                    startHeartbeatService();
                } else {
                    serverRefs.heartbeatStatusLabel.setText("🔴 HTTP POST 異常");
                    serverRefs.heartbeatStatusLabel.setForeground(new Color(239, 68, 68));
                    JOptionPane.showMessageDialog(this, "❌ 無法連線至指定 Server，請確認網址或 Server 狀態！", "測試失敗", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
    }

    // ==================== 工具方法 ====================

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

    // ==================== Main ====================

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
