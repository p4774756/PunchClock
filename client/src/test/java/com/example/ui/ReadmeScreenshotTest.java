package com.example.ui;

import com.example.ui.hud.AnimeShell;
import com.example.ui.hud.ShellDimensions;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * 產出根目錄 README 用桌面端截圖。設 {@code README_SHOTS_DIR} 才執行，輸出 JPG。
 */
public class ReadmeScreenshotTest {

    @Test
    public void writeDesktopReadmeShots() throws Exception {
        String dir = System.getenv("README_SHOTS_DIR");
        Assume.assumeTrue(dir != null && !dir.isBlank());
        Path out = Path.of(dir);
        Files.createDirectories(out);

        int w = 1100;
        int h = 760;
        writeJpeg(out.resolve("home.jpg"), WindowChrome.renderHomePreview(w, h), 0.88f);
        writeJpeg(out.resolve("mini.jpg"),
                AnimeShell.renderShellPreview(ShellDimensions.MINI_WIDTH, ShellDimensions.MINI_HEIGHT, true),
                0.88f);

        int sw = ShellDimensions.DEFAULT_WIDTH;
        int sh = ShellDimensions.DEFAULT_HEIGHT;
        writeJpeg(out.resolve("schedule.jpg"), AnimeShell.renderShellPreview(sw, sh, false, 1), 0.88f);
        writeJpeg(out.resolve("friends.jpg"), AnimeShell.renderShellPreview(sw, sh, false, 2), 0.88f);
        writeJpeg(out.resolve("log.jpg"), AnimeShell.renderShellPreview(sw, sh, false, 3), 0.88f);

        SwingUtilities.invokeAndWait(() -> {
            try {
                PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
                Font main = UiFonts.chinesePlain(13);
                Font bold = UiFonts.chineseBold(13);
                Font field = UiFonts.latinPlain(13);
                JTabbedPane tabs = PanelFactory.createServerConfigBody(refs, main, bold, field);
                tabs.setSelectedIndex(1);
                int pw = 860;
                int ph = 620;
                BufferedImage appearance = WindowChrome.renderOffscreen(
                        tabs, pw, ph, new Color(28, 20, 48));
                writeJpeg(out.resolve("settings.jpg"), appearance, 0.88f);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void writeJpeg(Path path, BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        Assume.assumeTrue("JPEG writer missing", writers.hasNext());
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(Files.newOutputStream(path))) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        Assume.assumeTrue(path.getFileName() + " too small", Files.size(path) > 2_000L);
    }
}