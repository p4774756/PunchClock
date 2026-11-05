package com.example.pet;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 讀取喵露 PNG。解圖在背景執行緒，結果回到 EDT。
 * 待機六張缺檔或不合格時回傳 null，呼叫端改畫原本的像素角色。
 * 走路、揮手、坐下、趴睡、拖曳會逐張篩掉裁切、標籤底和分離殘塊，缺的動作改畫待機。
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
    private static final String[] WALK_LEFT = {
            "/pet/milu/walk/left_01.png",
            "/pet/milu/walk/left_02.png",
            "/pet/milu/walk/left_03.png",
            "/pet/milu/walk/left_04.png"
    };
    private static final String[] WALK_RIGHT = {
            "/pet/milu/walk/right_01.png",
            "/pet/milu/walk/right_02.png",
            "/pet/milu/walk/right_03.png",
            "/pet/milu/walk/right_04.png"
    };
    private static final String[] WAVE = {
            "/pet/milu/wave/01.png",
            "/pet/milu/wave/02.png",
            "/pet/milu/wave/03.png",
            "/pet/milu/wave/04.png"
    };
    private static final String[] SIT = {
            "/pet/milu/sit/01.png",
            "/pet/milu/sit/02.png",
            "/pet/milu/sit/blink_01.png"
    };
    private static final String[] SLEEP = {
            "/pet/milu/sleep/01.png",
            "/pet/milu/sleep/02.png"
    };
    private static final String[] DRAG = {
            "/pet/milu/drag/01.png",
            "/pet/milu/drag/02.png"
    };

    private DesktopPetSpriteLoader() {
    }

    public static final class Frames {
        private final BufferedImage[] breath;
        private final BufferedImage blink1;
        private final BufferedImage blink2;
        private final BufferedImage[] walkLeft;
        private final BufferedImage[] walkRight;
        private final BufferedImage[] wave;
        private final BufferedImage[] sit;
        private final BufferedImage[] sleep;
        private final BufferedImage[] drag;

        Frames(BufferedImage[] breath, BufferedImage blink1, BufferedImage blink2,
                BufferedImage[] walkLeft, BufferedImage[] walkRight, BufferedImage[] wave,
                BufferedImage[] sit, BufferedImage[] sleep, BufferedImage[] drag) {
            this.breath = breath;
            this.blink1 = blink1;
            this.blink2 = blink2;
            this.walkLeft = walkLeft;
            this.walkRight = walkRight;
            this.wave = wave;
            this.sit = sit;
            this.sleep = sleep;
            this.drag = drag;
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

        public int walkLeftCount() {
            return walkLeft.length;
        }

        public int walkRightCount() {
            return walkRight.length;
        }

        public int waveCount() {
            return wave.length;
        }

        public int sitCount() {
            return sit.length;
        }

        public int sleepCount() {
            return sleep.length;
        }

        public int dragCount() {
            return drag.length;
        }

        public BufferedImage walkLeft(int index) {
            return walkLeft[Math.floorMod(index, walkLeft.length)];
        }

        public BufferedImage walkRight(int index) {
            return walkRight[Math.floorMod(index, walkRight.length)];
        }

        public BufferedImage wave(int index) {
            return wave[Math.floorMod(index, wave.length)];
        }

        /**
         * 顯示優先序：拖曳、揮手、走路、坐下、趴睡、待機。
         * 該動作沒有合格影格時，改畫待機呼吸或眨眼。
         */
        BufferedImage imageFor(DesktopPetAnimation animation) {
            if (animation.isCarried()) {
                return clipOrIdle(drag, animation);
            }
            DesktopPetAnimation.Mode mode = animation.mode();
            if (mode == DesktopPetAnimation.Mode.REACT) {
                return clipOrIdle(wave, animation);
            }
            if (mode == DesktopPetAnimation.Mode.WALK) {
                BufferedImage[] side = animation.facingRight() ? walkRight : walkLeft;
                return clipOrIdle(side, animation);
            }
            if (mode == DesktopPetAnimation.Mode.SIT) {
                return clipOrIdle(sit, animation);
            }
            if (mode == DesktopPetAnimation.Mode.SLEEP) {
                return clipOrIdle(sleep, animation);
            }
            return idleImage(animation);
        }

        private BufferedImage clipOrIdle(BufferedImage[] clip, DesktopPetAnimation animation) {
            if (clip == null || clip.length == 0) {
                return idleImage(animation);
            }
            return clip[animation.actionFrame(clip.length)];
        }

        private BufferedImage idleImage(DesktopPetAnimation animation) {
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
            flushAll(breath);
            blink1.flush();
            blink2.flush();
            flushAll(walkLeft);
            flushAll(walkRight);
            flushAll(wave);
            flushAll(sit);
            flushAll(sleep);
            flushAll(drag);
        }

        private static void flushAll(BufferedImage[] images) {
            if (images == null) {
                return;
            }
            for (BufferedImage image : images) {
                if (image != null) {
                    image.flush();
                }
            }
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
            return new Frames(
                    breath,
                    defringe(blink1),
                    defringe(blink2),
                    loadOptional(WALK_LEFT),
                    loadOptional(WALK_RIGHT),
                    loadOptional(WAVE),
                    loadOptional(SIT),
                    loadOptional(SLEEP),
                    loadOptional(DRAG));
        } catch (IOException ex) {
            return null;
        }
    }

    private static BufferedImage[] loadOptional(String[] paths) {
        List<BufferedImage> accepted = new ArrayList<BufferedImage>();
        for (String path : paths) {
            try {
                BufferedImage image = read(path);
                if (usable(image)) {
                    accepted.add(defringe(image));
                }
            } catch (IOException ex) {
                // 單張讀取失敗就略過，其他動作照常。
            }
        }
        return accepted.toArray(new BufferedImage[0]);
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

    /**
     * 動作影格能不能上桌面。
     * 畫布邊緣被角色碰到、腳下有寬標籤底、細長分離線，或角色外面有殘塊時回傳 false。
     * 頭上的小塊效果（例如 Z）留著。
     */
    static boolean usable(BufferedImage image) {
        if (!acceptable(image)) {
            return false;
        }
        List<int[]> blobs = blobs(image);
        if (blobs.isEmpty()) {
            return false;
        }
        int[] body = blobs.get(0);
        int width = image.getWidth();
        int height = image.getHeight();
        if (body[1] <= 0 || body[2] <= 0 || body[3] >= width - 1 || body[4] >= height - 1) {
            return false;
        }
        for (int i = 1; i < blobs.size(); i++) {
            int[] blob = blobs.get(i);
            if (inside(blob, body, 6)) {
                continue;
            }
            int blobW = blob[3] - blob[1] + 1;
            int blobH = blob[4] - blob[2] + 1;
            boolean thinBar = (blobH <= 14 && blobW >= 80) || (blobW <= 8 && blobH >= 80);
            boolean captionPlate = blob[2] >= height - 70 && blobW >= 80;
            if (thinBar || captionPlate) {
                return false;
            }
            int centerY = (blob[2] + blob[4]) / 2;
            int aspectSpan = Math.max(blobW, blobH);
            int aspectBase = Math.max(1, Math.min(blobW, blobH));
            boolean aboveHead = centerY < body[2] && aspectSpan < aspectBase * 3.5 && blob[0] < 3000;
            if (aboveHead) {
                continue;
            }
            if (blob[0] >= 80) {
                return false;
            }
        }
        return true;
    }

    private static boolean inside(int[] blob, int[] body, int pad) {
        return blob[1] >= body[1] - pad
                && blob[2] >= body[2] - pad
                && blob[3] <= body[3] + pad
                && blob[4] <= body[4] + pad;
    }

    /** 連通塊：n, minX, minY, maxX, maxY。面積大的在前。 */
    private static List<int[]> blobs(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[] seen = new boolean[width * height];
        List<int[]> blobs = new ArrayList<int[]>();
        int[] stack = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int start = y * width + x;
                if (seen[start] || alphaAt(image, x, y) <= 32) {
                    continue;
                }
                int top = 0;
                stack[top++] = start;
                seen[start] = true;
                int count = 0;
                int minX = x;
                int minY = y;
                int maxX = x;
                int maxY = y;
                while (top > 0) {
                    int current = stack[--top];
                    int cx = current % width;
                    int cy = current / width;
                    count++;
                    if (cx < minX) {
                        minX = cx;
                    }
                    if (cy < minY) {
                        minY = cy;
                    }
                    if (cx > maxX) {
                        maxX = cx;
                    }
                    if (cy > maxY) {
                        maxY = cy;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        int ny = cy + dy;
                        if (ny < 0 || ny >= height) {
                            continue;
                        }
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) {
                                continue;
                            }
                            int nx = cx + dx;
                            if (nx < 0 || nx >= width) {
                                continue;
                            }
                            int next = ny * width + nx;
                            if (seen[next] || alphaAt(image, nx, ny) <= 32) {
                                continue;
                            }
                            seen[next] = true;
                            stack[top++] = next;
                        }
                    }
                }
                blobs.add(new int[] {count, minX, minY, maxX, maxY});
            }
        }
        blobs.sort((a, b) -> Integer.compare(b[0], a[0]));
        return blobs;
    }

    private static int alphaAt(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
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

    static BufferedImage read(String path) throws IOException {
        try (InputStream in = DesktopPetSpriteLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        }
    }
}
