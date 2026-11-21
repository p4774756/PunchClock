package com.example.pet;

import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** 審核用合成圖：主視窗矩形 + 避開後的完整寵物視埠（不 commit）。 */
public class Issue347ScreenshotTest {

    @Test
    public void captureSummonBesideMain() throws Exception {
        String dir = System.getenv("SHOTS347_DIR");
        Assume.assumeTrue("SHOTS347_DIR not set", dir != null && !dir.isBlank());
        Path out = Path.of(dir);
        Files.createDirectories(out);
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        Assume.assumeNotNull(frames);
        Rectangle shell = DesktopPetWindow.viewForPose(
                frames, DesktopPetWindow.shellFor(frames), new DesktopPetAnimation());
        Rectangle main = new Rectangle(120, 90, 720, 520);
        Rectangle usable = new Rectangle(0, 24, 1440, 876);
        Point spot = overlapSpot(main, shell);
        Point clear = DesktopPetWindow.nudgeOutsideMainWindow(
                spot.x, spot.y, shell.width, shell.height, main, usable, new Rectangle[] {usable});
        BufferedImage pet = DesktopPetWindow.paintMiluViewport(shell, frames, new DesktopPetAnimation(), true);
        BufferedImage composite = new BufferedImage(980, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = composite.createGraphics();
        try {
            g.setColor(new Color(32, 28, 48, 255));
            g.fillRect(0, 0, composite.getWidth(), composite.getHeight());
            g.setColor(new Color(120, 180, 255, 90));
            g.fillRect(40, 40, main.width, main.height);
            g.setColor(new Color(200, 220, 255, 200));
            g.drawRect(40, 40, main.width, main.height);
            g.drawString("main window", 48, 58);
            int petX = 40 + clear.x - main.x;
            int petY = 40 + clear.y - main.y;
            g.drawImage(pet, petX, petY, null);
            g.setColor(new Color(255, 90, 140, 220));
            g.drawRect(petX, petY, shell.width, shell.height);
            g.setColor(Color.WHITE);
            g.drawString("pet (nudged clear of main)", petX, petY - 6);
            g.setColor(new Color(255, 64, 96, 180));
            int badX = 40 + spot.x - main.x;
            int badY = 40 + spot.y - main.y;
            g.drawLine(badX, badY, badX, badY + shell.height);
            g.drawString("old overlap cut", badX + 4, badY + 16);
        } finally {
            g.dispose();
        }
        ImageIO.write(composite, "png", out.resolve("summon-beside-main.png").toFile());
        ImageIO.write(pet, "png", out.resolve("pet-viewport-full.png").toFile());
    }

    private static Point overlapSpot(Rectangle main, Rectangle shell) {
        return new Point(main.x + shell.x, main.y + main.height - shell.height - 48);
    }
}