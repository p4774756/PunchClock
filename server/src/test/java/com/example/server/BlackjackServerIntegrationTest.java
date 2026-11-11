package com.example.server;

import com.example.BlackjackProtocol;
import com.example.service.BlackjackInvitePolicy;
import com.example.service.BlackjackSession;
import com.example.service.HeartbeatService;
import com.example.service.LunchChallenge;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 兩個獨立的桌面端身分（各自一個真正的 HeartbeatService）經真正的 ServerApp 互傳訊息與 21 點協定。
 * 收到 21 點訊息後的路由比照 App.handleBlackjackMessage：邀請交給 BlackjackInvitePolicy，其餘依 roomId 套用。
 * 每一步都等心跳回應處理完才往下，結果不靠計時器。
 */
public class BlackjackServerIntegrationTest {

    private static final long WAIT_SECONDS = 10;

    private Javalin app;
    private String base;
    private Desk alice;
    private Desk bob;

    @Before
    public void setUp() throws Exception {
        app = new ServerApp().start(0);
        base = "http://127.0.0.1:" + app.port();
        alice = new Desk("alice");
        bob = new Desk("bob");
        alice.pump();
        bob.pump();
    }

    @After
    public void tearDown() {
        if (alice != null) alice.close();
        if (bob != null) bob.close();
        if (app != null) app.stop();
    }

    @Test(timeout = 30_000)
    public void plainMessagesArriveInOrderBothWays() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertTrue(alice.send("bob", "午安 " + i));
            assertTrue(bob.send("alice", "你好 " + i + "｜含分隔符號"));
        }
        bob.pump();
        alice.pump();
        assertEquals(List.of("alice>午安 0", "alice>午安 1", "alice>午安 2", "alice>午安 3", "alice>午安 4"), bob.chat());
        assertEquals(5, alice.chat().size());
        assertEquals("bob>你好 4｜含分隔符號", alice.chat().get(4));
        // 已取走的不會在下一次心跳重複出現
        bob.pump();
        assertEquals(5, bob.chat().size());
    }

    @Test(timeout = 30_000)
    public void inviteAcceptAndGameSyncThroughServer() throws Exception {
        BlackjackSession host = invite(alice, bob, BlackjackSession.Mode.NORMAL);
        bob.pump();
        BlackjackSession guest = bob.game("alice");
        assertNotNull("bob 應收到邀請", guest);
        assertEquals(BlackjackSession.Phase.INVITED, guest.phase());
        assertEquals(host.roomId(), guest.roomId());

        assertTrue(bob.send("alice", bob.act("alice", BlackjackSession::accept)));
        alice.pump();
        playOut();

        assertEquals(BlackjackSession.Phase.SETTLED, host.phase());
        assertEquals(BlackjackSession.Phase.SETTLED, guest.phase());
        assertEquals(host.myScore(), guest.peerScore());
        assertEquals(host.peerScore(), guest.myScore());
        assertEquals(host.myHand().size(), guest.peerHand().size());
        assertEquals(mirror(host.outcome()), guest.outcome());
    }

    @Test(timeout = 60_000)
    public void lunchChoiceSyncsToLoser() throws Exception {
        BlackjackSession[] pair = lunchAwaitingChoice();
        BlackjackSession winnerSession = pair[0];
        Desk winner = winnerSession.localId().equals("alice") ? alice : bob;
        Desk loser = winner == alice ? bob : alice;

        String payload = winner.act(loser.id, s -> s.lunchPayload("牛肉麵"));
        assertNotNull(payload);
        assertTrue(winner.send(loser.id, payload));
        assertTrue(winner.act(loser.id, s -> s.confirmLunchSent("牛肉麵") ? "ok" : null) != null);
        loser.pump();

        assertEquals(LunchChallenge.Status.DECIDED, pair[1].lunch().status());
        assertEquals("牛肉麵", pair[1].lunch().choice());
        assertEquals(pair[0].lunch().choice(), pair[1].lunch().choice());
    }

    @Test(timeout = 30_000)
    public void crossedInvitesSettleOnSmallerRoomWithoutDecline() throws Exception {
        BlackjackSession aliceInvite = invite(alice, bob, BlackjackSession.Mode.NORMAL);
        BlackjackSession bobInvite = invite(bob, alice, BlackjackSession.Mode.NORMAL);
        alice.pump();
        bob.pump();
        alice.flush();
        bob.flush();
        alice.pump();
        bob.pump();

        String winnerRoom = aliceInvite.roomId().compareTo(bobInvite.roomId()) < 0
                ? aliceInvite.roomId() : bobInvite.roomId();
        assertEquals(winnerRoom, alice.game("bob").roomId());
        assertEquals(winnerRoom, bob.game("alice").roomId());
        assertEquals("交錯邀請不應互送婉拒", 0, alice.declinesSent() + bob.declinesSent());

        Desk keeper = winnerRoom.equals(aliceInvite.roomId()) ? alice : bob;
        Desk switcher = keeper == alice ? bob : alice;
        assertEquals(BlackjackSession.Phase.INVITING, keeper.game(switcher.id).phase());
        assertEquals(BlackjackSession.Phase.INVITED, switcher.game(keeper.id).phase());

        assertTrue(switcher.send(keeper.id, switcher.act(keeper.id, BlackjackSession::accept)));
        keeper.pump();
        assertNotEquals(BlackjackSession.Phase.INVITING, keeper.game(switcher.id).phase());
        playOut();
        assertEquals(BlackjackSession.Phase.SETTLED, alice.game("bob").phase());
        assertEquals(BlackjackSession.Phase.SETTLED, bob.game("alice").phase());
    }

    @Test(timeout = 30_000)
    public void inviteWhileBusyIsDeclinedAndRunningGameUntouched() throws Exception {
        BlackjackSession host = startPlaying();
        BlackjackSession running = bob.game("alice");
        int bobScore = running.myScore();
        String runningRoom = running.roomId();

        // alice 這邊那一場的 abort 沒送到（例如視窗被強制關閉），又重新邀請
        BlackjackSession retry = invite(alice, bob, BlackjackSession.Mode.NORMAL);
        assertNotEquals(host.roomId(), retry.roomId());
        bob.pump();
        bob.flush();
        assertEquals(1, bob.declinesSent());
        alice.pump();

        assertEquals("邀請方收到婉拒後不再卡在等待接受", BlackjackSession.Phase.CLOSED, retry.phase());
        assertEquals(runningRoom, bob.game("alice").roomId());
        assertEquals(BlackjackSession.Phase.PLAYING, bob.game("alice").phase());
        assertEquals(bobScore, bob.game("alice").myScore());
    }

    @Test(timeout = 30_000)
    public void declineCancelAndAbortReachThePeer() throws Exception {
        // 被邀請方拒絕
        BlackjackSession first = invite(alice, bob, BlackjackSession.Mode.NORMAL);
        bob.pump();
        assertTrue(bob.send("alice", bob.act("alice", BlackjackSession::decline)));
        alice.pump();
        assertEquals(BlackjackSession.Phase.CLOSED, first.phase());

        // 邀請方在對方接受前取消：邀請與取消依序送達
        BlackjackSession second = invite(alice, bob, BlackjackSession.Mode.NORMAL);
        assertTrue(alice.send("bob", alice.act("bob", BlackjackSession::abort)));
        bob.pump();
        assertEquals(second.roomId(), bob.game("alice").roomId());
        assertEquals(BlackjackSession.Phase.CLOSED, bob.game("alice").phase());

        // 對戰中離開
        startPlaying();
        assertTrue(bob.send("alice", bob.act("alice", BlackjackSession::abort)));
        alice.pump();
        assertEquals(BlackjackSession.Phase.CLOSED, alice.game("bob").phase());
        assertEquals("對方結束了對戰", alice.game("bob").status());
    }

    @Test(timeout = 60_000)
    public void lunchAbortWinsOverLateLunchSend() throws Exception {
        BlackjackSession[] pair = lunchAwaitingChoice();
        Desk winner = pair[0].localId().equals("alice") ? alice : bob;
        Desk loser = winner == alice ? bob : alice;

        String lunch = winner.act(loser.id, s -> s.lunchPayload("咖哩飯"));
        assertNotNull(lunch);
        // 勝者送出途中，輸家先離開
        assertTrue(loser.send(winner.id, loser.act(winner.id, BlackjackSession::abort)));
        winner.pump();
        assertTrue(winner.send(loser.id, lunch));
        assertNull("對方已離開，不能再算成已決定", winner.act(loser.id, s -> s.confirmLunchSent("咖哩飯") ? "ok" : null));
        loser.pump();

        assertEquals(LunchChallenge.Status.ABANDONED, pair[0].lunch().status());
        assertEquals(LunchChallenge.Status.ABANDONED, pair[1].lunch().status());
        assertEquals(BlackjackSession.Phase.CLOSED, pair[0].phase());
        assertEquals(BlackjackSession.Phase.CLOSED, pair[1].phase());
    }

    @Test(timeout = 30_000)
    public void dashboardCancelReachesClientThroughHeartbeat() throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("password=secret")).build(),
                HttpResponse.BodyHandlers.ofString());
        String cookie = login.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith("auth=")).map(h -> h.split(";", 2)[0]).findFirst().orElse(null);
        assertNotNull("登入失敗", cookie);
        HttpResponse<String> cancel = http.send(HttpRequest.newBuilder(
                        URI.create(base + "/api/clients/bob/cancel-task/t-1"))
                .timeout(Duration.ofSeconds(5))
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cancel.statusCode());
        assertTrue(alice.send("bob", "取消之後的訊息"));
        bob.pump();
        assertEquals(List.of("CANCEL_TASK:t-1"), bob.commands());
        assertEquals(List.of("alice>取消之後的訊息"), bob.chat());
    }

    // ---------------------------------------------------------------- helpers

    private BlackjackSession invite(Desk from, Desk to, BlackjackSession.Mode mode) throws Exception {
        BlackjackSession session = BlackjackSession.startInvite(from.id, to.id, mode);
        from.put(to.id, session);
        assertTrue(from.send(to.id, session.invitePayload()));
        return session;
    }

    /** 輪到誰誰就出牌（17 點以下要牌），每一步都經伺服器送到對方。 */
    private void playOut() throws Exception {
        for (int step = 0; step < 40; step++) {
            BlackjackSession a = alice.game("bob");
            BlackjackSession b = bob.game("alice");
            if (a.phase() != BlackjackSession.Phase.PLAYING && b.phase() != BlackjackSession.Phase.PLAYING) {
                return;
            }
            Desk mover = a.phase() == BlackjackSession.Phase.PLAYING && a.myTurn() ? alice
                    : b.phase() == BlackjackSession.Phase.PLAYING && b.myTurn() ? bob : null;
            assertNotNull("雙方都不是自己的回合：狀態不同步", mover);
            Desk other = mover == alice ? bob : alice;
            String payload = mover.act(other.id, s -> s.canHit() && s.myScore() < 17 ? s.hit() : s.stand());
            assertNotNull(payload);
            assertTrue(mover.send(other.id, payload));
            other.pump();
        }
        fail("對戰沒有在 40 步內結束");
    }

    /** 跑到有勝負、等勝者決定午餐的那一局；回傳 [勝者那邊的 session, 輸家那邊的 session]。 */
    private BlackjackSession[] lunchAwaitingChoice() throws Exception {
        for (int game = 0; game < 40; game++) {
            invite(alice, bob, BlackjackSession.Mode.LUNCH);
            bob.pump();
            assertTrue(bob.send("alice", bob.act("alice", BlackjackSession::accept)));
            alice.pump();
            playOut();
            BlackjackSession a = alice.game("bob");
            BlackjackSession b = bob.game("alice");
            assertEquals(a.roomId(), b.roomId());
            if (a.lunch() != null && a.lunch().status() == LunchChallenge.Status.AWAITING_CHOICE) {
                assertEquals(LunchChallenge.Status.AWAITING_CHOICE, b.lunch().status());
                assertEquals(a.lunch().winnerId(), b.lunch().winnerId());
                return a.lunch().canChoose("alice") ? new BlackjackSession[]{a, b} : new BlackjackSession[]{b, a};
            }
        }
        throw new AssertionError("40 局內沒有分出勝負");
    }

    /** alice 邀請、bob 接受，直到開局後雙方都還在 PLAYING（開局兩張就分勝負的局重來）；回傳 alice 那邊的 session。 */
    private BlackjackSession startPlaying() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            BlackjackSession host = invite(alice, bob, BlackjackSession.Mode.NORMAL);
            bob.pump();
            assertTrue(bob.send("alice", bob.act("alice", BlackjackSession::accept)));
            alice.pump();
            if (host.phase() == BlackjackSession.Phase.PLAYING
                    && bob.game("alice").phase() == BlackjackSession.Phase.PLAYING) {
                return host;
            }
        }
        throw new AssertionError("20 局都在開局就結束");
    }

    private static BlackjackSession.Outcome mirror(BlackjackSession.Outcome outcome) {
        switch (outcome) {
            case WIN: return BlackjackSession.Outcome.LOSE;
            case LOSE: return BlackjackSession.Outcome.WIN;
            default: return outcome;
        }
    }

    private static void assertNotEquals(Object unexpected, Object actual) {
        assertFalse("不應為 " + unexpected, unexpected.equals(actual));
    }

    /** 一台桌面端：真正的 HeartbeatService，加上比照 App 的 21 點路由。 */
    private final class Desk {
        final String id;
        final HeartbeatService heartbeat = new HeartbeatService();
        private final Map<String, BlackjackSession> games = new HashMap<>();
        private final List<String> chat = Collections.synchronizedList(new ArrayList<>());
        private final List<String> commands = Collections.synchronizedList(new ArrayList<>());
        private final List<CompletableFuture<Boolean>> outgoing = Collections.synchronizedList(new ArrayList<>());
        private int declinesSent;

        Desk(String id) {
            this.id = id;
            heartbeat.setClientId(id);
            heartbeat.setCommandListener(this::onCommand);
            heartbeat.startHeartbeat(base, line -> { }, null);
        }

        /** 送一次心跳並等回應裡的指令都處理完。 */
        void pump() throws Exception {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            heartbeat.sendPostHeartbeat(line -> { }, done::complete);
            assertTrue(id + " 心跳失敗", done.get(WAIT_SECONDS, TimeUnit.SECONDS));
        }

        boolean send(String to, String text) throws Exception {
            assertNotNull("沒有可送出的內容", text);
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            heartbeat.sendPeerMessage(to, text, line -> { }, done::complete);
            return Boolean.TRUE.equals(done.get(WAIT_SECONDS, TimeUnit.SECONDS));
        }

        /** 等收訊時自動回覆的訊息（例如婉拒）都送完。 */
        void flush() throws Exception {
            List<CompletableFuture<Boolean>> pending;
            synchronized (outgoing) {
                pending = new ArrayList<>(outgoing);
                outgoing.clear();
            }
            for (CompletableFuture<Boolean> f : pending) {
                assertTrue(id + " 自動回覆沒送出", f.get(WAIT_SECONDS, TimeUnit.SECONDS));
            }
        }

        synchronized BlackjackSession game(String peer) {
            return games.get(peer);
        }

        synchronized void put(String peer, BlackjackSession session) {
            games.put(peer, session);
        }

        synchronized String act(String peer, Function<BlackjackSession, String> action) {
            return action.apply(games.get(peer));
        }

        synchronized int declinesSent() {
            return declinesSent;
        }

        List<String> chat() {
            return new ArrayList<>(chat);
        }

        List<String> commands() {
            return new ArrayList<>(commands);
        }

        private void onCommand(String command) {
            if (!command.startsWith("MSG|")) {
                commands.add(command);
                return;
            }
            // MSG|fromId|sentAtMs|avatar|text
            String[] parts = command.split("\\|", 5);
            String from = parts[1];
            String text = parts[4];
            if (BlackjackProtocol.isMessage(text)) {
                onBlackjack(from, text);
            } else {
                chat.add(from + ">" + text);
            }
        }

        private synchronized void onBlackjack(String from, String text) {
            BlackjackProtocol.Message message = BlackjackProtocol.parse(text);
            if (message == null) {
                return;
            }
            if (BlackjackProtocol.KIND_INVITE.equals(message.kind)) {
                if (message.seed == null) {
                    return;
                }
                switch (BlackjackInvitePolicy.onInvite(games.get(from), message.roomId)) {
                    case DUPLICATE:
                    case KEEP_LOCAL:
                        return;
                    case DECLINE_BUSY:
                        declinesSent++;
                        CompletableFuture<Boolean> done = new CompletableFuture<>();
                        outgoing.add(done);
                        heartbeat.sendPeerMessage(from, BlackjackProtocol.decline(message.roomId), line -> { },
                                done::complete);
                        return;
                    default:
                        games.put(from, BlackjackSession.fromInvite(id, from, message.roomId, message.seed,
                                BlackjackSession.modeOf(message)));
                        return;
                }
            }
            BlackjackSession session = games.get(from);
            if (session != null && message.roomId.equals(session.roomId())) {
                session.applyRemote(message);
            }
        }

        void close() {
            heartbeat.stopHeartbeat();
        }
    }
}
