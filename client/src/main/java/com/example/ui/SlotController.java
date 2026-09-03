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
    private boolean historyMenusBound;

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
        slotRefs.executeNowButton.addActionListener(e -> executeNowShared());
        refreshSharedSettingsActions();
    }

    public void loadConfigToUi(CloudConfig loaded) {
        suppressUiSave = true;
        try {
            config = loaded;
            applySlotToUi(slotRefs.workIn, config.workIn);
            applySlotToUi(slotRefs.workOut, config.workOut);
            RecentValuesHelper.applyHistory(slotRefs.urlCombo, config.recentTargetUrls, config.targetUrl);
            RecentValuesHelper.applyHistory(slotRefs.buttonIdCombo, config.recentButtonIds, config.buttonId);
            slotRefs.browserCombo.setSelectedItem(config.browserChoice);
            bindHistoryMenusIfNeeded();
            refreshSharedSettingsActions();
        } finally {
            suppressUiSave = false;
        }
    }

    private void bindHistoryMenusIfNeeded() {
        if (historyMenusBound) {
            return;
        }
        RecentValuesHelper.bindHistoryMenu(slotRefs.urlCombo, config.recentTargetUrls, this::saveConfigAfterHistoryEdit);
        RecentValuesHelper.bindHistoryMenu(slotRefs.buttonIdCombo, config.recentButtonIds, this::saveConfigAfterHistoryEdit);
        historyMenusBound = true;
    }

    public CloudConfig readConfigFromUi() {
        readSlotFromUi(slotRefs.workIn, config.workIn);
        readSlotFromUi(slotRefs.workOut, config.workOut);
        config.targetUrl = RecentValuesHelper.getValue(slotRefs.urlCombo);
        config.buttonId = RecentValuesHelper.getValue(slotRefs.buttonIdCombo);
        Object browser = slotRefs.browserCombo.getSelectedItem();
        config.browserChoice = browser != null ? browser.toString() : config.browserChoice;
        config.weekdaysOnly = true; // 上班工具固定跳過週末
        return config;
    }

    public void saveConfig() {
        if (suppressUiSave) return;
        readConfigFromUi();
        ConfigPersistenceService.pushRecent(config.recentTargetUrls, config.targetUrl);
        ConfigPersistenceService.pushRecent(config.recentButtonIds, config.buttonId);
        configPersistenceService.saveConfig(config, null);
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

        readConfigFromUi();
        for (WorkSlot.Kind kind : WorkSlot.Kind.values()) {
            SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
            if (slot.enabled && !isSharedSettingsReady()) {
                slot.enabled = false;
                applySlotToUi(refsFor(kind), slot);
                appendLog.accept(String.format(
                        "[警告] 【%s】打卡網址或 Selector 未設定，已取消「啟用」", kind.displayName));
            }
        }
        saveConfig();

        int scheduled = 0;
        for (WorkSlot.Kind kind : WorkSlot.Kind.values()) {
            if (scheduleSlot(kind, false)) {
                scheduled++;
            }
        }
        if (scheduled > 0) {
            appendLog.accept(String.format("[成功] 已排程 %d 個打卡槽位", scheduled));
        }
        for (WorkSlot.Kind kind : WorkSlot.Kind.values()) {
            CheckInTask t = schedulerService.getTask(kind.id);
            if (t == null) continue;
            if (t.getStatus() == TaskStatus.SCHEDULED) {
                appendLog.accept(String.format("[排程] 【%s】下次觸發：%s", kind.displayName, t.getFormattedActualTime()));
            } else {
                String extra = t.getResultMessage() != null && !t.getResultMessage().isBlank()
                        ? "（" + t.getResultMessage() + "）" : "";
                appendLog.accept(String.format("[資訊] 【%s】目前狀態：%s%s",
                        kind.displayName, t.getStatus().getDisplayName(), extra));
            }
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

    /**
     * 網頁後台取消單一任務：停止計時並取消「啟用」，避免仍顯示為開啟而自動重排。
     */
    public void handleRemoteCancelTask(String taskId) {
        WorkSlot.Kind kind = WorkSlot.Kind.fromId(taskId);
        CheckInTask task = schedulerService.getTask(taskId);
        String name = task != null && task.getName() != null ? task.getName() : taskId;
        String prev = task != null && task.getStatus() != null
                ? task.getStatus().getDisplayName() : "未知";
        boolean timerStopped = schedulerService.cancelTask(taskId, "網頁後台遠端取消");

        if (kind != null) {
            disableSlotInUi(kind);
            appendLog.accept(String.format(
                    "[取消] 【遠端指令】取消【%s】(%s)，取消前狀態：%s，計時器：%s，已取消「啟用」",
                    name,
                    taskId,
                    prev,
                    timerStopped ? "已停止" : "當時沒有在跑（可能已執行完、已過期，或本來就不是等待中）"));
        } else {
            appendLog.accept(String.format(
                    "[取消] 【遠端指令】取消【%s】(%s)，取消前狀態：%s，計時器：%s",
                    name,
                    taskId,
                    prev,
                    timerStopped ? "已停止" : "當時沒有在跑（可能已執行完、已過期，或本來就不是等待中）"));
        }

        onSlotStateChanged.run();
        heartbeatService.sendHeartbeat(appendLog, null);
    }

    /**
     * 網頁後台取消全部任務：停止所有計時並取消兩槽位的「啟用」。
     */
    public void handleRemoteCancelAll() {
        schedulerService.cancelAllTasks("網頁後台遠端取消全部任務");
        disableSlotInUi(WorkSlot.Kind.WORK_IN);
        disableSlotInUi(WorkSlot.Kind.WORK_OUT);
        onSlotStateChanged.run();
        appendLog.accept("[取消] 【遠端指令】收到網頁後台【取消全部任務】，已停止所有等待中的排程，並取消「啟用」。");
        heartbeatService.sendHeartbeat(appendLog, null);
    }

    private void disableSlotInUi(WorkSlot.Kind kind) {
        setSlotEnabledProgrammatically(kind, false);
    }

    /** 程式變更「啟用」時不觸發 onEnableChanged，避免遠端取消後又被誤判為重新啟用 */
    private void setSlotEnabledProgrammatically(WorkSlot.Kind kind, boolean enabled) {
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        slot.enabled = enabled;
        PanelFactory.SlotCardRefs refs = refsFor(kind);
        java.awt.event.ActionListener[] listeners = refs.enabledCheckBox.getActionListeners();
        for (java.awt.event.ActionListener listener : listeners) {
            refs.enabledCheckBox.removeActionListener(listener);
        }
        suppressUiSave = true;
        try {
            applySlotToUi(refs, slot);
        } finally {
            suppressUiSave = false;
            for (java.awt.event.ActionListener listener : listeners) {
                refs.enabledCheckBox.addActionListener(listener);
            }
        }
        configPersistenceService.saveConfig(config, appendLog);
    }

    private PanelFactory.SlotCardRefs refsFor(WorkSlot.Kind kind) {
        return kind == WorkSlot.Kind.WORK_IN ? slotRefs.workIn : slotRefs.workOut;
    }

    private void bindSlotCard(PanelFactory.SlotCardRefs refs, WorkSlot.Kind kind) {
        refs.enabledCheckBox.addActionListener(e -> {
            syncSlotEditorsEnabled(refs);
            onEnableChanged(kind);
        });
        refs.hourCombo.addActionListener(e -> onSlotTimingChanged(kind));
        refs.minuteCombo.addActionListener(e -> onSlotTimingChanged(kind));
        refs.randomOffsetCheckBox.addActionListener(e -> onSlotTimingChanged(kind));
        syncSlotEditorsEnabled(refs);
    }

    /** 啟用中鎖定時分／隨機偏移，避免誤改正在排程的設定 */
    private void syncSlotEditorsEnabled(PanelFactory.SlotCardRefs refs) {
        boolean editable = !refs.enabledCheckBox.isSelected();
        refs.hourCombo.setEnabled(editable);
        refs.minuteCombo.setEnabled(editable);
        refs.randomOffsetCheckBox.setEnabled(editable);
    }

    private void bindSharedSettingsListeners() {
        Runnable onSharedChanged = this::onSharedSettingsChanged;
        RecentValuesHelper.attachTextChangeListener(slotRefs.urlCombo, onSharedChanged);
        RecentValuesHelper.attachTextChangeListener(slotRefs.buttonIdCombo, onSharedChanged);
        slotRefs.browserCombo.addActionListener(e -> onSharedSettingsChanged());
    }

    private void saveConfigAfterHistoryEdit() {
        if (suppressUiSave) return;
        readConfigFromUi();
        configPersistenceService.saveConfig(config, null);
        refreshSharedSettingsActions();
    }

    private void onEnableChanged(WorkSlot.Kind kind) {
        if (suppressUiSave) return;
        readConfigFromUi();
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        if (slot.enabled && !isSharedSettingsReady()) {
            revertSlotEnable(kind);
            showSharedSettingsRequiredMessage();
            refreshSharedSettingsActions();
            return;
        }
        saveConfig();
        if (!slot.enabled) {
            scheduleSlot(kind, true);
            heartbeatService.sendHeartbeat(appendLog, null);
            refreshSharedSettingsActions();
            return;
        }
        snapshotSharedSettingsToTask(kind);
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task != null && task.getStatus() == TaskStatus.CANCELLED) {
            rescheduleEnabledSlot(kind);
        } else {
            scheduleSlot(kind, true);
        }
        heartbeatService.sendHeartbeat(appendLog, null);
        refreshSharedSettingsActions();
    }

    private boolean isSharedSettingsReady() {
        String url = RecentValuesHelper.getValue(slotRefs.urlCombo);
        String buttonId = RecentValuesHelper.getValue(slotRefs.buttonIdCombo);
        return !url.isBlank() && !buttonId.isBlank();
    }

    private void showSharedSettingsRequiredMessage() {
        UiFonts.showWarning(owner, "請先設定打卡網址與 Selector！", "提示");
    }

    private void revertSlotEnable(WorkSlot.Kind kind) {
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        slot.enabled = false;
        PanelFactory.SlotCardRefs refs = refsFor(kind);
        suppressUiSave = true;
        try {
            refs.enabledCheckBox.setSelected(false);
            syncSlotEditorsEnabled(refs);
        } finally {
            suppressUiSave = false;
        }
    }

    private void refreshSharedSettingsActions() {
        boolean ready = isSharedSettingsReady();
        slotRefs.executeNowButton.setEnabled(ready);
        slotRefs.executeNowButton.setToolTipText(ready
                ? "使用上方共用設定立即測試，不會變更已啟用槽位的鎖定設定"
                : "請先設定打卡網址與 Selector");
    }

    /** 啟用當下將上方共用設定寫入該槽位任務（之後改共用欄位不會影響已啟用槽位） */
    private void snapshotSharedSettingsToTask(WorkSlot.Kind kind) {
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task == null) {
            return;
        }
        SlotScheduleHelper.applySharedSettings(task, config);
        appendLog.accept(String.format(
                "[鎖定] 【%s】已鎖定網址：%s，Selector：%s",
                kind.displayName, task.getTargetUrl(), task.getButtonId()));
    }

    /** 已取消的槽位僅儲存時分設定，不自動重排（需重新勾選「啟用」才會排程） */
    private void onSlotTimingChanged(WorkSlot.Kind kind) {
        if (suppressUiSave) return;
        readConfigFromUi();
        saveConfig();
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        if (!slot.enabled) {
            return;
        }
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task != null && task.getStatus() == TaskStatus.CANCELLED) {
            return;
        }
        if (scheduleSlot(kind, true)) {
            heartbeatService.sendHeartbeat(appendLog, null);
        }
    }

    private void rescheduleEnabledSlot(WorkSlot.Kind kind) {
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task == null) {
            scheduleSlot(kind, true);
            return;
        }
        snapshotSharedSettingsToTask(kind);
        LocalDateTime nextTime = SlotScheduleHelper.nextTriggerTime(
                slot.hour, slot.minute, config.weekdaysOnly, LocalDateTime.now());
        resetTaskForSchedule(task, kind, slot, nextTime);
        boolean ok = schedulerService.scheduleTask(
                task,
                t -> SwingUtilities.invokeLater(onSlotStateChanged),
                appendLog,
                this::executeCheckInForTask);
        onSlotStateChanged.run();
        if (ok) {
            appendLog.accept(String.format("[排程] 【%s】已重新啟用並排程", kind.displayName));
        }
    }

    private void onSharedSettingsChanged() {
        if (suppressUiSave) return;
        readConfigFromUi();
        saveConfig();
        refreshSharedSettingsActions();
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
            appendLog.accept(String.format("[排程] 已更新排程：%d 個槽位啟用中", scheduled));
        }
        refreshSlotCards();
        persistTasks();
        heartbeatService.sendHeartbeat(appendLog, null);
    }

    private boolean scheduleSlot(WorkSlot.Kind kind, boolean logChanges) {
        return scheduleSlot(kind, logChanges, false);
    }

    private boolean scheduleSlot(WorkSlot.Kind kind, boolean logChanges, boolean skipRemainingTodayWindow) {
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task == null) {
            task = SlotScheduleHelper.buildTask(kind, config,
                    SlotScheduleHelper.nextTriggerTime(slot.hour, slot.minute, config.weekdaysOnly, LocalDateTime.now()));
            schedulerService.addTaskRecord(task);
        }

        if (!slot.enabled) {
            if (task.getStatus() == TaskStatus.SCHEDULED || task.getStatus() == TaskStatus.CHECKING_IN) {
                schedulerService.cancelTask(kind.id, "槽位已停用");
                task.setResultMessage("槽位已停用");
            }
            if (logChanges) {
                appendLog.accept("[停用] 【" + kind.displayName + "】已停用，排程已取消");
            }
            onSlotStateChanged.run();
            return false;
        }

        if (task.getStatus() == TaskStatus.CHECKING_IN) {
            return false;
        }

        if (task.getStatus() == TaskStatus.CANCELLED) {
            return false;
        }

        LocalDateTime nextTime = skipRemainingTodayWindow
                ? SlotScheduleHelper.nextTriggerTimeAfterTodaysSlot(
                        slot.hour, slot.minute, config.weekdaysOnly, LocalDateTime.now())
                : resolveNextScheduleTime(kind, task, slot);
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
        schedulerService.stopTimer(kind.id);
        task.setName(kind.displayName);
        task.setTargetTime(targetTime);
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

    private void executeNowShared() {
        readConfigFromUi();
        if (!isSharedSettingsReady()) {
            showSharedSettingsRequiredMessage();
            refreshSharedSettingsActions();
            return;
        }
        // 共用設定執行一次即可；結果寫入目前啟用中的槽位（優先上班）
        WorkSlot.Kind kind = config.workIn.enabled
                ? WorkSlot.Kind.WORK_IN
                : (config.workOut.enabled ? WorkSlot.Kind.WORK_OUT : WorkSlot.Kind.WORK_IN);
        executeNow(kind);
    }

    private void executeNow(WorkSlot.Kind kind) {
        readConfigFromUi();
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task == null) {
            JOptionPane.showMessageDialog(owner, "找不到【" + kind.displayName + "】槽位。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String url = config.targetUrl;
        String buttonId = config.buttonId;
        String browser = TaskEditDialog.parseBrowserType(config.browserChoice);
        appendLog.accept("[執行] 【立即執行】使用上方共用設定（不變更已鎖定排程，結果記入【" + kind.displayName + "】）");
        new Thread(() -> executeCheckInForTask(task, url, buttonId, browser, null, false)).start();
    }

    public void executeCheckInForTask(CheckInTask task, Runnable onComplete) {
        executeCheckInForTask(task, task.getTargetUrl(), task.getButtonId(), task.getBrowserType(), onComplete, true);
    }

    private void executeCheckInForTask(
            CheckInTask task, String targetUrl, String buttonId, String browserType,
            Runnable onComplete, boolean fromScheduler) {
        LocalDateTime triggerTime = LocalDateTime.now();
        String triggerTimeStr = triggerTime.format(FMT);
        long startTimeMs = System.currentTimeMillis();

        try {
            boolean ok = automationService.executeCheckIn(
                    targetUrl, buttonId, browserType, appendLog);
            double durationSec = (System.currentTimeMillis() - startTimeMs) / 1000.0;
            String finishTimeStr = LocalDateTime.now().format(FMT);

            if (ok) {
                String msg = String.format("[成功] 打卡成功！(觸發: %s, 完成: %s, 耗時: %.1f秒)", triggerTimeStr, finishTimeStr, durationSec);
                task.setStatus(TaskStatus.SUCCESS);
                task.setResultMessage(msg);
                appendLog.accept("[成功] 【" + task.getName() + "】" + msg);
            } else {
                String msg = String.format("[失敗] 打卡失敗 (觸發: %s, 耗時: %.1f秒)", triggerTimeStr, durationSec);
                task.setStatus(TaskStatus.FAILED);
                task.setResultMessage(msg);
                appendLog.accept("[失敗] 【" + task.getName() + "】" + msg);
            }
        } catch (Exception ex) {
            double durationSec = (System.currentTimeMillis() - startTimeMs) / 1000.0;
            String msg = String.format("[失敗] 打卡失敗：%s (觸發: %s, 耗時: %.1f秒)", sanitizeErrorMessage(ex.getMessage()), triggerTimeStr, durationSec);
            task.setStatus(TaskStatus.FAILED);
            task.setResultMessage(msg);
            appendLog.accept("[失敗] 【" + task.getName() + "】" + msg);
        } finally {
            onSlotTaskFinished(task, fromScheduler);
            if (onComplete != null) onComplete.run();
        }
    }

    private void onSlotTaskFinished(CheckInTask task, boolean fromScheduler) {
        WorkSlot.Kind kind = WorkSlot.Kind.fromId(task.getId());
        if (kind != null) {
            SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
            if (slot.enabled) {
                // 排程成功打卡後略過今天剩餘時段；立即執行不消耗今日排程
                boolean skipRemainingToday = fromScheduler && task.getStatus() == TaskStatus.SUCCESS;
                // 等排程執行緒跑完再重排，避免 stopTimer 中斷同一條執行緒
                SwingUtilities.invokeLater(() -> scheduleSlot(kind, skipRemainingToday, skipRemainingToday));
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
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        if (task == null) {
            setWrappedMetricLabel(refs.statusLabel, null);
            setWrappedMetricLabel(refs.countdownLabel, null);
            setWrappedMetricLabel(refs.triggerLabel, null);
            setWrappedMetricLabel(refs.resultLabel, null);
            setWrappedMetricLabel(refs.lockedSettingsLabel, null);
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
        refreshLockedSettingsLabel(refs, task, slot);
    }

    private static final int METRIC_LABEL_MAX_CHARS = 36;
    private static final int LOCKED_SUMMARY_MAX_CHARS = 56;

    private void refreshLockedSettingsLabel(PanelFactory.SlotCardRefs refs, CheckInTask task, SlotSettings slot) {
        if (!slot.enabled) {
            refs.lockedSettingsLabel.setText("—");
            refs.lockedSettingsLabel.setToolTipText("未啟用；下次勾選「啟用」時會鎖定上方共用設定");
            return;
        }
        String url = nullToDash(task.getTargetUrl());
        String selector = nullToDash(task.getButtonId());
        if ("—".equals(url) && "—".equals(selector)) {
            setWrappedMetricLabel(refs.lockedSettingsLabel, null);
            return;
        }
        String summary = formatLockedSummary(url, selector);
        setMetricLabelWithTooltip(refs.lockedSettingsLabel, summary, buildLockedSettingsTooltip(task));
    }

    private static String formatLockedSummary(String url, String selector) {
        if ("—".equals(selector)) {
            return truncateForDisplay(url, LOCKED_SUMMARY_MAX_CHARS);
        }
        if ("—".equals(url)) {
            return selector;
        }
        String suffix = "｜" + selector;
        if (url.length() + suffix.length() <= LOCKED_SUMMARY_MAX_CHARS) {
            return url + suffix;
        }
        int urlBudget = LOCKED_SUMMARY_MAX_CHARS - suffix.length() - 1;
        if (urlBudget < 6) {
            return truncateForDisplay(selector, LOCKED_SUMMARY_MAX_CHARS);
        }
        return url.substring(0, urlBudget) + "…" + suffix;
    }

    private String buildLockedSettingsTooltip(CheckInTask task) {
        String url = nullToDash(task.getTargetUrl());
        String selector = nullToDash(task.getButtonId());
        String browser = nullToDash(task.getBrowserType());
        String draftUrl = config.targetUrl;
        String draftSelector = config.buttonId;
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("網址：").append(url)
                .append("\nSelector：").append(selector)
                .append("\n瀏覽器：").append(browser);
        boolean draftDiffers = (draftUrl != null && !draftUrl.isBlank() && !draftUrl.equals(task.getTargetUrl()))
                || (draftSelector != null && !draftSelector.isBlank() && !draftSelector.equals(task.getButtonId()));
        if (draftDiffers) {
            tooltip.append("\n\n上方欄位目前：").append(nullToDash(draftUrl))
                    .append("｜").append(nullToDash(draftSelector))
                    .append("\n（與鎖定值不同，需取消啟用後再啟用才會更新）");
        } else {
            tooltip.append("\n\n啟用時鎖定，改上方共用欄位不影響此槽位");
        }
        return tooltip.toString();
    }

    private static String truncateForDisplay(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "—";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars - 1) + "…";
    }

    private void setMetricLabelWithTooltip(JLabel label, String displayText, String tooltip) {
        label.setText(displayText);
        label.setToolTipText(tooltip);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private void setWrappedMetricLabel(JLabel label, String text) {
        if (text == null || text.isBlank()) {
            label.setText("—");
            label.setToolTipText(null);
            return;
        }
        if (text.length() > METRIC_LABEL_MAX_CHARS) {
            label.setText(text.substring(0, METRIC_LABEL_MAX_CHARS - 1) + "…");
        } else {
            label.setText(text);
        }
        label.setToolTipText(text);
    }

    private void applySlotToUi(PanelFactory.SlotCardRefs refs, SlotSettings slot) {
        refs.enabledCheckBox.setSelected(slot.enabled);
        refs.hourCombo.setSelectedIndex(clamp(slot.hour, 0, 23));
        refs.minuteCombo.setSelectedIndex(clamp(slot.minute, 0, 59));
        refs.randomOffsetCheckBox.setSelected(slot.useRandomOffset);
        syncSlotEditorsEnabled(refs);
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
            return "網頁連線或按鈕點擊逾時 (Timeout 45s)";
        }
        if (sanitized.contains("網頁載入逾時")) {
            int start = sanitized.indexOf(']');
            return start >= 0 ? sanitized.substring(start + 1).trim() : sanitized;
        }
        if (sanitized.startsWith("[失敗]")) {
            sanitized = sanitized.substring(4).trim();
        }
        if (sanitized.startsWith("打卡失敗：")) {
            sanitized = sanitized.substring(5).trim();
        }
        if (sanitized.startsWith("打卡過程中發生錯誤：")) {
            sanitized = sanitized.substring(10).trim();
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
