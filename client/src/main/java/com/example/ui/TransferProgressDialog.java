package com.example.ui;

import com.example.PeerFileRules;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;

/**
 * 傳檔用非模態進度視窗（上傳／下載皆可）。
 * <p>Mac Aqua 的進度條常幾乎看不見，改用 Basic UI 自繪。</p>
 */
public final class TransferProgressDialog {

    private static final Color TRACK = new Color(226, 232, 240);
    private static final Color FILL = new Color(37, 99, 235);
    private static final Color BORDER = new Color(148, 163, 184);

    private final JDialog dialog;
    private final JLabel statusLabel;
    private final JLabel detailLabel;
    private final JProgressBar bar;

    private TransferProgressDialog(Window owner, String title) {
        dialog = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        statusLabel = new JLabel("準備中…");
        statusLabel.setFont(UiFonts.chinesePlain(13));
        detailLabel = new JLabel(" ");
        detailLabel.setFont(UiFonts.chinesePlain(12));
        bar = createProgressBar();

        javax.swing.JPanel panel = new javax.swing.JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        panel.add(detailLabel, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
    }

    private static JProgressBar createProgressBar() {
        JProgressBar progress = new JProgressBar(0, 1000);
        progress.setUI(new VisibleProgressBarUI());
        progress.setIndeterminate(true);
        progress.setStringPainted(false);
        progress.setBorderPainted(true);
        progress.setOpaque(true);
        progress.setBackground(TRACK);
        progress.setForeground(FILL);
        progress.setBorder(BorderFactory.createLineBorder(BORDER));
        progress.setPreferredSize(new Dimension(380, 22));
        progress.setMinimumSize(new Dimension(280, 22));
        return progress;
    }

    public static TransferProgressDialog open(Component parent, String title) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (owner == null && parent instanceof Window) {
            owner = (Window) parent;
        }
        if (owner == null) {
            owner = new JFrame();
        }
        TransferProgressDialog dialog = new TransferProgressDialog(owner, title);
        dialog.dialog.setVisible(true);
        dialog.dialog.toFront();
        return dialog;
    }

    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            if (status != null && !status.isBlank()) {
                statusLabel.setText(status);
            }
        });
    }

    /** {@code total <= 0} 時改為不定進度。 */
    public void setProgress(long transferred, long total) {
        SwingUtilities.invokeLater(() -> {
            long done = Math.max(0L, transferred);
            if (total <= 0L) {
                bar.setIndeterminate(true);
                bar.setStringPainted(false);
                detailLabel.setText(done > 0 ? PeerFileRules.formatSize(done) : " ");
                return;
            }
            bar.setIndeterminate(false);
            int value = (int) Math.min(1000L, (done * 1000L) / Math.max(1L, total));
            bar.setValue(value);
            int percent = (int) Math.min(100L, (done * 100L) / Math.max(1L, total));
            bar.setString(percent + "%");
            bar.setStringPainted(true);
            detailLabel.setText(PeerFileRules.formatSize(done) + " / " + PeerFileRules.formatSize(total)
                    + "（" + percent + "%）");
        });
    }

    /** 準備階段：不定進度＋說明文字。 */
    public void setPreparing(String status) {
        SwingUtilities.invokeLater(() -> {
            if (status != null && !status.isBlank()) {
                statusLabel.setText(status);
            }
            bar.setIndeterminate(true);
            bar.setStringPainted(false);
            bar.setValue(0);
            detailLabel.setText("請稍候…");
        });
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            dialog.setVisible(false);
            dialog.dispose();
        });
    }

    /** 跨平台可見的進度條（避開 macOS Aqua 幾乎透明的預設樣式）。 */
    private static final class VisibleProgressBarUI extends BasicProgressBarUI {
        @Override
        protected void paintDeterminate(Graphics g, javax.swing.JComponent c) {
            Insets b = progressBar.getInsets();
            int width = progressBar.getWidth() - (b.right + b.left);
            int height = progressBar.getHeight() - (b.top + b.bottom);
            if (width <= 0 || height <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int x = b.left;
                int y = b.top;
                g2.setColor(TRACK);
                g2.fillRoundRect(x, y, width, height, 6, 6);
                int amount = getAmountFull(b, width, height);
                if (amount > 0) {
                    g2.setColor(FILL);
                    g2.fillRoundRect(x, y, Math.max(amount, Math.min(6, width)), height, 6, 6);
                }
                if (progressBar.isStringPainted()) {
                    paintString(g2, x, y, width, height, amount, b);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        protected void paintIndeterminate(Graphics g, javax.swing.JComponent c) {
            Insets b = progressBar.getInsets();
            int width = progressBar.getWidth() - (b.right + b.left);
            int height = progressBar.getHeight() - (b.top + b.bottom);
            if (width <= 0 || height <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int x = b.left;
                int y = b.top;
                g2.setColor(TRACK);
                g2.fillRoundRect(x, y, width, height, 6, 6);
                boxRect = getBox(boxRect);
                if (boxRect != null) {
                    g2.setColor(FILL);
                    g2.fillRoundRect(boxRect.x, boxRect.y, boxRect.width, boxRect.height, 6, 6);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        protected Color getSelectionForeground() {
            return Color.WHITE;
        }

        @Override
        protected Color getSelectionBackground() {
            return new Color(30, 41, 59);
        }
    }
}
