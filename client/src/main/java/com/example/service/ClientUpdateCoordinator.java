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
 * 收到心跳的更新通知後處理桌面端更新。
 * <ul>
 *   <li>自動模式（預設）：閒置時直接背景下載、校驗、換檔重開，不打擾使用者。</li>
 *   <li>手動模式：先詢問，同意才下載；同一版按過「否」不再問。</li>
 * </ul>
 * 兩種模式都只在「閒置」時動作：打卡執行中、或最近一次排程快到了（見 {@link UpdateIdlePolicy}）就等下一次心跳。
 * 下載完成、真正重啟前會再確認一次；若這時剛好不閒置，就等到閒置才重啟（有上限，逾時放棄這次、下次心跳再來）。
 */
public final class ClientUpdateCoordinator {

    /** 等待閒置的輪詢間隔與上限。 */
    static final long IDLE_POLL_MS = 15_000L;
    static final long IDLE_MAX_WAIT_MS = 30L * 60L * 1000L;

    public interface Actions {
        String localDigest();

        /** 目前執行中的版本標示，例如 {@code v2.0.2 · a1b2c3d}。 */
        String localLabel();

        String ignoredDigest();

        /** 閒置：沒有打卡執行中、近期沒有排程、沒有傳檔進行中。 */
        boolean idle();

        /** 是否自動更新（不詢問）。 */
        boolean autoUpdate();

        void rememberIgnored(String digest);

        boolean confirm(String message);

        void log(String line);

        void warn(String message);

        Path download(UpdateNotice notice) throws Exception;

        boolean canInstall();

        void install(Path jar) throws Exception;

        void restart();
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

    public void handle(UpdateNotice notice) {
        if (notice == null) {
            return;
        }
        String digest = notice.sourceDigest;
        if (digest.isEmpty() || digest.equals(safe(actions.localDigest()))) {
            return;
        }
        boolean auto = actions.autoUpdate();
        // 手動模式才有「這一版不再問」；自動模式不詢問，所以不套用。
        if (!auto && digest.equals(safe(actions.ignoredDigest()))) {
            return;
        }
        if (!actions.idle()) {
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        String remote = BuildInfo.buildLabel(notice.version, digest);
        String time = BuildInfo.labelFromIso(notice.releaseTime, ZoneId.systemDefault());
        String remoteWithTime = time.isEmpty() ? remote : remote + "（建置 " + time + "）";
        try {
            actions.log("[更新] 發現新版桌面端 " + remoteWithTime + "，目前 " + actions.localLabel());
            if (!auto && !askUser(digest, remoteWithTime)) {
                busy.set(false);
                return;
            }
        } catch (RuntimeException ex) {
            busy.set(false);
            throw ex;
        }
        if (!actions.idle()) {
            actions.log("[更新] 打卡執行中或排程將至，這次先不更新");
            busy.set(false);
            return;
        }
        if (auto) {
            actions.log("[更新] 目前閒置，自動下載並更新（設定與排程會保留）");
        }
        executor.execute(() -> install(notice));
    }

    /** 手動模式：詢問使用者；按「否」會記住這一版。 */
    private boolean askUser(String digest, String remoteWithTime) {
        boolean accepted = actions.confirm(
                "伺服器上有不同的桌面端：\n"
                        + "目前：" + actions.localLabel() + "\n"
                        + "新版：" + remoteWithTime + "\n\n"
                        + "要下載並重新啟動嗎？\n"
                        + "設定與排程會保留。選「否」之後不會再問這一版。");
        if (!accepted) {
            actions.rememberIgnored(digest);
        }
        return accepted;
    }

    private void install(UpdateNotice notice) {
        boolean relaunched = false;
        try {
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
                actions.log("[更新] 等候閒置逾時，這次不更新，下次心跳會再嘗試");
                Files.deleteIfExists(jar);
                return;
            }
            actions.install(jar);
            relaunched = true;
            actions.log("[更新] 下載完成，即將重新啟動");
            actions.restart();
        } catch (TransferIo.CancelledException ex) {
            actions.log("[更新] 已取消下載，下次心跳會再嘗試");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            actions.warn("更新失敗：" + message);
        } finally {
            if (!relaunched) {
                busy.set(false);
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
                actions.log("[更新] 新版已下載，但目前有打卡執行中或排程將至，等閒置後再重新啟動");
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
