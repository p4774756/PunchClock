package com.example.service;

import com.example.BuildInfo;
import com.example.ClientUpdatePolicy;
import com.example.service.HeartbeatService.UpdateNotice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 桌面端更新。心跳或「檢查更新」只負責發現新版，交給畫面顯示，不跳詢問、也不自己安裝。
 * 使用者按「現在更新」才下載、校驗、換檔重開。
 * 下載可以立刻開始；真正重啟前若正在打卡、或十分鐘內有排程，會等到閒置（有上限），
 * 免得把執行中的打卡砍掉。
 */
public final class ClientUpdateCoordinator {

    /** 等待閒置的輪詢間隔與上限。 */
    static final long IDLE_POLL_MS = 15_000L;
    static final long IDLE_MAX_WAIT_MS = 30L * 60L * 1000L;

    public interface Actions {
        String localDigest();

        /** 目前執行中的版本標示，例如 {@code v2.0.2 · a1b2c3d}。 */
        String localLabel();

        /** 閒置：沒有打卡執行中、近期沒有排程、沒有傳檔進行中。只在重啟前用。 */
        boolean idle();

        void log(String line);

        /** 畫面上的狀態標題，例如「正在下載更新…」。 */
        void phase(String title);

        void warn(String message);

        Path download(UpdateNotice notice) throws Exception;

        boolean canInstall();

        void install(Path jar) throws Exception;

        void restart();

        /** 這次升級結束。{@code restarted == false} 時畫面恢復，讓使用者可以再試。 */
        void finished(boolean restarted);
    }

    /** 測試可替換，避免真的睡。 */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Actions actions;
    private final Executor executor;
    private final Sleeper sleeper;
    private final long idlePollMs;
    private final long idleMaxWaitMs;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public ClientUpdateCoordinator(Actions actions) {
        this(actions, command -> {
            Thread thread = new Thread(command, "client-update");
            thread.setDaemon(false);
            thread.start();
        });
    }

    ClientUpdateCoordinator(Actions actions, Executor executor) {
        this(actions, executor, Thread::sleep, IDLE_POLL_MS, IDLE_MAX_WAIT_MS);
    }

    ClientUpdateCoordinator(Actions actions, Executor executor, Sleeper sleeper, long idlePollMs, long idleMaxWaitMs) {
        this.actions = actions;
        this.executor = executor;
        this.sleeper = sleeper;
        this.idlePollMs = idlePollMs;
        this.idleMaxWaitMs = idleMaxWaitMs;
    }

    /**
     * 這則通知是不是比本機新。相同、空的、或格式在更早已經被擋下的，回 null。
     * 不下載、不詢問。
     */
    public UpdateNotice offer(UpdateNotice notice) {
        if (notice == null) {
            return null;
        }
        String digest = notice.sourceDigest;
        if (digest == null || digest.isEmpty() || digest.equals(safe(actions.localDigest()))) {
            return null;
        }
        return notice;
    }

    /** 例如 {@code v3.0.0 · bbbbbbb（建置 2026-11-07 15:30）}。 */
    public static String describe(UpdateNotice notice) {
        if (notice == null) {
            return "";
        }
        String remote = BuildInfo.buildLabel(notice.version, notice.sourceDigest);
        String time = BuildInfo.labelFromIso(notice.releaseTime, ZoneId.systemDefault());
        return time.isEmpty() ? remote : remote + "（建置 " + time + "）";
    }

    /** 使用者按了「現在更新」。已經在下載時回 false。 */
    public boolean apply(UpdateNotice notice) {
        if (offer(notice) == null) {
            return false;
        }
        if (!busy.compareAndSet(false, true)) {
            return false;
        }
        try {
            actions.log("[更新] 開始下載 " + describe(notice) + "（設定與排程會保留）");
        } catch (RuntimeException ex) {
            busy.set(false);
            throw ex;
        }
        executor.execute(() -> install(notice));
        return true;
    }

    private void install(UpdateNotice notice) {
        boolean relaunched = false;
        try {
            actions.phase("正在下載更新…");
            actions.log("[更新] 正在下載…");
            Path jar = actions.download(notice);
            if (jar == null || !Files.isRegularFile(jar)
                    || Files.size(jar) != notice.size
                    || !ClientUpdatePolicy.sha256Matches(jar, notice.sha256)) {
                actions.warn("下載的檔案校驗失敗，已取消更新。");
                return;
            }
            if (!actions.canInstall()) {
                actions.warn("目前不是以 jar 啟動，無法就地更新。請改為執行 punchclock-client-standalone.jar。");
                return;
            }
            // 下載期間可能剛好到了打卡時間：等閒置才重啟，免得砍掉執行中的打卡、或讓排程落在重啟空窗。
            if (!awaitIdle()) {
                actions.log("[更新] 等候閒置逾時，這次不更新，下次再按「現在更新」");
                actions.warn("目前有打卡進行中或排程即將開始，這次先不重新啟動。稍後再按「現在更新」。");
                Files.deleteIfExists(jar);
                return;
            }
            actions.install(jar);
            relaunched = true;
            actions.log("[更新] 下載完成，即將重新啟動");
            actions.restart();
        } catch (TransferIo.CancelledException ex) {
            actions.log("[更新] 已取消下載");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            actions.warn("更新失敗：" + message);
        } finally {
            if (!relaunched) {
                busy.set(false);
                actions.finished(false);
            }
        }
    }

    /** @return true 表示已閒置可重啟；等超過上限回 false */
    private boolean awaitIdle() throws InterruptedException {
        long waited = 0L;
        boolean announced = false;
        while (!actions.idle()) {
            if (waited >= idleMaxWaitMs) {
                return false;
            }
            if (!announced) {
                actions.phase("等打卡結束後重新啟動");
                actions.log("[更新] 新版已下載，但目前有任務執行中或排程將至，等閒置後再重新啟動");
                announced = true;
            }
            sleeper.sleep(idlePollMs);
            waited += idlePollMs;
        }
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
