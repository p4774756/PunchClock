package com.example.service;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrivateAlbumPathsTest {

    @Test
    public void defaultDirectoryUsesUserHome() {
        Path home = Path.of(System.getProperty("java.io.tmpdir"), "album-home-test");
        String previous = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            assertEquals(home.resolve(".sklbaby/album"), PrivateAlbumPaths.defaultDirectory());
        } finally {
            if (previous == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previous);
            }
        }
    }

    @Test
    public void blankConfiguredPathUsesDefault() {
        Path custom = Path.of("/tmp/custom-album");
        assertEquals(PrivateAlbumPaths.defaultDirectory(), PrivateAlbumPaths.resolveDirectory(""));
        assertEquals(PrivateAlbumPaths.defaultDirectory(), PrivateAlbumPaths.resolveDirectory("   "));
        assertEquals(custom, PrivateAlbumPaths.resolveDirectory(custom.toString()));
    }

    @Test
    public void listsSupportedExtensionsSorted() throws Exception {
        Path dir = Files.createTempDirectory("private-album-list");
        writePng(dir.resolve("b.png"));
        writePng(dir.resolve("a.jpg"));
        Files.writeString(dir.resolve("notes.txt"), "skip");
        assertEquals(2, PrivateAlbumPaths.listImages(dir).size());
        assertEquals("a.jpg", PrivateAlbumPaths.listImages(dir).get(0).getFileName().toString());
        assertEquals("b.png", PrivateAlbumPaths.listImages(dir).get(1).getFileName().toString());
    }

    @Test
    public void missingOrEmptyDirectoryReturnsEmptyList() throws Exception {
        Path dir = Files.createTempDirectory("private-album-empty");
        assertTrue(PrivateAlbumPaths.listImages(dir).isEmpty());
        assertTrue(PrivateAlbumPaths.listImages(dir.resolve("missing")).isEmpty());
    }

    @Test
    public void recognizesImageExtensions() {
        assertTrue(PrivateAlbumPaths.isImageFileName("x.JPG"));
        assertTrue(PrivateAlbumPaths.isImageFileName("x.webp"));
        assertFalse(PrivateAlbumPaths.isImageFileName("x.gif"));
    }

    private static void writePng(Path file) throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                image.setRGB(x, y, Color.RED.getRGB());
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }
}