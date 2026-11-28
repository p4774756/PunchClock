package com.example.ui;

import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PrivateAlbumPanelTest {

    @Test
    public void gridListsAllResourceImages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PrivateAlbumPanel panel = new PrivateAlbumPanel();
            assertTrue(panel.imageFileNamesForTests().size() >= 6);
            assertEquals(PrivateAlbumPanel.CARD_GRID, panel.activeCardForTests());
        });
    }

    @Test
    public void viewerHonorsNavigationBoundaries() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PrivateAlbumPanel panel = new PrivateAlbumPanel();
            int count = panel.imageFileNamesForTests().size();
            panel.showViewIndexForTests(0);
            assertEquals(0, panel.viewIndexForTests());
            panel.showViewIndexForTests(-5);
            assertEquals(0, panel.viewIndexForTests());
            panel.showViewIndexForTests(count - 1);
            assertEquals(count - 1, panel.viewIndexForTests());
            panel.showViewIndexForTests(count + 3);
            assertEquals(count - 1, panel.viewIndexForTests());
        });
    }

    @Test
    public void offscreenLayoutsExposeGridAndViewer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PrivateAlbumPanel panel = new PrivateAlbumPanel();
            panel.setSize(640, 480);
            assertEquals(PrivateAlbumPanel.CARD_GRID, panel.activeCardForTests());
            BufferedImage grid = panel.renderForTests(640, 480);
            assertTrue(grid.getWidth() > 0);

            panel.showViewIndexForTests(0);
            assertEquals(PrivateAlbumPanel.CARD_VIEW, panel.activeCardForTests());
            BufferedImage view = panel.renderForTests(640, 480);
            assertTrue(view.getWidth() > 0);
            assertNotEquals(panel.activeCardForTests(), PrivateAlbumPanel.CARD_GRID);
        });
    }
}