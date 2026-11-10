package com.example.ui.blackjack;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * 牌桌用的小像素圖。圖形以字串陣列描述，每個字元對一個顏色，'.' 為透明。
 * 不用 emoji：Java2D 在 Windows／macOS 都畫不出彩色 emoji。
 */
public final class PixelArt {

    static final String[] TROPHY = {
            "..YYYYYYYY..",
            "YYYWYYYYYYYY",
            "Y.YWYYYYYY.Y",
            "Y.YYYYYYYY.Y",
            ".YYYYYYYYYY.",
            "...YYYYYY...",
            "....YYYY....",
            ".....YY.....",
            ".....YY.....",
            "...OOOOOO...",
            "...OOOOOO...",
    };

    static final String[] RAIN = {
            "....WWWW....",
            "..WWWWWWWW..",
            ".WWWWWWWWWW.",
            "WWWWWWWWWWWW",
            ".WWWWWWWWWW.",
            "............",
            "..B...B...B.",
            ".B...B...B..",
    };

    static final String[] STAR = {
            "....Y....",
            "....Y....",
            "...YYY...",
            "YYYYYYYYY",
            ".YYYYYYY.",
            "..YYYYY..",
            "..YY.YY..",
            ".YY...YY.",
    };

    static final String[] BENTO = {
            "RRRRRRRRRRRR",
            "RWWWWWRGGGGR",
            "RWWWWWRGGGGR",
            "RWWPWWRYYYYR",
            "RWWWWWRYYYYR",
            "RRRRRRRRRRRR",
    };

    static final String[] HEART = {
            ".HH.HH.",
            "HHHHHHH",
            "HHHHHHH",
            ".HHHHH.",
            "..HHH..",
            "...H...",
    };

    static final String[] CROWN = {
            "Y..Y..Y",
            "YY.Y.YY",
            "YYYYYYY",
            "YRYYYRY",
            "YYYYYYY",
    };

    private static final Map<Character, Color> PALETTE = Map.of(
            'Y', new Color(0xFF, 0xD2, 0x5E),
            'W', new Color(0xFF, 0xFB, 0xF2),
            'O', new Color(0xB0, 0x74, 0x4E),
            'B', new Color(0x8E, 0xC5, 0xFF),
            'R', new Color(0xE8, 0x5A, 0x7A),
            'G', new Color(0x8F, 0xD9, 0x8B),
            'P', new Color(0xFF, 0x8F, 0xB8),
            'H', new Color(0xFF, 0x9A, 0xC4));

    private PixelArt() {
    }

    static int width(String[] art) {
        return art.length == 0 ? 0 : art[0].length();
    }

    static int height(String[] art) {
        return art.length;
    }

    static void draw(Graphics2D g, String[] art, int x, int y, int scale) {
        draw(g, art, x, y, scale, null);
    }

    /** {@code tint} 不為 null 時，所有非透明像素都用這個顏色（陰影、剪影用）。 */
    static void draw(Graphics2D g, String[] art, int x, int y, int scale, Color tint) {
        for (int row = 0; row < art.length; row++) {
            String line = art[row];
            for (int col = 0; col < line.length(); col++) {
                Color color = tint != null ? tint : PALETTE.get(line.charAt(col));
                if (line.charAt(col) == '.' || color == null) {
                    continue;
                }
                g.setColor(color);
                g.fillRect(x + col * scale, y + row * scale, scale, scale);
            }
        }
    }

    /** 午餐便當小圖示，給標籤、面板用。 */
    public static Icon bentoIcon(int scale) {
        int s = Math.max(1, scale);
        BufferedImage image = new BufferedImage(width(BENTO) * s, height(BENTO) * s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            draw(g, BENTO, 0, 0, s);
        } finally {
            g.dispose();
        }
        return new ImageIcon(image);
    }

    /** 有 1 格深色陰影的版本，貼在綠色牌桌上比較跳。 */
    static void drawShadowed(Graphics2D g, String[] art, int x, int y, int scale) {
        draw(g, art, x + scale / 2 + 1, y + scale / 2 + 1, scale, new Color(10, 30, 20, 110));
        draw(g, art, x, y, scale);
    }
}
