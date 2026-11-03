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
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 和單一同事的小聊天視窗：對話紀錄（只存記憶體）、輸入框（Enter 送出、Shift+Enter 換行）、
 * 附圖（Ctrl+V／⌘V 可貼多張）、附加檔案（先列出，按傳送才送給這個人）、戳一下。
 */
public class PeerChatWindow extends JFrame {

    /** 視窗對外的動作，由 App 實作。 */
    public interface Host {
        /** 目前能不能對這位同事傳送（雲端已啟用、不是後台）。 */
        boolean canSend(String peerId);

        void sendMessage(String peerId, String text, String imageEncoded, Consumer<Boolean> done);

        void poke(String peerId);

        /**
         * 聊天視窗裡已排好的檔案，只送給這一位，不開多人傳送視窗。
         *
         * @return 已經開始傳送。沒送出時呼叫端保留清單。
         */
        boolean sendQueuedFiles(String peerId, Component owner, List<Path> files);

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
    private final JButton clearFilesButton;
    private final JPanel imageThumbs;
    private final JPanel fileItems;
    private final JScrollPane fileScroll;
    private final JLabel fileSummary;
    private final JLabel imageLabel;
    private final JLabel statusLabel;
    private final JLabel nameLabel;
    private final JLabel avatarLabel;
    private final JPanel imageRow;
    private final JPanel fileRow;

    private final List<String> attachedImages = new ArrayList<>();
    private final List<Path> pendingFiles = new ArrayList<>();
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
        CandyCheckBox developerMode = new CandyCheckBox("開發者", false);
        developerMode.setFont(plain);
        developerMode.setToolTipText("程式碼與錯誤訊息改用等寬字，並保留縮排、Tab 與換行");
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBorder(new EmptyBorder(8, 12, 8, 12));
        header.add(avatarLabel, BorderLayout.WEST);
        header.add(names, BorderLayout.CENTER);
        header.add(developerMode, BorderLayout.EAST);

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

        fileItems = new JPanel();
        fileItems.setLayout(new BoxLayout(fileItems, BoxLayout.Y_AXIS));
        fileItems.setOpaque(true);
        fileItems.setBackground(NightTheme.FIELD);
        fileScroll = new JScrollPane(fileItems,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        fileScroll.putClientProperty(CandyScrollBar.MARK, Boolean.TRUE);
        fileScroll.setBorder(BorderFactory.createLineBorder(CandyTheme.EDGE));
        fileScroll.setOpaque(true);
        fileScroll.setBackground(NightTheme.FIELD);
        fileScroll.getViewport().setOpaque(true);
        fileScroll.getViewport().setBackground(NightTheme.FIELD);
        fileSummary = new JLabel();
        fileSummary.setFont(plain);
        fileSummary.setForeground(NightTheme.MUTED);
        clearFilesButton = new CandyButton("移除檔案", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        clearFilesButton.setFont(plain);
        clearFilesButton.setToolTipText("拿掉這批還沒送出的檔案");
        clearFilesButton.addActionListener(e -> clearPendingFiles());
        JPanel fileCenter = new JPanel(new BorderLayout(0, 4));
        fileCenter.setOpaque(false);
        fileCenter.add(fileScroll, BorderLayout.CENTER);
        fileCenter.add(fileSummary, BorderLayout.SOUTH);
        fileRow = new JPanel(new BorderLayout(8, 0));
        fileRow.setOpaque(false);
        fileRow.setBorder(new EmptyBorder(6, 12, 0, 12));
        fileRow.add(fileCenter, BorderLayout.CENTER);
        fileRow.add(clearFilesButton, BorderLayout.EAST);
        fileRow.setVisible(false);

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
                            "Ctrl+V 或 ⌘V 貼上圖片，可多張",
                            "拖曳檔案會先列在這裡"
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
        developerMode.addItemListener(e -> {
            boolean on = developerMode.isSelected();
            transcript.setDeveloperMode(on);
            input.setFont(on ? new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13) : plain);
        });
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setToolTipText("Enter 送出，Shift+Enter 換行；Ctrl+V 或 ⌘V 可貼多張截圖（最多 "
                + MAX_TEXT_CHARS + " 字，一則最多 " + ChatProtocol.MAX_PEER_IMAGES
                + " 張）。拖曳檔案會先列在這裡，按傳送才送出");
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
        pokeButton.setToolTipText("對方會響一聲，並彈出聊天視窗晃一下（約 15 秒內隨心跳送達）");
        pokeButton.addActionListener(e -> host.poke(peerId));
        fileButton = new CandyButton("附加檔案", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        fileButton.setFont(plain);
        fileButton.setToolTipText("選檔案或資料夾（可多選）。先列在這裡，按傳送才送給這個人；多個項目會打包成一個 ZIP");
        fileButton.addActionListener(e -> chooseQueuedFiles());
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
        fileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        imageRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        inputBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!textOnly) {
            south.add(fileRow);
            south.add(imageRow);
        }
        south.add(inputBox);
        south.add(buttons);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(transcriptScroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        installFileDrop(root);

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
        refreshPendingFiles();
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
        clearFilesButton.setEnabled(extras && !pendingFiles.isEmpty());
        pokeButton.setEnabled(extras);
        fileButton.setEnabled(extras && !fileBusy);
    }

    private void installFileDrop(JComponent target) {
        if (target == null || Boolean.TRUE.equals(target.getClientProperty("chat.fileDrop"))) {
            return;
        }
        target.putClientProperty("chat.fileDrop", Boolean.TRUE);
        new DropTarget(target, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent event) {
                try {
                    if (textOnly || readOnly || !interactionEnabled
                            || !event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        event.rejectDrop();
                        return;
                    }
                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    Object data = event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    List<Path> paths = new ArrayList<>();
                    if (data instanceof List) {
                        for (Object item : (List<?>) data) {
                            if (item instanceof File) {
                                paths.add(((File) item).toPath());
                            }
                        }
                    }
                    event.dropComplete(!paths.isEmpty());
                    if (!paths.isEmpty()) {
                        SwingUtilities.invokeLater(() -> addPendingFiles(paths));
                    }
                } catch (Exception ex) {
                    event.dropComplete(false);
                }
            }
        }, true);
        target.addContainerListener(new java.awt.event.ContainerAdapter() {
            @Override
            public void componentAdded(java.awt.event.ContainerEvent event) {
                if (event.getChild() instanceof JComponent) {
                    installFileDrop((JComponent) event.getChild());
                }
            }
        });
        for (Component child : target.getComponents()) {
            if (child instanceof JComponent) {
                installFileDrop((JComponent) child);
            }
        }
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

    void send() {
        if (!interactionEnabled || readOnly || sending || !host.canSend(peerId)) {
            return;
        }
        String text = input.getText().trim();
        boolean hasMessage = !text.isEmpty() || !attachedImages.isEmpty();
        boolean hasFiles = !pendingFiles.isEmpty();
        if (!hasMessage && !hasFiles) {
            return;
        }
        if (text.length() > MAX_TEXT_CHARS) {
            UiFonts.showWarning(this, "訊息長度不可超過 " + MAX_TEXT_CHARS + " 字（目前 "
                    + text.length() + " 字）。", "傳送訊息");
            return;
        }
        if (hasFiles) {
            List<Path> files = new ArrayList<>(pendingFiles);
            if (host.sendQueuedFiles(peerId, this, files)) {
                clearPendingFiles();
            }
        }
        if (!hasMessage) {
            input.requestFocusInWindow();
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

    // ==================== 附加檔案 ====================

    private void chooseQueuedFiles() {
        JFileChooser chooser = UiFonts.fileChooser();
        chooser.setDialogTitle("選擇要傳送的檔案或資料夾（可多選）");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        chooser.setAcceptAllFileFilterUsed(true);
        if (UiFonts.showOpenDialog(chooser, this) != JFileChooser.APPROVE_OPTION) {
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
        addPendingFiles(files);
    }

    /** 拖曳或多選進來的檔案先排進清單。不合規則的項目會跳過並說明原因。 */
    void addPendingFiles(List<Path> incoming) {
        if (textOnly || readOnly || !interactionEnabled || incoming == null || incoming.isEmpty()) {
            return;
        }
        StringBuilder problems = new StringBuilder();
        for (Path raw : incoming) {
            Path path = raw == null ? null : raw.toAbsolutePath().normalize();
            String label = raw == null || raw.getFileName() == null ? "檔案" : raw.getFileName().toString();
            String reject = FileTransferDialog.rejection(path);
            if (reject != null) {
                problems.append(label).append("：").append(reject).append('\n');
                continue;
            }
            if (containsPending(path)) {
                continue;
            }
            pendingFiles.add(path);
        }
        refreshPendingFiles();
        refreshEnabled();
        if (problems.length() > 0) {
            UiFonts.showWarning(this, problems.toString().trim(), "附加檔案");
        }
    }

    private boolean containsPending(Path path) {
        for (Path existing : pendingFiles) {
            if (existing.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void clearPendingFiles() {
        pendingFiles.clear();
        refreshPendingFiles();
        refreshEnabled();
    }

    private void refreshPendingFiles() {
        fileItems.removeAll();
        for (Path path : pendingFiles) {
            fileItems.add(pendingFileLine(path));
        }
        int lines = Math.min(Math.max(pendingFiles.size(), 1), 4);
        fileScroll.setPreferredSize(new Dimension(280, lines * 26 + 8));
        boolean many = pendingFiles.size() > 1;
        fileSummary.setText(many ? "這 " + pendingFiles.size() + " 個項目會打包成一個 ZIP 後傳送" : "");
        fileSummary.setVisible(many);
        fileRow.setVisible(!pendingFiles.isEmpty());
        sendButton.setText(pendingFiles.isEmpty() ? "傳送" : "傳送 (" + pendingFiles.size() + ")");
        fileRow.revalidate();
        revalidate();
        repaint();
    }

    private JComponent pendingFileLine(Path path) {
        String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        JLabel line = new JLabel(name + "    " + fileMeta(path));
        line.setFont(UiFonts.chinesePlain(13));
        line.setForeground(NightTheme.INK);
        line.setBorder(new EmptyBorder(3, 8, 3, 8));
        line.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        line.setToolTipText(path + "（點一下移除）");
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                pendingFiles.remove(path);
                refreshPendingFiles();
                refreshEnabled();
            }
        });
        return line;
    }

    private static String fileMeta(Path path) {
        try {
            if (Files.isDirectory(path)) {
                return "資料夾";
            }
            return PeerFileRules.formatSize(Files.size(path));
        } catch (Exception ex) {
            return "檔案";
        }
    }

    /** 測試用：目前排好、還沒送出的檔案。 */
    List<Path> pendingFiles() {
        return new ArrayList<>(pendingFiles);
    }

    // ==================== 附圖 ====================

    private void chooseImage() {
        JFileChooser chooser = UiFonts.fileChooser();
        chooser.setDialogTitle("選擇要附加的圖片（可多選）");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "圖片檔 (JPG / PNG / GIF)", "jpg", "jpeg", "png", "gif"));
        if (UiFonts.showOpenDialog(chooser, this) != JFileChooser.APPROVE_OPTION) {
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
