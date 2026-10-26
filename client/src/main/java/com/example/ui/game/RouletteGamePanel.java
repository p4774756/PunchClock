package com.example.ui.game;

import com.example.game.GameRuleException;
import com.example.game.Roulette;
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Random;

final class RouletteGamePanel extends JPanel {

    private static final int[] WHEEL = {
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
            5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    };
    private static final Color FELT = new Color(72, 16, 32);
    private static final Color GOLD = new Color(232, 188, 104);

    private final GameHost host;
    private final Random random = new Random();
    private final JLabel balance;
    private final JSpinner bet = GameForms.betSpinner(UiFonts.latinPlain(13));
    private final JTextArea message = GameForms.message(UiFonts.chinesePlain(13));
    private final JButton spin = new JButton("旋轉");
    private final JButton back = new JButton("返回");
    private final JButton[] kinds = new JButton[Roulette.BetKind.values().length];
    private final Wheel wheel = new Wheel();
    private final Timer timer;
    private Roulette.BetKind selected = Roulette.BetKind.RED;
    private Roulette.Spin pending;
    private double angle;
    private double target;
    private int ticks;
    private boolean spinning;

    RouletteGamePanel(GameHost host) {
        super(new BorderLayout(0, 10));
        this.host = host;
        setOpaque(false);
        setBorder(new EmptyBorder(4, 4, 4, 4));
        balance = GameForms.balanceLabel(host.boldFont());
        spin.setFont(host.boldFont());
        back.setFont(host.boldFont());
        timer = new Timer(40, e -> tick());

        JPanel north = new JPanel(new BorderLayout(8, 0));
        north.setOpaque(false);
        back.addActionListener(e -> leave());
        north.add(back, BorderLayout.WEST);
        JLabel title = new JLabel("輪盤");
        title.setFont(host.boldFont());
        title.setForeground(GOLD);
        north.add(title, BorderLayout.CENTER);
        north.add(balance, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(wheel, BorderLayout.CENTER);
        center.add(GameForms.note(
                "歐式輪盤，號碼 0 到 36。可押紅、黑、單、雙、1–18、19–36。押中拿回兩倍（含下注）。0 不屬於任何一組。",
                host.mainFont(), 3), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        choices.setOpaque(false);
        Roulette.BetKind[] values = Roulette.BetKind.values();
        for (int i = 0; i < values.length; i++) {
            Roulette.BetKind kind = values[i];
            JButton button = new JButton(kind.label);
            button.setFont(host.mainFont());
            button.addActionListener(e -> choose(kind));
            kinds[i] = button;
            choices.add(button);
        }
        JPanel stake = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        stake.setOpaque(false);
        stake.add(GameForms.caption("下注", host.mainFont()));
        stake.add(bet);
        stake.add(spin);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(choices, BorderLayout.NORTH);
        south.add(stake, BorderLayout.CENTER);
        south.add(message, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        spin.addActionListener(e -> start());
        markSelection();
        refresh();
    }

    void refresh() {
        int points = host.wallet().balance();
        GameForms.showBalance(balance, points);
        boolean busy = spinning || host.wallet().roundOpen();
        spin.setEnabled(!busy && points >= 1);
        back.setEnabled(!busy);
        bet.setEnabled(!busy);
        for (JButton kind : kinds) {
            kind.setEnabled(!busy);
        }
        wheel.setAngle(angle, pending == null ? -1 : pending.number);
    }

    void onShow() {
        host.mood(GameCompanion.ENCOURAGE);
        GameForms.say(message, "先選一種押注，再按旋轉。", false);
        refresh();
    }

    private void choose(Roulette.BetKind kind) {
        if (spinning) {
            return;
        }
        selected = kind;
        markSelection();
        GameForms.say(message, "這局押" + kind.label + "。", false);
    }

    private void markSelection() {
        Roulette.BetKind[] values = Roulette.BetKind.values();
        for (int i = 0; i < values.length; i++) {
            boolean on = values[i] == selected;
            kinds[i].setText(on ? "● " + values[i].label : values[i].label);
        }
    }

    private void start() {
        if (spinning) {
            return;
        }
        try {
            int stake = GameForms.readBet(bet);
            host.wallet().begin("輪盤", stake);
            persist();
            pending = Roulette.spin(selected, random);
            int index = indexOf(pending.number);
            double slice = 360.0 / WHEEL.length;
            double landing = 90 - slice / 2 - index * slice;
            double turns = 720;
            target = angle + turns + normalize(landing - normalize(angle));
            ticks = 0;
            spinning = true;
            timer.start();
            host.mood(GameCompanion.ENCOURAGE);
            GameForms.say(message, "輪盤轉動中…", false);
            refresh();
        } catch (GameRuleException ex) {
            GameForms.say(message, ex.getMessage(), true);
        }
    }

    private void tick() {
        ticks++;
        angle = angle + (target - angle) * 0.12;
        if (ticks >= 50 || Math.abs(target - angle) < 0.4) {
            angle = target;
            timer.stop();
            spinning = false;
            int stake = host.wallet().openBet();
            int returned = pending.returned(stake);
            host.wallet().finish(returned, pending.detail);
            persist();
            host.mood(pending.win ? GameCompanion.HAPPY : GameCompanion.COMFORT);
            GameForms.say(message, pending.detail + "。", false);
            host.refreshAll();
            return;
        }
        wheel.setAngle(angle, -1);
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

    private static int indexOf(int number) {
        for (int i = 0; i < WHEEL.length; i++) {
            if (WHEEL[i] == number) {
                return i;
            }
        }
        return 0;
    }

    private static double normalize(double degrees) {
        double value = degrees % 360;
        return value < 0 ? value + 360 : value;
    }

    private static final class Wheel extends JPanel {
        private double angle;
        private int landed = -1;

        private Wheel() {
            setOpaque(true);
            setBackground(FELT);
            setPreferredSize(new Dimension(280, 250));
            setMinimumSize(new Dimension(200, 180));
        }

        void setAngle(double angle, int landed) {
            this.angle = angle;
            this.landed = landed;
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
            int size = Math.min(getWidth(), getHeight()) - 28;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2 + 6;
            double slice = 360.0 / WHEEL.length;
            for (int i = 0; i < WHEEL.length; i++) {
                int number = WHEEL[i];
                if (number == 0) {
                    g.setColor(new Color(28, 122, 78));
                } else if (Roulette.isRed(number)) {
                    g.setColor(new Color(176, 42, 58));
                } else {
                    g.setColor(new Color(28, 22, 28));
                }
                g.fillArc(x, y, size, size, (int) Math.round(angle + i * slice), (int) Math.ceil(slice) + 1);
            }
            g.setColor(GOLD);
            g.drawOval(x, y, size, size);
            g.fillOval(x + size / 2 - 28, y + size / 2 - 18, 56, 36);
            g.setColor(new Color(48, 18, 28));
            g.setFont(UiFonts.latinBold(16));
            String hub = landed < 0 ? "●" : Integer.toString(landed);
            int tx = x + size / 2 - g.getFontMetrics().stringWidth(hub) / 2;
            g.drawString(hub, tx, y + size / 2 + 6);
            int tip = x + size / 2;
            g.setColor(GOLD);
            g.fillPolygon(new int[] {tip - 8, tip + 8, tip}, new int[] {y - 4, y - 4, y + 16}, 3);
            g.dispose();
        }
    }
}
