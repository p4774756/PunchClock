package com.example.ui.hud;

import com.example.ui.MacGraphicsWorkaround;
import com.example.ui.UiFonts;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 遊戲風主畫面示範。不接排程、聊天、傳檔，只用來確認視覺方向。
 */
public final class HudDemo extends JFrame {

    public HudDemo() {
        super("主畫面示範");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(new Stage());
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(980, 640));
    }

    public static void main(String[] args) {
        MacGraphicsWorkaround.apply();
        SwingUtilities.invokeLater(() -> {
            HudDemo demo = new HudDemo();
            demo.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            demo.setVisible(true);
        });
    }

    /** 可單獨畫出來看的畫面。 */
    public static final class Stage extends JPanel {

        enum Mood {
            IDLE("待命", "排程設好就會自己跑"),
            WAITING("等待中", "時間到了會自己去點"),
            WORKING("執行中", "正在幫你打卡"),
            DONE("完成了", "打卡成功"),
            FAILED("沒打成", "看一下日誌");

            final String title;
            final String detail;

            Mood(String title, String detail) {
                this.title = title;
                this.detail = detail;
            }
        }

        private static final Color BG_TOP = new Color(28, 22, 48);
        private static final Color BG_BOTTOM = new Color(16, 12, 28);
        private static final Color CARD = new Color(36, 28, 58);
        private static final Color CARD_EDGE = new Color(78, 64, 112);
        private static final Color INK = new Color(244, 239, 230);
        private static final Color MUTED = new Color(176, 164, 196);
        private static final Color PINK = new Color(244, 143, 177);
        private static final Color BLUE = new Color(126, 200, 227);
        private static final Color ORANGE = new Color(255, 138, 91);
        private static final Color MINT = new Color(126, 214, 168);
        private static final int TOTAL_SECONDS = 90;

        private Mood mood = Mood.IDLE;
        private double phase;
        private double remaining = TOTAL_SECONDS;
        private boolean counting = true;
        private int hot = -1;
        private int pressed = -1;
        private final List<Bit> bits = new ArrayList<>();
        private final Random random = new Random();
        private final Timer timer;
        private BufferedImage portrait;
        private boolean portraitMissing;

        public Stage() {
            setOpaque(true);
            setBackground(BG_BOTTOM);
            setPreferredSize(new Dimension(1100, 700));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    setHot(hit(e.getX(), e.getY()));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setHot(-1);
                    pressed = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = hit(e.getX(), e.getY());
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    int id = hit(e.getX(), e.getY());
                    if (id >= 0 && id == pressed) {
                        activate(id);
                    }
                    pressed = -1;
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            timer = new Timer(32, e -> tick(0.032));
        }

        public void setMood(Mood mood) {
            this.mood = mood == null ? Mood.IDLE : mood;
            repaint();
        }

        public void tick(double dt) {
            if (dt <= 0) {
                return;
            }
            phase += dt * 2.6;
            if (counting && remaining > 0) {
                remaining = Math.max(0, remaining - dt);
                if (remaining == 0) {
                    counting = false;
                    mood = Mood.DONE;
                    Frame frame = layout(getWidth(), getHeight());
                    spawn(frame.ring.x + frame.ring.width / 2f, frame.ring.y + frame.ring.height / 2f);
                }
            }
            for (int i = bits.size() - 1; i >= 0; i--) {
                Bit bit = bits.get(i);
                bit.life -= (float) dt;
                if (bit.life <= 0) {
                    bits.remove(i);
                    continue;
                }
                bit.vy += 520f * (float) dt;
                bit.x += bit.vx * (float) dt;
                bit.y += bit.vy * (float) dt;
            }
            repaint();
        }

        @Override
        public void addNotify() {
            super.addNotify();
            timer.start();
        }

        @Override
        public void removeNotify() {
            timer.stop();
            super.removeNotify();
        }

        private void setHot(int id) {
            if (hot == id) {
                return;
            }
            hot = id;
            setCursor(id >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            repaint();
        }

        private void activate(int id) {
            if (id >= 0 && id < Mood.values().length) {
                mood = Mood.values()[id];
                return;
            }
            Frame frame = layout(getWidth(), getHeight());
            if (id == 5) {
                mood = Mood.DONE;
                counting = false;
                spawn(frame.complete.x + frame.complete.width / 2f, frame.complete.y + frame.complete.height / 2f);
            } else if (id == 6) {
                remaining = TOTAL_SECONDS;
                counting = true;
                mood = Mood.WAITING;
                bits.clear();
            }
        }

        private void spawn(float x, float y) {
            bits.clear();
            Color[] palette = {PINK, ORANGE, BLUE, MINT, INK};
            for (int i = 0; i < 40; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double speed = 90 + random.nextDouble() * 240;
                Bit bit = new Bit();
                bit.x = x;
                bit.y = y;
                bit.vx = (float) (Math.cos(angle) * speed);
                bit.vy = (float) (Math.sin(angle) * speed - 160);
                bit.life = 0.75f + random.nextFloat() * 0.35f;
                bit.max = bit.life;
                bit.color = palette[i % palette.length];
                bit.size = 3f + random.nextFloat() * 4f;
                bits.add(bit);
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[] {0f, 1f}, new Color[] {BG_TOP, BG_BOTTOM}));
            g.fillRect(0, 0, w, h);

            Frame frame = layout(w, h);
            paintTop(g, frame);
            paintCard(g, frame.mascot);
            paintCard(g, frame.task);
            paintCard(g, frame.ring);
            paintCard(g, frame.hint);
            paintMascot(g, frame.mascot);
            paintTask(g, frame);
            paintRing(g, frame);
            paintMoods(g, frame);
            paintHint(g, frame.hint);
            paintBits(g);
            g.dispose();
        }

        private void paintTop(Graphics2D g, Frame frame) {
            paintCard(g, frame.top);
            g.setColor(PINK);
            g.fillOval(frame.top.x + 18, frame.top.y + 20, 16, 16);
            g.setFont(UiFonts.chineseBold(18));
            g.setColor(INK);
            g.drawString("主畫面示範", frame.top.x + 44, frame.top.y + 34);
            String pill = mood.title;
            g.setFont(UiFonts.chineseBold(13));
            FontMetrics metrics = g.getFontMetrics();
            int pillW = metrics.stringWidth(pill) + 28;
            int pillX = frame.top.x + frame.top.width - pillW - 16;
            int pillY = frame.top.y + 14;
            g.setColor(new Color(58, 46, 88));
            g.fillRoundRect(pillX, pillY, pillW, 28, 14, 14);
            g.setColor(moodColor());
            g.fillOval(pillX + 10, pillY + 10, 8, 8);
            g.setColor(INK);
            g.drawString(pill, pillX + 22, pillY + 19);
        }

        private void paintMascot(Graphics2D g, java.awt.Rectangle card) {
            Graphics2D clip = (Graphics2D) g.create();
            clip.setClip(new RoundRectangle2D.Float(card.x, card.y, card.width, card.height, 18, 18));
            BufferedImage image = portrait();
            if (image != null) {
                double bob = Math.sin(phase * (mood == Mood.WORKING ? 2.4 : 1.1)) * (mood == Mood.WORKING ? 4 : 2);
                double scale = Math.max(card.width / (double) image.getWidth(), card.height / (double) image.getHeight()) * 1.08;
                int dw = (int) Math.ceil(image.getWidth() * scale);
                int dh = (int) Math.ceil(image.getHeight() * scale);
                int dx = card.x + (card.width - dw) / 2;
                int dy = card.y + (card.height - dh) / 2 + (int) bob;
                clip.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                clip.drawImage(image, dx, dy, dw, dh, null);
            }
            clip.setPaint(new GradientPaint(0, card.y + card.height - 78, new Color(16, 12, 28, 0),
                    0, card.y + card.height, new Color(16, 12, 28, 220)));
            clip.fillRect(card.x, card.y + card.height - 78, card.width, 78);
            clip.dispose();
            g.setColor(moodColor());
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new RoundRectangle2D.Float(card.x + 1.5f, card.y + 1.5f, card.width - 3, card.height - 3, 16, 16));
            g.setFont(UiFonts.chineseBold(16));
            g.setColor(INK);
            drawCentered(g, mood.title, card.x, card.y + card.height - 48, card.width);
            g.setFont(UiFonts.chinesePlain(12));
            g.setColor(new Color(220, 210, 230));
            drawCentered(g, mood.detail, card.x, card.y + card.height - 26, card.width);
        }

        private BufferedImage portrait() {
            if (portrait != null || portraitMissing) {
                return portrait;
            }
            try (InputStream in = HudDemo.class.getResourceAsStream("/hud/companion.jpg")) {
                if (in == null) {
                    portraitMissing = true;
                    return null;
                }
                portrait = ImageIO.read(in);
            } catch (IOException ex) {
                portraitMissing = true;
            }
            if (portrait == null) {
                portraitMissing = true;
            }
            return portrait;
        }

        private void paintTask(Graphics2D g, Frame frame) {
            java.awt.Rectangle card = frame.task;
            g.setFont(UiFonts.chineseBold(18));
            g.setColor(INK);
            g.drawString("今天的任務", card.x + 20, card.y + 40);
            g.setFont(UiFonts.chinesePlain(13));
            g.setColor(MUTED);
            g.drawString("完成時會跳一下", card.x + 20, card.y + 64);
            String[] rows = {"上班打卡", "跟同事說一聲", "傳個檔"};
            for (int i = 0; i < rows.length; i++) {
                int y = card.y + 92 + i * 42;
                boolean done = i == 0 && mood == Mood.DONE;
                g.setColor(new Color(50, 40, 78));
                g.fillRoundRect(card.x + 18, y, card.width - 36, 34, 10, 10);
                g.setColor(done ? MINT : new Color(90, 78, 120));
                g.fillOval(card.x + 28, y + 9, 16, 16);
                if (done) {
                    g.setColor(BG_BOTTOM);
                    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(card.x + 32, y + 17, card.x + 35, y + 21);
                    g.drawLine(card.x + 35, y + 21, card.x + 41, y + 13);
                }
                g.setFont(UiFonts.chinesePlain(14));
                g.setColor(INK);
                g.drawString(rows[i], card.x + 54, y + 22);
            }
            paintButton(g, frame.complete, "完成", true, hot == 5, pressed == 5, ORANGE);
        }

        private void paintRing(Graphics2D g, Frame frame) {
            java.awt.Rectangle card = frame.ring;
            g.setFont(UiFonts.chinesePlain(13));
            g.setColor(MUTED);
            drawCentered(g, "示範倒數", card.x, card.y + 22, card.width);
            int cx = card.x + card.width / 2;
            int cy = card.y + card.height / 2 - 6;
            int radius = Math.min(card.width, card.height) / 2 - 58;
            if (radius < 48) {
                radius = 48;
            }
            g.setPaint(new RadialGradientPaint(cx, cy, radius + 28f,
                    new float[] {0f, 1f},
                    new Color[] {new Color(126, 200, 227, 70), new Color(126, 200, 227, 0)}));
            g.fillOval(cx - radius - 28, cy - radius - 28, (radius + 28) * 2, (radius + 28) * 2);
            g.setColor(new Color(58, 48, 88));
            g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
            double progress = remaining / TOTAL_SECONDS;
            int sweep = (int) Math.round(-360 * progress);
            g.setColor(new Color(126, 200, 227, 90));
            g.setStroke(new BasicStroke(18f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(cx - radius, cy - radius, radius * 2, radius * 2, 90, sweep);
            g.setColor(BLUE);
            g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(cx - radius, cy - radius, radius * 2, radius * 2, 90, sweep);
            int seconds = (int) Math.ceil(remaining);
            String clock = String.format("%02d:%02d", seconds / 60, seconds % 60);
            g.setFont(UiFonts.latinBold(32));
            g.setColor(INK);
            FontMetrics clockMetrics = g.getFontMetrics();
            int clockTop = cy - clockMetrics.getHeight() / 2 - 8;
            drawCentered(g, clock, card.x, clockTop, card.width);
            g.setFont(UiFonts.chinesePlain(12));
            g.setColor(MUTED);
            drawCentered(g, "假的秒數", card.x, clockTop + clockMetrics.getHeight() + 2, card.width);
            paintButton(g, frame.restart, "重來", false, hot == 6, pressed == 6, BLUE);
        }

        private void paintMoods(Graphics2D g, Frame frame) {
            Mood[] moods = Mood.values();
            Color[] colors = {MUTED, BLUE, PINK, MINT, ORANGE};
            for (int i = 0; i < moods.length; i++) {
                boolean active = mood == moods[i];
                paintButton(g, frame.moods[i], moods[i].title, active, hot == i, pressed == i, colors[i]);
            }
        }

        private void paintHint(Graphics2D g, java.awt.Rectangle card) {
            g.setFont(UiFonts.chinesePlain(14));
            g.setColor(MUTED);
            drawCentered(g, "網址、排程、聊天、傳檔以後留在這裡。這次只看主畫面。", card.x, card.y + card.height / 2 - 8, card.width);
        }

        private void paintBits(Graphics2D g) {
            for (Bit bit : bits) {
                float alpha = Math.max(0f, bit.life / bit.max);
                Color color = bit.color;
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(255 * alpha)));
                float size = bit.size * (0.6f + alpha);
                g.fillOval(Math.round(bit.x - size / 2), Math.round(bit.y - size / 2), Math.round(size), Math.round(size));
            }
        }

        private void paintButton(Graphics2D g, java.awt.Rectangle rect, String text, boolean active,
                                 boolean hover, boolean down, Color accent) {
            if (rect.width <= 0 || rect.height <= 0) {
                return;
            }
            float scale = down ? 0.96f : hover ? 1.04f : 1f;
            Graphics2D b = (Graphics2D) g.create();
            b.translate(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0);
            b.scale(scale, scale);
            b.translate(-rect.width / 2.0, -rect.height / 2.0);
            b.setColor(active ? accent : new Color(48, 38, 74));
            b.fillRoundRect(0, 0, rect.width, rect.height, 14, 14);
            if (!active) {
                b.setColor(CARD_EDGE);
                b.drawRoundRect(0, 0, rect.width - 1, rect.height - 1, 14, 14);
            }
            b.setFont(UiFonts.chineseBold(14));
            b.setColor(active ? new Color(28, 18, 32) : INK);
            drawCentered(b, text, 0, rect.height / 2 - 8, rect.width);
            b.dispose();
        }

        private Color moodColor() {
            switch (mood) {
                case WAITING:
                    return BLUE;
                case WORKING:
                    return PINK;
                case DONE:
                    return MINT;
                case FAILED:
                    return ORANGE;
                default:
                    return MUTED;
            }
        }

        private static void drawCentered(Graphics2D g, String text, int x, int y, int width) {
            FontMetrics metrics = g.getFontMetrics();
            int tx = x + (width - metrics.stringWidth(text)) / 2;
            g.drawString(text, tx, y + metrics.getAscent());
        }

        private static void paintCard(Graphics2D g, java.awt.Rectangle rect) {
            g.setColor(CARD);
            g.fill(new RoundRectangle2D.Float(rect.x, rect.y, rect.width, rect.height, 18, 18));
            g.setColor(CARD_EDGE);
            g.draw(new RoundRectangle2D.Float(rect.x + 0.5f, rect.y + 0.5f, rect.width - 1, rect.height - 1, 18, 18));
        }

        private int hit(int x, int y) {
            Frame frame = layout(getWidth(), getHeight());
            if (frame.complete.contains(x, y)) {
                return 5;
            }
            if (frame.restart.contains(x, y)) {
                return 6;
            }
            for (int i = 0; i < frame.moods.length; i++) {
                if (frame.moods[i].contains(x, y)) {
                    return i;
                }
            }
            return -1;
        }

        private Frame layout(int w, int h) {
            Frame frame = new Frame();
            int pad = 18;
            int gap = 12;
            frame.top = new java.awt.Rectangle(pad, pad, Math.max(0, w - pad * 2), 56);
            frame.moods = new java.awt.Rectangle[Mood.values().length];
            int buttonH = 40;
            int buttonsY = h - pad - buttonH;
            int buttonW = Math.max(0, (w - pad * 2 - gap * (frame.moods.length - 1)) / frame.moods.length);
            for (int i = 0; i < frame.moods.length; i++) {
                frame.moods[i] = new java.awt.Rectangle(pad + i * (buttonW + gap), buttonsY, buttonW, buttonH);
            }
            int hintH = 52;
            int hintY = buttonsY - gap - hintH;
            frame.hint = new java.awt.Rectangle(pad, hintY, Math.max(0, w - pad * 2), hintH);
            int contentY = frame.top.y + frame.top.height + gap;
            int contentH = Math.max(160, hintY - gap - contentY);
            int mascotW = Math.min(520, Math.max(280, (w - pad * 2) * 48 / 100));
            int ringW = Math.min(250, Math.max(190, (w - pad * 2) * 24 / 100));
            int midW = Math.max(160, w - pad * 2 - mascotW - ringW - gap * 2);
            frame.mascot = new java.awt.Rectangle(pad, contentY, mascotW, contentH);
            frame.task = new java.awt.Rectangle(pad + mascotW + gap, contentY, midW, contentH);
            frame.ring = new java.awt.Rectangle(frame.task.x + midW + gap, contentY, ringW, contentH);
            frame.complete = new java.awt.Rectangle(frame.task.x + 18, frame.task.y + frame.task.height - 58, frame.task.width - 36, 40);
            frame.restart = new java.awt.Rectangle(frame.ring.x + 28, frame.ring.y + frame.ring.height - 52, frame.ring.width - 56, 34);
            return frame;
        }

        private static final class Frame {
            java.awt.Rectangle top;
            java.awt.Rectangle mascot;
            java.awt.Rectangle task;
            java.awt.Rectangle ring;
            java.awt.Rectangle hint;
            java.awt.Rectangle complete;
            java.awt.Rectangle restart;
            java.awt.Rectangle[] moods;
        }

        private static final class Bit {
            float x;
            float y;
            float vx;
            float vy;
            float life;
            float max;
            float size;
            Color color;
        }
    }
}
