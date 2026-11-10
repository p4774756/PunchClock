package com.example.ui.blackjack;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 牌桌上反覆用到的 Java2D 小零件：名牌、點數膠囊、奶油色橫幅、桂冠、籌碼。
 */
final class CasinoPaint {

    private CasinoPaint() {
    }

    /** 深梅紅底、金邊的圓角名牌。 */
    static void plate(Graphics2D g, double x, double y, double w, double h, float scale) {
        if (slice3(g, CasinoAssets.NAME_PLATE, x, y, w, h, 29)) {
            return;
        }
        RoundRectangle2D.Double shape = new RoundRectangle2D.Double(x, y, w, h, h, h);
        g.setColor(new Color(10, 0, 8, 90));
        g.fill(new RoundRectangle2D.Double(x, y + 3 * scale, w, h, h, h));
        g.setPaint(new GradientPaint(0, (float) y, CasinoTheme.PLUM, 0, (float) (y + h), CasinoTheme.PLUM_DARK));
        g.fill(shape);
        CasinoTheme.goldFrame(g, shape, Math.max(1.5f, 2.4f * scale));
    }

    /** 點數、停牌之類的膠囊；回傳寬度。 */
    static int pill(Graphics2D g, double right, double cy, String label, String value, Color fill,
                    String asset, CasinoIcons icon, float scale) {
        Font labelFont = CasinoTheme.chinese(true, scale, 17);
        Font valueFont = CasinoTheme.latin(scale, 24);
        FontMetrics lm = g.getFontMetrics(labelFont);
        FontMetrics vm = g.getFontMetrics(valueFont);
        int pad = CasinoTheme.px(scale, 16);
        int gap = CasinoTheme.px(scale, 10);
        int iconSize = icon == null ? 0 : CasinoTheme.px(scale, 22);
        int textW = lm.stringWidth(label) + (value == null ? 0 : gap + vm.stringWidth(value));
        int w = pad * 2 + iconSize + (icon == null ? 0 : CasinoTheme.px(scale, 6)) + textW;
        int h = CasinoTheme.px(scale, 44);
        double x = right - w;
        double y = cy - h / 2.0;
        if (asset == null || !slice3(g, asset, x, y, w, h, 22)) {
            RoundRectangle2D.Double shape = new RoundRectangle2D.Double(x, y, w, h, h, h);
            g.setColor(new Color(10, 0, 8, 90));
            g.fill(new RoundRectangle2D.Double(x, y + 3 * scale, w, h, h, h));
            g.setPaint(new GradientPaint(0, (float) y, fill.brighter(), 0, (float) (y + h), fill.darker()));
            g.fill(shape);
            CasinoTheme.goldFrame(g, shape, Math.max(1.5f, 2.2f * scale));
        }
        double tx = x + pad;
        if (icon != null) {
            icon.paint(g, (int) Math.round(tx), (int) Math.round(cy - iconSize / 2.0), iconSize, Color.WHITE);
            tx += iconSize + CasinoTheme.px(scale, 6);
        }
        float base = (float) (cy + lm.getAscent() / 2.0 - lm.getDescent() / 2.0);
        g.setFont(labelFont);
        boolean light = CasinoAssets.PILL_GOLD.equals(asset) && CasinoAssets.get(asset) != null;
        g.setColor(light ? CasinoTheme.INK : CasinoTheme.TEXT);
        g.drawString(label, (float) tx, base);
        if (value != null) {
            float vb = (float) (cy + vm.getAscent() / 2.0 - vm.getDescent() / 2.0);
            g.setFont(valueFont);
            g.setColor(CasinoTheme.GOLD_LIGHT);
            g.drawString(value, (float) (tx + lm.stringWidth(label) + gap), vb);
        }
        return w;
    }

    /** 兩端尖角的奶油色橫幅（回合提示、等待訊息）。 */
    static Shape bannerShape(double x, double y, double w, double h) {
        double tip = h * 0.42;
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + h / 2);
        p.lineTo(x + tip, y);
        p.lineTo(x + w - tip, y);
        p.lineTo(x + w, y + h / 2);
        p.lineTo(x + w - tip, y + h);
        p.lineTo(x + tip, y + h);
        p.closePath();
        return p;
    }

    static void banner(Graphics2D g, double x, double y, double w, double h, float scale, float glow) {
        if (glow > 0f) {
            g.setColor(new Color(255, 230, 150, Math.round(80 * glow)));
            g.setStroke(new BasicStroke(10f * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(bannerShape(x, y, w, h));
        }
        g.setColor(new Color(10, 0, 8, 80));
        g.fill(bannerShape(x, y + 3 * scale, w, h));
        if (slice3(g, CasinoAssets.BANNER, x - 6 * scale, y, w + 12 * scale, h, 40)) {
            g.setStroke(new BasicStroke(1f));
            return;
        }
        Shape shape = bannerShape(x, y, w, h);
        g.setPaint(new GradientPaint(0, (float) y, CasinoTheme.CREAM, 0, (float) (y + h), CasinoTheme.CREAM_DEEP));
        g.fill(shape);
        CasinoTheme.goldFrame(g, shape, Math.max(2f, 3f * scale));
        double inset = 5 * scale;
        g.setColor(new Color(0xC9, 0x9A, 0x48, 150));
        g.setStroke(new BasicStroke(Math.max(1f, scale)));
        g.draw(bannerShape(x + inset * 1.6, y + inset, w - inset * 3.2, h - inset * 2));
        double tipX = 8 * scale;
        diamond(g, x - tipX, y + h / 2, 5 * scale);
        diamond(g, x + w + tipX, y + h / 2, 5 * scale);
        g.setStroke(new BasicStroke(1f));
    }

    static void diamond(Graphics2D g, double cx, double cy, double r) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - r);
        p.lineTo(cx + r, cy);
        p.lineTo(cx, cy + r);
        p.lineTo(cx - r, cy);
        p.closePath();
        g.setPaint(CasinoTheme.gold((float) (cy - r), (float) (cy + r)));
        g.fill(p);
    }

    /** 一支桂冠枝；side = -1 是左邊那支（葉子朝左上彎），1 是右邊。 */
    static void laurel(Graphics2D source, double cx, double cy, double h, int side) {
        Graphics2D g = (Graphics2D) source.create();
        g.translate(cx, cy);
        if (side > 0) {
            g.scale(-1, 1);
        }
        g.setPaint(CasinoTheme.gold((float) (-h / 2), (float) (h / 2)));
        g.setStroke(new BasicStroke((float) Math.max(1.2, h * 0.035), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D stem = new Path2D.Double();
        stem.moveTo(h * 0.12, h * 0.48);
        stem.quadTo(-h * 0.28, h * 0.1, -h * 0.06, -h * 0.48);
        g.draw(stem);
        int leaves = 5;
        for (int i = 0; i < leaves; i++) {
            double t = (i + 0.6) / (leaves + 0.4);
            double u = 1 - t;
            double px = u * u * h * 0.12 + 2 * u * t * -h * 0.28 + t * t * -h * 0.06;
            double py = u * u * h * 0.48 + 2 * u * t * h * 0.1 + t * t * -h * 0.48;
            leaf(g, px, py, h * 0.17, -2.3 + t * 0.9);
            leaf(g, px, py, h * 0.15, -0.6 + t * 0.4);
        }
        g.dispose();
    }

    private static void leaf(Graphics2D g, double x, double y, double len, double angle) {
        Graphics2D c = (Graphics2D) g.create();
        c.translate(x, y);
        c.rotate(angle);
        c.fill(new Ellipse2D.Double(0, -len * 0.22, len, len * 0.44));
        c.dispose();
    }

    private static final Color[][] CHIPS = {
            {new Color(0xD8, 0x2A, 0x3E), new Color(0x8E, 0x10, 0x22)},
            {new Color(0x2F, 0x4E, 0xC8), new Color(0x18, 0x26, 0x78)},
            {new Color(0x2B, 0x26, 0x2C), new Color(0x0E, 0x0A, 0x10)}};

    /** 一疊籌碼，底部中心在 (cx, bottom)。 */
    static void chipStack(Graphics2D g, double cx, double bottom, double d, int count, int color) {
        Color[] c = CHIPS[Math.floorMod(color, CHIPS.length)];
        double ry = d * 0.28;
        double thick = d * 0.11;
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(cx - d / 2 - d * 0.05, bottom - ry * 0.9, d * 1.1, ry * 2));
        for (int i = 0; i < count; i++) {
            double y = bottom - ry - i * thick;
            g.setColor(c[1]);
            g.fill(new RoundRectangle2D.Double(cx - d / 2, y, d, ry * 2 - thick * 0.2 + thick, d * 0.3, ry * 2));
            g.setColor(c[0]);
            g.fill(new Ellipse2D.Double(cx - d / 2, y - thick * 0.15, d, ry * 2));
            g.setColor(new Color(255, 255, 255, 200));
            for (int k = 0; k < 4; k++) {
                double a = Math.PI * (0.2 + k * 0.2);
                double sx = cx + Math.cos(a) * d * 0.45;
                g.fill(new RoundRectangle2D.Double(sx - d * 0.035, y + ry + Math.sin(a) * ry * 0.4,
                        d * 0.07, thick * 0.8, 2, 2));
            }
        }
        double topY = bottom - ry - (count - 1) * thick - thick * 0.15;
        g.setColor(new Color(255, 255, 255, 210));
        g.setStroke(new BasicStroke((float) Math.max(1, d * 0.05), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                1f, new float[]{(float) (d * 0.12), (float) (d * 0.12)}, 0f));
        g.draw(new Ellipse2D.Double(cx - d * 0.42, topY + ry * 0.16, d * 0.84, ry * 1.68));
        g.setStroke(new BasicStroke(1f));
        g.setColor(c[0].brighter());
        g.fill(new Ellipse2D.Double(cx - d * 0.26, topY + ry * 0.48, d * 0.52, ry * 1.04));
        g.setColor(new Color(255, 255, 255, 220));
        g.fill(PixelStar.shape(cx, topY + ry, d * 0.13));
    }

    /**
     * 九宮格：四角等比縮放、四邊單向拉伸、中央雙向拉伸。邊距以 1x 素材像素為單位，
     * {@code capScale} 是每個素材像素要畫成幾個邏輯像素。素材不存在時回 false，呼叫端改畫 Java2D。
     */
    static boolean slice9(Graphics2D g, String path, double x, double y, double w, double h,
                          int top, int left, int bottom, int right, double capScale) {
        if (CasinoAssets.get(path) == null) {
            return false;
        }
        int iw = (int) Math.round(w);
        int ih = (int) Math.round(h);
        if (iw <= 0 || ih <= 0) {
            return true;
        }
        double device = Math.max(1.0, Math.abs(g.getTransform().getScaleX()));
        String key = path + '|' + iw + 'x' + ih + '|' + top + ',' + left + ',' + bottom + ',' + right
                + '|' + Math.round(capScale * 1000) + '|' + Math.round(device * 100);
        java.awt.image.BufferedImage cached = SLICES.get(key);
        if (cached == null) {
            cached = new java.awt.image.BufferedImage((int) Math.ceil(iw * device), (int) Math.ceil(ih * device),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D cg = cached.createGraphics();
            drawSlices(cg, path, cached.getWidth(), cached.getHeight(), top, left, bottom, right, capScale * device);
            cg.dispose();
            SLICES.put(key, cached);
        }
        g.drawImage(cached, (int) Math.round(x), (int) Math.round(y), iw, ih, null);
        return true;
    }

    /** 切好的成品依尺寸與螢幕倍率快取（以裝置像素繪製，切片接縫不會因邏輯座標取整而錯位）；動畫每格重畫時不必再縮放大圖。 */
    private static final java.util.Map<String, java.awt.image.BufferedImage> SLICES =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, java.awt.image.BufferedImage>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, java.awt.image.BufferedImage> eldest) {
                    return size() > 48;
                }
            });

    private static void drawSlices(Graphics2D g, String path, double w, double h,
                                   int top, int left, int bottom, int right, double capScale) {
        double x = 0;
        double y = 0;
        CasinoAssets.Sprite sprite = CasinoAssets.sprite(path, capScale);
        double kx = Math.min(capScale, (left + right) == 0 ? capScale : w / (double) (left + right));
        double ky = Math.min(capScale, (top + bottom) == 0 ? capScale : h / (double) (top + bottom));
        int[] dx = {(int) Math.round(x), (int) Math.round(x + left * kx),
                (int) Math.round(x + w - right * kx), (int) Math.round(x + w)};
        int[] dy = {(int) Math.round(y), (int) Math.round(y + top * ky),
                (int) Math.round(y + h - bottom * ky), (int) Math.round(y + h)};
        int f = sprite.factor;
        int sw = sprite.width();
        int sh = sprite.height();
        int[] sx = {0, left * f, (sw - right) * f, sw * f};
        int[] sy = {0, top * f, (sh - bottom) * f, sh * f};
        Graphics2D q = (Graphics2D) g.create();
        q.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (dx[c + 1] <= dx[c] || dy[r + 1] <= dy[r] || sx[c + 1] <= sx[c] || sy[r + 1] <= sy[r]) {
                    continue;
                }
                q.drawImage(sprite.image, dx[c], dy[r], dx[c + 1], dy[r + 1],
                        sx[c], sy[r], sx[c + 1], sy[r + 1], null);
            }
        }
        q.dispose();
    }

    /** 三段式（左右端等比、中間橫向拉伸），高度決定縮放。 */
    static boolean slice3(Graphics2D g, String path, double x, double y, double w, double h, int caps) {
        CasinoAssets.Sprite probe = CasinoAssets.sprite(path, 1);
        if (probe == null) {
            return false;
        }
        return slice9(g, path, x, y, w, h, 0, caps, 0, caps, h / probe.height());
    }

    /** 素材存在就畫素材（等比、底部對齊），回傳是否有畫。 */
    static boolean asset(Graphics2D g, String path, double cx, double bottom, double width) {
        Image image = CasinoAssets.get(path);
        if (image == null) {
            return false;
        }
        double h = width * image.getHeight(null) / Math.max(1.0, image.getWidth(null));
        g.drawImage(image, (int) Math.round(cx - width / 2), (int) Math.round(bottom - h),
                (int) Math.round(width), (int) Math.round(h), null);
        return true;
    }

    /** 五角星形狀。 */
    static final class PixelStar {
        private PixelStar() {
        }

        static Shape shape(double cx, double cy, double r) {
            Path2D p = new Path2D.Double();
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5;
                double rr = i % 2 == 0 ? r : r * 0.45;
                double x = cx + Math.cos(a) * rr;
                double y = cy + Math.sin(a) * rr;
                if (i == 0) {
                    p.moveTo(x, y);
                } else {
                    p.lineTo(x, y);
                }
            }
            p.closePath();
            return AffineTransform.getTranslateInstance(0, 0).createTransformedShape(p);
        }
    }
}
