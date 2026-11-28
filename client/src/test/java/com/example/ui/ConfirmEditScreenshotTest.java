package com.example.ui;

import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 行內確認欄位的 offscreen 截圖，輸出到 worktree 外 shots-inline（不 commit）。
 */
public class ConfirmEditScreenshotTest {

    private static final Path OUT =
            Path.of(System.getProperty("user.home"), "PunchClock-oki", "shots-inline");

    @Test
    public void captureStatusAndSlotNameRows() throws Exception {
        Assume.assumeFalse("true".equals(System.getenv("CI")));
        Files.createDirectories(OUT);
        SwingUtilities.invokeAndWait(() -> {
            try {
                Font main = UiFonts.chinesePlain(12);
                Font bold = UiFonts.chineseBold(12);
                Font field = UiFonts.chinesePlain(12);

                PanelFactory.PeerInteractionRefs peer = new PanelFactory.PeerInteractionRefs();
                JPanel peerPanel = PanelFactory.createPeerInteractionPanel(peer, main, bold);
                peer.statusTextField.setText("忙碌中請留言");
                ConfirmEditSupport.setCommitted(peer.statusTextField, "");
                ConfirmEditSupport.syncButtons(peer.statusApplyButton, peer.statusCancelButton, true, true);
                write("swing-status-dirty.png", cropRow(peer.statusRow, 420, 44));

                peer.statusTextField.setText("");
                ConfirmEditSupport.setCommitted(peer.statusTextField, "");
                ConfirmEditSupport.syncButtons(peer.statusApplyButton, peer.statusCancelButton, false, false);
                write("swing-status-empty.png", cropRow(peer.statusRow, 420, 44));

                PanelFactory.SlotPanelRefs slots = new PanelFactory.SlotPanelRefs();
                PanelFactory.createSlotPanel(slots, main, bold, field);
                slots.workIn.nameField.setText("下午出門一趟");
                ConfirmEditSupport.setCommitted(slots.workIn.nameField, "上班");
                ConfirmEditSupport.syncButtons(slots.workIn.nameApplyButton, slots.workIn.nameCancelButton, true, true);
                write("swing-slot-name-dirty.png", cropRow(slots.workIn.nameField.getParent(), 420, 44));

                // 完整好友互動面板，確認狀態列與裝置 ID 列都看得到方鈕
                JScrollPane scroll = new JScrollPane(peerPanel);
                scroll.setPreferredSize(new Dimension(360, 520));
                scroll.setSize(new Dimension(360, 520));
                scroll.doLayout();
                peerPanel.setSize(Math.max(360, peerPanel.getPreferredSize().width),
                        Math.max(200, peerPanel.getPreferredSize().height));
                layoutDeep(peerPanel);
                write("swing-peer-panel.png", paint(peerPanel,
                        Math.max(360, peerPanel.getWidth()),
                        Math.min(520, Math.max(200, peerPanel.getPreferredSize().height))));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static BufferedImage cropRow(java.awt.Component row, int width, int height) {
        row.setSize(new Dimension(width, Math.max(height, row.getPreferredSize().height)));
        layoutDeep(row);
        return paint(row, width, Math.max(height, row.getHeight()));
    }

    private static BufferedImage paint(java.awt.Component component, int width, int height) {
        component.setSize(width, height);
        layoutDeep(component);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        component.paint(g);
        g.dispose();
        return image;
    }

    private static void layoutDeep(java.awt.Component component) {
        component.doLayout();
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                layoutDeep(child);
            }
        }
    }

    private static void write(String name, BufferedImage image) throws Exception {
        Path file = OUT.resolve(name);
        ImageIO.write(image, "png", file.toFile());
    }
}
