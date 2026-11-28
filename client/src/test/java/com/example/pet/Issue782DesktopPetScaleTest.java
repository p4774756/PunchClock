package com.example.pet;

import com.example.ui.UiFonts;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** #782：站立尺度、Retina 貼圖兩路徑、台詞泡泡可讀；contact sheet 僅在 SHOTS782_DIR 時寫檔。 */
public class Issue782DesktopPetScaleTest {

    @Test
    public void standingBodyHeightsStayWithinFivePercentOfIdle() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        int ref =
                bodyHeight(DesktopPetMiluLayout.placement(spriteNamed(frames, "idle/breath_01")));
        for (DesktopPetSpriteLoader.Sprite sprite : frames.allSprites()) {
            if (sprite == null || sprite.name == null) {
                continue;
            }
            if (!isStandingBody(sprite.name)) {
                continue;
            }
            int height = bodyHeight(DesktopPetMiluLayout.placement(sprite));
            double ratio = height / (double) ref;
            assertTrue(
                    sprite.name + " body height ratio " + String.format(Locale.ROOT, "%.3f", ratio),
                    ratio >= 0.95 && ratio <= 1.05);
        }
    }

    @Test
    public void blitPathsMatchLogicalFootprintAtScaleOneAndTwo() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle view = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation anim = new DesktopPetAnimation();
        for (double pixelScale : new double[] {1, 2}) {
            Rectangle scaled = logicalOpaqueAfterBlit(view, frames, anim, pixelScale, true);
            Rectangle device = logicalOpaqueAfterBlit(view, frames, anim, pixelScale, false);
            assertTrue(
                    "width @" + pixelScale,
                    Math.abs(scaled.width - device.width) <= 2);
            assertTrue(
                    "height @" + pixelScale,
                    Math.abs(scaled.height - device.height) <= 2);
        }
    }

    @Test
    public void speechBubbleFontReadsAsFourteenPointLogicalOnRetinaBuffer() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle view = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation anim = new DesktopPetAnimation();
        anim.playCare(DesktopPetAnimation.Mode.PAT, "好舒服～");
        double pixelScale = 2;
        int bw = DesktopPetMiluLayout.bufferPixelWidth(view.width, pixelScale);
        int bh = DesktopPetMiluLayout.bufferPixelHeight(view.height, pixelScale);
        BufferedImage buf = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buf.createGraphics();
        try {
            g.translate(-view.x, -view.y);
            g.scale(pixelScale, pixelScale);
            DesktopPetWindow.paintSpeechBubble(g, view, frames, anim, "好舒服～");
            g.setFont(UiFonts.chinesePlain(14));
            FontMetrics metrics = g.getFontMetrics();
            assertTrue(metrics.getHeight() >= 18);
            assertTrue(metrics.getAscent() >= 13);
        } finally {
            g.dispose();
        }
        boolean ink = false;
        for (int y = 0; y < bh && !ink; y++) {
            for (int x = 0; x < bw; x++) {
                int argb = buf.getRGB(x, y);
                if ((argb >>> 24) < 32) {
                    continue;
                }
                Color c = new Color(argb, true);
                if (c.getRed() < 120 && c.getGreen() < 80 && c.getBlue() < 100) {
                    ink = true;
                    break;
                }
            }
        }
        assertTrue("bubble should render dark ink on retina buffer", ink);
    }

    @Test
    public void contactSheetBuildsForMajorModesInMemory() throws Exception {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        List<Shot> shots = collectVerificationShots(frames, shell);
        assertTrue(shots.size() >= 12);
        BufferedImage sheet = composeContactSheet(shots, shell.width, shell.height);
        assertTrue(sheet.getWidth() > shell.width);
        assertTrue(sheet.getHeight() > shell.height);
        DesktopPetWindow.assertOpaqueWithinViewport(sheet, 12);

        String dir = System.getenv("SHOTS782_DIR");
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("shots782.dir");
        }
        if (dir != null && !dir.isBlank()) {
            Path out = Path.of(dir);
            Files.createDirectories(out);
            ImageIO.write(sheet, "png", out.resolve("milu-contact-sheet.png").toFile());
        }
    }

    private static int bodyHeight(DesktopPetMiluLayout.Placement placement) {
        return placement.footDisplayY - placement.headDisplayTop;
    }

    private static boolean isStandingBody(String name) {
        if (name.startsWith("sit/") || name.startsWith("sleep/")) {
            return false;
        }
        if (name.startsWith("pat/")
                || "walk/left_01".equals(name)
                || "walk/right_01".equals(name)) {
            return false;
        }
        return name.startsWith("idle/")
                || name.startsWith("walk/")
                || name.startsWith("play/")
                || name.startsWith("greet/")
                || name.startsWith("feed/")
                || name.startsWith("think/")
                || name.startsWith("busy/")
                || name.startsWith("worry/")
                || name.startsWith("hungry/")
                || name.startsWith("struggle/")
                || name.startsWith("grab/")
                || name.startsWith("carry/")
                || name.startsWith("drop/");
    }

    private static Rectangle logicalOpaqueAfterBlit(
            Rectangle view,
            DesktopPetSpriteLoader.Frames frames,
            DesktopPetAnimation animation,
            double pixelScale,
            boolean scaledGraphics) {
        int bw = DesktopPetMiluLayout.bufferPixelWidth(view.width, pixelScale);
        int bh = DesktopPetMiluLayout.bufferPixelHeight(view.height, pixelScale);
        BufferedImage frame = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        DesktopPetWindow.paintMiluViewport(frame, view, frames, animation, 0, 0, 0, 1, pixelScale);
        BufferedImage target = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            if (scaledGraphics) {
                g.setTransform(AffineTransform.getScaleInstance(pixelScale, pixelScale));
            }
            DesktopPetMiluLayout.drawLiveFrame(g, frame, view.width, view.height, false);
        } finally {
            g.dispose();
        }
        Rectangle opaque = DesktopPetWindow.opaqueBounds(target, 16);
        return toLogical(opaque, pixelScale);
    }

    private static Rectangle toLogical(Rectangle device, double pixelScale) {
        if (pixelScale < 1) {
            pixelScale = 1;
        }
        return new Rectangle(
                (int) Math.round(device.x / pixelScale),
                (int) Math.round(device.y / pixelScale),
                (int) Math.round(device.width / pixelScale),
                (int) Math.round(device.height / pixelScale));
    }

    private static List<Shot> collectVerificationShots(
            DesktopPetSpriteLoader.Frames frames, Rectangle shell) {
        List<Shot> shots = new ArrayList<>();
        shots.add(shot("idle", frames, shell, new DesktopPetAnimation()));
        DesktopPetAnimation walk = new DesktopPetAnimation();
        walk.forceWalk(1);
        shots.add(shot("walk", frames, shell, walk));
        DesktopPetAnimation pat = new DesktopPetAnimation();
        pat.playCare(DesktopPetAnimation.Mode.PAT, "好舒服～");
        shots.add(shot("pat-bubble", frames, shell, pat));
        DesktopPetAnimation feed = new DesktopPetAnimation();
        feed.playCare(DesktopPetAnimation.Mode.FEED, "好好吃～");
        shots.add(shot("feed", frames, shell, feed));
        DesktopPetAnimation play = new DesktopPetAnimation();
        play.playCare(DesktopPetAnimation.Mode.PLAY, "玩一玩！");
        shots.add(shot("play-care", frames, shell, play));
        DesktopPetAnimation think = new DesktopPetAnimation();
        think.setCursorHold(DesktopPetAnimation.Mode.THINK);
        shots.add(shot("think", frames, shell, think));
        DesktopPetAnimation busy = new DesktopPetAnimation();
        busy.setCursorHold(DesktopPetAnimation.Mode.BUSY);
        shots.add(shot("busy", frames, shell, busy));
        DesktopPetAnimation worry = new DesktopPetAnimation();
        worry.flashWorry("嗯？");
        shots.add(shot("worry", frames, shell, worry));
        DesktopPetAnimation hungry = new DesktopPetAnimation(new Random(17));
        hungry.setCareIdle(true, 12, 80, 80);
        for (int i = 0; i < 400; i++) {
            hungry.tick();
            if (hungry.mode() == DesktopPetAnimation.Mode.HUNGRY) {
                break;
            }
        }
        Assume.assumeTrue(hungry.mode() == DesktopPetAnimation.Mode.HUNGRY);
        shots.add(shot("hungry", frames, shell, hungry));
        DesktopPetAnimation carried = new DesktopPetAnimation();
        carried.setCarried(true);
        for (int i = 0; i < DesktopPetAnimation.GRAB_TICKS; i++) {
            carried.tick();
        }
        shots.add(shot("grab", frames, shell, carried));
        DesktopPetAnimation react = new DesktopPetAnimation();
        react.poke();
        shots.add(shot("poke", frames, shell, react));
        DesktopPetAnimation sit = sittingAnim();
        shots.add(shot("sit", frames, shell, sit));
        DesktopPetAnimation sleep = sleepingAnim(sit);
        shots.add(shot("sleep", frames, shell, sleep));
        return shots;
    }

    private static Shot shot(
            String label,
            DesktopPetSpriteLoader.Frames frames,
            Rectangle shell,
            DesktopPetAnimation animation) {
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        double pixelScale = 2;
        int bw = DesktopPetMiluLayout.bufferPixelWidth(view.width, pixelScale);
        int bh = DesktopPetMiluLayout.bufferPixelHeight(view.height, pixelScale);
        BufferedImage image = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        DesktopPetWindow.paintMiluViewport(image, view, frames, animation, 0, 0, 0, 1, pixelScale);
        if (animation.bubble() != null) {
            Graphics2D g = image.createGraphics();
            try {
                g.translate(-view.x, -view.y);
                g.scale(pixelScale, pixelScale);
                DesktopPetWindow.paintSpeechBubble(g, view, frames, animation, animation.bubble());
            } finally {
                g.dispose();
            }
        }
        return new Shot(label, image);
    }

    private static BufferedImage composeContactSheet(List<Shot> shots, int cellW, int cellH) {
        int cols = 4;
        int rows = (shots.size() + cols - 1) / cols;
        int gap = 4;
        BufferedImage sheet =
                new BufferedImage(
                        cols * (cellW + gap) + gap,
                        rows * (cellH + gap) + gap,
                        BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        try {
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int i = 0; i < shots.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int x = gap + col * (cellW + gap);
                int y = gap + row * (cellH + gap);
                BufferedImage frame = shots.get(i).image;
                g.drawImage(frame, x, y, cellW, cellH, null);
            }
        } finally {
            g.dispose();
        }
        return sheet;
    }

    private static DesktopPetAnimation sittingAnim() {
        DesktopPetAnimation animation = new DesktopPetAnimation(new Random(0) {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        });
        for (int i = 0; i < DesktopPetAnimation.IDLE_TICKS * 3; i++) {
            animation.tick();
        }
        Assume.assumeTrue(animation.mode() == DesktopPetAnimation.Mode.SIT);
        return animation;
    }

    private static DesktopPetAnimation sleepingAnim(DesktopPetAnimation sit) {
        DesktopPetAnimation animation = sit;
        for (int i = 0; i < DesktopPetAnimation.SIT_TICKS + 4; i++) {
            animation.tick();
        }
        Assume.assumeTrue(animation.mode() == DesktopPetAnimation.Mode.SLEEP);
        return animation;
    }

    private static DesktopPetSpriteLoader.Sprite spriteNamed(
            DesktopPetSpriteLoader.Frames frames, String name) {
        for (DesktopPetSpriteLoader.Sprite sprite : frames.allSprites()) {
            if (name.equals(sprite.name)) {
                return sprite;
            }
        }
        throw new AssertionError("missing sprite " + name);
    }

    private static final class Shot {
        final String label;
        final BufferedImage image;

        Shot(String label, BufferedImage image) {
            this.label = label;
            this.image = image;
        }
    }
}