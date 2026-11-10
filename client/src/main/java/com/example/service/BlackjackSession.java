package com.example.service;

import com.example.BlackjackProtocol;
import com.example.BlackjackRules;
import com.example.BlackjackRules.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一局兩人 21 點的狀態機。邀請方先手；牌序由 seed 決定。
 */
public final class BlackjackSession {

    public enum Phase {
        /** 已送出邀請，等對方接受 */
        INVITING,
        /** 收到邀請，等本機決定 */
        INVITED,
        /** 對戰中 */
        PLAYING,
        /** 已結算 */
        SETTLED,
        /** 已結束／拒絕對／中止 */
        CLOSED
    }

    public enum Outcome {
        WIN, LOSE, DRAW, NONE
    }

    public enum Mode {
        /** 單純比點數 */
        NORMAL,
        /** 贏家決定今天午餐 */
        LUNCH
    }

    private final String localId;
    private final String peerId;
    private final String roomId;
    private final boolean host;
    private long seed;
    private Mode mode = Mode.NORMAL;
    private LunchChallenge lunch;
    private boolean downgraded;
    private List<Card> deck = List.of();
    private int nextIndex;
    private final List<Card> myHand = new ArrayList<>();
    private final List<Card> peerHand = new ArrayList<>();
    private boolean myStood;
    private boolean peerStood;
    private boolean myTurn;
    private Phase phase;
    private Outcome outcome = Outcome.NONE;
    private String status = "";

    private BlackjackSession(String localId, String peerId, String roomId, boolean host, Phase phase) {
        this.localId = localId;
        this.peerId = peerId;
        this.roomId = roomId;
        this.host = host;
        this.phase = phase;
    }

    public static BlackjackSession startInvite(String localId, String peerId) {
        return startInvite(localId, peerId, Mode.NORMAL);
    }

    public static BlackjackSession startInvite(String localId, String peerId, Mode mode) {
        return startInvite(localId, peerId, mode, ThreadLocalRandom.current().nextLong());
    }

    /** 指定牌序，測試用。 */
    static BlackjackSession startInvite(String localId, String peerId, Mode mode, long seed) {
        String roomId = newRoomId();
        BlackjackSession session = new BlackjackSession(localId, peerId, roomId, true, Phase.INVITING);
        session.seed = seed;
        session.setMode(mode);
        session.status = "已邀請【" + peerId + "】" + (session.isLunch() ? "來場午餐爭奪戰" : "")
                + "，等待接受（約 15 秒內送達）";
        return session;
    }

    public static BlackjackSession fromInvite(String localId, String peerId, String roomId, long seed) {
        return fromInvite(localId, peerId, roomId, seed, Mode.NORMAL);
    }

    public static BlackjackSession fromInvite(String localId, String peerId, String roomId, long seed, Mode mode) {
        BlackjackSession session = new BlackjackSession(localId, peerId, roomId, false, Phase.INVITED);
        session.seed = seed;
        session.setMode(mode);
        session.status = session.isLunch()
                ? "【" + peerId + "】向你發起午餐爭奪戰！贏的人決定今天午餐"
                : "【" + peerId + "】邀請你玩 21 點";
        return session;
    }

    public static Mode modeOf(BlackjackProtocol.Message message) {
        return message != null && message.isLunchMode() ? Mode.LUNCH : Mode.NORMAL;
    }

    private void setMode(Mode next) {
        mode = next == null ? Mode.NORMAL : next;
        if (mode == Mode.LUNCH) {
            List<String> players = host ? List.of(localId, peerId) : List.of(peerId, localId);
            lunch = new LunchChallenge(roomId, players);
        } else {
            lunch = null;
        }
    }

    public Mode mode() {
        return mode;
    }

    public boolean isLunch() {
        return mode == Mode.LUNCH;
    }

    /** 午餐模式才有；普通對戰回 null。 */
    public LunchChallenge lunch() {
        return lunch;
    }

    /** 邀請時選了午餐模式，但對方是舊版，已改成普通對戰。 */
    public boolean downgraded() {
        return downgraded;
    }

    public String localId() {
        return localId;
    }

    public String peerId() {
        return peerId;
    }

    public String roomId() {
        return roomId;
    }

    public boolean host() {
        return host;
    }

    public Phase phase() {
        return phase;
    }

    public Outcome outcome() {
        return outcome;
    }

    public String status() {
        return status;
    }

    public boolean myTurn() {
        return myTurn;
    }

    public boolean myStood() {
        return myStood;
    }

    public boolean peerStood() {
        return peerStood;
    }

    public List<Card> myHand() {
        return Collections.unmodifiableList(myHand);
    }

    public List<Card> peerHand() {
        return Collections.unmodifiableList(peerHand);
    }

    public int myScore() {
        return BlackjackRules.handValue(myHand);
    }

    public int peerScore() {
        return BlackjackRules.handValue(peerHand);
    }

    /** 牌堆還剩幾張（開局前是整副）。 */
    public int deckLeft() {
        return BlackjackRules.DECK_SIZE - nextIndex;
    }

    public boolean canHit() {
        return phase == Phase.PLAYING && myTurn && !myStood && !BlackjackRules.isBust(myHand);
    }

    public boolean canStand() {
        return phase == Phase.PLAYING && myTurn && !myStood;
    }

    public boolean canAccept() {
        return phase == Phase.INVITED;
    }

    public boolean canDecline() {
        return phase == Phase.INVITED;
    }

    public String invitePayload() {
        return BlackjackProtocol.invite(roomId, seed, protocolMode());
    }

    /** 接受邀請並開局；回傳要送給對方的 accept 字串（午餐模式會帶上 lunch，讓邀請方知道我看得懂）。 */
    public String accept() {
        if (phase != Phase.INVITED) {
            return null;
        }
        beginRound();
        status = myTurn ? "輪到你出牌" : "等待對方出牌（約 15 秒）";
        return BlackjackProtocol.accept(roomId, protocolMode());
    }

    private String protocolMode() {
        return isLunch() ? BlackjackProtocol.MODE_LUNCH : BlackjackProtocol.MODE_NORMAL;
    }

    /**
     * 勝者要送出的午餐訊息。只檢查、不改狀態：等雲端確認收到再呼叫 {@link #confirmLunchSent}，
     * 送失敗時畫面才不會以為已經同步。
     */
    public String lunchPayload(String choice) {
        if (phase != Phase.SETTLED || lunch == null || !lunch.canChoose(localId)) {
            return null;
        }
        String clean = BlackjackProtocol.cleanLunchChoice(choice);
        return clean.isEmpty() ? null : BlackjackProtocol.lunch(roomId, clean);
    }

    public boolean confirmLunchSent(String choice) {
        if (phase != Phase.SETTLED || lunch == null || !lunch.decide(localId, choice)) {
            return false;
        }
        status = "今天午餐：" + lunch.choice() + "（已通知對方）";
        return true;
    }

    public String decline() {
        if (phase != Phase.INVITED) {
            return null;
        }
        phase = Phase.CLOSED;
        status = "已拒絕邀請";
        return BlackjackProtocol.decline(roomId);
    }

    public String hit() {
        if (!canHit()) {
            return null;
        }
        int index = nextIndex;
        applyHit(true);
        return BlackjackProtocol.hit(roomId, index);
    }

    public String stand() {
        if (!canStand()) {
            return null;
        }
        applyStand(true);
        return BlackjackProtocol.stand(roomId);
    }

    public String abort() {
        if (phase == Phase.SETTLED && lunch != null
                && lunch.status() == LunchChallenge.Status.AWAITING_CHOICE) {
            // 午餐還沒決定就離開，要通知對方別再等
            lunch.abandon();
            phase = Phase.CLOSED;
            status = "這次午餐沒有決定";
            return BlackjackProtocol.abort(roomId);
        }
        if (phase == Phase.CLOSED || phase == Phase.SETTLED) {
            phase = Phase.CLOSED;
            return null;
        }
        phase = Phase.CLOSED;
        status = "對戰已結束";
        if (lunch != null) {
            lunch.abandon();
        }
        return BlackjackProtocol.abort(roomId);
    }

    /**
     * 套用對方訊息。回傳 true 表示有狀態變化。
     */
    public boolean applyRemote(BlackjackProtocol.Message message) {
        if (message == null || !roomId.equals(message.roomId)) {
            return false;
        }
        switch (message.kind) {
            case BlackjackProtocol.KIND_ACCEPT:
                if (phase != Phase.INVITING || !host) {
                    return false;
                }
                if (isLunch() && !message.isLunchMode()) {
                    // 舊版接受時不會回 lunch：對方只會玩普通對戰，這邊也跟著改
                    setMode(Mode.NORMAL);
                    downgraded = true;
                }
                beginRound();
                status = downgraded
                        ? "對方的版本不支援午餐爭奪戰，改為普通對戰"
                        : (myTurn ? "對方已接受，輪到你出牌" : "對方已接受，等待對方出牌");
                return true;
            case BlackjackProtocol.KIND_DECLINE:
                if (phase != Phase.INVITING) {
                    return false;
                }
                phase = Phase.CLOSED;
                status = "對方拒絕了邀請";
                return true;
            case BlackjackProtocol.KIND_HIT:
                if (phase != Phase.PLAYING || myTurn) {
                    return false;
                }
                if (message.deckIndex != null && message.deckIndex < nextIndex) {
                    // 重送或重複收到的舊要牌，這張已經算過
                    return false;
                }
                if (message.deckIndex == null || message.deckIndex != nextIndex) {
                    status = "牌序不同步，對戰中止";
                    phase = Phase.CLOSED;
                    return true;
                }
                applyHit(false);
                return true;
            case BlackjackProtocol.KIND_STAND:
                if (phase != Phase.PLAYING || myTurn) {
                    return false;
                }
                applyStand(false);
                return true;
            case BlackjackProtocol.KIND_ABORT:
                if (phase == Phase.CLOSED) {
                    return false;
                }
                boolean awaitingLunch = phase == Phase.SETTLED && lunch != null
                        && lunch.status() == LunchChallenge.Status.AWAITING_CHOICE;
                if (lunch != null) {
                    lunch.abandon();
                }
                phase = Phase.CLOSED;
                status = awaitingLunch ? "對方離開了，這次午餐沒有決定" : "對方結束了對戰";
                return true;
            case BlackjackProtocol.KIND_LUNCH:
                if (phase != Phase.SETTLED || lunch == null || !lunch.canChoose(peerId)) {
                    return false;
                }
                if (!lunch.decide(peerId, message.choice)) {
                    return false;
                }
                status = "今天午餐：" + lunch.choice() + "（由對方決定）";
                return true;
            default:
                return false;
        }
    }

    private void beginRound() {
        deck = BlackjackRules.shuffledDeck(seed);
        nextIndex = 0;
        myHand.clear();
        peerHand.clear();
        myStood = false;
        peerStood = false;
        // 發牌順序：host, guest, host, guest
        if (host) {
            myHand.add(draw());
            peerHand.add(draw());
            myHand.add(draw());
            peerHand.add(draw());
        } else {
            peerHand.add(draw());
            myHand.add(draw());
            peerHand.add(draw());
            myHand.add(draw());
        }
        myTurn = host;
        phase = Phase.PLAYING;
        outcome = Outcome.NONE;
        if (BlackjackRules.isBust(myHand) || BlackjackRules.isBust(peerHand)) {
            settle();
        }
    }

    private void applyHit(boolean local) {
        Card card = draw();
        if (local) {
            myHand.add(card);
            if (BlackjackRules.isBust(myHand)) {
                myStood = true;
                settle();
                return;
            }
            status = "你要了一張 " + card.label() + "，可繼續或停牌";
        } else {
            peerHand.add(card);
            if (BlackjackRules.isBust(peerHand)) {
                peerStood = true;
                settle();
                return;
            }
            status = "對方要了一張 " + card.label() + "，等待對方行動";
        }
    }

    private void applyStand(boolean local) {
        if (local) {
            myStood = true;
            myTurn = false;
            if (peerStood) {
                settle();
            } else {
                status = "你已停牌，等待對方（約 15 秒）";
            }
        } else {
            peerStood = true;
            if (myStood) {
                settle();
            } else {
                myTurn = true;
                status = "對方已停牌，輪到你";
            }
        }
    }

    private void settle() {
        phase = Phase.SETTLED;
        myTurn = false;
        int cmp = BlackjackRules.compare(myHand, peerHand);
        if (cmp > 0) {
            outcome = Outcome.WIN;
            status = "你贏了！" + summary();
        } else if (cmp < 0) {
            outcome = Outcome.LOSE;
            status = "你輸了。" + summary();
        } else {
            outcome = Outcome.DRAW;
            status = "平手。" + summary();
        }
        if (lunch != null) {
            lunch.settle(outcome == Outcome.WIN ? localId : outcome == Outcome.LOSE ? peerId : null);
            if (outcome == Outcome.WIN) {
                status = "恭喜！今天午餐由你決定！";
            } else if (outcome == Outcome.LOSE) {
                status = "今天午餐由對方決定！等待對方選擇";
            } else {
                status = "平手！午餐還沒有主人，再戰一局吧";
            }
        }
    }

    private String summary() {
        return " 你 " + myScore() + " ：對方 " + peerScore();
    }

    private Card draw() {
        if (nextIndex >= deck.size()) {
            throw new IllegalStateException("deck exhausted");
        }
        return deck.get(nextIndex++);
    }

    private static String newRoomId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
