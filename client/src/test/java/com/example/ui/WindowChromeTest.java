package com.example.ui;

import com.example.ui.hud.AnimeShell;
import com.example.ui.hud.HomePortrait;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WindowChromeTest {

    @Test
    public void installProvidesSliderAndBody() throws Exception {
        Assume.assumeFalse("headless JVM cannot create frames", GraphicsEnvironment.isHeadless());

        JFrame[] holder = new JFrame[1];
        WindowChrome.Controls[] controls = new WindowChrome.Controls[1];
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            holder[0] = frame;
            controls[0] = WindowChrome.install(
                    frame, "透明度測試", null, new Font(Font.SANS_SERIF, Font.BOLD, 13),
                    new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        });
        try {
            assertNotNull(controls[0]);
            assertNotNull(controls[0].transparencySlider);
            assertEquals(0, controls[0].transparencySlider.getMinimum());
            assertEquals(WindowOpacity.MAX_TRANSPARENCY_PERCENT, controls[0].transparencySlider.getMaximum());
            JPanel body = WindowChrome.bodyOf(holder[0]);
            assertNotNull(body);
            assertTrue(holder[0].isUndecorated());
            WindowChrome.setWallpaper(holder[0], null);
        } finally {
            SwingUtilities.invokeAndWait(() -> holder[0].dispose());
        }
    }

    @Test
    public void homePortraitFollowsDragResizeMaximizeAndOpacity() throws Exception {
        Assume.assumeFalse("headless JVM cannot show a window", GraphicsEnvironment.isHeadless());
        JFrame[] holder = new JFrame[1];
        int[] closing = {0};
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("洋流版面");
            holder[0] = frame;
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    closing[0]++;
                }
            });
            WindowChrome.Controls controls = WindowChrome.install(
                    frame, "洋流版面", null, new Font(Font.SANS_SERIF, Font.BOLD, 13),
                    new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            JTabbedPane tabs = new JTabbedPane();
            AnimeShell shell = new AnimeShell(null, tabs);
            WindowChrome.bodyOf(frame).add(shell);
            WindowChrome.setFramePortrait(frame, new HomePortrait(shell));
            controls.transparencySlider.addChangeListener(e ->
                    WindowOpacity.applyTransparencyPercent(frame, controls.transparencySlider.getValue()));
            frame.setSize(1280, 920);
            frame.setLocation(80, 60);
            frame.setVisible(true);
            frame.validate();

            Rectangle stage = WindowChrome.stageBounds(frame);
            assertTrue("hair needs space above the panel", stage.y >= 96);
            assertTrue("sock needs space to the right", stage.width < frame.getContentPane().getWidth() - 40);

            JPanel title = findMarked(frame.getContentPane(), "punchclock.titleBar");
            assertNotNull(title);
            int x = frame.getX();
            int y = frame.getY();
            java.awt.Point screen = title.getLocationOnScreen();
            title.dispatchEvent(mouse(title, MouseEvent.MOUSE_PRESSED, 24, 12, screen.x + 24, screen.y + 12, 1));
            title.dispatchEvent(mouse(title, MouseEvent.MOUSE_DRAGGED, 90, 40, screen.x + 90, screen.y + 40, 0));
            title.dispatchEvent(mouse(title, MouseEvent.MOUSE_RELEASED, 90, 40, screen.x + 90, screen.y + 40, 1));
            assertEquals(x + 66, frame.getX());
            assertEquals(y + 28, frame.getY());

            JComponent canvas = (JComponent) frame.getContentPane();
            int width = frame.getWidth();
            java.awt.Point origin = canvas.getLocationOnScreen();
            int edgeX = canvas.getWidth() - 2;
            int edgeY = canvas.getHeight() / 2;
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_PRESSED, edgeX, edgeY,
                    origin.x + edgeX, origin.y + edgeY, 1));
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_DRAGGED, edgeX + 48, edgeY,
                    origin.x + edgeX + 48, origin.y + edgeY, 0));
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_RELEASED, edgeX + 48, edgeY,
                    origin.x + edgeX + 48, origin.y + edgeY, 1));
            assertTrue(frame.getWidth() >= width + 40);
            frame.validate();
            Rectangle resized = WindowChrome.stageBounds(frame);
            assertTrue(resized.y > 40);
            assertTrue(resized.x + resized.width < frame.getContentPane().getWidth());

            assertNotNull(findButton(frame, "最大化"));
            assertNotNull(findButton(frame, "最小化"));
            assertNotNull(findButton(frame, "關閉"));
            assertTrue(findButton(frame, "最大化").isEnabled());
        });
        try {
            awaitState(holder[0], "最大化", Frame.MAXIMIZED_BOTH, true);
            SwingUtilities.invokeAndWait(() -> {
                holder[0].validate();
                assertTrue(WindowChrome.isMaximized(holder[0]));
                Rectangle full = WindowChrome.stageBounds(holder[0]);
                assertEquals(0, full.x);
                assertEquals(0, full.y);
                assertEquals(holder[0].getContentPane().getWidth(), full.width);
            });
            awaitState(holder[0], "最大化", Frame.MAXIMIZED_BOTH, false);
            SwingUtilities.invokeAndWait(() -> {
                holder[0].validate();
                assertFalse(WindowChrome.isMaximized(holder[0]));
                Rectangle restored = WindowChrome.stageBounds(holder[0]);
                assertTrue(restored.y >= 96);
                assertTrue(restored.width < holder[0].getContentPane().getWidth() - 40);
            });
            awaitState(holder[0], "最小化", Frame.ICONIFIED, true);
            SwingUtilities.invokeLater(() -> holder[0].setExtendedState(Frame.NORMAL));
            long start = System.currentTimeMillis();
            while ((holder[0].getExtendedState() & Frame.ICONIFIED) != 0
                    && System.currentTimeMillis() - start < 4000) {
                Thread.sleep(40);
            }
            assertEquals(0, holder[0].getExtendedState() & Frame.ICONIFIED);
            SwingUtilities.invokeAndWait(() -> {
                javax.swing.JSlider slider = findSlider(holder[0].getContentPane());
                assertNotNull(slider);
                slider.setValue(20);
                assertEquals(WindowOpacity.toOpacity(20), holder[0].getOpacity(), 0.02);
                findButton(holder[0], "關閉").doClick();
                assertEquals(1, closing[0]);
                assertTrue(holder[0].isDisplayable());
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                holder[0].setExtendedState(Frame.NORMAL);
                holder[0].dispose();
            });
        }
    }

    @Test(timeout = 20000)
    public void homeSwitchKeepsThePanelAndDoesNotFlashTheBackdrop() throws Exception {
        Assume.assumeFalse("headless JVM cannot show a window", GraphicsEnvironment.isHeadless());
        JFrame[] backdrop = new JFrame[1];
        JFrame[] holder = new JFrame[1];
        AnimeShell[] shell = new AnimeShell[1];
        SwingUtilities.invokeAndWait(() -> {
            JFrame red = new JFrame("backdrop");
            backdrop[0] = red;
            red.setUndecorated(true);
            red.getContentPane().setBackground(Color.RED);
            red.setBounds(0, 0, 1400, 1000);
            red.setFocusableWindowState(false);
            red.setVisible(true);

            JFrame frame = new JFrame("洋流 版面切換");
            holder[0] = frame;
            WindowChrome.install(frame, "洋流 版面切換", null,
                    new Font(Font.SANS_SERIF, Font.BOLD, 13),
                    new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("排程", new JPanel());
            tabs.addTab("設定", new JPanel());
            shell[0] = new AnimeShell(null, tabs);
            WindowChrome.bodyOf(frame).add(shell[0]);
            WindowChrome.setFramePortrait(frame, new HomePortrait(shell[0]));
            frame.setSize(1100, 760);
            frame.setLocation(80, 80);
            frame.setVisible(true);
            frame.toFront();
        });
        try {
            Thread.sleep(200);
            Rectangle[] homePanel = new Rectangle[1];
            SwingUtilities.invokeAndWait(() -> {
                holder[0].toFront();
                homePanel[0] = panelOnScreen(holder[0]);
            });
            assertTrue(homePanel[0].width > 80);
            AtomicReference<Color> flash = new AtomicReference<>();
            sampleSwitch(homePanel[0], flash, () -> shell[0].openTab(0));
            assertEquals("離開首頁時不該透出背後的紅色", null, flash.get());
            SwingUtilities.invokeAndWait(() -> holder[0].toFront());
            Robot robot = new Robot();
            Color margin = robot.getPixelColor(
                    homePanel[0].x + homePanel[0].width / 2, homePanel[0].y - 30);
            assertTrue("其他頁面不該把背景畫出面板 " + margin, isBackdropRed(margin));
            Color side = robot.getPixelColor(
                    homePanel[0].x + homePanel[0].width + 24, homePanel[0].y + 80);
            assertTrue("右側留白不該畫上背景 " + side, isBackdropRed(side));
            SwingUtilities.invokeAndWait(() -> {
                Rectangle away = panelOnScreen(holder[0]);
                assertEquals(homePanel[0].x, away.x);
                assertEquals(homePanel[0].y, away.y);
                assertEquals(homePanel[0].width, away.width);
                assertEquals(homePanel[0].height, away.height);
            });
            flash.set(null);
            sampleSwitch(homePanel[0], flash, () -> shell[0].showHome());
            SwingUtilities.invokeAndWait(() -> {
                Rectangle again = panelOnScreen(holder[0]);
                assertEquals(homePanel[0].x, again.x);
                assertEquals(homePanel[0].y, again.y);
            });
            assertEquals("回到首頁時不該透出背後的紅色", null, flash.get());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                holder[0].dispose();
                backdrop[0].dispose();
            });
        }
    }

    private static boolean isBackdropRed(Color color) {
        return color.getRed() > 220 && color.getGreen() < 70 && color.getBlue() < 70;
    }

    private static void sampleSwitch(Rectangle panel, AtomicReference<Color> flash, Runnable onEdt)
            throws Exception {
        Robot robot = new Robot();
        int x = panel.x + 48;
        int y = panel.y + Math.min(120, Math.max(40, panel.height / 3));
        Thread sampler = new Thread(() -> {
            long end = System.currentTimeMillis() + 450;
            while (System.currentTimeMillis() < end) {
                Color color = robot.getPixelColor(x, y);
                if (color.getRed() > 220 && color.getGreen() < 70 && color.getBlue() < 70) {
                    flash.compareAndSet(null, color);
                }
            }
        }, "page-switch-sampler");
        sampler.start();
        SwingUtilities.invokeLater(onEdt);
        sampler.join(1000);
    }

    private static Rectangle panelOnScreen(JFrame frame) {
        Rectangle stage = WindowChrome.stageBounds(frame);
        java.awt.Point origin = frame.getContentPane().getLocationOnScreen();
        return new Rectangle(origin.x + stage.x, origin.y + stage.y, stage.width, stage.height);
    }

    @Test
    public void mainTitleDropsCommitAndTime() {
        assertEquals("洋流 v3.0.0", WindowChrome.mainTitle(
                "洋流  v3.0.0 · a1b2c3d（提交 2026-11-06 14:05）"));
        assertEquals("洋流 v3.0.0", WindowChrome.mainTitle("洋流 v3.0.0"));
    }

    @Test
    public void pageSwitchKeepsThePanelAnchored() {
        Rectangle panel = new Rectangle(80, 25, 1100, 700);
        Rectangle work = new Rectangle(0, 25, 1440, 790);
        Insets none = new Insets(0, 0, 0, 0);
        Insets home = WindowChrome.overflowForPanel(panel.width, panel.height, true);
        assertTrue(home.top >= 96);
        Rectangle homeWindow = WindowChrome.keepTitleVisible(
                WindowChrome.windowForPanel(panel, home, none), home, none, 35, work);
        Rectangle awayWindow = WindowChrome.keepTitleVisible(
                WindowChrome.windowForPanel(panel, none, none), none, none, 35, work);
        assertEquals(panel.x, awayWindow.x);
        assertEquals(panel.y, awayWindow.y);
        assertEquals(panel.y, homeWindow.y + home.top);
        assertEquals(panel.x, homeWindow.x + home.left);
        Rectangle again = WindowChrome.keepTitleVisible(
                WindowChrome.windowForPanel(panel, home, none), home, none, 35, work);
        assertEquals(homeWindow, again);
        Insets solved = com.example.ui.hud.HomeCharacterLayout.overflow(
                homeWindow.width, homeWindow.height, false, true);
        assertEquals(home.top, solved.top);
        assertEquals(home.right, solved.right);
    }

    @Test
    public void titleBarStaysInsideTheWorkArea() {
        Rectangle panel = new Rectangle(40, -40, 1100, 700);
        Rectangle work = new Rectangle(0, 25, 1440, 790);
        Insets none = new Insets(0, 0, 0, 0);
        Rectangle nudged = WindowChrome.keepTitleVisible(
                WindowChrome.windowForPanel(panel, none, none), none, none, 35, work);
        assertEquals(work.y, nudged.y);
        assertTrue(nudged.x + 1100 <= work.x + work.width);
    }

    private static void awaitState(JFrame frame, String tip, int mask, boolean wantSet) throws Exception {
        SwingUtilities.invokeLater(() -> findButton(frame, tip).doClick());
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 4000) {
            boolean on = (frame.getExtendedState() & mask) != 0;
            if (on == wantSet) {
                return;
            }
            Thread.sleep(40);
        }
        throw new AssertionError("視窗沒有在時限內變成 " + tip);
    }

    private static javax.swing.JSlider findSlider(Component root) {
        if (root instanceof javax.swing.JSlider) {
            return (javax.swing.JSlider) root;
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                javax.swing.JSlider found = findSlider(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static MouseEvent mouse(Component source, int id, int x, int y, int xAbs, int yAbs, int clicks) {
        return new MouseEvent(source, id, System.currentTimeMillis(), 0, x, y, xAbs, yAbs, clicks, false, MouseEvent.BUTTON1);
    }

    private static JButton findButton(Component root, String tip) {
        if (root instanceof JButton && tip.equals(((JButton) root).getToolTipText())) {
            return (JButton) root;
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                JButton found = findButton(child, tip);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JPanel findMarked(Component root, String key) {
        if (root instanceof JPanel && Boolean.TRUE.equals(((JPanel) root).getClientProperty(key))) {
            return (JPanel) root;
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                JPanel found = findMarked(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
