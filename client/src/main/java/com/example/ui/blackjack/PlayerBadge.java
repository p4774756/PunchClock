package com.example.ui.blackjack;

import com.example.ui.CandyTheme;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;

/**
 * 玩家列：左邊金框頭像與名牌，右邊點數與狀態標籤。輪到這位玩家時頭像會呼吸發光。
 */
public final class PlayerBadge extends JComponent implements CasinoTheme.Scalable {

    /** 標籤樣式 */
    public enum Tag {
        STAND("停牌", new Color(0xC4, 0x1E, 0x3A), CasinoAssets.PILL_RED, CasinoIcons.HAND),
        BUST("爆牌！", new Color(0x7E, 0x52, 0xD6), CasinoAssets.PILL_PURPLE, null),
        TWENTY_ONE("21 點！", new Color(0xC8, 0x8A, 0x1E), CasinoAssets.PILL_GOLD, null);

        final String text;
        final Color fill;
        final String asset;
        final CasinoIcons icon;

        Tag(String text, Color fill, String asset, CasinoIcons icon) {
            this.text = text;
            this.fill = fill;
            this.asset = asset;
            this.icon = icon;
        }
    }

    private final String role;
    private final String fallbackAsset;
    private String name = "";
    private Image avatar;
    private int score = -1;
    static final String FINISHED_TEXT = "已結束";
    private static final Color FINISHED_INK = new Color(0xE8, 0xD8, 0xC0);

    private Tag tag;
    private boolean finished;
    private boolean active;
    private float scale = 1f;
    private Timer pulse;
    private long pulseStart;

    /**
     * @param fallbackAsset 沒有頭像時的預設角色素材；null 時依名字挑對手預設圖
     */
    public PlayerBadge(String role, String fallbackAsset) {
        this.role = role;
        this.fallbackAsset = fallbackAsset;
        setOpaque(false);
    }

    public void setPlayer(String name, Image avatar) {
        this.name = name == null ? "" : name;
        this.avatar = avatar;
        repaint();
    }

    String playerName() {
        return name;
    }

    /** 用這位玩家的頭像（自訂優先，否則預設角色）畫一個金框圓章，給結算面板用。 */
    void paintMedallion(Graphics2D g, double cx, double cy, double d, float glow) {
        Medallion.paint(g, cx, cy, d, avatar,
                fallbackAsset != null ? fallbackAsset : CasinoAssets.peerDefault(name), name, glow);
    }

    /** 負數代表還沒有點數。 */
    public void setScore(int score) {
        this.score = score;
        repaint();
    }

    public void setTag(Tag tag) {
        setTag(tag, false);
    }

    /** @param finished 這局已結算：標籤改成扁平的「已結束」類文字，不再像操作鈕 */
    public void setTag(Tag tag, boolean finished) {
        this.tag = tag;
        this.finished = finished;
        repaint();
    }

    /** 目前畫在右側的標籤文字；沒有標籤時為 null。 */
    String tagText() {
        if (finished) {
            return tag == null || tag == Tag.STAND ? FINISHED_TEXT : tag.text;
        }
        return tag == null ? null : tag.text;
    }

    public void setActive(boolean value) {
        if (active == value) {
            return;
        }
        active = value;
        if (active) {
            pulseStart = System.currentTimeMillis();
            if (pulse == null) {
                pulse = new Timer(40, e -> repaint());
            }
            if (isDisplayable()) {
                pulse.start();
            }
        } else if (pulse != null) {
            pulse.stop();
        }
        repaint();
    }

    @Override
    public void setUiScale(float value) {
        scale = value;
        revalidate();
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (active && pulse != null) {
            pulse.start();
        }
    }

    @Override
    public void removeNotify() {
        if (pulse != null) {
            pulse.stop();
        }
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(CasinoTheme.px(scale, 640), CasinoTheme.px(scale, 86));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(CasinoTheme.px(scale, 360), CasinoTheme.px(scale, 76));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
        int h = getHeight();
        double d = Math.min(h - 4, CasinoTheme.px(scale, 84));
        double cy = h / 2.0;
        double mx = 2 + d / 2;

        int right = getWidth() - CasinoTheme.px(scale, 4);
        int tagW = 0;
        if (finished) {
            Color accent = tag == Tag.BUST ? new Color(0xFF, 0x9A, 0xAA)
                    : tag == Tag.TWENTY_ONE ? CasinoTheme.GOLD_LIGHT : FINISHED_INK;
            tagW = CasinoPaint.flatTag(g, right, cy, tagText(), accent, scale) + CasinoTheme.px(scale, 12);
        } else if (tag != null) {
            tagW = CasinoPaint.pill(g, right, cy, tag.text, null, tag.fill, tag.asset, tag.icon, scale)
                    + CasinoTheme.px(scale, 12);
        }
        int scoreW = CasinoPaint.pill(g, right - tagW, cy, "點數", score < 0 ? "–" : String.valueOf(score),
                CasinoTheme.PLUM, CasinoAssets.PILL_PLUM, null, scale);

        double plateX = mx + d * 0.3;
        double plateH = CasinoTheme.px(scale, 58);
        double maxPlate = right - tagW - scoreW - CasinoTheme.px(scale, 20) - plateX;
        double plateW = Math.max(CasinoTheme.px(scale, 120), Math.min(CasinoTheme.px(scale, 300), maxPlate));
        CasinoPaint.plate(g, plateX, cy - plateH / 2, plateW, plateH, scale);

        float glow = 0f;
        if (active) {
            glow = 0.55f + 0.45f * (float) Math.sin((System.currentTimeMillis() - pulseStart) / 260.0);
        }
        paintMedallion(g, mx, cy, d, glow);

        double textX = mx + d / 2 + CasinoTheme.px(scale, 12);
        int room = (int) Math.max(20, plateX + plateW - CasinoTheme.px(scale, 16) - textX);
        Font nameFont = CasinoTheme.chinese(true, scale, 23);
        Font roleFont = CasinoTheme.chinese(false, scale, 15);
        FontMetrics nm = g.getFontMetrics(nameFont);
        FontMetrics rm = g.getFontMetrics(roleFont);
        int block = nm.getAscent() + rm.getAscent() + CasinoTheme.px(scale, 4);
        float base = (float) (cy - block / 2.0 + nm.getAscent());
        g.setFont(nameFont);
        g.setColor(CasinoTheme.TEXT);
        g.drawString(CandyTheme.fit(name, nm, room), (float) textX, base);
        g.setFont(roleFont);
        g.setColor(active ? CasinoTheme.GOLD_LIGHT : CasinoTheme.TEXT_MUTED);
        g.drawString(CandyTheme.fit(active ? role + "・行動中" : role, rm, room), (float) textX,
                base + rm.getAscent() + CasinoTheme.px(scale, 4));
        g.dispose();
    }
}
