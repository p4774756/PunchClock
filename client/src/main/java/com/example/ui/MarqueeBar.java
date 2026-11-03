package com.example.ui;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 主視窗最上方的跑馬燈。沒有文字或後台關掉時不佔高度。
 * 系統開了減少動態效果時，文字停在左邊。
 */
public final class MarqueeBar extends JPanel {

    private static final int HEIGHT = 30;
    private static final int PAD = 10;
    private static final Color BAR = new Color(72, 36, 78);
    private static final Color CHIP = new Color(255, 122, 168);
    private static final Color CHIP_INK = new Color(48, 18, 36);
    private static final Color TEXT = new Color(255, 248, 252);

    private final boolean reducedMotion = reducedMotion();
    private final Timer timer;
    private String text = "";
    private int offset;
    private boolean primed;

    public MarqueeBar() {
        setOpaque(true);
        setBackground(BAR);
        setFont(UiFonts.chinesePlain(13));
        setVisible(false);
        setPreferredSize(new Dimension(0, 0));
        timer = new Timer(32, e -> {
            offset++;
            repaint();
        });
    }

    /** 後台心跳送來的開關與文字。空字或關閉時收起。 */
    public void showNotice(boolean enabled, String message) {
        String next = message == null ? "" : message.trim();
        boolean show = enabled && !next.isEmpty();
        text = next;
        primed = false;
        offset = 0;
        setToolTipText(show ? next : null);
        setVisible(show);
        setPreferredSize(show ? new Dimension(120, HEIGHT) : new Dimension(0, 0));
        if (show && !reducedMotion) {
            if (!timer.isRunning()) {
                timer.start();
            }
        } else {
            timer.stop();
            offset = 0;
        }
        revalidate();
        repaint();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (text.isEmpty() || !isVisible()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(UiFonts.chineseBold(12));
        FontMetrics chipMetrics = g2.getFontMetrics();
        String chip = "公告";
        int chipW = chipMetrics.stringWidth(chip) + 16;
        int chipH = chipMetrics.getHeight() + 4;
        int chipX = PAD;
        int chipY = (getHeight() - chipH) / 2;
        g2.setColor(CHIP);
        g2.fillRoundRect(chipX, chipY, chipW, chipH, 10, 10);
        g2.setColor(CHIP_INK);
        g2.drawString(chip, chipX + 8, chipY + chipMetrics.getAscent() + 2);

        int textX = chipX + chipW + 10;
        int textW = Math.max(0, getWidth() - textX - PAD);
        g2.setFont(getFont());
        g2.setColor(TEXT);
        FontMetrics metrics = g2.getFontMetrics();
        int baseline = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
        g2.setClip(textX, 0, textW, getHeight());
        if (reducedMotion || textW <= 0) {
            g2.drawString(CandyTheme.fit(text, metrics, textW), textX, baseline);
            g2.dispose();
            return;
        }
        int messageW = metrics.stringWidth(text);
        int gap = Math.max(48, textW / 3);
        int cycle = messageW + gap;
        if (cycle <= 0) {
            g2.dispose();
            return;
        }
        if (!primed) {
            offset = textW;
            primed = true;
        }
        int shift = offset % cycle;
        int x = textX + textW - shift;
        g2.drawString(text, x, baseline);
        g2.drawString(text, x + cycle, baseline);
        g2.dispose();
    }

    private static boolean reducedMotion() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("defaults", "read", "com.apple.universalaccess", "reduceMotion")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                process.destroy();
                return false;
            }
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return "1".equals(value);
        } catch (Exception ex) {
            return false;
        }
    }
}
