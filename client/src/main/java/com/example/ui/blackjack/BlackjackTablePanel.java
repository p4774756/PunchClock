package com.example.ui.blackjack;

import com.example.ui.CandyTheme;
import com.example.ui.UiFonts;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.CardLayout;
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
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 金框綠絨牌桌。開局前顯示 {@link WaitingView}，開局後是「對手列／對手手牌／牌堆與回合／自己手牌／自己列」。
 * 結算時在中間彈出結果牌。只負責畫面，狀態由 {@code BlackjackWindow} 從 session 讀出來再塞進來。
 */
public final class BlackjackTablePanel extends JPanel implements CasinoTheme.Scalable {

    public enum Result {
        WIN, LOSE, DRAW
    }

    private static final String CARD_WAIT = "wait";
    private static final String CARD_PLAY = "play";
    private static final int POP_MS = 520;
    private static final int CONFETTI_MS = 2600;

    private final PlayerBadge peerBadge = new PlayerBadge("對手", null);
    private final PlayerBadge myBadge = new PlayerBadge("你", CasinoAssets.PLAYER_DEFAULT);
    private final HandView peerHand = new HandView(false);
    private final HandView myHand = new HandView(true);
    private final TableCenter center = new TableCenter();
    private final WaitingView waitingView = new WaitingView();
    private final CardLayout cards = new CardLayout();
    private final JPanel play;
    private final TexturePaint felt = feltTexture();
    private float scale = 1f;
    private boolean waiting = true;

    private Result result;
    private String resultTitle = "";
    private String resultDetail = "";
    private long resultStart;
    private final List<float[]> confetti = new ArrayList<>();
    private final Timer resultTimer;

    public BlackjackTablePanel() {
        setOpaque(false);
        setLayout(cards);
        resultTimer = new Timer(16, e -> tickResult());
        resultTimer.setRepeats(true);

        play = new JPanel(new GridBagLayout());
        play.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        play.add(peerBadge, c);

        c.gridy = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        play.add(peerHand, c);

        c.gridy = 2;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        play.add(center, c);

        c.gridy = 3;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        play.add(myHand, c);

        c.gridy = 4;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        play.add(myBadge, c);

        add(waitingView, CARD_WAIT);
        add(play, CARD_PLAY);
        cards.show(this, CARD_WAIT);
        updateBorder();
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

    public WaitingView waitingView() {
        return waitingView;
    }

    /** 切換開局前的等待畫面與牌局畫面。 */
    public void setWaiting(boolean value) {
        if (waiting == value) {
            return;
        }
        waiting = value;
        cards.show(this, value ? CARD_WAIT : CARD_PLAY);
        repaint();
    }

    public boolean isWaiting() {
        return waiting;
    }

    @Override
    public void setUiScale(float value) {
        scale = value;
        updateBorder();
        revalidate();
        repaint();
    }

    private void updateBorder() {
        int rim = rim();
        setBorder(new EmptyBorder(rim + CasinoTheme.px(scale, 4), rim + CasinoTheme.px(scale, 56),
                rim + CasinoTheme.px(scale, 14), rim + CasinoTheme.px(scale, 56)));
    }

    private int rim() {
        return CasinoTheme.px(scale, 18);
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

    /** 牌桌外形：上緣平直、肩部斜切、下緣大圓角。 */
    static Shape tableShape(double x, double y, double w, double h, double scale, double inset) {
        double[][] pts = {
                {x + w * 0.12, y}, {x + w * 0.88, y}, {x + w, y + h * 0.3},
                {x + w, y + h}, {x, y + h}, {x, y + h * 0.3}};
        double[] radii = {44, 44, 70, 150, 150, 70};
        Path2D path = new Path2D.Double();
        int n = pts.length;
        for (int i = 0; i < n; i++) {
            double[] prev = pts[(i + n - 1) % n];
            double[] cur = pts[i];
            double[] next = pts[(i + 1) % n];
            double r = Math.max(4, radii[i] * scale - inset * 0.6);
            double[] a = toward(cur, prev, r);
            double[] b = toward(cur, next, r);
            if (i == 0) {
                path.moveTo(a[0], a[1]);
            } else {
                path.lineTo(a[0], a[1]);
            }
            path.quadTo(cur[0], cur[1], b[0], b[1]);
        }
        path.closePath();
        return path;
    }

    private static double[] toward(double[] from, double[] to, double distance) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double len = Math.max(1e-6, Math.hypot(dx, dy));
        double t = Math.min(0.45, distance / len);
        return new double[]{from[0] + dx * t, from[1] + dy * t};
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
        int w = getWidth();
        int h = getHeight();
        double rim = rim();
        double sh = CasinoTheme.px(scale, 8);
        if (CasinoPaint.slice9(g, CasinoAssets.TABLE, 0, 0, w, h, 210, 150, 170, 150, scale)) {
            double edge = 34 * scale;
            paintClubs(g, edge, edge, w - edge * 2, h - edge * 2 - 30 * scale);
            g.dispose();
            return;
        }

        g.setColor(new Color(12, 0, 8, 120));
        g.fill(AffineTransform.getTranslateInstance(0, sh).createTransformedShape(
                tableShape(2, 2, w - 4, h - 4 - sh, scale, 0)));
        Shape outer = tableShape(2, 2, w - 4, h - 4 - sh, scale, 0);
        g.setPaint(CasinoTheme.gold(0, h));
        g.fill(outer);
        g.setColor(CasinoTheme.GOLD_DARK);
        g.setStroke(new BasicStroke(Math.max(1.5f, 2f * scale)));
        g.draw(outer);
        Shape bevel = tableShape(2 + rim * 0.35, 2 + rim * 0.35, w - 4 - rim * 0.7, h - 4 - sh - rim * 0.7, scale, rim * 0.35);
        g.setColor(new Color(255, 248, 210, 150));
        g.setStroke(new BasicStroke(Math.max(1f, 1.4f * scale)));
        g.draw(bevel);

        double fx = 2 + rim;
        double fy = 2 + rim;
        double fw = w - 4 - rim * 2;
        double fh = h - 4 - sh - rim * 2;
        Shape feltShape = tableShape(fx, fy, fw, fh, scale, rim);
        g.setColor(CasinoTheme.GOLD_DARK);
        g.setStroke(new BasicStroke(Math.max(2f, 3f * scale)));
        g.draw(feltShape);
        float radius = (float) (Math.max(fw, fh) * 0.62);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(fx + fw / 2, fy + fh * 0.45), radius,
                new float[]{0f, 0.55f, 1f},
                new Color[]{CasinoTheme.FELT_LIGHT, CasinoTheme.FELT, CasinoTheme.FELT_DARK}));
        g.fill(feltShape);
        g.setPaint(felt);
        g.fill(feltShape);

        Graphics2D inner = (Graphics2D) g.create();
        inner.clip(feltShape);
        inner.setColor(new Color(0, 0, 0, 70));
        inner.setStroke(new BasicStroke((float) (rim * 0.9)));
        inner.draw(feltShape);
        double line = CasinoTheme.px(scale, 18);
        inner.setColor(new Color(0xE8, 0xC2, 0x6A, 120));
        inner.setStroke(new BasicStroke(Math.max(1f, 1.5f * scale)));
        inner.draw(tableShape(fx + line, fy + line, fw - line * 2, fh - line * 2, scale, rim + line));
        if (!waiting) {
            inner.setColor(new Color(255, 236, 170, 46));
            inner.setStroke(new BasicStroke(Math.max(1f, 2f * scale)));
            double ew = fw * 0.82;
            double eh = fh * 0.46;
            inner.draw(new Ellipse2D.Double(fx + (fw - ew) / 2, fy + fh / 2 - eh / 2, ew, eh));
        } else {
            inner.setColor(new Color(255, 236, 170, 36));
            inner.setStroke(new BasicStroke(Math.max(1f, 2f * scale)));
            double ew = fw * 0.9;
            double eh = fh * 0.7;
            inner.draw(new Ellipse2D.Double(fx + (fw - ew) / 2, fy + fh * 0.55 - eh / 2, ew, eh));
        }
        paintClubs(inner, fx, fy, fw, fh);
        inner.dispose();
        g.dispose();
    }

    private void paintClubs(Graphics2D g, double fx, double fy, double fw, double fh) {
        double cs = CasinoTheme.px(scale, 30);
        double[][] spots = {{0.17, 0.2}, {0.83, 0.2}, {0.1, 0.78}, {0.9, 0.78}};
        g.setColor(new Color(0x0C, 0x3A, 0x22, 150));
        for (double[] p : spots) {
            g.fill(CardPainter.suitShape(3, fx + fw * p[0], fy + fh * p[1], cs));
        }
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
    public void paint(Graphics graphics) {
        super.paint(graphics);
        if (result == null) {
            return;
        }
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
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
            int size = Math.round(p[5] * scale);
            g.setColor(CONFETTI_COLORS[(int) p[4]]);
            g.fillRect(Math.round(x), Math.round(y), size, size);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    // 結算牌在 C 階段改成定稿樣式；這裡先維持原本的動畫與資訊
    private void paintBanner(Graphics2D g, long elapsed) {
        Rectangle anchor = SwingUtilities.convertRectangle(center.getParent(), center.getBounds(), this);
        int bw = Math.min(CasinoTheme.px(scale, 460), getWidth() - 60);
        int bh = Math.max(CasinoTheme.px(scale, 96), anchor.height - CasinoTheme.px(scale, 20));
        int cx = anchor.x + anchor.width / 2;
        int cy = anchor.y + anchor.height / 2;

        float t = Math.min(1f, elapsed / (float) POP_MS);
        float pop = 1f;
        float alpha = Math.min(1f, t * 2.2f);
        double angle = 0;
        int dy = 0;
        Color top;
        Color bottom;
        String[] icon;
        switch (result) {
            case WIN:
                pop = 0.55f + 0.45f * easeOutBack(t);
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
                pop = 0.8f + 0.2f * easeOut(t);
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
        g.scale(pop, pop);
        g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        int x = -bw / 2;
        int y = -bh / 2;
        int arc = 26;
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(x + 2, y + 5, bw, bh, arc, arc);
        g.setPaint(CasinoTheme.gold(y - 4, y + bh + 4));
        g.fillRoundRect(x - 4, y - 4, bw + 8, bh + 8, arc + 8, arc + 8);
        g.setPaint(new GradientPaint(0, y, top, 0, y + bh, bottom));
        g.fillRoundRect(x, y, bw, bh, arc, arc);
        g.setColor(new Color(255, 255, 255, 120));
        g.fillRoundRect(x + 10, y + 5, bw - 20, bh / 4, arc / 2, arc / 2);

        int scalePx = Math.max(3, Math.round(4 * scale));
        int iw = PixelArt.width(icon) * scalePx;
        int ih = PixelArt.height(icon) * scalePx;
        int ix = x + 18;
        PixelArt.drawShadowed(g, icon, ix, y + (bh - ih) / 2, scalePx);

        Font titleFont = CasinoTheme.chinese(true, scale, 28);
        Font detailFont = UiFonts.chinesePlain(Math.max(12, CasinoTheme.px(scale, 15)));
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
        BufferedImage asset = CasinoAssets.getBuffered(CasinoAssets.FELT_TILE);
        if (asset != null) {
            return new TexturePaint(asset, new Rectangle(0, 0, asset.getWidth(), asset.getHeight()));
        }
        int size = 64;
        BufferedImage tile = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(21);
        for (int i = 0; i < 900; i++) {
            int x = random.nextInt(size);
            int y = random.nextInt(size);
            boolean light = random.nextBoolean();
            tile.setRGB(x, y, light ? new Color(255, 255, 255, 10 + random.nextInt(12)).getRGB()
                    : new Color(0, 0, 0, 14 + random.nextInt(16)).getRGB());
        }
        return new TexturePaint(tile, new Rectangle(0, 0, size, size));
    }
}
