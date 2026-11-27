package com.example.pet;

import com.example.pet.DesktopPetAnimation.Mode;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** #332：縮視窗後繪製的不透明像素必須完整落在視窗內。 */
public class Issue332PetFrameClipTest {

    @Test
    public void everydaySpritesFitResolvedViewport() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.shellFor(frames);
        for (DesktopPetSpriteLoader.Sprite sprite : frames.allSprites()) {
            if (sprite == null || sprite.name == null) {
                continue;
            }
            if (!everyday(sprite.name)) {
                continue;
            }
            DesktopPetAnimation pose = poseForSprite(sprite.name, frames);
            Rectangle view = DesktopPetWindow.viewForPose(frames, shell, pose);
            BufferedImage shot = DesktopPetWindow.paintMiluViewport(view, frames, pose, false);
            DesktopPetWindow.assertOpaqueWithinViewport(shot, 16);
        }
    }

    @Test
    public void animationModesStayInsideViewport() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.shellFor(frames);
        DesktopPetAnimation anim = new DesktopPetAnimation();
        exercise(anim, frames, shell, a -> { /* idle */ });
        anim.forceWalk(1);
        exercise(anim, frames, shell, a -> a.forceWalk(1));
        anim.forceWalk(-1);
        exercise(anim, frames, shell, a -> a.forceWalk(-1));
        anim.poke();
        exercise(anim, frames, shell, DesktopPetAnimation::poke);
        exercise(anim, frames, shell, a -> a.setCarried(true));
        DesktopPetAnimation carried = new DesktopPetAnimation();
        carried.setCarried(true);
        for (int i = 0; i < DesktopPetAnimation.GRAB_TICKS + 4; i++) {
            carried.tick();
            assertViewport(frames, shell, carried);
        }
        carried.releaseCarry();
        for (int i = 0; i < DesktopPetAnimation.RELEASE_TICKS + 2; i++) {
            carried.tick();
            assertViewport(frames, shell, carried);
        }
        exercise(anim, frames, shell, a -> a.setPlayerControl(true));
        exercise(anim, frames, shell, a -> a.setCursorHold(Mode.BUSY));
        exercise(anim, frames, shell, a -> a.setCursorHold(Mode.THINK));
        exercise(anim, frames, shell, a -> a.flashWorry("嗯？"));
        for (Mode care : new Mode[] {Mode.FEED, Mode.PAT, Mode.PLAY}) {
            Mode mode = care;
            exercise(anim, frames, shell, a -> a.playCare(mode, null));
        }
    }

    private static void assertViewport(
            DesktopPetSpriteLoader.Frames frames, Rectangle shell, DesktopPetAnimation anim) {
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, anim);
        BufferedImage shot = DesktopPetWindow.paintMiluViewport(view, frames, anim, false);
        DesktopPetWindow.assertOpaqueWithinViewport(shot, 16);
    }

    /**
     * 模擬 refreshFit 後 stage 仍是 260 寬、系統只裁左側視窗寬：角色會只剩右半。
     * 正確視埠裁剪應與此不同。
     */
    @Test
    public void unlaidOversizedStageWouldShowWrongSlice() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle view = DesktopPetWindow.shellFor(frames);
        DesktopPetAnimation idle = new DesktopPetAnimation();
        BufferedImage correct = DesktopPetWindow.paintMiluViewport(view, frames, idle, false);

        BufferedImage stage = new BufferedImage(
                DesktopPetWindow.WIDTH, DesktopPetWindow.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = stage.createGraphics();
        g.translate(-view.x, -view.y);
        int x = (DesktopPetWindow.WIDTH - DesktopPetSpriteLoader.DISPLAY) / 2;
        DesktopPetSpriteLoader.Sprite sprite = frames.spriteFor(idle);
        DesktopPetSpriteLoader.draw(
                g, sprite.image, x, DesktopPetWindow.BUBBLE_SLOT,
                DesktopPetSpriteLoader.DISPLAY, sprite.offsetX, sprite.offsetY);
        DesktopPetWindow.paintMiluShadow(g, DesktopPetWindow.WIDTH);
        g.dispose();
        BufferedImage buggy = stage.getSubimage(0, 0, view.width, view.height);

        assertEquals(view.width, correct.getWidth());
        assertEquals(view.height, correct.getHeight());
        assertTrue(imagesDiffer(correct, buggy));
        DesktopPetWindow.assertOpaqueWithinViewport(correct, 16);
    }

    private static void exercise(
            DesktopPetAnimation anim,
            DesktopPetSpriteLoader.Frames frames,
            Rectangle shell,
            java.util.function.Consumer<DesktopPetAnimation> trigger) {
        DesktopPetAnimation probe = new DesktopPetAnimation();
        trigger.accept(probe);
        for (int t = 0; t < 8; t++) {
            probe.tick();
            Rectangle view = DesktopPetWindow.viewForPose(frames, shell, probe);
            BufferedImage shot = DesktopPetWindow.paintMiluViewport(view, frames, probe, false);
            DesktopPetWindow.assertOpaqueWithinViewport(shot, 16);
        }
    }

    private static DesktopPetAnimation poseForSprite(String name, DesktopPetSpriteLoader.Frames frames) {
        DesktopPetAnimation anim = new DesktopPetAnimation();
        if (name.startsWith("walk/left")) {
            anim.forceWalk(-1);
        } else if (name.startsWith("walk/right")) {
            anim.forceWalk(1);
        } else if (name.startsWith("wave/")) {
            anim.poke();
        } else if (name.startsWith("drag/")) {
            anim.setCarried(true);
        } else if (name.startsWith("grab/") || name.startsWith("struggle/")) {
            anim.setCarried(true);
        } else if (name.startsWith("release/")) {
            anim.setCarried(true);
            anim.releaseCarry();
        } else if (name.startsWith("feed/")) {
            anim.playCare(Mode.FEED, null);
        } else if (name.startsWith("pat/")) {
            anim.playCare(Mode.PAT, null);
        } else if (name.startsWith("play/")) {
            anim.playCare(Mode.PLAY, null);
        } else if (name.startsWith("think/")) {
            anim.setCursorHold(Mode.THINK);
        } else if (name.startsWith("busy/")) {
            anim.setCursorHold(Mode.BUSY);
        } else if (name.startsWith("worry/")) {
            anim.flashWorry(null);
        } else if (name.startsWith("hungry/")) {
            anim.flashHungry();
        }
        return anim;
    }

    private static boolean everyday(String name) {
        return name.startsWith("idle/") || name.startsWith("walk/")
                || name.startsWith("wave/") || name.startsWith("grab/")
                || name.startsWith("struggle/") || name.startsWith("release/")
                || name.startsWith("think/") || name.startsWith("busy/")
                || name.startsWith("worry/") || name.startsWith("hungry/")
                || name.startsWith("feed/")
                || name.startsWith("sit/") || name.startsWith("drag/")
                || name.startsWith("pat/") || name.startsWith("play/");
    }

    private static boolean imagesDiffer(BufferedImage left, BufferedImage right) {
        if (left.getWidth() != right.getWidth() || left.getHeight() != right.getHeight()) {
            return true;
        }
        for (int y = 0; y < left.getHeight(); y++) {
            for (int x = 0; x < left.getWidth(); x++) {
                if (left.getRGB(x, y) != right.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }
}