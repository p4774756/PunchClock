package com.example.ui.blackjack;

import com.example.ui.CandyTheme;

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
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 金框綠絨牌桌。開局前顯示 {@link WaitingView}，開局後是「對手列／對手手牌／牌堆與回合／自己手牌／自己列」。
 * 結算時在中間那條疊上結果面板（上下手牌仍看得到）；午餐爭奪戰結算後中間那條換成 {@link LunchPanel} 公告板。
 * 只負責畫面，狀態由 {@code BlackjackWindow} 從 session 讀出來再塞進來。
 */
public final class BlackjackTablePanel extends JPanel implements CasinoTheme.Scalable {

    private static final String CARD_WAIT = "wait";
    private static final String CARD_PLAY = "play";
    private static final int POP_MS = 520;
    private static final int CONFETTI_MS = 2600;

    private final PlayerBadge peerBadge = new PlayerBadge("對手", null);
    private final PlayerBadge myBadge = new PlayerBadge("你", CasinoAssets.PLAYER_DEFAULT);
    private final HandView peerHand = new HandView(false);
    private final HandView myHand = new HandView(true);
    private final TableCenter center = new TableCenter();
    private final LunchPanel lunchBoard = new LunchPanel();
    private final WaitingView waitingView = new WaitingView();
    private final CardLayout cards = new CardLayout();
    private final JPanel play;
    private final TexturePaint felt = feltTexture();
    private float scale = 1f;
    private boolean waiting = true;

    private BlackjackScene.Verdict result;
    private int resultMine;
    private int resultPeer;
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
        lunchBoard.setVisible(false);
        play.add(lunchBoard, c);

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

    public LunchPanel lunchBoard() {
        return lunchBoard;
    }

    /** 中間那條換成午餐公告板（或換回牌堆與回合）。公告板顯示時不畫結果面板，結果改由公告板說明。 */
    public void showLunchBoard(boolean value) {
        if (lunchBoard.isVisible() == value) {
            return;
        }
        lunchBoard.setVisible(value);
        center.setVisible(!value);
        play.revalidate();
        repaint();
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

    public BlackjackScene.Verdict result() {
        return result;
    }

    /** 播結果動畫。同一種結果重複呼叫不會重播。 */
    public void showResult(BlackjackScene.Verdict next, int myScore, int peerScore, String detail) {
        if (next == null || next == BlackjackScene.Verdict.NONE) {
            clearResult();
            return;
        }
        boolean replay = next != result;
        result = next;
        resultMine = myScore;
        resultPeer = peerScore;
        resultDetail = detail == null ? "" : detail;
        if (replay) {
            resultStart = System.currentTimeMillis();
            confetti.clear();
            if (next == BlackjackScene.Verdict.WIN) {
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
        for (int i = 0; i < 36; i++) {
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
        long limit = result == BlackjackScene.Verdict.WIN ? CONFETTI_MS : POP_MS + 900;
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
        if (!lunchBoard.isVisible()) {
            paintResult(g, elapsed);
        }
        paintConfetti(g, elapsed);
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

    /**
     * 結算面板：疊在中間那條上、只比它略高，上下手牌仍看得到。
     * 左右是雙方頭像與最終點數，中間是結果大字；勝利有獎盃、桂冠與星光，落敗與爆牌只用沉穩配色，平手左右對稱。
     */
    private void paintResult(Graphics2D g, long elapsed) {
        Rectangle anchor = SwingUtilities.convertRectangle(center.getParent(), center.getBounds(), this);
        double s = scale;
        double bw = Math.min(600 * s, getWidth() - 2 * (rim() + 20 * s));
        double bh = anchor.height + 24 * s;
        double cx = anchor.x + anchor.width / 2.0;
        double cy = anchor.y + anchor.height / 2.0;
        BlackjackScene.Verdict verdict = result;

        float t = Math.min(1f, elapsed / (float) POP_MS);
        float pop = 1f;
        double angle = 0;
        double dy = 0;
        switch (verdict) {
            case WIN:
                pop = 0.6f + 0.4f * easeOutBack(t);
                break;
            case LOSE:
            case BUST:
                dy = (1f - easeOut(t)) * -30 * s;
                break;
            default:
                pop = 0.85f + 0.15f * easeOut(t);
                long wobble = Math.min(elapsed, POP_MS + 900L);
                angle = 0.04 * Math.sin(wobble / 90.0) * Math.max(0, 1 - wobble / (double) (POP_MS + 900));
                break;
        }

        Graphics2D p = (Graphics2D) g.create();
        p.translate(cx, cy + dy);
        p.rotate(angle);
        p.scale(pop, pop);
        p.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, t * 2.2f)));
        double x = -bw / 2;
        double y = -bh / 2;
        double arc = 30 * s;
        Color[] body = resultColors(verdict);
        RoundRectangle2D.Double shape = new RoundRectangle2D.Double(x, y, bw, bh, arc, arc);
        p.setColor(new Color(10, 0, 8, 120));
        p.fill(new RoundRectangle2D.Double(x + 2 * s, y + 6 * s, bw, bh, arc, arc));
        p.setPaint(new GradientPaint(0, (float) y, body[0], 0, (float) (y + bh), body[1]));
        p.fill(shape);
        if (verdict == BlackjackScene.Verdict.WIN) {
            p.setPaint(new RadialGradientPaint(new Point2D.Double(0, y + bh * 0.5), (float) (bw * 0.36),
                    new float[]{0f, 1f}, new Color[]{new Color(255, 214, 120, 110), new Color(255, 214, 120, 0)}));
            p.fill(shape);
        }
        p.setPaint(new GradientPaint(0, (float) y, new Color(255, 255, 255, 46), 0, (float) (y + bh * 0.45),
                new Color(255, 255, 255, 0)));
        p.fill(new RoundRectangle2D.Double(x + 6 * s, y + 4 * s, bw - 12 * s, bh * 0.45, arc * 0.8, arc * 0.8));
        boolean bright = verdict == BlackjackScene.Verdict.WIN || verdict == BlackjackScene.Verdict.DRAW;
        CasinoTheme.goldFrame(p, shape, (float) ((bright ? 3.4 : 2.4) * s));
        double inset = 8 * s;
        p.setColor(new Color(0xE8, 0xC2, 0x6A, bright ? 130 : 80));
        p.setStroke(new BasicStroke((float) Math.max(1, s)));
        p.draw(new RoundRectangle2D.Double(x + inset, y + inset, bw - inset * 2, bh - inset * 2, arc * 0.7, arc * 0.7));

        double col = Math.min(124 * s, bw * 0.22);
        double d = Math.min(68 * s, bh * 0.46);
        double medalY = y + bh * 0.42;
        float myGlow = verdict == BlackjackScene.Verdict.WIN ? 0.85f : verdict == BlackjackScene.Verdict.DRAW ? 0.45f : 0f;
        float peerGlow = verdict == BlackjackScene.Verdict.LOSE || verdict == BlackjackScene.Verdict.BUST ? 0.5f
                : verdict == BlackjackScene.Verdict.DRAW ? 0.45f : 0f;
        boolean dimMine = verdict == BlackjackScene.Verdict.LOSE || verdict == BlackjackScene.Verdict.BUST;
        boolean dimPeer = verdict == BlackjackScene.Verdict.WIN;
        paintSide(p, x + col / 2 + 8 * s, medalY, d, myBadge, "你", resultMine, myGlow, dimMine);
        paintSide(p, x + bw - col / 2 - 8 * s, medalY, d, peerBadge, peerBadge.playerName(), resultPeer,
                peerGlow, dimPeer);

        double left = x + col + 12 * s;
        double right = x + bw - col - 12 * s;
        paintVerdictText(p, verdict, left, right, y, bh, elapsed);
        p.dispose();
    }

    private static Color[] resultColors(BlackjackScene.Verdict verdict) {
        switch (verdict) {
            case WIN:
                return new Color[]{new Color(0x8E, 0x1A, 0x3C), new Color(0x46, 0x08, 0x1E)};
            case BUST:
                return new Color[]{new Color(0x5E, 0x14, 0x2C), new Color(0x2A, 0x06, 0x14)};
            case DRAW:
                return new Color[]{new Color(0x56, 0x2C, 0x72), new Color(0x28, 0x10, 0x3C)};
            default:
                return new Color[]{CasinoTheme.PLUM, CasinoTheme.PLUM_DARK};
        }
    }

    private void paintSide(Graphics2D g, double cx, double cy, double d, PlayerBadge badge, String name, int score,
                           float glow, boolean dim) {
        badge.paintMedallion(g, cx, cy, d, glow);
        if (dim) {
            g.setColor(new Color(20, 6, 18, 96));
            g.fill(new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d));
        }
        Font nameFont = CasinoTheme.chinese(true, scale, 14);
        Font scoreFont = CasinoTheme.latin(scale, 20);
        FontMetrics nm = g.getFontMetrics(nameFont);
        FontMetrics sm = g.getFontMetrics(scoreFont);
        String value = String.valueOf(score);
        int gap = CasinoTheme.px(scale, 6);
        String label = CandyTheme.fit(name, nm, (int) (d * 1.5) - sm.stringWidth(value) - gap);
        double total = nm.stringWidth(label) + gap + sm.stringWidth(value);
        float base = (float) (cy + d / 2 + 6 * scale + sm.getAscent());
        float tx = (float) (cx - total / 2);
        g.setFont(nameFont);
        g.setColor(CasinoTheme.TEXT_MUTED);
        g.drawString(label, tx, base);
        g.setFont(scoreFont);
        g.setColor(score > 21 ? new Color(0xFF, 0x8A, 0x9A) : CasinoTheme.GOLD_LIGHT);
        g.drawString(value, tx + nm.stringWidth(label) + gap, base);
    }

    private void paintVerdictText(Graphics2D g, BlackjackScene.Verdict verdict, double left, double right,
                                  double y, double bh, long elapsed) {
        double s = scale;
        double mid = (left + right) / 2;
        String title;
        String tag;
        Paint fill;
        Color outline;
        switch (verdict) {
            case WIN:
                title = "勝利！";
                tag = "YOU WIN";
                fill = null;
                outline = CasinoTheme.GOLD_DARK;
                break;
            case BUST:
                title = "爆牌！";
                tag = "BUST";
                fill = new GradientPaint(0, (float) y, new Color(0xFF, 0xB0, 0xB8), 0, (float) (y + bh * 0.7),
                        new Color(0xE8, 0x3A, 0x52));
                outline = new Color(0x3A, 0x04, 0x12);
                break;
            case DRAW:
                title = "平手";
                tag = "DRAW";
                fill = new GradientPaint(0, (float) y, CasinoTheme.CREAM, 0, (float) (y + bh * 0.7),
                        CasinoTheme.PURPLE_LIGHT);
                outline = new Color(0x22, 0x0C, 0x36);
                break;
            default:
                title = "落敗";
                tag = "YOU LOSE";
                fill = new GradientPaint(0, (float) y, new Color(0xF2, 0xEA, 0xF4), 0, (float) (y + bh * 0.7),
                        new Color(0xB8, 0xA8, 0xC4));
                outline = new Color(0x22, 0x0C, 0x20);
                break;
        }

        Font tagFont = CasinoTheme.serif(scale, 15, false);
        FontMetrics tgm = g.getFontMetrics(tagFont);
        String spaced = String.join(" ", tag.split(""));
        float tagBase = (float) (y + 16 * s + tgm.getAscent());
        if (verdict == BlackjackScene.Verdict.BUST) {
            double tw = tgm.stringWidth(spaced) + 18 * s;
            double th = tgm.getHeight() + 2 * s;
            Graphics2D stamp = (Graphics2D) g.create();
            stamp.rotate(-0.06, mid, tagBase - tgm.getAscent() / 2.0);
            RoundRectangle2D.Double box = new RoundRectangle2D.Double(mid - tw / 2, tagBase - tgm.getAscent() - 2 * s,
                    tw, th, 6 * s, 6 * s);
            stamp.setColor(new Color(0xC4, 0x1E, 0x3A));
            stamp.fill(box);
            stamp.setColor(new Color(0xFF, 0xD6, 0xDC));
            stamp.setStroke(new BasicStroke((float) Math.max(1, 1.4 * s)));
            stamp.draw(box);
            stamp.setFont(tagFont);
            stamp.drawString(spaced, (float) (mid - tgm.stringWidth(spaced) / 2.0), tagBase);
            stamp.dispose();
        } else {
            g.setFont(tagFont);
            g.setColor(verdict == BlackjackScene.Verdict.WIN ? CasinoTheme.GOLD : CasinoTheme.TEXT_MUTED);
            g.drawString(spaced, (float) (mid - tgm.stringWidth(spaced) / 2.0), tagBase);
        }

        Font titleFont = CasinoTheme.titleChinese(title, scale, 42);
        FontMetrics tm = g.getFontMetrics(titleFont);
        int titleW = tm.stringWidth(title);
        double trophyH = verdict == BlackjackScene.Verdict.WIN ? 46 * s : 0;
        double trophyGap = trophyH > 0 ? 10 * s : 0;
        double groupW = trophyH * 0.9 + trophyGap + titleW;
        float titleBase = (float) (y + bh * 0.62);
        double tx = mid - groupW / 2 + trophyH * 0.9 + trophyGap;
        if (trophyH > 0) {
            StandInArt.trophy(g, mid - groupW / 2 + trophyH * 0.45, titleBase + 4 * s, trophyH);
        }
        if (fill == null) {
            CasinoTheme.goldText(g, title, titleFont, (float) tx, titleBase, (float) Math.max(1.5, 2.4 * s));
        } else {
            outlinedText(g, title, titleFont, (float) tx, titleBase, fill, outline, (float) Math.max(1.5, 2.2 * s));
        }
        double laurelH = tm.getAscent() * 1.15;
        double groupL = mid - groupW / 2;
        double groupR = mid + groupW / 2;
        if (verdict == BlackjackScene.Verdict.WIN) {
            CasinoPaint.laurel(g, groupL - 22 * s, titleBase - tm.getAscent() * 0.38, laurelH, -1);
            CasinoPaint.laurel(g, groupR + 22 * s, titleBase - tm.getAscent() * 0.38, laurelH, 1);
            paintSparkles(g, left, right, y, bh, elapsed);
        } else if (verdict == BlackjackScene.Verdict.DRAW) {
            CasinoPaint.diamond(g, groupL - 20 * s, titleBase - tm.getAscent() * 0.38, 6 * s);
            CasinoPaint.diamond(g, groupR + 20 * s, titleBase - tm.getAscent() * 0.38, 6 * s);
        }

        if (!resultDetail.isEmpty()) {
            Font detailFont = CasinoTheme.chinese(false, scale, 15);
            FontMetrics dm = g.getFontMetrics(detailFont);
            String detail = CandyTheme.fit(resultDetail, dm, (int) (right - left));
            float base = (float) (titleBase + dm.getAscent() + 10 * s);
            g.setFont(detailFont);
            g.setColor(new Color(0, 0, 0, 100));
            g.drawString(detail, (float) (mid - dm.stringWidth(detail) / 2.0), base + 1);
            g.setColor(CasinoTheme.TEXT);
            g.drawString(detail, (float) (mid - dm.stringWidth(detail) / 2.0), base);
        }
    }

    private static void outlinedText(Graphics2D g, String text, Font font, float x, float baseline,
                                     Paint fill, Color outline, float stroke) {
        Shape glyphs = new java.awt.font.TextLayout(text, font, g.getFontRenderContext())
                .getOutline(AffineTransform.getTranslateInstance(x, baseline));
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(AffineTransform.getTranslateInstance(0, stroke).createTransformedShape(glyphs));
        g.setColor(outline);
        g.setStroke(new BasicStroke(stroke * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(glyphs);
        g.setPaint(fill);
        g.fill(glyphs);
        g.setStroke(new BasicStroke(1f));
    }

    private static final double[][] SPARKLES = {
            {0.06, 0.22, 1.0}, {0.94, 0.26, 0.8}, {0.16, 0.86, 0.7}, {0.86, 0.84, 1.0}, {0.5, 0.1, 0.6}};

    private void paintSparkles(Graphics2D g, double left, double right, double y, double bh, long elapsed) {
        for (int i = 0; i < SPARKLES.length; i++) {
            double[] p = SPARKLES[i];
            double twinkle = elapsed < CONFETTI_MS ? 0.55 + 0.45 * Math.sin(elapsed / 160.0 + i * 1.7) : 0.85;
            double r = 7 * scale * p[2] * (0.7 + 0.3 * twinkle);
            g.setColor(new Color(255, 240, 190, (int) Math.round(230 * twinkle)));
            g.fill(CasinoPaint.PixelStar.shape(left + (right - left) * p[0], y + bh * p[1], r));
        }
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
