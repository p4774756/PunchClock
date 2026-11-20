package com.example.pet;

import com.example.ui.UiFonts;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DesktopPetMenuPaintTest {

    @Test
    public void menuItemOrderStatusBarsAndSingleInstance() throws Exception {
        long t = 1_700_000_000_000L;
        DesktopPetCare care = new DesktopPetCare(() -> t);
        care.setEnabled(true, t);
        care.restore(new DesktopPetCare.Snapshot(
                55, 60, 65, t, 0L, 0L, 0L, t, t, true, false));
        final PetContextMenu[] holder = new PetContextMenu[1];
        SwingUtilities.invokeAndWait(() -> {
            UiFonts.chinesePlain(13);
            holder[0] = PetContextMenu.build(
                    true, true, false, care, t, noopListener());
        });
        PetContextMenu menu = holder[0];
        assertEquals(55, menu.statusHunger());
        assertEquals(60, menu.statusMood());
        assertEquals(65, menu.statusEnergy());
        List<String> labels = new ArrayList<>();
        for (PetContextMenu.Row row : menu.rows()) {
            if (row.kind == PetContextMenu.RowKind.STATUS) {
                continue;
            }
            labels.add(row.label);
        }
        assertEquals(5, labels.size());
        assertEquals("操控模式", labels.get(0));
        assertEquals("餵食", labels.get(1));
        assertEquals("摸頭", labels.get(2));
        assertEquals("玩耍", labels.get(3));
        assertEquals("顯示除錯", labels.get(4));
        assertTrue(menu.rows().stream().anyMatch(r -> r.kind == PetContextMenu.RowKind.CONTROL && r.checked));
    }

    @Test
    public void controlInputMapAndFocusableToggle() throws Exception {
        Assume.assumeFalse("headless JVM cannot construct pet window",
                java.awt.GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            DesktopPetWindow pet = new DesktopPetWindow(new DesktopPetSettings(new TestPreferences()));
            try {
                assertTrue(pet.controlBindingsInstalled());
                assertFalse(pet.getFocusableWindowState());
                pet.enterControlMode();
                assertTrue(pet.controlModeActive());
                assertTrue(pet.getFocusableWindowState());
                assertTrue(pet.stage().isFocusable());
                pet.exitControlMode();
                assertFalse(pet.controlModeActive());
                assertFalse(pet.getFocusableWindowState());
                assertFalse(pet.stage().isFocusable());
            } finally {
                pet.disposePet();
            }
        });
    }

    @Test
    public void headlessControlKeyBindingsOnPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JPanel root = new javax.swing.JPanel();
            PetControlKeyBindings.install(root, noopHandler());
            assertTrue(PetControlKeyBindings.bindingsPresent(root));
        });
    }

    @Test
    public void clampMenuOriginStaysInsideTheOverlappingScreen() {
        Rectangle[] staggered = {
                new Rectangle(0, 0, 1440, 900),
                new Rectangle(1440, -200, 1920, 1200)
        };
        Point nearBottom = PetContextMenu.clampMenuOrigin(200, 850, 208, 286, staggered);
        assertEquals(200, nearBottom.x);
        assertEquals(614, nearBottom.y);
        assertTrue(staggered[0].contains(new Rectangle(nearBottom.x, nearBottom.y, 208, 286)));

        Point straddling = PetContextMenu.clampMenuOrigin(1400, 850, 208, 286, staggered);
        assertEquals(1440, straddling.x);
        assertEquals(714, straddling.y);
        assertTrue(staggered[1].contains(new Rectangle(straddling.x, straddling.y, 208, 286)));

        Point onSecond = PetContextMenu.clampMenuOrigin(2000, 100, 208, 286, staggered);
        assertEquals(2000, onSecond.x);
        assertEquals(100, onSecond.y);
        assertTrue(staggered[1].contains(new Rectangle(onSecond.x, onSecond.y, 208, 286)));

        Rectangle[] one = {new Rectangle(0, 0, 1280, 800)};
        Point corner = PetContextMenu.clampMenuOrigin(1200, 760, 208, 286, one);
        assertEquals(1072, corner.x);
        assertEquals(514, corner.y);
        assertTrue(one[0].contains(new Rectangle(corner.x, corner.y, 208, 286)));

        Point inside = PetContextMenu.clampMenuOrigin(40, 50, 208, 286, one);
        assertEquals(40, inside.x);
        assertEquals(50, inside.y);

        Point none = PetContextMenu.clampMenuOrigin(3, 4, 208, 286, new Rectangle[0]);
        assertEquals(3, none.x);
        assertEquals(4, none.y);
        Point nil = PetContextMenu.clampMenuOrigin(3, 4, 208, 286, null);
        assertEquals(3, nil.x);
        assertEquals(4, nil.y);
    }

    @Test
    public void menuWindowUsesPerPixelTransparencyWhenSupported() throws Exception {
        Assume.assumeFalse("headless JVM cannot construct a window",
                GraphicsEnvironment.isHeadless());
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Assume.assumeTrue(device.isWindowTranslucencySupported(
                GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT));
        SwingUtilities.invokeAndWait(() -> {
            JWindow window = new JWindow();
            try {
                DesktopPetWindow.prepareMenuWindowTransparency(window);
                assertNotNull(window.getBackground());
                assertEquals(0, window.getBackground().getAlpha());
                assertFalse(window.getRootPane().isOpaque());
                assertTrue(window.getContentPane() instanceof JComponent);
                assertFalse(((JComponent) window.getContentPane()).isOpaque());
            } finally {
                window.dispose();
            }
        });
    }

    @Test
    public void refocusHintWhenUnfocused() {
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        assertEquals(DesktopPetControl.HINT, control.hintText(true));
        assertEquals(DesktopPetControl.HINT_REFOCUS, control.hintText(false));
    }

    @Test
    public void paintMenuToBufferedImageForScreenshot() throws Exception {
        long t = 1_700_000_000_000L;
        DesktopPetCare care = new DesktopPetCare(() -> t);
        care.setEnabled(true, t);
        final BufferedImage[] shot = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            UiFonts.chinesePlain(13);
            PetContextMenu menu = PetContextMenu.build(
                    true, false, true, care, t, noopListener());
            java.awt.Dimension size = menu.getPreferredSize();
            shot[0] = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = shot[0].createGraphics();
            try {
                menu.setSize(size);
                menu.paint(g);
            } finally {
                g.dispose();
            }
        });
        assertNotNull(shot[0]);
        Path dir = Path.of(System.getProperty("user.home"), "PunchClock-oki", "shots273-self");
        Files.createDirectories(dir);
        Path out = dir.resolve("pet-menu.png");
        ImageIO.write(shot[0], "png", out.toFile());
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 500L);
    }

    private static PetControlKeyBindings.Handler noopHandler() {
        return new PetControlKeyBindings.Handler() {
            @Override
            public boolean controlActive() {
                return false;
            }

            @Override
            public void onKeyPressed(int vk) {
            }

            @Override
            public void onKeyReleased(int vk) {
            }

            @Override
            public boolean menuVisible() {
                return false;
            }

            @Override
            public void hideMenu() {
            }

            @Override
            public void exitControl() {
            }
        };
    }

    private static PetContextMenu.Listener noopListener() {
        return new PetContextMenu.Listener() {
            @Override
            public void onToggleControl(boolean enable) {
            }

            @Override
            public void onCare(DesktopPetCare.Action action, DesktopPetAnimation.Mode mode) {
            }

            @Override
            public void onToggleDebug() {
            }

            @Override
            public void onDismiss() {
            }
        };
    }

    private static final class TestPreferences extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();

        TestPreferences() {
            super(null, "");
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
            values.clear();
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return new TestPreferences();
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
        }
    }
}