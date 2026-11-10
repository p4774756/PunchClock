package com.example.service;

import com.example.BlackjackProtocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一場午餐爭奪戰：誰參加、誰贏、贏家選了什麼。
 * 參加者用清單存，之後若做三人同桌也不用改資料形狀；勝負本身仍由對戰規則決定後再交進來。
 */
public final class LunchChallenge {

    public enum Status {
        /** 牌局還沒結束 */
        PLAYING,
        /** 已分出勝負，等勝者送出午餐 */
        AWAITING_CHOICE,
        /** 午餐已決定，雙方一致 */
        DECIDED,
        /** 平手，沒有人取得決定權 */
        DRAW,
        /** 勝者還沒選就離開了 */
        ABANDONED
    }

    private final String roomId;
    private final List<String> participantIds;
    private Status status = Status.PLAYING;
    private String winnerId;
    private String choice;

    public LunchChallenge(String roomId, List<String> participantIds) {
        if (participantIds == null || participantIds.size() < 2) {
            throw new IllegalArgumentException("lunch challenge needs at least two participants");
        }
        this.roomId = roomId;
        this.participantIds = Collections.unmodifiableList(new ArrayList<>(participantIds));
    }

    public String roomId() {
        return roomId;
    }

    public List<String> participantIds() {
        return participantIds;
    }

    public Status status() {
        return status;
    }

    public String winnerId() {
        return winnerId;
    }

    public String choice() {
        return choice;
    }

    /** 牌局結束時呼叫一次。{@code winner} 為 null 代表平手。 */
    void settle(String winner) {
        if (status != Status.PLAYING) {
            return;
        }
        if (winner == null) {
            status = Status.DRAW;
            return;
        }
        if (!participantIds.contains(winner)) {
            throw new IllegalArgumentException("winner is not a participant: " + winner);
        }
        winnerId = winner;
        status = Status.AWAITING_CHOICE;
    }

    public boolean canChoose(String clientId) {
        return status == Status.AWAITING_CHOICE && winnerId != null && winnerId.equals(clientId);
    }

    /** 由勝者決定午餐。不是勝者、狀態不對或名稱空白都回 false，狀態不變。 */
    boolean decide(String clientId, String rawChoice) {
        String clean = BlackjackProtocol.cleanLunchChoice(rawChoice);
        if (!canChoose(clientId) || clean.isEmpty()) {
            return false;
        }
        choice = clean;
        status = Status.DECIDED;
        return true;
    }

    void abandon() {
        if (status == Status.AWAITING_CHOICE || status == Status.PLAYING) {
            status = Status.ABANDONED;
        }
    }

    /** 寫進聊天紀錄的結果。只有已決定時才有內容。 */
    public String resultText() {
        if (status != Status.DECIDED) {
            return "";
        }
        return "【午餐爭奪戰結果】\n"
                + "勝者：" + winnerId + "\n"
                + "今天午餐：" + choice + "\n"
                + "由" + winnerId + "決定！";
    }
}
