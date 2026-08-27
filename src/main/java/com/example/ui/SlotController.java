package com.example.ui;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.model.WorkSlot;
import com.example.service.AutomationService;
import com.example.service.ConfigPersistenceService;
import com.example.service.ConfigPersistenceService.CloudConfig;
import com.example.service.ConfigPersistenceService.SlotSettings;
import com.example.service.HeartbeatService;
import com.example.service.SchedulerService;
import com.example.service.SlotScheduleHelper;
import com.example.service.TaskPersistenceService;

import javax.swing.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * 上班 / 下班雙槽位排程控制器。
 */
public class SlotController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JFrame owner;
    private final PanelFactory.SlotPanelRefs slotRefs;
    private final SchedulerService schedulerService;
    private final AutomationService automationService;
    private final HeartbeatService heartbeatService;
    private final TaskPersistenceService persistenceService;
    private final ConfigPersistenceService configPersistenceService;
    private final Consumer<String> appendLog;
    private final Runnable onSlotStateChanged;

    private CloudConfig config;
    private boolean suppressUiSave;

    public SlotController(
            JFrame owner,
            PanelFactory.SlotPanelRefs slotRefs,
            SchedulerService schedulerService,
            AutomationService automationService,
            HeartbeatService heartbeatService,
            TaskPersistenceService persistenceService,
            ConfigPersistenceService configPersistenceService,
            Consumer<String> appendLog,
            Runnable onSlotStateChanged) {
        this.owner = owner;
        this.slotRefs = slotRefs;
        this.schedulerService = schedulerService;
        this.automationService = automationService;
        this.heartbeatService = heartbeatService;
        this.persistenceService = persistenceService;
        this.configPersistenceService = configPersistenceService;
        this.appendLog = appendLog;
        this.onSlotStateChanged = onSlotStateChanged;
        this.config = new CloudConfig();
    }

    public void bindUi() {
        bindSlotCard(slotRefs.workIn, WorkSlot.Kind.WORK_IN);
        bindSlotCard(slotRefs.workOut, WorkSlot.Kind.WORK_OUT);
        bindSharedSettingsListeners();
    }

    public void loadConfigToUi(CloudConfig loaded) {
        suppressUiSave = true;
        try {
            config = loaded;
            applySlotToUi(slotRefs.workIn, config.workIn);
            applySlotToUi(slotRefs.workOut, config.workOut);
            slotRefs.urlTextField.setText(config.targetUrl);
            slotRefs.buttonIdTextField.setText(config.buttonId);
            slotRefs.browserCombo.setSelectedItem(config.browserChoice);
            slotRefs.weekdaysOnlyCheckBox.setSelected(config.weekdaysOnly);
        } finally {
            suppressUiSave = false;
        }
    }

    public CloudConfig readConfigFromUi() {
        readSlotFromUi(slotRefs.workIn, config.workIn);
        readSlotFromUi(slotRefs.workOut, config.workOut);
        config.targetUrl = slotRefs.urlTextField.getText().trim();
        config.buttonId = slotRefs.buttonIdTextField.getText().trim();
        Object browser = slotRefs.browserCombo.getSelectedItem();
        config.browserChoice = browser != null ? browser.toString() : config.browserChoice;
        config.weekdaysOnly = slotRefs.weekdaysOnlyCheckBox.isSelected();
        return config;
    }

    public void saveConfig() {
        if (suppressUiSave) return;
        configPersistenceService.saveConfig(readConfigFromUi(), null);
    }

    public CloudConfig getConfig() {
        return config;
    }

    public void initializeFromPersistence() {
        List<CheckInTask> loaded = persistenceService.loadTasks(appendLog);
        schedulerService.removeAllTasks();

        CheckInTask workIn = findOrMigrate(loaded, WorkSlot.Kind.WORK_IN);
        CheckInTask workOut = findOrMigrate(loaded, WorkSlot.Kind.WORK_OUT);

        schedulerService.addTaskRecord(workIn);
        schedulerService.addTaskRecord(workOut);

        int scheduled = 0;
        for (WorkSlot.Kind kind : WorkSlot.Kind.values()) {
            if (scheduleSlot(kind, false)) {
                scheduled++;
            }
        }
        if (scheduled > 0) {
            appendLog.accept(String.format("✅ 已排程 %d 個打卡槽位", scheduled));
        }
        refreshSlotCards();
        persistTasks();
    }

    public void refreshSlotCards() {
        SwingUtilities.invokeLater(() -> {
            refreshCard(slotRefs.workIn, WorkSlot.Kind.WORK_IN);
            refreshCard(slotRefs.workOut, WorkSlot.Kind.WORK_OUT);
        });
    }

    public void refreshCountdowns() {
        refreshSlotCards();
    }

    private void bindSlotCard(PanelFactory.SlotCardRefs refs, WorkSlot.Kind kind) {
        java.awt.event.ActionListener change = e -> onSlotSettingsChanged(kind);
        refs.enabledCheckBox.addActionListener(change);
        refs.hourCombo.addActionListener(change);
        refs.minuteCombo.addActionListener(change);
        refs.randomOffsetCheckBox.addActionListener(change);
        refs.executeNowButton.addActionListener(e -> executeNow(kind));
    }

    private void bindSharedSettingsListeners() {
        javax.swing.event.DocumentListener textSave = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                onSharedSettingsChanged();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                onSharedSettingsChanged();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                onSharedSettingsChanged();
            }
        };
        slotRefs.urlTextField.getDocument().addDocumentListener(textSave);
        slotRefs.buttonIdTextField.getDocument().addDocumentListener(textSave);
        slotRefs.browserCombo.addActionListener(e -> onSharedSettingsChanged());
        slotRefs.weekdaysOnlyCheckBox.addActionListener(e -> onSharedSettingsChanged());
    }

    private void onSlotSettingsChanged(WorkSlot.Kind kind) {
        if (suppressUiSave) return;
        readConfigFromUi();
        saveConfig();
        scheduleSlot(kind, true);
    }

    private void onSharedSettingsChanged() {
        if (suppressUiSave) return;
        readConfigFromUi();
        saveConfig();
        for (CheckInTask task : schedulerService.getAllTasks()) {
            if (WorkSlot.isSlotId(task.getId())) {
                SlotScheduleHelper.applySharedSettings(task, config);
            }
        }
        persistTasks();
        refreshSlotCards();
    }

    public void scheduleAllEnabledSlots(boolean logSummary) {
        readConfigFromUi();
        saveConfig();
        int scheduled = 0;
        for (WorkSlot.Kind kind : WorkSlot.Kind.values()) {
            if (scheduleSlot(kind, true)) {
                scheduled++;
            }
        }
        if (logSummary) {
            appendLog.accept(String.format("📌 已更新排程：%d 個槽位啟用中", scheduled));
        }
        refreshSlotCards();
        persistTasks();
        heartbeatService.sendHeartbeat(appendLog, null);
    }

    private boolean scheduleSlot(WorkSlot.Kind kind, boolean logChanges) {
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task == null) {
            task = SlotScheduleHelper.buildTask(kind, config,
                    SlotScheduleHelper.nextTriggerTime(slot.hour, slot.minute, config.weekdaysOnly, LocalDateTime.now()));
            schedulerService.addTaskRecord(task);
        }

        if (!slot.enabled) {
            if (task.getStatus() == TaskStatus.SCHEDULED || task.getStatus() == TaskStatus.CHECKING_IN) {
                schedulerService.cancelTask(kind.id);
                task.setResultMessage("槽位已停用");
            }
            if (logChanges) {
                appendLog.accept("⏸️ 【" + kind.displayName + "】已停用");
            }
            onSlotStateChanged.run();
            return false;
        }

        if (task.getStatus() == TaskStatus.CHECKING_IN) {
            return false;
        }

        LocalDateTime nextTime = resolveNextScheduleTime(kind, task, slot);
        resetTaskForSchedule(task, kind, slot, nextTime);

        boolean ok = schedulerService.scheduleTask(
                task,
                t -> SwingUtilities.invokeLater(onSlotStateChanged),
                logChanges ? appendLog : null,
                this::executeCheckInForTask);
        onSlotStateChanged.run();
        return ok;
    }

    private LocalDateTime resolveNextScheduleTime(WorkSlot.Kind kind, CheckInTask task, SlotSettings slot) {
        LocalDateTime now = LocalDateTime.now();
        if (task.getStatus() == TaskStatus.SCHEDULED && task.hasComputedSchedule()) {
            LocalDateTime trigger = task.getActualTriggerTime();
            if (trigger != null && trigger.isAfter(now)) {
                task.setTargetTime(trigger.withSecond(0).withNano(0));
                return task.getTargetTime();
            }
        }
        return SlotScheduleHelper.nextTriggerTime(slot.hour, slot.minute, config.weekdaysOnly, now);
    }

    private void resetTaskForSchedule(
            CheckInTask task, WorkSlot.Kind kind, SlotSettings slot, LocalDateTime targetTime) {
        schedulerService.cancelTask(kind.id);
        task.setName(kind.displayName);
        task.setTargetTime(targetTime);
        SlotScheduleHelper.applySharedSettings(task, config);
        SlotScheduleHelper.applySlotSettings(task, slot);
        task.setActualTriggerTime(null);
        task.setRandomOffsetSeconds(0);
        task.setResultMessage("");
        task.setStatus(TaskStatus.PENDING);
    }

    private CheckInTask findOrMigrate(List<CheckInTask> loaded, WorkSlot.Kind kind) {
        for (CheckInTask task : loaded) {
            if (kind.id.equals(task.getId())) {
                return task;
            }
        }
        for (CheckInTask task : loaded) {
            if (kind.displayName.equals(task.getName())) {
                task.setId(kind.id);
                return task;
            }
        }
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        return SlotScheduleHelper.buildTask(kind, config,
                SlotScheduleHelper.nextTriggerTime(slot.hour, slot.minute, config.weekdaysOnly, LocalDateTime.now()));
    }

    private void executeNow(WorkSlot.Kind kind) {
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task == null) {
            JOptionPane.showMessageDialog(owner, "找不到【" + kind.displayName + "】槽位。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        appendLog.accept("⚡ 【立即執行】" + kind.displayName);
        new Thread(() -> executeCheckInForTask(task, null)).start();
    }

    public void executeCheckInForTask(CheckInTask task, Runnable onComplete) {
        LocalDateTime triggerTime = LocalDateTime.now();
        String triggerTimeStr = triggerTime.format(FMT);
        long startTimeMs = System.currentTimeMillis();

        try {
            boolean ok = automationService.executeCheckIn(
                    task.getTargetUrl(), task.getButtonId(), task.getBrowserType(), appendLog);
            double durationSec = (System.currentTimeMillis() - startTimeMs) / 1000.0;
            String finishTimeStr = LocalDateTime.now().format(FMT);

            if (ok) {
                String msg = String.format("✅ 打卡成功！(觸發: %s, 完成: %s, 耗時: %.1f秒)", triggerTimeStr, finishTimeStr, durationSec);
                task.setStatus(TaskStatus.SUCCESS);
                task.setResultMessage(msg);
                appendLog.accept("🎉 【" + task.getName() + "】" + msg);
            } else {
                String msg = String.format("❌ 打卡失敗 (觸發: %s, 耗時: %.1f秒)", triggerTimeStr, durationSec);
                task.setStatus(TaskStatus.FAILED);
                task.setResultMessage(msg);
                appendLog.accept("❌ 【" + task.getName() + "】" + msg);
            }
        } catch (Exception ex) {
            double durationSec = (System.currentTimeMillis() - startTimeMs) / 1000.0;
            String msg = String.format("❌ 打卡失敗：%s (觸發: %s, 耗時: %.1f秒)", sanitizeErrorMessage(ex.getMessage()), triggerTimeStr, durationSec);
            task.setStatus(TaskStatus.FAILED);
            task.setResultMessage(msg);
            appendLog.accept("❌ 【" + task.getName() + "】" + msg);
        } finally {
            onSlotTaskFinished(task);
            if (onComplete != null) onComplete.run();
        }
    }

    private void onSlotTaskFinished(CheckInTask task) {
        WorkSlot.Kind kind = WorkSlot.Kind.fromId(task.getId());
        if (kind != null) {
            SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
            if (slot.enabled) {
                scheduleSlot(kind, false);
            }
        }
        onSlotStateChanged.run();
        persistTasks();
        refreshSlotCards();
        heartbeatService.sendHeartbeat(appendLog, null);
    }

    public void persistTasks() {
        persistenceService.saveTasks(schedulerService.getAllTasks(), null);
    }

    private void refreshCard(PanelFactory.SlotCardRefs refs, WorkSlot.Kind kind) {
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task == null) {
            setWrappedMetricLabel(refs.statusLabel, null);
            setWrappedMetricLabel(refs.countdownLabel, null);
            setWrappedMetricLabel(refs.triggerLabel, null);
            setWrappedMetricLabel(refs.resultLabel, null);
            return;
        }
        setWrappedMetricLabel(refs.statusLabel, task.getStatus().getBadge());
        setWrappedMetricLabel(refs.countdownLabel, task.getCountdownLabel());
        if (task.getStatus() == TaskStatus.SCHEDULED && task.hasComputedSchedule()) {
            String offset = task.isUseRandomOffset()
                    ? String.format(" (%s%ds)", task.getRandomOffsetSeconds() >= 0 ? "+" : "", task.getRandomOffsetSeconds())
                    : " (精準)";
            setWrappedMetricLabel(refs.triggerLabel, task.getFormattedActualTime() + offset);
        } else if (task.getTargetTime() != null) {
            setWrappedMetricLabel(refs.triggerLabel, task.getFormattedTargetTime());
        } else {
            setWrappedMetricLabel(refs.triggerLabel, "—");
        }
        setWrappedMetricLabel(refs.resultLabel, task.getResultMessage());
    }

    private void setWrappedMetricLabel(JLabel label, String text) {
        if (text == null || text.isBlank()) {
            label.setText("—");
            label.setToolTipText(null);
            return;
        }
        label.setText(text);
        label.setToolTipText(text);
    }

    private void applySlotToUi(PanelFactory.SlotCardRefs refs, SlotSettings slot) {
        refs.enabledCheckBox.setSelected(slot.enabled);
        refs.hourCombo.setSelectedIndex(clamp(slot.hour, 0, 23));
        refs.minuteCombo.setSelectedIndex(clamp(slot.minute, 0, 59));
        refs.randomOffsetCheckBox.setSelected(slot.useRandomOffset);
    }

    private void readSlotFromUi(PanelFactory.SlotCardRefs refs, SlotSettings slot) {
        slot.enabled = refs.enabledCheckBox.isSelected();
        slot.hour = refs.hourCombo.getSelectedIndex();
        slot.minute = refs.minuteCombo.getSelectedIndex();
        slot.useRandomOffset = refs.randomOffsetCheckBox.isSelected();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
}
