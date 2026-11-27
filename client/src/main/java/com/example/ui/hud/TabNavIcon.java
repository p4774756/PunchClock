package com.example.ui.hud;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 底部分頁列 Win98 風格像素圖示（16×16 原稿，整數倍 nearest-neighbor 繪製）。
 */
final class TabNavIcon {

    static final int ICON_SIZE = 16;
    static final int ICON_GAP = 4;
    private static final int SOURCE = 16;

    private static final int T = 0x00000000;
    private static final int K = 0xFF000000;
    private static final int D = 0xFF404040;
    private static final int M = 0xFF808080;
    private static final int L = 0xFFC0C0C0;
    private static final int W = 0xFFFFFFFF;

    private static final String[] NAV_ICON_IDS = {
            "home", "calendar-clock", "folder-up", "notebook-text", "settings"
    };

    private static final Map<String, TabNavIcon> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String[]> PIXELS = buildPixelMaps();

    private final BufferedImage image;
    private final int opaquePixels;

    private TabNavIcon(BufferedImage image, int opaquePixels) {
        this.image = image;
        this.opaquePixels = opaquePixels;
    }

    static String navIconId(int index) {
        if (index < 0 || index >= NAV_ICON_IDS.length) {
            return "";
        }
        return NAV_ICON_IDS[index];
    }

    static TabNavIcon load(String id) {
        return load(id, false);
    }

    static TabNavIcon loadActive(String id) {
        return load(id, true);
    }

    static TabNavIcon load(String id, boolean active) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String key = active ? id + "\0active" : id;
        return CACHE.computeIfAbsent(key, k -> bake(id, active));
    }

    boolean ready() {
        return opaquePixels > 0;
    }

    int partCount() {
        return opaquePixels;
    }

    int pixelDigest() {
        int hash = 0;
        for (int y = 0; y < SOURCE; y++) {
            for (int x = 0; x < SOURCE; x++) {
                hash = 31 * hash + image.getRGB(x, y);
            }
        }
        return hash;
    }

    static void draw(Graphics2D g, String id, int x, int y, int size, boolean active) {
        TabNavIcon icon = load(id, active);
        if (icon == null || !icon.ready() || size < 8) {
            return;
        }
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int drawW = size;
        int drawH = size;
        if (size % SOURCE == 0) {
            drawW = size;
            drawH = size;
        } else {
            int scale = Math.max(1, size / SOURCE);
            drawW = scale * SOURCE;
            drawH = scale * SOURCE;
        }
        g.drawImage(icon.image, x, y, drawW, drawH, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        if (interp != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
        }
    }

    private static TabNavIcon bake(String id, boolean active) {
        String key = pixelKey(id, active);
        String[] rows = PIXELS.get(key);
        if (rows == null) {
            return new TabNavIcon(emptyImage(), 0);
        }
        BufferedImage img = new BufferedImage(SOURCE, SOURCE, BufferedImage.TYPE_INT_ARGB);
        int count = 0;
        for (int y = 0; y < SOURCE && y < rows.length; y++) {
            String row = rows[y];
            for (int x = 0; x < SOURCE && x < row.length(); x++) {
                int argb = colorAt(row.charAt(x));
                img.setRGB(x, y, argb);
                if ((argb >>> 24) != 0) {
                    count++;
                }
            }
        }
        return new TabNavIcon(img, count);
    }

    private static BufferedImage emptyImage() {
        return new BufferedImage(SOURCE, SOURCE, BufferedImage.TYPE_INT_ARGB);
    }

    private static String pixelKey(String id, boolean active) {
        return active ? id + "-active" : id;
    }

    private static int colorAt(char ch) {
        switch (ch) {
            case 'k':
                return K;
            case 'd':
                return D;
            case 'm':
                return M;
            case 'l':
                return L;
            case 'w':
                return W;
            case '.':
            case ' ':
            default:
                return T;
        }
    }

    private static Map<String, String[]> buildPixelMaps() {
        Map<String, String[]> map = new HashMap<>();
        map.put("home", home(false));
        map.put("home-active", home(true));
        map.put("calendar-clock", calendar(false));
        map.put("calendar-clock-active", calendar(true));
        map.put("folder-up", folderUp(false));
        map.put("folder-up-active", folderUp(true));
        map.put("notebook-text", notebook(false));
        map.put("notebook-text-active", notebook(true));
        map.put("settings", settings(false));
        map.put("settings-active", settings(true));
        return Map.copyOf(map);
    }

  /** 小屋 */
    private static String[] home(boolean active) {
        if (active) {
            return rows(
                    "......kkkk......",
                    ".....kwwwwk.....",
                    "....kwwwwwwk....",
                    "...kwwwwwwwwk...",
                    "...kwkkkkkwwk...",
                    "...kwlwwwlwwk...",
                    "...kwlwwwlwwk...",
                    "...kwkkkkkwwk...",
                    "....kwwwwwwk....",
                    ".....kk..kk.....",
                    ".....k....k.....",
                    ".....kkkkkk.....",
                    "................",
                    "................",
                    "................",
                    "................");
        }
        return rows(
                "......kkkk......",
                ".....kllllk.....",
                "....kllllllk....",
                "...kllllllllk...",
                "...klkkkklllk...",
                "...klwwwwlllk...",
                "...klwwwwlllk...",
                "...klkkkklllk...",
                "....kllllllk....",
                ".....kk..kk.....",
                ".....k....k.....",
                ".....kkkkkk.....",
                "................",
                "................",
                "................",
                "................");
    }

    /** 日曆 */
    private static String[] calendar(boolean active) {
        if (active) {
            return rows(
                    "...kkkkkkkkkk...",
                    "...kwwwwwwwwk...",
                    "...kwwwwwwwwk...",
                    "...kkkkkkkkkk...",
                    "...kwkwkwkwkwk...",
                    "...kwdwdwdwdwk...",
                    "...kwkwkwkwkwk...",
                    "...kwdwdwdwdwk...",
                    "...kwkwkwkwkwk...",
                    "...kwdwdwdwdwk...",
                    "...kwkwkwkwkwk...",
                    "...kkkkkkkkkk...",
                    "................",
                    "................",
                    "................",
                    "................");
        }
        return rows(
                "...kkkkkkkkkk...",
                "...kmmmmmmmmk...",
                "...kmmmmmmmmk...",
                "...kkkkkkkkkk...",
                "...klmlmlmlmlk...",
                "...kmdmdmdmdmk...",
                "...klmlmlmlmlk...",
                "...kmdmdmdmdmk...",
                "...klmlmlmlmlk...",
                "...kmdmdmdmdmk...",
                "...klmlmlmlmlk...",
                "...kkkkkkkkkk...",
                "................",
                "................",
                "................",
                "................");
    }

    /** 資料夾上傳 */
    private static String[] folderUp(boolean active) {
        if (active) {
            return rows(
                    "................",
                    "......kkkk......",
                    ".....kwwwwk.....",
                    "....kwwwwwwk....",
                    "....kwwwwwwk....",
                    "...kkkkkkkkkk...",
                    "...kwkwkwkwkwk...",
                    "...kwwwwwwwwk...",
                    "...kwwwwwwwwk...",
                    "...kwwwwwwwwk...",
                    "...kkkkkkkkkk...",
                    "................",
                    ".....k....k.....",
                    ".....k....k.....",
                    "......kkkk......",
                    "................");
        }
        return rows(
                "................",
                "......kkkk......",
                ".....kllllk.....",
                "....kllllllk....",
                "....kllllllk....",
                "...kkkkkkkkkk...",
                "...klmlmlmlmlk...",
                "...kllllllllk...",
                "...kllllllllk...",
                "...kllllllllk...",
                "...kkkkkkkkkk...",
                "................",
                ".....k....k.....",
                ".....k....k.....",
                "......kkkk......",
                "................");
    }

    /** 記事本 */
    private static String[] notebook(boolean active) {
        if (active) {
            return rows(
                    "....kkkkkkkk....",
                    "...kwwwwwwwwk...",
                    "...kwkkkkkkkwk...",
                    "...kwdddddddwk...",
                    "...kwdddddddwk...",
                    "...kwdddddddwk...",
                    "...kwdddddddwk...",
                    "...kwdddddddwk...",
                    "...kwdddddddwk...",
                    "...kwdddddddwk...",
                    "...kwwwwwwwwk...",
                    "....kkkkkkkk....",
                    "................",
                    "................",
                    "................",
                    "................");
        }
        return rows(
                "....kkkkkkkk....",
                "...kllllllllk...",
                "...klkkkkkkklk...",
                "...kldddddddlk...",
                "...kldddddddlk...",
                "...kldddddddlk...",
                "...kldddddddlk...",
                "...kldddddddlk...",
                "...kldddddddlk...",
                "...kldddddddlk...",
                "...kllllllllk...",
                "....kkkkkkkk....",
                "................",
                "................",
                "................",
                "................");
    }

    /** 齒輪 */
    private static String[] settings(boolean active) {
        if (active) {
            return rows(
                    "......kkkk......",
                    "....kkwwwwkk....",
                    "...kwwwwwwwwk...",
                    "...kwwkkkkwwk...",
                    "..kwwwkkkkwwwk..",
                    "..kwwwkkkkwwwk..",
                    "...kwwkkkkwwk...",
                    "...kwwwwwwwwk...",
                    "....kkwwwwkk....",
                    "......kkkk......",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................");
        }
        return rows(
                "......kkkk......",
                "....kkllllkk....",
                "...kllllllllk...",
                "...kllkkkkllk...",
                "..klllklkklllk..",
                "..klllklkklllk..",
                "...kllkkkkllk...",
                "...kllllllllk...",
                "....kkllllkk....",
                "......kkkk......",
                "................",
                "................",
                "................",
                "................",
                "................",
                "................");
    }

    private static String[] rows(String... lines) {
        String[] out = new String[SOURCE];
        for (int i = 0; i < SOURCE; i++) {
            out[i] = i < lines.length ? lines[i] : "................";
        }
        return out;
    }
}