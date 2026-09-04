package com.example.service;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PeerAvatarTest {

    @Test
    public void isValidEncoded_acceptsUrlSafeBase64Only() {
        assertTrue(PeerAvatar.isValidEncoded("abcXYZ012-_"));
        assertFalse(PeerAvatar.isValidEncoded(""));
        assertFalse(PeerAvatar.isValidEncoded("has+plus"));
        assertFalse(PeerAvatar.isValidEncoded("has/slash"));
        assertFalse(PeerAvatar.isValidEncoded("has=equals"));
        assertEquals("", PeerAvatar.sanitizeEncoded("nope!"));
    }

    @Test
    public void toSquareRgb_cropsCenterAndResizes() {
        BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 50, 100);
        g.setColor(Color.GREEN);
        g.fillRect(50, 0, 100, 100);
        g.setColor(Color.BLUE);
        g.fillRect(150, 0, 50, 100);
        g.dispose();

        BufferedImage square = PeerAvatar.toSquareRgb(source, 32);
        assertEquals(32, square.getWidth());
        assertEquals(32, square.getHeight());
        // 中央 100x100 是綠色，縮放後中心仍應偏綠
        Color center = new Color(square.getRGB(16, 16));
        assertTrue("center should stay green-ish", center.getGreen() > center.getRed());
        assertTrue(center.getGreen() > center.getBlue());
    }

    @Test
    public void importImage_writesJpegAndRoundTrips() throws Exception {
        Path dir = Files.createTempDirectory("punchclock-avatar-test");
        Path source = dir.resolve("source.png");
        Path dest = dir.resolve(PeerAvatar.FILE_NAME);

        BufferedImage sourceImage = new BufferedImage(80, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sourceImage.createGraphics();
        g.setColor(new Color(12, 107, 107));
        g.fillRect(0, 0, 80, 120);
        g.dispose();
        ImageIO.write(sourceImage, "png", source.toFile());

        String encoded = PeerAvatar.importImage(source, dest);
        assertTrue(PeerAvatar.isValidEncoded(encoded));
        assertTrue(Files.exists(dest));
        assertTrue(encoded.length() < PeerAvatar.MAX_ENCODED_CHARS);

        BufferedImage decoded = PeerAvatar.decode(encoded);
        assertNotNull(decoded);
        assertEquals(PeerAvatar.PIXEL_SIZE, decoded.getWidth());
        assertEquals(PeerAvatar.PIXEL_SIZE, decoded.getHeight());

        String loaded = PeerAvatar.loadEncoded(dest);
        assertTrue(PeerAvatar.isValidEncoded(loaded));

        PeerAvatar.deleteFile(dest);
        assertFalse(Files.exists(dest));
        assertEquals("", PeerAvatar.loadEncoded(dest));
    }

    @Test
    public void fileBeside_usesConfigDirectory() {
        Path config = Path.of("/tmp/demo/.punchclock/config.json");
        assertEquals(Path.of("/tmp/demo/.punchclock/" + PeerAvatar.FILE_NAME), PeerAvatar.fileBeside(config));
    }
}
