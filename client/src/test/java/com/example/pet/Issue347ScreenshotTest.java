package com.example.pet;

import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** 真實視窗擷圖。只在指定輸出目錄時跑，圖不進版控。 */
public class Issue347ScreenshotTest {

    @Test
    public void captureSummonBesideMain() throws Exception {
        String dir = System.getenv("SHOTS347_DIR");
        Assume.assumeTrue("SHOTS347_DIR not set", dir != null && !dir.isBlank());
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Path out = Path.of(dir);
        Files.createDirectories(out);
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        Assume.assumeNotNull(frames);
        JWindow green = new JWindow();
        JFrame main = new JFrame("主視窗");
        DesktopPetManager manager = new DesktopPetManager(
                new DesktopPetSettings(new Issue347PetMainOverlapTest.MemoryPreferences()));
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                green.setFocusableWindowState(false);
                green.setAutoRequestFocus(false);
                JPanel pad = new JPanel();
                pad.setBackground(new Color(0, 190, 70));
                green.setContentPane(pad);
                green.setBounds(16, 48, 860, 480);
                main.setUndecorated(true);
                main.setFocusableWindowState(false);
                main.setAutoRequestFocus(false);
                JPanel fill = new JPanel();
                fill.setBackground(new Color(166, 206, 255));
                main.setContentPane(fill);
                main.setBounds(360, 110, 470, 340);
                green.setVisible(true);
                main.setVisible(true);
                green.toFront();
                main.toFront();
                manager.setCursorReactEnabled(false);
                manager.setCareEnabled(false);
                manager.setMainBoundsSupplier(() -> {
                    if ((main.getExtendedState() & Frame.ICONIFIED) != 0) {
                        return null;
                    }
                    return new Rectangle(main.getX(), main.getY(), main.getWidth(), main.getHeight());
                });
                if (!manager.setEnabled(true)) {
                    throw new IllegalStateException("pet did not show");
                }
                DesktopPetWindow pet = manager.window();
                pet.useFrames(frames);
                pet.holdStill();
                pet.setLocation(48, 150);
                pet.toFront();
            });
            Thread.sleep(350);
            DesktopPetWindow pet = manager.window();
            BufferedImage beside = robot.createScreenCapture(new Rectangle(16, 48, 860, 480));
            assertPetVisible(pet, beside, 16, 48);
            ImageIO.write(beside, "png", out.resolve("beside.png").toFile());
            BufferedImage close = robot.createScreenCapture(pet.getBounds());
            ImageIO.write(scale(close, 3), "png", out.resolve("pet-close.png").toFile());

            SwingUtilities.invokeAndWait(() -> {
                DesktopPetWindow window = manager.window();
                window.setLocation(main.getX() - window.getWidth() / 2, main.getY() + 50);
                window.toFront();
            });
            Thread.sleep(250);
            BufferedImage straddle = robot.createScreenCapture(new Rectangle(16, 48, 860, 480));
            assertPetVisible(pet, straddle, 16, 48);
            ImageIO.write(straddle, "png", out.resolve("straddle.png").toFile());

            SwingUtilities.invokeAndWait(() -> {
                manager.window().setLocation(48, 150);
                manager.window().toFront();
                main.setExtendedState(Frame.ICONIFIED);
            });
            Thread.sleep(500);
            BufferedImage minimized = robot.createScreenCapture(new Rectangle(16, 48, 860, 480));
            assertPetVisible(manager.window(), minimized, 16, 48);
            ImageIO.write(minimized, "png", out.resolve("minimized.png").toFile());

            BufferedImage sheet = sheet(beside, scale(close, 3), minimized);
            ImageIO.write(sheet, "jpg", out.resolve("contact-sheet.jpg").toFile());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                manager.shutdown();
                main.dispose();
                green.dispose();
            });
        }
    }

    private static void assertPetVisible(DesktopPetWindow pet, BufferedImage scene, int originX, int originY) {
        BufferedImage buffer = new BufferedImage(pet.getWidth(), pet.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = buffer.createGraphics();
        try {
            pet.stage().paint(graphics);
        } finally {
            graphics.dispose();
        }
        int opaque = 0;
        int matched = 0;
        for (int y = 0; y < buffer.getHeight(); y++) {
            int sy = pet.getY() - originY + y;
            if (sy < 0 || sy >= scene.getHeight()) {
                continue;
            }
            for (int x = 0; x < buffer.getWidth(); x++) {
                int argb = buffer.getRGB(x, y);
                if (((argb >>> 24) & 0xff) <= 180) {
                    continue;
                }
                int sx = pet.getX() - originX + x;
                if (sx < 0 || sx >= scene.getWidth()) {
                    continue;
                }
                opaque++;
                int screen = scene.getRGB(sx, sy);
                if (Math.abs(((screen >> 16) & 0xff) - ((argb >> 16) & 0xff)) < 48
                        && Math.abs(((screen >> 8) & 0xff) - ((argb >> 8) & 0xff)) < 48
                        && Math.abs((screen & 0xff) - (argb & 0xff)) < 48) {
                    matched++;
                }
            }
        }
        if (opaque < 1000 || matched * 4 <= opaque * 3) {
            throw new AssertionError("pet not visible on screen opaque=" + opaque + " matched=" + matched);
        }
    }

    private static BufferedImage scale(BufferedImage source, int factor) {
        BufferedImage image = new BufferedImage(
                source.getWidth() * factor, source.getHeight() * factor, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.drawImage(source, 0, 0, image.getWidth(), image.getHeight(), null);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage sheet(BufferedImage beside, BufferedImage close, BufferedImage minimized) {
        int width = beside.getWidth() + close.getWidth() + 48;
        int height = Math.max(beside.getHeight(), close.getHeight()) + minimized.getHeight() + 80;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        try {
            g.setColor(new Color(24, 24, 28));
            g.fillRect(0, 0, width, height);
            g.setColor(Color.WHITE);
            g.drawString("主視窗開著，整隻寵物在旁邊", 12, 18);
            g.drawImage(beside, 12, 28, null);
            g.drawString("放大", beside.getWidth() + 24, 18);
            g.drawImage(close, beside.getWidth() + 24, 28, null);
            int bottom = Math.max(beside.getHeight(), close.getHeight()) + 56;
            g.drawString("主視窗最小化，寵物仍在", 12, bottom);
            g.drawImage(minimized, 12, bottom + 8, null);
        } finally {
            g.dispose();
        }
        return sheet;
    }
}
