package com.example.ui;

import com.example.PeerGraffiti;
import com.example.service.PeerImage;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.BiConsumer;

/** 好友聊天 Win98 迷你塗鴉畫板。 */
public final class PeerGraffitiDialog extends JDialog {

    public static final class SendPayload {
        public final PeerGraffiti.Mode mode;
        public final String imageEncoded;

        SendPayload(PeerGraffiti.Mode mode, String imageEncoded) {
            this.mode = mode;
            this.imageEncoded = imageEncoded;
        }
    }

    private final PeerGraffitiBoard board;
    private JButton sendButton;
    private JRadioButton directMode;
    private JRadioButton cardMode;
    private volatile boolean sending;

    private PeerGraffitiDialog(Frame owner) {
        super(owner, "塗鴉畫板", true);
        java.awt.Font plain = UiFonts.chinesePlain(13);
        java.awt.Font bold = UiFonts.chineseBold(13);
        setLayout(new BorderLayout());
        JPanel title = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    ClassicBevel.fillTitleBar(g2, getWidth(), getHeight());
                } finally {
                    g2.dispose();
                }
            }
        };
        title.setPreferredSize(new Dimension(420, 22));
        title.setOpaque(false);
        JLabel titleText = new JLabel("  塗鴉畫板");
        titleText.setFont(UiFonts.chineseBold(12));
        titleText.setForeground(Color.WHITE);
        title.add(titleText, BorderLayout.WEST);
        add(title, BorderLayout.NORTH);

        board = new PeerGraffitiBoard();
        JPanel canvasWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        canvasWrap.setBorder(new EmptyBorder(8, 12, 0, 12));
        canvasWrap.setOpaque(false);
        canvasWrap.add(board);
        add(canvasWrap, BorderLayout.CENTER);

        JPanel tools = buildTools(plain);
        JPanel sendRow = buildSendRow(plain, bold);
        JPanel south = new JPanel();
        south.setLayout(new javax.swing.BoxLayout(south, javax.swing.BoxLayout.Y_AXIS));
        south.setBorder(new EmptyBorder(8, 12, 10, 12));
        south.setOpaque(false);
        tools.setAlignmentX(Component.LEFT_ALIGNMENT);
        sendRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(tools);
        south.add(sendRow);
        add(south, BorderLayout.SOUTH);

        refreshSendEnabled();
        pack();
        setResizable(false);
        NightTheme.apply(getContentPane());
        setLocationRelativeTo(owner);
    }

    private JPanel buildTools(java.awt.Font plain) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        row.setOpaque(false);
        JButton[] colorButtons = new JButton[PeerGraffitiBoard.PALETTE.length];
        JButton[] eraserRef = new JButton[1];
        for (int i = 0; i < PeerGraffitiBoard.PALETTE.length; i++) {
            final Color c = PeerGraffitiBoard.PALETTE[i];
            JButton btn = swatchButton(c);
            int index = i;
            btn.addActionListener(e -> {
                board.setEraser(false);
                board.setBrushColor(c);
                highlightSwatches(colorButtons, index);
                if (eraserRef[0] != null) {
                    highlightEraser(eraserRef[0], false);
                }
            });
            colorButtons[i] = btn;
            row.add(btn);
        }
        row.add(new JLabel(" "));
        JButton thin = toolButton("細", plain);
        JButton mid = toolButton("中", plain);
        JButton thick = toolButton("粗", plain);
        thin.addActionListener(e -> board.setBrushSizeIndex(0));
        mid.addActionListener(e -> board.setBrushSizeIndex(1));
        thick.addActionListener(e -> board.setBrushSizeIndex(2));
        row.add(thin);
        row.add(mid);
        row.add(thick);
        JButton undo = toolButton("復原", plain);
        undo.addActionListener(e -> {
            board.undo();
            refreshSendEnabled();
        });
        JButton clear = toolButton("清空", plain);
        clear.addActionListener(e -> {
            board.clearCanvas();
            refreshSendEnabled();
        });
        row.add(undo);
        row.add(clear);
        JButton eraserBtn = toolButton("橡皮", plain);
        eraserRef[0] = eraserBtn;
        eraserBtn.addActionListener(e -> {
            board.setEraser(true);
            highlightEraser(eraserBtn, true);
        });
        row.add(eraserBtn);
        highlightSwatches(colorButtons, 0);
        return row;
    }

    private JPanel buildSendRow(java.awt.Font plain, java.awt.Font bold) {
        JPanel row = new JPanel(new BorderLayout(8, 6));
        row.setOpaque(false);
        JPanel modes = new JPanel(new GridLayout(1, 2, 8, 0));
        modes.setOpaque(false);
        directMode = new JRadioButton("直接顯示", true);
        cardMode = new JRadioButton("塗鴉卡片", false);
        directMode.setFont(plain);
        cardMode.setFont(plain);
        directMode.setOpaque(false);
        cardMode.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        group.add(directMode);
        group.add(cardMode);
        modes.add(directMode);
        modes.add(cardMode);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        CandyButton cancel = new CandyButton("取消", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        cancel.setFont(plain);
        cancel.addActionListener(e -> dispose());
        sendButton = new CandyButton("送出", CandyButton.Variant.PRIMARY, CandyButton.Size.SMALL);
        sendButton.setFont(bold);
        sendButton.addActionListener(e -> encodeAndSend());
        buttons.add(cancel);
        buttons.add(sendButton);

        JLabel hint = new JLabel("空白畫布不能送出");
        hint.setFont(plain.deriveFont(11f));
        hint.setForeground(NightTheme.MUTED);
        row.add(modes, BorderLayout.WEST);
        row.add(hint, BorderLayout.CENTER);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    private void encodeAndSend() {
        if (sending || !board.hasInk()) {
            if (!board.hasInk()) {
                UiFonts.showWarning(this, "請先在畫布上塗鴉。", "塗鴉畫板");
            }
            return;
        }
        sending = true;
        sendButton.setEnabled(false);
        PeerGraffiti.Mode mode = cardMode.isSelected() ? PeerGraffiti.Mode.CARD : PeerGraffiti.Mode.DIRECT;
        BufferedImage image = board.renderImage();
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return PeerImage.encode(image);
            }

            @Override
            protected void done() {
                sending = false;
                try {
                    String encoded = get();
                    result = new SendPayload(mode, encoded);
                    dispose();
                } catch (Exception ex) {
                    refreshSendEnabled();
                    String msg = ex.getCause() instanceof IOException
                            ? ex.getCause().getMessage() : "圖片編碼失敗";
                    UiFonts.showWarning(PeerGraffitiDialog.this, msg, "塗鴉畫板");
                }
            }
        };
        worker.execute();
    }

    private SendPayload result;

    private void refreshSendEnabled() {
        if (sendButton != null) {
            sendButton.setEnabled(board.hasInk() && !sending);
        }
    }

    JButton swatchButton(Color color) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    if (!isOpaque()) {
                        setOpaque(true);
                    }
                    g.setColor(color);
                    g.fillRect(4, 4, getWidth() - 8, getHeight() - 8);
                    if (Boolean.TRUE.equals(getClientProperty("graffiti.selected"))) {
                        g.setColor(ClassicBevel.DARK);
                        g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                    }
                    ClassicBevel.paintSunkenEdges(g, 0, 0, getWidth(), getHeight());
                } finally {
                    g.dispose();
                }
            }
        };
        Dimension d = new Dimension(26, 22);
        btn.setPreferredSize(d);
        btn.setMinimumSize(d);
        btn.setMaximumSize(d);
        btn.setBorder(BorderFactory.createEmptyBorder());
        btn.setFocusPainted(false);
        return btn;
    }

    private static JButton toolButton(String text, java.awt.Font font) {
        CandyButton btn = new CandyButton(text, CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        btn.setFont(font);
        return btn;
    }

    private static void highlightSwatches(JButton[] buttons, int selected) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].putClientProperty("graffiti.selected", i == selected);
            buttons[i].repaint();
        }
    }

    private static void highlightEraser(JButton eraser, boolean on) {
        eraser.putClientProperty("graffiti.selected", on);
        eraser.repaint();
    }

    /** 阻塞到使用者送出或取消；送出時 callback 會在對話框關閉後於 EDT 被呼叫。 */
    public static void showModal(Component owner, BiConsumer<PeerGraffiti.Mode, String> onSend) {
        Frame frame = owner instanceof Frame ? (Frame) owner : (Frame) SwingUtilities.getWindowAncestor(owner);
        PeerGraffitiDialog dialog = new PeerGraffitiDialog(frame);
        dialog.setVisible(true);
        if (dialog.result != null && onSend != null) {
            onSend.accept(dialog.result.mode, dialog.result.imageEncoded);
        }
    }

    /** 測試用：組好工具列與畫布，不彈窗。 */
    static PeerGraffitiBoard exposeBoardForTest(PeerGraffitiDialog dialog) {
        return dialog.board;
    }
}