package com.example.pet;

import com.example.ui.AppIconImages;
import com.example.ui.PanelFactory;
import com.example.ui.UiFonts;
import com.example.ui.hud.AnimeShell;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** 本機驗收截圖；輸出目錄由環境變數 SHOTS305_DIR 指定（不 commit）。 */
public class Issue305ScreenshotTest {

    @Test
    public void captureLayoutShots() throws Exception {
        String dir = System.getenv("SHOTS305_DIR");
        Assume.assumeTrue("SHOTS305_DIR not set", dir != null && !dir.isBlank());
        Path out = Path.of(dir);
        Files.createDirectories(out);
        SwingUtilities.invokeAndWait(() -> {
            try {
                var main = UiFonts.chinesePlain(13);
                var bold = UiFonts.chineseBold(13);
                var field = UiFonts.latinPlain(13);

                PanelFactory.ServerConfigRefs srefs = new PanelFactory.ServerConfigRefs();
                JTabbedPane settings = PanelFactory.createServerConfigBody(srefs, main, bold, field);
                settings.setSize(340, 520);
                save(settings, out.resolve("10-settings-connection.png"));
                settings.setSelectedIndex(1);
                save(settings, out.resolve("02-settings-appearance-pet.png"));
                settings.setSelectedIndex(2);
                save(settings, out.resolve("04-settings-about.png"));

                PanelFactory.PeerInteractionRefs prefs = new PanelFactory.PeerInteractionRefs();
                var peer = PanelFactory.createPeerInteractionPanel(prefs, main, bold);
                peer.setSize(360, 380);
                save(peer, out.resolve("01-peer-avatar-device-id.png"));

                PanelFactory.SlotPanelRefs slots = new PanelFactory.SlotPanelRefs();
                var slot = PanelFactory.createSlotPanel(slots, main, bold, field);
                slot.setSize(360, 480);
                save(slot, out.resolve("11-12-13-schedule-card.png"));

                AnimeShell shell = new AnimeShell(null, new JTabbedPane());
                shell.setSize(380, 520);
                shell.showHome();
                save(shell, out.resolve("08-tab-bar-mini.png"));

                long t = System.currentTimeMillis();
                DesktopPetCare care = new DesktopPetCare(() -> t);
                care.setEnabled(true, t);
                PetContextMenu menu = PetContextMenu.build(true, true, false, care, t, noopListener());
                menu.setSize(menu.getPreferredSize());
                save(menu, out.resolve("14-pet-context-menu.png"));

                BufferedImage icon = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                var icons = AppIconImages.loadAll();
                if (!icons.isEmpty()) {
                    Graphics2D g = icon.createGraphics();
                    g.drawImage(icons.get(Math.min(3, icons.size() - 1)), 0, 0, 128, 128, null);
                    g.dispose();
                    ImageIO.write(icon, "png", out.resolve("16-app-icon-128.png").toFile());
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static PetContextMenu.Listener noopListener() {
        return new PetContextMenu.Listener() {
            @Override public void onToggleControl(boolean enable) {}
            @Override public void onCare(DesktopPetCare.Action action, DesktopPetAnimation.Mode mode) {}
            @Override public void onToggleDebug() {}
            @Override public void onDismiss() {}
        };
    }

    private static void save(Component c, Path file) throws Exception {
        BufferedImage img = new BufferedImage(
                Math.max(1, c.getWidth()), Math.max(1, c.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        c.paint(g);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }
}