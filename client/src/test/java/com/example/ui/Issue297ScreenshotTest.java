package com.example.ui;

import com.example.ui.hud.AnimeShell;
import com.example.ui.hud.HomeIllustration;
import com.example.ui.hud.ShellDimensions;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Headless 截圖輸出到 worktree 外 shots297-self（不 commit）。
 */
public class Issue297ScreenshotTest {

    private static final Path OUT =
            Path.of(System.getProperty("user.home"), "PunchClock-oki", "shots297-self", "dev1");

    @Test
    public void captureHomeIllustrationsAndUi() throws Exception {
        Assume.assumeFalse("true".equals(System.getenv("CI")));
        Files.createDirectories(OUT);
        writeIllustrationShot("home-b.png", HomeIllustration.ROOM_B, ShellDimensions.DEFAULT_WIDTH, ShellDimensions.DEFAULT_HEIGHT);
        writeIllustrationShot("home-c.png", HomeIllustration.POOL_C, ShellDimensions.DEFAULT_WIDTH, ShellDimensions.DEFAULT_HEIGHT);
        writeIllustrationShot("home-c-min.png", HomeIllustration.POOL_C, ShellDimensions.MIN_WIDTH, ShellDimensions.MIN_HEIGHT);
        writeShellShot("tabs-enlarged.png", false);
        writeHistoryShot("history-expanded.png");
    }

    private static void writeIllustrationShot(
            String name,
            HomeIllustration illustration,
            int width,
            int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AnimeShell shell = new AnimeShell(null, new JTabbedPane());
                shell.setHomeIllustration(illustration);
                shell.setSize(width, height);
                shell.doLayout();
                shell.showHome();
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                shell.paint(g);
                g.dispose();
                write(name, image);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void writeShellShot(String name, boolean mini) throws Exception {
        BufferedImage image = AnimeShell.renderShellPreview(
                mini ? ShellDimensions.MINI_WIDTH : ShellDimensions.DEFAULT_WIDTH,
                mini ? ShellDimensions.MINI_HEIGHT : ShellDimensions.DEFAULT_HEIGHT,
                mini);
        write(name, image);
    }

    private static void writeHistoryShot(String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Font main = UiFonts.chinesePlain(12);
                Font bold = UiFonts.chineseBold(12);
                PanelFactory.CheckInHistoryRefs refs = new PanelFactory.CheckInHistoryRefs();
                refs.listModel = new DefaultListModel<>();
                String line = "2026-01-01 09:00:00"
                        + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                        + "晨會"
                        + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                        + "失敗"
                        + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                        + "排程"
                        + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                        + "TimeoutError: page.goto: net::ERR_CONNECTION_TIMED_OUT";
                refs.listModel.addElement(line);
                JPanel panel = PanelFactory.createCheckInHistoryPanel(refs, main, bold);
                refs.historyList.setSize(CandyHistoryColumns.tableWidth(), 60);
                refs.historyList.dispatchEvent(new java.awt.event.MouseEvent(
                        refs.historyList,
                        java.awt.event.MouseEvent.MOUSE_CLICKED,
                        0,
                        0,
                        80,
                        16,
                        1,
                        false,
                        java.awt.event.MouseEvent.BUTTON1));
                JScrollPane scroll = new JScrollPane(panel);
                BufferedImage shot = WindowChrome.renderOffscreen(scroll, 320, 260, null);
                write(name, shot);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void write(String name, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", OUT.resolve(name).toFile());
    }
}