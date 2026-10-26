package com.example.game;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 三款遊戲共用的虛擬點數。同一時間只能有一局，結算只能發生一次。
 */
public final class GameWallet {

    public static final int STARTING_BALANCE = 10_000;
    public static final int MAX_RECORDS = 40;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private int balance = STARTING_BALANCE;
    private String openGame;
    private int openBet;
    private final List<GameRecord> records = new ArrayList<>();

    public static GameWallet fresh() {
        return new GameWallet();
    }

    public int balance() {
        return balance;
    }

    public boolean roundOpen() {
        return openGame != null;
    }

    public String openGame() {
        return openGame;
    }

    public int openBet() {
        return openBet;
    }

    public List<GameRecord> records() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    /** 扣下這一注。餘額不足、下注不合法，或上一局還沒結束時不會扣點。 */
    public void begin(String game, int bet) {
        if (roundOpen()) {
            throw new GameRuleException("這一局還沒結束");
        }
        if (game == null || game.isBlank()) {
            throw new GameRuleException("沒有指定遊戲");
        }
        if (bet < 1) {
            throw new GameRuleException("下注額至少要 1 點");
        }
        if (bet > balance) {
            throw new GameRuleException("點數不夠，現在有 " + balance + " 點");
        }
        balance -= bet;
        openGame = game;
        openBet = bet;
    }

    /**
     * 結束這一局。{@code returned} 是退回錢包的點數，含原本的下注；0 表示這注输掉。
     */
    public void finish(int returned, String detail) {
        if (!roundOpen()) {
            throw new GameRuleException("目前沒有進行中的一局");
        }
        if (returned < 0) {
            throw new GameRuleException("派彩不能是負的");
        }
        balance = add(balance, returned);
        String text = detail == null ? "" : detail.trim();
        if (text.length() > 80) {
            text = text.substring(0, 80);
        }
        records.add(0, new GameRecord(
                LocalDateTime.now().format(TIME),
                openGame,
                openBet,
                returned - openBet,
                text));
        while (records.size() > MAX_RECORDS) {
            records.remove(records.size() - 1);
        }
        openGame = null;
        openBet = 0;
    }

    /** 進行中的一局不算數，把下注退回。 */
    public void refundOpenRound(String detail) {
        if (!roundOpen()) {
            return;
        }
        finish(openBet, detail == null || detail.isBlank() ? "這局沒有完成，下注已退回" : detail);
    }

    public void reset() {
        if (roundOpen()) {
            throw new GameRuleException("這一局還沒結束，先打完再重置");
        }
        balance = STARTING_BALANCE;
        records.clear();
    }

    static GameWallet restored(int balance, List<GameRecord> saved, String openGame, int openBet) {
        GameWallet wallet = new GameWallet();
        wallet.balance = Math.max(0, balance);
        if (saved != null) {
            for (GameRecord record : saved) {
                if (record != null && wallet.records.size() < MAX_RECORDS) {
                    wallet.records.add(record);
                }
            }
        }
        if (openGame != null && !openGame.isBlank() && openBet > 0) {
            wallet.openGame = openGame;
            wallet.openBet = openBet;
            wallet.finish(openBet, "上次沒有完成，下注已退回");
        }
        return wallet;
    }

    private static int add(int left, int right) {
        long sum = (long) left + right;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (sum < 0) {
            return 0;
        }
        return (int) sum;
    }
}
