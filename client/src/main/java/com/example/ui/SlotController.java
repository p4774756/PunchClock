package com.example.ui;

import com.example.model.CheckInHistoryEntry;
import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.model.WorkSlot;
import com.example.service.AutomationService;
import com.example.service.CheckInHistoryService;
import com.example.service.ConfigPersistenceService;
import com.example.service.ConfigPersistenceService.CloudConfig;
import com.example.service.ConfigPersistenceService.SlotSettings;
import com.example.service.HeartbeatService;
import com.example.service.SchedulerService;
import com.example.service.SlotScheduleHelper;
import com.example.service.TaskPersistenceService;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 上班排程控制器。
 */
public class SlotController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JFrame owner;
    private final PanelFactory.SlotPanelRefs slotRefs;
    private final PanelFactory.CheckInHistoryRefs historyRefs;
    private final SchedulerService schedulerService;
    private final AutomationService automationService;
    private final HeartbeatService heartbeatService;
    private final TaskPersistenceService persistenceService;
    private final CheckInHistoryService historyService;
    private final ConfigPersistenceService configPersistenceService;
    private final Consumer<String> appendLog;
    private final Runnable onSlotStateChanged;

    private CloudConfig config;
    private boolean suppressUiSave;
    private boolean historyMenusBound;
    private ShopStrip shopStrip;

    public SlotController(
            JFrame owner,
            PanelFactory.SlotPanelRefs slotRefs,
            PanelFactory.CheckInHistoryRefs historyRefs,
            SchedulerService schedulerService,
            AutomationService automationService,
            HeartbeatService heartbeatService,
            TaskPersistenceService persistenceService,
            CheckInHistoryService historyService,
            ConfigPersistenceService configPersistenceService,
            Consumer<String> appendLog,
            Runnable onSlotStateChanged) {
        this.owner = owner;
        this.slotRefs = slotRefs;
        this.historyRefs = historyRefs;
        this.schedulerService = schedulerService;
        this.automationService = automationService;
        this.heartbeatService = heartbeatService;
        this.persistenceService = persistenceService;
        this.historyService = historyService;
        this.configPersistenceService = configPersistenceService;
        this.appendLog = appendLog;
        this.onSlotStateChanged = onSlotStateChanged;
        this.config = new CloudConfig();
    }

    public void bindUi() {
        bindSlotCard(slotRefs.workIn, WorkSlot.Kind.WORK_IN);
        bindSharedSettingsListeners();
        slotRefs.executeNowButton.addActionListener(e -> executeNowShared());
        slotRefs.skipDatesButton.addActionListener(e -> editSkipDates());
        refreshSkipDatesButton();
        if (historyRefs != null && historyRefs.clearButton != null) {
            historyRefs.clearButton.addActionListener(e -> clearCheckInHistory());
        }
        refreshSharedSettingsActions();
        refreshHistoryList();
    }

    public void loadConfigToUi(CloudConfig loaded) {
        suppressUiSave = true;
        try {
            config = loaded;
            applySlotToUi(slotRefs.workIn, config.workIn);
            RecentValuesHelper.applyHistory(slotRefs.urlCombo, config.recentTargetUrls, config.targetUrl);
            RecentValuesHelper.applyHistory(slotRefs.buttonIdCombo, config.recentButtonIds, config.buttonId);
            slotRefs.browserCombo.setSelectedItem(config.browserChoice);
            bindHistoryMenusIfNeeded();
            refreshSharedSettingsActions();
            refreshSkipDatesButton();
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
        schedulerService.addTaskRecord(workIn);

        readConfigFromUi();
        if (!isSharedSettingsReady() && config.workIn.enabled) {
            config.workIn.enabled = false;
            applySlotToUi(slotRefs.workIn, config.workIn);
            appendLog.accept("[警告] 【" + slotLabel(WorkSlot.Kind.WORK_IN) + "】目標網址或 Selector 未設定，已取消「啟用」");
        }
        saveConfig();

        // 程式關閉／重啟（含更新重啟）期間錯過的排程：載入時被標成「已過期取消」，
        // 槽位仍啟用就排到下一個時間，否則整個槽位會從此不再排程。錯過的那次不補打。
        SlotSettings slot = config.workIn;
        CheckInTask expired = schedulerService.getTask(WorkSlot.Kind.WORK_IN.id);
        if (slot.enabled && SlotScheduleHelper.isExpiredOnRestart(expired)) {
            String missed = expired.getActualTriggerTime() != null
                    ? expired.getActualTriggerTime().format(
                            java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "未知時間";
            expired.setStatus(TaskStatus.PENDING);
            expired.setResultMessage("");
            appendLog.accept(String.format(
                    "[排程] 【" + slotLabel(WorkSlot.Kind.WORK_IN) + "】程式重啟時錯過了原定的 %s，不補執行，改排下一次", missed));
        }

        if (scheduleSlot(WorkSlot.Kind.WORK_IN, false)) {
            appendLog.accept("[成功] 已排程每日任務");
        }
        CheckInTask scheduledTask = schedulerService.getTask(WorkSlot.Kind.WORK_IN.id);
        if (scheduledTask != null) {
            if (scheduledTask.getStatus() == TaskStatus.SCHEDULED) {
                appendLog.accept(String.format("[排程] 【%s】下次觸發：%s", slotLabel(WorkSlot.Kind.WORK_IN), scheduledTask.getFormattedActualTime()));
            } else {
                String extra = scheduledTask.getResultMessage() != null && !scheduledTask.getResultMessage().isBlank()
                        ? "（" + scheduledTask.getResultMessage() + "）" : "";
                appendLog.accept(String.format("[資訊] 【%s】目前狀態：%s%s",
                        slotLabel(WorkSlot.Kind.WORK_IN),
                        scheduledTask.getStatus().getDisplayName(), WorkSlot.screenText(extra)));
            }
        }
        refreshSlotCards();
        persistTasks();
    }

    public void setShopStrip(ShopStrip shopStrip) {
        this.shopStrip = shopStrip;
        refreshSlotCards();
    }

    public void refreshSlotCards() {
        SwingUtilities.invokeLater(() -> {
            pruneExpiredSkipDates();
            refreshSkipDatesButton();
            refreshCard(slotRefs.workIn, WorkSlot.Kind.WORK_IN);
            updateShopStrip();
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
     * 網頁後台取消全部任務：停止計時並取消上班排程的「啟用」。
     */
    public void handleRemoteCancelAll() {
        schedulerService.cancelAllTasks("網頁後台遠端取消全部任務");
        disableSlotInUi(WorkSlot.Kind.WORK_IN);
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
        if (refs == null) {
            configPersistenceService.saveConfig(config, appendLog);
            return;
        }
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
        if (kind == WorkSlot.Kind.WORK_IN) {
            return slotRefs.workIn;
        }
        return null;
    }

    private void bindSlotCard(PanelFactory.SlotCardRefs refs, WorkSlot.Kind kind) {
        refs.enabledCheckBox.addActionListener(e -> {
            syncSlotEditorsEnabled(refs);
            onEnableChanged(kind);
        });
        refs.hourCombo.addActionListener(e -> onSlotTimingChanged(kind));
        refs.minuteCombo.addActionListener(e -> onSlotTimingChanged(kind));
        refs.randomOffsetCheckBox.addActionListener(e -> onSlotTimingChanged(kind));
        if (refs.nameField != null) {
            refs.nameField.addActionListener(e -> commitSlotName(kind));
            refs.nameField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    commitSlotName(kind);
                }
            });
        }
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
        UiFonts.showWarning(owner, "請先設定目標網址與 Selector！", "提示");
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
                : "請先設定目標網址與 Selector");
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
                slotLabel(kind), task.getTargetUrl(), task.getButtonId()));
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
        LocalDateTime nextTime = nextSlotTime(slot.hour, slot.minute, LocalDateTime.now());
        resetTaskForSchedule(task, kind, slot, nextTime);
        boolean ok = schedulerService.scheduleTask(
                task,
                t -> SwingUtilities.invokeLater(onSlotStateChanged),
                appendLog,
                this::executeCheckInForTask,
                t -> SwingUtilities.invokeLater(() -> rescheduleSlotAfterRun(t)));
        onSlotStateChanged.run();
        if (ok) {
            appendLog.accept(String.format("[排程] 【%s】已重新啟用並排程", slotLabel(kind)));
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
        boolean scheduled = scheduleSlot(WorkSlot.Kind.WORK_IN, true);
        if (logSummary) {
            String label = slotLabel(WorkSlot.Kind.WORK_IN);
            appendLog.accept(scheduled ? "[排程] " + label + "啟用中" : "[排程] " + label + "未啟用");
        }
        refreshSlotCards();
        persistTasks();
        heartbeatService.sendHeartbeat(appendLog, null);
    }

    private boolean scheduleSlot(WorkSlot.Kind kind, boolean logChanges) {
        return scheduleSlot(kind, logChanges, false, false);
    }

    private boolean scheduleSlot(WorkSlot.Kind kind, boolean logChanges, boolean skipRemainingTodayWindow) {
        return scheduleSlot(kind, logChanges, skipRemainingTodayWindow, false);
    }

    private boolean scheduleSlot(
            WorkSlot.Kind kind, boolean logChanges, boolean skipRemainingTodayWindow, boolean forceRecompute) {
        if (kind != WorkSlot.Kind.WORK_IN) {
            return false;
        }
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task == null) {
            task = SlotScheduleHelper.buildTask(kind, config,
                    nextSlotTime(slot.hour, slot.minute, LocalDateTime.now()));
            schedulerService.addTaskRecord(task);
        }

        if (!slot.enabled) {
            if (task.getStatus() == TaskStatus.SCHEDULED || task.getStatus() == TaskStatus.CHECKING_IN) {
                schedulerService.cancelTask(kind.id, "槽位已停用");
                task.setResultMessage("槽位已停用");
            }
            if (logChanges) {
                appendLog.accept("[停用] 【" + slotLabel(kind) + "】已停用，排程已取消");
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
                ? nextSlotTimeAfterToday(slot.hour, slot.minute)
                : (forceRecompute
                        ? nextSlotTime(slot.hour, slot.minute, LocalDateTime.now())
                        : resolveNextScheduleTime(kind, task, slot));
        resetTaskForSchedule(task, kind, slot, nextTime);

        boolean ok = schedulerService.scheduleTask(
                task,
                t -> SwingUtilities.invokeLater(onSlotStateChanged),
                logChanges ? appendLog : null,
                this::executeCheckInForTask,
                t -> SwingUtilities.invokeLater(() -> rescheduleSlotAfterRun(t)));
        onSlotStateChanged.run();
        return ok;
    }

    private LocalDateTime resolveNextScheduleTime(WorkSlot.Kind kind, CheckInTask task, SlotSettings slot) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime natural = nextSlotTime(slot.hour, slot.minute, now);
        if (task.getStatus() == TaskStatus.SCHEDULED && task.hasComputedSchedule()) {
            LocalDateTime trigger = task.getActualTriggerTime();
            // 觸發日仍是下一個可打卡日就沿用；跳過日期增減使日期對不上時，改算下一個時分。
            if (trigger != null && trigger.isAfter(now)
                    && !SlotScheduleHelper.isBlockedDay(trigger.toLocalDate(), config.weekdaysOnly, activeSkipDates())
                    && !trigger.toLocalDate().isAfter(natural.toLocalDate())) {
                task.setTargetTime(trigger.withSecond(0).withNano(0));
                return task.getTargetTime();
            }
        }
        return natural;
    }

    private LocalDateTime nextSlotTime(int hour, int minute, LocalDateTime after) {
        return SlotScheduleHelper.nextTriggerTime(
                hour, minute, config.weekdaysOnly, after, activeSkipDates());
    }

    private LocalDateTime nextSlotTimeAfterToday(int hour, int minute) {
        return SlotScheduleHelper.nextTriggerTimeAfterTodaysSlot(
                hour, minute, config.weekdaysOnly, LocalDateTime.now(), activeSkipDates());
    }

    private Collection<LocalDate> activeSkipDates() {
        return ConfigPersistenceService.skipDates(config);
    }

    private void resetTaskForSchedule(
            CheckInTask task, WorkSlot.Kind kind, SlotSettings slot, LocalDateTime targetTime) {
        schedulerService.stopTimer(kind.id);
        task.setName(WorkSlot.normalizeName(kind, task.getName()));
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
                task.setName(WorkSlot.normalizeName(kind, task.getName()));
                return task;
            }
        }
        for (CheckInTask task : loaded) {
            if (kind.displayName.equals(task.getName()) || WorkSlot.isLegacyName(kind, task.getName())) {
                task.setId(kind.id);
                task.setName(WorkSlot.normalizeName(kind, task.getName()));
                return task;
            }
        }
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        return SlotScheduleHelper.buildTask(kind, config,
                nextSlotTime(slot.hour, slot.minute, LocalDateTime.now()));
    }

    private void editSkipDates() {
        ConfigPersistenceService.normalizeSkipDates(config, LocalDate.now());
        List<LocalDate> current = ConfigPersistenceService.skipDates(config);
        List<LocalDate> edited = SkipDatesDialog.showDialog(owner, current);
        if (edited == null || edited.equals(current)) {
            refreshSkipDatesButton();
            return;
        }
        config.skipDates = new ArrayList<>();
        for (LocalDate date : edited) {
            config.skipDates.add(date.toString());
        }
        ConfigPersistenceService.normalizeSkipDates(config, LocalDate.now());
        saveConfig();
        refreshSkipDatesButton();

        int updated = 0;
        WorkSlot.Kind kind = WorkSlot.Kind.WORK_IN;
        SlotSettings slot = config.workIn;
        if (slot.enabled) {
            CheckInTask task = schedulerService.getTask(kind.id);
            boolean skip = task != null && (task.getStatus() == TaskStatus.CANCELLED
                    || task.getStatus() == TaskStatus.CHECKING_IN);
            LocalDateTime next = nextSlotTime(slot.hour, slot.minute, LocalDateTime.now());
            boolean already = task != null && task.getStatus() == TaskStatus.SCHEDULED
                    && task.getActualTriggerTime() != null
                    && task.getActualTriggerTime().isAfter(LocalDateTime.now())
                    && task.getActualTriggerTime().toLocalDate().equals(next.toLocalDate());
            if (!skip && !already && scheduleSlot(kind, true, false, true)) {
                updated++;
            }
        }
        if (config.skipDates.isEmpty()) {
            appendLog.accept(String.format("[排程] 已清除跳過日期，重新排程 %d 個槽位", updated));
        } else {
            appendLog.accept(String.format(
                    "[排程] 跳過日期：%s，重新排程 %d 個槽位",
                    String.join("、", config.skipDates), updated));
        }
        refreshSlotCards();
        persistTasks();
        heartbeatService.sendHeartbeat(appendLog, null);
    }

    private void pruneExpiredSkipDates() {
        int before = config.skipDates == null ? 0 : config.skipDates.size();
        ConfigPersistenceService.normalizeSkipDates(config, LocalDate.now());
        if (config.skipDates.size() != before) {
            configPersistenceService.saveConfig(config, null);
            appendLog.accept("[排程] 已移除過期的跳過日期");
        }
    }

    private void refreshSkipDatesButton() {
        if (slotRefs.skipDatesButton == null) {
            return;
        }
        int count = config.skipDates == null ? 0 : config.skipDates.size();
        slotRefs.skipDatesButton.setText(count <= 0 ? "跳過日期" : "跳過日期 (" + count + ")");
    }

    /** 現在就跑一次。只有一個任務時直接確認；多個才讓使用者挑。不改原本的排程時間。 */
    public void promptExecuteNow() {
        readConfigFromUi();
        List<CheckInTask> runnable = new ArrayList<>();
        for (CheckInTask task : schedulerService.getAllTasks()) {
            if (task == null || task.getStatus() == null) {
                continue;
            }
            TaskStatus status = task.getStatus();
            if (status == TaskStatus.CHECKING_IN) {
                continue;
            }
            runnable.add(task);
        }
        if (runnable.isEmpty()) {
            UiFonts.showMessage(owner, "目前沒有可執行的任務。可以先到排程任務啟用一個。", "執行任務",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] labels = new String[runnable.size()];
        for (int i = 0; i < runnable.size(); i++) {
            labels[i] = runnableLabel(runnable.get(i));
        }
        if (runnable.size() == 1) {
            CheckInTask only = runnable.get(0);
            String when = clockLabel(only);
            String message = when.isEmpty()
                    ? "現在就跑一次「" + labels[0] + "」？\n只會馬上執行，原本的排程時間不會改。"
                    : "現在就跑一次「" + labels[0] + "」？\n只會馬上執行，" + when + " 的排程不會改。";
            int confirm = UiFonts.showConfirm(
                    owner, message, "執行任務", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            runChosenNow(only);
            return;
        }
        int choice = UiFonts.showChoice(
                owner,
                "要現在立刻跑哪一個？只會馬上執行一次，原本的排程時間不會改。",
                "執行任務",
                labels);
        if (choice < 0 || choice >= runnable.size()) {
            return;
        }
        runChosenNow(runnable.get(choice));
    }

    private static String runnableLabel(CheckInTask task) {
        String name = WorkSlot.screenText(task.getName());
        if (name == null || name.isBlank()) {
            name = "任務";
        }
        if (task.getStatus() == TaskStatus.CANCELLED) {
            name = name + "（已取消）";
        }
        return name;
    }

    private static String clockLabel(CheckInTask task) {
        LocalDateTime when = task.getActualTriggerTime();
        if (when == null) {
            return "";
        }
        return when.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private void runChosenNow(CheckInTask task) {
        WorkSlot.Kind kind = WorkSlot.Kind.fromId(task.getId());
        if (kind != null) {
            if (!isSharedSettingsReady()) {
                showSharedSettingsRequiredMessage();
                return;
            }
            executeNow(kind);
            return;
        }
        executeCheckInForTask(task, null);
    }

    private void executeNowShared() {
        readConfigFromUi();
        if (!isSharedSettingsReady()) {
            showSharedSettingsRequiredMessage();
            refreshSharedSettingsActions();
            return;
        }
        executeNow(WorkSlot.Kind.WORK_IN);
    }

    private void executeNow(WorkSlot.Kind kind) {
        readConfigFromUi();
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task == null) {
            UiFonts.showWarning(owner, "找不到【" + slotLabel(kind) + "】槽位。", "提示");
            return;
        }
        String url = config.targetUrl;
        String buttonId = config.buttonId;
        String browser = TaskEditDialog.parseBrowserType(config.browserChoice);
        appendLog.accept("[執行] 【立即執行】使用上方共用設定（不變更已鎖定排程，結果記入【" + slotLabel(kind) + "】）");
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
                String msg = String.format("[成功] 執行成功！(觸發: %s, 完成: %s, 耗時: %.1f秒)", triggerTimeStr, finishTimeStr, durationSec);
                task.setStatus(TaskStatus.SUCCESS);
                task.setResultMessage(msg);
                task.rememberLastResult();
                appendLog.accept("[成功] 【" + task.getName() + "】" + msg);
            } else {
                String msg = String.format("[失敗] 執行失敗 (觸發: %s, 耗時: %.1f秒)", triggerTimeStr, durationSec);
                task.setStatus(TaskStatus.FAILED);
                task.setResultMessage(msg);
                task.rememberLastResult();
                appendLog.accept("[失敗] 【" + task.getName() + "】" + msg);
            }
        } catch (Exception ex) {
            double durationSec = (System.currentTimeMillis() - startTimeMs) / 1000.0;
            String msg = String.format("[失敗] 執行失敗：%s (觸發: %s, 耗時: %.1f秒)", sanitizeErrorMessage(ex.getMessage()), triggerTimeStr, durationSec);
            task.setStatus(TaskStatus.FAILED);
            task.setResultMessage(msg);
            task.rememberLastResult();
            appendLog.accept("[失敗] 【" + task.getName() + "】" + msg);
        } finally {
            if (onComplete != null) onComplete.run();
            onSlotTaskFinished(task, fromScheduler);
        }
    }

    private void onSlotTaskFinished(CheckInTask task, boolean fromScheduler) {
        if (historyService != null) {
            historyService.record(task, fromScheduler);
        }
        WorkSlot.Kind kind = WorkSlot.Kind.fromId(task.getId());
        if (kind != null && !fromScheduler) {
            SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
            if (slot.enabled) {
                // 立即執行不消耗今日排程；排程觸發的重排改等排程執行緒 finally 之後
                SwingUtilities.invokeLater(() -> scheduleSlot(kind, false, false));
            }
        }
        onSlotStateChanged.run();
        persistTasks();
        refreshSlotCards();
        refreshHistoryList();
        heartbeatService.sendHeartbeat(appendLog, null);
    }

    public void refreshHistoryList() {
        if (historyRefs == null || historyRefs.listModel == null || historyService == null) {
            return;
        }
        Runnable update = () -> {
            historyRefs.listModel.clear();
            List<CheckInHistoryEntry> recent = historyService.getRecent();
            for (CheckInHistoryEntry entry : recent) {
                historyRefs.listModel.addElement(entry.toDisplayLine());
            }
            boolean empty = recent.isEmpty();
            if (historyRefs.emptyLabel != null) {
                historyRefs.emptyLabel.setVisible(empty);
            }
            if (historyRefs.clearButton != null) {
                historyRefs.clearButton.setEnabled(!empty);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private void clearCheckInHistory() {
        if (historyService == null) {
            return;
        }
        int confirm = UiFonts.showConfirm(
                owner,
                "確定清除本機執行歷史紀錄？\n（不會影響目前排程）",
                "清除執行記錄",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        historyService.clear(appendLog);
        refreshHistoryList();
        appendLog.accept("[資訊] 已清除執行歷史紀錄");
    }

    /**
     * 排程執行結束後再重排。若在 executeAction 內就重排，排程 finally 會把已改成
     * 「等待中」的明天任務誤標成「未回報最終結果」。
     */
    private void rescheduleSlotAfterRun(CheckInTask task) {
        WorkSlot.Kind kind = WorkSlot.Kind.fromId(task.getId());
        if (kind == null) {
            return;
        }
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        if (!slot.enabled) {
            return;
        }
        TaskStatus status = task.getStatus();
        if (status != TaskStatus.SUCCESS && status != TaskStatus.FAILED) {
            return;
        }
        boolean skipRemainingToday = status == TaskStatus.SUCCESS;
        scheduleSlot(kind, skipRemainingToday, skipRemainingToday);
    }

    public void persistTasks() {
        persistenceService.saveTasks(schedulerService.getAllTasks(), null);
    }

    private void updateShopStrip() {
        if (shopStrip == null) {
            return;
        }
        boolean checking = false;
        boolean scheduled = false;
        boolean failed = false;
        boolean success = false;
        for (CheckInTask task : schedulerService.getAllTasks()) {
            if (task == null || task.getStatus() == null) {
                continue;
            }
            switch (task.getStatus()) {
                case CHECKING_IN:
                    checking = true;
                    break;
                case SCHEDULED:
                    scheduled = true;
                    break;
                case FAILED:
                    failed = true;
                    break;
                case SUCCESS:
                    success = true;
                    break;
                default:
                    break;
            }
        }
        if (checking) {
            shopStrip.setMood(ShopStrip.Mood.SHAKING);
        } else if (scheduled) {
            shopStrip.setMood(ShopStrip.Mood.WAITING);
        } else if (failed) {
            shopStrip.setMood(ShopStrip.Mood.FAILED);
        } else if (success) {
            shopStrip.setMood(ShopStrip.Mood.DONE);
        } else {
            shopStrip.setMood(ShopStrip.Mood.IDLE);
        }
    }

    private void refreshCard(PanelFactory.SlotCardRefs refs, WorkSlot.Kind kind) {
        CheckInTask task = schedulerService.getTask(kind.id);
        SlotSettings slot = SlotScheduleHelper.settingsFor(kind, config);
        if (task == null) {
            showSlotName(refs, null, kind);
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
        setWrappedMetricLabel(refs.resultLabel, WorkSlot.screenText(task.getDisplayResultMessage()));
        refreshLockedSettingsLabel(refs, task, slot);
        showSlotName(refs, task, kind);
    }

    private void showSlotName(PanelFactory.SlotCardRefs refs, CheckInTask task, WorkSlot.Kind kind) {
        if (refs.nameField == null || refs.nameField.isFocusOwner()) {
            return;
        }
        String name = WorkSlot.normalizeName(kind, task == null ? null : task.getName());
        if (!name.equals(refs.nameField.getText())) {
            refs.nameField.setText(name);
        }
    }

    private void commitSlotName(WorkSlot.Kind kind) {
        if (suppressUiSave) {
            return;
        }
        PanelFactory.SlotCardRefs refs = refsFor(kind);
        if (refs == null || refs.nameField == null) {
            return;
        }
        String next = WorkSlot.normalizeName(kind, refs.nameField.getText());
        if (!next.equals(refs.nameField.getText())) {
            refs.nameField.setText(next);
        }
        CheckInTask task = schedulerService.getTask(kind.id);
        if (task == null || next.equals(task.getName())) {
            return;
        }
        task.setName(next);
        persistTasks();
        appendLog.accept("[排程] 任務名稱已改為「" + next + "」");
        onSlotStateChanged.run();
        heartbeatService.sendHeartbeat(appendLog, null);
    }

    private String slotLabel(WorkSlot.Kind kind) {
        CheckInTask task = schedulerService.getTask(kind.id);
        return WorkSlot.normalizeName(kind, task == null ? null : task.getName());
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
        if (rawMsg == null) return "執行異常";
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
        if (sanitized.startsWith("執行失敗：") || sanitized.startsWith("打卡失敗：")) {
            sanitized = sanitized.substring(5).trim();
        }
        if (sanitized.startsWith("執行過程中發生錯誤：")) {
            sanitized = sanitized.substring(10).trim();
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
