package com.example.service;

import com.example.ClientUpdatePolicy;
import com.example.service.HeartbeatService.UpdateNotice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 收到心跳的更新通知後詢問使用者。同意才下載、校驗、換檔重開。
 * 同一版按過「否」不再問；打卡執行中先跳過，等下一次心跳。
 */
public final class ClientUpdateCoordinator {

    public interface Actions {
        String localDigest();

        String ignoredDigest();

        boolean checkingIn();

        void rememberIgnored(String digest);

        boolean confirm(String message);

        void log(String line);

        void warn(String message);

        Path download(UpdateNotice notice) throws Exception;

        boolean canInstall();

        void install(Path jar) throws Exception;

        void restart();
    }

    private final Actions actions;
    private final Executor executor;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public ClientUpdateCoordinator(Actions actions) {
        this(actions, command -> {
            Thread thread = new Thread(command, "client-update");
            thread.setDaemon(false);
            thread.start();
        });
    }

    ClientUpdateCoordinator(Actions actions, Executor executor) {
        this.actions = actions;
        this.executor = executor;
    }

    public void handle(UpdateNotice notice) {
        if (notice == null) {
            return;
        }
        String digest = notice.sourceDigest;
        if (digest.isEmpty() || digest.equals(safe(actions.localDigest())) || digest.equals(safe(actions.ignoredDigest()))) {
            return;
        }
        if (actions.checkingIn()) {
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        boolean accepted;
        try {
            String label = notice.version.isBlank() ? "新版本" : "v" + notice.version;
            String shortId = digest.length() >= 8 ? digest.substring(0, 8) : digest;
            actions.log("[更新] 發現不同的桌面端 " + label + "（" + shortId + "）");
            accepted = actions.confirm(
                    "伺服器上的桌面端與你現在用的不同（" + label + "）。\n"
                            + "要下載並重新啟動嗎？\n"
                            + "設定與排程會保留。選「否」之後不會再問這一版。");
        } catch (RuntimeException ex) {
            busy.set(false);
            throw ex;
        }
        if (!accepted) {
            actions.rememberIgnored(digest);
            busy.set(false);
            return;
        }
        if (actions.checkingIn()) {
            actions.log("[更新] 打卡執行中，這次先不更新");
            busy.set(false);
            return;
        }
        executor.execute(() -> install(notice));
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
            actions.install(jar);
            relaunched = true;
            actions.log("[更新] 下載完成，即將重新啟動");
            actions.restart();
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            actions.warn("更新失敗：" + message);
        } finally {
            if (!relaunched) {
                busy.set(false);
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
