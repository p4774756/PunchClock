package com.example;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.service.AutomationService;
import com.example.service.HeartbeatService;
import com.example.service.SchedulerService;
import com.example.service.TaskPersistenceService;
import com.example.ui.PanelFactory;
import com.example.ui.PanelFactory.*;
import com.example.ui.TaskEditDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    public App() {
        this.schedulerService = new SchedulerService();
        this.automationService = new AutomationService();
        this.heartbeatService = new HeartbeatService();
        this.persistenceService = new TaskPersistenceService();

        initHeartbeatService();
        initUI();
        bindEventListeners();

        startHeartbeatService();
        loadPersistedTasks();
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
        setTitle("圖形日曆多任務排程自動打卡控制台 (含隨機浮動打卡)");
        setSize(860, 840);
        setMinimumSize(new Dimension(820, 760));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        Font mainFont = new Font("微軟正黑體", Font.PLAIN, 13);
        Font boldFont = new Font("微軟正黑體", Font.BOLD, 13);

        JPanel mainContentPanel = new JPanel();
        mainContentPanel.setLayout(new BoxLayout(mainContentPanel, BoxLayout.Y_AXIS));
        mainContentPanel.setBorder(new EmptyBorder(10, 12, 6, 12));

        // 分組 1: 雲端服務設定
        JPanel serverBody = PanelFactory.createServerConfigBody(serverRefs, mainFont, boldFont);
        JPanel serverGroup = PanelFactory.createCollapsibleGroupPanel("🖥️ 雲端服務與裝置設定", serverBody, boldFont, false);
        mainContentPanel.add(serverGroup);
        mainContentPanel.add(Box.createVerticalStrut(6));

        // 分組 2: 任務設定表單
        JPanel taskFormBody = PanelFactory.createTaskFormBody(formRefs, mainFont, boldFont);
        JPanel taskFormGroup = PanelFactory.createCollapsibleGroupPanel("⚙️ 打卡任務設定與快捷模板", taskFormBody, boldFont, false);
        mainContentPanel.add(taskFormGroup);
        mainContentPanel.add(Box.createVerticalStrut(6));

        // 分組 3: 任務列表
        JPanel tableGroup = PanelFactory.createTaskTablePanel(tableRefs, mainFont, boldFont);
        mainContentPanel.add(tableGroup);
        mainContentPanel.add(Box.createVerticalStrut(6));

        add(mainContentPanel, BorderLayout.NORTH);

        // 分組 4: 系統日誌
        JPanel logPanel = PanelFactory.createLogPanel(logRefs, boldFont);
        add(logPanel, BorderLayout.CENTER);
    }

    private void bindEventListeners() {
        // 快捷模板
        formRefs.presetWorkInButton.addActionListener(e -> applyPreset("上班打卡", 9, 0, true));
        formRefs.presetWorkOutButton.addActionListener(e -> applyPreset("下班打卡", 18, 0, true));
        formRefs.presetTest1MinButton.addActionListener(e -> applyTestPreset(1));
        formRefs.presetTest3MinButton.addActionListener(e -> applyTestPreset(3));

        // 表單操作
        formRefs.addTaskButton.addActionListener(e -> addNewTaskFromForm());
        formRefs.batchAddButton.addActionListener(e -> addBatchTasksFromForm());
        formRefs.selectWorkdaysButton.addActionListener(e -> setWorkdaysSelected(true));
        formRefs.clearWorkdaysButton.addActionListener(e -> setWorkdaysSelected(false));

        // 任務列表操作
        tableRefs.cancelTaskButton.addActionListener(e -> cancelSelectedTask());
        tableRefs.deleteTaskButton.addActionListener(e -> deleteSelectedTask());
        tableRefs.executeNowButton.addActionListener(e -> executeSelectedTaskNow());
        tableRefs.editTaskButton.addActionListener(e -> editSelectedTask());
        tableRefs.reuseTaskButton.addActionListener(e -> reuseSelectedTask());
        tableRefs.cancelAllButton.addActionListener(e -> {
            schedulerService.cancelAllTasks();
            onTaskStateChanged();
            heartbeatService.sendHeartbeat(null, null);
            appendLog("🛑 已取消所有排定之打卡任務。");
        });
        tableRefs.deleteAllButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "確定要清空並刪除列表中【所有】打卡任務嗎？", "刪除全部確認",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                schedulerService.removeAllTasks();
                onTaskStateChanged();
                heartbeatService.sendHeartbeat(null, null);
                appendLog("🗑️ 已成功刪除並清空所有打卡任務紀錄。");
            }
        });

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
        });
        serverRefs.clientIdCombo.addActionListener(e -> {
            String selected = (String) serverRefs.clientIdCombo.getSelectedItem();
            if (selected != null && !selected.trim().isEmpty()) {
                heartbeatService.setClientId(selected.trim());
            }
        });
        serverRefs.testServerButton.addActionListener(e -> testServerConnection());

        // 視窗關閉
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                persistenceService.saveTasks(schedulerService.getAllTasks(), null);
                heartbeatService.stopHeartbeat();
                schedulerService.shutdown();
                automationService.shutdown();
            }
        });
    }

    // ==================== 任務持久化 ====================

    private void loadPersistedTasks() {
        List<CheckInTask> loaded = persistenceService.loadTasks(this::appendLog);
        int rescheduled = 0;
        for (CheckInTask task : loaded) {
            if (task.getStatus() == TaskStatus.SCHEDULED) {
                boolean ok = schedulerService.scheduleTask(task,
                        t -> SwingUtilities.invokeLater(this::onTaskStateChanged),
                        this::appendLog, this::executeCheckInForTask);
                if (ok) rescheduled++;
            } else {
                schedulerService.addTaskRecord(task);
            }
        }
        if (rescheduled > 0) {
            appendLog(String.format("✅ 已自動重新排定 %d 個尚未過期的任務", rescheduled));
        }
        refreshTaskTable();
    }

    private void onTaskStateChanged() {
        refreshTaskTable();
        persistenceService.saveTasks(schedulerService.getAllTasks(), null);
    }

    // ==================== 表單操作 ====================

    private void setWorkdaysSelected(boolean select) {
        formRefs.monCheckBox.setSelected(select);
        formRefs.tueCheckBox.setSelected(select);
        formRefs.wedCheckBox.setSelected(select);
        formRefs.thuCheckBox.setSelected(select);
        formRefs.friCheckBox.setSelected(select);
        formRefs.satCheckBox.setSelected(false);
        formRefs.sunCheckBox.setSelected(false);
    }

    private void applyPreset(String taskName, int targetHour, int targetMin, boolean useRandom) {
        formRefs.taskNameTextField.setText(taskName);
        formRefs.datePicker.setDateToToday();
        formRefs.hourCombo.setSelectedIndex(targetHour);
        formRefs.minuteCombo.setSelectedIndex(targetMin);
        formRefs.randomOffsetCheckBox.setSelected(useRandom);
        appendLog(String.format("💡 已載入預設模板【%s】(時間 %02d:%02d, 隨機浮動: %s)",
                taskName, targetHour, targetMin, useRandom ? "開啟" : "關閉"));
    }

    private void applyTestPreset(int minutesFromNow) {
        LocalDateTime testTime = LocalDateTime.now().plusMinutes(minutesFromNow);
        formRefs.taskNameTextField.setText("⚡ 測試打卡 (+" + minutesFromNow + "分)");
        formRefs.datePicker.setDate(testTime.toLocalDate());
        formRefs.hourCombo.setSelectedIndex(testTime.getHour());
        formRefs.minuteCombo.setSelectedIndex(testTime.getMinute());
        formRefs.randomOffsetCheckBox.setSelected(false);
        appendLog(String.format("⚡ 已載入測試快捷：當前時間 +%d 分鐘 (%02d:%02d)，自動關閉隨機時間以利精準測試！",
                minutesFromNow, testTime.getHour(), testTime.getMinute()));
    }

    // ==================== 任務管理 ====================

    private void addNewTaskFromForm() {
        String name = formRefs.taskNameTextField.getText().trim();
        if (name.isEmpty()) name = "打卡任務";

        String targetUrl = formRefs.urlTextField.getText().trim();
        if (targetUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入目標打卡網址！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String buttonId = formRefs.buttonIdTextField.getText().trim();
        if (buttonId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入打卡按鈕 Selector！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate selectedDate = formRefs.datePicker.getDate();
        if (selectedDate == null) {
            JOptionPane.showMessageDialog(this, "請選擇有效的日期！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int hour = Integer.parseInt((String) formRefs.hourCombo.getSelectedItem());
        int minute = Integer.parseInt((String) formRefs.minuteCombo.getSelectedItem());
        LocalDateTime targetTime = selectedDate.atTime(hour, minute, 0);

        LocalDateTime now = LocalDateTime.now();
        if (targetTime.isBefore(now.plusSeconds(5))) {
            targetTime = now.plusMinutes(1).withSecond(0).withNano(0);
            formRefs.hourCombo.setSelectedIndex(targetTime.getHour());
            formRefs.minuteCombo.setSelectedIndex(targetTime.getMinute());
        }

        boolean useRandom = formRefs.randomOffsetCheckBox.isSelected();
        String browserType = TaskEditDialog.parseBrowserType((String) formRefs.browserCombo.getSelectedItem());

        CheckInTask task = new CheckInTask(name, targetUrl, buttonId, targetTime, useRandom, browserType);
        boolean scheduled = schedulerService.scheduleTask(task,
                t -> SwingUtilities.invokeLater(this::onTaskStateChanged),
                this::appendLog, this::executeCheckInForTask);

        if (!scheduled) {
            JOptionPane.showMessageDialog(this, "無法排定任務，可能是選擇的時間與隨機偏移已屬於過去！", "時間錯誤", JOptionPane.ERROR_MESSAGE);
        } else {
            onTaskStateChanged();
            heartbeatService.sendHeartbeat(this::appendLog, null);
        }
    }

    private void addBatchTasksFromForm() {
        String name = formRefs.taskNameTextField.getText().trim();
        if (name.isEmpty()) name = "打卡任務";

        String targetUrl = formRefs.urlTextField.getText().trim();
        if (targetUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入目標打卡網址！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String buttonId = formRefs.buttonIdTextField.getText().trim();
        if (buttonId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入打卡按鈕 Selector！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int hour = Integer.parseInt((String) formRefs.hourCombo.getSelectedItem());
        int minute = Integer.parseInt((String) formRefs.minuteCombo.getSelectedItem());
        boolean useRandom = formRefs.randomOffsetCheckBox.isSelected();
        String browserType = TaskEditDialog.parseBrowserType((String) formRefs.browserCombo.getSelectedItem());

        List<DayOfWeek> selectedDays = new ArrayList<>();
        if (formRefs.monCheckBox.isSelected()) selectedDays.add(DayOfWeek.MONDAY);
        if (formRefs.tueCheckBox.isSelected()) selectedDays.add(DayOfWeek.TUESDAY);
        if (formRefs.wedCheckBox.isSelected()) selectedDays.add(DayOfWeek.WEDNESDAY);
        if (formRefs.thuCheckBox.isSelected()) selectedDays.add(DayOfWeek.THURSDAY);
        if (formRefs.friCheckBox.isSelected()) selectedDays.add(DayOfWeek.FRIDAY);
        if (formRefs.satCheckBox.isSelected()) selectedDays.add(DayOfWeek.SATURDAY);
        if (formRefs.sunCheckBox.isSelected()) selectedDays.add(DayOfWeek.SUNDAY);

        if (selectedDays.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請先勾選至少一個星期（例如 週一~週五）！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate baseDate = formRefs.datePicker.getDate();
        if (baseDate == null) baseDate = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int addedCount = 0;

        for (DayOfWeek dayOfWeek : selectedDays) {
            LocalDate targetDate = baseDate;
            while (targetDate.getDayOfWeek() != dayOfWeek) {
                targetDate = targetDate.plusDays(1);
            }
            if (targetDate.isBefore(LocalDate.now())) {
                targetDate = targetDate.plusWeeks(1);
            }

            LocalDateTime targetTime = targetDate.atTime(hour, minute, 0);
            if (targetTime.isBefore(now.plusSeconds(5))) {
                targetDate = targetDate.plusWeeks(1);
                targetTime = targetDate.atTime(hour, minute, 0);
            }

            String dayName = getDayOfWeekName(dayOfWeek);
            String taskFullName = name + " (" + dayName + ")";

            CheckInTask task = new CheckInTask(taskFullName, targetUrl, buttonId, targetTime, useRandom, browserType);
            boolean scheduled = schedulerService.scheduleTask(task,
                    t -> SwingUtilities.invokeLater(this::onTaskStateChanged),
                    this::appendLog, this::executeCheckInForTask);
            if (scheduled) addedCount++;
        }

        if (addedCount > 0) {
            onTaskStateChanged();
            heartbeatService.sendHeartbeat(this::appendLog, null);
            appendLog("🗓️ 【批量排定】成功一次排定 " + addedCount + " 個工作日打卡任務 (含隨機時間浮動)！");
            JOptionPane.showMessageDialog(this, "成功一次排定 " + addedCount + " 個星期的打卡任務！", "批量成功", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "無法排定任務，可能是選擇的時間已過！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ==================== 打卡執行 ====================

    private void executeCheckInForTask(CheckInTask task, Runnable onComplete) {
        LocalDateTime triggerTime = LocalDateTime.now();
        String triggerTimeStr = triggerTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        long startTimeMs = System.currentTimeMillis();

        try {
            boolean ok = automationService.executeCheckIn(task.getTargetUrl(), task.getButtonId(), task.getBrowserType(), this::appendLog);
            long durationMs = System.currentTimeMillis() - startTimeMs;
            double durationSec = durationMs / 1000.0;
            String finishTimeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            if (ok) {
                String msg = String.format("✅ 打卡成功！(觸發: %s, 完成: %s, 耗時: %.1f秒)", triggerTimeStr, finishTimeStr, durationSec);
                task.setStatus(TaskStatus.SUCCESS);
                task.setResultMessage(msg);
                appendLog("🎉 【" + task.getName() + "】" + msg);
            } else {
                String msg = String.format("❌ 打卡失敗 (觸發: %s, 耗時: %.1f秒)", triggerTimeStr, durationSec);
                task.setStatus(TaskStatus.FAILED);
                task.setResultMessage(msg);
                appendLog("❌ 【" + task.getName() + "】" + msg);
            }
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTimeMs;
            double durationSec = durationMs / 1000.0;
            String cleanMsg = sanitizeErrorMessage(ex.getMessage());
            String msg = String.format("❌ 打卡失敗：%s (觸發: %s, 耗時: %.1f秒)", cleanMsg, triggerTimeStr, durationSec);
            task.setStatus(TaskStatus.FAILED);
            task.setResultMessage(msg);
            appendLog("❌ 【" + task.getName() + "】" + msg);
        } finally {
            onTaskStateChanged();
            heartbeatService.sendHeartbeat(this::appendLog, null);
            if (onComplete != null) onComplete.run();
        }
    }

    // ==================== 任務列表操作 ====================

    private void cancelSelectedTask() {
        int selectedRow = tableRefs.taskTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "請先選取列表中要取消的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskId = (String) tableRefs.tableModel.getValueAt(selectedRow, 0);
        boolean ok = schedulerService.cancelTask(taskId);
        onTaskStateChanged();
        heartbeatService.sendHeartbeat(null, null);
        appendLog(ok ? "🛑 已取消任務 ID [" + taskId + "]" : "⚠️ 任務 ID [" + taskId + "] 不存在或已結束");
    }

    private void deleteSelectedTask() {
        int selectedRow = tableRefs.taskTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "請先選取列表中要刪除的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskId = (String) tableRefs.tableModel.getValueAt(selectedRow, 0);
        schedulerService.removeTask(taskId);
        onTaskStateChanged();
        heartbeatService.sendHeartbeat(null, null);
        appendLog("🗑️ 已移除任務紀錄 ID [" + taskId + "]");
    }

    private void executeSelectedTaskNow() {
        int selectedRow = tableRefs.taskTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "請先選取列表中要立即執行的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskId = (String) tableRefs.tableModel.getValueAt(selectedRow, 0);
        CheckInTask task = schedulerService.getTask(taskId);
        if (task != null) {
            appendLog("⚡ 【立即執行】觸發任務【" + task.getName() + "】進行打卡...");
            new Thread(() -> executeCheckInForTask(task, null)).start();
        }
    }

    private void editSelectedTask() {
        int selectedRow = tableRefs.taskTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "請先選取列表中要編輯的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskId = (String) tableRefs.tableModel.getValueAt(selectedRow, 0);
        CheckInTask task = schedulerService.getTask(taskId);
        if (task == null) {
            JOptionPane.showMessageDialog(this, "找不到該任務！", "錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (task.getStatus() != TaskStatus.SCHEDULED) {
            JOptionPane.showMessageDialog(this, "只有狀態為【⏳ 等待中】的任務才可編輯！\n若要重新使用已完成的任務，請使用【🔄 重新排定】按鈕。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        TaskEditDialog.Result result = new TaskEditDialog(this, task, false).showDialog();
        if (result != null) {
            schedulerService.cancelTask(task.getId());
            task.setName(result.name);
            task.setTargetUrl(result.targetUrl);
            task.setButtonId(result.buttonId);
            task.setTargetTime(result.targetTime);
            task.setUseRandomOffset(result.useRandomOffset);
            task.setBrowserType(result.browserType);
            task.setStatus(TaskStatus.PENDING);
            task.setResultMessage("");

            boolean scheduled = schedulerService.scheduleTask(task,
                    t -> SwingUtilities.invokeLater(this::onTaskStateChanged),
                    this::appendLog, this::executeCheckInForTask);
            if (scheduled) {
                onTaskStateChanged();
                heartbeatService.sendHeartbeat(this::appendLog, null);
                appendLog(String.format("✏️ 【編輯完成】任務【%s】已更新排程設定", result.name));
            } else {
                JOptionPane.showMessageDialog(this, "無法排定任務，可能是選擇的時間已過！", "時間錯誤", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void reuseSelectedTask() {
        int selectedRow = tableRefs.taskTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "請先選取列表中要重新排定的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskId = (String) tableRefs.tableModel.getValueAt(selectedRow, 0);
        CheckInTask task = schedulerService.getTask(taskId);
        if (task == null) {
            JOptionPane.showMessageDialog(this, "找不到該任務！", "錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }
        TaskStatus status = task.getStatus();
        if (status == TaskStatus.SCHEDULED || status == TaskStatus.CHECKING_IN) {
            JOptionPane.showMessageDialog(this, "該任務仍在排程中或執行中，無需重新排定！\n若要修改設定，請使用【✏️ 編輯任務】按鈕。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        TaskEditDialog.Result result = new TaskEditDialog(this, task, true).showDialog();
        if (result != null) {
            CheckInTask newTask = new CheckInTask(result.name, result.targetUrl, result.buttonId,
                    result.targetTime, result.useRandomOffset, result.browserType);
            boolean scheduled = schedulerService.scheduleTask(newTask,
                    t -> SwingUtilities.invokeLater(this::onTaskStateChanged),
                    this::appendLog, this::executeCheckInForTask);
            if (scheduled) {
                onTaskStateChanged();
                heartbeatService.sendHeartbeat(this::appendLog, null);
                appendLog(String.format("🔄 【重新排定】已根據任務【%s】建立新排程任務【%s】", task.getName(), result.name));
            } else {
                JOptionPane.showMessageDialog(this, "無法排定任務，可能是選擇的時間已過！", "時間錯誤", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ==================== Table 刷新 ====================

    private void refreshTaskTable() {
        SwingUtilities.invokeLater(() -> {
            DefaultTableModel model = tableRefs.tableModel;
            JTable table = tableRefs.taskTable;

            int selectedRow = table.getSelectedRow();
            String selectedTaskId = (selectedRow >= 0 && selectedRow < model.getRowCount())
                    ? (String) model.getValueAt(selectedRow, 0) : null;

            model.setRowCount(0);
            List<CheckInTask> tasks = schedulerService.getAllTasks();
            int restoreRow = -1;

            for (int i = 0; i < tasks.size(); i++) {
                CheckInTask t = tasks.get(i);
                if (selectedTaskId != null && selectedTaskId.equals(t.getId())) {
                    restoreRow = i;
                }
                String statusStr = t.getStatus().getBadge();
                String offsetStr = t.isUseRandomOffset()
                        ? String.format("%s (%s%ds)", t.getFormattedActualTime(), t.getRandomOffsetSeconds() >= 0 ? "+" : "", t.getRandomOffsetSeconds())
                        : t.getFormattedActualTime() + " (精準)";

                model.addRow(new Object[]{
                        t.getId(), t.getName(), t.getFormattedTargetTime(), offsetStr,
                        t.getTargetUrl(), formatBrowserName(t.getBrowserType()),
                        statusStr, t.getResultMessage()
                });
            }

            if (restoreRow >= 0 && restoreRow < table.getRowCount()) {
                table.setRowSelectionInterval(restoreRow, restoreRow);
            }
        });
    }

    // ==================== 心跳服務 ====================

    private void startHeartbeatService() {
        if (serverRefs.enableServerCheckBox != null && !serverRefs.enableServerCheckBox.isSelected()) return;
        String serverUrl = serverRefs.serverUrlTextField.getText().trim();
        if (!serverUrl.isEmpty()) {
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
        heartbeatService.testConnection(serverUrl, this::appendLog, isOk -> {
            SwingUtilities.invokeLater(() -> {
                if (isOk) {
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

    private String getDayOfWeekName(DayOfWeek day) {
        switch (day) {
            case MONDAY: return "週一";
            case TUESDAY: return "週二";
            case WEDNESDAY: return "週三";
            case THURSDAY: return "週四";
            case FRIDAY: return "週五";
            case SATURDAY: return "週六";
            case SUNDAY: return "週日";
            default: return "";
        }
    }

    private String formatBrowserName(String browserType) {
        if (browserType == null) return "Edge";
        switch (browserType) {
            case "msedge": case "edge": return "Edge";
            case "chrome": return "Chrome";
            case "chromium": return "Chromium";
            case "firefox": return "Firefox";
            case "webkit": return "WebKit";
            default: return browserType;
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

    private String sanitizeErrorMessage(String rawMsg) {
        if (rawMsg == null) return "打卡異常";
        String sanitized = rawMsg.replaceAll("https?://[^\\s\"'>]+", "[隱私保護網址]");
        if (sanitized.contains("Timeout") && sanitized.contains("exceeded")) {
            return "打卡失敗：網頁連線或按鈕點擊逾時 (Timeout 30s)";
        }
        int callLogIdx = sanitized.indexOf("Call log:");
        if (callLogIdx != -1) {
            sanitized = sanitized.substring(0, callLogIdx).trim();
        }
        if (sanitized.length() > 60) {
            sanitized = sanitized.substring(0, 60) + "...";
        }
        return sanitized;
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
