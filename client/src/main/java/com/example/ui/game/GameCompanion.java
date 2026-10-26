package com.example.ui.game;

import com.example.ui.NightTheme;
import com.example.ui.UiFonts;
import com.example.ui.hud.AnimeCast;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 遊戲旁的助手。沿用現有角色表，沒有圖時只留文字，不另畫一個人。
 */
final class GameCompanion extends JPanel {

    static final String IDLE = "idle";
    static final String HAPPY = "happy";
    static final String ENCOURAGE = "encourage";
    static final String COMFORT = "comfort";

    private static final String[] IDLE_LINES = {
            "玩一局就好，記得起來動一動。",
            "這些點數只存在這台電腦，不能換成別的東西。",
            "今天不用急，看懂規則再開始。"
    };
    private static final String[] HAPPY_LINES = {
            "這局有點數進來了，開心一下就好。",
            "手氣不錯。下一局是另一回事。"
    };
    private static final String[] ENCOURAGE_LINES = {
            "先看剩下的點數，再決定要不要玩。",
            "慢慢來，規則都寫在旁邊。"
    };
    private static final String[] COMFORT_LINES = {
            "沒關係，這局就到這裡。要不要休息一下？",
            "輸了也只是畫面上的數字，別放在心上。"
    };

    private final AnimeCast cast = AnimeCast.load();
    private final JTextArea line = GameForms.note(" ", UiFonts.chinesePlain(13), 3);
    private final Portrait portrait = new Portrait();
    private int frame = AnimeCast.IDLE;

    GameCompanion() {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(new EmptyBorder(8, 8, 8, 4));
        setPreferredSize(new Dimension(196, 420));
        setMinimumSize(new Dimension(160, 200));
        line.setForeground(NightTheme.INK);
        add(line, BorderLayout.NORTH);
        add(portrait, BorderLayout.CENTER);
        mood(IDLE);
    }

    static List<String> lines() {
        List<String> all = new ArrayList<>();
        all.addAll(Arrays.asList(IDLE_LINES));
        all.addAll(Arrays.asList(HAPPY_LINES));
        all.addAll(Arrays.asList(ENCOURAGE_LINES));
        all.addAll(Arrays.asList(COMFORT_LINES));
        return all;
    }

    void mood(String mood) {
        String[] lines;
        if (HAPPY.equals(mood)) {
            frame = AnimeCast.SMILE;
            lines = HAPPY_LINES;
        } else if (ENCOURAGE.equals(mood)) {
            frame = AnimeCast.WAVE;
            lines = ENCOURAGE_LINES;
        } else if (COMFORT.equals(mood)) {
            frame = AnimeCast.REACT;
            lines = COMFORT_LINES;
        } else {
            frame = AnimeCast.IDLE;
            lines = IDLE_LINES;
        }
        line.setText(lines[ThreadLocalRandom.current().nextInt(lines.length)]);
        line.setCaretPosition(0);
        portrait.repaint();
    }

    private final class Portrait extends JPanel {
        private Portrait() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (cast == null || getWidth() < 40 || getHeight() < 80) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int height = Math.min(getHeight() - 8, 320);
            int width = height * cast.frameWidth() / Math.max(1, cast.frameHeight());
            if (width > getWidth() - 8) {
                width = Math.max(1, getWidth() - 8);
                height = width * cast.frameHeight() / Math.max(1, cast.frameWidth());
            }
            int x = (getWidth() - width) / 2;
            int y = getHeight() - height;
            cast.draw(g, frame, x, y, width, height);
            g.dispose();
        }
    }
}
