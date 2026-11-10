package com.example.ui.blackjack;

import com.example.ui.CandyTheme;
import com.example.ui.UiFonts;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 綠色牌桌：上方對手、中間牌堆與回合提示、下方自己與操作鈕。結算時在中間彈出結果牌。
 * 只負責畫面，遊戲狀態由 {@code BlackjackWindow} 從 session 讀出來再塞進來。
 */
public final class BlackjackTablePanel extends JPanel {

    public enum Result {
        WIN, LOSE, DRAW
    }

    private static final int RIM = 10;
    private static final int POP_MS = 520;
    private static final int CONFETTI_MS = 2600;
    private static final Color RIM_OUTER = new Color(0xB8, 0x6A, 0x86);
    private static final Color RIM_INNER = new Color(0xE9, 0xA5, 0xBE);
    private static final Color FELT_CENTER = new Color(0x4C, 0xB0, 0x86);
    private static final Color FELT_EDGE = new Color(0x22, 0x6E, 0x50);

    private final PlayerBadge peerBadge = new PlayerBadge("對手");
    private final PlayerBadge myBadge = new PlayerBadge("你");
    private final HandView peerHand = new HandView(false);
    private final HandView myHand = new HandView(true);
    private final TableCenter center = new TableCenter();
    private final TexturePaint felt = feltTexture();

    private Result result;
    private String resultTitle = "";
    private String resultDetail = "";
    private long resultStart;
    private final List<float[]> confetti = new ArrayList<>();
    private final Timer resultTimer;

    public BlackjackTablePanel(JComponent actions) {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(RIM + 10, RIM + 14, RIM + 10, RIM + 14));
        resultTimer = new Timer(16, e -> tickResult());
        resultTimer.setRepeats(true);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        add(peerBadge, c);

        c.gridy = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(4, 0, 2, 0);
        add(peerHand, c);

        c.gridy = 2;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);
        add(center, c);

        c.gridy = 3;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(2, 0, 4, 0);
        add(myHand, c);

        c.gridy = 4;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        add(myBadge, c);

        c.gridy = 5;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(10, 0, 0, 0);
        add(actions, c);
    }

    public PlayerBadge peerBadge() {
        return peerBadge;
    }

    public PlayerBadge myBadge() {
        return myBadge;
    }

    public HandView peerHand() {
        return peerHand;
    }

    public HandView myHand() {
        return myHand;
    }

    public TableCenter center() {
        return center;
    }

    public Result result() {
        return result;
    }

    /** 播結果動畫。同一種結果重複呼叫不會重播。 */
    public void showResult(Result next, String title, String detail) {
        if (next == null) {
            clearResult();
            return;
        }
        boolean replay = next != result;
        result = next;
        resultTitle = title == null ? "" : title;
        resultDetail = detail == null ? "" : detail;
        if (replay) {
            resultStart = System.currentTimeMillis();
            confetti.clear();
            if (next == Result.WIN) {
                seedConfetti();
            }
            resultTimer.start();
        }
        repaint();
    }

    public void clearResult() {
        if (result == null) {
            return;
        }
        result = null;
        confetti.clear();
        resultTimer.stop();
        repaint();
    }

    @Override
    public void removeNotify() {
        resultTimer.stop();
        super.removeNotify();
    }

    private void seedConfetti() {
        Random random = new Random();
        for (int i = 0; i < 70; i++) {
            confetti.add(new float[]{
                    random.nextFloat(),
                    -random.nextFloat() * 0.5f,
                    0.25f + random.nextFloat() * 0.35f,
                    (random.nextFloat() - 0.5f) * 0.2f,
                    random.nextInt(CONFETTI_COLORS.length),
                    3 + random.nextInt(4)});
        }
    }

    private static final Color[] CONFETTI_COLORS = {
            new Color(0xFF, 0x9A, 0xC4), new Color(0xFF, 0xD2, 0x5E), new Color(0x9F, 0xE0, 0xFF),
            new Color(0xC9, 0xB4, 0xFF), new Color(0xFF, 0xFF, 0xFF)};

    private void tickResult() {
        long elapsed = System.currentTimeMillis() - resultStart;
        long limit = result == Result.WIN ? CONFETTI_MS : POP_MS + 900;
        repaint();
        if (elapsed > limit) {
            resultTimer.stop();
            confetti.clear();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int arc = 42;

        g.setColor(new Color(10, 6, 20, 90));
        g.fillRoundRect(3, 6, w - 6, h - 6, arc, arc);
        g.setPaint(new GradientPaint(0, 0, RIM_INNER, 0, h, RIM_OUTER));
        g.fillRoundRect(0, 0, w - 4, h - 6, arc, arc);
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(3, 3, w - 11, h - 13, arc - 6, arc - 6);

        int fx = RIM;
        int fy = RIM;
        int fw = w - 4 - RIM * 2;
        int fh = h - 6 - RIM * 2;
        int farc = arc - RIM;
        float radius = Math.max(fw, fh) * 0.75f;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(fx + fw / 2f, fy + fh / 2f), radius,
                new float[]{0f, 1f}, new Color[]{FELT_CENTER, FELT_EDGE}));
        g.fillRoundRect(fx, fy, fw, fh, farc, farc);
        g.setPaint(felt);
        g.fillRoundRect(fx, fy, fw, fh, farc, farc);
        g.setColor(new Color(0, 0, 0, 50));
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(fx + 1, fy + 1, fw - 3, fh - 3, farc, farc);

        g.setColor(new Color(255, 255, 255, 34));
        g.setStroke(new BasicStroke(2f));
        int ringW = fw - 70;
        int ringH = Math.max(60, fh / 3);
        g.drawRoundRect(fx + 35, fy + (fh - ringH) / 2, ringW, ringH, ringH, ringH);

        int scale = 2;
        Color soft = new Color(255, 255, 255, 60);
        PixelArt.draw(g, PixelArt.HEART, fx + 14, fy + 14, scale, soft);
        PixelArt.draw(g, PixelArt.STAR, fx + fw - 14 - PixelArt.width(PixelArt.STAR) * scale, fy + 14, scale, soft);
        PixelArt.draw(g, PixelArt.STAR, fx + 14, fy + fh - 14 - PixelArt.height(PixelArt.STAR) * scale, scale, soft);
        PixelArt.draw(g, PixelArt.HEART, fx + fw - 14 - PixelArt.width(PixelArt.HEART) * scale,
                fy + fh - 14 - PixelArt.height(PixelArt.HEART) * scale, scale, soft);
        g.dispose();
    }

    @Override
    public void paint(Graphics graphics) {
        super.paint(graphics);
        if (result == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        long elapsed = System.currentTimeMillis() - resultStart;
        paintConfetti(g, elapsed);
        paintBanner(g, elapsed);
        g.dispose();
    }

    private void paintConfetti(Graphics2D g, long elapsed) {
        if (confetti.isEmpty()) {
            return;
        }
        float t = elapsed / 1000f;
        float fade = Math.max(0f, 1f - Math.max(0f, elapsed - (CONFETTI_MS - 700)) / 700f);
        g.setComposite(AlphaComposite.SrcOver.derive(fade));
        for (float[] p : confetti) {
            float x = (p[0] + p[3] * t + 0.02f * (float) Math.sin(t * 4 + p[0] * 20)) * getWidth();
            float y = (p[1] + p[2] * t) * getHeight();
            int size = (int) p[5];
            g.setColor(CONFETTI_COLORS[(int) p[4]]);
            g.fillRect(Math.round(x), Math.round(y), size, size);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void paintBanner(Graphics2D g, long elapsed) {
        Rectangle anchor = center.getBounds();
        int bw = Math.min(400, getWidth() - 60);
        int bh = Math.max(84, anchor.height + 8);
        int cx = anchor.x + anchor.width / 2;
        int cy = anchor.y + anchor.height / 2;

        float t = Math.min(1f, elapsed / (float) POP_MS);
        float scale = 1f;
        float alpha = Math.min(1f, t * 2.2f);
        double angle = 0;
        int dy = 0;
        Color top;
        Color bottom;
        String[] icon;
        switch (result) {
            case WIN:
                scale = 0.55f + 0.45f * easeOutBack(t);
                top = new Color(0xFF, 0xEA, 0x8F);
                bottom = new Color(0xFF, 0xB3, 0x5C);
                icon = PixelArt.TROPHY;
                break;
            case LOSE:
                dy = Math.round((1f - easeOut(t)) * -36);
                top = new Color(0xD9, 0xCD, 0xF2);
                bottom = new Color(0xA2, 0x92, 0xCC);
                icon = PixelArt.RAIN;
                break;
            default:
                scale = 0.8f + 0.2f * easeOut(t);
                long wobble = Math.min(elapsed, POP_MS + 900L);
                angle = 0.06 * Math.sin(wobble / 90.0) * Math.max(0, 1 - wobble / (double) (POP_MS + 900));
                top = new Color(0xD3, 0xF7, 0xE6);
                bottom = new Color(0xB7, 0x9A, 0xEF);
                icon = PixelArt.STAR;
                break;
        }

        AffineTransform saved = g.getTransform();
        g.translate(cx, cy + dy);
        g.rotate(angle);
        g.scale(scale, scale);
        g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        int x = -bw / 2;
        int y = -bh / 2;
        int arc = 26;
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(x + 2, y + 5, bw, bh, arc, arc);
        g.setColor(Color.WHITE);
        g.fillRoundRect(x - 3, y - 3, bw + 6, bh + 6, arc + 6, arc + 6);
        g.setPaint(new GradientPaint(0, y, top, 0, y + bh, bottom));
        g.fillRoundRect(x, y, bw, bh, arc, arc);
        g.setColor(new Color(255, 255, 255, 120));
        g.fillRoundRect(x + 10, y + 5, bw - 20, bh / 4, arc / 2, arc / 2);

        int scalePx = 4;
        int iw = PixelArt.width(icon) * scalePx;
        int ih = PixelArt.height(icon) * scalePx;
        int ix = x + 18;
        PixelArt.drawShadowed(g, icon, ix, y + (bh - ih) / 2, scalePx);

        Font titleFont = UiFonts.chineseBold(24);
        Font detailFont = UiFonts.chinesePlain(13);
        g.setFont(titleFont);
        FontMetrics tm = g.getFontMetrics();
        g.setFont(detailFont);
        FontMetrics dm = g.getFontMetrics();
        int textLeft = ix + iw + 14;
        int room = x + bw - 16 - textLeft;
        int block = tm.getHeight() + (resultDetail.isEmpty() ? 0 : dm.getHeight());
        int ty = y + (bh - block) / 2 + tm.getAscent();
        g.setFont(titleFont);
        g.setColor(new Color(0x4A, 0x22, 0x3C));
        String title = CandyTheme.fit(resultTitle, tm, room);
        g.drawString(title, textLeft + (room - tm.stringWidth(title)) / 2, ty);
        if (!resultDetail.isEmpty()) {
            g.setFont(detailFont);
            g.setColor(new Color(0x5A, 0x3A, 0x55));
            String detail = CandyTheme.fit(resultDetail, dm, room);
            g.drawString(detail, textLeft + (room - dm.stringWidth(detail)) / 2, ty + dm.getHeight() + 2);
        }
        g.setTransform(saved);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.6f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static TexturePaint feltTexture() {
        int size = 6;
        BufferedImage tile = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        tile.setRGB(1, 1, new Color(255, 255, 255, 16).getRGB());
        tile.setRGB(4, 3, new Color(0, 0, 0, 20).getRGB());
        tile.setRGB(2, 5, new Color(255, 255, 255, 10).getRGB());
        return new TexturePaint(tile, new Rectangle(0, 0, size, size));
    }
}
