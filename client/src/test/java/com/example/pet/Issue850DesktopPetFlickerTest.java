package com.example.pet;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 待機呼吸／眨眼／走路與操控跳躍連續擷幀不得空白或接近全透明；視野外框在同一姿勢內穩定。
 * 貼回視窗時，邏輯座標裁切不得把 Retina 緩衝畫成兩倍大；站立姿勢可見外框須維持相近尺寸。
 */
public class Issue850DesktopPetFlickerTest {

    private static final int OPAQUE_THRESHOLD = 16;
    /** 同一姿勢家族（呼吸、眨眼、困擾）可見外框允許的偏差。 */
    private static final double TIGHT_SIZE_TOLERANCE = 0.08;
    /** 走路／跳躍等側身或離地姿勢，寬度允許稍大變化，但絕不能到接近兩倍。 */
    private static final double LOOSE_SIZE_CEILING = 1.35;

    @Test
    public void idleBreathFullCycleStaysOpaqueAcrossFrameBoundaries() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        int ticksPerBreath = DesktopPetAnimation.BREATH_HOLD_TICKS;
        int totalTicks = ticksPerBreath * 4 + ticksPerBreath;
        Rectangle ref = null;
        for (int t = 0; t < totalTicks; t++) {
            animation.tick();
            assertEquals(view, DesktopPetWindow.viewForPose(frames, shell, animation));
            BufferedImage shot = assertFrameOpaque(frames, shell, animation, t, "breath");
            Rectangle box = DesktopPetWindow.opaqueBounds(shot, OPAQUE_THRESHOLD);
            if (ref == null) {
                ref = box;
            } else {
                assertSizeClose("breath tick " + t, ref, box, TIGHT_SIZE_TOLERANCE);
            }
        }
    }

    @Test
    public void blinkRoundStaysOpaqueAndViewStable() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        animation.armBlinkNow();
        int blinkTicks =
                DesktopPetAnimation.BLINK_HOLD_TICKS * 4 + DesktopPetAnimation.BLINK_HOLD_TICKS;
        Rectangle ref = null;
        for (int t = 0; t < blinkTicks; t++) {
            animation.tick();
            assertEquals(view, DesktopPetWindow.viewForPose(frames, shell, animation));
            BufferedImage shot = assertFrameOpaque(frames, shell, animation, t, "blink");
            Rectangle box = DesktopPetWindow.opaqueBounds(shot, OPAQUE_THRESHOLD);
            if (ref == null) {
                ref = box;
            } else {
                assertSizeClose("blink tick " + t, ref, box, TIGHT_SIZE_TOLERANCE);
            }
        }
    }

    @Test
    public void walkSegmentStaysOpaque() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.forceWalk(1);
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        int ticks = DesktopPetAnimation.ACTION_HOLD_TICKS
                * Math.max(1, frames.walkLeftCount()) * 2;
        Rectangle ref = null;
        for (int t = 0; t < ticks; t++) {
            animation.tick();
            assertEquals(view, DesktopPetWindow.viewForPose(frames, shell, animation));
            BufferedImage shot = assertFrameOpaque(frames, shell, animation, t, "walk");
            Rectangle box = DesktopPetWindow.opaqueBounds(shot, OPAQUE_THRESHOLD);
            if (ref == null) {
                ref = box;
            } else {
                assertNotDoubled("walk tick " + t, ref, box);
            }
        }
    }

    @Test
    public void controlJumpArcStaysOpaque() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.setPlayerControl(true);
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        control.applyKeyPressed(KeyEvent.VK_D);
        control.startJump();
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        Rectangle ref = null;
        for (int t = 0; t < DesktopPetControl.JUMP_TICKS; t++) {
            control.tickJump();
            animation.syncPlayerWalk(control.walkDirection());
            animation.tick();
            assertEquals(view, DesktopPetWindow.viewForPose(frames, shell, animation));
            BufferedImage shot = assertFrameOpaque(frames, shell, animation, t, "jump");
            Rectangle box = DesktopPetWindow.opaqueBounds(shot, OPAQUE_THRESHOLD);
            if (ref == null) {
                ref = box;
            } else {
                assertTrue(
                        "jump tick " + t + " width ratio " + ratio(box.width, ref.width),
                        ratio(box.width, ref.width) <= LOOSE_SIZE_CEILING
                                && ratio(ref.width, box.width) <= LOOSE_SIZE_CEILING);
            }
        }
    }

    @Test
    public void identityGraphicsWithLogicalClipDoesNotBlitAtRetinaSize() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle view = DesktopPetWindow.stableShellFor(frames);
        int lw = view.width;
        int lh = view.height;
        BufferedImage retina = new BufferedImage(lw * 2, lh * 2, BufferedImage.TYPE_INT_ARGB);
        BufferedImage logical = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D logicalGraphics = logical.createGraphics();
        try {
            Rectangle dest = DesktopPetMiluLayout.blitDestRect(logicalGraphics, lw, lh, retina);
            assertEquals("logical-clip dest width", lw, dest.width);
            assertEquals("logical-clip dest height", lh, dest.height);
        } finally {
            logicalGraphics.dispose();
        }
        BufferedImage device = new BufferedImage(lw * 2, lh * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D deviceGraphics = device.createGraphics();
        try {
            Rectangle dest = DesktopPetMiluLayout.blitDestRect(deviceGraphics, lw, lh, retina);
            assertEquals("device-clip dest stays logical", lw, dest.width);
            assertEquals("device-clip dest stays logical", lh, dest.height);
        } finally {
            deviceGraphics.dispose();
        }
        BufferedImage scaledTarget = new BufferedImage(lw * 2, lh * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D scaledGraphics = scaledTarget.createGraphics();
        try {
            scaledGraphics.setTransform(AffineTransform.getScaleInstance(2, 2));
            Rectangle dest = DesktopPetMiluLayout.blitDestRect(scaledGraphics, lw, lh, retina);
            assertEquals("scaled dest width", lw, dest.width);
            assertEquals("scaled dest height", lh, dest.height);
        } finally {
            scaledGraphics.dispose();
        }
    }

    @Test
    public void retinaFrameBlittedOntoLogicalCanvasKeepsFullBodyAndIdleSize() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle view = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.tick();
        BufferedImage logicalPaint = DesktopPetWindow.paintMiluViewport(view, frames, animation, false);
        Rectangle logicalBox = DesktopPetWindow.opaqueBounds(logicalPaint, OPAQUE_THRESHOLD);
        assertTrue("logical paint must be opaque", logicalBox.width > 1 && logicalBox.height > 1);

        double pixelScale = 2;
        int bw = DesktopPetMiluLayout.bufferPixelWidth(view.width, pixelScale);
        int bh = DesktopPetMiluLayout.bufferPixelHeight(view.height, pixelScale);
        BufferedImage retina = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        DesktopPetWindow.paintMiluViewport(retina, view, frames, animation, 0, 0, 0, 1, pixelScale);

        BufferedImage logicalSurface = new BufferedImage(view.width, view.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = logicalSurface.createGraphics();
        try {
            DesktopPetMiluLayout.drawLiveFrame(g, retina, view.width, view.height, false);
        } finally {
            g.dispose();
        }
        DesktopPetWindow.assertOpaqueWithinViewport(logicalSurface, OPAQUE_THRESHOLD);
        Rectangle blitted = DesktopPetWindow.opaqueBounds(logicalSurface, OPAQUE_THRESHOLD);
        assertSizeClose("retina-buffer onto logical canvas", logicalBox, blitted, TIGHT_SIZE_TOLERANCE);
        assertTrue(
                "feet should remain in the lower half, not a 2x crop of the head",
                blitted.y + blitted.height >= logicalSurface.getHeight() / 2);
    }

    @Test
    public void visibleBoundingBoxStaysComparableAcrossPoses() throws Exception {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        List<NamedShot> shots = new ArrayList<NamedShot>();

        DesktopPetAnimation idle = new DesktopPetAnimation();
        idle.tick();
        shots.add(capture("idle", frames, shell, idle));

        DesktopPetAnimation blink = new DesktopPetAnimation();
        blink.armBlinkNow();
        blink.tick();
        shots.add(capture("blink", frames, shell, blink));

        DesktopPetAnimation walk = new DesktopPetAnimation();
        walk.forceWalk(1);
        walk.tick();
        shots.add(capture("walk", frames, shell, walk));

        DesktopPetAnimation worry = new DesktopPetAnimation();
        assertTrue(worry.flashWorry("？"));
        worry.tick();
        shots.add(capture("worry", frames, shell, worry));

        DesktopPetAnimation busy = new DesktopPetAnimation();
        busy.setCursorHold(DesktopPetAnimation.Mode.BUSY);
        busy.tick();
        shots.add(capture("busy", frames, shell, busy));

        DesktopPetAnimation pat = new DesktopPetAnimation();
        pat.playCare(DesktopPetAnimation.Mode.PAT, "好舒服～");
        pat.tick();
        shots.add(capture("pat", frames, shell, pat));

        DesktopPetAnimation sit = sittingAnim();
        shots.add(capture("sit", frames, shell, sit));

        DesktopPetAnimation jump = new DesktopPetAnimation();
        jump.setPlayerControl(true);
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        control.startJump();
        for (int t = 0; t < Math.max(1, DesktopPetControl.JUMP_TICKS / 2); t++) {
            control.tickJump();
            jump.syncPlayerWalk(control.walkDirection());
            jump.tick();
        }
        shots.add(capture("jump", frames, shell, jump));

        Rectangle idleBox = shots.get(0).box;
        assertTrue("idle bbox", idleBox.width > 8 && idleBox.height > 8);
        for (NamedShot shot : shots) {
            assertEquals(shot.label + " view", shell, shot.view);
            DesktopPetWindow.assertOpaqueWithinViewport(shot.image, OPAQUE_THRESHOLD);
            if ("idle".equals(shot.label) || "blink".equals(shot.label) || "worry".equals(shot.label)
                    || "busy".equals(shot.label)) {
                assertSizeClose(shot.label, idleBox, shot.box, TIGHT_SIZE_TOLERANCE);
            } else {
                assertNotDoubled(shot.label, idleBox, shot.box);
                if (!"jump".equals(shot.label) && !"sit".equals(shot.label)) {
                    assertTrue(
                            shot.label + " width ratio " + ratio(shot.box.width, idleBox.width),
                            ratio(shot.box.width, idleBox.width) <= LOOSE_SIZE_CEILING
                                    && ratio(idleBox.width, shot.box.width) <= LOOSE_SIZE_CEILING);
                }
            }
        }
        writeShotsIfRequested(shots, frames, shell);
    }

    private static DesktopPetAnimation sittingAnim() {
        DesktopPetAnimation animation = new DesktopPetAnimation(new Random(0) {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        });
        for (int i = 0; i < DesktopPetAnimation.IDLE_TICKS; i++) {
            animation.tick();
        }
        assertEquals(DesktopPetAnimation.Mode.WALK, animation.mode());
        for (int i = 0; i < DesktopPetAnimation.WALK_TICKS; i++) {
            animation.tick();
        }
        assertEquals(DesktopPetAnimation.Mode.IDLE, animation.mode());
        for (int i = 0; i < DesktopPetAnimation.IDLE_TICKS; i++) {
            animation.tick();
        }
        assertEquals(DesktopPetAnimation.Mode.SIT, animation.mode());
        return animation;
    }

    private static NamedShot capture(
            String label,
            DesktopPetSpriteLoader.Frames frames,
            Rectangle shell,
            DesktopPetAnimation animation) {
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        BufferedImage image = DesktopPetWindow.paintMiluViewport(view, frames, animation, false);
        Rectangle box = DesktopPetWindow.opaqueBounds(image, OPAQUE_THRESHOLD);
        return new NamedShot(label, view, image, box);
    }

    private static void writeShotsIfRequested(
            List<NamedShot> poses, DesktopPetSpriteLoader.Frames frames, Rectangle shell)
            throws Exception {
        String dir = System.getenv("SHOTS850_DIR");
        if (dir == null || dir.isEmpty()) {
            return;
        }
        Path out = Paths.get(dir);
        Files.createDirectories(out);
        List<NamedShot> sequence = new ArrayList<NamedShot>();
        sequence.addAll(poses);
        DesktopPetAnimation breath = new DesktopPetAnimation();
        int hold = DesktopPetAnimation.BREATH_HOLD_TICKS;
        for (int t = 0; t < hold * 4; t++) {
            breath.tick();
            if (t % hold == 0) {
                sequence.add(capture("breath-" + (t / hold + 1), frames, shell, breath));
            }
        }
        for (NamedShot shot : sequence) {
            BufferedImage flat = flatten(shot.image);
            ImageIO.write(flat, "png", out.resolve(shot.label + ".png").toFile());
        }
        ImageIO.write(composeStrip(sequence), "png", out.resolve("pose-strip.png").toFile());
        ImageIO.write(composeStrip(breathStrip(frames, shell)), "png", out.resolve("breath-strip.png").toFile());
    }

    private static List<NamedShot> breathStrip(DesktopPetSpriteLoader.Frames frames, Rectangle shell) {
        List<NamedShot> framesOut = new ArrayList<NamedShot>();
        DesktopPetAnimation animation = new DesktopPetAnimation();
        int hold = DesktopPetAnimation.BREATH_HOLD_TICKS;
        for (int t = 0; t < hold * 4; t++) {
            animation.tick();
            if (t % Math.max(1, hold / 2) == 0) {
                framesOut.add(capture("b" + t, frames, shell, animation));
            }
        }
        return framesOut;
    }

    private static BufferedImage flatten(BufferedImage source) {
        BufferedImage flat =
                new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flat.createGraphics();
        try {
            int cell = 8;
            for (int y = 0; y < flat.getHeight(); y += cell) {
                for (int x = 0; x < flat.getWidth(); x += cell) {
                    boolean dark = ((x / cell) + (y / cell)) % 2 == 0;
                    g.setColor(dark ? new Color(210, 210, 214) : new Color(236, 236, 240));
                    g.fillRect(x, y, cell, cell);
                }
            }
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return flat;
    }

    private static BufferedImage composeStrip(List<NamedShot> shots) {
        int gap = 6;
        int cellW = 1;
        int cellH = 1;
        for (NamedShot shot : shots) {
            cellW = Math.max(cellW, shot.image.getWidth());
            cellH = Math.max(cellH, shot.image.getHeight());
        }
        int width = gap + shots.size() * (cellW + gap);
        int height = gap + cellH + 18;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        try {
            g.setColor(new Color(248, 248, 250));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(40, 40, 48));
            for (int i = 0; i < shots.size(); i++) {
                int x = gap + i * (cellW + gap);
                int y = gap;
                g.drawImage(flatten(shots.get(i).image), x, y, null);
                g.drawString(shots.get(i).label, x, height - 4);
            }
        } finally {
            g.dispose();
        }
        return sheet;
    }

    private static BufferedImage assertFrameOpaque(
            DesktopPetSpriteLoader.Frames frames,
            Rectangle shell,
            DesktopPetAnimation animation,
            int tick,
            String phase) {
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        BufferedImage shot = DesktopPetWindow.paintMiluViewport(view, frames, animation, false);
        try {
            DesktopPetWindow.assertOpaqueWithinViewport(shot, OPAQUE_THRESHOLD);
        } catch (AssertionError ex) {
            throw new AssertionError(phase + " tick " + tick + ": " + ex.getMessage(), ex);
        }
        int size = DesktopPetSpriteLoader.DISPLAY;
        int spriteX = (DesktopPetWindow.WIDTH - size) / 2;
        int spriteY = DesktopPetWindow.BUBBLE_SLOT + animation.bob();
        int cx = spriteX + size / 2 - view.x;
        int cy = spriteY + size / 2 - view.y;
        assertTrue(
                phase + " tick " + tick + " center alpha",
                centerAlpha(shot, cx, cy) > OPAQUE_THRESHOLD);
        return shot;
    }

    private static void assertSizeClose(String label, Rectangle ref, Rectangle box, double tolerance) {
        double wr = ratio(box.width, ref.width);
        double hr = ratio(box.height, ref.height);
        assertTrue(
                label + " width " + box.width + " vs " + ref.width + " ratio " + format(wr),
                wr >= 1.0 - tolerance && wr <= 1.0 + tolerance);
        assertTrue(
                label + " height " + box.height + " vs " + ref.height + " ratio " + format(hr),
                hr >= 1.0 - tolerance && hr <= 1.0 + tolerance);
        assertNotDoubled(label, ref, box);
    }

    private static void assertNotDoubled(String label, Rectangle ref, Rectangle box) {
        assertTrue(label + " width must not jump 2x (" + box.width + " vs " + ref.width + ")",
                ratio(box.width, ref.width) < 1.5 && ratio(ref.width, box.width) < 1.5);
        assertTrue(label + " height must not jump 2x (" + box.height + " vs " + ref.height + ")",
                ratio(box.height, ref.height) < 1.5 && ratio(ref.height, box.height) < 1.5);
    }

    private static double ratio(int a, int b) {
        if (b < 1) {
            return a < 1 ? 1 : Double.POSITIVE_INFINITY;
        }
        return a / (double) b;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static int centerAlpha(BufferedImage shot, int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= shot.getWidth() || cy >= shot.getHeight()) {
            return 0;
        }
        return (shot.getRGB(cx, cy) >>> 24) & 0xff;
    }

    private static final class NamedShot {
        final String label;
        final Rectangle view;
        final BufferedImage image;
        final Rectangle box;

        NamedShot(String label, Rectangle view, BufferedImage image, Rectangle box) {
            this.label = label;
            this.view = view;
            this.image = image;
            this.box = box;
        }
    }
}
