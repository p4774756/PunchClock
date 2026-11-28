package com.example.ui;

import com.example.service.ConfigPersistenceService;
import com.example.service.PrivateAlbumPaths;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Issue801PrivateAlbumTest {

    @Test
    public void enabledListsCustomDirectoryImages() throws Exception {
        Path dir = Files.createTempDirectory("issue801-album");
        writePng(dir.resolve("one.png"));
        writePng(dir.resolve("two.png"));
        SwingUtilities.invokeAndWait(() -> {
            PrivateAlbumPanel panel = new PrivateAlbumPanel(UiFonts.chinesePlain(13), UiFonts.chineseBold(13));
            panel.setAlbumDirectory(dir);
            panel.reload();
            assertEquals(2, panel.listedImagesForTests().size());
        });
    }

    @Test
    public void emptyDirectoryShowsEmptyState() throws Exception {
        Path dir = Files.createTempDirectory("issue801-empty");
        SwingUtilities.invokeAndWait(() -> {
            PrivateAlbumPanel panel = new PrivateAlbumPanel(UiFonts.chinesePlain(13), UiFonts.chineseBold(13));
            panel.setAlbumDirectory(dir);
            panel.reload();
            assertTrue(panel.listedImagesForTests().isEmpty());
            BufferedImage shot = panel.renderForTests(360, 320);
            assertTrue(hasSample(shot, ClassicBevel.FACE));
        });
    }

    @Test
    public void viewerNavigationHonorsBounds() throws Exception {
        Path dir = Files.createTempDirectory("issue801-view");
        writePng(dir.resolve("a.png"));
        writePng(dir.resolve("b.png"));
        writePng(dir.resolve("c.png"));
        SwingUtilities.invokeAndWait(() -> {
            PrivateAlbumPanel panel = new PrivateAlbumPanel(UiFonts.chinesePlain(13), UiFonts.chineseBold(13));
            panel.setSize(400, 360);
            panel.setAlbumDirectory(dir);
            panel.reload();
            panel.openViewerForTests(0);
            assertEquals(0, panel.viewerIndexForTests());
            panel.viewerPreviousForTests();
            assertEquals(0, panel.viewerIndexForTests());
            panel.viewerNextForTests();
            assertEquals(1, panel.viewerIndexForTests());
            panel.viewerNextForTests();
            assertEquals(2, panel.viewerIndexForTests());
            panel.viewerNextForTests();
            assertEquals(2, panel.viewerIndexForTests());
            BufferedImage shot = panel.renderForTests(400, 360);
            assertTrue(shot.getWidth() > 0);
        });
    }

    @Test
    public void settingsExposeAlbumSwitchAndPathField() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
            JTabbedPane tabs = PanelFactory.createServerConfigBody(
                    refs,
                    UiFonts.chinesePlain(13),
                    UiFonts.chineseBold(13),
                    UiFonts.latinPlain(13));
            assertNotNull(refs.privateAlbumVisibleSwitch);
            assertEquals("顯示私房相簿", refs.privateAlbumVisibleSwitch.getAccessibleContext().getAccessibleName());
            assertNotNull(refs.privateAlbumDirectoryField);
            assertNotNull(refs.privateAlbumOpenFolderButton);
            assertFalse(refs.privateAlbumVisibleSwitch.isSelected());
            tabs.setSelectedIndex(PanelFactory.SETTINGS_APPEARANCE_TAB);
            assertTrue(containsDescendant((Container) tabs.getSelectedComponent(), refs.privateAlbumVisibleSwitch));
        });
    }

    @Test
    public void configRoundTripPersistsAlbumSettings() {
        Path configFile = Path.of(System.getProperty("java.io.tmpdir"), "issue801-config-" + System.nanoTime() + ".json");
        ConfigPersistenceService service = new ConfigPersistenceService(configFile);
        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
        config.privateAlbumVisible = true;
        config.privateAlbumDirectory = "/tmp/demo-album";
        service.saveConfig(config, null);
        ConfigPersistenceService.CloudConfig loaded = service.loadConfig(null);
        assertTrue(loaded.privateAlbumVisible);
        assertEquals("/tmp/demo-album", loaded.privateAlbumDirectory);
    }

    @Test
    public void resolveDirectoryDoesNotTouchRealSklbaby() {
        Path temp = Path.of(System.getProperty("java.io.tmpdir"), "issue801-resolve");
        assertEquals(temp, PrivateAlbumPaths.resolveDirectory(temp.toString()));
    }

    private static boolean hasSample(BufferedImage image, Color color) {
        int target = color.getRGB() & 0xFFFFFF;
        for (int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight() / 8)) {
            for (int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth() / 8)) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsDescendant(Container root, Component target) {
        if (root == null || target == null) {
            return false;
        }
        for (Component child : root.getComponents()) {
            if (child == target) {
                return true;
            }
            if (child instanceof Container && containsDescendant((Container) child, target)) {
                return true;
            }
            if (child instanceof JButton && target instanceof JButton) {
                if (((JButton) child).getText().equals(((JButton) target).getText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void writePng(Path file) throws Exception {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                image.setRGB(x, y, (x + y) % 2 == 0 ? Color.BLUE.getRGB() : Color.CYAN.getRGB());
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }
}