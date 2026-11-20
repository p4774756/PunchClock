package com.example.ui;

import com.example.ui.hud.AnimeShell;
import com.example.ui.hud.ShellDimensions;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * headless 截圖到 ~/PunchClock-oki/shots274-self/，不進版控。
 */
public class Issue274ScreenshotTest {

    private static final Path OUT_DIR = Paths.get(
            System.getProperty("user.home"), "PunchClock-oki", "shots274-self");

    @Test
    public void writeContactSheet() throws Exception {
        Files.createDirectories(OUT_DIR);
        List<BufferedImage> shots = new ArrayList<>();
        int w = ShellDimensions.DEFAULT_WIDTH;
        int h = ShellDimensions.DEFAULT_HEIGHT;
        shots.add(AnimeShell.renderShellPreview(w, h, false));
        shots.add(WindowChrome.renderHomePreview(w, h));
        shots.add(AnimeShell.renderShellPreview(ShellDimensions.MINI_WIDTH, ShellDimensions.MINI_HEIGHT, true));
        for (int i = 0; i < shots.size(); i++) {
            ImageIO.write(shots.get(i), "png", OUT_DIR.resolve("shot-" + i + ".png").toFile());
        }
        BufferedImage sheet = contactSheet(shots, 2);
        Path sheetPath = OUT_DIR.resolve("contact-sheet.png");
        assertTrue(ImageIO.write(sheet, "png", sheetPath.toFile()));
        assertTrue(Files.size(sheetPath) > 5000L);
    }

    private static BufferedImage contactSheet(List<BufferedImage> images, int cols) {
        int pad = 8;
        int cellW = 0;
        int cellH = 0;
        for (BufferedImage img : images) {
            cellW = Math.max(cellW, img.getWidth());
            cellH = Math.max(cellH, img.getHeight());
        }
        int rows = (images.size() + cols - 1) / cols;
        BufferedImage out = new BufferedImage(
                cols * cellW + (cols + 1) * pad,
                rows * cellH + (rows + 1) * pad,
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        try {
            g.setColor(new java.awt.Color(24, 18, 40));
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            for (int i = 0; i < images.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int x = pad + col * (cellW + pad);
                int y = pad + row * (cellH + pad);
                g.drawImage(images.get(i), x, y, null);
            }
        } finally {
            g.dispose();
        }
        return out;
    }
}