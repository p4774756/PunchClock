package com.example.ui.hud;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 首頁泡泡是純圓角矩形，沒有漫畫尖角；PNG 僅在 SHOTS_BUBBLE_TAIL_DIR / shots.bubbleTail.dir 時寫檔。 */
public class HomeBubbleShapeReviewTest {

    private static final Color PANEL = new Color(248, 248, 244, 240);
    private static final Color BG = new Color(36, 52, 96);
    private static final String SAMPLE = "需要幫忙就叫我吧";

    @Test
    public void bubbleIsPlainRoundedRectWithoutPointer() throws Exception {
        double scale = 2;
        Rectangle stage = new Rectangle(0, 0, 400, 220);
        int w = (int) Math.round(stage.width * scale);
        int h = (int) Math.round(stage.height * scale);
        BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buf.createGraphics();
        Rectangle box;
        try {
            g.scale(scale, scale);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BG);
            g.fillRect(0, 0, stage.width, stage.height);

            HomeBoard board = new HomeBoard();
            board.layout(stage);
            board.setReducedMotion(true);
            board.setIllustrationContext(HomeIllustration.ROOM_B, 800, 1067, new Rectangle());
            board.setBubble(new HomeBubbleFormat.BubbleLine(SAMPLE, false));
            board.paint(g, 0);
            box = board.bubbleBoxForTests();
        } finally {
            g.dispose();
        }

        assertTrue("bubble should paint", box.width > 40 && box.height > 20);

        int pad = (int) Math.ceil(3 * scale);
        int boxLeft = (int) Math.round(box.x * scale) - pad;
        int boxRight = (int) Math.round((box.x + box.width) * scale) + pad;
        int boxTop = (int) Math.round(box.y * scale) - pad;
        int boxBottom = (int) Math.round((box.y + box.height) * scale) + pad;

        int inside = 0;
        int outside = 0;
        for (int y = 0; y < buf.getHeight(); y++) {
            for (int x = 0; x < buf.getWidth(); x++) {
                if (!isPanel(new Color(buf.getRGB(x, y), true))) {
                    continue;
                }
                boolean inBox = x >= boxLeft && x <= boxRight && y >= boxTop && y <= boxBottom;
                if (inBox) {
                    inside++;
                } else {
                    outside++;
                }
            }
        }
        assertTrue("rounded bubble should fill its box", inside > 200);
        assertEquals("no pointer tail should paint outside the rounded box, outside=" + outside, 0, outside);

        maybeWritePng(buf);
    }

    private static boolean isPanel(Color c) {
        if (c.getAlpha() < 40) {
            return false;
        }
        if (Math.abs(c.getRed() - PANEL.getRed()) <= 20
                && Math.abs(c.getGreen() - PANEL.getGreen()) <= 20
                && Math.abs(c.getBlue() - PANEL.getBlue()) <= 24) {
            return true;
        }
        return c.getRed() >= 215 && c.getGreen() >= 215 && c.getBlue() >= 205;
    }

    private static void maybeWritePng(BufferedImage buf) throws Exception {
        String dir = System.getenv("SHOTS_BUBBLE_TAIL_DIR");
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("shots.bubbleTail.dir");
        }
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path out = Path.of(dir);
        Files.createDirectories(out);
        ImageIO.write(buf, "png", out.resolve("bubble-shape-review.png").toFile());
    }
}
