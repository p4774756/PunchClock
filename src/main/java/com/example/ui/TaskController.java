package com.example.ui;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.service.AutomationService;
import com.example.service.HeartbeatService;
import com.example.service.SchedulerService;
import com.example.ui.PanelFactory.TaskFormRefs;
import com.example.ui.PanelFactory.TaskTableRefs;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 任務表單與列表操作：快捷模板、新增/批量、選取操作、表格刷新與打卡執行。
 */
public class TaskController {

    private final JFrame owner;
    private final TaskFormRefs formRefs;
    private final TaskTableRefs tableRefs;
    private final SchedulerService schedulerService;
    private final AutomationService automationService;
    private final HeartbeatService heartbeatService;
    private final Consumer<String> appendLog;
    private final Runnable onTaskStateChanged;

    public TaskController(
            JFrame owner,
            TaskFormRefs formRefs,
            TaskTableRefs tableRefs,
            SchedulerService schedulerService,
            AutomationService automationService,
            HeartbeatService heartbeatService,
            Consumer<String> appendLog,
            Runnable onTaskStateChanged) {
        this.owner = owner;
        this.formRefs = formRefs;
        this.tableRefs = tableRefs;
        this.schedulerService = schedulerService;
        this.automationService = automationService;
        this.heartbeatService = heartbeatService;
        this.appendLog = appendLog;
        this.onTaskStateChanged = onTaskStateChanged;
    }

    // ==================== 表單操作 ====================

    public void setWorkdaysSelected(boolean select) {
        formRefs.monCheckBox.setSelected(select);
        formRefs.tueCheckBox.setSelected(select);
        formRefs.wedCheckBox.setSelected(select);
        formRefs.thuCheckBox.setSelected(select);
        formRefs.friCheckBox.setSelected(select);
        formRefs.satCheckBox.setSelected(false);
        formRefs.sunCheckBox.setSelected(false);
    }

    /** 清除全部星期（含週末） */
    public void clearAllWeekdaySelection() {
        formRefs.monCheckBox.setSelected(false);
        formRefs.tueCheckBox.setSelected(false);
        formRefs.wedCheckBox.setSelected(false);
        formRefs.thuCheckBox.setSelected(false);
        formRefs.friCheckBox.setSelected(false);
        formRefs.satCheckBox.setSelected(false);
        formRefs.sunCheckBox.setSelected(false);
    }

    public void applyPreset(String taskName, int targetHour, int targetMin, boolean useRandom) {
        formRefs.taskNameTextField.setText(taskName);
        formRefs.datePicker.setDateToToday();
        formRefs.hourCombo.setSelectedIndex(targetHour);
        formRefs.minuteCombo.setSelectedIndex(targetMin);
        formRefs.randomOffsetCheckBox.setSelected(useRandom);
        appendLog.accept(String.format("💡 已載入預設模板【%s】(時間 %02d:%02d, 隨機浮動: %s)",
                taskName, targetHour, targetMin, useRandom ? "開啟" : "關閉"));
    }

    public void applyTestPreset(int minutesFromNow) {
        LocalDateTime testTime = LocalDateTime.now().plusMinutes(minutesFromNow);
        formRefs.taskNameTextField.setText("⚡ 測試打卡 (+" + minutesFromNow + "分)");
        formRefs.datePicker.setDate(testTime.toLocalDate());
        formRefs.hourCombo.setSelectedIndex(testTime.getHour());
        formRefs.minuteCombo.setSelectedIndex(testTime.getMinute());
        formRefs.randomOffsetCheckBox.setSelected(false);
        appendLog.accept(String.format("⚡ 已載入測試快捷：當前時間 +%d 分鐘 (%02d:%02d)，自動關閉隨機時間以利精準測試！",
                minutesFromNow, testTime.getHour(), testTime.getMinute()));
    }

    // ==================== 任務管理 ====================

    public void addNewTaskFromForm() {
        String name = formRefs.taskNameTextField.getText().trim();
        if (name.isEmpty()) name = "打卡任務";

        String targetUrl = formRefs.urlTextField.getText().trim();
        if (targetUrl.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "請輸入目標打卡網址！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String buttonId = formRefs.buttonIdTextField.getText().trim();
        if (buttonId.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "請輸入打卡按鈕 Selector！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate selectedDate = formRefs.datePicker.getDate();
        if (selectedDate == null) {
            JOptionPane.showMessageDialog(owner, "請選擇有效的日期！", "提示", JOptionPane.WARNING_MESSAGE);
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
                t -> SwingUtilities.invokeLater(onTaskStateChanged),
                appendLog, this::executeCheckInForTask);

        if (!scheduled) {
            JOptionPane.showMessageDialog(owner, "無法排定任務，可能是選擇的時間與隨機偏移已屬於過去！", "時間錯誤", JOptionPane.ERROR_MESSAGE);
        } else {
            onTaskStateChanged.run();
            heartbeatService.sendHeartbeat(appendLog, null);
        }
    }

    public void addBatchTasksFromForm() {
        String name = formRefs.taskNameTextField.getText().trim();
        if (name.isEmpty()) name = "打卡任務";

        String targetUrl = formRefs.urlTextField.getText().trim();
        if (targetUrl.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "請輸入目標打卡網址！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String buttonId = formRefs.buttonIdTextField.getText().trim();
        if (buttonId.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "請輸入打卡按鈕 Selector！", "提示", JOptionPane.WARNING_MESSAGE);
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
            JOptionPane.showMessageDialog(owner, "請先勾選至少一個星期（例如 週一~週五）！", "提示", JOptionPane.WARNING_MESSAGE);
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
                    t -> SwingUtilities.invokeLater(onTaskStateChanged),
                    appendLog, this::executeCheckInForTask);
            if (scheduled) addedCount++;
        }

        if (addedCount > 0) {
            onTaskStateChanged.run();
            heartbeatService.sendHeartbeat(appendLog, null);
            appendLog.accept("🗓️ 【批量排定】成功一次排定 " + addedCount + " 個工作日打卡任務 (含隨機時間浮動)！");
            JOptionPane.showMessageDialog(owner, "成功一次排定 " + addedCount + " 個星期的打卡任務！", "批量成功", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(owner, "無法排定任務，可能是選擇的時間已過！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ==================== 打卡執行 ====================

    public void executeCheckInForTask(CheckInTask task, Runnable onComplete) {
        LocalDateTime triggerTime = LocalDateTime.now();
        String triggerTimeStr = triggerTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        long startTimeMs = System.currentTimeMillis();

        try {
            boolean ok = automationService.executeCheckIn(task.getTargetUrl(), task.getButtonId(), task.getBrowserType(), appendLog);
            long durationMs = System.currentTimeMillis() - startTimeMs;
            double durationSec = durationMs / 1000.0;
            String finishTimeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

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
            long durationMs = System.currentTimeMillis() - startTimeMs;
            double durationSec = durationMs / 1000.0;
            String cleanMsg = sanitizeErrorMessage(ex.getMessage());
            String msg = String.format("❌ 打卡失敗：%s (觸發: %s, 耗時: %.1f秒)", cleanMsg, triggerTimeStr, durationSec);
            task.setStatus(TaskStatus.FAILED);
            task.setResultMessage(msg);
            appendLog.accept("❌ 【" + task.getName() + "】" + msg);
        } finally {
            onTaskStateChanged.run();
            heartbeatService.sendHeartbeat(appendLog, null);
            if (onComplete != null) onComplete.run();
        }
    }

    // ==================== 任務列表操作 ====================

    public List<String> getSelectedTaskIds() {
        List<String> ids = new ArrayList<>();
        JTable table = tableRefs.taskTable;
        int[] viewRows = table.getSelectedRows();
        for (int viewRow : viewRows) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < tableRefs.tableModel.getRowCount()) {
                Object id = tableRefs.tableModel.getValueAt(modelRow, 0);
                if (id != null) {
                    ids.add(id.toString());
                }
            }
        }
        return ids;
    }

    public void cancelSelectedTasks() {
        List<String> taskIds = getSelectedTaskIds();
        if (taskIds.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "請先選取列表中要取消的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int cancelled = 0;
        for (String taskId : taskIds) {
            if (schedulerService.cancelTask(taskId)) {
                cancelled++;
                appendLog.accept("🛑 已取消任務 ID [" + taskId + "]");
            } else {
                appendLog.accept("⚠️ 任務 ID [" + taskId + "] 不存在或已結束");
            }
        }
        onTaskStateChanged.run();
        heartbeatService.sendHeartbeat(null, null);
        appendLog.accept(String.format("🛑 已處理取消選取任務：成功 %d / 共 %d 筆", cancelled, taskIds.size()));
    }

    public void deleteSelectedTasks() {
        List<String> taskIds = getSelectedTaskIds();
        if (taskIds.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "請先選取列表中要刪除的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(owner,
                "確定要刪除選取的 " + taskIds.size() + " 筆任務紀錄嗎？", "刪除確認",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        for (String taskId : taskIds) {
            schedulerService.removeTask(taskId);
            appendLog.accept("🗑️ 已移除任務紀錄 ID [" + taskId + "]");
        }
        onTaskStateChanged.run();
        heartbeatService.sendHeartbeat(null, null);
        appendLog.accept("🗑️ 已刪除選取任務共 " + taskIds.size() + " 筆");
    }

    public void executeSelectedTasksNow() {
        List<String> taskIds = getSelectedTaskIds();
        if (taskIds.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "請先選取列表中要立即執行的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int started = 0;
        for (String taskId : taskIds) {
            CheckInTask task = schedulerService.getTask(taskId);
            if (task != null) {
                appendLog.accept("⚡ 【立即執行】觸發任務【" + task.getName() + "】進行打卡...");
                new Thread(() -> executeCheckInForTask(task, null)).start();
                started++;
            }
        }
        if (started == 0) {
            JOptionPane.showMessageDialog(owner, "選取的任務皆無法執行！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void editSelectedTask() {
        List<String> taskIds = getSelectedTaskIds();
        if (taskIds.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "請先選取列表中要編輯的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (taskIds.size() > 1) {
            JOptionPane.showMessageDialog(owner, "編輯任務一次只能選取 1 筆！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskId = taskIds.get(0);
        CheckInTask task = schedulerService.getTask(taskId);
        if (task == null) {
            JOptionPane.showMessageDialog(owner, "找不到該任務！", "錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (task.getStatus() == TaskStatus.CHECKING_IN) {
            JOptionPane.showMessageDialog(owner, "任務正在執行中，請稍後再編輯。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        TaskEditDialog.Result result = new TaskEditDialog(owner, task, false).showDialog();
        if (result == null) return;

        // 先取消既有排程（若有），再套用新設定
        schedulerService.cancelTask(task.getId());
        task.setName(result.name);
        task.setTargetUrl(result.targetUrl);
        task.setButtonId(result.buttonId);
        task.setTargetTime(result.targetTime);
        task.setUseRandomOffset(result.useRandomOffset);
        task.setBrowserType(result.browserType);
        task.setResultMessage("");
        task.setActualTriggerTime(null);
        task.setRandomOffsetSeconds(0);
        task.setStatus(TaskStatus.PENDING);

        boolean scheduled = schedulerService.scheduleTask(task,
                t -> SwingUtilities.invokeLater(onTaskStateChanged),
                appendLog, this::executeCheckInForTask);
        onTaskStateChanged.run();
        heartbeatService.sendHeartbeat(appendLog, null);

        if (scheduled) {
            appendLog.accept(String.format("✏️ 【編輯完成】任務【%s】已更新，狀態：等待中", result.name));
        } else {
            appendLog.accept(String.format("✏️ 【編輯完成】任務【%s】已更新設定（時間已過，未排入等待）", result.name));
            JOptionPane.showMessageDialog(owner,
                    "設定已儲存，但排程時間已過，無法進入「等待中」。\n若要再排程，請編輯成未來時間，再按「重新排定」。",
                    "已儲存", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * 重新排定：同一筆任務，若觸發時間仍在未來，再次進入「等待中」。
     */
    public void reuseSelectedTask() {
        List<String> taskIds = getSelectedTaskIds();
        if (taskIds.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "請先選取列表中要重新排定的任務！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (taskIds.size() > 1) {
            JOptionPane.showMessageDialog(owner, "重新排定一次只能選取 1 筆！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskId = taskIds.get(0);
        CheckInTask task = schedulerService.getTask(taskId);
        if (task == null) {
            JOptionPane.showMessageDialog(owner, "找不到該任務！", "錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }

        TaskStatus status = task.getStatus();
        if (status == TaskStatus.SCHEDULED) {
            JOptionPane.showMessageDialog(owner, "該任務已在等待中，無需重新排定。\n若要改設定，請使用【編輯】。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (status == TaskStatus.CHECKING_IN) {
            JOptionPane.showMessageDialog(owner, "任務正在執行中，無法重新排定。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDateTime triggerTime = task.hasComputedSchedule()
                ? task.getActualTriggerTime()
                : task.getTargetTime();
        if (triggerTime == null) {
            JOptionPane.showMessageDialog(owner, "任務沒有有效的排程時間，請先用【編輯】設定時間。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!triggerTime.isAfter(LocalDateTime.now())) {
            JOptionPane.showMessageDialog(owner,
                    "觸發時間已過，無法重新排定成等待中。\n請先用【編輯】改成未來時間。",
                    "時間已過", JOptionPane.WARNING_MESSAGE);
            return;
        }

        task.setResultMessage("");
        // 保留既有 actualTriggerTime / offset，讓排程沿用同一觸發點
        boolean scheduled = schedulerService.scheduleTask(task,
                t -> SwingUtilities.invokeLater(onTaskStateChanged),
                appendLog, this::executeCheckInForTask);
        if (scheduled) {
            onTaskStateChanged.run();
            heartbeatService.sendHeartbeat(appendLog, null);
            appendLog.accept(String.format("🔄 【重新排定】任務【%s】已再次進入等待中（觸發：%s）",
                    task.getName(), task.getFormattedActualTime()));
        } else {
            JOptionPane.showMessageDialog(owner, "無法重新排定，請確認時間是否仍在未來。", "時間錯誤", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== Table 刷新 ====================

    public void refreshTaskTable() {
        SwingUtilities.invokeLater(() -> {
            DefaultTableModel model = tableRefs.tableModel;
            JTable table = tableRefs.taskTable;

            java.util.Set<String> selectedTaskIds = new java.util.HashSet<>(getSelectedTaskIds());

            model.setRowCount(0);
            List<CheckInTask> tasks = schedulerService.getAllTasks();
            List<Integer> restoreRows = new ArrayList<>();

            for (int i = 0; i < tasks.size(); i++) {
                CheckInTask t = tasks.get(i);
                if (selectedTaskIds.contains(t.getId())) {
                    restoreRows.add(i);
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

            table.clearSelection();
            for (int modelRow : restoreRows) {
                if (modelRow >= 0 && modelRow < model.getRowCount()) {
                    int viewRow = table.convertRowIndexToView(modelRow);
                    if (viewRow >= 0) {
                        table.addRowSelectionInterval(viewRow, viewRow);
                    }
                }
            }
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
