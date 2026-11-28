package com.example.ui;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 聊天視窗「附加／塗鴉／傳送」按鈕排版離屏預覽。 */
public class Issue777PeerChatLayoutTest {

    private static final Path OUT_DIR = Paths.get("target", "issue-777-verify");

    @Test
    public void composeButtonsFitAtDefaultAndMinimumSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = PeerChatWindow.createHeadlessPreviewRoot("alice");
            try {
                paintAt(root, PeerChatWindow.PREVIEW_DEFAULT_SIZE);
                assertComposeButtons(root);
                paintAt(root, PeerChatWindow.PREVIEW_MINIMUM_SIZE);
                assertComposeButtons(root);
            } finally {
                root.removeAll();
            }
        });
    }

    @Test
    public void writeChatWindowLayoutPreviews() throws Exception {
        Files.createDirectories(OUT_DIR);
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = PeerChatWindow.createHeadlessPreviewRoot("alice");
            try {
                Dimension def = PeerChatWindow.PREVIEW_DEFAULT_SIZE;
                writePreview(root, def.width, def.height, OUT_DIR.resolve("chat-window-default.png"));
                Dimension min = PeerChatWindow.PREVIEW_MINIMUM_SIZE;
                writePreview(root, min.width, min.height, OUT_DIR.resolve("chat-window-minimum.png"));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                root.removeAll();
            }
        });
    }

    private static void paintAt(JPanel root, Dimension size) {
        WindowChrome.renderOffscreen(root, size.width, size.height, new Color(28, 20, 48));
    }

    private static void assertComposeButtons(JPanel root) {
        List<JButton> buttons = composeActionButtons(root);
        assertEquals(3, buttons.size());
        assertEquals("附加", buttons.get(0).getText());
        assertEquals("塗鴉", buttons.get(1).getText());
        assertEquals("傳送", buttons.get(2).getText());
        assertTrue(buttons.get(2) instanceof CandyButton);
        assertEquals(CandyButton.Variant.PRIMARY, ((CandyButton) buttons.get(2)).variant());

        Component common = buttons.get(0).getParent();
        List<Rectangle> rects = new ArrayList<>();
        for (JButton button : buttons) {
            Dimension pref = button.getPreferredSize();
            Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), common);
            assertTrue("按鈕高度不足：" + button.getText(),
                    bounds.height >= pref.height - 1);
            assertTrue("按鈕寬度不足：" + button.getText(),
                    bounds.width >= pref.width - 1);
            rects.add(bounds);
        }
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                Rectangle overlap = rects.get(i).intersection(rects.get(j));
                assertFalse("按鈕重疊：" + buttons.get(i).getText() + "／" + buttons.get(j).getText(),
                        overlap.width > 0 && overlap.height > 0);
            }
        }
    }

    private static List<JButton> composeActionButtons(Container root) {
        List<JButton> found = new ArrayList<>();
        for (Component component : flatten(root)) {
            if (component instanceof JButton) {
                String text = ((JButton) component).getText();
                if ("附加".equals(text) || "塗鴉".equals(text) || "傳送".equals(text)) {
                    found.add((JButton) component);
                }
            }
        }
        return found;
    }

    private static void writePreview(JPanel root, int w, int h, Path out) throws Exception {
        BufferedImage image = WindowChrome.renderOffscreen(root, w, h, new Color(28, 20, 48));
        assertTrue(ImageIO.write(image, "png", out.toFile()));
        assertTrue(Files.size(out) > 400L);
        assertComposeButtons(root);
    }

    private static List<Component> flatten(Component root) {
        List<Component> list = new ArrayList<>();
        list.add(root);
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                list.addAll(flatten(child));
            }
        }
        return list;
    }
}