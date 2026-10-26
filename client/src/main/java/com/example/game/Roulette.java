package com.example.game;

import java.util.Random;

/** 單零歐式輪盤。0 不屬於紅黑、單雙或大小。 */
public final class Roulette {

    public static final int[] RED_NUMBERS = {
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36
    };

    public enum BetKind {
        RED("紅色"),
        BLACK("黑色"),
        ODD("單數"),
        EVEN("雙數"),
        LOW("1–18"),
        HIGH("19–36");

        public final String label;

        BetKind(String label) {
            this.label = label;
        }
    }

    public static final class Spin {
        public final int number;
        public final boolean win;
        public final String detail;

        Spin(int number, boolean win, String detail) {
            this.number = number;
            this.win = win;
            this.detail = detail;
        }

        public int returned(int bet) {
            if (bet < 1) {
                throw new GameRuleException("下注額至少要 1 點");
            }
            return win ? bet * 2 : 0;
        }
    }

    private Roulette() {
    }

    public static Spin spin(BetKind kind, Random random) {
        if (kind == null) {
            throw new GameRuleException("先選一種押注");
        }
        int number = random.nextInt(37);
        boolean win = wins(kind, number);
        return new Spin(number, win, describe(kind, number, win));
    }

    public static boolean wins(BetKind kind, int number) {
        if (kind == null || number < 0 || number > 36) {
            return false;
        }
        if (number == 0) {
            return false;
        }
        switch (kind) {
            case RED:
                return isRed(number);
            case BLACK:
                return !isRed(number);
            case ODD:
                return number % 2 == 1;
            case EVEN:
                return number % 2 == 0;
            case LOW:
                return number <= 18;
            case HIGH:
                return number >= 19;
            default:
                return false;
        }
    }

    public static boolean isRed(int number) {
        for (int red : RED_NUMBERS) {
            if (red == number) {
                return true;
            }
        }
        return false;
    }

    public static String colorName(int number) {
        if (number == 0) {
            return "綠";
        }
        return isRed(number) ? "紅" : "黑";
    }

    private static String describe(BetKind kind, int number, boolean win) {
        String outcome = win ? "押中，拿回兩倍" : "沒中";
        if (number == 0) {
            outcome = "0 不屬於" + kind.label + "，沒中";
        }
        return "開出 " + number + "（" + colorName(number) + "），押" + kind.label + "，" + outcome;
    }
}
