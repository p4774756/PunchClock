package com.example.ui.game;

import com.example.game.GameRecord;
import com.example.game.GameRuleException;
import com.example.game.GameStore;
import com.example.game.GameWallet;
import com.example.ui.NightTheme;
import com.example.ui.UiFonts;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/** 遊戲中心：點數、三款遊戲與紀錄。點數只存在本機。 */
public final class GameCenterPanel extends JPanel implements GameHost {

    private final GameStore store;
    private final GameWallet wallet;
    private final Font mainFont;
    private final Font boldFont;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final GameCompanion companion = new GameCompanion();
    private final Hub hub;
    private final SlotGamePanel slot;
    private final BlackjackGamePanel blackjack;
    private final RouletteGamePanel roulette;

    public GameCenterPanel(GameStore store, Font mainFont, Font boldFont) {
        super(new BorderLayout(12, 0));
        this.store = store;
        this.wallet = store.load();
        this.mainFont = mainFont;
        this.boldFont = boldFont;
        setOpaque(false);
        hub = new Hub();
        slot = new SlotGamePanel(this);
        blackjack = new BlackjackGamePanel(this);
        roulette = new RouletteGamePanel(this);
        deck.setOpaque(false);
        deck.add(hub, "hub");
        deck.add(slot, "slot");
        deck.add(blackjack, "blackjack");
        deck.add(roulette, "roulette");
        add(deck, BorderLayout.CENTER);
        add(companion, BorderLayout.EAST);
        refreshAll();
        mood(GameCompanion.IDLE);
    }

    @Override
    public GameWallet wallet() {
        return wallet;
    }

    @Override
    public GameStore store() {
        return store;
    }

    @Override
    public Font mainFont() {
        return mainFont;
    }

    @Override
    public Font boldFont() {
        return boldFont;
    }

    @Override
    public void showHub() {
        cards.show(deck, "hub");
        mood(GameCompanion.IDLE);
        refreshAll();
    }

    @Override
    public void refreshAll() {
        hub.refresh();
        slot.refresh();
        blackjack.refresh();
        roulette.refresh();
    }

    @Override
    public void mood(String mood) {
        companion.mood(mood);
    }

    @Override
    public void doLayout() {
        boolean show = getWidth() >= 880;
        if (companion.isVisible() != show) {
            companion.setVisible(show);
        }
        super.doLayout();
    }

    private void open(String card) {
        cards.show(deck, card);
        if ("slot".equals(card)) {
            slot.onShow();
        } else if ("blackjack".equals(card)) {
            blackjack.onShow();
        } else {
            roulette.onShow();
        }
    }

    private final class Hub extends JPanel {
        private final JLabel balance = GameForms.balanceLabel(boldFont);
        private final JTextArea notice = GameForms.message(mainFont);
        private final DefaultListModel<String> records = new DefaultListModel<>();
        private final JButton reset = new JButton("重置點數");

        private Hub() {
            super(new BorderLayout(0, 10));
            setOpaque(false);
            setBorder(new EmptyBorder(4, 4, 4, 4));
            JLabel title = new JLabel("遊戲中心");
            title.setFont(UiFonts.chineseBold(20));
            title.setForeground(NightTheme.INK);
            JTextArea intro = GameForms.note(
                    "老虎機、21 點和輪盤共用同一份點數。一開始有 10,000 點，只存在這台電腦，不能充值、提領或兌換。",
                    mainFont, 2);
            JPanel heading = new JPanel(new BorderLayout(0, 4));
            heading.setOpaque(false);
            heading.add(title, BorderLayout.NORTH);
            heading.add(intro, BorderLayout.CENTER);

            reset.setFont(boldFont);
            reset.addActionListener(e -> confirmReset());
            JPanel balanceRow = new JPanel(new BorderLayout(8, 0));
            balanceRow.setOpaque(false);
            balanceRow.add(balance, BorderLayout.WEST);
            balanceRow.add(reset, BorderLayout.EAST);

            JPanel games = new JPanel(new GridLayout(1, 3, 10, 0));
            games.setOpaque(false);
            games.add(entry("老虎機", new Color(255, 206, 120),
                    "三軸符號，對照固定獎勵表。", "slot"));
            games.add(entry("21 點", new Color(90, 220, 160),
                    "單人對莊家。A 可當 1 或 11。", "blackjack"));
            games.add(entry("輪盤", new Color(232, 188, 104),
                    "0 到 36。0 不屬於紅、黑或單雙。", "roulette"));

            JList<String> list = new JList<>(records);
            list.setFont(mainFont);
            list.setBackground(NightTheme.FIELD);
            list.setForeground(NightTheme.INK);
            list.setSelectionBackground(NightTheme.SELECT);
            list.setSelectionForeground(NightTheme.PINK_INK);
            JScrollPane scroll = new JScrollPane(list);
            scroll.setBorder(BorderFactory.createLineBorder(NightTheme.EDGE));
            scroll.setPreferredSize(new Dimension(200, 132));
            JPanel history = new JPanel(new BorderLayout(0, 4));
            history.setOpaque(false);
            JLabel historyTitle = new JLabel("最近紀錄");
            historyTitle.setFont(boldFont);
            historyTitle.setForeground(NightTheme.INK);
            history.add(historyTitle, BorderLayout.NORTH);
            history.add(scroll, BorderLayout.CENTER);

            JPanel body = new JPanel(new BorderLayout(0, 10));
            body.setOpaque(false);
            body.add(balanceRow, BorderLayout.NORTH);
            body.add(games, BorderLayout.CENTER);
            body.add(notice, BorderLayout.SOUTH);

            add(heading, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
            add(history, BorderLayout.SOUTH);
        }

        void refresh() {
            GameForms.showBalance(balance, wallet.balance());
            reset.setEnabled(!wallet.roundOpen());
            records.clear();
            if (wallet.records().isEmpty()) {
                records.addElement("還沒有紀錄");
                return;
            }
            for (GameRecord record : wallet.records()) {
                String delta = (record.delta > 0 ? "+" : "") + record.delta;
                records.addElement(record.at + "  " + record.game + "  " + delta + "  " + record.detail);
            }
        }

        private JPanel entry(String name, Color accent, String blurb, String card) {
            JPanel panel = new JPanel(new BorderLayout(0, 6));
            panel.setOpaque(true);
            panel.setBackground(NightTheme.HEADER);
            panel.setBorder(new EmptyBorder(12, 12, 12, 12));
            JLabel label = new JLabel(name);
            label.setFont(boldFont);
            label.setForeground(accent);
            JTextArea text = GameForms.note(blurb, mainFont, 3);
            JButton open = new JButton("進入");
            open.setFont(boldFont);
            open.addActionListener(e -> open(card));
            panel.add(label, BorderLayout.NORTH);
            panel.add(text, BorderLayout.CENTER);
            panel.add(open, BorderLayout.SOUTH);
            return panel;
        }

        private void confirmReset() {
            int answer = UiFonts.showConfirm(
                    this,
                    "點數會回到 10,000，遊戲紀錄也會清掉。\n這只是這台電腦上的娛樂點數。",
                    "重置點數",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                wallet.reset();
                store.save(wallet);
                GameForms.say(notice, "已重置為 10,000 點。", false);
                mood(GameCompanion.IDLE);
                refreshAll();
            } catch (GameRuleException ex) {
                GameForms.say(notice, ex.getMessage(), true);
            }
        }
    }
}
