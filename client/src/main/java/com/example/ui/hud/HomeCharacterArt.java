package com.example.ui.hud;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * 首頁跨框的素材。上半身和襪腳是同一張全身圖，兩個圖層共用像素和對齊點。
 * <p>
 * {@code /hud/home/home-body.png}：768×1024。
 * 前臂下緣在高 32.6%，貼卡片頂緣；指尖在高 37.8%。
 * 卡片底緣在高 66%，右下角露出一小截小腿、襪口和襪腳。
 */
final class HomeCharacterArt {

    final BufferedImage bust;
    final BufferedImage sock;
    /** 卡片前面只畫這兩隻手，其餘像素是透明的。 */
    final BufferedImage fingers;
    /** 卡片右下角露出的那一條腿，其餘像素是透明的。 */
    final BufferedImage extendedLeg;

    private HomeCharacterArt(BufferedImage body, HomeFigureMask mask) {
        this.bust = body;
        this.sock = body;
        this.fingers = mask.fingers;
        this.extendedLeg = mask.extendedLeg;
    }

    static HomeCharacterArt load() {
        BufferedImage body = read("/hud/home/home-body.png");
        if (body == null) {
            return null;
        }
        return new HomeCharacterArt(body, HomeFigureMask.build(body));
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
