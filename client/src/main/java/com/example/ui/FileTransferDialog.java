package com.example.ui;

import com.example.ChatProtocol;
import com.example.PeerFileRules;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * 傳送檔案：左側選在線好友，右側挑檔再送出。
 * <p>
 * 好友只使用呼叫端傳入的真實名單（裝置 ID、在線、版本、大頭照）。
 * 多個檔案仍走原本的傳送流程（多個項目打包成一個 ZIP），這裡不改傳輸協定。
 * 截圖與貼上圖片會先存成暫存 PNG，再跟一般檔案一樣送出。
 * </p>
 */
public final class FileTransferDialog extends JDialog {

    private static final int PREFERRED_WIDTH = 900;
    private static final int PREFERRED_HEIGHT = 650;
    private static final int GRIP = 6;

    private final List<Friend> ordered;
    private final List<Chosen> files = new ArrayList<>();
    private final HintField search = new HintField("搜尋好友或裝置…");
    private final HintField noteField = new HintField("（選填）輸入訊息…");
    private final TrackColumn friendColumn = new TrackColumn(CandyTheme.FIELD);
    private final TrackColumn fileColumn = new TrackColumn(CandyTheme.FIELD);
    private final JLabel countLabel = label("好友 (0)", UiFonts.chinesePlain(12), CandyTheme.MUTED);
    private final JLabel fileCount = label("已選擇的檔案 (0)", UiFonts.chineseBold(13), CandyTheme.INK);
    private final FriendListItem.FitLabel nameLabel =
            new FriendListItem.FitLabel(UiFonts.chineseBold(15), CandyTheme.MUTED);
    private final FriendListItem.FitLabel statusLabel =
            new FriendListItem.FitLabel(UiFonts.chinesePlain(12), CandyTheme.DISABLED);
    private final JPanel avatarSlot = new JPanel(new GridBagLayout());
    private final CandyButton switchButton =
            new CandyButton("切換", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
    private final CandyButton clearButton =
            new CandyButton("清空", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
    private final CandyButton sendButton = new CandyButton("傳送", CandyButton.Variant.PRIMARY, CandyButton.Size.MEDIUM);
    private final JSplitPane split;
    private final JPanel leftPane;
    private final JPanel rightPane;
    private final DropZone dropZone = new DropZone();
    private final JScrollPane friendScroll;
    private Friend selected;
    private Result result;
    private boolean finished;
    private boolean dividerPlaced;
    private boolean dropHot;
    private int tempSerial;

    FileTransferDialog(Window owner, List<Friend> friends, String preselectId) {
        super(owner, "傳送檔案", Dialog.ModalityType.APPLICATION_MODAL);
        this.ordered = order(friends);
        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setBackground(CandyTheme.NIGHT);
        getRootPane().setBackground(CandyTheme.NIGHT);

        search.setName("transfer.search");
        search.setFont(UiFonts.chinesePlain(13));
        noteField.setName("transfer.note");
        noteField.setFont(UiFonts.chinesePlain(13));
        noteField.setToolTipText("選填。會另外傳一則訊息給對方，不會包進檔案裡。");
        switchButton.setFont(UiFonts.chinesePlain(13));
        switchButton.setToolTipText("回到左側改選好友");
        switchButton.setEnabled(false);
        clearButton.setFont(UiFonts.chinesePlain(12));
        clearButton.setToolTipText("清除已選檔案");
        clearButton.setEnabled(false);
        sendButton.setName("transfer.send");
        sendButton.setFont(UiFonts.chineseBold(14));
        sendButton.setToolTipText("多個檔案或資料夾會打包成一個 ZIP 再傳送");
        sendButton.setEnabled(false);
        nameLabel.setFullText("尚未選擇好友");
        statusLabel.setFullText("請從左側選擇一位在線好友");
        avatarSlot.setOpaque(false);

        friendScroll = scroll(friendColumn, "transfer.friends");
        JScrollPane fileScroll = scroll(fileColumn, "transfer.files");
        leftPane = buildLeft();
        rightPane = buildRight(fileScroll);
        split = buildSplit();

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(10, 10, 10, 10));
        body.add(split, BorderLayout.CENTER);

        JPanel title = buildTitle();
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(true);
        outer.setBackground(CandyTheme.NIGHT);
        outer.setBorder(new CompoundBorder(
                new LineBorder(CandyTheme.EDGE, 1, true),
                new EmptyBorder(GRIP - 1, GRIP - 1, GRIP - 1, GRIP - 1)));
        outer.add(title, BorderLayout.NORTH);
        outer.add(body, BorderLayout.CENTER);
        setContentPane(outer);

        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                rebuildFriends();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                rebuildFriends();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                rebuildFriends();
            }
        });
        switchButton.addActionListener(e -> focusFriendSearch());
        clearButton.addActionListener(e -> clearChosen());
        sendButton.addActionListener(e -> confirmSelection());
        installFileDrop(dropZone);
        installFileDrop(fileColumn);
        installDrag(title);
        new EdgeResize().attach(outer);

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelTransfer");
        getRootPane().getActionMap().put("cancelTransfer", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cancelSelection();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelSelection();
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                placeDividerOnce();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                placeDividerOnce();
            }
        });

        Rectangle usable = usableBounds(owner);
        int availW = Math.max(320, usable.width - 24);
        int availH = Math.max(240, usable.height - 24);
        int prefW = Math.min(PREFERRED_WIDTH, availW);
        int prefH = Math.min(PREFERRED_HEIGHT, availH);
        int minW = Math.min(760, prefW);
        int minH = Math.min(520, prefH);
        int leftMin = Math.max(168, Math.min(240, (int) (minW * 0.34)));
        int rightMin = Math.max(220, minW - leftMin - 36);
        leftPane.setMinimumSize(new Dimension(leftMin, 180));
        rightPane.setMinimumSize(new Dimension(rightMin, 180));
        leftPane.setPreferredSize(new Dimension((int) (prefW * 0.35), prefH - 80));
        rightPane.setPreferredSize(new Dimension(prefW - leftPane.getPreferredSize().width, prefH - 80));
        setMinimumSize(new Dimension(minW, minH));
        setPreferredSize(new Dimension(prefW, prefH));
        setSize(prefW, prefH);
        setLocationRelativeTo(owner);
        nudgeOntoScreen(usable);

        rebuildFriends();
        rebuildFiles();
        applyInitialSelection(preselectId);
    }

    /**
     * 開視窗。取消、關閉或按 Esc 回傳 null，不會送出檔案。必須在 EDT 呼叫。
     */
    public static Result show(Component parent, List<Friend> friends) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("傳送檔案視窗必須在 EDT 開啟");
        }
        FileTransferDialog dialog = new FileTransferDialog(ownerWindow(parent), friends, null);
        dialog.setVisible(true);
        Result picked = dialog.result;
        dialog.dispose();
        return picked;
    }

    static List<Friend> order(List<Friend> friends) {
        List<Friend> online = new ArrayList<>();
        List<Friend> offline = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (friends != null) {
            for (Friend friend : friends) {
                if (friend == null || friend.clientId.isEmpty() || !seen.add(friend.clientId)) {
                    continue;
                }
                if (friend.online) {
                    online.add(friend);
                } else {
                    offline.add(friend);
                }
            }
        }
        online.addAll(offline);
        return online;
    }

    static String kindLabel(String filename) {
        String ext = extension(filename);
        switch (ext) {
            case "txt":
            case "md":
            case "log":
            case "csv":
            case "json":
            case "xml":
                return "文字檔";
            case "java":
            case "kt":
            case "py":
            case "js":
            case "ts":
            case "c":
            case "cpp":
            case "h":
            case "cs":
            case "go":
            case "rs":
            case "swift":
                return "原始碼";
            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
            case "webp":
            case "bmp":
                return "圖片";
            case "pdf":
                return "PDF";
            case "zip":
            case "7z":
            case "rar":
            case "tar":
            case "gz":
                return "壓縮檔";
            default:
                return "檔案";
        }
    }

    /** 不能加入清單時回傳原因，可以加入時回傳 null。 */
    static String rejection(Path path) {
        if (path == null) {
            return "沒有檔案";
        }
        if (!Files.exists(path)) {
            return "找不到檔案";
        }
        try {
            if (Files.isDirectory(path)) {
                return null;
            }
            if (!Files.isRegularFile(path)) {
                return "無法傳送這個項目";
            }
            long size = Files.size(path);
            if (size <= 0) {
                return "檔案是空的";
            }
            if (size > PeerFileRules.MAX_BYTES) {
                return "檔案不可超過 " + PeerFileRules.MAX_SIZE_LABEL;
            }
            return null;
        } catch (IOException ex) {
            return "無法讀取檔案";
        }
    }

    List<String> visibleClientIds() {
        List<String> ids = new ArrayList<>();
        for (Component child : friendColumn.getComponents()) {
            if (child instanceof FriendListItem) {
                ids.add(((FriendListItem) child).clientId());
            }
        }
        return ids;
    }

    String selectedClientId() {
        return selected == null ? null : selected.clientId;
    }

    List<Path> chosenPaths() {
        List<Path> paths = new ArrayList<>();
        for (Chosen chosen : files) {
            paths.add(chosen.path);
        }
        return paths;
    }

    List<String> chosenDescriptions() {
        List<String> lines = new ArrayList<>();
        for (Chosen chosen : files) {
            lines.add(chosen.name + "|" + chosen.meta);
        }
        return lines;
    }

    boolean canSend() {
        return selected != null && selected.online && !files.isEmpty();
    }

    void setQuery(String query) {
        search.setText(query == null ? "" : query);
    }

    boolean selectClient(String clientId) {
        Friend friend = find(clientId);
        if (friend == null || !friend.online) {
            return false;
        }
        selected = friend;
        refreshFriendSelection();
        showRecipient(friend);
        refreshSend();
        return true;
    }

    int addPaths(List<Path> paths, boolean notify) {
        if (paths == null || paths.isEmpty()) {
            return 0;
        }
        List<String> problems = new ArrayList<>();
        int added = 0;
        for (Path path : paths) {
            String reason = rejection(path);
            String label = path == null || path.getFileName() == null ? "檔案" : path.getFileName().toString();
            if (reason != null) {
                problems.add(label + "：" + reason);
                continue;
            }
            Path stored = canonical(path);
            if (containsPath(stored)) {
                problems.add(label + "：已經在清單裡");
                continue;
            }
            files.add(new Chosen(stored, false));
            added++;
        }
        if (added > 0) {
            rebuildFiles();
        }
        if (notify && !problems.isEmpty()) {
            UiFonts.showWarning(this, String.join("\n", problems), "傳送檔案");
        }
        return added;
    }

    boolean removeChosen(Path path) {
        Path key = canonical(path);
        if (key == null) {
            return false;
        }
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).path.equals(key)) {
                deleteIfTemp(files.remove(i));
                rebuildFiles();
                return true;
            }
        }
        return false;
    }

    void clearChosen() {
        for (Chosen chosen : files) {
            deleteIfTemp(chosen);
        }
        files.clear();
        rebuildFiles();
    }

    void setNote(String note) {
        noteField.setText(note == null ? "" : note);
    }

    String note() {
        return noteField.getText() == null ? "" : noteField.getText().trim();
    }

    Result confirmSelection() {
        if (finished || !canSend()) {
            return null;
        }
        String message = note();
        if (message.length() > ChatProtocol.MAX_TEXT_CHARS) {
            UiFonts.showWarning(this, "訊息長度不可超過 " + ChatProtocol.MAX_TEXT_CHARS + " 字。", "傳送檔案");
            return null;
        }
        result = new Result(selected.clientId, chosenPaths(), message);
        finished = true;
        setVisible(false);
        return result;
    }

    void cancelSelection() {
        if (finished) {
            return;
        }
        for (Chosen chosen : files) {
            deleteIfTemp(chosen);
        }
        result = null;
        finished = true;
        setVisible(false);
    }

    Path writeTempPng(BufferedImage image, String prefix) throws IOException {
        if (image == null) {
            throw new IOException("沒有圖片");
        }
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "punchclock-send");
        Files.createDirectories(dir);
        String stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss").format(LocalDateTime.now());
        tempSerial++;
        Path target = dir.resolve(prefix + " " + stamp + "-" + tempSerial + ".png");
        while (Files.exists(target)) {
            tempSerial++;
            target = dir.resolve(prefix + " " + stamp + "-" + tempSerial + ".png");
        }
        if (!ImageIO.write(image, "png", target.toFile())) {
            throw new IOException("無法儲存圖片");
        }
        return target;
    }

    void layoutForTest(int width, int height) {
        setSize(width, height);
        if (!isDisplayable()) {
            addNotify();
        }
        validate();
        dividerPlaced = false;
        placeDividerOnce();
        validate();
    }

    int dividerLocation() {
        return split.getDividerLocation();
    }

    int splitWidth() {
        return split.getWidth();
    }

    JButton sendButton() {
        return sendButton;
    }

    private void applyInitialSelection(String preselectId) {
        if (preselectId != null && selectClient(preselectId)) {
            return;
        }
        Friend only = null;
        int onlineCount = 0;
        for (Friend friend : ordered) {
            if (!friend.online) {
                continue;
            }
            onlineCount++;
            only = friend;
        }
        if (onlineCount == 1) {
            selectClient(only.clientId);
        } else {
            showRecipient(null);
        }
    }

    private void rebuildFriends() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        friendColumn.removeAll();
        int shown = 0;
        FriendListItem selectedItem = null;
        for (Friend friend : ordered) {
            if (!query.isEmpty() && !friend.clientId.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            if (shown > 0) {
                friendColumn.add(Box.createVerticalStrut(6));
            }
            FriendListItem item = new FriendListItem(
                    friend.clientId, friend.online, friend.appVersion, friend.avatarEncoded);
            item.setSelected(selected != null && friend.clientId.equals(selected.clientId));
            item.setOnActivate(() -> selectClient(friend.clientId));
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            friendColumn.add(item);
            if (item.isItemSelected()) {
                selectedItem = item;
            }
            shown++;
        }
        if (shown == 0) {
            JLabel empty = label(query.isEmpty() ? "目前沒有好友" : "沒有符合的好友",
                    UiFonts.chinesePlain(13), CandyTheme.MUTED);
            empty.setBorder(new EmptyBorder(18, 8, 8, 8));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            friendColumn.add(empty);
        } else {
            friendColumn.add(Box.createVerticalGlue());
        }
        countLabel.setText("好友 (" + shown + ")");
        friendColumn.revalidate();
        friendColumn.repaint();
        if (selectedItem != null) {
            FriendListItem target = selectedItem;
            SwingUtilities.invokeLater(() -> target.scrollRectToVisible(new Rectangle(target.getSize())));
        }
    }

    private void refreshFriendSelection() {
        for (Component child : friendColumn.getComponents()) {
            if (child instanceof FriendListItem) {
                FriendListItem item = (FriendListItem) child;
                item.setSelected(selected != null && item.clientId().equals(selected.clientId));
            }
        }
    }

    private void showRecipient(Friend friend) {
        avatarSlot.removeAll();
        if (friend == null) {
            nameLabel.setForeground(CandyTheme.MUTED);
            nameLabel.setFullText("尚未選擇好友");
            statusLabel.setForeground(CandyTheme.DISABLED);
            statusLabel.setFullText("請從左側選擇一位在線好友");
            avatarSlot.add(FriendListItem.badge("", false, "", 44));
        } else {
            nameLabel.setForeground(CandyTheme.INK);
            nameLabel.setFullText(friend.clientId);
            statusLabel.setForeground(friend.online ? new Color(0x8F, 0xE8, 0xB8) : CandyTheme.DISABLED);
            statusLabel.setFullText(FriendListItem.statusLine(friend.online, friend.appVersion));
            avatarSlot.add(FriendListItem.badge(
                    friend.clientId, friend.online, friend.avatarEncoded, 44));
        }
        avatarSlot.revalidate();
        avatarSlot.repaint();
        refreshSend();
    }

    private void rebuildFiles() {
        fileColumn.removeAll();
        if (files.isEmpty()) {
            JLabel empty = label("還沒有選擇檔案", UiFonts.chinesePlain(13), CandyTheme.MUTED);
            empty.setBorder(new EmptyBorder(16, 10, 8, 10));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            fileColumn.add(empty);
        } else {
            for (int i = 0; i < files.size(); i++) {
                if (i > 0) {
                    fileColumn.add(Box.createVerticalStrut(6));
                }
                fileColumn.add(fileRow(files.get(i)));
            }
            fileColumn.add(Box.createVerticalGlue());
        }
        String extra = files.size() > 1 ? " · 將打包成一個 ZIP" : "";
        fileCount.setText("已選擇的檔案 (" + files.size() + ")" + extra);
        fileColumn.revalidate();
        fileColumn.repaint();
        refreshSend();
    }

    private JComponent fileRow(Chosen chosen) {
        FileRow row = new FileRow();
        row.add(new FileGlyph(chosen.directory ? "資料夾" : kindLabel(chosen.name)), BorderLayout.WEST);
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.setBorder(new EmptyBorder(0, 10, 0, 8));
        FriendListItem.FitLabel name = new FriendListItem.FitLabel(UiFonts.chineseBold(13), CandyTheme.INK);
        name.setFullText(chosen.name);
        FriendListItem.FitLabel meta = new FriendListItem.FitLabel(UiFonts.chinesePlain(12), CandyTheme.MUTED);
        meta.setFullText(chosen.meta);
        text.add(name);
        text.add(meta);
        row.add(text, BorderLayout.CENTER);
        JButton remove = new RemoveButton();
        remove.addActionListener(e -> removeChosen(chosen.path));
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        east.add(remove);
        row.add(east, BorderLayout.EAST);
        return row;
    }

    private void refreshSend() {
        sendButton.setText(files.isEmpty() ? "傳送" : "傳送 (" + files.size() + ")");
        sendButton.setEnabled(canSend());
        switchButton.setEnabled(selected != null);
        clearButton.setEnabled(!files.isEmpty());
    }

    private void focusFriendSearch() {
        search.requestFocusInWindow();
        search.selectAll();
        for (Component child : friendColumn.getComponents()) {
            if (child instanceof FriendListItem && ((FriendListItem) child).isItemSelected()) {
                ((JComponent) child).scrollRectToVisible(new Rectangle(child.getSize()));
            }
        }
    }

    private void chooseFiles() {
        JFileChooser chooser = UiFonts.fileChooser();
        chooser.setDialogTitle("選擇檔案或資料夾（可多選，多個項目會打包成一個 ZIP）");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        chooser.setAcceptAllFileFilterUsed(true);
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
        List<Path> paths = new ArrayList<>();
        for (File file : picked) {
            if (file != null) {
                paths.add(file.toPath());
            }
        }
        addPaths(paths, true);
    }

    private void pasteImage() {
        Image image = clipboardImage();
        if (image == null) {
            UiFonts.showMessage(this, "剪貼簿裡沒有圖片。", "傳送檔案", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        BufferedImage buffered = toBuffered(image);
        if (buffered == null) {
            UiFonts.showWarning(this, "無法讀取剪貼簿裡的圖片。", "傳送檔案");
            return;
        }
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return writeTempPng(buffered, "貼上圖片");
            }

            @Override
            protected void done() {
                acceptTempImage(this);
            }
        }.execute();
    }

    private void captureScreen() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) {
            UiFonts.showWarning(this, "目前無法截圖。", "傳送檔案");
            return;
        }
        Rectangle bounds = new Rectangle(gc.getBounds());
        Point origin = getLocation();
        boolean faded = hideForCapture(gc);
        Timer timer = new Timer(280, event -> {
            ((Timer) event.getSource()).stop();
            new SwingWorker<Path, Void>() {
                @Override
                protected Path doInBackground() throws Exception {
                    BufferedImage shot = new Robot(gc.getDevice()).createScreenCapture(bounds);
                    return writeTempPng(shot, "截圖");
                }

                @Override
                protected void done() {
                    restoreAfterShot(faded, origin);
                    acceptTempImage(this);
                }
            }.execute();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private boolean hideForCapture(GraphicsConfiguration gc) {
        try {
            GraphicsDevice device = gc.getDevice();
            if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
                setOpacity(0.01f);
                return true;
            }
        } catch (RuntimeException ex) {
            // 改把視窗移出畫面再截
        }
        Rectangle bounds = gc.getBounds();
        setLocation(bounds.x - getWidth() - 32, bounds.y);
        return false;
    }

    private void restoreAfterShot(boolean faded, Point origin) {
        if (faded) {
            try {
                setOpacity(1f);
                return;
            } catch (RuntimeException ex) {
                // 還原位置
            }
        }
        if (origin != null) {
            setLocation(origin);
        }
    }

    private void acceptTempImage(SwingWorker<Path, Void> worker) {
        Path path = null;
        Exception failure = null;
        try {
            path = worker.get();
        } catch (Exception ex) {
            failure = ex;
        }
        if (finished || !isDisplayable()) {
            deleteQuiet(path);
            return;
        }
        if (failure != null || path == null) {
            Throwable cause = failure == null ? null : (failure.getCause() != null ? failure.getCause() : failure);
            UiFonts.showWarning(this, "無法加入圖片"
                    + (cause == null || cause.getMessage() == null ? "。" : "：" + cause.getMessage()), "傳送檔案");
            deleteQuiet(path);
            return;
        }
        addTemporaryImage(path);
    }

    void addTemporaryImage(Path path) {
        String reason = rejection(path);
        if (reason != null) {
            deleteQuiet(path);
            UiFonts.showWarning(this, reason, "傳送檔案");
            return;
        }
        Path stored = canonical(path);
        if (containsPath(stored)) {
            deleteQuiet(path);
            return;
        }
        files.add(new Chosen(stored, true));
        rebuildFiles();
    }

    private void installFileDrop(JComponent target) {
        if (target == null || Boolean.TRUE.equals(target.getClientProperty("transfer.fileDrop"))) {
            return;
        }
        if (target instanceof javax.swing.text.JTextComponent) {
            return;
        }
        target.putClientProperty("transfer.fileDrop", Boolean.TRUE);
        new DropTarget(target, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent event) {
                if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    event.acceptDrag(DnDConstants.ACTION_COPY);
                    setDropHot(true);
                } else {
                    event.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent event) {
                setDropHot(false);
            }

            @Override
            public void drop(DropTargetDropEvent event) {
                setDropHot(false);
                try {
                    if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
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
                        SwingUtilities.invokeLater(() -> addPaths(paths, true));
                    }
                } catch (Exception ex) {
                    event.dropComplete(false);
                }
            }
        }, true);
        target.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent event) {
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

    private void setDropHot(boolean hot) {
        if (dropHot == hot) {
            return;
        }
        dropHot = hot;
        dropZone.repaint();
    }

    private JPanel buildLeft() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(2, 2, 2, 6));
        panel.add(search, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        center.add(countLabel, BorderLayout.NORTH);
        center.add(friendScroll, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRight(JScrollPane fileScroll) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(2, 6, 2, 2));
        panel.add(buildRecipient(), BorderLayout.NORTH);
        JPanel files = new JPanel(new BorderLayout(0, 8));
        files.setOpaque(false);
        JPanel fileHead = new JPanel(new BorderLayout(8, 0));
        fileHead.setOpaque(false);
        fileHead.add(fileCount, BorderLayout.CENTER);
        fileHead.add(clearButton, BorderLayout.EAST);
        files.add(fileHead, BorderLayout.NORTH);
        files.add(fileScroll, BorderLayout.CENTER);
        JPanel middle = new JPanel(new BorderLayout(0, 10));
        middle.setOpaque(false);
        middle.add(dropZone, BorderLayout.NORTH);
        middle.add(files, BorderLayout.CENTER);
        panel.add(middle, BorderLayout.CENTER);
        panel.add(buildBottom(), BorderLayout.SOUTH);
        installFileDrop(middle);
        return panel;
    }

    private JComponent buildRecipient() {
        JPanel block = new JPanel(new BorderLayout(0, 6));
        block.setOpaque(false);
        block.add(label("發送給", UiFonts.chinesePlain(12), CandyTheme.MUTED), BorderLayout.NORTH);
        RoundCard card = new RoundCard();
        card.setLayout(new BorderLayout(10, 0));
        avatarSlot.setPreferredSize(new Dimension(48, 48));
        card.add(avatarSlot, BorderLayout.WEST);
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(nameLabel);
        text.add(statusLabel);
        card.add(text, BorderLayout.CENTER);
        JPanel switchWrap = new JPanel(new GridBagLayout());
        switchWrap.setOpaque(false);
        switchWrap.add(switchButton);
        card.add(switchWrap, BorderLayout.EAST);
        block.add(card, BorderLayout.CENTER);
        showRecipient(null);
        return block;
    }

    private JComponent buildBottom() {
        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setOpaque(false);
        GridBagConstraints noteConstraints = new GridBagConstraints();
        noteConstraints.gridx = 0;
        noteConstraints.weightx = 1;
        noteConstraints.fill = GridBagConstraints.HORIZONTAL;
        noteConstraints.insets = new Insets(0, 0, 0, 10);
        bottom.add(noteField, noteConstraints);
        GridBagConstraints sendConstraints = new GridBagConstraints();
        sendConstraints.gridx = 1;
        sendConstraints.anchor = GridBagConstraints.EAST;
        bottom.add(sendButton, sendConstraints);
        return bottom;
    }

    private JPanel buildTitle() {
        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(true);
        title.setBackground(CandyTheme.HEADER);
        title.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CandyTheme.EDGE),
                new EmptyBorder(6, 12, 6, 8)));
        JLabel heading = new JLabel("傳送檔案", new DotIcon(), JLabel.LEFT);
        heading.setFont(UiFonts.chineseBold(15));
        heading.setForeground(CandyTheme.INK);
        heading.setIconTextGap(8);
        title.add(heading, BorderLayout.CENTER);
        JButton close = new CloseButton();
        close.addActionListener(e -> cancelSelection());
        title.add(close, BorderLayout.EAST);
        return title;
    }

    private JSplitPane buildSplit() {
        JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, rightPane);
        pane.setContinuousLayout(true);
        pane.setResizeWeight(0.35);
        pane.setDividerSize(8);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setOpaque(true);
        pane.setBackground(CandyTheme.NIGHT);
        pane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(CandyTheme.NIGHT);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
                        g2.setColor(CandyTheme.EDGE);
                        int barH = Math.max(28, getHeight() / 5);
                        int y = (getHeight() - barH) / 2;
                        g2.fillRoundRect(Math.max(0, getWidth() / 2 - 1), y, 3, barH, 3, 3);
                        g2.dispose();
                    }
                };
            }
        });
        return pane;
    }

    private void placeDividerOnce() {
        if (dividerPlaced) {
            return;
        }
        int width = split.getWidth();
        if (width <= 0) {
            return;
        }
        dividerPlaced = true;
        int loc = (int) Math.round(width * 0.35);
        int minLeft = leftPane.getMinimumSize().width;
        int minRight = rightPane.getMinimumSize().width;
        int max = width - minRight - split.getDividerSize();
        if (max < minLeft) {
            loc = Math.max(0, width / 3);
        } else {
            loc = Math.max(minLeft, Math.min(max, loc));
        }
        split.setDividerLocation(loc);
    }

    private Friend find(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        for (Friend friend : ordered) {
            if (friend.clientId.equals(clientId)) {
                return friend;
            }
        }
        return null;
    }

    private boolean containsPath(Path path) {
        if (path == null) {
            return false;
        }
        for (Chosen chosen : files) {
            if (chosen.path.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static JScrollPane scroll(JComponent view, String name) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setName(name);
        scroll.setBorder(new LineBorder(CandyTheme.EDGE, 1, true));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(CandyTheme.FIELD);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(FriendListItem.ROW_HEIGHT);
        CandyScrollBar.install(scroll);
        return scroll;
    }

    private static JLabel label(String text, java.awt.Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static Window ownerWindow(Component parent) {
        if (parent == null) {
            return null;
        }
        if (parent instanceof Window) {
            return (Window) parent;
        }
        return SwingUtilities.getWindowAncestor(parent);
    }

    private static Rectangle usableBounds(Window owner) {
        GraphicsConfiguration gc = owner == null ? null : owner.getGraphicsConfiguration();
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }
        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                Math.max(320, bounds.width - insets.left - insets.right),
                Math.max(240, bounds.height - insets.top - insets.bottom));
    }

    private void nudgeOntoScreen(Rectangle usable) {
        int x = getX();
        int y = getY();
        if (x < usable.x) {
            x = usable.x;
        }
        if (y < usable.y) {
            y = usable.y;
        }
        if (x + getWidth() > usable.x + usable.width) {
            x = usable.x + Math.max(0, usable.width - getWidth());
        }
        if (y + getHeight() > usable.y + usable.height) {
            y = usable.y + Math.max(0, usable.height - getHeight());
        }
        setLocation(x, y);
    }

    private static Path canonical(Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize();
    }

    private static void deleteIfTemp(Chosen chosen) {
        if (chosen != null && chosen.temporary) {
            deleteQuiet(chosen.path);
        }
    }

    private static void deleteQuiet(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 暫存圖刪不掉不影響傳送
        }
    }

    private static Image clipboardImage() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                return null;
            }
            Object data = clipboard.getData(DataFlavor.imageFlavor);
            return data instanceof Image ? (Image) data : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static BufferedImage toBuffered(Image image) {
        if (image instanceof BufferedImage) {
            BufferedImage buffered = (BufferedImage) image;
            if (buffered.getWidth() > 0 && buffered.getHeight() > 0) {
                return buffered;
            }
        }
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        if (w <= 0 || h <= 0) {
            return null;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String describe(Path path) {
        try {
            if (Files.isDirectory(path)) {
                return "資料夾";
            }
            String name = path.getFileName() == null ? "" : path.getFileName().toString();
            return PeerFileRules.formatSize(Files.size(path)) + " · " + kindLabel(name);
        } catch (IOException ex) {
            return "檔案";
        }
    }

    private void installDrag(JComponent title) {
        MouseAdapter adapter = new MouseAdapter() {
            private Point press;
            private Point origin;

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1 || e.getSource() instanceof CloseButton) {
                    return;
                }
                press = e.getLocationOnScreen();
                origin = getLocation();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                press = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (press == null || origin == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                setLocation(origin.x + now.x - press.x, origin.y + now.y - press.y);
            }
        };
        attachDrag(title, adapter);
    }

    private static void attachDrag(Component component, MouseAdapter adapter) {
        if (component instanceof CloseButton) {
            return;
        }
        component.addMouseListener(adapter);
        component.addMouseMotionListener(adapter);
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                attachDrag(child, adapter);
            }
        }
    }

    private final class EdgeResize {
        private static final int N = 1;
        private static final int S = 2;
        private static final int W = 4;
        private static final int E = 8;

        private int dir;
        private Point press;
        private Rectangle start;

        void attach(JComponent outer) {
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    outer.setCursor(cursorFor(directionAt(outer, e)));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (dir == 0) {
                        outer.setCursor(Cursor.getDefaultCursor());
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    dir = directionAt(outer, e);
                    if (dir == 0) {
                        return;
                    }
                    press = e.getLocationOnScreen();
                    start = getBounds();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dir = 0;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dir == 0 || press == null || start == null) {
                        return;
                    }
                    Point now = e.getLocationOnScreen();
                    Rectangle next = new Rectangle(start);
                    int dx = now.x - press.x;
                    int dy = now.y - press.y;
                    if ((dir & N) != 0) {
                        next.y += dy;
                        next.height -= dy;
                    }
                    if ((dir & S) != 0) {
                        next.height += dy;
                    }
                    if ((dir & W) != 0) {
                        next.x += dx;
                        next.width -= dx;
                    }
                    if ((dir & E) != 0) {
                        next.width += dx;
                    }
                    Dimension min = getMinimumSize();
                    if (next.width < min.width) {
                        if ((dir & W) != 0) {
                            next.x = start.x + start.width - min.width;
                        }
                        next.width = min.width;
                    }
                    if (next.height < min.height) {
                        if ((dir & N) != 0) {
                            next.y = start.y + start.height - min.height;
                        }
                        next.height = min.height;
                    }
                    setBounds(next);
                }
            };
            outer.addMouseListener(adapter);
            outer.addMouseMotionListener(adapter);
        }

        private int directionAt(JComponent outer, MouseEvent e) {
            Insets insets = outer.getInsets();
            int x = e.getX();
            int y = e.getY();
            int w = outer.getWidth();
            int h = outer.getHeight();
            int gripX = Math.max(insets.left, GRIP);
            int gripY = Math.max(insets.top, GRIP);
            int next = 0;
            if (y < gripY) {
                next |= N;
            } else if (y >= h - Math.max(insets.bottom, GRIP)) {
                next |= S;
            }
            if (x < gripX) {
                next |= W;
            } else if (x >= w - Math.max(insets.right, GRIP)) {
                next |= E;
            }
            return next;
        }

        private Cursor cursorFor(int direction) {
            switch (direction) {
                case N:
                    return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
                case S:
                    return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
                case W:
                    return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
                case E:
                    return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
                case N | W:
                    return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
                case N | E:
                    return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
                case S | W:
                    return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
                case S | E:
                    return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
                default:
                    return Cursor.getDefaultCursor();
            }
        }
    }

    private final class DropZone extends JPanel {
        private DropZone() {
            setLayout(new BorderLayout(0, 8));
            setOpaque(false);
            setBorder(new EmptyBorder(16, 16, 12, 16));
            setPreferredSize(new Dimension(120, 158));
            setMinimumSize(new Dimension(80, 112));
            CandyTheme.watch(this);
            JLabel title = label("拖曳檔案到這裡", UiFonts.chineseBold(15), CandyTheme.INK);
            title.setHorizontalAlignment(JLabel.CENTER);
            JLabel hint = label("或點下方按鈕選擇檔案", UiFonts.chinesePlain(12), CandyTheme.MUTED);
            hint.setHorizontalAlignment(JLabel.CENTER);
            JPanel hints = new JPanel(new GridLayout(2, 1));
            hints.setOpaque(false);
            hints.add(title);
            hints.add(hint);
            add(hints, BorderLayout.CENTER);
            JPanel actions = new JPanel(new GridLayout(1, 3, 8, 0));
            actions.setOpaque(false);
            CandyButton choose = new CandyButton("選擇檔案", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
            CandyButton shot = new CandyButton("截圖", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
            CandyButton paste = new CandyButton("貼上圖片", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
            choose.setFont(UiFonts.chinesePlain(13));
            shot.setFont(UiFonts.chinesePlain(13));
            paste.setFont(UiFonts.chinesePlain(13));
            choose.setToolTipText("可多選檔案或資料夾");
            shot.setName("transfer.screenshot");
            shot.setToolTipText("截下這個螢幕，存成圖片後加入清單");
            paste.setName("transfer.paste");
            paste.setToolTipText("把剪貼簿裡的圖片加入清單");
            choose.addActionListener(e -> chooseFiles());
            shot.addActionListener(e -> captureScreen());
            paste.addActionListener(e -> pasteImage());
            actions.add(choose);
            actions.add(shot);
            actions.add(paste);
            add(actions, BorderLayout.SOUTH);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            int w = Math.max(1, getWidth() - 1);
            int h = Math.max(1, getHeight() - 1);
            float hover = CandyTheme.hover(this);
            Color top = CandyTheme.mix(CandyTheme.FIELD, CandyTheme.HEADER_HOT, dropHot ? 1f : hover * 0.7f);
            g2.setPaint(new java.awt.GradientPaint(0, 0, top, 0, h, CandyTheme.FIELD));
            g2.fillRoundRect(1, 1, w - 1, h - 1, 16, 16);
            g2.setColor(dropHot ? CandyTheme.PINK : CandyTheme.mix(CandyTheme.EDGE, CandyTheme.PINK, hover));
            float[] dash = dropHot ? null : new float[]{7f, 5f};
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dash, 0f));
            g2.drawRoundRect(2, 2, Math.max(1, w - 4), Math.max(1, h - 4), 16, 16);
            g2.dispose();
        }
    }

    private static final class Chosen {
        final Path path;
        final boolean temporary;
        final boolean directory;
        final String name;
        final String meta;

        Chosen(Path path, boolean temporary) {
            this.path = path;
            this.temporary = temporary;
            this.directory = Files.isDirectory(path);
            Path fileName = path.getFileName();
            this.name = fileName == null ? path.toString() : fileName.toString();
            this.meta = describe(path);
        }
    }

    private static final class TrackColumn extends JPanel implements Scrollable {
        private TrackColumn(Color background) {
            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
            setOpaque(true);
            setBackground(background);
            setBorder(new EmptyBorder(8, 8, 8, 8));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(260, 280);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return FriendListItem.ROW_HEIGHT + 6;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(FriendListItem.ROW_HEIGHT, visibleRect.height - FriendListItem.ROW_HEIGHT);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class RoundCard extends JPanel {
        private RoundCard() {
            setOpaque(false);
            setBorder(new EmptyBorder(10, 12, 10, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            int w = Math.max(1, getWidth() - 1);
            int h = Math.max(1, getHeight() - 1);
            g2.setColor(CandyTheme.PANEL);
            g2.fillRoundRect(0, 0, w, h, 14, 14);
            g2.setColor(CandyTheme.EDGE);
            g2.drawRoundRect(0, 0, w, h, 14, 14);
            g2.dispose();
        }
    }

    private static final class FileRow extends JPanel {
        private FileRow() {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(8, 10, 8, 8));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            CandyTheme.watch(this);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension pref = super.getPreferredSize();
            pref.height = Math.max(pref.height, 56);
            return pref;
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            float hover = CandyTheme.hover(this);
            g2.setColor(CandyTheme.mix(CandyTheme.ROW, CandyTheme.ROW_HOT, hover));
            g2.fillRoundRect(0, 0, Math.max(1, getWidth() - 1), Math.max(1, getHeight() - 1), 12, 12);
            g2.dispose();
        }
    }

    private static final class FileGlyph extends JComponent {
        private final String kind;

        private FileGlyph(String kind) {
            this.kind = kind == null ? "檔案" : kind;
            Dimension box = new Dimension(36, 36);
            setPreferredSize(box);
            setMinimumSize(box);
            setMaximumSize(box);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            g2.setColor(glyphFill(kind));
            g2.fillRoundRect(0, 0, 36, 36, 10, 10);
            g2.setColor("圖片".equals(kind) ? CandyTheme.PINK_INK : CandyTheme.NIGHT);
            String mark = glyphMark(kind);
            g2.setFont("</>".equals(mark) ? UiFonts.latinBold(11) : UiFonts.chineseBold(12));
            java.awt.FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(mark, (36 - metrics.stringWidth(mark)) / 2,
                    (36 - metrics.getHeight()) / 2 + metrics.getAscent());
            g2.dispose();
        }

        private static Color glyphFill(String kind) {
            switch (kind) {
                case "圖片":
                    return CandyTheme.PINK;
                case "原始碼":
                    return CandyTheme.LAVENDER;
                case "資料夾":
                    return new Color(0xE0, 0xB0, 0x6A);
                case "文字檔":
                    return new Color(0xC8, 0xB8, 0xE4);
                default:
                    return CandyTheme.EDGE;
            }
        }

        private static String glyphMark(String kind) {
            switch (kind) {
                case "圖片":
                    return "圖";
                case "原始碼":
                    return "</>";
                case "資料夾":
                    return "夾";
                case "文字檔":
                    return "文";
                case "PDF":
                    return "PDF";
                case "壓縮檔":
                    return "ZIP";
                default:
                    return "檔";
            }
        }
    }

    private static final class RemoveButton extends JButton {
        private RemoveButton() {
            setToolTipText("移除");
            Dimension box = new Dimension(28, 28);
            setPreferredSize(box);
            setMinimumSize(box);
            setMaximumSize(box);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
            putClientProperty(CandyButton.MARK, Boolean.TRUE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            int s = 22;
            int x = (getWidth() - s) / 2;
            int y = (getHeight() - s) / 2;
            if (getModel().isRollover() || getModel().isPressed()) {
                g2.setColor(getModel().isPressed() ? CandyTheme.PINK_DEEP : new Color(0x6A, 0x48, 0x88));
                g2.fillOval(x, y, s, s);
            }
            g2.setColor(CandyTheme.INK);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int pad = 7;
            g2.drawLine(x + pad, y + pad, x + s - pad, y + s - pad);
            g2.drawLine(x + s - pad, y + pad, x + pad, y + s - pad);
            g2.dispose();
        }
    }

    private static final class CloseButton extends JButton {
        private CloseButton() {
            setToolTipText("關閉");
            Dimension box = new Dimension(28, 28);
            setPreferredSize(box);
            setMinimumSize(box);
            setMaximumSize(box);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
            putClientProperty(CandyButton.MARK, Boolean.TRUE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            int s = 22;
            int x = (getWidth() - s) / 2;
            int y = (getHeight() - s) / 2;
            if (getModel().isRollover() || getModel().isPressed()) {
                g2.setColor(getModel().isPressed() ? new Color(0xC4, 0x45, 0x58) : new Color(0xE1, 0x5B, 0x6A));
                g2.fillOval(x, y, s, s);
            }
            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(getModel().isRollover() ? Color.WHITE : CandyTheme.INK);
            int pad = 7;
            g2.drawLine(x + pad, y + pad, x + s - pad, y + s - pad);
            g2.drawLine(x + s - pad, y + pad, x + pad, y + s - pad);
            g2.dispose();
        }
    }

    private static final class DotIcon implements javax.swing.Icon {
        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            g2.setColor(CandyTheme.PINK);
            g2.fillOval(x, y, 12, 12);
            g2.setColor(new Color(255, 255, 255, 140));
            g2.fillOval(x + 3, y + 3, 4, 4);
            g2.dispose();
        }
    }

    private static final class HintField extends CandyTextField {
        private final String hint;

        private HintField(String hint) {
            super("", 16);
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText() != null && !getText().isEmpty()) {
                return;
            }
            if (isFocusOwner()) {
                return;
            }
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            g2.setFont(getFont());
            g2.setColor(CandyTheme.DISABLED);
            java.awt.FontMetrics metrics = g2.getFontMetrics();
            Insets insets = getInsets();
            int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.drawString(hint, insets.left, y);
            g2.dispose();
        }
    }

    /** 呼叫端傳入的一位真實好友，不在這裡補假資料。 */
    public static final class Friend {
        public final String clientId;
        public final boolean online;
        public final String appVersion;
        public final String avatarEncoded;

        public Friend(String clientId, boolean online, String appVersion, String avatarEncoded) {
            this.clientId = clientId == null ? "" : clientId.trim();
            this.online = online;
            this.appVersion = appVersion == null ? "" : appVersion.trim();
            this.avatarEncoded = avatarEncoded == null ? "" : avatarEncoded.trim();
        }
    }

    /** 使用者按傳送後的結果。取消時不會產生。 */
    public static final class Result {
        private final String clientId;
        private final List<Path> files;
        private final String note;

        Result(String clientId, List<Path> files, String note) {
            this.clientId = clientId;
            this.files = List.copyOf(files);
            this.note = note == null ? "" : note;
        }

        public String clientId() {
            return clientId;
        }

        public List<Path> files() {
            return files;
        }

        /** 選填訊息。空白表示不要另傳訊息。 */
        public String note() {
            return note;
        }
    }
}
