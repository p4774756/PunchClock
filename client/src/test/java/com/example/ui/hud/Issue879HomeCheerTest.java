package com.example.ui.hud;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Headless 截圖：加油女孩 g／h 首頁。PNG 寫到 -Dcheer.shots.dir 或 java.io.tmpdir/shots-cheer，不 commit。 */
public class Issue879HomeCheerTest {

    private static Path outDir() {
        String dir = System.getProperty("cheer.shots.dir");
        if (dir == null || dir.isBlank()) {
            dir = Path.of(System.getProperty("java.io.tmpdir"), "shots-cheer").toString();
        }
        return Path.of(dir);
    }

    @Test
    public void cheerIllustrationsRenderWithoutTopCrop() throws Exception {
        assertEquals(0, HomeBubbleAnchor.sourceTopCropPx(HomeIllustration.CHEER_PURPLE_G, 1152));
        assertEquals(0, HomeBubbleAnchor.sourceTopCropPx(HomeIllustration.CHEER_RED_H, 1152));
        Path out = outDir();
        Files.createDirectories(out);
        writeShot("home-g-380.png", HomeIllustration.CHEER_PURPLE_G, 380, 520, out);
        writeShot("home-h-380.png", HomeIllustration.CHEER_RED_H, 380, 520, out);
        writeShot("home-g-340.png", HomeIllustration.CHEER_PURPLE_G, 340, 440, out);
    }

    private static void writeShot(
            String name,
            HomeIllustration illustration,
            int width,
            int height,
            Path out) throws Exception {
        AtomicReference<BufferedImage> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            AnimeShell shell = new AnimeShell(null, new JTabbedPane());
            shell.setHomeIllustration(illustration);
            shell.setSize(width, height);
            shell.doLayout();
            shell.showHome();
            shell.showHomeBubbleImmediatelyForTests("今天也加油");
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                shell.paint(g);
            } finally {
                g.dispose();
            }
            ref.set(image);
        });
        BufferedImage image = ref.get();
        assertNotNull(name, image);
        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
        // 不該是單色失敗畫面：取內容區幾個點，應有變化
        int mid = image.getRGB(width / 2, height / 3);
        int left = image.getRGB(Math.max(8, width / 6), height / 3);
        int right = image.getRGB(width - Math.max(8, width / 6), height / 3);
        assertTrue(name + " looks flat", mid != left || mid != right);
        ImageIO.write(image, "PNG", out.resolve(name).toFile());
    }
}
