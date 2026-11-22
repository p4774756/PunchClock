package com.example.ui;

import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headless 截圖輸出到 worktree 外 shots360-self（不 commit）。
 */
public class Issue360ScreenshotTest {

    private static final Path OUT =
            Path.of(System.getProperty("user.home"), "PunchClock-oki", "shots360-self", "dev1");

    @Test
    public void capturePeerFriendTable() throws Exception {
        Assume.assumeFalse("true".equals(System.getenv("CI")));
        Files.createDirectories(OUT);
        SwingUtilities.invokeAndWait(() -> {
            try {
                Font main = UiFonts.chinesePlain(13);
                Font bold = UiFonts.chineseBold(13);
                PanelFactory.PeerInteractionRefs refs = new PanelFactory.PeerInteractionRefs();
                JPanel panel = PanelFactory.createPeerInteractionPanel(refs, main, bold);
                Map<String, Boolean> online = new LinkedHashMap<>();
                online.put("company-worker2", true);
                online.put("company-worker3", false);
                refs.peerTable.putClientProperty(PanelFactory.PEER_ONLINE_CLIENT_KEY, online);
                refs.peerTableModel.addRow(new Object[]{"company-worker2", 1, "5.0.0", "★"});
                refs.peerTableModel.addRow(new Object[]{"company-worker3", 0, "4.8.2", "☆"});
                NightTheme.apply(refs.peerTable);
                refs.peerTable.setSize(300, 120);
                refs.peerTable.doLayout();
                refs.peerStatusLabel.setText("共 2 位好友 · 1 位在線");
                JScrollPane scroll = CompactShellPanels.wrap(panel);
                scroll.setSize(380, 560);
                scroll.doLayout();
                BufferedImage shot = new BufferedImage(380, 560, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = shot.createGraphics();
                scroll.paint(g);
                g.dispose();
                write("peer-friends-no-status-column.png", shot);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void write(String name, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", OUT.resolve(name).toFile());
    }
}