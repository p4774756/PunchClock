package com.example.ui;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.assertTrue;

/** 設定外觀分頁 CandySwitch 開／關／disabled 對比與離屏預覽。 */
public class Issue787SettingsToggleTest {

    private static final Path OUT_DIR = Paths.get("target", "issue-787-verify");

    @Test
    public void switchPaletteMeetsContrastTargets() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertContrastAtLeast(CandySwitch.KNOB_FILL, CandySwitch.TRACK_ON_BOTTOM, 3.0);
            assertContrastAtLeast(CandySwitch.KNOB_FILL, CandySwitch.TRACK_OFF_BOTTOM, 3.0);
            assertContrastAtLeast(CandySwitch.TRACK_OFF_TOP, CandySwitch.TRACK_EDGE_OFF, 3.0);
        });
    }

    @Test
    public void appearanceSwitchesDifferOnAndOffWhenDisabled() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CandySwitch sw = new CandySwitch();
            sw.setSize(sw.getPreferredSize());
            int offX = knobCenterX(paintSwitch(new CandySwitch(), false, true));
            int onX = knobCenterX(paintSwitch(new CandySwitch(), true, true));
            assertTrue(offX + 4 < onX);
            int disabledOff = knobCenterX(paintSwitch(new CandySwitch(), false, false));
            int disabledOn = knobCenterX(paintSwitch(new CandySwitch(), true, false));
            assertTrue(disabledOff + 4 < disabledOn);
        });
    }

    @Test
    public void writeAppearanceTogglePreviews() throws Exception {
        Files.createDirectories(OUT_DIR);
        SwingUtilities.invokeAndWait(() -> {
            try {
                int w = 860;
                int h = 700;
                BufferedImage offShot = captureAppearanceSheet(w, h, false, false, false);
                BufferedImage onShot = captureAppearanceSheet(w, h, true, true, false);
                BufferedImage disabledShot = captureAppearanceSheet(w, h, true, false, true);

                assertTrue("off/on previews must differ", pixelDiff(offShot, onShot) > 0);
                assertTrue(pixelDiff(onShot, disabledShot) > 0);

                writeAppearancePng("settings-appearance-toggles-off.png", offShot);
                assertTrue(countPinkTrackPixels(offShot, 50) < 5);

                writeAppearancePng("settings-appearance-toggles-on.png", onShot);
                assertTrue(countPinkTrackPixels(onShot, 50) > 40);

                writeAppearancePng("settings-appearance-toggles-disabled.png", disabledShot);
                assertTrue(countPinkTrackPixels(disabledShot, 50) > 0);
                assertTrue(countMutedTrackPixels(disabledShot, 50) > 40);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void writeAppearancePng(String name, BufferedImage shot) throws Exception {
        Path path = OUT_DIR.resolve(name);
        assertTrue(ImageIO.write(shot, "png", path.toFile()));
        assertTrue(Files.size(path) > 400L);
    }

    private static BufferedImage captureAppearanceSheet(
            int w,
            int h,
            boolean desktopPet,
            boolean desktopPetCare,
            boolean mixedDisabled) {
        PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
        JTabbedPane tabs = PanelFactory.createServerConfigBody(
                refs, UiFonts.chinesePlain(13), UiFonts.chineseBold(13), UiFonts.latinPlain(13));
        tabs.setSelectedIndex(PanelFactory.SETTINGS_APPEARANCE_TAB);
        tabs.setSize(w, h);
        boolean dockSwimsuit = mixedDisabled ? true : desktopPet;
        boolean enabled = !mixedDisabled;
        setAppearanceSwitchStates(refs, desktopPet, desktopPetCare, dockSwimsuit, enabled);
        return paintTabs(tabs);
    }

    private static void setAppearanceSwitchStates(
            PanelFactory.ServerConfigRefs refs,
            boolean desktopPet,
            boolean desktopPetCare,
            boolean dockSwimsuit,
            boolean enabled) {
        syncSelected(refs.desktopPetSwitch, desktopPet);
        syncSelected(refs.desktopPetCare, desktopPetCare);
        syncSelected(refs.dockIconSwimsuitSwitch, dockSwimsuit);
        refs.desktopPetSwitch.setEnabled(enabled);
        refs.desktopPetCare.setEnabled(enabled);
        refs.dockIconSwimsuitSwitch.setEnabled(enabled);
        applyDesktopPetCareEnabled(refs);
    }

    private static void syncSelected(JToggleButton toggle, boolean selected) {
        if (toggle.isSelected() != selected) {
            toggle.setSelected(selected);
            return;
        }
        toggle.setSelected(!selected);
        toggle.setSelected(selected);
    }

    private static void applyDesktopPetCareEnabled(PanelFactory.ServerConfigRefs refs) {
        boolean on = refs.desktopPetSwitch.isSelected();
        refs.desktopPetCare.setEnabled(on);
    }

    private static BufferedImage paintTabs(JTabbedPane tabs) {
        int w = tabs.getWidth();
        int h = tabs.getHeight();
        return WindowChrome.renderOffscreen(tabs, w, h, ClassicBevel.FACE);
    }

    private static int pixelDiff(BufferedImage a, BufferedImage b) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        int diff = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    diff++;
                }
            }
        }
        return diff;
    }

    private static int countPinkTrackPixels(BufferedImage image, int yMin) {
        int n = 0;
        for (int y = yMin; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x, y));
                if (c.getRed() > 140 && c.getBlue() > 70 && c.getRed() > c.getGreen() + 10) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int countMutedTrackPixels(BufferedImage image, int yMin) {
        int n = 0;
        for (int y = yMin; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x, y));
                int spread = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()))
                        - Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
                if (spread < 28 && c.getRed() > 118 && c.getRed() < 198 && c.getGreen() > 118) {
                    n++;
                }
            }
        }
        return n;
    }

    private static BufferedImage paintSwitch(CandySwitch sw, boolean selected, boolean enabled) {
        sw.setSelected(selected);
        sw.setEnabled(enabled);
        Dimension size = sw.getPreferredSize();
        sw.setSize(size);
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(40, 40, 48));
            g.fillRect(0, 0, size.width, size.height);
            sw.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int knobCenterX(BufferedImage image) {
        int bestX = image.getWidth() / 2;
        int bestBright = -1;
        for (int y = 2; y < image.getHeight() - 2; y++) {
            for (int x = 2; x < image.getWidth() - 2; x++) {
                Color c = new Color(image.getRGB(x, y));
                int bright = c.getRed() + c.getGreen() + c.getBlue();
                if (c.getRed() > 220 && c.getGreen() > 220 && c.getBlue() > 220 && bright > bestBright) {
                    bestBright = bright;
                    bestX = x;
                }
            }
        }
        return bestX;
    }

    private static boolean looksLikePinkTrack(BufferedImage image) {
        Color c = sampleTrack(image, true);
        return c.getRed() > 140 && c.getBlue() > 70;
    }

    private static boolean looksLikeGrayTrack(BufferedImage image) {
        Color c = sampleTrack(image, false);
        int spread = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()))
                - Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
        return spread < 35 && c.getRed() > 95 && c.getRed() < 210;
    }

    private static Color sampleTrack(BufferedImage image, boolean onSide) {
        int x = onSide ? image.getWidth() * 3 / 4 : image.getWidth() / 2;
        int y = image.getHeight() / 2;
        return new Color(image.getRGB(x, y));
    }

    private static void assertContrastAtLeast(Color a, Color b, double min) {
        double ratio = contrast(a, b);
        assertTrue("contrast " + ratio + " < " + min, ratio >= min - 0.01);
    }

    private static double contrast(Color a, Color b) {
        double l1 = relativeLuminance(a);
        double l2 = relativeLuminance(b);
        double lighter = Math.max(l1, l2) + 0.05;
        double darker = Math.min(l1, l2) + 0.05;
        return lighter / darker;
    }

    private static double relativeLuminance(Color color) {
        double r = channel(color.getRed());
        double g = channel(color.getGreen());
        double b = channel(color.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
