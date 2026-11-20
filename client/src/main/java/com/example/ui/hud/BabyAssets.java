package com.example.ui.hud;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** 新光小寶貝坐姿／趴姿去背圖（#274）。 */
public final class BabyAssets {

    private final BufferedImage seated;
    private final BufferedImage prone;

    private BabyAssets(BufferedImage seated, BufferedImage prone) {
        this.seated = seated;
        this.prone = prone;
    }

    public BufferedImage seated() {
        return seated;
    }

    public BufferedImage prone() {
        return prone;
    }

    public static BabyAssets load() {
        return new BabyAssets(read("/hud/baby/seated.png"), read("/hud/baby/prone.png"));
    }

    private static BufferedImage read(String path) {
        try (InputStream in = BabyAssets.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException ex) {
            return null;
        }
    }
}