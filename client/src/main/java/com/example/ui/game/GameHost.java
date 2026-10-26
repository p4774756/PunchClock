package com.example.ui.game;

import com.example.game.GameStore;
import com.example.game.GameWallet;

import java.awt.Font;

/** 遊戲畫面跟遊戲中心交換錢包、紀錄與角色情緒。 */
interface GameHost {

    GameWallet wallet();

    GameStore store();

    Font mainFont();

    Font boldFont();

    void showHub();

    void refreshAll();

    void mood(String mood);
}
