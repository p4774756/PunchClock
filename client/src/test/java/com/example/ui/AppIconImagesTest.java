package com.example.ui;

import org.junit.Test;

import java.awt.Image;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class AppIconImagesTest {

    @Test
    public void loadsIconsHeadless() {
        List<Image> icons = AppIconImages.loadAll();
        assertFalse(icons.isEmpty());
        assertNotNull(AppIconImages.primary());
    }

    @Test
    public void loadsSwimsuitDockIcons() {
        List<Image> icons = AppIconImages.loadAll(true);
        assertFalse(icons.isEmpty());
        assertNotNull(AppIconImages.primary(true));
        assertEquals(512, AppIconImages.primary(true).getWidth(null));
    }

    @Test
    public void swimsuitDiffersFromDefaultAt128() {
        Image def = AppIconImages.scaleNearest(
                readMaster("/app-icon.png"), 128);
        Image swim = AppIconImages.scaleNearest(
                readMaster("/dock-icon-b.png"), 128);
        assertNotNull(def);
        assertNotNull(swim);
        org.junit.Assert.assertNotEquals(
                ((java.awt.image.BufferedImage) def).getRGB(64, 64),
                ((java.awt.image.BufferedImage) swim).getRGB(64, 64));
    }

    private static java.awt.image.BufferedImage readMaster(String path) {
        try (java.io.InputStream in = AppIconImagesTest.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return javax.imageio.ImageIO.read(in);
        } catch (Exception ex) {
            return null;
        }
    }
}