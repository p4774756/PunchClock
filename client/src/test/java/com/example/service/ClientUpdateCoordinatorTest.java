package com.example.service;

import com.example.ClientUpdatePolicy;
import com.example.service.HeartbeatService.UpdateNotice;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientUpdateCoordinatorTest {

    private static final String LOCAL = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void skipsIgnoredBusyAndSameDigest() throws Exception {
        FakeActions actions = new FakeActions();
        actions.local = LOCAL;
        actions.ignored = OTHER;
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.handle(notice(OTHER, sampleJar(actions)));
        assertFalse(actions.confirmed);

        actions.ignored = "";
        actions.idleDefault = false;
        coordinator.handle(notice(OTHER, sampleJar(actions)));
        assertFalse(actions.confirmed);

        actions.idleDefault = true;
        coordinator.handle(notice(LOCAL, sampleJar(actions)));
        assertFalse(actions.confirmed);
    }

    @Test
    public void declineRemembersDigest() {
        FakeActions actions = new FakeActions();
        actions.accept = false;
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.handle(notice(OTHER, null));
        assertEquals(OTHER, actions.ignored);
        assertFalse(actions.restarted);

        actions.confirmed = false;
        coordinator.handle(notice(OTHER, null));
        assertFalse(actions.confirmed);
    }

    @Test
    public void confirmDialogShowsCurrentAndNewBuildWithTime() throws Exception {
        FakeActions actions = new FakeActions();
        actions.accept = false;
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.handle(new UpdateNotice("2.0.2", OTHER, "2026-10-20T07:58:00Z", LOCAL, 1,
                "/api/client-release/jar"));

        assertTrue(actions.confirmMessage, actions.confirmMessage.contains("目前：v2.0.2 · a1b2c3d"));
        assertTrue(actions.confirmMessage, actions.confirmMessage.contains("新版：v2.0.2 · bbbbbbb（建置 2026-10-"));
        assertTrue(actions.logs.toString(), actions.logs.stream().anyMatch(l -> l.contains("v2.0.2 · bbbbbbb")));
    }

    @Test
    public void acceptDownloadsChecksHashAndRestarts() throws Exception {
        FakeActions actions = new FakeActions();
        actions.accept = true;
        actions.canInstall = true;
        Path jar = sampleJar(actions);
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.handle(notice(OTHER, jar));

        assertEquals(1, actions.installed.size());
        assertTrue(actions.restarted);
        assertTrue(actions.logs.stream().anyMatch(line -> line.contains("即將重新啟動")));
    }

    @Test
    public void autoModeUpdatesWithoutAskingEvenIfVersionWasIgnoredBefore() throws Exception {
        FakeActions actions = new FakeActions();
        actions.auto = true;
        actions.canInstall = true;
        actions.ignored = OTHER;
        Path jar = sampleJar(actions);
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.handle(notice(OTHER, jar));

        assertFalse("自動模式不詢問", actions.confirmed);
        assertEquals(1, actions.installed.size());
        assertTrue(actions.restarted);
        assertTrue(actions.logs.stream().anyMatch(l -> l.contains("自動下載並更新")));
    }

    @Test
    public void autoModeDoesNothingWhileBusyThenUpdatesOnceIdle() throws Exception {
        FakeActions actions = new FakeActions();
        actions.auto = true;
        actions.canInstall = true;
        actions.idleDefault = false;
        Path jar = sampleJar(actions);
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.handle(notice(OTHER, jar));
        assertEquals("打卡中／排程將至不下載", 0, actions.downloads.get());
        assertFalse(actions.restarted);

        actions.idleDefault = true;
        coordinator.handle(notice(OTHER, jar));
        assertEquals(1, actions.downloads.get());
        assertTrue(actions.restarted);
    }

    @Test
    public void manualModeSkipsWhenBecomesBusyAfterAccepting() throws Exception {
        FakeActions actions = new FakeActions();
        actions.accept = true;
        actions.canInstall = true;
        actions.idleSequence.add(true);   // 通知當下閒置
        actions.idleDefault = false;      // 使用者按「是」之前打卡開始了
        Path jar = sampleJar(actions);
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.handle(notice(OTHER, jar));

        assertEquals(0, actions.downloads.get());
        assertFalse(actions.restarted);
        assertTrue(actions.logs.stream().anyMatch(l -> l.contains("這次先不更新")));
    }

    @Test
    public void waitsForIdleAfterDownloadBeforeRestarting() throws Exception {
        FakeActions actions = new FakeActions();
        actions.auto = true;
        actions.canInstall = true;
        actions.idleSequence.add(true);   // handle：通知當下
        actions.idleSequence.add(true);   // handle：排入背景前
        actions.idleSequence.add(false);  // 下載完成時打卡剛好開始
        actions.idleSequence.add(false);
        actions.idleSequence.add(true);   // 打卡結束
        Path jar = sampleJar(actions);
        AtomicInteger sleeps = new AtomicInteger();
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(
                actions, Runnable::run, millis -> sleeps.incrementAndGet(), 10, 1_000);

        coordinator.handle(notice(OTHER, jar));

        assertEquals("兩次輪詢後才閒置", 2, sleeps.get());
        assertEquals(1, actions.installed.size());
        assertTrue(actions.restarted);
        assertTrue(actions.logs.stream().anyMatch(l -> l.contains("等閒置後再重新啟動")));
    }

    @Test
    public void giveUpWhenNeverIdleAndAllowRetryOnNextHeartbeat() throws Exception {
        FakeActions actions = new FakeActions();
        actions.auto = true;
        actions.canInstall = true;
        actions.idleSequence.add(true);
        actions.idleSequence.add(true);
        actions.idleDefault = false;
        Path jar = sampleJar(actions);
        AtomicInteger sleeps = new AtomicInteger();
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(
                actions, Runnable::run, millis -> sleeps.incrementAndGet(), 10, 30);

        coordinator.handle(notice(OTHER, jar));

        assertEquals(3, sleeps.get());
        assertTrue(actions.installed.isEmpty());
        assertFalse(actions.restarted);
        assertTrue(actions.logs.stream().anyMatch(l -> l.contains("等候閒置逾時")));
        assertFalse("下載的檔案要清掉", Files.exists(jar));

        // 下次心跳：busy 旗標已釋放，會重新下載
        actions.jar = null;
        actions.idleDefault = true;
        Path again = sampleJar(actions);
        coordinator.handle(notice(OTHER, again));
        assertEquals(2, actions.downloads.get());
        assertTrue(actions.restarted);
    }

    @Test
    public void badHashDoesNotRestart() throws Exception {
        FakeActions actions = new FakeActions();
        actions.accept = true;
        actions.canInstall = true;
        Path jar = sampleJar(actions);
        UpdateNotice notice = notice(OTHER, jar);
        UpdateNotice bad = new UpdateNotice(notice.version, notice.sourceDigest, LOCAL, notice.size, notice.path);
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.handle(bad);

        assertTrue(actions.installed.isEmpty());
        assertFalse(actions.restarted);
        assertTrue(actions.warnings.stream().anyMatch(line -> line.contains("校驗失敗")));
    }

    @Test
    public void cancelledDownloadDoesNotRestartOrWarnAndAsksAgainLater() throws Exception {
        FakeActions actions = new FakeActions();
        actions.accept = true;
        actions.canInstall = true;
        actions.cancelDownload = true;
        Path jar = sampleJar(actions);
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.handle(notice(OTHER, jar));

        assertTrue(actions.installed.isEmpty());
        assertFalse(actions.restarted);
        assertTrue(actions.warnings.isEmpty());
        assertEquals("", actions.ignored);
        assertTrue(actions.logs.stream().anyMatch(line -> line.contains("已取消下載")));

        actions.cancelDownload = false;
        actions.confirmed = false;
        coordinator.handle(notice(OTHER, jar));
        assertTrue(actions.confirmed);
        assertTrue(actions.restarted);
    }

    private static UpdateNotice notice(String digest, Path jar) {
        String sha = LOCAL;
        long size = 1;
        if (jar != null) {
            try {
                sha = ClientUpdatePolicy.sha256(jar);
                size = Files.size(jar);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return new UpdateNotice("1.8.0", digest, sha, size, "/api/client-release/jar");
    }

    private static Path sampleJar(FakeActions actions) throws Exception {
        if (actions.jar == null) {
            actions.jar = Files.createTempFile("punchclock-update", ".jar");
            Files.writeString(actions.jar, "jar-bytes", StandardCharsets.UTF_8);
        }
        return actions.jar;
    }

    private static final class FakeActions implements ClientUpdateCoordinator.Actions {
        String local = "";
        String ignored = "";
        /** 依序回傳；用完後回傳 idleDefault。 */
        final Deque<Boolean> idleSequence = new ArrayDeque<>();
        boolean idleDefault = true;
        boolean auto;
        boolean accept = true;
        boolean canInstall;
        boolean cancelDownload;
        boolean confirmed;
        String confirmMessage = "";
        boolean restarted;
        Path jar;
        final AtomicInteger downloads = new AtomicInteger();
        final List<Path> installed = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        @Override
        public String localDigest() {
            return local;
        }

        @Override
        public String localLabel() {
            return "v2.0.2 · a1b2c3d";
        }

        @Override
        public String ignoredDigest() {
            return ignored;
        }

        @Override
        public boolean idle() {
            Boolean next = idleSequence.poll();
            return next != null ? next : idleDefault;
        }

        @Override
        public boolean autoUpdate() {
            return auto;
        }

        @Override
        public void rememberIgnored(String digest) {
            ignored = digest;
        }

        @Override
        public boolean confirm(String message) {
            confirmed = true;
            confirmMessage = message;
            return accept;
        }

        @Override
        public void log(String line) {
            logs.add(line);
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public Path download(UpdateNotice notice) throws TransferIo.CancelledException {
            downloads.incrementAndGet();
            if (cancelDownload) {
                throw new TransferIo.CancelledException();
            }
            return jar;
        }

        @Override
        public boolean canInstall() {
            return canInstall;
        }

        @Override
        public void install(Path jar) {
            installed.add(jar);
        }

        @Override
        public void restart() {
            restarted = true;
        }
    }
}
