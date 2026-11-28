package com.example.ui;

import com.example.PeerFileRules;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 傳檔用非模態進度視窗（上傳／下載皆可）。
 * <p>Mac Aqua 的進度條常幾乎看不見，改用 Basic UI 自繪。</p>
 */
public final class TransferProgressDialog {

    private static final Color TRACK = PetWalkerProgressBarUI.TRACK;
    private static final Color FILL = PetWalkerProgressBarUI.FILL;
    private static final Color BORDER = new Color(0x92, 0x75, 0xBD);

    /** 目前開著的傳檔／更新進度視窗；自動重啟更新前用來確認沒有傳檔進行中。 */
    private static final java.util.List<TransferProgressDialog> OPEN =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private final JDialog dialog;
    private final JLabel statusLabel;
    private final JLabel detailLabel;
    private final JProgressBar bar;
    private final JButton cancelButton;
    private volatile Runnable cancelAction;
    private volatile boolean cancelling;

    private TransferProgressDialog(Window owner, String title) {
        dialog = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        statusLabel = new JLabel("準備中…");
        statusLabel.setFont(UiFonts.chinesePlain(13));
        detailLabel = new JLabel(" ");
        detailLabel.setFont(UiFonts.chinesePlain(12));
        bar = createProgressBar();
        cancelButton = new CandyButton("取消", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        cancelButton.setFont(UiFonts.chinesePlain(12));
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> requestCancel());
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestCancel();
            }
        });

        javax.swing.JPanel bottom = new javax.swing.JPanel(new BorderLayout(12, 0));
        bottom.add(detailLabel, BorderLayout.CENTER);
        bottom.add(cancelButton, BorderLayout.EAST);

        javax.swing.JPanel panel = new javax.swing.JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        NightTheme.apply(panel);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
    }

    /** 聊天傳檔卡與此視窗共用粉紅進度條樣式。 */
    public static JProgressBar createRelayProgressBar() {
        return createProgressBar();
    }

    private static JProgressBar createProgressBar() {
        JProgressBar progress = new JProgressBar(0, 1000);
        progress.setUI(new PetWalkerProgressBarUI());
        progress.setIndeterminate(true);
        progress.setStringPainted(false);
        progress.setBorderPainted(true);
        progress.setOpaque(true);
        progress.setBackground(TRACK);
        progress.setForeground(FILL);
        progress.setBorder(BorderFactory.createLineBorder(BORDER));
        progress.setPreferredSize(new Dimension(380, 40));
        progress.setMinimumSize(new Dimension(280, 36));
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
        OPEN.add(dialog);
        dialog.dialog.setVisible(true);
        dialog.dialog.toFront();
        return dialog;
    }

    /** 設定按「取消」或關閉視窗時要做的事；未設定則取消鈕停用。 */
    public void setOnCancel(Runnable action) {
        cancelAction = action;
        SwingUtilities.invokeLater(() -> cancelButton.setEnabled(action != null && !cancelling));
    }

    private void requestCancel() {
        Runnable action = cancelAction;
        if (action == null || cancelling) {
            return;
        }
        cancelling = true;
        cancelButton.setEnabled(false);
        statusLabel.setText("正在取消…");
        action.run();
    }

    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            if (!cancelling && status != null && !status.isBlank()) {
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
            if (!cancelling && status != null && !status.isBlank()) {
                statusLabel.setText(status);
            }
            bar.setIndeterminate(true);
            bar.setStringPainted(false);
            bar.setValue(0);
            detailLabel.setText("請稍候…");
        });
    }

    /** 是否有進度視窗正顯示中（傳送、接收、下載任一種）。 */
    public static boolean isAnyActive() {
        for (TransferProgressDialog open : OPEN) {
            if (open.dialog.isShowing()) {
                return true;
            }
        }
        return false;
    }

    public void close() {
        OPEN.remove(this);
        SwingUtilities.invokeLater(() -> {
            dialog.setVisible(false);
            dialog.dispose();
        });
    }

}
