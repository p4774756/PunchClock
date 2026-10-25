package com.example.ui.hud;

import com.example.ui.MacGraphicsWorkaround;
import com.example.ui.UiFonts;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 像素風首頁示範。不接排程、聊天、傳檔，只用來確認日常模式的樣子。
 */
public final class PixelHomeDemo extends JFrame {

    public PixelHomeDemo() {
        super("像素助手 · 首頁示範");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(new Stage());
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(920, 640));
    }

    public static void main(String[] args) {
        MacGraphicsWorkaround.apply();
        SwingUtilities.invokeLater(() -> {
            PixelHomeDemo demo = new PixelHomeDemo();
            demo.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            demo.setVisible(true);
        });
    }

    public static final class Stage extends JPanel {

        private enum Act {
            IDLE, SMILE, WAVE, TALK
        }

        private static final Color WALL = new Color(243, 214, 166);
        private static final Color WALL_DARK = new Color(228, 190, 132);
        private static final Color WOOD = new Color(196, 137, 74);
        private static final Color WOOD_DARK = new Color(122, 74, 42);
        private static final Color CREAM = new Color(255, 246, 223);
        private static final Color INK = new Color(58, 42, 34);
        private static final Color LEAF = new Color(110, 150, 86);
        private static final Color RIBBON = new Color(212, 93, 93);
        private static final int TOTAL_SECONDS = 90;

        private final PixelSprite sprite = PixelSprite.load();
        private final Timer timer;
        private Act act = Act.IDLE;
        private double actLeft;
        private double phase;
        private double remaining = TOTAL_SECONDS;
        private boolean counting = true;
        private String bubble;
        private double bubbleLeft;
        private double transfer = -1;
        private String note = "進階的網址、連線、日誌還在原本的表單。";
        private int hot = -1;
        private int pressed = -1;
        private int hop;

        public Stage() {
            setOpaque(true);
            setBackground(WALL);
            setPreferredSize(new Dimension(1040, 680));
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

        public void tick(double dt) {
            phase += dt;
            if (counting && remaining > 0) {
                remaining = Math.max(0, remaining - dt);
                if (remaining == 0) {
                    counting = false;
                    play(Act.TALK, "時間到了", 2.4);
                }
            }
            if (actLeft > 0) {
                actLeft -= dt;
                if (actLeft <= 0) {
                    act = Act.IDLE;
                }
            }
            if (bubbleLeft > 0) {
                bubbleLeft -= dt;
                if (bubbleLeft <= 0) {
                    bubble = null;
                }
            }
            if (transfer >= 0) {
                transfer += dt / 4.2;
                if (transfer >= 1) {
                    transfer = -1;
                    play(Act.SMILE, "送到了", 1.6);
                }
            }
            hop = act == Act.TALK ? (int) (Math.sin(phase * 10) * 2) : 0;
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

        private void play(Act next, String line, double seconds) {
            act = next;
            actLeft = seconds;
            bubble = line;
            bubbleLeft = seconds;
        }

        private void activate(int id) {
            if (id == 0) {
                play(Act.WAVE, "今天也一起值班", 1.8);
            } else if (id == 1) {
                play(Act.TALK, "快到時間了", 2.2);
            } else if (id == 2) {
                play(Act.SMILE, "有人找你", 2.0);
                hop = 2;
            } else if (id == 3) {
                transfer = 0;
                play(Act.WAVE, "我來搬", 1.2);
            } else if (id == 4) {
                remaining = TOTAL_SECONDS;
                counting = true;
                note = "倒數重來了。這還是假的秒數。";
            } else if (id >= 5) {
                String[] pages = {"排程", "好友", "傳檔紀錄", "日誌"};
                note = pages[id - 5] + "頁之後接上現有功能，進階表單先留著。";
                play(Act.SMILE, "等一下就到", 1.4);
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(WALL);
            g.fillRect(0, 0, w, h);
            g.setColor(WALL_DARK);
            for (int y = 0; y < h; y += 8) {
                g.fillRect(0, y, w, 2);
            }
            Frame frame = layout(w, h);
            paintWoodBar(g, frame.top, "像素助手", "日常模式");
            paintRoom(g, frame);
            paintPanel(g, frame.clock);
            paintClock(g, frame.clock);
            paintPanel(g, frame.status);
            paintStatus(g, frame.status);
            paintButton(g, frame.wave, "打個招呼", hot == 0, pressed == 0, LEAF);
            paintButton(g, frame.remind, "快到了", hot == 1, pressed == 1, RIBBON);
            paintButton(g, frame.chat, "有人傳訊", hot == 2, pressed == 2, WOOD);
            paintButton(g, frame.carry, "模擬傳檔", hot == 3, pressed == 3, WOOD);
            paintButton(g, frame.restart, "重來", hot == 4, pressed == 4, LEAF);
            paintPanel(g, frame.transfer);
            paintTransfer(g, frame.transfer);
            String[] shortcuts = {"排程", "好友", "傳檔紀錄", "日誌"};
            for (int i = 0; i < shortcuts.length; i++) {
                paintButton(g, frame.shortcuts[i], shortcuts[i], hot == 5 + i, pressed == 5 + i, CREAM);
            }
            paintWoodBar(g, frame.hint, note, null);
            g.dispose();
        }

        private void paintRoom(Graphics2D g, Frame frame) {
            java.awt.Rectangle room = frame.room;
            g.setColor(new Color(236, 204, 150));
            g.fillRect(room.x, room.y, room.width, room.height);
            g.setColor(WOOD);
            g.fillRect(room.x, room.y + room.height - 72, room.width, 72);
            g.setColor(WOOD_DARK);
            for (int x = room.x; x < room.x + room.width; x += 28) {
                g.fillRect(x, room.y + room.height - 72, 2, 72);
            }
            g.setColor(WOOD_DARK);
            g.drawRect(room.x, room.y, room.width - 1, room.height - 1);
            pixelWindow(g, room.x + room.width - 118, room.y + 28);

            int scale = 6;
            int sw = sprite.frameWidth() * scale;
            int sh = sprite.frameHeight() * scale;
            int sx = room.x + 72;
            int sy = room.y + room.height - 78 - sh + hop;
            g.setColor(new Color(214, 120, 110));
            g.fillRect(sx + 16, room.y + room.height - 58, sw - 24, 32);
            int[] cell = spriteCell();
            sprite.draw(g, cell[0], cell[1], sx, sy, scale);
            if (bubble != null) {
                paintBubble(g, sx + sw - 10, sy + 18, bubble);
            }
        }

        private int[] spriteCell() {
            if (act == Act.WAVE) {
                int step = (int) (actLeft * 6) % 3;
                return new int[] {step, 1};
            }
            if (act == Act.SMILE) {
                return new int[] {3, 0};
            }
            if (act == Act.TALK) {
                return new int[] {3, 1};
            }
            double t = phase % 3.4;
            if (t > 3.05) {
                return new int[] {2, 0};
            }
            if (t > 2.9) {
                return new int[] {1, 0};
            }
            return new int[] {0, 0};
        }

        private void paintBubble(Graphics2D g, int x, int y, String text) {
            g.setFont(UiFonts.chineseBold(16));
            FontMetrics metrics = g.getFontMetrics();
            int width = metrics.stringWidth(text) + 28;
            int height = 36;
            g.setColor(CREAM);
            g.fillRect(x, y, width, height);
            g.setColor(INK);
            g.drawRect(x, y, width, height);
            g.drawRect(x + 1, y + 1, width - 2, height - 2);
            g.fillRect(x + 10, y + height, 8, 6);
            g.setColor(CREAM);
            g.fillRect(x + 12, y + height - 1, 4, 2);
            g.setColor(INK);
            g.drawString(text, x + 14, y + 24);
        }

        private void paintClock(Graphics2D g, java.awt.Rectangle card) {
            g.setFont(UiFonts.chinesePlain(14));
            g.setColor(INK);
            g.drawString("下次打卡", card.x + 16, card.y + 28);
            int seconds = (int) Math.ceil(remaining);
            String clock = String.format("%02d:%02d", seconds / 60, seconds % 60);
            drawPixelDigits(g, card.x + 16, card.y + 42, clock, 4);
            g.setFont(UiFonts.chinesePlain(12));
            g.setColor(new Color(122, 74, 42));
            g.drawString(counting ? "示範用的秒數" : "這輪結束了", card.x + 150, card.y + 78);
        }

        private void paintStatus(Graphics2D g, java.awt.Rectangle card) {
            String title = act == Act.IDLE ? "待命" : act == Act.WAVE ? "在揮手" : act == Act.SMILE ? "在笑" : "在說話";
            String detail = counting ? "排程還沒啟動" : "剛剛那輪跑完了";
            g.setFont(UiFonts.chineseBold(16));
            g.setColor(INK);
            g.drawString(title, card.x + 16, card.y + 28);
            g.setFont(UiFonts.chinesePlain(13));
            g.drawString(detail, card.x + 16, card.y + 50);
        }

        private void paintTransfer(Graphics2D g, java.awt.Rectangle card) {
            g.setFont(UiFonts.chinesePlain(13));
            g.setColor(INK);
            g.drawString(transfer < 0 ? "傳檔會從這裡走" : "搬運中", card.x + 14, card.y + 22);
            int barX = card.x + 14;
            int barY = card.y + 34;
            int barW = card.width - 28;
            g.setColor(new Color(232, 208, 160));
            g.fillRect(barX, barY, barW, 16);
            g.setColor(INK);
            g.drawRect(barX, barY, barW, 16);
            if (transfer >= 0) {
                int fill = (int) (barW * Math.min(1, transfer));
                g.setColor(LEAF);
                g.fillRect(barX + 2, barY + 2, Math.max(0, fill - 3), 13);
                int crate = barX + (int) ((barW - 18) * Math.min(1, transfer));
                g.setColor(WOOD);
                g.fillRect(crate, barY - 10, 14, 12);
                g.setColor(INK);
                g.drawRect(crate, barY - 10, 14, 12);
            }
        }

        private void paintWoodBar(Graphics2D g, java.awt.Rectangle bar, String title, String badge) {
            g.setColor(WOOD);
            g.fillRect(bar.x, bar.y, bar.width, bar.height);
            g.setColor(WOOD_DARK);
            g.drawRect(bar.x, bar.y, bar.width - 1, bar.height - 1);
            g.setColor(new Color(232, 184, 120));
            g.drawRect(bar.x + 2, bar.y + 2, bar.width - 5, bar.height - 5);
            g.setFont(UiFonts.chineseBold(16));
            g.setColor(INK);
            FontMetrics metrics = g.getFontMetrics();
            int ty = bar.y + (bar.height - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(title, bar.x + 16, ty);
            if (badge != null) {
                g.setFont(UiFonts.chineseBold(13));
                int bw = g.getFontMetrics().stringWidth(badge) + 20;
                int bx = bar.x + bar.width - bw - 12;
                int by = bar.y + 10;
                g.setColor(CREAM);
                g.fillRect(bx, by, bw, bar.height - 20);
                g.setColor(INK);
                g.drawRect(bx, by, bw, bar.height - 20);
                g.drawString(badge, bx + 10, ty);
            }
        }

        private void paintPanel(Graphics2D g, java.awt.Rectangle rect) {
            g.setColor(CREAM);
            g.fillRect(rect.x, rect.y, rect.width, rect.height);
            g.setColor(INK);
            g.drawRect(rect.x, rect.y, rect.width - 1, rect.height - 1);
            g.drawRect(rect.x + 2, rect.y + 2, rect.width - 5, rect.height - 5);
        }

        private void paintButton(Graphics2D g, java.awt.Rectangle rect, String text, boolean hover, boolean down, Color fill) {
            int shift = down ? 2 : 0;
            g.setColor(INK);
            g.fillRect(rect.x, rect.y + 4, rect.width, rect.height);
            Color face = down ? fill.darker() : hover ? lighten(fill) : fill;
            g.setColor(face);
            g.fillRect(rect.x, rect.y + shift, rect.width, rect.height - 2);
            g.setColor(INK);
            g.drawRect(rect.x, rect.y + shift, rect.width - 1, rect.height - 3);
            g.setFont(UiFonts.chineseBold(14));
            g.setColor(INK);
            FontMetrics metrics = g.getFontMetrics();
            int tx = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
            int ty = rect.y + shift + (rect.height - 2 - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(text, tx, ty);
        }

        private static void pixelWindow(Graphics2D g, int x, int y) {
            g.setColor(new Color(122, 74, 42));
            g.fillRect(x, y, 78, 64);
            g.setColor(new Color(168, 206, 214));
            g.fillRect(x + 6, y + 6, 30, 24);
            g.fillRect(x + 42, y + 6, 30, 24);
            g.fillRect(x + 6, y + 34, 30, 24);
            g.fillRect(x + 42, y + 34, 30, 24);
            g.setColor(new Color(244, 196, 120));
            g.fillRect(x + 46, y + 38, 10, 8);
        }

        private static void drawPixelDigits(Graphics2D g, int x, int y, String text, int scale) {
            int cursor = x;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == ':') {
                    g.setColor(INK);
                    g.fillRect(cursor + scale, y + 2 * scale, scale, scale);
                    g.fillRect(cursor + scale, y + 5 * scale, scale, scale);
                    cursor += 4 * scale;
                    continue;
                }
                int digit = ch - '0';
                if (digit < 0 || digit > 9) {
                    continue;
                }
                String[] rows = DIGITS[digit];
                for (int row = 0; row < rows.length; row++) {
                    for (int col = 0; col < rows[row].length(); col++) {
                        if (rows[row].charAt(col) == '#') {
                            g.setColor(INK);
                            g.fillRect(cursor + col * scale, y + row * scale, scale, scale);
                        }
                    }
                }
                cursor += 4 * scale;
            }
        }

        private static final String[][] DIGITS = {
                {"###", "#.#", "#.#", "#.#", "###"},
                {"..#", "..#", "..#", "..#", "..#"},
                {"###", "..#", "###", "#..", "###"},
                {"###", "..#", "###", "..#", "###"},
                {"#.#", "#.#", "###", "..#", "..#"},
                {"###", "#..", "###", "..#", "###"},
                {"###", "#..", "###", "#.#", "###"},
                {"###", "..#", "..#", "..#", "..#"},
                {"###", "#.#", "###", "#.#", "###"},
                {"###", "#.#", "###", "..#", "###"}
        };

        private static Color lighten(Color color) {
            return new Color(Math.min(255, color.getRed() + 24), Math.min(255, color.getGreen() + 24),
                    Math.min(255, color.getBlue() + 18));
        }

        private void setHot(int id) {
            if (hot == id) {
                return;
            }
            hot = id;
            setCursor(id >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            repaint();
        }

        private int hit(int x, int y) {
            Frame frame = layout(getWidth(), getHeight());
            java.awt.Rectangle[] buttons = {
                    frame.wave, frame.remind, frame.chat, frame.carry, frame.restart,
                    frame.shortcuts[0], frame.shortcuts[1], frame.shortcuts[2], frame.shortcuts[3]
            };
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].contains(x, y)) {
                    return i;
                }
            }
            return -1;
        }

        private Frame layout(int w, int h) {
            Frame frame = new Frame();
            int pad = 16;
            int gap = 12;
            frame.top = new java.awt.Rectangle(pad, pad, w - pad * 2, 48);
            frame.hint = new java.awt.Rectangle(pad, h - pad - 44, w - pad * 2, 44);
            int bodyY = frame.top.y + frame.top.height + gap;
            int bodyH = frame.hint.y - gap - bodyY;
            int roomW = Math.max(300, (w - pad * 2 - gap) * 46 / 100);
            frame.room = new java.awt.Rectangle(pad, bodyY, roomW, bodyH);
            int colX = frame.room.x + roomW + gap;
            int colW = w - pad - colX;
            frame.clock = new java.awt.Rectangle(colX, bodyY, colW, 108);
            frame.status = new java.awt.Rectangle(colX, frame.clock.y + frame.clock.height + gap, colW, 72);
            int buttonsY = frame.status.y + frame.status.height + gap;
            int buttonW = (colW - gap) / 2;
            int buttonH = 40;
            frame.wave = new java.awt.Rectangle(colX, buttonsY, buttonW, buttonH);
            frame.remind = new java.awt.Rectangle(colX + buttonW + gap, buttonsY, colW - buttonW - gap, buttonH);
            frame.chat = new java.awt.Rectangle(colX, buttonsY + buttonH + gap, buttonW, buttonH);
            frame.carry = new java.awt.Rectangle(colX + buttonW + gap, buttonsY + buttonH + gap, colW - buttonW - gap, buttonH);
            frame.restart = new java.awt.Rectangle(colX, frame.chat.y + buttonH + gap, 88, buttonH);
            int transferY = frame.restart.y + buttonH + gap;
            frame.transfer = new java.awt.Rectangle(colX, transferY, colW, 64);
            int shortY = frame.transfer.y + frame.transfer.height + gap;
            int shortH = 36;
            frame.shortcuts = new java.awt.Rectangle[4];
            int shortW = (colW - gap * 3) / 4;
            for (int i = 0; i < 4; i++) {
                frame.shortcuts[i] = new java.awt.Rectangle(colX + i * (shortW + gap), shortY, shortW, shortH);
            }
            return frame;
        }

        private static final class Frame {
            java.awt.Rectangle top;
            java.awt.Rectangle room;
            java.awt.Rectangle clock;
            java.awt.Rectangle status;
            java.awt.Rectangle wave;
            java.awt.Rectangle remind;
            java.awt.Rectangle chat;
            java.awt.Rectangle carry;
            java.awt.Rectangle restart;
            java.awt.Rectangle transfer;
            java.awt.Rectangle hint;
            java.awt.Rectangle[] shortcuts;
        }
    }
}
