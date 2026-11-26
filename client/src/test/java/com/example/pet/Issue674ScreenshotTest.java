package com.example.pet;

import com.example.ui.UiFonts;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** 本機 headless 驗收截圖；目錄由 SHOTS674_DIR 指定（不 commit）。 */
public class Issue674ScreenshotTest {

    @Test
    public void captureVerificationFrames() throws Exception {
        String dir = System.getenv("SHOTS674_DIR");
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("shots674.dir");
        }
        Assume.assumeTrue("SHOTS674_DIR or shots674.dir not set", dir != null && !dir.isBlank());
        Path out = Path.of(dir);
        Files.createDirectories(out);
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        Assume.assumeNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);

        writePose(out.resolve("idle-breath.png"), frames, shell, new DesktopPetAnimation());
        writePose(out.resolve("sit.png"), frames, shell, sittingAnimation());
        writePose(out.resolve("sleep.png"), frames, shell, sleepingAnimation());
        DesktopPetAnimation walk = new DesktopPetAnimation();
        walk.forceWalk(1);
        writePose(out.resolve("walk.png"), frames, shell, walk);
        writeRetinaIdle(out.resolve("retina2x-idle.png"), frames, shell);
        writeMenu(out.resolve("pet-menu.png"));
    }

    private static DesktopPetAnimation sittingAnimation() {
        DesktopPetAnimation animation = new DesktopPetAnimation(new ZeroRandom());
        for (int i = 0; i < DesktopPetAnimation.IDLE_TICKS; i++) {
            animation.tick();
        }
        for (int i = 0; i < DesktopPetAnimation.WALK_TICKS; i++) {
            animation.tick();
        }
        for (int i = 0; i < DesktopPetAnimation.IDLE_TICKS; i++) {
            animation.tick();
        }
        Assume.assumeTrue(animation.mode() == DesktopPetAnimation.Mode.SIT);
        return animation;
    }

    private static DesktopPetAnimation sleepingAnimation() {
        DesktopPetAnimation animation = sittingAnimation();
        for (int i = 0; i < DesktopPetAnimation.SIT_TICKS; i++) {
            animation.tick();
        }
        Assume.assumeTrue(animation.mode() == DesktopPetAnimation.Mode.SLEEP);
        return animation;
    }

    private static void writePose(
            Path file, DesktopPetSpriteLoader.Frames frames, Rectangle shell, DesktopPetAnimation anim)
            throws Exception {
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, anim);
        BufferedImage shot = DesktopPetWindow.paintMiluViewport(view, frames, anim, false);
        ImageIO.write(shot, "png", file.toFile());
    }

    private static void writeRetinaIdle(Path file, DesktopPetSpriteLoader.Frames frames, Rectangle shell)
            throws Exception {
        DesktopPetAnimation anim = new DesktopPetAnimation();
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, anim);
        double pixelScale = 2;
        BufferedImage hi = new BufferedImage(
                DesktopPetMiluLayout.bufferPixelWidth(view.width, pixelScale),
                DesktopPetMiluLayout.bufferPixelHeight(view.height, pixelScale),
                BufferedImage.TYPE_INT_ARGB);
        DesktopPetWindow.paintMiluViewport(hi, view, frames, anim, 0, 0, 0, 1, pixelScale);
        ImageIO.write(hi, "png", file.toFile());
    }

    private static void writeMenu(Path file) throws Exception {
        long t = 1_700_000_000_000L;
        DesktopPetCare care = new DesktopPetCare(() -> t);
        care.setEnabled(true, t);
        final BufferedImage[] shot = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            UiFonts.chinesePlain(13);
            PetContextMenu menu = PetContextMenu.build(true, false, true, care, t, noopListener());
            java.awt.Dimension size = menu.getPreferredSize();
            shot[0] = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = shot[0].createGraphics();
            try {
                menu.setSize(size);
                menu.paint(g);
            } finally {
                g.dispose();
            }
        });
        ImageIO.write(shot[0], "png", file.toFile());
    }

    private static final class ZeroRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }

    private static PetContextMenu.Listener noopListener() {
        return new PetContextMenu.Listener() {
            @Override
            public void onToggleControl(boolean enable) {
            }

            @Override
            public void onCare(DesktopPetCare.Action action, DesktopPetAnimation.Mode mode) {
            }

            @Override
            public void onToggleDebug() {
            }

            @Override
            public void onDismiss() {
            }
        };
    }
}