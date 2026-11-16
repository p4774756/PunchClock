package com.example.pet;

import com.example.pet.DesktopPetAnimation.Mode;
import com.example.pet.DesktopPetCursorStatus.Action;
import com.example.pet.DesktopPetCursorStatus.Outcome;
import com.example.pet.DesktopPetCursorStatus.Phase;
import org.junit.Assume;
import org.junit.Test;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DesktopPetCursorTest {

    @Test
    public void mapsPromptEditStopAndErrors() {
        DesktopPetCursorStatus status = new DesktopPetCursorStatus();
        Outcome think = status.apply("beforeSubmitPrompt", "a", null);
        assertEquals(Action.THINK, think.action);
        assertEquals(Phase.THINK, think.hold);
        assertEquals(DesktopPetCursorStatus.BUBBLE_THINK, think.bubble);
        assertEquals(1, status.activeCount());

        Outcome busy = status.apply("afterFileEdit", "a", null);
        assertEquals(Action.BUSY, busy.action);
        assertEquals(Phase.BUSY, busy.hold);

        Outcome fail = status.apply("postToolUseFailure", "a", null);
        assertEquals(Action.WORRY_FLASH, fail.action);
        assertEquals(Phase.BUSY, fail.hold);

        Outcome done = status.apply("stop", "a", "completed");
        assertEquals(Action.CELEBRATE, done.action);
        assertEquals(Phase.NONE, done.hold);
        assertEquals(0, status.activeCount());

        status.apply("beforeSubmitPrompt", "b", null);
        Outcome error = status.apply("stop", "b", "error");
        assertEquals(Action.WORRY_FLASH, error.action);
        assertEquals(Phase.NONE, error.hold);

        status.apply("sessionStart", "c", null);
        Outcome aborted = status.apply("stop", "c", "aborted");
        assertEquals(Action.CLEAR, aborted.action);
        assertEquals(Phase.NONE, aborted.hold);
        assertEquals(0, status.activeCount());
    }

    @Test
    public void multipleSessionsStayBusyUntilLastCompletes() {
        DesktopPetCursorStatus status = new DesktopPetCursorStatus();
        status.apply("beforeSubmitPrompt", "one", null);
        status.apply("afterShellExecution", "one", null);
        status.apply("beforeSubmitPrompt", "two", null);
        status.apply("postToolUse", "two", null);
        assertEquals(2, status.activeCount());
        assertEquals(Phase.BUSY, status.phase());

        Outcome first = status.apply("stop", "one", "completed");
        assertEquals(Action.CELEBRATE, first.action);
        assertEquals(Phase.BUSY, first.hold);
        assertEquals(1, status.activeCount());

        Outcome last = status.apply("stop", "two", "completed");
        assertEquals(Action.CELEBRATE, last.action);
        assertEquals(Phase.NONE, last.hold);
        assertEquals(0, status.activeCount());
    }

    @Test
    public void sessionEndClearsWithoutLeaking() {
        DesktopPetCursorStatus status = new DesktopPetCursorStatus();
        status.apply("sessionStart", "x", null);
        status.apply("afterAgentThought", "x", null);
        assertEquals(1, status.activeCount());
        Outcome end = status.apply("sessionEnd", "x", null);
        assertEquals(Action.CLEAR, end.action);
        assertEquals(0, status.activeCount());
        assertEquals(Phase.NONE, status.phase());
    }

    @Test
    public void animationHoldsCursorPoseAndResumesAfterReact() {
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.setCursorHold(Mode.THINK);
        assertEquals(Mode.THINK, animation.mode());
        assertEquals(0, animation.stepX());
        for (int i = 0; i < DesktopPetAnimation.IDLE_TICKS + 5; i++) {
            animation.tick();
        }
        assertEquals(Mode.THINK, animation.mode());

        animation.setCursorHold(Mode.BUSY);
        assertEquals(Mode.BUSY, animation.mode());
        animation.celebrate(DesktopPetCursorStatus.BUBBLE_DONE);
        assertEquals(Mode.REACT, animation.mode());
        for (int i = 0; i < DesktopPetAnimation.REACT_TICKS; i++) {
            animation.tick();
        }
        assertEquals(Mode.BUSY, animation.mode());

        animation.flashWorry(DesktopPetCursorStatus.BUBBLE_ERROR);
        assertEquals(Mode.WORRY, animation.mode());
        for (int i = 0; i < DesktopPetAnimation.WORRY_TICKS; i++) {
            animation.tick();
        }
        assertEquals(Mode.BUSY, animation.mode());

        animation.clearCursorHold();
        assertEquals(Mode.IDLE, animation.mode());
    }

    @Test
    public void carryIgnoresCursorSwitchUntilReleased() {
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.setCarried(true);
        assertFalse(animation.flashWorry("x"));
        assertFalse(animation.celebrate("y"));
        animation.setCursorHold(Mode.THINK);
        assertTrue(animation.isCarried());
        assertEquals(Mode.THINK, animation.cursorHold());
        animation.setCarried(false);
        assertEquals(Mode.THINK, animation.mode());
    }

    @Test
    public void noticeInterruptReturnsToCursorHold() {
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.setCursorHold(Mode.BUSY);
        assertTrue(animation.showNotice("有新訊息"));
        assertEquals(Mode.REACT, animation.mode());
        assertEquals(Mode.BUSY, animation.cursorHold());
        for (int i = 0; i < DesktopPetAnimation.REACT_TICKS; i++) {
            animation.tick();
        }
        assertEquals(Mode.BUSY, animation.mode());
    }

    @Test
    public void hookServerBindsLoopbackAndChecksToken() throws Exception {
        Path dir = Files.createTempDirectory("milu-hook-test");
        Path file = dir.resolve("milu-hook.json");
        List<DesktopPetHookServer.HookEvent> events = new ArrayList<DesktopPetHookServer.HookEvent>();
        CountDownLatch latch = new CountDownLatch(1);
        DesktopPetHookServer server = new DesktopPetHookServer(file, event -> {
            events.add(event);
            latch.countDown();
        });
        Assume.assumeTrue("本機無法綁 127.0.0.1（沙箱限制）", server.start());
        try {
            assertEquals("127.0.0.1", server.bindHost());
            assertTrue(server.port() >= DesktopPetHookServer.DEFAULT_PORT);
            assertTrue(Files.isRegularFile(file));
            String body = "{\"hook_event_name\":\"stop\",\"conversation_id\":\"c1\",\"status\":\"completed\",\"prompt\":\"secret\"}";

            HttpURLConnection denied = post(server.port(), body, "wrong");
            assertEquals(401, denied.getResponseCode());
            denied.disconnect();

            HttpURLConnection ok = post(server.port(), body, server.token());
            assertEquals(200, ok.getResponseCode());
            ok.disconnect();
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(1, events.size());
            assertEquals("stop", events.get(0).eventName);
            assertEquals("c1", events.get(0).conversationId);
            assertEquals("completed", events.get(0).status);
        } finally {
            server.stop();
        }
        assertFalse(Files.exists(file));
    }

    @Test
    public void extractJsonStringIgnoresOtherFields() {
        String json = "{\"hook_event_name\":\"afterFileEdit\",\"conversation_id\":\"abc\","
                + "\"file_path\":\"/secret/path.java\",\"status\":\"ok\"}";
        assertEquals("afterFileEdit", DesktopPetHookServer.extractJsonString(json, "hook_event_name"));
        assertEquals("abc", DesktopPetHookServer.extractJsonString(json, "conversation_id"));
        assertEquals("ok", DesktopPetHookServer.extractJsonString(json, "status"));
    }

    @Test
    public void thinkBusyWorrySpritesLoadWhenPresent() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        assertEquals(4, frames.thinkCount());
        assertEquals(4, frames.busyCount());
        assertEquals(4, frames.worryCount());

        DesktopPetAnimation think = new DesktopPetAnimation();
        think.setCursorHold(Mode.THINK);
        assertEquals(frames.think(0), frames.imageFor(think));
        think.setCursorHold(Mode.BUSY);
        assertEquals(frames.busy(0), frames.imageFor(think));
        think.flashWorry(null);
        assertEquals(frames.worry(0), frames.imageFor(think));
    }

    @Test
    public void managerStartsAndStopsHookServerWithCursorSwitch() throws Exception {
        Path dir = Files.createTempDirectory("milu-hook-mgr");
        Path file = dir.resolve("milu-hook.json");
        MemoryPreferences prefs = new MemoryPreferences();
        DesktopPetSettings settings = new DesktopPetSettings(prefs);
        DesktopPetManager manager = new DesktopPetManager(settings, file);
        assertTrue(settings.cursorReact());

        // headless CI 可能無法顯示視窗；仍驗證關掉時會清檔、不丟例外。
        manager.setEnabled(false);
        assertNull(manager.hookServer());
        assertFalse(Files.exists(file));

        manager.setCursorReactEnabled(false);
        assertFalse(settings.cursorReact());
        manager.setCursorReactEnabled(true);
        assertTrue(settings.cursorReact());
        manager.shutdown();
        assertNull(manager.hookServer());
    }

    private static HttpURLConnection post(int port, String body, String token) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + port + "/hook");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout(1000);
        conn.setReadTimeout(1000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null) {
            conn.setRequestProperty("X-Milu-Token", token);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
        }
        return conn;
    }

    /** 與 DesktopPetTest 相同的記憶體 Preferences。 */
    private static final class MemoryPreferences extends java.util.prefs.AbstractPreferences {
        private final java.util.Map<String, String> values = new java.util.HashMap<String, String>();

        MemoryPreferences() {
            super(null, "");
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected java.util.prefs.AbstractPreferences childSpi(String name) {
            return null;
        }

        @Override
        protected void syncSpi() {
        }

        @Override
        protected void flushSpi() {
        }
    }
}
