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

    private final String localId;
    private final String peerId;
    private final String roomId;
    private final boolean host;
    private long seed;
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
        String roomId = newRoomId();
        long seed = ThreadLocalRandom.current().nextLong();
        BlackjackSession session = new BlackjackSession(localId, peerId, roomId, true, Phase.INVITING);
        session.seed = seed;
        session.status = "已邀請【" + peerId + "】，等待接受（約 15 秒內送達）";
        return session;
    }

    public static BlackjackSession fromInvite(String localId, String peerId, String roomId, long seed) {
        BlackjackSession session = new BlackjackSession(localId, peerId, roomId, false, Phase.INVITED);
        session.seed = seed;
        session.status = "【" + peerId + "】邀請你玩 21 點";
        return session;
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
        return BlackjackProtocol.invite(roomId, seed);
    }

    /** 接受邀請並開局；回傳要送給對方的 accept 字串。 */
    public String accept() {
        if (phase != Phase.INVITED) {
            return null;
        }
        beginRound();
        status = myTurn ? "輪到你出牌" : "等待對方出牌（約 15 秒）";
        return BlackjackProtocol.accept(roomId);
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
        if (phase == Phase.CLOSED || phase == Phase.SETTLED) {
            phase = Phase.CLOSED;
            return null;
        }
        phase = Phase.CLOSED;
        status = "對戰已結束";
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
                beginRound();
                status = myTurn ? "對方已接受，輪到你出牌" : "對方已接受，等待對方出牌";
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
                phase = Phase.CLOSED;
                status = "對方結束了對戰";
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
