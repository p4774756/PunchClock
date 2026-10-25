package com.example.ui;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * 排程頁上方的平面手搖店。杯子跟著目前任務狀態搖。
 */
public final class ShopStrip extends JPanel {

    public enum Mood {
        IDLE("還沒開始搖", "排程設好就會自己營業"),
        WAITING("等下一杯", "時間到了會自己去點"),
        SHAKING("搖杯中", "正在幫你按打卡"),
        DONE("上一杯好了", "打卡成功"),
        FAILED("這杯沒搖成", "看一下日誌");

        final String title;
        final String detail;

        Mood(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }
    }

    private static final Color CREAM = new Color(255, 246, 223);
    private static final Color EDGE = new Color(234, 215, 162);
    private static final Color SKY = new Color(207, 231, 238);
    private static final Color ROOF = new Color(9, 101, 151);
    private static final Color AWNING = new Color(234, 84, 32);
    private static final Color WALL = new Color(255, 248, 241);
    private static final Color CUP = new Color(244, 143, 177);
    private static final Color INK = new Color(30, 41, 59);
    private static final Color MUTED = new Color(100, 116, 139);

    private Mood mood = Mood.IDLE;
    private double phase;
    private final Timer timer;

    public ShopStrip() {
        setOpaque(false);
        setPreferredSize(new Dimension(640, 96));
        setMinimumSize(new Dimension(280, 96));
        timer = new Timer(40, e -> {
            double step = mood == Mood.SHAKING ? 0.42 : mood == Mood.WAITING ? 0.1 : 0.04;
            phase += step;
            repaint();
        });
    }

    public void setMood(Mood mood) {
        this.mood = mood == null ? Mood.IDLE : mood;
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
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g.setColor(CREAM);
        g.fill(new RoundRectangle2D.Float(2, 2, w - 4, h - 4, 16, 16));
        g.setColor(EDGE);
        g.draw(new RoundRectangle2D.Float(2.5f, 2.5f, w - 5, h - 5, 16, 16));

        int sceneX = 14;
        int sceneY = 12;
        int sceneW = 188;
        int sceneH = h - 24;
        int ground = sceneY + sceneH - 12;
        g.setColor(SKY);
        g.fillRoundRect(sceneX, sceneY, sceneW, sceneH, 12, 12);
        g.setColor(new Color(142, 174, 106));
        g.fillRect(sceneX, ground, sceneW, sceneY + sceneH - ground);

        g.setColor(WALL);
        g.fillRoundRect(36, sceneY + 22, 78, ground - (sceneY + 22), 6, 6);
        g.setColor(ROOF);
        g.fillRoundRect(30, sceneY + 14, 90, 12, 4, 4);
        g.setColor(AWNING);
        g.fillRect(40, sceneY + 26, 70, 7);
        g.setColor(new Color(186, 154, 110));
        g.fillRoundRect(58, ground - 22, 22, 22, 3, 3);

        double bob = Math.sin(phase) * (mood == Mood.SHAKING ? 3.2 : mood == Mood.WAITING ? 1.4 : 0.6);
        double tilt = mood == Mood.SHAKING ? Math.sin(phase * 2) * 0.28 : 0;
        int cupX = 156;
        int cupY = (int) (ground - 4 + bob);
        g.translate(cupX, cupY);
        g.rotate(tilt);
        g.setColor(new Color(186, 154, 110));
        g.fillRoundRect(-16, -4, 32, 6, 2, 2);
        g.setColor(CUP);
        g.fillRoundRect(-7, -24, 14, 20, 4, 4);
        g.setColor(WALL);
        g.fillOval(-8, -27, 16, 7);
        g.setColor(INK);
        g.fillRoundRect(1, -36, 2, 12, 1, 1);
        g.rotate(-tilt);
        g.translate(-cupX, -cupY);

        g.setFont(UiFonts.chineseBold(16));
        g.setColor(INK);
        FontMetrics title = g.getFontMetrics();
        int textX = 218;
        int titleY = 40;
        g.drawString(mood.title, textX, titleY);
        g.setFont(UiFonts.chinesePlain(13));
        g.setColor(MUTED);
        g.drawString(mood.detail, textX, titleY + title.getHeight());
        g.dispose();
    }
}
