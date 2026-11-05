package com.example.pet;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * 讀取喵露待機 PNG。解圖在背景執行緒，結果回到 EDT。
 * 缺檔、尺寸不對或沒有透明色時回傳 null，呼叫端改畫原本的像素角色。
 */
public final class DesktopPetSpriteLoader {

    public static final int SOURCE = 400;
    public static final double SCALE = 0.5;
    public static final int DISPLAY = 200;

    private static final String[] BREATH = {
            "/pet/milu/idle/breath_01.png",
            "/pet/milu/idle/breath_02.png",
            "/pet/milu/idle/breath_03.png",
            "/pet/milu/idle/breath_04.png"
    };
    private static final String BLINK_1 = "/pet/milu/idle/blink_01.png";
    private static final String BLINK_2 = "/pet/milu/idle/blink_02.png";

    private DesktopPetSpriteLoader() {
    }

    public static final class Frames {
        private final BufferedImage[] breath;
        private final BufferedImage blink1;
        private final BufferedImage blink2;

        Frames(BufferedImage[] breath, BufferedImage blink1, BufferedImage blink2) {
            this.breath = breath;
            this.blink1 = blink1;
            this.blink2 = blink2;
        }

        public BufferedImage breath(int index) {
            return breath[Math.floorMod(index, breath.length)];
        }

        public BufferedImage blink1() {
            return blink1;
        }

        public BufferedImage blink2() {
            return blink2;
        }

        BufferedImage imageFor(DesktopPetAnimation animation) {
            if (animation.miluBlinking()) {
                int step = animation.miluBlinkStep();
                if (step == 1) {
                    return blink2;
                }
                if (step == 3) {
                    return breath[0];
                }
                return blink1;
            }
            return breath(animation.miluBreathIndex());
        }

        void flush() {
            for (BufferedImage image : breath) {
                image.flush();
            }
            blink1.flush();
            blink2.flush();
        }
    }

    /** 可在背景執行緒呼叫。失敗時回傳 null。 */
    public static Frames load() {
        try {
            BufferedImage[] breath = new BufferedImage[BREATH.length];
            for (int i = 0; i < BREATH.length; i++) {
                BufferedImage image = read(BREATH[i]);
                if (!acceptable(image)) {
                    return null;
                }
                breath[i] = defringe(image);
            }
            BufferedImage blink1 = read(BLINK_1);
            BufferedImage blink2 = read(BLINK_2);
            if (!acceptable(blink1) || !acceptable(blink2)) {
                return null;
            }
            return new Frames(breath, defringe(blink1), defringe(blink2));
        } catch (IOException ex) {
            return null;
        }
    }

    public static void loadAsync(Consumer<Frames> onEdt) {
        Thread worker = new Thread(() -> {
            Frames frames = load();
            SwingUtilities.invokeLater(() -> onEdt.accept(frames));
        }, "milu-idle-load");
        worker.setDaemon(true);
        worker.start();
    }

    static boolean acceptable(BufferedImage image) {
        if (image == null || image.getWidth() != SOURCE || image.getHeight() != SOURCE) {
            return false;
        }
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        return ((image.getRGB(0, 0) >>> 24) & 0xff) == 0;
    }

    /** 非透明像素的外框。用來確認六張圖的角色沒有跑位。 */
    static int[] contentBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) > 16) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    static void draw(Graphics2D g, BufferedImage image, int x, int y, int size) {
        Graphics2D copy = (Graphics2D) g.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            copy.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            copy.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            copy.drawImage(image, x, y, size, size, null);
        } finally {
            copy.dispose();
        }
    }

    /**
     * 透明像素的 RGB 是黑色時，雙三次縮放會在輪廓外拉出黑邊。
     * 只把鄰近角色的顏色滲進透明像素，alpha 維持 0。
     */
    private static BufferedImage defringe(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        int[] current = pixels;
        for (int pass = 0; pass < 6; pass++) {
            int[] next = current.clone();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    if (((current[index] >>> 24) & 0xff) != 0) {
                        continue;
                    }
                    int red = 0;
                    int green = 0;
                    int blue = 0;
                    int found = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = y + dy;
                        if (yy < 0 || yy >= height) {
                            continue;
                        }
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx;
                            if (xx < 0 || xx >= width || (dx == 0 && dy == 0)) {
                                continue;
                            }
                            int neighbor = current[yy * width + xx];
                            if (((neighbor >>> 24) & 0xff) == 0) {
                                continue;
                            }
                            red += (neighbor >> 16) & 0xff;
                            green += (neighbor >> 8) & 0xff;
                            blue += neighbor & 0xff;
                            found++;
                        }
                    }
                    if (found > 0) {
                        next[index] = ((red / found) << 16) | ((green / found) << 8) | (blue / found);
                    }
                }
            }
            current = next;
        }
        BufferedImage cleaned = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        cleaned.setRGB(0, 0, width, height, current, 0, width);
        return cleaned;
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = DesktopPetSpriteLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        }
    }
}
