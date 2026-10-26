package com.example.game;

/** 一局結束後留下的紀錄。欄位公開是為了寫進本機 json。 */
public final class GameRecord {

    public String at = "";
    public String game = "";
    public int bet;
    public int delta;
    public String detail = "";

    public GameRecord() {
    }

    public GameRecord(String at, String game, int bet, int delta, String detail) {
        this.at = at == null ? "" : at;
        this.game = game == null ? "" : game;
        this.bet = bet;
        this.delta = delta;
        this.detail = detail == null ? "" : detail;
    }
}
