package com.example.ui;

import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PeerChatRelayCardTest {

    @Test
    public void inboundOfferButtonsStayFullyVisibleInsideTranscript() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatTranscript transcript = new PeerChatTranscript("言承旭", "SansSerif", 13);
            transcript.setSize(360, 520);
            PeerChatRelayCard card = transcript.addRelayCard();
            card.showInboundOffer(
                    "言承旭",
                    "eapp-backend-prod-deploy-notes.pdf",
                    "67.1 KB",
                    false,
                    180_000L,
                    () -> {
                    },
                    () -> {
                    });
            layoutAt(transcript, 360, 520);
            assertOfferButtonsVisible(card, transcript);

            layoutAt(transcript, 720, 640);
            assertOfferButtonsVisible(card, transcript);

            card.setOfferClosed("已關閉");
        });
    }

    @Test
    public void transferCancelButtonsStayFullyVisibleInsideTranscript() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatTranscript transcript = new PeerChatTranscript("alice", "SansSerif", 13);
            PeerChatRelayCard outbound = transcript.addRelayCard();
            outbound.showOutboundTransfer("notes.zip", "12 KB");
            layoutAt(transcript, 400, 520);
            assertButtonFullyInside(outbound, "取消");

            PeerChatRelayCard inbound = transcript.addRelayCard();
            inbound.showInboundTransfer("notes.zip", "12 KB");
            layoutAt(transcript, 400, 520);
            assertButtonFullyInside(inbound, "取消");
        });
    }

    @Test
    public void inboundOfferLabelsAlignLeft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatRelayCard card = new PeerChatRelayCard(new Font("SansSerif", Font.PLAIN, 13));
            card.showInboundOffer("言承旭", "a.pdf", "1 KB", false, 60_000L, () -> {
            }, () -> {
            });
            card.setSize(card.getPreferredSize());
            layoutTree(card);
            for (JLabel label : findLabels(card)) {
                assertEquals("標籤應靠左：" + label.getText(),
                        Component.LEFT_ALIGNMENT, label.getAlignmentX(), 0.0001f);
            }
            card.setOfferClosed("已關閉");
        });
    }

    private static void assertOfferButtonsVisible(PeerChatRelayCard card, PeerChatTranscript transcript) {
        assertTrue("卡片高度應能容納按鈕列 preferred height=" + card.getPreferredSize()
                        + " actual=" + card.getSize()
                        + " parent=" + (card.getParent() == null ? "null" : card.getParent().getSize()),
                card.getHeight() >= card.getPreferredSize().height);
        assertTrue("對話列高度不應小於卡片",
                card.getParent() != null
                        && card.getParent().getHeight() >= card.getPreferredSize().height);
        assertEquals(Component.LEFT_ALIGNMENT, card.getAlignmentX(), 0.0001f);

        JButton accept = findButton(card, "接收");
        JButton decline = findButton(card, "拒收");
        assertNotNull("應顯示「接收」按鈕", accept);
        assertNotNull("應顯示「拒收」按鈕", decline);
        assertButtonFullyInside(card, accept);
        assertButtonFullyInside(card, decline);
        assertFalse("倒數標籤不應被裁切", findLabels(card).isEmpty());
        // Keep transcript referenced so the layout tree stays rooted during assertions.
        assertTrue(transcript.isAncestorOf(card));
    }

    private static void assertButtonFullyInside(PeerChatRelayCard card, String text) {
        JButton button = findButton(card, text);
        assertNotNull("應顯示「" + text + "」按鈕", button);
        assertButtonFullyInside(card, button);
    }

    private static void assertButtonFullyInside(PeerChatRelayCard card, JButton button) {
        assertTrue("「" + button.getText() + "」應可見", button.isVisible() && button.isShowing() || button.isVisible());
        assertTrue("「" + button.getText() + "」應有寬高：" + button.getSize(),
                button.getWidth() > 0 && button.getHeight() > 0);
        Rectangle inCard = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), card);
        Rectangle cardBounds = new Rectangle(0, 0, card.getWidth(), card.getHeight());
        assertTrue("「" + button.getText() + "」應完整落在卡片內：button=" + inCard
                        + " card=" + cardBounds + " pref=" + card.getPreferredSize(),
                cardBounds.contains(inCard));
    }

    private static void layoutAt(PeerChatTranscript transcript, int width, int height) {
        transcript.setSize(width, height);
        transcript.invalidate();
        transcript.validate();
        layoutTree(transcript);
    }

    private static void layoutTree(Component component) {
        component.doLayout();
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static JButton findButton(Component root, String text) {
        if (root instanceof JButton) {
            JButton button = (JButton) root;
            if (text.equals(button.getText())) {
                return button;
            }
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                JButton found = findButton(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<JLabel> findLabels(Component root) {
        List<JLabel> labels = new ArrayList<>();
        collectLabels(root, labels);
        return labels;
    }

    private static void collectLabels(Component root, List<JLabel> labels) {
        if (root instanceof JLabel) {
            labels.add((JLabel) root);
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                collectLabels(child, labels);
            }
        }
    }
}
