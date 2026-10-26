package com.example.ui.game;

import com.example.game.GameRuleException;
import com.example.game.SlotMachine;
import com.example.ui.NightTheme;
import com.example.ui.UiFonts;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Random;

final class SlotGamePanel extends JPanel {

    private static final Color FELT = new Color(46, 18, 58);
    private static final Color GOLD = new Color(255, 206, 120);
    private static final int[] STOP_AT = {8, 14, 22};

    private final GameHost host;
    private final Random random = new Random();
    private final JLabel balance;
    private final JSpinner bet = GameForms.betSpinner(UiFonts.latinPlain(13));
    private final JTextArea message = GameForms.message(UiFonts.chinesePlain(13));
    private final JButton spin = new JButton("開始");
    private final JButton back = new JButton("返回");
    private final JButton[] chips = chipButtons();
    private final Reels reels = new Reels();
    private final Timer timer;
    private SlotMachine.Result pending;
    private int ticks;
    private boolean spinning;

    SlotGamePanel(GameHost host) {
        super(new BorderLayout(0, 10));
        this.host = host;
        setOpaque(false);
        setBorder(new EmptyBorder(4, 4, 4, 4));
        balance = GameForms.balanceLabel(host.boldFont());
        Font buttonFont = host.boldFont();
        spin.setFont(buttonFont);
        back.setFont(buttonFont);
        timer = new Timer(70, e -> tick());
        timer.setRepeats(true);

        JPanel north = new JPanel(new BorderLayout(8, 0));
        north.setOpaque(false);
        back.addActionListener(e -> leave());
        north.add(back, BorderLayout.WEST);
        JLabel title = new JLabel("老虎機");
        title.setFont(host.boldFont());
        title.setForeground(GOLD);
        north.add(title, BorderLayout.CENTER);
        north.add(balance, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(reels, BorderLayout.CENTER);
        center.add(GameForms.note(
                "三個相同：紅七 ×20、星星 ×10、鈴鐺 ×6、檸檬 ×4、櫻桃 ×3。剛好兩個櫻桃 ×2。其他沒有點數。倍數含原本的下注。",
                host.mainFont(), 3), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(GameForms.caption("下注", host.mainFont()));
        for (JButton chip : chips) {
            controls.add(chip);
        }
        controls.add(bet);
        controls.add(spin);
        south.add(controls, BorderLayout.NORTH);
        south.add(message, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        spin.addActionListener(e -> start());
        refresh();
    }

    void refresh() {
        int points = host.wallet().balance();
        GameForms.showBalance(balance, points);
        boolean busy = spinning || host.wallet().roundOpen();
        spin.setEnabled(!busy && points >= 1);
        back.setEnabled(!busy);
        bet.setEnabled(!busy);
        for (JButton chip : chips) {
            chip.setEnabled(!busy);
        }
    }

    void onShow() {
        host.mood(GameCompanion.ENCOURAGE);
        GameForms.say(message, "按開始後三軸會轉一下，停下來才結算。", false);
        refresh();
    }

    private void start() {
        if (spinning) {
            return;
        }
        try {
            int stake = GameForms.readBet(bet);
            host.wallet().begin("老虎機", stake);
            persist();
            pending = SlotMachine.spin(random);
            ticks = 0;
            spinning = true;
            reels.use(new String[] {"？", "？", "？"});
            timer.start();
            host.mood(GameCompanion.ENCOURAGE);
            GameForms.say(message, "轉動中…", false);
            refresh();
        } catch (GameRuleException ex) {
            GameForms.say(message, ex.getMessage(), true);
        }
    }

    private void tick() {
        ticks++;
        String[] shown = new String[3];
        boolean done = true;
        for (int i = 0; i < 3; i++) {
            if (ticks >= STOP_AT[i]) {
                shown[i] = pending.reels[i];
            } else {
                shown[i] = SlotMachine.SYMBOLS[random.nextInt(SlotMachine.SYMBOLS.length)];
                done = false;
            }
        }
        reels.use(shown);
        if (!done) {
            return;
        }
        timer.stop();
        spinning = false;
        int stake = host.wallet().openBet();
        int returned = pending.returned(stake);
        String detail = SlotMachine.describe(pending.reels, pending.multiplier);
        host.wallet().finish(returned, detail);
        persist();
        host.mood(returned > stake ? GameCompanion.HAPPY : GameCompanion.COMFORT);
        GameForms.say(message, detail + "。" + (returned > stake ? "這局拿回 " + GameForms.points(returned) + " 點。" : "這局沒有拿回點數。"), false);
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
        if (spinning || host.wallet().roundOpen()) {
            GameForms.say(message, "這一局還沒結束", true);
            return;
        }
        timer.stop();
        host.showHub();
    }

    private JButton[] chipButtons() {
        int[] amounts = {10, 50, 100, 500};
        JButton[] buttons = new JButton[amounts.length];
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i];
            JButton button = new JButton(Integer.toString(amount));
            button.setFont(UiFonts.latinPlain(13));
            button.addActionListener(e -> {
                if (amount > host.wallet().balance()) {
                    GameForms.say(message, "點數不夠選 " + amount, true);
                    return;
                }
                bet.setValue(amount);
            });
            buttons[i] = button;
        }
        return buttons;
    }

    private static final class Reels extends JPanel {
        private String[] symbols = {"櫻桃", "檸檬", "鈴鐺"};

        private Reels() {
            setOpaque(true);
            setBackground(FELT);
            setPreferredSize(new Dimension(360, 132));
            setMinimumSize(new Dimension(240, 96));
        }

        void use(String[] symbols) {
            this.symbols = symbols.clone();
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
            int gap = 12;
            int width = Math.max(40, (getWidth() - gap * 4) / 3);
            int height = Math.max(48, getHeight() - gap * 2);
            g.setFont(UiFonts.chineseBold(Math.min(28, height / 3)));
            for (int i = 0; i < 3; i++) {
                int x = gap + i * (width + gap);
                g.setColor(new Color(28, 10, 40));
                g.fillRoundRect(x, gap, width, height, 16, 16);
                g.setColor(GOLD);
                g.drawRoundRect(x, gap, width - 1, height - 1, 16, 16);
                String text = symbols[i];
                int tx = x + Math.max(8, (width - g.getFontMetrics().stringWidth(text)) / 2);
                int ty = gap + (height + g.getFontMetrics().getAscent()) / 2 - 4;
                g.setColor(NightTheme.INK);
                g.drawString(text, tx, ty);
            }
            g.dispose();
        }
    }
}
