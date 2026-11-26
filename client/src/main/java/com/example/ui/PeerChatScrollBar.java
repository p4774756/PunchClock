package com.example.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** 聊天對話區專用：細、淡、圓角捲軸；不影響其他 {@link CandyScrollBar#MARK} 頁面。 */
public final class PeerChatScrollBar {

    public static final String MARK = "candy.peerChatScroll";
    private static final int WIDTH = 10;
    private static final Color TRACK = new Color(0xD8, 0xD8, 0xD8);
    private static final Color THUMB_IDLE = new Color(0xC4, 0xC4, 0xC4);
    private static final Color THUMB_HOT = new Color(0xA8, 0xA8, 0xA8);

    private PeerChatScrollBar() {
    }

    public static void install(JScrollPane scroll) {
        if (scroll == null) {
            return;
        }
        scroll.putClientProperty(MARK, Boolean.TRUE);
        installBar(scroll.getVerticalScrollBar(), true);
        JScrollBar horizontal = scroll.getHorizontalScrollBar();
        if (horizontal != null) {
            installBar(horizontal, false);
        }
    }

    private static void installBar(JScrollBar bar, boolean vertical) {
        if (bar == null) {
            return;
        }
        bar.setUI(new ThinBarUI());
        bar.setOpaque(false);
        bar.setPreferredSize(vertical
                ? new Dimension(WIDTH, 0)
                : new Dimension(0, WIDTH));
        if (bar.getUnitIncrement() <= 1) {
            bar.setUnitIncrement(18);
        }
    }

    static final class ThinBarUI extends BasicScrollBarUI {
        private boolean armed;

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return stub();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return stub();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle track) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(TRACK);
                g2.fillRoundRect(track.x, track.y, track.width, track.height, 6, 6);
            } finally {
                g2.dispose();
            }
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumb) {
            if (thumb.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hot = armed
                        || Boolean.TRUE.equals(c.getClientProperty("peer.scroll.hot"));
                g2.setColor(hot ? THUMB_HOT : THUMB_IDLE);
                int pad = 1;
                g2.fillRoundRect(thumb.x + pad, thumb.y + pad,
                        Math.max(1, thumb.width - pad * 2), Math.max(1, thumb.height - pad * 2), 5, 5);
            } finally {
                g2.dispose();
            }
        }

        @Override
        protected void installListeners() {
            super.installListeners();
            scrollbar.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    scrollbar.putClientProperty("peer.scroll.hot", Boolean.TRUE);
                    scrollbar.repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    scrollbar.putClientProperty("peer.scroll.hot", Boolean.FALSE);
                    scrollbar.repaint();
                }

                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    armed = true;
                    scrollbar.repaint();
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    armed = false;
                    scrollbar.repaint();
                }
            });
        }

        private static JButton stub() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setFocusable(false);
            button.setOpaque(false);
            return button;
        }
    }
}