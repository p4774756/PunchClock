package com.example.ui.hud;

import com.example.ui.WindowChrome;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * headless 把首頁視窗 paint 到 PNG，供 #268 人工驗收。輸出不進版控。
 */
public class HomeChromeScreenshotTest {

    private static final Path OUT_DIR = Paths.get(
            System.getProperty("user.home"), "PunchClock-oki", "shots268-self");

    @Test
    public void writeHomeChromePreviews() throws Exception {
        Files.createDirectories(OUT_DIR);
        assertTrue(ImageIO.write(WindowChrome.renderHomePreview(1100, 760),
                "png", OUT_DIR.resolve("home-restored-1100x760.png").toFile()));
        assertTrue(ImageIO.write(WindowChrome.renderHomePreview(1600, 1000),
                "png", OUT_DIR.resolve("home-large-1600x1000.png").toFile()));
        assertTrue(Files.size(OUT_DIR.resolve("home-restored-1100x760.png")) > 10_000L);
        assertTrue(Files.size(OUT_DIR.resolve("home-large-1600x1000.png")) > 10_000L);
    }
}