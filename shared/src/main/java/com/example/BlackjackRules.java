package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 兩人互拚的 21 點規則（無莊家）。牌序由 seed 決定，雙方各自重播同一副牌。
 */
public final class BlackjackRules {

    public static final int DECK_SIZE = 52;

    private BlackjackRules() {
    }

    /** 一張牌：0–51。 */
    public static final class Card {
        public final int index;

        public Card(int index) {
            if (index < 0 || index >= DECK_SIZE) {
                throw new IllegalArgumentException("card index out of range: " + index);
            }
            this.index = index;
        }

        /** A,2–10,J,Q,K */
        public String rank() {
            int r = index % 13;
            if (r == 0) {
                return "A";
            }
            if (r <= 9) {
                return String.valueOf(r + 1);
            }
            if (r == 10) {
                return "J";
            }
            if (r == 11) {
                return "Q";
            }
            return "K";
        }

        /** ♠♥♦♣ */
        public String suitSymbol() {
            switch (index / 13) {
                case 0:
                    return "♠";
                case 1:
                    return "♥";
                case 2:
                    return "♦";
                default:
                    return "♣";
            }
        }

        public boolean isRed() {
            int suit = index / 13;
            return suit == 1 || suit == 2;
        }

        /** 例如 A♠、10♥ */
        public String label() {
            return rank() + suitSymbol();
        }

        /** 硬點數（A 先當 11；若爆再把 A 當 1 的計算在 {@link #handValue}）。 */
        int baseValue() {
            int r = index % 13;
            if (r == 0) {
                return 11;
            }
            if (r >= 10) {
                return 10;
            }
            return r + 1;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Card && ((Card) obj).index == index;
        }

        @Override
        public int hashCode() {
            return index;
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /** 用 seed 洗出一副牌（Fisher–Yates）。 */
    public static List<Card> shuffledDeck(long seed) {
        List<Card> deck = new ArrayList<>(DECK_SIZE);
        for (int i = 0; i < DECK_SIZE; i++) {
            deck.add(new Card(i));
        }
        Random random = new Random(seed);
        for (int i = DECK_SIZE - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(deck, i, j);
        }
        return deck;
    }

    /** 手牌點數；A 可當 1 或 11，取不爆的最大值。 */
    public static int handValue(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        int total = 0;
        int aces = 0;
        for (Card card : cards) {
            total += card.baseValue();
            if (card.index % 13 == 0) {
                aces++;
            }
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    public static boolean isBust(List<Card> cards) {
        return handValue(cards) > 21;
    }

    /**
     * 比大小：正數＝a 贏，負數＝b 贏，0＝平手。
     * 爆掉的一方輸；雙方都爆則平手。
     */
    public static int compare(List<Card> a, List<Card> b) {
        boolean aBust = isBust(a);
        boolean bBust = isBust(b);
        if (aBust && bBust) {
            return 0;
        }
        if (aBust) {
            return -1;
        }
        if (bBust) {
            return 1;
        }
        return Integer.compare(handValue(a), handValue(b));
    }

    /** 手牌顯示，例如「A♠ 10♥（21）」 */
    public static String formatHand(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return "（無）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(cards.get(i).label());
        }
        sb.append("（").append(handValue(cards)).append('）');
        return sb.toString();
    }
}
