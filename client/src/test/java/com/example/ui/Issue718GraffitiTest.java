package com.example.ui;

import com.example.PeerGraffiti;
import com.example.service.PeerChatStore;
import com.example.service.PeerImage;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 塗鴉畫板與聊天縮圖／卡片的離屏 PNG 預覽。 */
public class Issue718GraffitiTest {

    private static final Path OUT_DIR = Paths.get("target", "issue-718-verify");

    @Test
    public void miniBoardRendersFramedPreview() throws Exception {
        Files.createDirectories(OUT_DIR);
        SwingUtilities.invokeAndWait(() -> {
            try {
                PeerGraffitiBoard board = new PeerGraffitiBoard();
                board.setBrushColor(Color.BLACK);
                board.setBrushSizeIndex(1);
                simulateStroke(board, 40, 40, 200, 120);
                assertTrue(board.hasInk());
                BufferedImage preview = board.renderFramedPreview();
                Path out = OUT_DIR.resolve("graffiti-mini-board.png");
                assertTrue(ImageIO.write(preview, "png", out.toFile()));
                assertTrue(Files.size(out) > 400L);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    public void transcriptDirectAndCardStates() throws Exception {
        Files.createDirectories(OUT_DIR);
        SwingUtilities.invokeAndWait(() -> {
            try {
                String encoded = PeerImage.encode(sampleDoodle());
                PeerChatStore store = new PeerChatStore();
                PeerChatTranscript direct = new PeerChatTranscript("alice", "SansSerif", 13);
                direct.setSize(480, 360);
                direct.append(store.addMessage("alice", false, PeerGraffiti.TAG_DIRECT, encoded, 1_000L));
                direct.doLayout();
                BufferedImage directShot = paintTranscript(direct);
                assertTrue(ImageIO.write(directShot, "png", OUT_DIR.resolve("chat-graffiti-direct.png").toFile()));

                PeerChatTranscript card = new PeerChatTranscript("alice", "SansSerif", 13);
                card.setSize(480, 360);
                card.append(store.addMessage("alice", false, PeerGraffiti.TAG_CARD, encoded, 2_000L));
                card.doLayout();
                BufferedImage sealed = paintTranscript(card);
                assertTrue(sealed.getWidth() > 0);
                assertTrue(ImageIO.write(sealed, "png", OUT_DIR.resolve("chat-graffiti-card-sealed.png").toFile()));
                assertTrue(treeText(card).contains(PeerGraffiti.cardLabel(false)));

                openFirstGraffitiCard(card);
                card.doLayout();
                BufferedImage opened = paintTranscript(card);
                assertTrue(ImageIO.write(opened, "png", OUT_DIR.resolve("chat-graffiti-card-open.png").toFile()));
                assertTrue("展開後應顯示塗鴉縮圖", hasThumb(card));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

  private static void simulateStroke(PeerGraffitiBoard board, int x0, int y0, int x1, int y1) {
        java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(
                board, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x0, y0, 1, false);
        java.awt.event.MouseEvent drag = new java.awt.event.MouseEvent(
                board, java.awt.event.MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, x1, y1, 1, false);
        java.awt.event.MouseEvent release = new java.awt.event.MouseEvent(
                board, java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x1, y1, 1, false);
        for (java.awt.event.MouseListener listener : board.getMouseListeners()) {
            listener.mousePressed(press);
        }
        for (java.awt.event.MouseMotionListener listener : board.getMouseMotionListeners()) {
            listener.mouseDragged(drag);
        }
        for (java.awt.event.MouseListener listener : board.getMouseListeners()) {
            listener.mouseReleased(release);
        }
        board.repaint();
    }

    private static BufferedImage sampleDoodle() {
        BufferedImage img = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 120, 80);
            g.setColor(Color.RED);
            g.drawLine(10, 10, 100, 60);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static BufferedImage paintTranscript(PeerChatTranscript transcript) {
        int w = Math.max(transcript.getWidth(), 480);
        int h = Math.max(transcript.getHeight(), 360);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            transcript.setSize(w, h);
            transcript.doLayout();
            transcript.paint(g);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static void openFirstGraffitiCard(PeerChatTranscript transcript) {
        java.awt.Component found = findClickable(transcript, PeerGraffiti.cardLabel(false));
        assertNotNull(found);
        for (java.awt.event.MouseListener listener : found.getMouseListeners()) {
            listener.mouseClicked(new java.awt.event.MouseEvent(
                    found, java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 4, 4, 1, false));
        }
    }

    private static java.awt.Component findClickable(java.awt.Component root, String text) {
        if (root instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel) root).getText())) {
            return root;
        }
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                java.awt.Component hit = findClickable(child, text);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static boolean hasThumb(java.awt.Component component) {
        if (component instanceof javax.swing.JLabel && ((javax.swing.JLabel) component).getIcon() != null) {
            return true;
        }
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                if (hasThumb(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String treeText(java.awt.Component component) {
        StringBuilder sb = new StringBuilder();
        collectText(component, sb);
        return sb.toString();
    }

    private static void collectText(java.awt.Component component, StringBuilder sb) {
        if (component instanceof javax.swing.JLabel) {
            sb.append(((javax.swing.JLabel) component).getText());
        }
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                collectText(child, sb);
            }
        }
    }
}