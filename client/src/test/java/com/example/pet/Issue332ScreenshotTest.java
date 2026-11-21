package com.example.pet;

import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** 本機 headless 截圖；目錄由 SHOTS332_DIR 指定（不 commit）。 */
public class Issue332ScreenshotTest {

    @Test
    public void captureViewportFrames() throws Exception {
        String dir = System.getenv("SHOTS332_DIR");
        Assume.assumeTrue("SHOTS332_DIR not set", dir != null && !dir.isBlank());
        Path out = Path.of(dir);
        Files.createDirectories(out);
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        Assume.assumeNotNull(frames);
        Rectangle shell = DesktopPetWindow.shellFor(frames);
        DesktopPetAnimation anim = new DesktopPetAnimation();
        write(out.resolve("idle.png"), frames, shell, anim);
        anim.forceWalk(1);
        write(out.resolve("walk-right.png"), frames, shell, anim);
        anim.forceWalk(-1);
        write(out.resolve("walk-left.png"), frames, shell, anim);
        anim.setCarried(true);
        write(out.resolve("drag.png"), frames, shell, anim);
        anim.setCarried(false);
        anim.poke();
        write(out.resolve("wave.png"), frames, shell, anim);
        anim.playCare(DesktopPetAnimation.Mode.FEED, null);
        write(out.resolve("feed.png"), frames, shell, anim);
        anim.setPlayerControl(true);
        write(out.resolve("control.png"), frames, shell, anim);
    }

    private static void write(Path file, DesktopPetSpriteLoader.Frames frames, Rectangle shell,
                              DesktopPetAnimation anim) throws Exception {
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, anim);
        BufferedImage shot = DesktopPetWindow.paintMiluViewport(view, frames, anim, true);
        ImageIO.write(shot, "png", file.toFile());
    }
}