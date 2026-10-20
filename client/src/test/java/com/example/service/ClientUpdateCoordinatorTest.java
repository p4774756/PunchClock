package com.example.service;

import com.example.ClientUpdatePolicy;
import com.example.service.HeartbeatService.UpdateNotice;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientUpdateCoordinatorTest {

    private static final String LOCAL = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void skipsIgnoredCheckingInAndSameDigest() throws Exception {
        FakeActions actions = new FakeActions();
        actions.local = LOCAL;
        actions.ignored = OTHER;
        ClientUpdateCoordinator coordinator = new ClientUpdateCoordinator(actions, Runnable::run);

        coordinator.handle(notice(OTHER, sampleJar(actions)));
        assertFalse(actions.confirmed);

        actions.ignored = "";
        actions.checkingIn = true;
        coordinator.handle(notice(OTHER, sampleJar(actions)));
        assertFalse(actions.confirmed);

        actions.checkingIn = false;
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
        boolean checkingIn;
        boolean accept = true;
        boolean canInstall;
        boolean cancelDownload;
        boolean confirmed;
        boolean restarted;
        Path jar;
        final List<Path> installed = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        @Override
        public String localDigest() {
            return local;
        }

        @Override
        public String ignoredDigest() {
            return ignored;
        }

        @Override
        public boolean checkingIn() {
            return checkingIn;
        }

        @Override
        public void rememberIgnored(String digest) {
            ignored = digest;
        }

        @Override
        public boolean confirm(String message) {
            confirmed = true;
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
