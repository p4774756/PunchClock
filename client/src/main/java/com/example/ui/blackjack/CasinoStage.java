package com.example.ui.blackjack;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Random;

/**
 * 21 點視窗的根面板：畫賭場背景、依設計畫布排版標題／牌桌／操作列／頁尾，並在最上層補上籌碼裝飾。
 * 視窗大小改變時重新計算縮放並通知整棵元件樹。
 */
public final class CasinoStage extends JPanel {

    private final JComponent header;
    private final BlackjackTablePanel table;
    private final JComponent actions;
    private final JComponent footer;
    private float scale = 1f;

    public CasinoStage(JComponent header, BlackjackTablePanel table, JComponent actions, JComponent footer) {
        super(null);
        this.header = header;
        this.table = table;
        this.actions = actions;
        this.footer = footer;
        setOpaque(true);
        setBackground(CasinoTheme.PLUM_DARK);
        // 先加的元件畫在最上面：標題壓住牌桌上緣，按鈕壓住牌桌下緣
        add(header);
        add(actions);
        add(footer);
        add(table);
        putClientProperty(CasinoTheme.SCALE_KEY, scale);
    }

    public float uiScale() {
        return scale;
    }

    /** 依目前大小換算縮放；有變化才通知子元件。 */
    private void syncScale() {
        float next = CasinoTheme.clampScale(Math.min(getWidth() / (float) CasinoTheme.DESIGN_W,
                getHeight() / (float) CasinoTheme.DESIGN_H));
        if (Math.abs(next - scale) > 0.004f) {
            scale = next;
            CasinoTheme.applyScale(this, scale);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        return new Dimension(CasinoTheme.px(scale, CasinoTheme.DESIGN_W), CasinoTheme.px(scale, CasinoTheme.DESIGN_H));
    }

    @Override
    public void doLayout() {
        syncScale();
        int w = getWidth();
        int h = getHeight();
        int margin = CasinoTheme.px(scale, 12);
        int footerH = CasinoTheme.px(scale, 66);
        int footerY = h - CasinoTheme.px(scale, 10) - footerH;
        footer.setBounds(margin, footerY, w - margin * 2, footerH);

        int headerH = CasinoTheme.px(scale, 150);
        header.setBounds(0, 0, w, headerH);

        boolean hasActions = hasVisibleChild(actions);
        int actionsH = hasActions
                ? Math.max(CasinoTheme.px(scale, 92), actions.getPreferredSize().height)
                : 0;
        int actionsY = footerY - CasinoTheme.px(scale, 10) - actionsH;
        actions.setBounds(margin, actionsY, w - margin * 2, actionsH);
        actions.setVisible(hasActions);

        int tableX = CasinoTheme.px(scale, 14);
        int tableY = headerH - CasinoTheme.px(scale, 38);
        int tableBottom = hasActions ? actionsY + CasinoTheme.px(scale, 30) : footerY - CasinoTheme.px(scale, 14);
        table.setBounds(tableX, tableY, w - tableX * 2, Math.max(CasinoTheme.px(scale, 300), tableBottom - tableY));
    }

    private static boolean hasVisibleChild(JComponent container) {
        for (Component child : container.getComponents()) {
            if (child.isVisible()) {
                return true;
            }
        }
        return false;
    }

    /** 操作列內容變了（例如按鈕顯示／隱藏）時呼叫，重新分配牌桌高度。 */
    public void actionsChanged() {
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
        int w = getWidth();
        int h = getHeight();
        Image room = CasinoAssets.get(CasinoAssets.ROOM);
        if (room != null) {
            g.drawImage(roomBackdrop(room, w, h, Math.max(1.0, Math.abs(g.getTransform().getScaleX()))),
                    0, 0, w, h, null);
        } else {
            paintRoom(g, w, h);
        }
        g.dispose();
    }

    /** 背景圖等比填滿後的成品，依視窗大小與螢幕倍率快取。 */
    private java.awt.image.BufferedImage roomCache;
    private String roomKey;

    private java.awt.image.BufferedImage roomBackdrop(Image room, int w, int h, double device) {
        String key = w + "x" + h + "@" + device;
        if (roomCache == null || !key.equals(roomKey)) {
            int pw = (int) Math.ceil(w * device);
            int ph = (int) Math.ceil(h * device);
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(pw, ph,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = CasinoTheme.quality(image.createGraphics());
            double fit = Math.max(pw / (double) room.getWidth(null), ph / (double) room.getHeight(null));
            int dw = (int) Math.ceil(room.getWidth(null) * fit);
            int dh = (int) Math.ceil(room.getHeight(null) * fit);
            Image source = room;
            if (device > 1.01 && room instanceof java.awt.image.MultiResolutionImage) {
                source = ((java.awt.image.MultiResolutionImage) room).getResolutionVariant(dw, dh);
            }
            g.drawImage(source, (pw - dw) / 2, (ph - dh) / 2, dw, dh, null);
            g.dispose();
            roomCache = image;
            roomKey = key;
        }
        return roomCache;
    }

    private void paintRoom(Graphics2D g, int w, int h) {
        float s = scale;
        g.setPaint(new GradientPaint(0, 0, new Color(0x3A, 0x16, 0x22), 0, h * 0.7f, new Color(0x1E, 0x0A, 0x14)));
        g.fillRect(0, 0, w, h);

        double panel = 120 * s;
        for (double x = -panel / 2; x < w; x += panel) {
            g.setColor(new Color(0x14, 0x06, 0x0C, 120));
            g.fill(new Rectangle2D.Double(x, 0, panel * 0.14, h * 0.75));
            g.setColor(new Color(0xFF, 0xC8, 0x80, 14));
            g.fill(new Rectangle2D.Double(x + panel * 0.14, 0, 2 * s, h * 0.75));
        }

        Random random = new Random(902);
        for (int i = 0; i < 46; i++) {
            double bx = random.nextDouble() * w;
            double by = random.nextDouble() * h * 0.62;
            double r = (6 + random.nextDouble() * 22) * s;
            boolean cool = random.nextInt(6) == 0;
            Color core = cool ? new Color(0x9A, 0x8C, 0xFF, 70) : new Color(0xFF, 0xC4, 0x6A, 60 + random.nextInt(60));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(bx, by), (float) r, new float[]{0f, 1f},
                    new Color[]{core, new Color(core.getRed(), core.getGreen(), core.getBlue(), 0)}));
            g.fill(new Ellipse2D.Double(bx - r, by - r, r * 2, r * 2));
        }

        paintLamp(g, 82 * s, 0, s);

        double floorY = h * 0.72;
        g.setPaint(new GradientPaint(0, (float) floorY, new Color(0x6A, 0x10, 0x22), 0, h, new Color(0x2E, 0x05, 0x10)));
        g.fill(new Rectangle2D.Double(0, floorY, w, h - floorY));
        g.setColor(new Color(0xE8, 0xB0, 0x50, 60));
        g.fill(new Rectangle2D.Double(0, floorY, w, 2 * s));

        g.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2.0, h * 0.45), (float) (Math.max(w, h) * 0.75),
                new float[]{0.55f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
        g.fillRect(0, 0, w, h);
    }

    private static void paintLamp(Graphics2D g, double cx, double top, float s) {
        double cordH = 52 * s;
        g.setColor(new Color(0x2A, 0x18, 0x10));
        g.fill(new Rectangle2D.Double(cx - 1.5 * s, top, 3 * s, cordH));
        double domeW = 120 * s;
        double domeH = 44 * s;
        double dy = top + cordH;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, dy + domeH + 30 * s), (float) (180 * s),
                new float[]{0f, 1f}, new Color[]{new Color(255, 210, 130, 110), new Color(255, 210, 130, 0)}));
        g.fill(new Ellipse2D.Double(cx - 180 * s, dy + domeH + 30 * s - 180 * s, 360 * s, 360 * s));
        Path2D dome = new Path2D.Double();
        dome.moveTo(cx - domeW / 2, dy + domeH);
        dome.quadTo(cx - domeW * 0.42, dy, cx, dy);
        dome.quadTo(cx + domeW * 0.42, dy, cx + domeW / 2, dy + domeH);
        dome.closePath();
        g.setPaint(new GradientPaint(0, (float) dy, CasinoTheme.GOLD_DEEP, 0, (float) (dy + domeH), CasinoTheme.GOLD_DARK));
        g.fill(dome);
        g.setColor(new Color(255, 240, 190, 120));
        g.fill(new Ellipse2D.Double(cx - domeW * 0.3, dy + domeH * 0.2, domeW * 0.18, domeH * 0.3));
        g.setColor(new Color(255, 236, 180));
        g.fill(new Ellipse2D.Double(cx - domeW * 0.18, dy + domeH - 8 * s, domeW * 0.36, 18 * s));
    }

    @Override
    public void paint(Graphics graphics) {
        super.paint(graphics);
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
        float s = scale;
        java.awt.Rectangle t = table.getBounds();
        // 籌碼底邊不能低過頁尾上緣減去最低那疊的下移量，否則會蓋住頁尾的標籤與按鈕
        double bottom = Math.min(t.y + t.height + 6 * s, footer.getY() - (table.isWaiting() ? 8 : 44) * s);
        if (table.isWaiting()) {
            double chip = 52 * s;
            CasinoPaint.chipStack(g, t.x + 30 * s, bottom - 14 * s, chip, 6, 0);
            CasinoPaint.chipStack(g, t.x + 82 * s, bottom - 6 * s, chip, 4, 0);
            CasinoPaint.asset(g, CasinoAssets.LUNCH_EASEL, t.x + t.width - 120 * s, bottom + 10 * s, 170 * s);
        } else if (!CasinoPaint.asset(g, CasinoAssets.CHIPS_LEFT, t.x + 30 * s, bottom + 30 * s, 120 * s)) {
            double chip = 50 * s;
            CasinoPaint.chipStack(g, t.x + 30 * s, bottom + 26 * s, chip, 7, 0);
            CasinoPaint.chipStack(g, t.x + 76 * s, bottom + 36 * s, chip, 5, 1);
        }
        if (!table.isWaiting() && !CasinoPaint.asset(g, CasinoAssets.CHIPS_RIGHT, t.x + t.width - 30 * s,
                bottom + 30 * s, 120 * s)) {
            double chip = 50 * s;
            CasinoPaint.chipStack(g, t.x + t.width - 76 * s, bottom + 36 * s, chip, 6, 0);
            CasinoPaint.chipStack(g, t.x + t.width - 30 * s, bottom + 26 * s, chip, 8, 2);
        }
        g.dispose();
    }
}
