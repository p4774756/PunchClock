package com.example.ui;

import com.example.PeerFileRules;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;

/**
 * 傳檔用非模態進度視窗（上傳／下載皆可）。
 */
public final class TransferProgressDialog {

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
        bar = new JProgressBar(0, 1000);
        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(360, 18));

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
                detailLabel.setText(PeerFileRules.formatSize(done));
                return;
            }
            bar.setIndeterminate(false);
            int value = (int) Math.min(1000L, (done * 1000L) / Math.max(1L, total));
            bar.setValue(value);
            detailLabel.setText(PeerFileRules.formatSize(done) + " / " + PeerFileRules.formatSize(total));
        });
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            dialog.setVisible(false);
            dialog.dispose();
        });
    }
}
