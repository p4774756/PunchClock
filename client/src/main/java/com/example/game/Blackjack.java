package com.example.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 單人 21 點。A 可當 1 或 11，J／Q／K 為 10，莊家未滿 17 補牌。 */
public final class Blackjack {

    public enum Outcome {
        PLAYER_BUST,
        DEALER_BUST,
        PLAYER_WIN,
        DEALER_WIN,
        PUSH,
        PLAYER_BLACKJACK,
        DEALER_BLACKJACK
    }

    public static final class Card {
        public final int rank;
        public final int suit;

        public Card(int rank, int suit) {
            if (rank < 1 || rank > 13 || suit < 0 || suit > 3) {
                throw new GameRuleException("牌面不正確");
            }
            this.rank = rank;
            this.suit = suit;
        }

        public String text() {
            return SUITS[suit] + RANKS[rank];
        }

        public boolean red() {
            return suit == 1 || suit == 2;
        }
    }

    private static final String[] SUITS = {"黑桃", "紅心", "方塊", "梅花"};
    private static final String[] RANKS = {
            "", "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"
    };

    private final ArrayDeque<Card> deck;
    private final List<Card> player = new ArrayList<>();
    private final List<Card> dealer = new ArrayList<>();
    private boolean dealerHidden = true;
    private boolean finished;
    private Outcome outcome;

    private Blackjack(ArrayDeque<Card> deck) {
        this.deck = deck;
        player.add(draw());
        dealer.add(draw());
        player.add(draw());
        dealer.add(draw());
        resolveOpening();
    }

    public static Blackjack start(Random random) {
        List<Card> cards = new ArrayList<>();
        for (int suit = 0; suit < 4; suit++) {
            for (int rank = 1; rank <= 13; rank++) {
                cards.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(cards, random);
        return new Blackjack(new ArrayDeque<>(cards));
    }

    /** 牌由左到右依序發出，方便測試固定牌序。 */
    public static Blackjack start(List<Card> topFirst) {
        return new Blackjack(new ArrayDeque<>(topFirst));
    }

    public void hit() {
        if (finished) {
            throw new GameRuleException("這局已經結束");
        }
        player.add(draw());
        if (handValue(player) > 21) {
            dealerHidden = false;
            finish(Outcome.PLAYER_BUST);
        }
    }

    public void stand() {
        if (finished) {
            throw new GameRuleException("這局已經結束");
        }
        dealerHidden = false;
        while (handValue(dealer) < 17) {
            dealer.add(draw());
        }
        int playerValue = handValue(player);
        int dealerValue = handValue(dealer);
        if (dealerValue > 21) {
            finish(Outcome.DEALER_BUST);
        } else if (playerValue > dealerValue) {
            finish(Outcome.PLAYER_WIN);
        } else if (playerValue < dealerValue) {
            finish(Outcome.DEALER_WIN);
        } else {
            finish(Outcome.PUSH);
        }
    }

    public boolean finished() {
        return finished;
    }

    public boolean dealerHidden() {
        return dealerHidden;
    }

    public List<Card> playerHand() {
        return Collections.unmodifiableList(player);
    }

    public List<Card> dealerHand() {
        return Collections.unmodifiableList(dealer);
    }

    public Outcome outcome() {
        return outcome;
    }

    public int playerValue() {
        return handValue(player);
    }

    public int dealerValue() {
        if (dealerHidden && dealer.size() > 1) {
            return handValue(dealer.subList(0, 1));
        }
        return handValue(dealer);
    }

    /** 退回錢包的點數。Blackjack 以 3:2 計算，不足 1 點捨去。 */
    public int returnedFor(int bet) {
        if (!finished) {
            throw new GameRuleException("這局還沒結束");
        }
        if (bet < 1) {
            throw new GameRuleException("下注額至少要 1 點");
        }
        switch (outcome) {
            case PUSH:
                return bet;
            case PLAYER_WIN:
            case DEALER_BUST:
                return bet * 2;
            case PLAYER_BLACKJACK:
                return bet * 5 / 2;
            default:
                return 0;
        }
    }

    public String summary() {
        if (!finished) {
            return "玩家 " + playerValue() + "，莊家 " + dealerValue();
        }
        switch (outcome) {
            case PLAYER_BUST:
                return "玩家爆牌 " + playerValue();
            case DEALER_BUST:
                return "莊家爆牌 " + dealerValue() + "，玩家 " + playerValue();
            case PLAYER_BLACKJACK:
                return "Blackjack";
            case DEALER_BLACKJACK:
                return "莊家 Blackjack";
            case PUSH:
                return "平手 " + playerValue();
            case PLAYER_WIN:
                return "玩家 " + playerValue() + "，莊家 " + dealerValue();
            case DEALER_WIN:
                return "莊家 " + dealerValue() + "，玩家 " + playerValue();
            default:
                return "";
        }
    }

    /** A 先算 11，超過 21 再改算 1。 */
    public static int handValue(List<Card> cards) {
        int total = 0;
        int aces = 0;
        for (Card card : cards) {
            if (card.rank == 1) {
                aces++;
                total += 11;
            } else {
                total += Math.min(card.rank, 10);
            }
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    private void resolveOpening() {
        boolean playerBlackjack = player.size() == 2 && handValue(player) == 21;
        boolean dealerBlackjack = dealer.size() == 2 && handValue(dealer) == 21;
        if (!playerBlackjack && !dealerBlackjack) {
            return;
        }
        dealerHidden = false;
        if (playerBlackjack && dealerBlackjack) {
            finish(Outcome.PUSH);
        } else if (playerBlackjack) {
            finish(Outcome.PLAYER_BLACKJACK);
        } else {
            finish(Outcome.DEALER_BLACKJACK);
        }
    }

    private void finish(Outcome outcome) {
        this.outcome = outcome;
        this.finished = true;
    }

    private Card draw() {
        Card card = deck.pollFirst();
        if (card == null) {
            throw new GameRuleException("牌組不足");
        }
        return card;
    }
}
