package com.example.ui.hud;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 相簿首頁泡泡：放在頭頂上空，不可蓋臉／胸口／手；畫成純圓角，沒有尖角尾巴。
 * 使用合成圖，不讀取 ~/.sklbaby。
 */
public class Issue894AlbumBubbleTest {

    private static final int W = 400;
    private static final int H = 520;
    private static final int BOX_W = 160;
    private static final int BOX_H = 52;
    private static final Rectangle STAGE = new Rectangle(0, 0, 380, 500);

    @Test
    public void centeredFigureBubbleStaysInTopBandAndMissesTorso() throws Exception {
        BufferedImage img = sceneCenteredFigure();
        HomeBubbleAnchor.Spec spec = HomeAlbumAnchor.estimate(img);
        assertNotNull("should find a face", spec);
        HomeBubbleAnchor.Placement placement = HomeBubbleAnchor.placeBubble(
                STAGE, spec, img.getWidth(), img.getHeight(), 0, BOX_W, BOX_H, swapButton());
        assertTopBand(placement.box);
        assertMisses(placement.box, torsoBox());
        assertTrue("bubble should sit in a top corner, not over the chest",
                placement.box.x <= 24 || placement.box.x + placement.box.width >= STAGE.width - 24);
        maybeWrite("centered-figure", img, placement);
    }

    @Test
    public void raisedHandsBubbleStaysAboveHandsInTopLeft() throws Exception {
        BufferedImage img = sceneRaisedHands();
        HomeBubbleAnchor.Spec spec = HomeAlbumAnchor.estimate(img);
        assertNotNull(spec);
        HomeBubbleAnchor.Placement placement = HomeBubbleAnchor.placeBubble(
                STAGE, spec, img.getWidth(), img.getHeight(), 0, BOX_W, BOX_H, swapButton());
        assertTopBand(placement.box);
        assertMisses(placement.box, new Rectangle(
                (int) (STAGE.width * 0.18), (int) (STAGE.height * 0.32),
                (int) (STAGE.width * 0.64), (int) (STAGE.height * 0.28)));
        maybeWrite("raised-hands", img, placement);
    }

    @Test
    public void closeUpFaceBubbleDoesNotCoverEyes() throws Exception {
        BufferedImage img = sceneCloseUpFace();
        HomeBubbleAnchor.Spec spec = HomeAlbumAnchor.estimate(img);
        assertNotNull(spec);
        HomeBubbleAnchor.Placement placement = HomeBubbleAnchor.placeBubble(
                STAGE, spec, img.getWidth(), img.getHeight(), 0, BOX_W, BOX_H, swapButton());
        // 眼睛約在畫面 190/520 高度；泡泡不得蓋到那一帶的臉中央。
        Rectangle eyes = new Rectangle(
                (int) (STAGE.width * 0.30),
                (int) (STAGE.height * 0.32),
                (int) (STAGE.width * 0.40),
                (int) (STAGE.height * 0.12));
        assertMisses(placement.box, eyes);
        assertTopBand(placement.box);
        maybeWrite("closeup-face", img, placement);
    }

    @Test
    public void paintedBubbleHasNoPointerTail() {
        Rectangle box = new Rectangle(24, 16, 160, 64);
        BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(20, 30, 50));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            HomeBoard.paintBubbleShape(g, box);
        } finally {
            g.dispose();
        }
        Color panel = new Color(248, 248, 244);
        int outside = 0;
        // 影子會往右下偏 1–2px，其餘面板色不該跑出圓角矩形（沒有尖角尾巴）。
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color c = new Color(img.getRGB(x, y), true);
                if (c.getAlpha() < 40) {
                    continue;
                }
                boolean looksPanel = Math.abs(c.getRed() - panel.getRed()) <= 20
                        && Math.abs(c.getGreen() - panel.getGreen()) <= 20
                        && Math.abs(c.getBlue() - panel.getBlue()) <= 24;
                if (!looksPanel) {
                    continue;
                }
                boolean insideShadowPad = x >= box.x && x <= box.x + box.width + 2
                        && y >= box.y && y <= box.y + box.height + 2;
                if (!insideShadowPad) {
                    outside++;
                }
            }
        }
        assertTrue("plain bubble should not paint a pointer outside the rounded box, outside=" + outside,
                outside == 0);
    }

    private static void assertTopBand(Rectangle box) {
        assertTrue("bubble should stay in the top third of the panel, was " + box,
                box.y + box.height <= STAGE.y + (int) Math.round(STAGE.height * 0.38));
    }

    private static void assertMisses(Rectangle box, Rectangle avoid) {
        Rectangle hit = box.intersection(avoid);
        assertFalse("bubble " + box + " overlaps " + avoid,
                hit.width > 4 && hit.height > 4);
    }

    private static Rectangle swapButton() {
        return new Rectangle(STAGE.x + STAGE.width - 72, STAGE.y + 8, 64, 28);
    }

    private static Rectangle torsoBox() {
        return new Rectangle(
                (int) (STAGE.width * 0.28),
                (int) (STAGE.height * 0.36),
                (int) (STAGE.width * 0.44),
                (int) (STAGE.height * 0.40));
    }

    private static Rectangle faceBoxFromSpec(HomeBubbleAnchor.Spec spec, BufferedImage img) {
        Rectangle draw = HomeBubbleAnchor.imageDrawRect(
                STAGE.width, STAGE.height, img.getWidth(), img.getHeight(), 0);
        Rectangle face = HomeBubbleAnchor.imageNormRectToPanel(
                spec.faceX, spec.faceY, spec.faceW, spec.faceH,
                draw, img.getWidth(), img.getHeight(), 0);
        face.translate(STAGE.x, STAGE.y);
        return face;
    }

    /** 中景站姿：臉在上方三分之一，身體在中央，左上是空的暗色背景。 */
    private static BufferedImage sceneCenteredFigure() {
        BufferedImage img = backdrop();
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(224, 176, 150));
            // face
            g.fillOval(155, 70, 90, 110);
            // torso
            g.setColor(new Color(210, 150, 130));
            g.fillOval(140, 175, 120, 200);
            // hands down at hips
            g.fillOval(120, 300, 40, 40);
            g.fillOval(240, 300, 40, 40);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** 雙手舉高：手約在畫面中左／中右、胸口高度，左上背景仍空。 */
    private static BufferedImage sceneRaisedHands() {
        BufferedImage img = backdrop();
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(224, 176, 150));
            g.fillOval(160, 90, 80, 100);
            g.setColor(new Color(210, 150, 130));
            g.fillOval(145, 185, 110, 190);
            // raised hands mid-left and mid-right, below the top band
            g.setColor(new Color(224, 176, 150));
            g.fillOval(70, 200, 50, 50);
            g.fillOval(280, 200, 50, 50);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** 大臉特寫：臉佔畫面中央偏上，眼睛約在中線附近。 */
    private static BufferedImage sceneCloseUpFace() {
        BufferedImage img = backdrop();
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(224, 176, 150));
            g.fillOval(90, 80, 220, 280);
            g.setColor(new Color(60, 40, 40));
            g.fillOval(140, 190, 36, 24);
            g.fillOval(224, 190, 36, 24);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static BufferedImage backdrop() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(28, 36, 48));
            g.fillRect(0, 0, W, H);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static void maybeWrite(String name, BufferedImage src, HomeBubbleAnchor.Placement placement)
            throws Exception {
        String dir = System.getenv("SHOTS_ALBUM_BUBBLE_DIR");
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("shots.albumBubble.dir");
        }
        if (dir == null || dir.isBlank()) {
            dir = "target/album-bubble-shots";
        }
        Path out = Path.of(dir);
        Files.createDirectories(out);
        BufferedImage shot = new BufferedImage(STAGE.width, STAGE.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = shot.createGraphics();
        try {
            Rectangle draw = HomeBubbleAnchor.imageDrawRect(
                    STAGE.width, STAGE.height, src.getWidth(), src.getHeight(), 0);
            g.drawImage(src, draw.x, draw.y, draw.width, draw.height, null);
            HomeBoard.paintBubbleShape(g, placement.box);
        } finally {
            g.dispose();
        }
        ImageIO.write(shot, "PNG", out.resolve(name + ".png").toFile());
    }
}
