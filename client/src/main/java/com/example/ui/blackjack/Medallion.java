package com.example.ui.blackjack;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/**
 * 金框圓形頭像。優先用玩家頭像，其次用預設角色素材，都沒有時畫一隻 Java2D 小貓臉。
 */
final class Medallion {

    private static final Color[][] FURS = {
            {new Color(0xFF, 0xC0, 0x7A), new Color(0xF0, 0x93, 0x45)},
            {new Color(0xEE, 0xE6, 0xF0), new Color(0xA8, 0x9E, 0xB4)},
            {new Color(0xFF, 0xDD, 0xB0), new Color(0x9B, 0x6A, 0x46)},
            {new Color(0xD9, 0xD3, 0xE8), new Color(0x7D, 0x70, 0x98)}};

    private Medallion() {
    }

    /**
     * @param fallbackAsset 沒有玩家頭像時要用的預設角色素材路徑（{@link CasinoAssets}）
     */
    static void paint(Graphics2D source, double cx, double cy, double d, Image avatar,
                      String fallbackAsset, String name, float glow) {
        Graphics2D g = CasinoTheme.quality(source);
        double r = d / 2;
        double ring = Math.max(3, d * 0.075);
        if (glow > 0f) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) (r * 1.35),
                    new float[]{0.6f, 1f},
                    new Color[]{new Color(255, 226, 140, Math.round(170 * glow)), new Color(255, 226, 140, 0)}));
            g.fill(new Ellipse2D.Double(cx - r * 1.35, cy - r * 1.35, r * 2.7, r * 2.7));
        }
        Image ringArt = CasinoAssets.get(CasinoAssets.MEDALLION_RING);
        if (ringArt != null) {
            paintWithRing(g, ringArt, cx, cy, d, avatar, fallbackAsset, name);
            g.dispose();
            return;
        }
        g.setColor(new Color(20, 4, 12, 110));
        g.fill(new Ellipse2D.Double(cx - r + 1, cy - r + d * 0.04, d, d));
        Ellipse2D outer = new Ellipse2D.Double(cx - r, cy - r, d, d);
        g.setPaint(CasinoTheme.gold((float) (cy - r), (float) (cy + r)));
        g.fill(outer);
        g.setColor(CasinoTheme.GOLD_DARK);
        g.setStroke(new BasicStroke(1f));
        g.draw(outer);

        double ir = r - ring;
        Ellipse2D inner = new Ellipse2D.Double(cx - ir, cy - ir, ir * 2, ir * 2);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy - ir * 0.3), (float) (ir * 1.2),
                new float[]{0f, 1f}, new Color[]{new Color(0xC8, 0xB2, 0xF4), new Color(0x7A, 0x5C, 0xC4)}));
        g.fill(inner);
        paintFace(g, inner, cx, cy, ir, avatar, fallbackAsset, name);

        g.setColor(new Color(255, 248, 220, 150));
        g.setStroke(new BasicStroke((float) Math.max(1, ring / 3)));
        g.draw(inner);
        g.dispose();
    }

    /**
     * 金框素材（256²）：外圈半徑約 120、內緣半徑 91～96，圓心在 (128, 128)；中央鏤空、不含陰影。
     * 先畫陰影與紫底，頭像裁成比內緣略大的圓，最後疊上金框蓋住頭像邊緣。
     */
    private static void paintWithRing(Graphics2D g, Image ring, double cx, double cy, double d,
                                      Image avatar, String fallbackAsset, String name) {
        double r = d / 2;
        g.setColor(new Color(20, 4, 12, 110));
        g.fill(new Ellipse2D.Double(cx - r + 1, cy - r + d * 0.04, d, d));
        double size = d * 256 / 240.0;
        double ir = size * 97 / 256.0;
        Ellipse2D inner = new Ellipse2D.Double(cx - ir, cy - ir, ir * 2, ir * 2);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy - ir * 0.3), (float) (ir * 1.2),
                new float[]{0f, 1f}, new Color[]{new Color(0xC8, 0xB2, 0xF4), new Color(0x7A, 0x5C, 0xC4)}));
        g.fill(inner);
        paintFace(g, inner, cx, cy, ir, avatar, fallbackAsset, name);
        double half = size / 2;
        g.drawImage(ring, (int) Math.round(cx - half), (int) Math.round(cy - half),
                (int) Math.round(size), (int) Math.round(size), null);
    }

    /** 玩家頭像優先；沒有時用預設角色素材，再沒有就畫小貓。 */
    private static void paintFace(Graphics2D g, Ellipse2D inner, double cx, double cy, double ir,
                                  Image avatar, String fallbackAsset, String name) {
        Image art = avatar;
        double zoom = 1;
        if (art == null && fallbackAsset != null) {
            art = CasinoAssets.get(fallbackAsset);
            zoom = fallbackZoom(fallbackAsset);
        }
        Graphics2D clip = (Graphics2D) g.create();
        clip.clip(inner);
        if (art != null) {
            int iw = art.getWidth(null);
            int ih = art.getHeight(null);
            double side = ir * 2 * zoom;
            double fit = Math.max(side / Math.max(1, iw), side / Math.max(1, ih));
            double dw = iw * fit;
            double dh = ih * fit;
            clip.drawImage(art, (int) Math.round(cx - dw / 2), (int) Math.round(cy - dh / 2),
                    (int) Math.round(dw), (int) Math.round(dh), null);
        } else {
            paintCat(clip, cx, cy + ir * 0.12, ir * 1.55, name);
        }
        clip.dispose();
    }

    /**
     * 預設角色是圓形裁切圖，最外圈有鋸齒與半透明雜邊：依素材實際的不透明半徑放大，只露出乾淨的部分。
     * 不是圓形裁切（不透明半徑太小）的素材不放大。
     */
    private static double fallbackZoom(String path) {
        double radius = CasinoAssets.opaqueRadius(path);
        return radius >= 0.8 ? 1 / (radius - 0.01) : 1;
    }

    private static void paintCat(Graphics2D g, double cx, double cy, double size, String name) {
        Color[] fur = FURS[Math.floorMod(name == null ? 0 : name.hashCode(), FURS.length)];
        double w = size;
        double h = size * 0.82;
        double top = cy - h / 2;
        g.setColor(fur[1]);
        g.fill(ear(cx - w * 0.3, top + h * 0.1, w * 0.26, -1));
        g.fill(ear(cx + w * 0.3, top + h * 0.1, w * 0.26, 1));
        g.setColor(new Color(0xFF, 0xB6, 0xC8));
        g.fill(ear(cx - w * 0.3, top + h * 0.16, w * 0.14, -1));
        g.fill(ear(cx + w * 0.3, top + h * 0.16, w * 0.14, 1));
        g.setColor(fur[0]);
        g.fill(new Ellipse2D.Double(cx - w / 2, top + h * 0.1, w, h));
        g.setColor(new Color(0xFF, 0xF6, 0xEC));
        g.fill(new Ellipse2D.Double(cx - w * 0.26, top + h * 0.5, w * 0.52, h * 0.46));

        double eyeY = top + h * 0.48;
        double eyeR = w * 0.055;
        g.setColor(new Color(0x2E, 0x1C, 0x24));
        g.fill(new Ellipse2D.Double(cx - w * 0.2 - eyeR, eyeY - eyeR * 1.2, eyeR * 2, eyeR * 2.4));
        g.fill(new Ellipse2D.Double(cx + w * 0.2 - eyeR, eyeY - eyeR * 1.2, eyeR * 2, eyeR * 2.4));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(cx - w * 0.2 - eyeR * 0.3, eyeY - eyeR, eyeR * 0.8, eyeR * 0.8));
        g.fill(new Ellipse2D.Double(cx + w * 0.2 - eyeR * 0.3, eyeY - eyeR, eyeR * 0.8, eyeR * 0.8));

        g.setColor(new Color(0xFF, 0x8F, 0xA8, 170));
        g.fill(new Ellipse2D.Double(cx - w * 0.36, eyeY + h * 0.1, w * 0.13, h * 0.08));
        g.fill(new Ellipse2D.Double(cx + w * 0.23, eyeY + h * 0.1, w * 0.13, h * 0.08));

        g.setColor(new Color(0x5A, 0x32, 0x34));
        g.setStroke(new BasicStroke((float) Math.max(1.2, w * 0.022), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double mouthY = eyeY + h * 0.16;
        Path2D mouth = new Path2D.Double();
        mouth.moveTo(cx - w * 0.07, mouthY);
        mouth.quadTo(cx - w * 0.035, mouthY + h * 0.05, cx, mouthY);
        mouth.quadTo(cx + w * 0.035, mouthY + h * 0.05, cx + w * 0.07, mouthY);
        g.draw(mouth);
        g.setColor(new Color(0xF0, 0x7A, 0x90));
        g.fill(new Ellipse2D.Double(cx - w * 0.03, mouthY - h * 0.05, w * 0.06, h * 0.04));
    }

    private static Path2D ear(double cx, double base, double size, int side) {
        Path2D ear = new Path2D.Double();
        ear.moveTo(cx - size * 0.55, base + size * 0.6);
        ear.lineTo(cx + side * size * 0.15, base - size * 0.55);
        ear.lineTo(cx + size * 0.55, base + size * 0.6);
        ear.closePath();
        return ear;
    }
}
