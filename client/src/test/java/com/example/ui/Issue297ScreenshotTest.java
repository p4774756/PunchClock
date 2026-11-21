package com.example.ui;

import com.example.ui.hud.AnimeShell;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Headless 截圖輸出到 worktree 外 shots297-self（不 commit）。
 */
public class Issue297ScreenshotTest {

    private static final Path OUT =
            Path.of(System.getProperty("user.home"), "PunchClock-oki", "shots297-self", "dev1");

    @Test
    public void captureHomePoolPhasesAndUi() throws Exception {
        Assume.assumeFalse("true".equals(System.getenv("CI")));
        Files.createDirectories(OUT);
        ZoneId zone = ZoneId.systemDefault();
        writePhaseShot("home-day.png", LocalDate.of(2026, 6, 1).atTime(10, 0).atZone(zone), zone);
        writePhaseShot("home-dusk.png", LocalDate.of(2026, 6, 1).atTime(17, 30).atZone(zone), zone);
        writePhaseShot("home-night.png", LocalDate.of(2026, 6, 1).atTime(22, 0).atZone(zone), zone);
        writeShellShot("tabs-enlarged.png", false);
        writeHistoryShot("history-expanded.png");
    }

    private static void writePhaseShot(String name, java.time.ZonedDateTime when, ZoneId zone) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AnimeShell shell = new AnimeShell(null, new JTabbedPane());
                shell.setClockForTests(Clock.fixed(when.toInstant(), zone));
                shell.setSize(ShellDimensions.DEFAULT_WIDTH, ShellDimensions.DEFAULT_HEIGHT);
                shell.doLayout();
                shell.showHome();
                BufferedImage image = new BufferedImage(
                        ShellDimensions.DEFAULT_WIDTH,
                        ShellDimensions.DEFAULT_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
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
                scroll.setSize(320, 260);
                BufferedImage shot = new BufferedImage(320, 260, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = shot.createGraphics();
                scroll.paint(g);
                g.dispose();
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