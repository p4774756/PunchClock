package com.example.ui;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** #720：外觀 Dock 泳裝圖示開關與未讀疊圖離屏預覽。 */
public class Issue720DockIconTest {

    private static final Path OUT_DIR = Paths.get("target", "issue-720-verify");

    @Test
    public void appearanceTab_hasDockSwimsuitSwitch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
            JTabbedPane tabs = PanelFactory.createServerConfigBody(
                    refs, UiFonts.chinesePlain(13), UiFonts.chineseBold(13), UiFonts.latinPlain(13));
            tabs.setSelectedIndex(PanelFactory.SETTINGS_APPEARANCE_TAB);
            assertNotNull(refs.dockIconSwimsuitSwitch);
            assertEquals("Dock 圖示泳裝角色", refs.dockIconSwimsuitSwitch.getAccessibleContext().getAccessibleName());
            assertTrue(refs.dockIconSwimsuitSwitch instanceof CandySwitch);
        });
    }

    @Test
    public void writeAppearanceAndIconPreviews() throws Exception {
        Files.createDirectories(OUT_DIR);
        SwingUtilities.invokeAndWait(() -> {
            try {
                PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
                JTabbedPane tabs = PanelFactory.createServerConfigBody(
                        refs, UiFonts.chinesePlain(13), UiFonts.chineseBold(13), UiFonts.latinPlain(13));
                refs.dockIconSwimsuitSwitch.setSelected(true);
                tabs.setSelectedIndex(PanelFactory.SETTINGS_APPEARANCE_TAB);
                int w = 860;
                int h = 700;
                tabs.setSize(w, h);
                tabs.doLayout();
                BufferedImage appearance = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = appearance.createGraphics();
                try {
                    g.setColor(new Color(28, 20, 48));
                    g.fillRect(0, 0, w, h);
                    tabs.paint(g);
                } finally {
                    g.dispose();
                }
                Path appearancePath = OUT_DIR.resolve("settings-appearance-dock-switch.png");
                assertTrue(ImageIO.write(appearance, "png", appearancePath.toFile()));
                assertTrue(Files.size(appearancePath) > 500L);

                writeIconSheet(OUT_DIR.resolve("dock-icon-default-128.png"),
                        AppIconImages.primary(false), 0);
                writeIconSheet(OUT_DIR.resolve("dock-icon-default-unread-128.png"),
                        AppIconImages.primary(false), 3);
                writeIconSheet(OUT_DIR.resolve("dock-icon-swimsuit-128.png"),
                        AppIconImages.primary(true), 0);
                writeIconSheet(OUT_DIR.resolve("dock-icon-swimsuit-unread-128.png"),
                        AppIconImages.primary(true), 3);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void writeIconSheet(Path path, Image base, int unread) throws Exception {
        List<Image> icons = TaskbarPeerUnreadIcons.composeTaskbarIcons(
                List.of(base), unread);
        Image icon = icons.isEmpty() ? base : icons.get(0);
        BufferedImage canvas = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.drawImage(icon, 0, 0, 128, 128, null);
        } finally {
            g.dispose();
        }
        assertTrue(ImageIO.write(canvas, "png", path.toFile()));
        assertTrue(Files.size(path) > 200L);
        if (unread > 0) {
            assertTrue(hasUnreadTint(canvas));
        }
    }

    private static boolean hasUnreadTint(BufferedImage img) {
        int w = img.getWidth();
        for (int y = 0; y < Math.min(48, img.getHeight()); y++) {
            for (int x = Math.max(0, w * 2 / 3); x < w; x++) {
                Color c = new Color(img.getRGB(x, y), true);
                if (c.getRed() >= 150 && c.getGreen() <= 80 && c.getBlue() <= 80 && c.getAlpha() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}