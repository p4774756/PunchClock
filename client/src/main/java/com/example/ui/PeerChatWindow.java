package com.example.ui;

import com.example.ChatProtocol;
import com.example.PeerFileRules;
import com.example.PeerGraffiti;
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
import javax.swing.text.DefaultEditorKit;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
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
import java.text.Normalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 和單一同事的小聊天視窗：對話紀錄（只存記憶體）、輸入框（Enter 送出、Shift+Enter 換行）、
 * 附圖（Ctrl+V／⌘V 可貼多張）、附加檔案（先列出，按傳送才送給這個人）。
 */
public class PeerChatWindow extends JFrame {

    static final Dimension PREVIEW_DEFAULT_SIZE = new Dimension(520, 600);
    static final Dimension PREVIEW_MINIMUM_SIZE = new Dimension(500, 440);

    /** 視窗對外的動作，由 App 實作。 */
    public interface Host {
        /** 目前能不能對這位同事傳送（雲端已啟用、不是後台）。 */
        boolean canSend(String peerId);

        void sendMessage(String peerId, String text, String imageEncoded, Consumer<Boolean> done);

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
    private final JButton attachButton;
    private final JButton graffitiButton;
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
    /** 清單重畫後，舊的縮圖工作就不該再貼上。 */
    private int filePreviewGeneration;
    private boolean interactionEnabled = true;
    private boolean fileBusy;
    private boolean sending;
    private boolean peerOnline;
    private boolean readOnly;
    private boolean closed;
    private final boolean textOnly;

    public PeerChatWindow(String peerId, Host host, Image appIcon, List<PeerChatStore.Entry> history,
                          boolean readOnly) {
        this(peerId, host, appIcon, history, readOnly, false);
    }

    /**
     * @param textOnly 後台／群組對話只傳文字，不顯示圖片、傳檔
     */
    public PeerChatWindow(String peerId, Host host, Image appIcon, List<PeerChatStore.Entry> history,
                          boolean readOnly, boolean textOnly) {
        super("聊天 · " + peerId);
        this.peerId = peerId;
        this.host = host;
        this.appIcon = appIcon;
        this.readOnly = readOnly;
        this.textOnly = textOnly;
        java.awt.Font plain = UiFonts.chinesePlain(13);
        java.awt.Font bold = UiFonts.chineseBold(13);
        WindowChrome.install(this, getTitle(),
                textOnly ? appIcon : PeerChatTitleIcon.create(), bold, plain);
        JPanel chromeBody = WindowChrome.bodyOf(this);
        if (appIcon != null) {
            java.util.List<Image> icons = AppIconImages.loadAll();
            if (!icons.isEmpty()) {
                setIconImages(icons);
            } else {
                setIconImage(appIcon);
            }
        }
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

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
        PeerChatScrollBar.install(transcriptScroll);
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

        ComposeAreaParts composeParts = ComposeAreaBuilder.create(plain, bold, textOnly);
        this.input = composeParts.input;
        this.attachButton = composeParts.attachButton;
        this.graffitiButton = composeParts.graffitiButton;
        this.sendButton = composeParts.sendButton;
        composeParts.wire(this);
        JPanel compose = composeParts.compose;
        JLabel composeHint = composeParts.composeHint;

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        fileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        imageRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        compose.setAlignmentX(Component.LEFT_ALIGNMENT);
        composeHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!textOnly) {
            south.add(fileRow);
            south.add(imageRow);
        }
        south.add(compose);
        south.add(composeHint);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(transcriptScroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        chromeBody.add(root, BorderLayout.CENTER);
        installFileDrop(root);
        WindowChrome.setStatusStripVisible(this, false);

        setMinimumSize(PREVIEW_MINIMUM_SIZE);
        setSize(PREVIEW_DEFAULT_SIZE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                closed = true;
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
        WindowChrome.setWindowTitle(this, "聊天 · " + label);
        nameLabel.setText(label);
    }

    /** 新訊息進來或自己送出後追加一筆。 */
    public void append(PeerChatStore.Entry entry) {
        transcript.append(entry);
    }

    /** 在對話區加一張直傳卡（進度／收件／已儲存）。 */
    public PeerChatRelayCard addRelayCard() {
        return transcript.addRelayCard();
    }

    public PeerChatTranscript transcript() {
        return transcript;
    }

    public void setPeerOnline(boolean online) {
        this.peerOnline = online;
        refreshStatus();
        refreshEnabled();
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
        // 等這一下點擊結束再開。視窗若蓋在游標上，同一個點擊會打到新視窗；
        // 主視窗又會立刻搶回前景，看起來就像彈開又關掉。
        SwingUtilities.invokeLater(() -> {
            if (closed) {
                return;
            }
            WindowShake.bringToFront(this);
            if (input.isEnabled() && input.isShowing()) {
                input.requestFocusInWindow();
            }
        });
    }

    private void refreshStatus() {
        if (textOnly) {
            if (!interactionEnabled) {
                statusLabel.setText("雲端未啟用，無法傳送");
                statusLabel.setForeground(new Color(255, 150, 150));
            } else {
                statusLabel.setText("文字對話，約 30 秒內送達");
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
            statusLabel.setForeground(new Color(0x1B, 0x7F, 0x3A));
        } else {
            statusLabel.setText("離線，上線後才能傳");
            statusLabel.setForeground(new Color(255, 150, 150));
        }
    }

    /** 同事要在線才送得出文字、圖片和檔案。後台文字對話不看在線狀態。 */
    private boolean canCompose() {
        return interactionEnabled && !readOnly && (textOnly || peerOnline);
    }

    private void refreshEnabled() {
        boolean canType = canCompose();
        // 離線也留著輸入框，視窗才拿得到焦點；送出仍要對方在線。
        input.setEnabled(interactionEnabled && !readOnly);
        sendButton.setEnabled(canType && !sending);
        boolean extras = canType && !textOnly;
        boolean canClear = interactionEnabled && !readOnly && !textOnly;
        attachButton.setEnabled(extras && !fileBusy);
        graffitiButton.setEnabled(extras && !sending);
        clearImageButton.setEnabled(canClear && !attachedImages.isEmpty());
        clearFilesButton.setEnabled(canClear && !pendingFiles.isEmpty());
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
                    if (!canCompose()
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
                        SwingUtilities.invokeLater(() -> routeIncomingPaths(paths));
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
                if (clipboardHasImageOnly() && canCompose()) {
                    pasteImageFromClipboard();
                } else if (defaultPaste != null) {
                    defaultPaste.actionPerformed(e);
                }
            }
        });
    }

    private void openGraffitiBoard() {
        if (!canCompose() || sending || !host.canSend(peerId)) {
            return;
        }
        PeerGraffitiDialog.showModal(this, (mode, imageEncoded) -> {
            if (imageEncoded == null || imageEncoded.isBlank()) {
                return;
            }
            String text = PeerGraffiti.messageText(mode);
            sending = true;
            refreshEnabled();
            host.sendMessage(peerId, text, imageEncoded, ok -> SwingUtilities.invokeLater(() -> {
                sending = false;
                refreshEnabled();
                input.requestFocusInWindow();
            }));
        });
    }

    void send() {
        if (!canCompose() || sending || !host.canSend(peerId)) {
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

    // ==================== 附加（與拖曳同一套規則） ====================

    private void chooseAttach() {
        if (!canCompose()) {
            return;
        }
        JFileChooser chooser = UiFonts.fileChooser();
        chooser.setDialogTitle("選擇要附加的檔案、資料夾或圖片（可多選）");
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
        routeIncomingPaths(files);
    }

    /**
     * 附加按鈕與拖曳共用：全部是圖片檔時走訊息附圖，否則全部進待傳清單。
     */
    void routeIncomingPaths(List<Path> incoming) {
        if (!canCompose() || incoming == null || incoming.isEmpty()) {
            return;
        }
        if (allMessageImages(incoming)) {
            attachImagesFromFiles(incoming);
        } else {
            addPendingFiles(incoming);
        }
    }

    private static boolean allMessageImages(List<Path> paths) {
        for (Path raw : paths) {
            if (raw == null) {
                return false;
            }
            Path path = raw.toAbsolutePath().normalize();
            try {
                if (!Files.isRegularFile(path) || !isQueuedImage(path)) {
                    return false;
                }
            } catch (Exception ex) {
                return false;
            }
        }
        return true;
    }

    /** 拖曳或多選進來的檔案先排進清單。不合規則的項目會跳過並說明原因。 */
    void addPendingFiles(List<Path> incoming) {
        if (!canCompose() || incoming == null || incoming.isEmpty()) {
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
                problems.append(label).append("：已經在清單裡\n");
                continue;
            }
            String clash = pendingNameClash(path);
            if (clash != null) {
                problems.append(label).append("：").append(clash).append('\n');
                continue;
            }
            pendingFiles.add(path);
        }
        refreshPendingFiles();
        refreshEnabled();
        if (problems.length() > 0 && isVisible()) {
            // 選檔面板關掉的當下立刻開對話框，macOS 有時會把提示吃掉，所以排到下一個事件。
            String text = problems.toString().trim();
            SwingUtilities.invokeLater(() -> {
                if (isDisplayable()) {
                    toFront();
                    UiFonts.showWarning(this, text, "附加");
                }
            });
        }
    }

    /** 不同路徑但檔名相同（不分大小寫）時擋下，避免打包進同一個 ZIP 時被改名。 */
    private String pendingNameClash(Path path) {
        String incoming = itemName(path);
        if (incoming.isEmpty()) {
            return null;
        }
        for (Path existing : pendingFiles) {
            if (incoming.equalsIgnoreCase(itemName(existing))) {
                return "已經有同名項目";
            }
        }
        return null;
    }

    private static String itemName(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        return Normalizer.normalize(path.getFileName().toString(), Normalizer.Form.NFC);
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
        int generation = ++filePreviewGeneration;
        fileItems.removeAll();
        int content = 8;
        int visible = 8;
        int shown = 0;
        for (Path path : pendingFiles) {
            boolean image = isQueuedImage(path);
            fileItems.add(pendingFileLine(path, image, generation));
            int rowHeight = image ? 44 : 32;
            content += rowHeight;
            if (shown < 4) {
                visible += rowHeight;
                shown++;
            }
        }
        if (pendingFiles.isEmpty()) {
            visible = 34;
        }
        fileScroll.setPreferredSize(new Dimension(280, Math.min(content, visible)));
        boolean zipPack = needsZipPack();
        fileSummary.setText(zipPack
                ? (pendingFiles.size() > 1
                ? "這 " + pendingFiles.size() + " 個項目會打包成一個 ZIP 後傳送"
                : "此資料夾會打包成一個 ZIP 後傳送")
                : "");
        fileSummary.setVisible(zipPack);
        fileRow.setVisible(!pendingFiles.isEmpty());
        sendButton.setText(pendingFiles.isEmpty() ? "傳送" : "傳送 (" + pendingFiles.size() + ")");
        fileRow.revalidate();
        revalidate();
        repaint();
    }

    private JComponent pendingFileLine(Path path, boolean image, int generation) {
        String name = itemName(path);
        if (name.isEmpty()) {
            name = path.toString();
        }
        int rowHeight = image ? 44 : 32;
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(2, 6, 2, 4));
        row.setPreferredSize(new Dimension(280, rowHeight));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
        row.setToolTipText(path.toString());
        JLabel line = new JLabel(name + "    " + fileMeta(path));
        line.setFont(UiFonts.chinesePlain(13));
        line.setForeground(NightTheme.INK);
        if (image) {
            JLabel thumb = new JLabel();
            thumb.setPreferredSize(new Dimension(32, 32));
            thumb.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel thumbWrap = new JPanel(new GridBagLayout());
            thumbWrap.setOpaque(false);
            thumbWrap.add(thumb);
            row.add(thumbWrap, BorderLayout.WEST);
            loadFileThumb(path, thumb, generation);
        }
        row.add(line, BorderLayout.CENTER);
        JButton remove = new PendingRemoveButton();
        remove.addActionListener(e -> {
            pendingFiles.remove(path);
            refreshPendingFiles();
            refreshEnabled();
        });
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        east.add(remove);
        row.add(east, BorderLayout.EAST);
        return row;
    }

    /** 清單每一列右側的小叉，只拿掉那一個項目。 */
    private static final class PendingRemoveButton extends JButton {
        private PendingRemoveButton() {
            setToolTipText("移除此項");
            Dimension box = new Dimension(22, 22);
            setPreferredSize(box);
            setMinimumSize(box);
            setMaximumSize(box);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            int s = 16;
            int x = (getWidth() - s) / 2;
            int y = (getHeight() - s) / 2;
            if (getModel().isRollover() || getModel().isPressed()) {
                g2.setColor(getModel().isPressed() ? CandyTheme.PINK_DEEP : new Color(0x6A, 0x48, 0x88));
                g2.fillOval(x, y, s, s);
            }
            g2.setColor(CandyTheme.INK);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int pad = 4;
            g2.drawLine(x + pad, y + pad, x + s - pad, y + s - pad);
            g2.drawLine(x + s - pad, y + pad, x + pad, y + s - pad);
            g2.dispose();
        }
    }

    private void loadFileThumb(Path path, JLabel thumb, int generation) {
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return PeerImage.fileThumbnail(path, 32);
            }

            @Override
            protected void done() {
                if (generation != filePreviewGeneration) {
                    return;
                }
                BufferedImage image = null;
                try {
                    image = get();
                } catch (Exception ignored) {
                    image = null;
                }
                if (image != null) {
                    thumb.setBorder(BorderFactory.createLineBorder(CandyTheme.EDGE));
                    thumb.setIcon(new ImageIcon(image));
                    return;
                }
                thumb.setVisible(false);
                if (thumb.getParent() != null) {
                    thumb.getParent().setVisible(false);
                }
            }
        }.execute();
    }

    private boolean needsZipPack() {
        if (pendingFiles.size() > 1) {
            return true;
        }
        if (pendingFiles.size() != 1) {
            return false;
        }
        try {
            return Files.isDirectory(pendingFiles.get(0));
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isQueuedImage(Path path) {
        String name = itemName(path).toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp");
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

    private void attachImagesFromFiles(List<Path> files) {
        if (files == null || files.isEmpty()) {
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
                        UiFonts.showWarning(PeerChatWindow.this, problems.toString().trim(), "附加");
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    UiFonts.showWarning(PeerChatWindow.this,
                            "無法使用這些圖片：" + cause.getMessage(), "附加");
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
                        UiFonts.showWarning(PeerChatWindow.this, label + "：" + reject, "附加");
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    UiFonts.showWarning(PeerChatWindow.this,
                            "無法使用這張圖片：" + cause.getMessage(), "附加");
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

    /** 離屏驗收：等同聊天視窗內容區，不需要頂層視窗。 */
    static JPanel createHeadlessPreviewRoot(String peerId) {
        java.awt.Font plain = UiFonts.chinesePlain(13);
        java.awt.Font bold = UiFonts.chineseBold(13);
        JLabel avatarLabel = new JLabel(PeerAvatar.previewIcon("", null, 36));
        JLabel nameLabel = new JLabel(peerId);
        nameLabel.setFont(UiFonts.chineseBold(15));
        JLabel statusLabel = new JLabel("在線");
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

        PeerChatTranscript transcript = new PeerChatTranscript(peerId, plain.getFamily(), 13);
        JScrollPane transcriptScroll = new JScrollPane(transcript,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        PeerChatScrollBar.install(transcriptScroll);
        transcriptScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, CandyTheme.EDGE));

        ComposeAreaParts composeParts = ComposeAreaBuilder.create(plain, bold, false);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        composeParts.compose.setAlignmentX(Component.LEFT_ALIGNMENT);
        composeParts.composeHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(composeParts.compose);
        south.add(composeParts.composeHint);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(transcriptScroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        NightTheme.apply(root);
        return root;
    }

    private static final class ComposeAreaParts {
        final JTextArea input;
        final CandyButton attachButton;
        final CandyButton graffitiButton;
        final CandyButton sendButton;
        final JPanel compose;
        final JLabel composeHint;

        ComposeAreaParts(JTextArea input, CandyButton attachButton, CandyButton graffitiButton,
                         CandyButton sendButton, JPanel compose, JLabel composeHint) {
            this.input = input;
            this.attachButton = attachButton;
            this.graffitiButton = graffitiButton;
            this.sendButton = sendButton;
            this.compose = compose;
            this.composeHint = composeHint;
        }

        void wire(PeerChatWindow window) {
            attachButton.addActionListener(e -> window.chooseAttach());
            graffitiButton.addActionListener(e -> window.openGraffitiBoard());
            sendButton.addActionListener(e -> window.send());
            window.bindInputKeys();
        }
    }

    private static final class ComposeAreaBuilder {
        static ComposeAreaParts create(java.awt.Font plain, java.awt.Font bold, boolean textOnly) {
            JTextArea input;
            input = new JTextArea(2, 24) {
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    super.paintComponent(g);
                    if (getDocument().getLength() == 0) {
                        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g2.setColor(NightTheme.MUTED);
                        g2.setFont(getFont());
                        java.awt.Insets in = getInsets();
                        int y = in.top + g2.getFontMetrics().getAscent();
                        g2.drawString("輸入訊息…", in.left, y);
                        g2.dispose();
                    }
                }
            };
            input.setFont(plain);
            input.setLineWrap(true);
            input.setWrapStyleWord(true);
            input.setToolTipText("Enter 送出，Shift+Enter 換行；Ctrl+V 或 ⌘V 可貼多張截圖（最多 "
                    + MAX_TEXT_CHARS + " 字，一則最多 " + ChatProtocol.MAX_PEER_IMAGES
                    + " 張）。拖曳檔案規則與「附加」相同");
            CandyTextField.dressArea(input);
            input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void resize() {
                    SwingUtilities.invokeLater(() -> {
                        int rows = Math.min(8, Math.max(2, input.getLineCount()));
                        if (input.getRows() != rows) {
                            input.setRows(rows);
                            input.revalidate();
                        }
                    });
                }

                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    resize();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    resize();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    resize();
                }
            });
            JScrollPane inputScroll = new JScrollPane(input,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            PeerChatScrollBar.install(inputScroll);
            inputScroll.setBorder(BorderFactory.createLineBorder(CandyTheme.EDGE));

            CandyButton attachButton = new CandyButton("附加", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
            attachButton.setFont(plain);
            attachButton.setToolTipText("選檔案、資料夾或圖片（可多選）。純圖片會跟文字一起送出；其餘先列在這裡，按傳送才送出");
            CandyButton graffitiButton = new CandyButton("塗鴉", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
            graffitiButton.setFont(plain);
            graffitiButton.setToolTipText("開啟迷你畫板，手繪塗鴉直接送出或包成卡片");
            CandyButton sendButton = new CandyButton("傳送", CandyButton.Variant.PRIMARY, CandyButton.Size.SMALL);
            sendButton.setFont(bold);
            JPanel composeActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            composeActions.setOpaque(false);
            if (!textOnly) {
                composeActions.add(attachButton);
                composeActions.add(graffitiButton);
            }
            composeActions.add(sendButton);

            JPanel compose = new JPanel(new BorderLayout(0, 6));
            compose.setOpaque(false);
            compose.setBorder(new EmptyBorder(8, 12, 0, 12));
            compose.add(inputScroll, BorderLayout.CENTER);
            compose.add(composeActions, BorderLayout.SOUTH);

            JLabel composeHint = new JLabel("Enter 送出 · Shift+Enter 換行 · 可貼上圖片或拖曳檔案");
            composeHint.setFont(plain.deriveFont((float) Math.max(10, plain.getSize() - 2)));
            composeHint.setForeground(NightTheme.MUTED);
            composeHint.setBorder(new EmptyBorder(2, 12, 6, 12));
            return new ComposeAreaParts(input, attachButton, graffitiButton, sendButton, compose, composeHint);
        }
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
