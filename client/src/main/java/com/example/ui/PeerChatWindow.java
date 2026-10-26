package com.example.ui;

import com.example.ChatProtocol;
import com.example.PeerFileRules;
import com.example.service.PeerAvatar;
import com.example.service.PeerChatStore;
import com.example.service.PeerImage;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 和單一同事的小聊天視窗：對話紀錄（只存記憶體）、輸入框（Enter 送出、Shift+Enter 換行）、
 * 附圖（Ctrl+V／⌘V 可貼多張）、戳一下、傳檔案。
 */
public class PeerChatWindow extends JFrame {

    /** 視窗對外的動作，由 App 實作。 */
    public interface Host {
        /** 目前能不能對這位同事傳送（雲端已啟用、不是後台）。 */
        boolean canSend(String peerId);

        void sendMessage(String peerId, String text, String imageEncoded, Consumer<Boolean> done);

        void poke(String peerId);

        /** @param owner 選檔、進度、提示對話框要掛在哪個視窗上（聊天視窗），才不會讓主視窗蓋住聊天視窗。 */
        void sendFiles(String peerId, Component owner);

        void chatClosed(PeerChatWindow window);
    }

    static final int MAX_TEXT_CHARS = 10_000;

    private final String peerId;
    private final Host host;
    private final Image appIcon;
    private final PeerChatTranscript transcript;
    private final JTextArea input;
    private final JButton sendButton;
    private final JButton pokeButton;
    private final JButton fileButton;
    private final JButton attachButton;
    private final JButton clearImageButton;
    private final JPanel imageThumbs;
    private final JLabel imageLabel;
    private final JLabel statusLabel;
    private final JLabel nameLabel;
    private final JLabel avatarLabel;
    private final JPanel imageRow;

    private final List<String> attachedImages = new ArrayList<>();
    private boolean interactionEnabled = true;
    private boolean fileBusy;
    private boolean sending;
    private boolean peerOnline;
    private boolean readOnly;
    private final boolean textOnly;

    public PeerChatWindow(String peerId, Host host, Image appIcon, List<PeerChatStore.Entry> history,
                          boolean readOnly) {
        this(peerId, host, appIcon, history, readOnly, false);
    }

    /**
     * @param textOnly 後台／群組對話只傳文字，不顯示圖片、戳一下、傳檔
     */
    public PeerChatWindow(String peerId, Host host, Image appIcon, List<PeerChatStore.Entry> history,
                          boolean readOnly, boolean textOnly) {
        super("聊天 · " + peerId);
        this.peerId = peerId;
        this.host = host;
        this.appIcon = appIcon;
        this.readOnly = readOnly;
        this.textOnly = textOnly;
        if (appIcon != null) {
            setIconImage(appIcon);
        }
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        java.awt.Font plain = UiFonts.chinesePlain(13);
        java.awt.Font bold = UiFonts.chineseBold(13);

        // 上方：頭像＋名稱＋狀態
        avatarLabel = new JLabel(PeerAvatar.previewIcon("", appIcon, 36));
        nameLabel = new JLabel(peerId);
        nameLabel.setFont(UiFonts.chineseBold(15));
        statusLabel = new JLabel(" ");
        statusLabel.setFont(UiFonts.chinesePlain(12));
        JPanel names = new JPanel();
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.setOpaque(false);
        names.add(nameLabel);
        names.add(statusLabel);
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBorder(new EmptyBorder(8, 12, 8, 12));
        header.add(avatarLabel, BorderLayout.WEST);
        header.add(names, BorderLayout.CENTER);

        // 中間：對話區
        transcript = new PeerChatTranscript(peerId, plain.getFamily(), 13);
        transcript.setImageViewer(image -> UiFonts.showCopyableMessageWithImage(
                this, "", "", "圖片 · " + peerId, JOptionPane.PLAIN_MESSAGE, null, image));
        for (PeerChatStore.Entry entry : history) {
            transcript.append(entry);
        }
        JScrollPane transcriptScroll = new JScrollPane(transcript,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        transcriptScroll.putClientProperty(CandyScrollBar.MARK, Boolean.TRUE);
        transcriptScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, CandyTheme.EDGE));

        // 下方：附圖列、輸入框、按鈕
        imageThumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        imageThumbs.setOpaque(false);
        imageLabel = new JLabel();
        imageLabel.setFont(plain);
        imageLabel.setForeground(NightTheme.MUTED);
        clearImageButton = new CandyButton("移除圖片", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        clearImageButton.setFont(plain);
        clearImageButton.setToolTipText("拿掉這則訊息已附的全部圖片");
        clearImageButton.addActionListener(e -> clearAttachedImages());
        imageRow = new JPanel(new BorderLayout(8, 0));
        imageRow.setBorder(new EmptyBorder(6, 12, 0, 12));
        imageRow.add(imageThumbs, BorderLayout.WEST);
        imageRow.add(imageLabel, BorderLayout.CENTER);
        imageRow.add(clearImageButton, BorderLayout.EAST);
        imageRow.setVisible(false);

        input = new JTextArea(3, 24) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                // 空白時顯示灰字提示，一開始就知道怎麼送出、怎麼換行
                if (getDocument().getLength() == 0) {
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(NightTheme.MUTED);
                    g2.setFont(getFont());
                    java.awt.Insets in = getInsets();
                    String[] hint = {
                            "Enter 送出，Shift+Enter 換行",
                            "Ctrl+V 或 ⌘V 貼上圖片，可多張"
                    };
                    int y = in.top + g2.getFontMetrics().getAscent();
                    int line = g2.getFontMetrics().getHeight();
                    for (String lineText : hint) {
                        g2.drawString(lineText, in.left, y);
                        y += line;
                    }
                    g2.dispose();
                }
            }
        };
        input.setFont(plain);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setToolTipText("Enter 送出，Shift+Enter 換行；Ctrl+V 或 ⌘V 可貼多張截圖（最多 "
                + MAX_TEXT_CHARS + " 字，一則最多 " + ChatProtocol.MAX_PEER_IMAGES + " 張）");
        bindInputKeys();
        CandyTextField.dressArea(input);
        JScrollPane inputScroll = new JScrollPane(input,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        inputScroll.putClientProperty(CandyScrollBar.MARK, Boolean.TRUE);
        inputScroll.setBorder(BorderFactory.createLineBorder(CandyTheme.EDGE));

        attachButton = new CandyButton("附加圖片", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        attachButton.setFont(plain);
        attachButton.setToolTipText("選一張或多張圖片，和文字一起傳給對方（會自動縮小壓縮）");
        attachButton.addActionListener(e -> chooseImage());
        pokeButton = new CandyButton("戳一下", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        pokeButton.setFont(plain);
        pokeButton.setToolTipText("對方桌面端會鳴叫、視窗晃動（約 15 秒內隨心跳送達）");
        pokeButton.addActionListener(e -> host.poke(peerId));
        fileButton = new CandyButton("傳檔案", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        fileButton.setFont(plain);
        fileButton.setToolTipText("傳檔案或整個資料夾；可一次多選，多個項目會壓成一個 ZIP");
        fileButton.addActionListener(e -> host.sendFiles(peerId, this));
        sendButton = new CandyButton("傳送", CandyButton.Variant.PRIMARY, CandyButton.Size.MEDIUM);
        sendButton.setFont(bold);
        sendButton.addActionListener(e -> send());

        // GridLayout：視窗再窄按鈕也只會一起變窄，不會像 FlowLayout 那樣把最後一顆換行後被裁掉
        JPanel tools = new JPanel(new GridLayout(1, 3, 6, 0));
        tools.setOpaque(false);
        tools.add(attachButton);
        tools.add(pokeButton);
        tools.add(fileButton);
        JPanel buttons = new JPanel(new BorderLayout(8, 0));
        buttons.setBorder(new EmptyBorder(6, 12, 10, 12));
        if (!textOnly) {
            buttons.add(tools, BorderLayout.CENTER);
        }
        buttons.add(sendButton, BorderLayout.EAST);

        JPanel inputBox = new JPanel(new BorderLayout());
        inputBox.setBorder(new EmptyBorder(8, 12, 0, 12));
        inputBox.add(inputScroll, BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        imageRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        inputBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!textOnly) {
            south.add(imageRow);
        }
        south.add(inputBox);
        south.add(buttons);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(transcriptScroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        setMinimumSize(new Dimension(500, 440));
        setSize(520, 600);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                host.chatClosed(PeerChatWindow.this);
            }
        });

        NightTheme.apply(getContentPane());
        clearAttachedImages();
        refreshEnabled();
        refreshStatus();
    }

    public String peerId() {
        return peerId;
    }

    /** 視窗標題與上方名稱。後台私訊固定叫「後台」，群組用群組名稱。 */
    public void setHeading(String heading) {
        String label = heading == null || heading.isBlank() ? peerId : heading.trim();
        setTitle("聊天 · " + label);
        nameLabel.setText(label);
    }

    /** 新訊息進來或自己送出後追加一筆。 */
    public void append(PeerChatStore.Entry entry) {
        transcript.append(entry);
    }

    public void setPeerOnline(boolean online) {
        this.peerOnline = online;
        refreshStatus();
    }

    public void setPeerAvatar(String encoded) {
        avatarLabel.setIcon(PeerAvatar.previewIcon(encoded, appIcon, 36));
    }

    /** 雲端未啟用時整個視窗不能送。 */
    public void setInteractionEnabled(boolean enabled) {
        this.interactionEnabled = enabled;
        refreshEnabled();
        refreshStatus();
    }

    public void setFileBusy(boolean busy) {
        this.fileBusy = busy;
        refreshEnabled();
    }

    /** 顯示在最上層並讓輸入框取得焦點。 */
    public void showAndFocus() {
        if (!isVisible()) {
            setVisible(true);
        }
        toFront();
        input.requestFocusInWindow();
    }

    private void refreshStatus() {
        if (textOnly) {
            if (!interactionEnabled) {
                statusLabel.setText("雲端未啟用，無法傳送");
                statusLabel.setForeground(new Color(255, 150, 150));
            } else {
                statusLabel.setText("文字對話，約 15 秒內送達");
                statusLabel.setForeground(NightTheme.MUTED);
            }
            return;
        }
        if (readOnly) {
            statusLabel.setText("後台訊息，無法回覆");
            statusLabel.setForeground(NightTheme.MUTED);
        } else if (!interactionEnabled) {
            statusLabel.setText("雲端未啟用，無法傳送");
            statusLabel.setForeground(new Color(255, 150, 150));
        } else if (peerOnline) {
            statusLabel.setText("在線");
            statusLabel.setForeground(new Color(140, 230, 170));
        } else {
            statusLabel.setText("離線（對方上線後才收得到）");
            statusLabel.setForeground(NightTheme.MUTED);
        }
    }

    private void refreshEnabled() {
        boolean canType = interactionEnabled && !readOnly;
        input.setEnabled(canType);
        sendButton.setEnabled(canType && !sending);
        boolean extras = canType && !textOnly;
        attachButton.setEnabled(extras);
        clearImageButton.setEnabled(extras && !attachedImages.isEmpty());
        pokeButton.setEnabled(extras);
        fileButton.setEnabled(extras && !fileBusy);
    }

    private void bindInputKeys() {
        javax.swing.InputMap inputMap = input.getInputMap(JComponent.WHEN_FOCUSED);
        javax.swing.ActionMap actionMap = input.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendChatMessage");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK),
                DefaultEditorKit.pasteAction);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK),
                DefaultEditorKit.pasteAction);
        actionMap.put("sendChatMessage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                send();
            }
        });
        // 剪貼簿裡是圖片（而且沒有文字）時，貼上就是附圖；否則維持一般貼上文字
        javax.swing.Action defaultPaste = actionMap.get(DefaultEditorKit.pasteAction);
        actionMap.put(DefaultEditorKit.pasteAction, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (clipboardHasImageOnly()) {
                    pasteImageFromClipboard();
                } else if (defaultPaste != null) {
                    defaultPaste.actionPerformed(e);
                }
            }
        });
    }

    private void send() {
        if (!interactionEnabled || readOnly || sending || !host.canSend(peerId)) {
            return;
        }
        String text = input.getText().trim();
        if (text.isEmpty() && attachedImages.isEmpty()) {
            return;
        }
        if (text.length() > MAX_TEXT_CHARS) {
            UiFonts.showWarning(this, "訊息長度不可超過 " + MAX_TEXT_CHARS + " 字（目前 "
                    + text.length() + " 字）。", "傳送訊息");
            return;
        }
        sending = true;
        refreshEnabled();
        String image = ChatProtocol.joinPeerImages(attachedImages);
        host.sendMessage(peerId, text, image, ok -> SwingUtilities.invokeLater(() -> {
            sending = false;
            if (ok) {
                input.setText("");
                clearAttachedImages();
            }
            refreshEnabled();
            input.requestFocusInWindow();
        }));
    }

    // ==================== 附圖 ====================

    private void chooseImage() {
        JFileChooser chooser = UiFonts.fileChooser();
        chooser.setDialogTitle("選擇要附加的圖片（可多選）");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "圖片檔 (JPG / PNG / GIF)", "jpg", "jpeg", "png", "gif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] picked = chooser.getSelectedFiles();
        if (picked == null || picked.length == 0) {
            if (chooser.getSelectedFile() == null) {
                return;
            }
            picked = new File[]{chooser.getSelectedFile()};
        }
        List<Path> files = new ArrayList<>();
        for (File file : picked) {
            if (file != null) {
                files.add(file.toPath());
            }
        }
        if (files.isEmpty()) {
            return;
        }
        new SwingWorker<List<EncodedPick>, Void>() {
            @Override
            protected List<EncodedPick> doInBackground() {
                List<EncodedPick> encoded = new ArrayList<>();
                for (Path file : files) {
                    String name = file.getFileName() == null ? "圖片" : file.getFileName().toString();
                    try {
                        encoded.add(new EncodedPick(PeerImage.encodeFile(file), name, null));
                    } catch (Exception ex) {
                        encoded.add(new EncodedPick(null, name, ex.getMessage()));
                    }
                }
                return encoded;
            }

            @Override
            protected void done() {
                try {
                    StringBuilder problems = new StringBuilder();
                    for (EncodedPick item : get()) {
                        if (item.error != null) {
                            problems.append(item.label).append("：").append(item.error).append('\n');
                            continue;
                        }
                        String reject = addAttachedImage(item.encoded);
                        if (reject != null) {
                            problems.append(item.label).append("：").append(reject).append('\n');
                        }
                    }
                    if (problems.length() > 0) {
                        UiFonts.showWarning(PeerChatWindow.this, problems.toString().trim(), "附加圖片");
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    UiFonts.showWarning(PeerChatWindow.this,
                            "無法使用這些圖片：" + cause.getMessage(), "附加圖片");
                }
            }
        }.execute();
    }

    private boolean clipboardHasImageOnly() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)
                    && !clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor);
        } catch (Exception ex) {
            return false;
        }
    }

    private void pasteImageFromClipboard() {
        Image pasted = null;
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                Object data = clipboard.getData(DataFlavor.imageFlavor);
                if (data instanceof Image) {
                    pasted = (Image) data;
                }
            }
        } catch (Exception ex) {
            // 讀不到就當作沒有圖片
        }
        if (pasted == null) {
            return;
        }
        Image source = pasted;
        encodeAsync(() -> PeerImage.encode(source), "剪貼簿圖片");
    }

    private interface ImageEncoder {
        String encode() throws Exception;
    }

    private void encodeAsync(ImageEncoder encoder, String label) {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return encoder.encode();
            }

            @Override
            protected void done() {
                try {
                    String reject = addAttachedImage(get());
                    if (reject != null) {
                        UiFonts.showWarning(PeerChatWindow.this, label + "：" + reject, "附加圖片");
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    UiFonts.showWarning(PeerChatWindow.this,
                            "無法使用這張圖片：" + cause.getMessage(), "附加圖片");
                }
            }
        }.execute();
    }

    /** 附上一張。成功回傳 null，放不下時回傳原因。 */
    private String addAttachedImage(String encoded) {
        String reject = ChatProtocol.rejectExtraPeerImage(attachedImages, encoded);
        if (reject != null) {
            return reject;
        }
        attachedImages.add(encoded);
        refreshAttachedImages();
        return null;
    }

    private void clearAttachedImages() {
        attachedImages.clear();
        refreshAttachedImages();
    }

    private void refreshAttachedImages() {
        imageThumbs.removeAll();
        long bytes = 0;
        for (String encoded : attachedImages) {
            bytes += encoded.length() * 3L / 4L;
            JLabel thumb = new JLabel();
            thumb.setHorizontalAlignment(SwingConstants.CENTER);
            thumb.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
            thumb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            thumb.setToolTipText("點一下移除此圖");
            BufferedImage preview = PeerImage.decodeThumbnail(encoded, 48);
            if (preview != null) {
                thumb.setIcon(new ImageIcon(preview));
            }
            thumb.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    attachedImages.remove(encoded);
                    refreshAttachedImages();
                }
            });
            imageThumbs.add(thumb);
        }
        boolean has = !attachedImages.isEmpty();
        String summary = has
                ? "已附 " + attachedImages.size() + " 張圖片（約 " + PeerFileRules.formatSize(bytes) + "）"
                : "";
        imageLabel.setText(summary);
        imageLabel.setToolTipText(has ? summary : null);
        imageRow.setVisible(has);
        clearImageButton.setEnabled(has && interactionEnabled && !readOnly && !textOnly);
        imageRow.revalidate();
        imageRow.repaint();
    }

    /** 測試與診斷用：目前附的圖片（多張以逗號串起），沒有是空字串。 */
    String attachedImage() {
        return ChatProtocol.joinPeerImages(attachedImages);
    }

    private static final class EncodedPick {
        final String encoded;
        final String label;
        final String error;

        EncodedPick(String encoded, String label, String error) {
            this.encoded = encoded;
            this.label = label;
            this.error = error;
        }
    }
}
