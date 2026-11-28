package com.example;

import com.example.service.ClientUpdateCoordinator;
import com.example.service.ClientUpdateInstaller;
import com.example.service.HeartbeatService.UpdateNotice;
import com.example.service.UpdateIdlePolicy;
import com.example.service.TransferCancel;
import com.example.service.TransferIo;
import com.example.ui.NightTheme;
import com.example.ui.PanelFactory;
import com.example.ui.TransferProgressDialog;
import com.example.ui.UiFonts;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 設定頁程式更新：檢查、提示、下載與套用。
 */
final class AppClientUpdateUi {

  private static final DateTimeFormatter UPDATE_CHECK_TIME =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final int UPDATE_NOTES_SHOWN = 4;

  private final App app;

  AppClientUpdateUi(App app) {
    this.app = app;
  }

  ClientUpdateCoordinator.Actions actions() {
    return new ClientUpdateCoordinator.Actions() {
      @Override
      public String localDigest() {
        return AppVersion.SOURCE_DIGEST;
      }

      @Override
      public String localVersion() {
        return AppVersion.VERSION;
      }

      @Override
      public String localLabel() {
        return AppVersion.buildLabel();
      }

      @Override
      public boolean idle() {
        return app.isIdleForUpdate();
      }

      @Override
      public void log(String line) {
        app.appendLog(line);
      }

      @Override
      public void phase(String title) {
        showUpdatePhase(title);
      }

      @Override
      public void warn(String message) {
        showUpdateWarning(message);
      }

      @Override
      public Path download(UpdateNotice notice) throws Exception {
        return downloadClientUpdateWithProgress(notice);
      }

      @Override
      public boolean canInstall() {
        return ClientUpdateInstaller.installTarget() != null;
      }

      @Override
      public void install(Path jar) throws Exception {
        ClientUpdateInstaller.relaunch(jar, ClientUpdateInstaller.installTarget());
      }

      @Override
      public void restart() {
        app.restartAfterUpdate();
      }

      @Override
      public void finished(boolean restarted) {
        SwingUtilities.invokeLater(() -> finishUpdateApply(restarted));
      }
    };
  }

  void onServerUpdateNotice(UpdateNotice notice) {
    boolean manual = app.pendingManualCheck;
    app.pendingManualCheck = false;
    if (app.updateApplying) {
      refreshUpdateButtons();
      return;
    }
    if (!manual && !isCheckForUpdatesEnabled()) {
      refreshUpdateButtons();
      return;
    }
    applyUpdateNotice(notice, manual);
  }

  void applyUpdateNotice(UpdateNotice notice, boolean manual) {
    rememberUpdateCheck(LocalDateTime.now());
    UpdateNotice offer = app.updateCoordinator.offer(notice);
    if (offer == null) {
      app.offeredUpdate = null;
      showUpToDate(manual);
    } else {
      app.offeredUpdate = offer;
      showOfferedUpdate();
      announceOffer(offer);
    }
    refreshUpdateButtons();
  }

  void checkForUpdateNow() {
    if (app.updateApplying || app.pendingManualCheck) {
      return;
    }
    if (!app.isCloudEnabled() || !app.heartbeatService.active()) {
      showUpdateText("無法檢查", "請先啟用雲端連線，並確認 Server 網址。", NightTheme.PINK);
      refreshUpdateButtons();
      return;
    }
    app.pendingManualCheck = true;
    showUpdateText("正在檢查更新…", "正在詢問伺服器", NightTheme.MUTED);
    refreshUpdateButtons();
    app.heartbeatService.sendHeartbeat(app::appendLog, ok ->
            SwingUtilities.invokeLater(() -> finishManualUpdateCheck(ok)));
  }

  void finishManualUpdateCheck(boolean ok) {
    if (!app.pendingManualCheck) {
      refreshUpdateButtons();
      return;
    }
    app.pendingManualCheck = false;
    if (app.updateApplying) {
      refreshUpdateButtons();
      return;
    }
    if (!ok) {
      if (app.offeredUpdate != null) {
        showOfferedUpdate();
        if (app.serverRefs.updateStatusTitle != null) {
          app.serverRefs.updateStatusTitle.setText("檢查失敗");
          app.serverRefs.updateStatusTitle.setForeground(NightTheme.PINK);
        }
      } else {
        showUpdateText("檢查失敗", "連不上伺服器。請確認雲端網址與 Token。", NightTheme.PINK);
      }
      refreshUpdateButtons();
      return;
    }
    applyUpdateNotice(null, true);
  }

  void applyOfferedUpdate() {
    if (app.offeredUpdate == null || app.updateApplying || app.pendingManualCheck) {
      return;
    }
    app.updateApplying = true;
    refreshUpdateButtons();
    if (!app.updateCoordinator.apply(app.offeredUpdate)) {
      app.updateApplying = false;
      showOfferedUpdate();
      refreshUpdateButtons();
    }
  }

  void finishUpdateApply(boolean restarted) {
    app.updateApplying = false;
    if (!restarted && app.offeredUpdate != null) {
      showOfferedUpdate();
    }
    refreshUpdateButtons();
  }

  void showUpdateUnchecked() {
    showUpdateText("尚未檢查更新",
            "目前 " + AppVersion.displayLabel() + "\n有新版時會顯示在這裡，由你決定要不要升級。",
            NightTheme.MUTED);
    refreshUpdateButtons();
  }

  void showUpToDate(boolean announce) {
    showUpdateText("已是最新版本",
            "目前 " + AppVersion.displayLabel(),
            new Color(34, 197, 94));
    if (announce) {
      app.appendLog("[更新] 已是最新版本，目前 " + AppVersion.displayLabel());
    }
  }

  void showOfferedUpdate() {
    UpdateNotice notice = app.offeredUpdate;
    if (notice == null) {
      return;
    }
    StringBuilder detail = new StringBuilder();
    detail.append("新版 ").append(ClientUpdateCoordinator.describe(notice));
    detail.append("\n大小 ").append(formatUpdateSize(notice.size));
    detail.append("\n目前 ").append(AppVersion.displayLabel());
    String notes = ReleaseNotes.dialogSection(previewChanges(notice), previewChangesHidden(notice));
    if (!notes.isEmpty()) {
      detail.append("\n\n").append(notes);
    }
    if (ClientUpdateInstaller.runningJar() == null) {
      detail.append("\n\n目前不是以 jar 啟動，更新後會改執行下載的程式。");
    }
    showUpdateText("有可用的更新", detail.toString(), NightTheme.PINK);
  }

  void rememberUpdateCheck(LocalDateTime at) {
    app.lastUpdateCheckAt = at;
    refreshLastCheckLabel();
    String minute = at.format(UPDATE_CHECK_TIME);
    if (minute.equals(app.lastUpdateCheckMinute)) {
      return;
    }
    app.lastUpdateCheckMinute = minute;
    app.saveCloudConfig();
  }

  void refreshLastCheckLabel() {
    if (app.serverRefs.updateCheckedAtLabel == null) {
      return;
    }
    if (app.lastUpdateCheckAt == null) {
      app.serverRefs.updateCheckedAtLabel.setText("尚未檢查過");
      return;
    }
    app.serverRefs.updateCheckedAtLabel.setText(
            "上次檢查 " + app.lastUpdateCheckAt.format(UPDATE_CHECK_TIME));
  }

  static String formatCheckMinute(LocalDateTime at) {
    return at.format(UPDATE_CHECK_TIME);
  }

  static LocalDateTime parseUpdateCheckAt(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDateTime.parse(raw.trim());
    } catch (java.time.format.DateTimeParseException e) {
      return null;
    }
  }

  static List<String> previewChanges(UpdateNotice notice) {
    if (notice.changes.size() <= UPDATE_NOTES_SHOWN) {
      return notice.changes;
    }
    return notice.changes.subList(0, UPDATE_NOTES_SHOWN);
  }

  static int previewChangesHidden(UpdateNotice notice) {
    return notice.changesMore + Math.max(0, notice.changes.size() - UPDATE_NOTES_SHOWN);
  }

  void announceOffer(UpdateNotice notice) {
    if (notice.version.equals(app.announcedUpdateVersion)) {
      return;
    }
    app.announcedUpdateVersion = notice.version;
    app.appendLog("[更新] 發現新版 " + ClientUpdateCoordinator.describe(notice)
            + "，目前 " + AppVersion.displayLabel() + "。要升級請到設定按「現在更新」");
    if (!notice.changes.isEmpty()) {
      app.appendLog("[更新] 這次修改：" + String.join("；", notice.changes));
    }
  }

  void showUpdatePhase(String title) {
    Runnable show = () -> {
      if (app.serverRefs.updateStatusTitle == null || title == null) {
        return;
      }
      app.serverRefs.updateStatusTitle.setText(title);
      app.serverRefs.updateStatusTitle.setForeground(NightTheme.PINK);
    };
    if (SwingUtilities.isEventDispatchThread()) {
      show.run();
    } else {
      SwingUtilities.invokeLater(show);
    }
  }

  void showUpdateText(String title, String detail, Color titleColor) {
    if (app.serverRefs.updateStatusTitle != null) {
      app.serverRefs.updateStatusTitle.setText(title == null ? "" : title);
      app.serverRefs.updateStatusTitle.setForeground(titleColor == null ? NightTheme.MUTED : titleColor);
    }
    if (app.serverRefs.updateStatusDetail == null) {
      return;
    }
    String text = detail == null ? "" : detail;
    JTextArea detailArea = app.serverRefs.updateStatusDetail;
    if (text.equals(detailArea.getText())) {
      return;
    }
    detailArea.setText(text);
    fitUpdateDetail(detailArea);
    detailArea.revalidate();
  }

  static void fitUpdateDetail(JTextArea area) {
    PanelFactory.fitRelayHelp(area);
  }

  void refreshUpdateButtons() {
    boolean busy = app.pendingManualCheck || app.updateApplying;
    if (app.serverRefs.checkUpdateButton != null) {
      app.serverRefs.checkUpdateButton.setEnabled(!busy);
    }
    if (app.serverRefs.applyUpdateButton != null) {
      boolean available = app.offeredUpdate != null;
      boolean wasVisible = app.serverRefs.applyUpdateButton.isVisible();
      app.serverRefs.applyUpdateButton.setVisible(available);
      app.serverRefs.applyUpdateButton.setEnabled(available && !busy);
      if (wasVisible != available && app.serverRefs.applyUpdateButton.getParent() != null) {
        app.serverRefs.applyUpdateButton.getParent().revalidate();
      }
    }
  }

  static String formatUpdateSize(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    }
    if (bytes < 1024 * 1024) {
      return String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024.0);
    }
    return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
  }

  Path downloadClientUpdateWithProgress(UpdateNotice notice) throws Exception {
    String label = notice.version == null || notice.version.isBlank() ? "新版本" : "v" + notice.version;
    TransferCancel cancel = new TransferCancel();
    TransferProgressDialog[] holder = new TransferProgressDialog[1];
    Runnable open = () -> {
      holder[0] = TransferProgressDialog.open(app, "程式更新");
      holder[0].setOnCancel(cancel::cancel);
      holder[0].setStatus("正在下載桌面端 " + label + "…");
    };
    if (SwingUtilities.isEventDispatchThread()) {
      open.run();
    } else {
      SwingUtilities.invokeAndWait(open);
    }
    TransferProgressDialog progress = holder[0];
    try {
      return app.heartbeatService.downloadClientUpdate(
              notice.path, notice.size, TransferIo.throttle(progress::setProgress), cancel);
    } finally {
      progress.close();
    }
  }

  boolean isCheckForUpdatesEnabled() {
    return app.serverRefs.checkForUpdatesCheckBox == null
            || app.serverRefs.checkForUpdatesCheckBox.isSelected();
  }

  void showUpdateWarning(String message) {
    Runnable show = () -> UiFonts.showWarning(app, message, "程式更新");
    if (SwingUtilities.isEventDispatchThread()) {
      show.run();
      return;
    }
    try {
      SwingUtilities.invokeAndWait(show);
    } catch (Exception ex) {
      app.appendLog("[更新] " + message);
    }
  }
}