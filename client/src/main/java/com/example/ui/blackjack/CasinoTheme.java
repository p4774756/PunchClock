package com.example.ui.blackjack;

import com.example.ui.UiFonts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.Map;

/**
 * 21 點「精品賭場 × 可愛角色」的共用配色、金框、字型與縮放。
 * 版面以 960×900 的設計畫布為準，實際尺寸都乘上 {@link #scale}。
 */
public final class CasinoTheme {

    public static final int DESIGN_W = 960;
    public static final int DESIGN_H = 900;
    public static final float MIN_SCALE = 0.72f;
    public static final float MAX_SCALE = 1.6f;

    public static final Color GOLD_LIGHT = new Color(0xFF, 0xEE, 0xB5);
    public static final Color GOLD = new Color(0xF0, 0xC2, 0x5A);
    public static final Color GOLD_DEEP = new Color(0xB5, 0x80, 0x2C);
    public static final Color GOLD_DARK = new Color(0x6E, 0x48, 0x14);

    public static final Color FELT_LIGHT = new Color(0x2E, 0x8C, 0x56);
    public static final Color FELT = new Color(0x1D, 0x6C, 0x41);
    public static final Color FELT_DARK = new Color(0x0F, 0x43, 0x28);

    public static final Color WINE = new Color(0x7A, 0x16, 0x34);
    public static final Color WINE_DARK = new Color(0x3C, 0x0A, 0x1E);
    public static final Color PLUM = new Color(0x4A, 0x1C, 0x40);
    public static final Color PLUM_DARK = new Color(0x26, 0x0C, 0x22);
    public static final Color PURPLE = new Color(0x7E, 0x52, 0xD6);
    public static final Color PURPLE_LIGHT = new Color(0xBC, 0xA2, 0xFF);

    public static final Color CREAM = new Color(0xFF, 0xF7, 0xE6);
    public static final Color CREAM_DEEP = new Color(0xF3, 0xDF, 0xB6);
    public static final Color INK = new Color(0x3B, 0x23, 0x14);
    public static final Color TEXT = new Color(0xFF, 0xF4, 0xE2);
    public static final Color TEXT_MUTED = new Color(0xE2, 0xCC, 0xD8);
    public static final Color PINK = new Color(0xFF, 0x9F, 0xC6);

    private static final Map<Long, Font> FONTS = new HashMap<>();

    private CasinoTheme() {
    }

    public static Graphics2D quality(Graphics2D source) {
        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    /** 依設計尺寸換算；結果至少 1。 */
    public static int px(float scale, double design) {
        return Math.max(1, (int) Math.round(design * scale));
    }

    /** 中文字型，最小 11pt，避免縮小後讀不到。 */
    public static Font chinese(boolean bold, float scale, double design) {
        int size = Math.max(11, px(scale, design));
        long key = (bold ? 1L : 0L) << 32 | size;
        return FONTS.computeIfAbsent(key, k -> bold ? UiFonts.chineseBold(size) : UiFonts.chinesePlain(size));
    }

    /** 襯線金字（BLACKJACK、VS、Blackjack）。 */
    public static Font serif(float scale, double design, boolean italic) {
        int size = Math.max(11, px(scale, design));
        return new Font(Font.SERIF, Font.BOLD | (italic ? Font.ITALIC : 0), size);
    }

    public static Font latin(float scale, double design) {
        return UiFonts.latinBold(Math.max(11, px(scale, design)));
    }

    /** 由上到下的金屬金漸層。 */
    public static Paint gold(float y0, float y1) {
        if (y1 <= y0) {
            y1 = y0 + 1;
        }
        return new LinearGradientPaint(0, y0, 0, y1,
                new float[]{0f, 0.35f, 0.55f, 1f},
                new Color[]{GOLD_LIGHT, GOLD, GOLD_DEEP, GOLD});
    }

    /** 沿著形狀描一圈金邊：外側深色、中間金漸層、內側亮線。 */
    public static void goldFrame(Graphics2D g, Shape shape, float thickness) {
        java.awt.Rectangle b = shape.getBounds();
        g.setColor(GOLD_DARK);
        g.setStroke(new BasicStroke(thickness + 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.setPaint(gold(b.y, b.y + b.height));
        g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.setColor(new Color(255, 250, 220, 120));
        g.setStroke(new BasicStroke(Math.max(1f, thickness / 5f)));
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
    }

    /** 金色描邊大字：先畫深色外框，再填金漸層。回傳文字寬度。 */
    public static int goldText(Graphics2D g, String text, Font font, float x, float baseline, float outline) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        FontRenderContext frc = g.getFontRenderContext();
        TextLayout layout = new TextLayout(text, font, frc);
        Shape glyphs = layout.getOutline(AffineTransform.getTranslateInstance(x, baseline));
        java.awt.Rectangle b = glyphs.getBounds();
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(AffineTransform.getTranslateInstance(0, Math.max(1, outline)).createTransformedShape(glyphs));
        g.setColor(GOLD_DARK);
        g.setStroke(new BasicStroke(outline * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(glyphs);
        g.setPaint(gold(b.y, b.y + b.height));
        g.fill(glyphs);
        g.setStroke(new BasicStroke(1f));
        return Math.round(layout.getAdvance());
    }

    /** 從元件往上找視窗設定的縮放；找不到就是 1。 */
    public static float scaleOf(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (c instanceof javax.swing.JComponent) {
                Object value = ((javax.swing.JComponent) c).getClientProperty(SCALE_KEY);
                if (value instanceof Float) {
                    return (Float) value;
                }
            }
        }
        return 1f;
    }

    public static final String SCALE_KEY = "casino.scale";

    /** 把新的縮放比例告訴整棵元件樹。 */
    public static void applyScale(Container root, float scale) {
        if (root instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) root).putClientProperty(SCALE_KEY, scale);
        }
        apply(root, scale);
        root.revalidate();
        root.repaint();
    }

    private static void apply(Component component, float scale) {
        if (component instanceof Scalable) {
            ((Scalable) component).setUiScale(scale);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                apply(child, scale);
            }
        }
    }

    /** 跟著視窗縮放的元件。 */
    public interface Scalable {
        void setUiScale(float scale);
    }

    public static float clampScale(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }
}
