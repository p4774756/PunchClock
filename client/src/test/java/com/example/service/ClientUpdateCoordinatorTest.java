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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ClientUpdateCoordinatorTest {

    private static final String LOCAL = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void offerIgnoresSameOrEmptyDigestAndDoesNotDownload() throws Exception {
        FakeActions actions = new FakeActions();
        actions.local = LOCAL;
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        assertNull(coordinator.offer(null));
        assertNull(coordinator.offer(notice("", null)));
        assertNull(coordinator.offer(notice(LOCAL, sampleJar(actions))));
        assertEquals(0, actions.downloads.get());
        assertTrue(actions.installed.isEmpty());
    }

    @Test
    public void offerReturnsNewerBuildEvenWhenBusy() throws Exception {
        FakeActions actions = new FakeActions();
        actions.local = LOCAL;
        actions.idleDefault = false;
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);
        UpdateNotice notice = notice(OTHER, sampleJar(actions));

        assertSame(notice, coordinator.offer(notice));
        assertEquals(0, actions.downloads.get());
    }

    @Test
    public void describeIncludesBuildTime() {
        UpdateNotice notice = new UpdateNotice("2.0.2", OTHER, "2026-10-20T07:58:00Z", LOCAL, 1,
                "/api/client-release/jar");
        String label = ClientUpdateCoordinator.describe(notice);
        assertTrue(label, label.startsWith("v2.0.2 · bbbbbbb（建置 2026-10-"));
    }

    @Test
    public void applyDownloadsChecksHashAndRestarts() throws Exception {
        FakeActions actions = new FakeActions();
        actions.canInstall = true;
        Path jar = sampleJar(actions);
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        assertTrue(coordinator.apply(notice(OTHER, jar)));

        assertEquals(1, actions.installed.size());
        assertTrue(actions.restarted);
        assertFalse(actions.finished);
        assertTrue(actions.phases.contains("正在下載更新…"));
        assertTrue(actions.logs.stream().anyMatch(line -> line.contains("即將重新啟動")));
    }

    @Test
    public void secondApplyWhileBusyIsIgnored() throws Exception {
        FakeActions actions = new FakeActions();
        actions.canInstall = true;
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, command -> {
        });

        assertTrue(coordinator.apply(notice(OTHER, sampleJar(actions))));
        assertFalse(coordinator.apply(notice(OTHER, actions.jar)));
        assertEquals(0, actions.downloads.get());
    }

    @Test
    public void waitsForIdleAfterDownloadBeforeRestarting() throws Exception {
        FakeActions actions = new FakeActions();
        actions.canInstall = true;
        actions.idleSequence.add(false);
        actions.idleSequence.add(false);
        actions.idleSequence.add(true);
        Path jar = sampleJar(actions);
        AtomicInteger sleeps = new AtomicInteger();
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(
                actions, Runnable::run, millis -> sleeps.incrementAndGet(), 10, 1_000);

        coordinator.apply(notice(OTHER, jar));

        assertEquals("兩次輪詢後才閒置", 2, sleeps.get());
        assertEquals(1, actions.installed.size());
        assertTrue(actions.restarted);
        assertTrue(actions.phases.contains("等打卡結束後重新啟動"));
        assertTrue(actions.logs.stream().anyMatch(line -> line.contains("等閒置後再重新啟動")));
    }

    @Test
    public void giveUpWhenNeverIdleAndAllowRetry() throws Exception {
        FakeActions actions = new FakeActions();
        actions.canInstall = true;
        actions.idleDefault = false;
        Path jar = sampleJar(actions);
        AtomicInteger sleeps = new AtomicInteger();
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(
                actions, Runnable::run, millis -> sleeps.incrementAndGet(), 10, 30);

        coordinator.apply(notice(OTHER, jar));

        assertEquals(3, sleeps.get());
        assertTrue(actions.installed.isEmpty());
        assertFalse(actions.restarted);
        assertTrue(actions.finished);
        assertTrue(actions.logs.stream().anyMatch(line -> line.contains("等候閒置逾時")));
        assertTrue(actions.warnings.stream().anyMatch(line -> line.contains("先不重新啟動")));
        assertFalse("下載的檔案要清掉", Files.exists(jar));

        actions.jar = null;
        actions.idleDefault = true;
        actions.finished = false;
        Path again = sampleJar(actions);
        coordinator.apply(notice(OTHER, again));
        assertEquals(2, actions.downloads.get());
        assertTrue(actions.restarted);
    }

    @Test
    public void badHashDoesNotRestart() throws Exception {
        FakeActions actions = new FakeActions();
        actions.canInstall = true;
        Path jar = sampleJar(actions);
        UpdateNotice notice = notice(OTHER, jar);
        UpdateNotice bad = new UpdateNotice(notice.version, notice.sourceDigest, LOCAL, notice.size, notice.path);
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.apply(bad);

        assertTrue(actions.installed.isEmpty());
        assertFalse(actions.restarted);
        assertTrue(actions.finished);
        assertTrue(actions.warnings.stream().anyMatch(line -> line.contains("校驗失敗")));
    }

    @Test
    public void cancelledDownloadDoesNotRestartAndCanBeTriedAgain() throws Exception {
        FakeActions actions = new FakeActions();
        actions.canInstall = true;
        actions.cancelDownload = true;
        Path jar = sampleJar(actions);
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.apply(notice(OTHER, jar));

        assertTrue(actions.installed.isEmpty());
        assertFalse(actions.restarted);
        assertTrue(actions.warnings.isEmpty());
        assertTrue(actions.finished);
        assertTrue(actions.logs.stream().anyMatch(line -> line.contains("已取消下載")));

        actions.cancelDownload = false;
        actions.finished = false;
        coordinator.apply(notice(OTHER, jar));
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
        /** 依序回傳；用完後回傳 idleDefault。 */
        final Deque<Boolean> idleSequence = new ArrayDeque<>();
        boolean idleDefault = true;
        boolean canInstall;
        boolean cancelDownload;
        boolean restarted;
        boolean finished;
        Path jar;
        final AtomicInteger downloads = new AtomicInteger();
        final List<Path> installed = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        final List<String> phases = new ArrayList<>();
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
        public boolean idle() {
            Boolean next = idleSequence.poll();
            return next != null ? next : idleDefault;
        }

        @Override
        public void log(String line) {
            logs.add(line);
        }

        @Override
        public void phase(String title) {
            phases.add(title);
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

        @Override
        public void finished(boolean restarted) {
            finished = !restarted;
        }
    }
}
