package com.example.ui;

import com.example.PeerFileRules;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天區的直傳進度／收件／已儲存卡。
 */
public final class PeerChatRelayCard extends JPanel {

    private static final Color CARD_FACE = ClassicBevel.FACE;
    private static final Color CARD_EDGE = ClassicBevel.DARK;

    private final JLabel titleLabel;
    private final JLabel detailLabel;
    private final JLabel statusLabel;
    private final JProgressBar bar;
    private final JPanel actionRow;
    private final JPanel stack;
    private final Font plain;
    private final Font small;
    private final AtomicBoolean finished = new AtomicBoolean();
    private Timer countdownTimer;
    private Runnable cancelAction;
    private volatile boolean cancelling;

    public PeerChatRelayCard(Font plainFont) {
        this.plain = plainFont;
        this.small = plainFont.deriveFont((float) Math.max(11, plainFont.getSize() - 2));
        setLayout(new BorderLayout(0, 6));
        setOpaque(true);
        setBackground(CARD_FACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_EDGE),
                new EmptyBorder(8, 10, 8, 10)));
        putClientProperty(CandyPanel.MARK, Boolean.TRUE);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel north = new JPanel(new BorderLayout(0, 2));
        north.setOpaque(false);
        titleLabel = label("", plain.deriveFont(Font.BOLD), CandyTheme.INK);
        detailLabel = label(" ", small, CandyTheme.MUTED);
        north.add(titleLabel, BorderLayout.NORTH);
        north.add(detailLabel, BorderLayout.SOUTH);

        bar = TransferProgressDialog.createRelayProgressBar();
        bar.setPreferredSize(new Dimension(280, 36));
        bar.setMinimumSize(new Dimension(180, 36));

        statusLabel = label(" ", small, CandyTheme.MUTED);

        actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        actionRow.setOpaque(false);

        stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.add(north);
        stack.add(bar);
        stack.add(statusLabel);
        stack.add(actionRow);
        alignStack();
        add(stack, BorderLayout.CENTER);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    public void showOutboundTransfer(String filename, String sizeLabel) {
        titleLabel.setText("直傳  " + filename + (sizeLabel.isEmpty() ? "" : " · " + sizeLabel));
        detailLabel.setText(" ");
        statusLabel.setText("準備中…");
        bar.setIndeterminate(true);
        bar.setStringPainted(false);
        actionRow.removeAll();
        JButton cancel = new CandyButton("取消", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        cancel.setFont(small);
        cancel.addActionListener(e -> requestCancel());
        actionRow.add(cancel);
        relayout();
    }

    public void showInboundTransfer(String filename, String sizeLabel) {
        titleLabel.setText("接收  " + filename + (sizeLabel.isEmpty() ? "" : " · " + sizeLabel));
        detailLabel.setText(" ");
        statusLabel.setText("準備中…");
        bar.setIndeterminate(true);
        bar.setStringPainted(false);
        actionRow.removeAll();
        JButton cancel = new CandyButton("取消", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        cancel.setFont(small);
        cancel.addActionListener(e -> requestCancel());
        actionRow.add(cancel);
        relayout();
    }

    public void showInboundOffer(String fromId, String filename, String sizeLabel, boolean folder,
                                 long timeoutMs, Runnable onAccept, Runnable onDecline) {
        titleLabel.setText("同事直傳  " + filename);
        detailLabel.setText("來自【" + fromId + "】" + (sizeLabel.isEmpty() ? "" : " · " + sizeLabel)
                + (folder ? "（資料夾 ZIP）" : ""));
        statusLabel.setText("請在 " + PeerFileRules.RELAY_ACCEPT_TIMEOUT_LABEL + " 內決定");
        bar.setVisible(false);
        actionRow.removeAll();
        long deadline = System.currentTimeMillis() + Math.max(1L, timeoutMs);
        JButton accept = new CandyButton("接收", CandyButton.Variant.PRIMARY, CandyButton.Size.SMALL);
        JButton decline = new CandyButton("拒收", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        accept.setFont(small);
        decline.setFont(small);
        accept.addActionListener(finishOnce(onAccept));
        decline.addActionListener(finishOnce(onDecline));
        actionRow.add(accept);
        actionRow.add(decline);
        JLabel countdown = label(PeerFileRules.RELAY_ACCEPT_TIMEOUT_LABEL, small, CandyTheme.LAVENDER);
        actionRow.add(countdown);
        stopCountdown();
        countdownTimer = new Timer(500, e -> {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                stopCountdown();
                if (finished.compareAndSet(false, true)) {
                    setOfferClosed("已逾時（視為拒收）");
                    if (onDecline != null) {
                        onDecline.run();
                    }
                }
                return;
            }
            int sec = (int) ((left + 999) / 1000);
            int min = sec / 60;
            int rem = sec % 60;
            countdown.setText(min + ":" + String.format("%02d", rem));
        });
        countdownTimer.start();
        relayout();
    }

    public void setOnCancel(Runnable action) {
        cancelAction = action;
    }

    public void setPreparing(String status) {
        runLater(() -> {
            if (!cancelling && status != null && !status.isBlank()) {
                statusLabel.setText(status);
            }
            bar.setIndeterminate(true);
            bar.setStringPainted(false);
        });
    }

    public void setProgress(long transferred, long total, boolean outbound) {
        runLater(() -> {
            long done = Math.max(0L, transferred);
            if (total <= 0L) {
                bar.setIndeterminate(true);
                bar.setStringPainted(false);
                statusLabel.setText(done > 0 ? (outbound ? "傳送中…" : "接收中…") : statusLabel.getText());
                return;
            }
            bar.setIndeterminate(false);
            int value = (int) Math.min(1000L, (done * 1000L) / Math.max(1L, total));
            bar.setValue(value);
            int percent = (int) Math.min(100L, (done * 100L) / Math.max(1L, total));
            bar.setStringPainted(true);
            bar.setString(percent + "%");
            statusLabel.setText((outbound ? "傳送中 " : "接收中 ") + percent + "%");
        });
    }

    public void setStatus(String status) {
        runLater(() -> {
            if (!cancelling && status != null && !status.isBlank()) {
                statusLabel.setText(status);
            }
        });
    }

    public void finishSuccess(boolean outbound, String filename) {
        runLater(() -> {
            finished.set(true);
            stopCountdown();
            bar.setVisible(false);
            actionRow.removeAll();
            statusLabel.setText(outbound ? "傳送完成" : "接收完成");
            titleLabel.setText((outbound ? "已直傳  " : "已接收  ") + filename);
            relayout();
        });
    }

    public void finishFailure(String message) {
        runLater(() -> {
            finished.set(true);
            stopCountdown();
            bar.setVisible(false);
            actionRow.removeAll();
            statusLabel.setText(message == null || message.isBlank() ? "直傳失敗" : message);
            relayout();
        });
    }

    public void finishCancelled() {
        runLater(() -> {
            finished.set(true);
            stopCountdown();
            bar.setVisible(false);
            actionRow.removeAll();
            statusLabel.setText("已取消");
            relayout();
        });
    }

    public void showSaved(Path file) {
        runLater(() -> {
            finished.set(true);
            stopCountdown();
            bar.setVisible(false);
            actionRow.removeAll();
            titleLabel.setText("已儲存  " + (file == null ? "" : file.getFileName().toString()));
            detailLabel.setText(file == null ? "" : file.toAbsolutePath().toString());
            statusLabel.setText(" ");
            if (file != null) {
                JButton open = new CandyButton("開啟檔案", CandyButton.Variant.PRIMARY, CandyButton.Size.SMALL);
                JButton folder = new CandyButton("打開資料夾", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
                open.setFont(small);
                folder.setFont(small);
                open.addActionListener(e -> openFile(file));
                folder.addActionListener(e -> openFolder(file));
                actionRow.add(open);
                actionRow.add(folder);
            }
            relayout();
        });
    }

    public void setOfferClosed(String message) {
        runLater(() -> {
            finished.set(true);
            stopCountdown();
            bar.setVisible(false);
            actionRow.removeAll();
            statusLabel.setText(message);
            relayout();
        });
    }

    private void requestCancel() {
        Runnable action = cancelAction;
        if (action == null || cancelling || finished.get()) {
            return;
        }
        cancelling = true;
        statusLabel.setText("正在取消…");
        action.run();
    }

    private ActionListener finishOnce(Runnable action) {
        return e -> {
            if (finished.compareAndSet(false, true)) {
                stopCountdown();
                actionRow.removeAll();
                bar.setVisible(false);
                if (action != null) {
                    action.run();
                }
                relayout();
            }
        };
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private static void openFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            Desktop.getDesktop().open(file.toFile());
        } catch (IOException ignored) {
        }
    }

    private static void openFolder(Path file) {
        if (file == null) {
            return;
        }
        Path dir = Files.isDirectory(file) ? file : file.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try {
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException ignored) {
        }
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void alignStack() {
        for (Component child : stack.getComponents()) {
            if (child instanceof JComponent) {
                ((JComponent) child).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
    }

    private void relayout() {
        alignStack();
        revalidate();
        repaint();
        Container parent = getParent();
        if (parent instanceof JComponent) {
            parent.invalidate();
            parent.revalidate();
            parent.repaint();
        }
    }

    private void runLater(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}