package com.example.ui.game;

import com.example.game.Blackjack;
import com.example.game.GameRuleException;
import com.example.ui.CandyButton;
import com.example.ui.UiFonts;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class BlackjackGamePanel extends JPanel {

    private static final Color FELT = new Color(14, 78, 64);

    private final GameHost host;
    private final Random random = new Random();
    private final JLabel balance;
    private final JSpinner bet = GameForms.betSpinner(UiFonts.latinPlain(13));
    private final JTextArea message = GameForms.message(UiFonts.chinesePlain(13));
    private final JButton deal = new CandyButton("發牌", CandyButton.Variant.PRIMARY, CandyButton.Size.SMALL);
    private final JButton hit = new CandyButton("補牌", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
    private final JButton stand = new CandyButton("停牌", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
    private final JButton back = new CandyButton("返回", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
    private final Table table = new Table();
    private Blackjack hand;

    BlackjackGamePanel(GameHost host) {
        super(new BorderLayout(0, 10));
        this.host = host;
        setOpaque(false);
        setBorder(new EmptyBorder(4, 4, 4, 4));
        balance = GameForms.balanceLabel(host.boldFont());
        for (JButton button : new JButton[] {deal, hit, stand, back}) {
            button.setFont(host.boldFont());
        }

        JPanel north = new JPanel(new BorderLayout(8, 0));
        north.setOpaque(false);
        back.addActionListener(e -> leave());
        north.add(back, BorderLayout.WEST);
        JLabel title = new JLabel("21 點");
        title.setFont(host.boldFont());
        title.setForeground(new Color(90, 220, 160));
        north.add(title, BorderLayout.CENTER);
        north.add(balance, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(table, BorderLayout.CENTER);
        center.add(GameForms.note(
                "A 可當 1 或 11，J／Q／K 為 10。莊家未滿 17 會補牌。爆牌輸、點數相同算平手。兩張就 21 點是 Blackjack，以 3:2 計算，不足 1 點捨去。每局只結算一次。",
                host.mainFont(), 3), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(GameForms.caption("下注", host.mainFont()));
        controls.add(bet);
        controls.add(deal);
        controls.add(hit);
        controls.add(stand);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(controls, BorderLayout.NORTH);
        south.add(message, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        deal.addActionListener(e -> start());
        hit.addActionListener(e -> playerHit());
        stand.addActionListener(e -> playerStand());
        refresh();
    }

    void refresh() {
        int points = host.wallet().balance();
        GameForms.showBalance(balance, points);
        boolean playing = hand != null && !hand.finished();
        deal.setEnabled(!playing && points >= 1);
        deal.setText(hand != null && hand.finished() ? "再來一局" : "發牌");
        hit.setEnabled(playing);
        stand.setEnabled(playing);
        bet.setEnabled(!playing);
        back.setEnabled(!playing);
        table.show(hand);
    }

    void onShow() {
        host.mood(GameCompanion.ENCOURAGE);
        GameForms.say(message, "發牌後可以補牌或停牌。", false);
        refresh();
    }

    private void start() {
        if (hand != null && !hand.finished()) {
            return;
        }
        try {
            int stake = GameForms.readBet(bet);
            host.wallet().begin("21 點", stake);
            persist();
            hand = Blackjack.start(random);
            if (hand.finished()) {
                settle();
            } else {
                host.mood(GameCompanion.ENCOURAGE);
                GameForms.say(message, hand.summary() + "。要補牌還是停牌？", false);
                host.refreshAll();
            }
        } catch (GameRuleException ex) {
            GameForms.say(message, ex.getMessage(), true);
            refresh();
        }
    }

    private void playerHit() {
        if (hand == null || hand.finished()) {
            return;
        }
        try {
            hand.hit();
            if (hand.finished()) {
                settle();
            } else {
                GameForms.say(message, "玩家 " + hand.playerValue() + " 點。", false);
                refresh();
            }
        } catch (GameRuleException ex) {
            GameForms.say(message, ex.getMessage(), true);
        }
    }

    private void playerStand() {
        if (hand == null || hand.finished()) {
            return;
        }
        try {
            hand.stand();
            settle();
        } catch (GameRuleException ex) {
            GameForms.say(message, ex.getMessage(), true);
        }
    }

    private void settle() {
        int stake = host.wallet().openBet();
        int returned = hand.returnedFor(stake);
        host.wallet().finish(returned, hand.summary());
        persist();
        boolean won = returned > stake;
        host.mood(won ? GameCompanion.HAPPY : returned == stake ? GameCompanion.ENCOURAGE : GameCompanion.COMFORT);
        String tail = won ? "拿回 " + GameForms.points(returned) + " 點。"
                : returned == stake ? "這局點數不變。" : "這局沒有拿回點數。";
        GameForms.say(message, hand.summary() + "。" + tail, false);
        host.refreshAll();
    }

    private void persist() {
        try {
            host.store().save(host.wallet());
        } catch (GameRuleException ex) {
            GameForms.say(message, ex.getMessage(), true);
        }
    }

    private void leave() {
        if (host.wallet().roundOpen()) {
            GameForms.say(message, "這一局還沒結束", true);
            return;
        }
        host.showHub();
    }

    private static final class Table extends JPanel {
        private Blackjack hand;

        private Table() {
            setOpaque(true);
            setBackground(FELT);
            setPreferredSize(new Dimension(420, 210));
            setMinimumSize(new Dimension(280, 160));
        }

        void show(Blackjack hand) {
            this.hand = hand;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(FELT);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(UiFonts.chinesePlain(13));
            g.setColor(new Color(214, 236, 224));
            if (hand == null) {
                g.drawString("還沒發牌", 16, 28);
                g.dispose();
                return;
            }
            g.drawString("莊家  " + hand.dealerValue(), 16, 24);
            paintCards(g, hand.dealerHand(), hand.dealerHidden(), 16, 34, getWidth());
            g.setColor(new Color(214, 236, 224));
            g.drawString("玩家  " + hand.playerValue(), 16, 118);
            paintCards(g, hand.playerHand(), false, 16, 128, getWidth());
            g.dispose();
        }

        private static void paintCards(
                Graphics2D g, List<Blackjack.Card> cards, boolean hideHole, int x, int y, int maxWidth) {
            List<Blackjack.Card> shown = new ArrayList<>(cards);
            int cardW = 58;
            int cardH = 74;
            for (int i = 0; i < shown.size(); i++) {
                int left = x + i * (cardW + 8);
                if (left + cardW > maxWidth - 8) {
                    break;
                }
                g.setColor(new Color(244, 240, 232));
                g.fillRoundRect(left, y, cardW, cardH, 8, 8);
                g.setColor(new Color(40, 30, 28));
                g.drawRoundRect(left, y, cardW - 1, cardH - 1, 8, 8);
                if (hideHole && i == 1) {
                    g.setColor(new Color(92, 48, 140));
                    g.fillRoundRect(left + 4, y + 4, cardW - 8, cardH - 8, 6, 6);
                    g.setColor(new Color(255, 228, 244));
                    g.setFont(UiFonts.chineseBold(16));
                    g.drawString("蓋", left + 18, y + 44);
                    continue;
                }
                Blackjack.Card card = shown.get(i);
                g.setColor(card.red() ? new Color(176, 42, 58) : new Color(28, 24, 32));
                g.setFont(UiFonts.latinBold(16));
                g.drawString(rank(card), left + 8, y + 24);
                g.setFont(UiFonts.chinesePlain(12));
                g.drawString(suit(card), left + 8, y + 46);
            }
        }

        private static String rank(Blackjack.Card card) {
            String text = card.text();
            return text.substring(2);
        }

        private static String suit(Blackjack.Card card) {
            return card.text().substring(0, 2);
        }
    }
}
