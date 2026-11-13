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
 * 走路、揮手、坐下、趴睡、拖曳、抓起、掙扎、放下會逐張篩掉裁切、標籤底和分離殘塊，缺的動作改畫待機。
 */
public final class DesktopPetSpriteLoader {

    public static final int SOURCE = 400;
    public static final double SCALE = 0.5;
    public static final int DISPLAY = 200;
    /** 畫布上的固定錨點：軀幹水平中心、站立腳底。 */
    public static final int ANCHOR_X = 200;
    public static final int ANCHOR_Y = 359;

    /** 一張已對齊的影格。offset 是畫進固定畫布前的源像素修正，不是視窗位移。 */
    public static final class Sprite {
        public final BufferedImage image;
        public final String name;
        public final int offsetX;
        public final int offsetY;
        public final int anchorX;
        public final int anchorY;

        Sprite(BufferedImage image, String name, int offsetX, int offsetY, int anchorX, int anchorY) {
            this.image = image;
            this.name = name;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }

        public int placedX() {
            return anchorX + offsetX;
        }

        public int placedY() {
            return anchorY + offsetY;
        }
    }

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
    private static final String[] GRAB = {
            "/pet/milu/grab/01.png",
            "/pet/milu/grab/02.png"
    };
    private static final String[] STRUGGLE = {
            "/pet/milu/struggle/01.png",
            "/pet/milu/struggle/02.png",
            "/pet/milu/struggle/03.png",
            "/pet/milu/struggle/04.png"
    };
    private static final String[] RELEASE = {
            "/pet/milu/release/01.png",
            "/pet/milu/release/02.png",
            "/pet/milu/release/03.png"
    };

    private DesktopPetSpriteLoader() {
    }

    public static final class Frames {
        private final Sprite[] breath;
        private final Sprite blink1;
        private final Sprite blink2;
        private final Sprite[] walkLeft;
        private final Sprite[] walkRight;
        private final Sprite[] wave;
        private final Sprite[] sit;
        private final Sprite[] sleep;
        private final Sprite[] drag;
        private final Sprite[] grab;
        private final Sprite[] struggle;
        private final Sprite[] release;

        Frames(Sprite[] breath, Sprite blink1, Sprite blink2,
                Sprite[] walkLeft, Sprite[] walkRight, Sprite[] wave,
                Sprite[] sit, Sprite[] sleep, Sprite[] drag,
                Sprite[] grab, Sprite[] struggle, Sprite[] release) {
            this.breath = breath;
            this.blink1 = blink1;
            this.blink2 = blink2;
            this.walkLeft = walkLeft;
            this.walkRight = walkRight;
            this.wave = wave;
            this.sit = sit;
            this.sleep = sleep;
            this.drag = drag;
            this.grab = grab;
            this.struggle = struggle;
            this.release = release;
        }

        public BufferedImage breath(int index) {
            return breath[Math.floorMod(index, breath.length)].image;
        }

        public BufferedImage blink1() {
            return blink1.image;
        }

        public BufferedImage blink2() {
            return blink2.image;
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

        public int grabCount() {
            return grab.length;
        }

        public int struggleCount() {
            return struggle.length;
        }

        public int releaseCount() {
            return release.length;
        }

        public BufferedImage grab(int index) {
            return grab[Math.floorMod(index, grab.length)].image;
        }

        public BufferedImage struggle(int index) {
            return struggle[Math.floorMod(index, struggle.length)].image;
        }

        public BufferedImage release(int index) {
            return release[Math.floorMod(index, release.length)].image;
        }

        public BufferedImage walkLeft(int index) {
            return walkLeft[Math.floorMod(index, walkLeft.length)].image;
        }

        public BufferedImage walkRight(int index) {
            return walkRight[Math.floorMod(index, walkRight.length)].image;
        }

        public BufferedImage wave(int index) {
            return wave[Math.floorMod(index, wave.length)].image;
        }

        public BufferedImage sit(int index) {
            return sit[Math.floorMod(index, sit.length)].image;
        }

        public BufferedImage sleep(int index) {
            return sleep[Math.floorMod(index, sleep.length)].image;
        }

        public Sprite spriteFor(DesktopPetAnimation animation) {
            Sprite[] carry = carryClip(animation.carryPhase());
            if (carry != null && carry.length > 0) {
                return carry[animation.carryFrame(carry.length)];
            }
            if (animation.isCarried()) {
                return clipOrIdleSprite(drag, animation);
            }
            DesktopPetAnimation.Mode mode = animation.mode();
            if (mode == DesktopPetAnimation.Mode.REACT) {
                return clipOrIdleSprite(wave, animation);
            }
            if (mode == DesktopPetAnimation.Mode.WALK) {
                Sprite[] side = animation.facingRight() ? walkRight : walkLeft;
                return clipOrIdleSprite(side, animation);
            }
            if (mode == DesktopPetAnimation.Mode.SIT) {
                return clipOrIdleSprite(sit, animation);
            }
            if (mode == DesktopPetAnimation.Mode.SLEEP) {
                return clipOrIdleSprite(sleep, animation);
            }
            return idleSprite(animation);
        }

        Sprite[] allSprites() {
            List<Sprite> all = new ArrayList<Sprite>();
            for (Sprite sprite : breath) {
                all.add(sprite);
            }
            all.add(blink1);
            all.add(blink2);
            addAll(all, walkLeft);
            addAll(all, walkRight);
            addAll(all, wave);
            addAll(all, sit);
            addAll(all, sleep);
            addAll(all, drag);
            addAll(all, grab);
            addAll(all, struggle);
            addAll(all, release);
            return all.toArray(new Sprite[0]);
        }

        private Sprite[] carryClip(DesktopPetAnimation.CarryPhase phase) {
            if (phase == DesktopPetAnimation.CarryPhase.GRAB) {
                return grab;
            }
            if (phase == DesktopPetAnimation.CarryPhase.STRUGGLE) {
                return struggle;
            }
            if (phase == DesktopPetAnimation.CarryPhase.RELEASE) {
                return release;
            }
            return null;
        }

        private static void addAll(List<Sprite> all, Sprite[] sprites) {
            if (sprites == null) {
                return;
            }
            for (Sprite sprite : sprites) {
                all.add(sprite);
            }
        }

        /**
         * 顯示優先序：抓起／掙扎／放下、揮手、走路、坐下、趴睡、待機。
         * 該動作沒有合格影格時，改畫待機呼吸或眨眼。
         */
        BufferedImage imageFor(DesktopPetAnimation animation) {
            return spriteFor(animation).image;
        }

        private Sprite clipOrIdleSprite(Sprite[] clip, DesktopPetAnimation animation) {
            if (clip == null || clip.length == 0) {
                return idleSprite(animation);
            }
            return clip[animation.actionFrame(clip.length)];
        }

        private Sprite idleSprite(DesktopPetAnimation animation) {
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
            return breath[Math.floorMod(animation.miluBreathIndex(), breath.length)];
        }

        void flush() {
            flushAll(breath);
            blink1.image.flush();
            blink2.image.flush();
            flushAll(walkLeft);
            flushAll(walkRight);
            flushAll(wave);
            flushAll(sit);
            flushAll(sleep);
            flushAll(drag);
            flushAll(grab);
            flushAll(struggle);
            flushAll(release);
        }

        private static void flushAll(Sprite[] sprites) {
            if (sprites == null) {
                return;
            }
            for (Sprite sprite : sprites) {
                if (sprite != null && sprite.image != null) {
                    sprite.image.flush();
                }
            }
        }
    }

    /** 可在背景執行緒呼叫。失敗時回傳 null。 */
    public static Frames load() {
        try {
            Sprite[] breath = new Sprite[BREATH.length];
            for (int i = 0; i < BREATH.length; i++) {
                BufferedImage image = read(BREATH[i]);
                if (!acceptable(image)) {
                    return null;
                }
                breath[i] = place(BREATH[i], defringe(image));
            }
            BufferedImage blink1 = read(BLINK_1);
            BufferedImage blink2 = read(BLINK_2);
            if (!acceptable(blink1) || !acceptable(blink2)) {
                return null;
            }
            Sprite[] release = loadOptional(RELEASE);
            int carryShift = carryOffsetY(release);
            return new Frames(
                    breath,
                    place(BLINK_1, defringe(blink1)),
                    place(BLINK_2, defringe(blink2)),
                    loadOptional(WALK_LEFT),
                    loadOptional(WALK_RIGHT),
                    loadOptional(WAVE),
                    loadOptional(SIT),
                    loadOptional(SLEEP),
                    loadOptional(DRAG),
                    withOffsetY(loadOptional(GRAB), carryShift),
                    withOffsetY(loadOptional(STRUGGLE), carryShift),
                    withOffsetY(release, carryShift));
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * 抓起、掙扎、放下是同一套畫布，懸空與落地的高度差是畫好的。
     * 整套共用一個垂直修正：讓放下的腳底落在 {@link #ANCHOR_Y}，懸空影格跟著同量平移，不逐張壓回地面。
     * 放下影格全部不合格時不平移，保留素材原高度。
     */
    static int carryOffsetY(Sprite[] landed) {
        int ground = -1;
        for (Sprite sprite : landed) {
            ground = Math.max(ground, sprite.anchorY);
        }
        return ground < 0 ? 0 : ANCHOR_Y - ground;
    }

    private static Sprite[] withOffsetY(Sprite[] sprites, int offsetY) {
        Sprite[] shifted = new Sprite[sprites.length];
        for (int i = 0; i < sprites.length; i++) {
            Sprite sprite = sprites[i];
            shifted[i] = new Sprite(sprite.image, sprite.name, sprite.offsetX, offsetY, sprite.anchorX, sprite.anchorY);
        }
        return shifted;
    }

    private static Sprite[] loadOptional(String[] paths) {
        List<Sprite> accepted = new ArrayList<Sprite>();
        for (String path : paths) {
            try {
                BufferedImage image = read(path);
                if (usable(image)) {
                    accepted.add(place(path, defringe(image)));
                }
            } catch (IOException ex) {
                // 單張讀取失敗就略過，其他動作照常。
            }
        }
        return accepted.toArray(new Sprite[0]);
    }

    private static Sprite place(String path, BufferedImage image) {
        int[] anchor = measureAnchor(image);
        String name = path;
        String prefix = "/pet/milu/";
        if (name.startsWith(prefix)) {
            name = name.substring(prefix.length());
        }
        if (name.endsWith(".png")) {
            name = name.substring(0, name.length() - 4);
        }
        return new Sprite(
                image,
                name,
                ANCHOR_X - anchor[0],
                ANCHOR_Y - anchor[1],
                anchor[0],
                anchor[1]);
    }

    /**
     * 站立、揮手、坐下量胸口水平中心與腳底。
     * 趴睡的身體是橫的，改量貼地那一段的中心，避免把頭部當成軀幹。
     */
    static int[] measureAnchor(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int footY = height - 1;
        for (int y = height - 1; y >= 0; y--) {
            int run = 0;
            for (int x = 0; x < width; x++) {
                if (alphaAt(image, x, y) > 40) {
                    run++;
                }
            }
            if (run >= 8) {
                footY = y;
                break;
            }
        }
        int[] box = contentBounds(image);
        boolean lying = box[2] >= box[0] && (box[2] - box[0]) > (box[3] - box[1]);
        int y0 = lying ? Math.max(0, footY - 20) : Math.max(0, footY - 190);
        int y1 = lying ? footY : Math.max(0, footY - 150);
        long sum = 0;
        int count = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = 0; x < width; x++) {
                if (alphaAt(image, x, y) > 48) {
                    sum += x;
                    count++;
                }
            }
        }
        int anchorX = count == 0 ? ANCHOR_X : (int) Math.round(sum / (double) count);
        return new int[] {anchorX, footY};
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
        draw(g, image, x, y, size, 0, 0);
    }

    static void draw(Graphics2D g, BufferedImage image, int x, int y, int size, int offsetX, int offsetY) {
        Graphics2D copy = (Graphics2D) g.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            copy.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            copy.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            copy.translate(offsetX * SCALE, offsetY * SCALE);
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
