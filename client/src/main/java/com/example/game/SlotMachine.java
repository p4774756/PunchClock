package com.example.game;

import java.util.Random;

/** 三軸老虎機。結果由權重抽出，獎勵對照固定表。 */
public final class SlotMachine {

    public static final String[] SYMBOLS = {"櫻桃", "檸檬", "鈴鐺", "星星", "紅七"};
    /** 對應 SYMBOLS，加總 100。 */
    public static final int[] WEIGHTS = {40, 28, 16, 10, 6};

    private SlotMachine() {
    }

    public static final class Result {
        public final String[] reels;
        public final int multiplier;

        Result(String[] reels, int multiplier) {
            this.reels = reels;
            this.multiplier = multiplier;
        }

        public int returned(int bet) {
            if (bet < 1) {
                throw new GameRuleException("下注額至少要 1 點");
            }
            return bet * multiplier;
        }
    }

    public static Result spin(Random random) {
        String[] reels = new String[3];
        for (int i = 0; i < reels.length; i++) {
            reels[i] = pick(random);
        }
        return new Result(reels, multiplier(reels));
    }

    /** 三個相同，或剛好兩個櫻桃。倍數含原本的下注，0 表示沒有退回。 */
    public static int multiplier(String[] reels) {
        if (reels == null || reels.length != 3) {
            throw new GameRuleException("老虎機需要三個符號");
        }
        if (reels[0].equals(reels[1]) && reels[1].equals(reels[2])) {
            switch (reels[0]) {
                case "紅七":
                    return 20;
                case "星星":
                    return 10;
                case "鈴鐺":
                    return 6;
                case "檸檬":
                    return 4;
                case "櫻桃":
                    return 3;
                default:
                    return 0;
            }
        }
        int cherries = 0;
        for (String reel : reels) {
            if ("櫻桃".equals(reel)) {
                cherries++;
            }
        }
        return cherries == 2 ? 2 : 0;
    }

    public static String describe(String[] reels, int multiplier) {
        String shown = reels[0] + "、" + reels[1] + "、" + reels[2];
        if (multiplier <= 0) {
            return shown + "，沒有對中";
        }
        return shown + "，×" + multiplier;
    }

    private static String pick(Random random) {
        int roll = random.nextInt(100);
        int passed = 0;
        for (int i = 0; i < SYMBOLS.length; i++) {
            passed += WEIGHTS[i];
            if (roll < passed) {
                return SYMBOLS[i];
            }
        }
        return SYMBOLS[0];
    }
}
