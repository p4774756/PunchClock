package com.example.service;

import org.junit.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PeerImageTest {

    @Test
    public void encode_scalesDownLargeImageAndRoundTrips() throws Exception {
        BufferedImage source = new BufferedImage(3000, 1500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(new Color(12, 107, 107));
        g.fillRect(0, 0, 3000, 1500);
        g.dispose();

        String encoded = PeerImage.encode(source);
        assertTrue(encoded.matches("[A-Za-z0-9_-]+"));

        BufferedImage decoded = PeerImage.decode(encoded);
        assertNotNull(decoded);
        assertEquals(PeerImage.MAX_EDGE_PIXELS, Math.max(decoded.getWidth(), decoded.getHeight()));
        assertEquals(2, decoded.getWidth() / decoded.getHeight());
    }

    @Test
    public void encode_keepsSmallImageSize() throws Exception {
        BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        BufferedImage decoded = PeerImage.decode(PeerImage.encode(source));
        assertNotNull(decoded);
        assertEquals(200, decoded.getWidth());
        assertEquals(100, decoded.getHeight());
    }

    @Test
    public void encode_noisyImageStaysUnderLimit() throws Exception {
        BufferedImage source = new BufferedImage(2400, 1800, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        String encoded = PeerImage.encode(source);
        int bytes = Base64.getUrlDecoder().decode(encoded).length;
        assertTrue("jpeg bytes " + bytes, bytes <= PeerImage.MAX_JPEG_BYTES);
    }

    @Test
    public void decode_rejectsGarbage() {
        assertNull(PeerImage.decode(""));
        assertNull(PeerImage.decode(null));
        assertNull(PeerImage.decode("not-an-image"));
    }
}
