package com.example.ui.hud;

import com.example.ui.MacGraphicsWorkaround;
import com.example.ui.UiFonts;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 日系房間風的首頁示範。面板是真的可點元件，背景和角色是圖，不接排程與傳檔。
 */
public final class AnimeHomeDemo extends JFrame {

    public AnimeHomeDemo() {
        super("主畫面示範");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(new Stage());
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1100, 720));
    }

    public static void main(String[] args) {
        MacGraphicsWorkaround.apply();
        SwingUtilities.invokeLater(() -> {
            AnimeHomeDemo demo = new AnimeHomeDemo();
            demo.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            demo.setVisible(true);
        });
    }

    public static final class Stage extends JPanel {

        private static final Color INK = new Color(255, 252, 255);
        private static final Color MUTED = new Color(226, 218, 242);
        private static final Color PINK = new Color(255, 122, 168);
        private static final Color PANEL = new Color(54, 42, 96, 232);
        private static final Color PANEL_EDGE = new Color(214, 198, 255, 150);
        private static final String[] NAV = {"首頁", "排程任務", "聊天", "檔案傳輸", "好友名單", "設定", "日誌"};

        private final AnimeCast cast = AnimeCast.load();
        private final BufferedImage room = loadRoom();
        private final List<TaskRow> tasks = new ArrayList<>();
        private final List<Friend> friends = new ArrayList<>();
        private final List<ChatLine> lines = new ArrayList<>();
        private final List<Transfer> transfers = new ArrayList<>();
        private final JTextField composer = new JTextField();
        private final Timer timer;
        private final HeroMotion hero = new HeroMotion();

        private int nav = 0;
        private int hot = -1;
        private int selectedTask;
        private double phase;
        private double remaining = 85 * 60 + 12;
        private String bubble;
        private double bubbleLeft;
        private String pageNote;
        private java.awt.Rectangle characterBounds = new java.awt.Rectangle();

        public Stage() {
            setLayout(null);
            setOpaque(true);
            setBackground(new Color(18, 14, 36));
            setPreferredSize(new Dimension(1280, 820));
            tasks.add(new TaskRow("08:50", "上班打卡", "每天", true));
            tasks.add(new TaskRow("12:30", "午休確認", "平日", false));
            tasks.add(new TaskRow("18:10", "下班打卡", "每天", true));
            tasks.add(new TaskRow("21:00", "晚間檢查", "需要時", false));
            friends.add(new Friend("小美", "在線", Friend.ONLINE, new Color(255, 168, 196)));
            friends.add(new Friend("阿傑", "忙碌", Friend.BUSY, new Color(148, 186, 255)));
            friends.add(new Friend("家人", "離線", Friend.OFFLINE, new Color(186, 170, 210)));
            lines.add(new ChatLine(false, "小美", "打卡頁我幫你看過了"));
            lines.add(new ChatLine(true, "我", "好，時間到就會自己去"));
            transfers.add(new Transfer("畫面截圖.png", Transfer.IMAGE, 0.72));
            transfers.add(new Transfer("本週紀錄.zip", Transfer.ZIP, 1));
            transfers.add(new Transfer("操作影片.mp4", Transfer.VIDEO, 0.36));
            composer.setFont(UiFonts.chinesePlain(14));
            composer.setForeground(INK);
            composer.setCaretColor(INK);
            composer.setBackground(new Color(64, 52, 108));
            composer.setOpaque(true);
            composer.setBorder(new EmptyBorder(6, 10, 6, 10));
            composer.addActionListener(e -> sendComposer());
            add(composer);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int id = hit(e.getX(), e.getY());
                    if (hot != id) {
                        hot = id;
                        setCursor(id >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hot = -1;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    activate(hit(e.getX(), e.getY()));
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            timer = new Timer(32, e -> tick(0.032));
        }

        private void sendComposer() {
            String text = composer.getText().trim();
            if (text.isEmpty()) {
                return;
            }
            lines.add(new ChatLine(true, "我", text));
            composer.setText("");
            say(AnimeCast.REACT, "送到了");
        }

        private void say(int gesture, String line) {
            hero.play(gesture, 2.0);
            bubble = line;
            bubbleLeft = 2.0;
        }

        private void tick(double dt) {
            phase += dt;
            hero.tick(dt);
            if (remaining > 0) {
                remaining = Math.max(0, remaining - dt);
            }
            if (bubbleLeft > 0) {
                bubbleLeft -= dt;
                if (bubbleLeft <= 0) {
                    bubble = null;
                }
            }
            for (Transfer transfer : transfers) {
                if (transfer.target < 0.995) {
                    transfer.target = Math.min(0.96, transfer.target + dt * 0.012);
                }
                transfer.shown += (transfer.target - transfer.shown) * Math.min(1, dt * 4);
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

        @Override
        public void doLayout() {
            super.doLayout();
            Frame frame = layout(getWidth(), getHeight());
            composer.setBounds(frame.composer);
        }

        private void activate(int id) {
            if (id < 0) {
                return;
            }
            if (id < NAV.length) {
                nav = id;
                pageNote = id == 0 ? null : NAV[id] + "會沿用現在的畫面，這次先看首頁怎麼排。";
                repaint();
                return;
            }
            if (id == 20) {
                say(AnimeCast.WAVE, "你好");
            } else if (id >= 30 && id < 40) {
                selectedTask = id - 30;
                say(AnimeCast.SMILE, tasks.get(selectedTask).title);
            } else if (id >= 40 && id < 50) {
                int index = id - 40;
                TaskRow row = tasks.get(index);
                row.enabled = !row.enabled;
            } else if (id >= 50 && id < 60) {
                Friend friend = friends.get(id - 50);
                lines.add(new ChatLine(false, friend.name, friend.presence == Friend.OFFLINE ? "我晚點回" : "我在，說吧"));
                say(AnimeCast.REACT, friend.name + " 傳訊來了");
            } else if (id == 70) {
                sendComposer();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            paintRoom(g, w, h);
            Frame frame = layout(w, h);
            paintNav(g, frame);
            paintSchedule(g, frame);
            paintCharacter(g, frame);
            paintFriends(g, frame);
            paintChat(g, frame);
            paintFiles(g, frame);
            paintClock(g, w);
            g.dispose();
        }

        private void paintRoom(Graphics2D g, int w, int h) {
            if (room == null) {
                g.setColor(new Color(28, 20, 48));
                g.fillRect(0, 0, w, h);
                g.setColor(INK);
                g.setFont(UiFonts.chinesePlain(16));
                g.drawString("還沒有房間圖", 24, 40);
                return;
            }
            double scale = Math.max(w / (double) room.getWidth(), h / (double) room.getHeight());
            int dw = (int) Math.ceil(room.getWidth() * scale);
            int dh = (int) Math.ceil(room.getHeight() * scale);
            int dx = (w - dw) / 2;
            int dy = (h - dh) / 2;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(room, dx, dy, dw, dh, null);
            g.setColor(new Color(16, 10, 32, 36));
            g.fillRect(0, 0, w, h);
        }

        private void paintNav(Graphics2D g, Frame frame) {
            panel(g, frame.nav);
            g.setFont(UiFonts.chineseBold(18));
            g.setColor(INK);
            g.drawString("助手", frame.nav.x + 18, frame.nav.y + 36);
            g.setFont(UiFonts.chinesePlain(12));
            g.setColor(MUTED);
            g.drawString("主畫面示範", frame.nav.x + 18, frame.nav.y + 56);
            for (int i = 0; i < NAV.length; i++) {
                java.awt.Rectangle row = frame.navRows[i];
                boolean on = nav == i || hot == i;
                if (on) {
                    g.setColor(nav == i ? new Color(255, 122, 168, 210) : new Color(255, 255, 255, 28));
                    g.fillRoundRect(row.x, row.y, row.width, row.height, 12, 12);
                }
                g.setFont(UiFonts.chinesePlain(15));
                g.setColor(nav == i ? new Color(48, 18, 36) : INK);
                g.drawString(NAV[i], row.x + 16, row.y + 24);
            }
        }

        private void paintSchedule(Graphics2D g, Frame frame) {
            panel(g, frame.schedule);
            g.setFont(UiFonts.chineseBold(16));
            g.setColor(INK);
            g.drawString("排程任務", frame.schedule.x + 16, frame.schedule.y + 30);
            if (pageNote != null) {
                g.setFont(UiFonts.chinesePlain(13));
                g.setColor(MUTED);
                drawWrapped(g, pageNote, frame.schedule.x + 16, frame.schedule.y + 52, frame.schedule.width - 32);
            }
            Graphics2D rows = (Graphics2D) g.create();
            rows.setClip(frame.schedule.x, frame.schedule.y + 36, frame.schedule.width, frame.schedule.height - 44);
            for (int i = 0; i < tasks.size(); i++) {
                java.awt.Rectangle row = frame.taskRows[i];
                rows.setColor(selectedTask == i || hot == 30 + i ? new Color(255, 255, 255, 42) : new Color(255, 255, 255, 24));
                rows.fillRoundRect(row.x, row.y, row.width, row.height, 12, 12);
                TaskRow task = tasks.get(i);
                rows.setFont(UiFonts.latinBold(14));
                rows.setColor(PINK);
                rows.drawString(task.time, row.x + 12, row.y + 22);
                rows.setFont(UiFonts.chinesePlain(14));
                rows.setColor(INK);
                int titleW = frame.toggles[i].x - row.x - 80;
                rows.drawString(ellipsis(rows.getFontMetrics(), task.title, titleW), row.x + 72, row.y + 22);
                if (row.height >= 44) {
                    rows.setFont(UiFonts.chinesePlain(12));
                    rows.setColor(MUTED);
                    rows.drawString(task.detail, row.x + 72, row.y + 40);
                }
                rows.setColor(task.enabled ? PINK : new Color(110, 96, 150));
                rows.fillRoundRect(frame.toggles[i].x, frame.toggles[i].y, frame.toggles[i].width, frame.toggles[i].height, 12, 12);
                rows.setColor(Color.WHITE);
                int knob = task.enabled ? frame.toggles[i].x + frame.toggles[i].width - 20 : frame.toggles[i].x + 3;
                rows.fillOval(knob, frame.toggles[i].y + 3, 18, 18);
            }
            rows.dispose();
        }

        private void paintCharacter(Graphics2D g, Frame frame) {
            if (cast == null) {
                g.setColor(INK);
                g.setFont(UiFonts.chinesePlain(14));
                g.drawString("還沒有角色圖", frame.hero.x + 12, frame.hero.y + 28);
                characterBounds = new java.awt.Rectangle();
                return;
            }
            int height = frame.hero.height;
            int width = height * cast.frameWidth() / cast.frameHeight();
            if (width > frame.hero.width) {
                width = frame.hero.width;
                height = width * cast.frameHeight() / cast.frameWidth();
            }
            int right = frame.hero.x + frame.hero.width - 8;
            int y = frame.hero.y + frame.hero.height - height;
            if (y < frame.clock.y + frame.clock.height && right > frame.clock.x) {
                right = frame.clock.x - 8;
                int fit = right - frame.hero.x;
                if (width > fit && fit > 40) {
                    width = fit;
                    height = width * cast.frameHeight() / cast.frameWidth();
                    y = frame.hero.y + frame.hero.height - height;
                }
            }
            int x = right - width;
            characterBounds = new java.awt.Rectangle(x, y, width, height);
            cast.draw(g, hero.frame(), x, y, width, height);
            if (bubble != null) {
                paintBubble(g, x + 8, y + 24, bubble);
            }
            panel(g, frame.countdown);
            g.setFont(UiFonts.chinesePlain(12));
            g.setColor(MUTED);
            g.drawString("下一個任務", frame.countdown.x + 14, frame.countdown.y + 22);
            TaskRow next = tasks.get(selectedTask);
            g.setFont(UiFonts.chineseBold(14));
            g.setColor(INK);
            g.drawString(next.time + "  " + next.title, frame.countdown.x + 14, frame.countdown.y + 44);
            g.setFont(UiFonts.chinesePlain(13));
            g.setColor(PINK);
            g.drawString(ellipsis(g.getFontMetrics(), remainLabel(), frame.countdown.width - 28), frame.countdown.x + 14, frame.countdown.y + 66);
        }

        private void paintFriends(Graphics2D g, Frame frame) {
            panel(g, frame.friends);
            g.setFont(UiFonts.chineseBold(15));
            g.setColor(INK);
            g.drawString("好友", frame.friends.x + 14, frame.friends.y + 28);
            Graphics2D clipped = (Graphics2D) g.create();
            clipped.setClip(frame.friends.x + 4, frame.friends.y + 36, frame.friends.width - 8, frame.friends.height - 44);
            for (int i = 0; i < friends.size(); i++) {
                java.awt.Rectangle row = frame.friendRows[i];
                if (row.y + row.height > frame.friends.y + frame.friends.height - 8) {
                    break;
                }
                if (hot == 50 + i) {
                    clipped.setColor(new Color(255, 255, 255, 40));
                    clipped.fillRoundRect(row.x, row.y, row.width, row.height, 12, 12);
                }
                Friend friend = friends.get(i);
                int avatar = Math.min(36, row.height - 12);
                int ax = row.x + 8;
                int ay = row.y + (row.height - avatar) / 2;
                clipped.setColor(friend.tint);
                clipped.fillOval(ax, ay, avatar, avatar);
                clipped.setColor(new Color(48, 28, 58));
                clipped.setFont(UiFonts.chineseBold(14));
                drawCentered(clipped, friend.name.substring(0, 1), ax, ay + avatar / 2 - 8, avatar);
                Color dot = friend.presence == Friend.ONLINE ? new Color(126, 224, 168)
                        : friend.presence == Friend.BUSY ? new Color(255, 186, 92) : new Color(150, 142, 168);
                clipped.setColor(new Color(54, 42, 96));
                clipped.fillOval(ax + avatar - 12, ay + avatar - 12, 14, 14);
                clipped.setColor(dot);
                clipped.fillOval(ax + avatar - 10, ay + avatar - 10, 10, 10);
                clipped.setFont(UiFonts.chinesePlain(14));
                clipped.setColor(INK);
                int labelX = ax + avatar + 10;
                int labelW = row.x + row.width - labelX - 6;
                clipped.drawString(ellipsis(clipped.getFontMetrics(), friend.name, labelW), labelX, ay + 14);
                clipped.setFont(UiFonts.chinesePlain(12));
                clipped.setColor(dot);
                clipped.drawString(ellipsis(clipped.getFontMetrics(), friend.status, labelW), labelX, ay + avatar - 6);
            }
            clipped.dispose();
        }

        private void paintChat(Graphics2D g, Frame frame) {
            panel(g, frame.chat);
            g.setFont(UiFonts.chineseBold(15));
            g.setColor(INK);
            g.drawString("聊天", frame.chat.x + 14, frame.chat.y + 28);
            int slot = 52;
            int room = frame.composer.y - 10 - (frame.chat.y + 40);
            int visible = Math.max(0, Math.min(lines.size(), room / slot));
            int y = frame.composer.y - 10 - visible * slot;
            int start = lines.size() - visible;
            Graphics2D clipped = (Graphics2D) g.create();
            clipped.setClip(frame.chat.x + 8, frame.chat.y + 36, frame.chat.width - 16, Math.max(0, room));
            for (int i = start; i < lines.size(); i++) {
                ChatLine line = lines.get(i);
                clipped.setFont(UiFonts.chinesePlain(13));
                FontMetrics metrics = clipped.getFontMetrics();
                int maxText = Math.max(48, frame.chat.width - 120);
                String body = ellipsis(metrics, line.body, maxText - 20);
                int textW = metrics.stringWidth(body) + 22;
                int bubbleX = line.mine
                        ? frame.chat.x + frame.chat.width - 18 - textW
                        : frame.chat.x + 46;
                clipped.setFont(UiFonts.chinesePlain(11));
                clipped.setColor(MUTED);
                String who = line.mine ? "我" : line.name;
                int nameX = line.mine ? bubbleX + textW - clipped.getFontMetrics().stringWidth(who) : bubbleX;
                clipped.drawString(who, nameX, y + 12);
                if (!line.mine) {
                    clipped.setColor(new Color(255, 168, 196));
                    clipped.fillOval(frame.chat.x + 14, y + 16, 24, 24);
                    clipped.setColor(new Color(48, 28, 58));
                    clipped.setFont(UiFonts.chineseBold(12));
                    drawCentered(clipped, line.name.substring(0, 1), frame.chat.x + 14, y + 20, 24);
                }
                clipped.setColor(line.mine ? new Color(255, 138, 178, 235) : new Color(255, 255, 255, 46));
                clipped.fillRoundRect(bubbleX, y + 16, textW, 28, 14, 14);
                clipped.setFont(UiFonts.chinesePlain(13));
                clipped.setColor(line.mine ? new Color(48, 18, 36) : INK);
                clipped.drawString(body, bubbleX + 11, y + 35);
                y += slot;
            }
            clipped.dispose();
            g.setColor(new Color(255, 255, 255, 28));
            g.fillRoundRect(frame.composer.x - 4, frame.composer.y - 4, frame.composer.width + frame.send.width + 16, frame.composer.height + 8, 12, 12);
            boolean hotSend = hot == 70;
            g.setColor(hotSend ? PINK : new Color(255, 122, 168, 180));
            g.fillRoundRect(frame.send.x, frame.send.y, frame.send.width, frame.send.height, 10, 10);
            g.setFont(UiFonts.chineseBold(13));
            g.setColor(new Color(48, 18, 36));
            drawCentered(g, "送出", frame.send.x, frame.send.y + 6, frame.send.width);
        }

        private void paintFiles(Graphics2D g, Frame frame) {
            panel(g, frame.files);
            g.setFont(UiFonts.chineseBold(15));
            g.setColor(INK);
            g.drawString("檔案傳輸", frame.files.x + 14, frame.files.y + 28);
            Graphics2D clipped = (Graphics2D) g.create();
            clipped.setClip(frame.files);
            int pitch = Math.min(58, Math.max(44, (frame.files.height - 48) / Math.max(1, transfers.size())));
            for (int i = 0; i < transfers.size(); i++) {
                Transfer transfer = transfers.get(i);
                int y = frame.files.y + 46 + i * pitch;
                if (y + 36 > frame.files.y + frame.files.height - 8) {
                    break;
                }
                paintFileIcon(clipped, frame.files.x + 14, y, transfer.kind);
                clipped.setFont(UiFonts.chinesePlain(13));
                clipped.setColor(INK);
                int nameX = frame.files.x + 48;
                int nameW = frame.files.width - 96;
                clipped.drawString(ellipsis(clipped.getFontMetrics(), transfer.name, nameW), nameX, y + 12);
                clipped.setFont(UiFonts.latinPlain(11));
                clipped.setColor(MUTED);
                int percent = (int) Math.round(Math.min(1, transfer.shown) * 100);
                String label = percent + "%";
                clipped.drawString(label, frame.files.x + frame.files.width - 14 - clipped.getFontMetrics().stringWidth(label), y + 12);
                int barX = nameX;
                int barW = frame.files.width - 62;
                clipped.setColor(new Color(255, 255, 255, 36));
                clipped.fillRoundRect(barX, y + 18, barW, 8, 8, 8);
                clipped.setColor(percent >= 100 ? new Color(126, 224, 168) : PINK);
                clipped.fillRoundRect(barX, y + 18, Math.max(0, (int) (barW * Math.min(1, transfer.shown))), 8, 8, 8);
            }
            clipped.dispose();
        }

        private static void paintFileIcon(Graphics2D g, int x, int y, int kind) {
            g.setColor(kind == Transfer.ZIP ? new Color(255, 196, 122) : kind == Transfer.VIDEO ? new Color(168, 186, 255) : new Color(255, 160, 196));
            g.fillRoundRect(x, y, 26, 26, 8, 8);
            g.setColor(new Color(48, 28, 58));
            if (kind == Transfer.IMAGE) {
                g.drawLine(x + 6, y + 18, x + 11, y + 12);
                g.drawLine(x + 11, y + 12, x + 20, y + 19);
                g.fillOval(x + 16, y + 7, 4, 4);
            } else if (kind == Transfer.ZIP) {
                g.drawRect(x + 8, y + 8, 10, 10);
                g.drawLine(x + 8, y + 13, x + 18, y + 13);
            } else {
                int[] xs = {x + 9, x + 9, x + 18};
                int[] ys = {y + 7, y + 19, y + 13};
                g.fillPolygon(xs, ys, 3);
            }
        }

        private void paintClock(Graphics2D g, int width) {
            LocalDateTime now = LocalDateTime.now();
            String time = now.format(DateTimeFormatter.ofPattern("HH:mm"));
            String date = now.format(DateTimeFormatter.ofPattern("M月d日"));
            int x = width - 132;
            g.setColor(PANEL);
            g.fillRoundRect(x, 14, 112, 52, 16, 16);
            g.setColor(PANEL_EDGE);
            g.drawRoundRect(x, 14, 112, 52, 16, 16);
            g.setFont(UiFonts.latinBold(20));
            g.setColor(INK);
            drawCentered(g, time, x, 20, 112);
            g.setFont(UiFonts.chinesePlain(12));
            g.setColor(MUTED);
            drawCentered(g, date, x, 42, 112);
        }

        private void paintBubble(Graphics2D g, int x, int y, String text) {
            g.setFont(UiFonts.chineseBold(14));
            FontMetrics metrics = g.getFontMetrics();
            int w = metrics.stringWidth(text) + 24;
            int h = 32;
            g.setColor(new Color(255, 248, 252, 235));
            g.fillRoundRect(x, y, w, h, 14, 14);
            g.setColor(new Color(70, 48, 78));
            g.drawString(text, x + 12, y + 21);
        }

        private String remainLabel() {
            int seconds = (int) Math.ceil(remaining);
            int hour = seconds / 3600;
            int minute = (seconds % 3600) / 60;
            int second = seconds % 60;
            if (hour > 0) {
                return "還有 " + hour + " 小時 " + minute + " 分 " + second + " 秒";
            }
            return "還有 " + minute + " 分 " + second + " 秒";
        }

        private static void panel(Graphics2D g, java.awt.Rectangle rect) {
            g.setColor(PANEL);
            g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 18, 18);
            g.setColor(PANEL_EDGE);
            g.drawRoundRect(rect.x, rect.y, rect.width - 1, rect.height - 1, 18, 18);
        }

        private static String ellipsis(FontMetrics metrics, String text, int maxWidth) {
            if (text == null || metrics.stringWidth(text) <= maxWidth) {
                return text == null ? "" : text;
            }
            String trimmed = text;
            while (trimmed.length() > 1 && metrics.stringWidth(trimmed + "…") > maxWidth) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed + "…";
        }

        private static void drawCentered(Graphics2D g, String text, int x, int y, int width) {
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(text, x + (width - metrics.stringWidth(text)) / 2, y + metrics.getAscent());
        }

        private static void drawWrapped(Graphics2D g, String text, int x, int y, int width) {
            FontMetrics metrics = g.getFontMetrics();
            StringBuilder line = new StringBuilder();
            int top = y;
            for (int i = 0; i < text.length(); i++) {
                line.append(text.charAt(i));
                if (metrics.stringWidth(line.toString()) > width) {
                    g.drawString(line.substring(0, line.length() - 1), x, top);
                    line = new StringBuilder().append(text.charAt(i));
                    top += metrics.getHeight();
                }
            }
            g.drawString(line.toString(), x, top);
        }

        private int hit(int x, int y) {
            Frame frame = layout(getWidth(), getHeight());
            for (int i = 0; i < frame.navRows.length; i++) {
                if (frame.navRows[i].contains(x, y)) {
                    return i;
                }
            }
            if (characterBounds.contains(x, y)) {
                return 20;
            }
            for (int i = 0; i < frame.toggles.length; i++) {
                if (frame.toggles[i] != null && frame.toggles[i].contains(x, y)) {
                    return 40 + i;
                }
            }
            for (int i = 0; i < frame.taskRows.length; i++) {
                if (frame.taskRows[i] != null && frame.taskRows[i].contains(x, y)) {
                    return 30 + i;
                }
            }
            for (int i = 0; i < frame.friendRows.length; i++) {
                if (frame.friendRows[i].contains(x, y)) {
                    return 50 + i;
                }
            }
            if (frame.send.contains(x, y)) {
                return 70;
            }
            return -1;
        }

        private Frame layout(int w, int h) {
            Frame frame = new Frame();
            int margin = 16;
            int gap = 12;
            int navW = Math.max(168, Math.min(210, w / 7));
            frame.nav = new java.awt.Rectangle(margin, margin, navW, h - margin * 2);
            frame.navRows = new java.awt.Rectangle[NAV.length];
            for (int i = 0; i < NAV.length; i++) {
                frame.navRows[i] = new java.awt.Rectangle(frame.nav.x + 12, frame.nav.y + 78 + i * 46, frame.nav.width - 24, 38);
            }
            int contentX = frame.nav.x + frame.nav.width + gap;
            int contentW = w - contentX - margin;
            int topH = Math.max(250, (h - margin * 2 - gap) * 50 / 100);
            int scheduleCap = Math.max(200, contentW - 200);
            int scheduleW = Math.min(scheduleCap, Math.max(240, contentW * 48 / 100));
            frame.schedule = new java.awt.Rectangle(contentX, margin, scheduleW, topH);
            frame.taskRows = new java.awt.Rectangle[tasks.size()];
            frame.toggles = new java.awt.Rectangle[tasks.size()];
            int taskTop = frame.schedule.y + (pageNote == null ? 48 : 86);
            int taskRoom = frame.schedule.y + frame.schedule.height - 10 - taskTop;
            int taskPitch = Math.max(36, Math.min(58, taskRoom / Math.max(1, tasks.size())));
            int taskH = Math.max(32, taskPitch - 6);
            for (int i = 0; i < tasks.size(); i++) {
                java.awt.Rectangle row = new java.awt.Rectangle(frame.schedule.x + 12, taskTop + i * taskPitch, frame.schedule.width - 24, taskH);
                frame.taskRows[i] = row;
                int toggleY = row.y + Math.max(4, (row.height - 24) / 2);
                frame.toggles[i] = new java.awt.Rectangle(row.x + row.width - 58, toggleY, 44, 24);
            }
            frame.hero = new java.awt.Rectangle(contentX + scheduleW + gap, margin, Math.max(0, contentW - scheduleW - gap), topH);
            int countdownW = Math.min(260, Math.max(0, frame.hero.width - 16));
            frame.countdown = new java.awt.Rectangle(frame.hero.x + 8, frame.hero.y + frame.hero.height - 86, countdownW, 74);
            frame.clock = new java.awt.Rectangle(w - 132, 14, 112, 52);
            int bottomY = margin + topH + gap;
            int bottomH = h - margin - bottomY;
            int friendsW = Math.max(168, contentW * 24 / 100);
            int filesW = Math.max(188, contentW * 28 / 100);
            int chatW = contentW - friendsW - filesW - gap * 2;
            if (chatW < 200) {
                int room = Math.max(0, contentW - 200 - gap * 2);
                friendsW = Math.max(140, room * 46 / 100);
                filesW = Math.max(150, room - friendsW);
                chatW = contentW - friendsW - filesW - gap * 2;
            }
            if (friendsW + filesW + gap * 2 > contentW) {
                int share = Math.max(0, contentW - gap * 2);
                friendsW = share / 2;
                filesW = share - friendsW;
                chatW = 0;
            }
            frame.friends = new java.awt.Rectangle(contentX, bottomY, friendsW, bottomH);
            frame.files = new java.awt.Rectangle(contentX + friendsW + gap + Math.max(0, chatW) + gap, bottomY, filesW, bottomH);
            frame.chat = new java.awt.Rectangle(frame.friends.x + friendsW + gap, bottomY, Math.max(0, chatW), bottomH);
            frame.friendRows = new java.awt.Rectangle[friends.size()];
            int friendRoom = Math.max(1, frame.friends.height - 48);
            int friendPitch = Math.max(40, Math.min(58, friendRoom / Math.max(1, friends.size())));
            for (int i = 0; i < friends.size(); i++) {
                frame.friendRows[i] = new java.awt.Rectangle(frame.friends.x + 8, frame.friends.y + 42 + i * friendPitch, frame.friends.width - 16, friendPitch - 4);
            }
            int fieldH = 32;
            frame.send = new java.awt.Rectangle(frame.chat.x + frame.chat.width - 64, frame.chat.y + frame.chat.height - fieldH - 14, 48, fieldH);
            frame.composer = new java.awt.Rectangle(frame.chat.x + 14, frame.send.y, frame.send.x - frame.chat.x - 24, fieldH);
            return frame;
        }

        private static BufferedImage loadRoom() {
            try (InputStream in = AnimeHomeDemo.class.getResourceAsStream("/hud/anime/room.jpg")) {
                if (in == null) {
                    return null;
                }
                return ImageIO.read(in);
            } catch (IOException ex) {
                return null;
            }
        }

        private static final class TaskRow {
            final String time;
            final String title;
            final String detail;
            boolean enabled;

            TaskRow(String time, String title, String detail, boolean enabled) {
                this.time = time;
                this.title = title;
                this.detail = detail;
                this.enabled = enabled;
            }
        }

        private static final class Friend {
            static final int ONLINE = 0;
            static final int BUSY = 1;
            static final int OFFLINE = 2;

            final String name;
            final String status;
            final int presence;
            final Color tint;

            Friend(String name, String status, int presence, Color tint) {
                this.name = name;
                this.status = status;
                this.presence = presence;
                this.tint = tint;
            }
        }

        private static final class ChatLine {
            final boolean mine;
            final String name;
            final String body;

            ChatLine(boolean mine, String name, String body) {
                this.mine = mine;
                this.name = name;
                this.body = body;
            }
        }

        private static final class Transfer {
            static final int IMAGE = 0;
            static final int ZIP = 1;
            static final int VIDEO = 2;

            final String name;
            final int kind;
            double target;
            double shown;

            Transfer(String name, int kind, double target) {
                this.name = name;
                this.kind = kind;
                this.target = target;
                this.shown = target;
            }
        }

        private static final class Frame {
            java.awt.Rectangle nav;
            java.awt.Rectangle[] navRows;
            java.awt.Rectangle schedule;
            java.awt.Rectangle[] taskRows;
            java.awt.Rectangle[] toggles;
            java.awt.Rectangle hero;
            java.awt.Rectangle countdown;
            java.awt.Rectangle clock;
            java.awt.Rectangle friends;
            java.awt.Rectangle[] friendRows;
            java.awt.Rectangle chat;
            java.awt.Rectangle files;
            java.awt.Rectangle composer;
            java.awt.Rectangle send;
        }
    }
}
