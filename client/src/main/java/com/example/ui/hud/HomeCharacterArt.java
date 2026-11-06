package com.example.ui.hud;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * 首頁跨框的素材。上半身和襪腳是同一張全身圖，兩個圖層共用像素和對齊點。
 * <p>
 * {@code /hud/home/home-body.png}：768×1024。
 * 臉中心在寬 46%、高 21%；袖子上緣在高 22.7%；前臂下緣在高 32.6%；指尖在高 37.8%；
 * 襪尖在寬 96.2%。面板蓋住軀幹和大部分腿，右框只露出一小段襪尖。
 */
final class HomeCharacterArt {

    final BufferedImage bust;
    final BufferedImage sock;

    private HomeCharacterArt(BufferedImage body) {
        this.bust = body;
        this.sock = body;
    }

    static HomeCharacterArt load() {
        BufferedImage body = read("/hud/home/home-body.png");
        if (body == null) {
            return null;
        }
        return new HomeCharacterArt(body);
    }

    boolean opaqueBust(int imageX, int imageY) {
        if (imageX < 0 || imageY < 0 || imageX >= bust.getWidth() || imageY >= bust.getHeight()) {
            return false;
        }
        return ((bust.getRGB(imageX, imageY) >>> 24) & 0xff) > 24;
    }

    private static BufferedImage read(String path) {
        try (InputStream in = HomeCharacterArt.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException ex) {
            return null;
        }
    }
}
