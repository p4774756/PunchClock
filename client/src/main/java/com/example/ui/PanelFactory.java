package com.example.ui;

import com.example.model.WorkSlot;
import com.example.service.CheckInHistoryService;
import com.example.service.PeerAvatar;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * UI 面板工廠 — 負責建構各區塊的 Swing 面板
 * 將 UI 佈局邏輯從 App 主類別中抽離，減少構造函式臃腫度
 */
public class PanelFactory {

    /** 裝置互動分頁／面板標題 */
    public static final String PEER_TAB_LABEL = "好友與傳檔";

    /** 設定分頁的「連線」子分頁；雲端欄位在這一頁。 */
    public static final int SETTINGS_CONNECTION_TAB = 0;

    // ==================== 分組 1: 設定 ====================

    /**
     * 設定頁的兩個子分頁：連線（雲端連線與程式更新）、中繼。
     * 呼叫端需透過 refs 取得元件引用。
     */
    public static JTabbedPane createServerConfigBody(ServerConfigRefs refs, Font mainFont, Font boldFont, Font fieldFont) {
        refs.heartbeatTokenField = new JPasswordField("punchclock-dev-secret");
        refs.heartbeatTokenField.setFont(fieldFont);
        refs.heartbeatTokenField.setColumns(18);
        refs.heartbeatTokenField.setToolTipText("與伺服器約定的認證 Token，需與後端設定一致；雲端連線中會鎖定，請先取消「啟用雲端」再修改");

        refs.serverUrlCombo = RecentValuesHelper.createCombo(
                fieldFont, "http://localhost:3000",
                "心跳伺服器網址，例如 https://xxx.onrender.com；雲端連線中會鎖定，請先取消「啟用雲端」再修改");
        refs.serverUrlCombo.setPrototypeDisplayValue("http://localhost:3000");

        lockFieldHeight(refs.heartbeatTokenField);
        lockFieldHeight(refs.serverUrlCombo);

        JLabel tokenLabel = new JLabel("心跳 Token：");
        tokenLabel.setFont(mainFont);
        JLabel serverUrlLabel = new JLabel("Server 雲端網址：");
        serverUrlLabel.setFont(mainFont);

        refs.enableServerCheckBox = new JCheckBox("啟用雲端單向狀態回報", false);
        refs.enableServerCheckBox.setFont(boldFont);
        refs.enableServerCheckBox.setToolTipText("開啟後定期回報本機執行狀態到雲端 Dashboard");
        refs.trustAllSslCheckBox = new JCheckBox("信任所有 SSL（除錯）", false);
        refs.trustAllSslCheckBox.setFont(mainFont);
        refs.trustAllSslCheckBox.setToolTipText("預設關閉。啟用雲端時會鎖定；需先取消雲端才能修改。僅本機自簽憑證除錯用。");
        refs.checkForUpdatesCheckBox = new JCheckBox("自動檢查更新", true);
        refs.checkForUpdatesCheckBox.setFont(mainFont);
        refs.checkForUpdatesCheckBox.setToolTipText(
                "關閉後，心跳不再處理伺服器上的新版，也不會下載或詢問。"
                        + "開啟後，再由「有新版時閒置自動更新」決定直接安裝或先詢問。");
        refs.autoUpdateCheckBox = new JCheckBox("有新版時閒置自動更新", true);
        refs.autoUpdateCheckBox.setFont(mainFont);
        refs.autoUpdateCheckBox.setToolTipText(
                "伺服器有不同的桌面端時，在沒有任務執行中、10 分鐘內沒有排程、沒有傳檔時自動下載並重啟（排程與設定會保留）。"
                        + "關閉則改為跳出詢問。需先開啟「自動檢查更新」。");
        refs.heartbeatStatusLabel = new JLabel("[離線] 未連線 (已停用)", SwingConstants.LEFT);
        refs.heartbeatStatusLabel.setFont(boldFont);
        refs.heartbeatStatusLabel.setForeground(NightTheme.MUTED);
        refs.heartbeatStatusLabel.setBorder(new EmptyBorder(0, 12, 0, 12));

        JPanel cloud = new JPanel();
        cloud.setOpaque(false);
        cloud.setLayout(new BoxLayout(cloud, BoxLayout.Y_AXIS));
        cloud.add(formRow(tokenLabel, refs.heartbeatTokenField));
        cloud.add(Box.createVerticalStrut(10));
        cloud.add(formRowStretch(serverUrlLabel, refs.serverUrlCombo));
        cloud.add(Box.createVerticalStrut(10));
        cloud.add(formRow(
                refs.enableServerCheckBox,
                refs.trustAllSslCheckBox,
                refs.heartbeatStatusLabel));

        JPanel updates = new JPanel();
        updates.setOpaque(false);
        updates.setLayout(new BoxLayout(updates, BoxLayout.Y_AXIS));
        updates.add(formRow(refs.checkForUpdatesCheckBox, refs.autoUpdateCheckBox));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UiFonts.chineseBold(13));
        tabs.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        tabs.setUI(new BasicTabbedPaneUI());
        tabs.setOpaque(false);
        tabs.setBackground(new Color(0, 0, 0, 0));
        tabs.addTab("連線", settingsPage(
                settingsSection("雲端連線", boldFont, cloud),
                settingsSection("程式更新", boldFont, updates)));
        tabs.addTab("中繼", settingsPage(
                settingsSection("中繼 Proxy", boldFont, relayProxyRow(refs, mainFont, boldFont))));
        tabs.setToolTipTextAt(SETTINGS_CONNECTION_TAB, "雲端連線與程式更新");
        tabs.setToolTipTextAt(1, "讓區網內的同事經由這台電腦連到雲端");
        tabs.setSelectedIndex(SETTINGS_CONNECTION_TAB);
        return tabs;
    }

    /** 子分頁內容貼在上方，下方留白，背景圖才透得出來。 */
    private static JPanel settingsPage(JComponent... sections) {
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBorder(new EmptyBorder(4, 4, 12, 8));
        for (JComponent section : sections) {
            stack.add(section);
        }
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(stack, BorderLayout.NORTH);
        return page;
    }

    /** 小標題加一條分割線，下面接該段的控制項。 */
    private static JPanel settingsSection(String title, Font titleFont, JComponent body) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(new EmptyBorder(4, 2, 18, 2));

        JLabel heading = new JLabel(title);
        heading.setFont(titleFont);
        heading.setForeground(NightTheme.INK);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSeparator line = new JSeparator();
        line.setForeground(NightTheme.EDGE);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(heading);
        section.add(Box.createVerticalStrut(4));
        section.add(line);
        section.add(Box.createVerticalStrut(8));
        section.add(body);
        return section;
    }

    private static JPanel relayProxyRow(ServerConfigRefs refs, Font mainFont, Font boldFont) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);

        refs.relayEnabledCheckBox = new JCheckBox("啟用中繼 Proxy", false);
        refs.relayEnabledCheckBox.setFont(boldFont);
        refs.relayEnabledCheckBox.setOpaque(false);
        refs.relayEnabledCheckBox.setToolTipText("讓區網內連不到 Server 的同事，把 Server 網址設成你的 IP，透過你連到雲端");

        JLabel portLabel = new JLabel("埠號：");
        portLabel.setFont(mainFont);

        refs.relayPortSpinner = new JSpinner(new SpinnerNumberModel(8888, 1024, 65535, 1));
        refs.relayPortSpinner.setFont(mainFont);
        refs.relayPortSpinner.setToolTipText("中繼服務監聽的埠號（預設 8888）");
        JComponent editor = refs.relayPortSpinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            ((JSpinner.DefaultEditor) editor).getTextField().setColumns(5);
        }

        refs.relayStatusLabel = new JLabel("未啟動");
        refs.relayStatusLabel.setFont(mainFont);
        refs.relayStatusLabel.setForeground(NightTheme.MUTED);

        top.add(refs.relayEnabledCheckBox);
        top.add(portLabel);
        top.add(refs.relayPortSpinner);
        top.add(refs.relayStatusLabel);

        refs.relayHelpArea = new JTextArea();
        refs.relayHelpArea.setEditable(false);
        refs.relayHelpArea.setLineWrap(true);
        refs.relayHelpArea.setWrapStyleWord(true);
        refs.relayHelpArea.setFont(UiFonts.chinesePlain(12));
        refs.relayHelpArea.setOpaque(true);
        refs.relayHelpArea.setBackground(new Color(248, 250, 252, 140));
        refs.relayHelpArea.setForeground(NightTheme.MUTED);
        refs.relayHelpArea.setMargin(new java.awt.Insets(6, 8, 6, 8));
        refs.relayHelpArea.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225, 160)));
        refs.relayHelpArea.setText(""
                + "同事連不到 Server，但你可以？試試中繼 Proxy！\n"
                + "啟用後，同事把「Server 雲端網址」改成 http://你的IP:8888\n"
                + "你的 App 會幫同事把請求轉發到真正的 Server。");
        fitRelayHelp(refs.relayHelpArea);

        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        refs.relayHelpArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(top);
        column.add(Box.createVerticalStrut(4));
        column.add(refs.relayHelpArea);

        return column;
    }

    /** 說明只有幾行，依內容撐高，不另開捲動。 */
    public static void fitRelayHelp(JTextArea area) {
        if (area == null) {
            return;
        }
        String text = area.getText();
        int lines = 1;
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    lines++;
                }
            }
        }
        area.setRows(lines);
        Dimension pref = area.getPreferredSize();
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    }

    public static void setBackgroundEffectControlsEnabled(ServerConfigRefs refs, boolean enabled) {
        if (refs == null) {
            return;
        }
        if (refs.backgroundBlurSlider != null) {
            refs.backgroundBlurSlider.setEnabled(enabled);
        }
        if (refs.backgroundOpacitySlider != null) {
            refs.backgroundOpacitySlider.setEnabled(enabled);
        }
    }

    /** 設定分頁的元件引用容器 */
    public static class ServerConfigRefs {
        public JComboBox<String> serverUrlCombo;
        public JPasswordField heartbeatTokenField;
        public JCheckBox enableServerCheckBox;
        public JCheckBox trustAllSslCheckBox;
        public JCheckBox checkForUpdatesCheckBox;
        public JCheckBox autoUpdateCheckBox;
        public JLabel heartbeatStatusLabel;
        public JLabel backgroundPreview;
        public JButton chooseBackgroundButton;
        public JButton clearBackgroundButton;
        public JSlider backgroundBlurSlider;
        public JLabel backgroundBlurValueLabel;
        public JSlider backgroundOpacitySlider;
        public JLabel backgroundOpacityValueLabel;
        // 中繼 Proxy
        public JCheckBox relayEnabledCheckBox;
        public JSpinner relayPortSpinner;
        public JLabel relayStatusLabel;
        public JTextArea relayHelpArea;
    }

    // ==================== 裝置互動 ====================

    public static JPanel createPeerInteractionPanel(PeerInteractionRefs refs, Font mainFont, Font boldFont) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(4, 0, 0, 0));

        JLabel hint = new JLabel("只顯示同一伺服器上的好友（不含本機，每 15 秒隨心跳更新）");
        hint.setFont(mainFont);
        hint.setForeground(NightTheme.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        refs.peerHintLabel = hint;

        refs.peerTableModel = new javax.swing.table.DefaultTableModel(
                new Object[]{"裝置 ID", "狀態", "等待任務", "版本"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        refs.peerTable = new JTable(refs.peerTableModel);
        refs.peerTable.setFont(mainFont);
        refs.peerTable.setRowHeight(44);
        refs.peerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refs.peerTable.getTableHeader().setFont(boldFont);
        installSelfPeerRowRenderer(refs.peerTable, mainFont, boldFont);

        JScrollPane tableScroll = new JScrollPane(refs.peerTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        tableScroll.setPreferredSize(new Dimension(400, 180));
        refs.peerTableScroll = tableScroll;

        refs.peerStatusLabel = new JLabel("尚未取得好友列表（請先啟用雲端狀態回報）");
        refs.peerStatusLabel.setFont(mainFont);
        refs.peerStatusLabel.setForeground(NightTheme.MUTED);
        refs.peerStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        refs.openCloudSettingsButton = new JButton("前往設定");
        refs.openCloudSettingsButton.setFont(boldFont);
        refs.openCloudSettingsButton.setToolTipText("切換至「設定」的「連線」子分頁以啟用連線");
        refs.openCloudSettingsButton.setVisible(false);
        refs.openCloudSettingsButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        refs.avatarPreview = new JLabel();
        refs.avatarPreview.setPreferredSize(new Dimension(44, 44));
        refs.avatarPreview.setMinimumSize(new Dimension(44, 44));
        refs.avatarPreview.setToolTipText("傳送訊息或戳一下時，對方對話框會顯示這張大頭照");

        refs.chooseAvatarButton = new JButton("選擇大頭照");
        refs.chooseAvatarButton.setFont(boldFont);
        refs.chooseAvatarButton.setToolTipText("從本機選一張照片，對方收到訊息／戳一下時會看到");

        refs.clearAvatarButton = new JButton("還原預設");
        refs.clearAvatarButton.setFont(mainFont);
        refs.clearAvatarButton.setToolTipText("改回使用 App 圖示");

        JPanel avatarRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        avatarRow.setOpaque(false);
        avatarRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel avatarLabel = new JLabel("大頭照：");
        avatarLabel.setFont(mainFont);
        avatarRow.add(avatarLabel);
        avatarRow.add(refs.avatarPreview);
        avatarRow.add(refs.chooseAvatarButton);
        avatarRow.add(refs.clearAvatarButton);
        refs.avatarRow = avatarRow;

        String[] workerOptions = {"company-worker", "company-worker2", "company-worker3", "company-worker4"};
        refs.clientIdCombo = new JComboBox<>(workerOptions);
        refs.clientIdCombo.setEditable(true);
        refs.clientIdCombo.setFont(mainFont);
        refs.clientIdCombo.setPrototypeDisplayValue("company-worker4");
        refs.clientIdCombo.setToolTipText("好友看到的本機名稱。可下拉選擇或自行輸入；雲端連線中會鎖定，請先到設定取消「啟用雲端」再修改");
        // 只設 preferred 時，旁邊的說明會把欄位擠回最小寬度，暱稱開頭會被裁掉。
        lockComponentSize(refs.clientIdCombo, new Dimension(280, 28));
        JPanel hintBlock = new JPanel();
        hintBlock.setOpaque(false);
        hintBlock.setLayout(new BoxLayout(hintBlock, BoxLayout.Y_AXIS));
        hintBlock.add(hint);
        hintBlock.add(Box.createVerticalStrut(2));
        hintBlock.add(refs.peerStatusLabel);
        hintBlock.add(refs.openCloudSettingsButton);

        JPanel idRow = new JPanel(new GridBagLayout());
        idRow.setOpaque(false);
        idRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints idGbc = new GridBagConstraints();
        idGbc.gridy = 0;
        idGbc.anchor = GridBagConstraints.WEST;
        idGbc.insets = new Insets(0, 8, 0, 8);
        idGbc.gridx = 0;
        JLabel idLabel = new JLabel("裝置 ID：");
        idLabel.setFont(mainFont);
        idRow.add(idLabel, idGbc);
        idGbc.gridx = 1;
        idGbc.insets = new Insets(0, 0, 0, 16);
        idRow.add(refs.clientIdCombo, idGbc);
        idGbc.gridx = 2;
        idGbc.weightx = 1.0;
        idGbc.fill = GridBagConstraints.HORIZONTAL;
        idGbc.insets = new Insets(0, 0, 0, 0);
        idRow.add(hintBlock, idGbc);

        refs.openChatButton = new JButton("開啟聊天");
        refs.openChatButton.setFont(boldFont);
        refs.openChatButton.setToolTipText("和選取的好友開一個小聊天視窗，可傳訊息、圖片、檔案或戳一下（雙擊好友也可以）");
        refs.openChatButton.setEnabled(false);
        JLabel chatHint = new JLabel("雙擊好友，或選取後按「開啟聊天」");
        chatHint.setFont(mainFont);
        chatHint.setForeground(NightTheme.MUTED);
        refs.deskChatButton = new JButton("跟後台說話");
        refs.deskChatButton.setFont(boldFont);
        refs.deskChatButton.setToolTipText("開一個小視窗，用文字和網頁後台聊天（約 15 秒內送達）");
        refs.deskChatButton.setEnabled(false);
        JPanel chatRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chatRow.setOpaque(false);
        chatRow.add(refs.openChatButton);
        chatRow.add(refs.deskChatButton);
        chatRow.add(chatHint);

        refs.fileTableModel = new javax.swing.table.DefaultTableModel(
                new Object[]{"方向", "檔名", "對象", "大小", "狀態", "剩餘"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        refs.fileTable = new JTable(refs.fileTableModel);
        refs.fileTable.setFont(mainFont);
        refs.fileTable.setRowHeight(32);
        refs.fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refs.fileTable.getTableHeader().setFont(boldFont);
        refs.fileTable.setToolTipText("過期前可下載；選取一筆後按「下載」或「清除」");

        JScrollPane fileScroll = new JScrollPane(refs.fileTable);
        fileScroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        fileScroll.setPreferredSize(new Dimension(400, 120));
        refs.fileTableScroll = fileScroll;

        refs.fileStatusLabel = new JLabel("傳檔紀錄：尚未連線");
        refs.fileStatusLabel.setFont(mainFont);
        refs.fileStatusLabel.setForeground(NightTheme.MUTED);

        refs.downloadFileButton = new JButton("下載");
        refs.downloadFileButton.setFont(boldFont);
        refs.downloadFileButton.setToolTipText("將選取的暫存檔存到本機（過期前可重複下載）");
        refs.clearFileButton = new JButton("清除");
        refs.clearFileButton.setFont(mainFont);
        refs.clearFileButton.setToolTipText("從伺服器刪除這份暫存，之後無法再下載");

        JPanel fileActionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fileActionRow.setOpaque(false);
        fileActionRow.add(refs.fileStatusLabel);
        fileActionRow.add(refs.downloadFileButton);
        fileActionRow.add(refs.clearFileButton);

        JPanel fileSection = new JPanel(new BorderLayout(0, 4));
        fileSection.setOpaque(false);
        JLabel fileTitle = new JLabel("傳檔紀錄（保留 " + com.example.PeerFileRules.OFFER_TTL_LABEL + "，過期前可下載／手動清除）");
        fileTitle.setFont(boldFont);
        fileTitle.setForeground(NightTheme.INK);
        fileSection.add(fileTitle, BorderLayout.NORTH);
        fileSection.add(fileScroll, BorderLayout.CENTER);
        fileSection.add(fileActionRow, BorderLayout.SOUTH);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(chatRow, BorderLayout.NORTH);
        south.add(fileSection, BorderLayout.CENTER);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        north.add(idRow);
        north.add(Box.createVerticalStrut(8));
        north.add(avatarRow);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(tableScroll, BorderLayout.CENTER);
        center.add(south, BorderLayout.SOUTH);

        panel.add(north, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    /** 好友列：圓形頭像、在線點、滑過變色。裝置 ID 仍放在第一欄的文字，選取邏輯不變。 */
    private static void installSelfPeerRowRenderer(JTable table, Font mainFont, Font boldFont) {
        Color selfBg = new Color(78, 58, 128);
        Color hoverBg = new Color(72, 56, 122);
        java.util.Map<String, Icon> faces = new java.util.HashMap<>();
        javax.swing.table.DefaultTableCellRenderer renderer = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                boolean self = isSelfPeerRow(tbl, row);
                boolean hover = !isSelected && row == hoverRow(tbl);
                if (c instanceof JLabel) {
                    JLabel label = (JLabel) c;
                    label.setFont(self && column == 0 ? boldFont : mainFont);
                    label.setBorder(new EmptyBorder(4, column == 0 ? 8 : 6, 4, 4));
                    label.setIcon(column == 0 ? friendFace(tbl, row, faces) : null);
                    label.setIconTextGap(8);
                }
                if (isSelected) {
                    c.setBackground(NightTheme.SELECT);
                    c.setForeground(NightTheme.PINK_INK);
                } else if (hover) {
                    c.setBackground(hoverBg);
                    c.setForeground(NightTheme.INK);
                } else if (self) {
                    c.setBackground(selfBg);
                    c.setForeground(NightTheme.INK);
                } else {
                    c.setBackground(tbl.getBackground());
                    c.setForeground(tbl.getForeground());
                }
                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(true);
                }
                return c;
            }
        };
        for (int col = 0; col < table.getColumnModel().getColumnCount(); col++) {
            table.getColumnModel().getColumn(col).setCellRenderer(renderer);
        }
        if (table.getClientProperty("peer.hoverInstalled") == null) {
            table.putClientProperty("peer.hoverInstalled", Boolean.TRUE);
            table.putClientProperty("peer.hover", Integer.valueOf(-1));
            java.awt.event.MouseAdapter hover = new java.awt.event.MouseAdapter() {
                @Override
                public void mouseMoved(java.awt.event.MouseEvent e) {
                    setHover(table, table.rowAtPoint(e.getPoint()));
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    setHover(table, -1);
                }
            };
            table.addMouseMotionListener(hover);
            table.addMouseListener(hover);
        }
    }

    private static void setHover(JTable table, int row) {
        Object previous = table.getClientProperty("peer.hover");
        if (previous instanceof Integer && ((Integer) previous).intValue() == row) {
            return;
        }
        table.putClientProperty("peer.hover", Integer.valueOf(row));
        table.repaint();
    }

    private static int hoverRow(JTable table) {
        Object value = table.getClientProperty("peer.hover");
        return value instanceof Integer ? ((Integer) value).intValue() : -1;
    }

    @SuppressWarnings("unchecked")
    private static Icon friendFace(JTable table, int row, java.util.Map<String, Icon> faces) {
        Object idValue = table.getValueAt(row, 0);
        String id = idValue == null ? "" : String.valueOf(idValue);
        boolean online = false;
        if (table.getColumnCount() > 1) {
            Object status = table.getValueAt(row, 1);
            online = status != null && "在線".equals(String.valueOf(status));
        }
        String encoded = "";
        Object stored = table.getClientProperty("peer.avatars");
        if (stored instanceof java.util.Map) {
            Object found = ((java.util.Map<String, String>) stored).get(peerIdOf(id));
            if (found != null) {
                encoded = String.valueOf(found);
            }
        }
        String key = peerIdOf(id) + "|" + online + "|" + encoded.hashCode();
        Icon cached = faces.get(key);
        if (cached != null) {
            return cached;
        }
        Icon created = friendFaceIcon(id, encoded, online);
        faces.put(key, created);
        return created;
    }

    private static String peerIdOf(String displayId) {
        String suffix = "（本機）";
        if (displayId.endsWith(suffix)) {
            return displayId.substring(0, displayId.length() - suffix.length());
        }
        return displayId;
    }

    private static Icon friendFaceIcon(String id, String encoded, boolean online) {
        int size = 28;
        BufferedImage face = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = face.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Icon photo = encoded == null || encoded.isBlank() ? null : PeerAvatar.previewIcon(encoded, null, size);
            if (photo != null && PeerAvatar.decode(encoded) != null) {
                photo.paintIcon(null, g, 0, 0);
            } else {
                g.setColor(faceColor(id));
                g.fillOval(1, 1, size - 2, size - 2);
                g.setColor(NightTheme.INK);
                g.setFont(UiFonts.chineseBold(13));
                String letter = faceLetter(id);
                FontMetrics metrics = g.getFontMetrics();
                int x = (size - metrics.stringWidth(letter)) / 2;
                int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(letter, x, y);
            }
            g.setColor(new Color(28, 20, 48));
            g.fillOval(size - 11, size - 11, 10, 10);
            g.setColor(online ? new Color(120, 210, 150) : new Color(120, 108, 148));
            g.fillOval(size - 9, size - 9, 6, 6);
        } finally {
            g.dispose();
        }
        return new ImageIcon(face);
    }

    private static Color faceColor(String id) {
        int n = Math.floorMod(id.hashCode(), 4);
        if (n == 0) {
            return new Color(186, 112, 164);
        }
        if (n == 1) {
            return new Color(132, 104, 196);
        }
        if (n == 2) {
            return new Color(206, 132, 168);
        }
        return new Color(112, 92, 176);
    }

    private static String faceLetter(String id) {
        String clean = peerIdOf(id).trim();
        if (clean.isEmpty()) {
            return "?";
        }
        int point = clean.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(point)));
    }

    private static boolean isSelfPeerRow(JTable table, int row) {
        if (table == null || row < 0 || row >= table.getRowCount()) {
            return false;
        }
        Object id = table.getValueAt(row, 0);
        return id != null && String.valueOf(id).contains("（本機）");
    }

    /** 裝置互動面板元件引用容器 */
    public static class PeerInteractionRefs {
        public JTable peerTable;
        public javax.swing.table.DefaultTableModel peerTableModel;
        public JScrollPane peerTableScroll;
        public JButton openChatButton;
        public JButton deskChatButton;
        public JButton downloadFileButton;
        public JButton clearFileButton;
        public JTable fileTable;
        public javax.swing.table.DefaultTableModel fileTableModel;
        public JScrollPane fileTableScroll;
        public JLabel fileStatusLabel;
        public JButton openCloudSettingsButton;
        public JLabel peerHintLabel;
        public JLabel peerStatusLabel;
        public JComboBox<String> clientIdCombo;
        public JLabel avatarPreview;
        public JButton chooseAvatarButton;
        public JButton clearAvatarButton;
        public JPanel avatarRow;
    }

    // ==================== 分組 2: 上班排程 ====================

    public static JPanel createSlotPanel(SlotPanelRefs refs, Font mainFont, Font boldFont, Font fieldFont) {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(2, 2, 4, 2));

        JPanel sharedContent = new JPanel();
        sharedContent.setOpaque(false);
        sharedContent.setLayout(new BoxLayout(sharedContent, BoxLayout.Y_AXIS));

        refs.urlCombo = RecentValuesHelper.createCombo(
                fieldFont, "https://www.msn.com/zh-tw",
                "目標頁面的完整網址；已啟用槽位會使用啟用當下鎖定的值，此欄位供下次啟用或「立即執行」");
        refs.urlCombo.setPrototypeDisplayValue("https://www.msn.com/zh-tw");
        refs.buttonIdCombo = RecentValuesHelper.createCombo(
                fieldFont, "finance",
                "要點擊的按鈕 CSS Selector 或 id；已啟用槽位使用啟用當下鎖定的值");
        refs.buttonIdCombo.setPrototypeDisplayValue("#finance");
        refs.browserCombo = new JComboBox<>(TaskEditDialog.BROWSER_OPTIONS);
        refs.browserCombo.setFont(fieldFont);
        refs.browserCombo.setPrototypeDisplayValue("Chromium（內建）");
        TaskEditDialog.attachBrowserTooltips(refs.browserCombo);
        refs.executeNowButton = new JButton("立即執行");
        refs.executeNowButton.setFont(boldFont);
        refs.executeNowButton.setToolTipText("使用上方共用設定立即測試，不會變更已啟用槽位的鎖定設定");

        lockFieldHeight(refs.urlCombo);
        sharedContent.add(formRowStretch(labeled(mainFont, "目標網址："), refs.urlCombo));
        sharedContent.add(Box.createVerticalStrut(14));
        sharedContent.add(selectorBrowserActionRow(mainFont, fieldFont,
                refs.buttonIdCombo, refs.browserCombo, refs.executeNowButton));

        JPanel shared = createCollapsibleGroupPanel("共用排程設定", sharedContent, boldFont, false);

        refs.skipDatesButton = new JButton("跳過日期");
        refs.skipDatesButton.setFont(boldFont);
        refs.skipDatesButton.setToolTipText("指定日期不執行；時分維持原設定");

        JPanel skipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        skipRow.setOpaque(false);
        skipRow.add(refs.skipDatesButton);

        refs.workIn = createSlotCard(WorkSlot.WORK_IN_NAME, mainFont, boldFont);

        GridBagConstraints rootGbc = new GridBagConstraints();
        rootGbc.gridx = 0;
        rootGbc.weightx = 1.0;
        rootGbc.fill = GridBagConstraints.HORIZONTAL;
        rootGbc.insets = new Insets(0, 0, 12, 0);

        rootGbc.gridy = 0;
        root.add(shared, rootGbc);
        rootGbc.gridy = 1;
        rootGbc.insets = new Insets(4, 0, 12, 0);
        root.add(skipRow, rootGbc);
        rootGbc.gridy = 2;
        root.add(refs.workIn.panel, rootGbc);

        return root;
    }

    /**
     * 打卡歷史簡易列表（最近 5～10 筆），放在排程卡片下方。
     */
    public static JPanel createCheckInHistoryPanel(
            CheckInHistoryRefs refs, Font mainFont, Font boldFont) {
        JPanel panel = createGroupPanel(
                "執行記錄（最近 " + CheckInHistoryService.MAX_ENTRIES + " 筆）", boldFont);
        panel.setLayout(new BorderLayout(0, 10));

        refs.listModel = new DefaultListModel<>();
        refs.historyList = new JList<>(refs.listModel);
        refs.historyList.setFont(mainFont);
        refs.historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refs.historyList.setVisibleRowCount(6);
        refs.historyList.setFixedCellHeight(28);
        refs.historyList.setOpaque(false);
        refs.historyList.setBackground(new Color(0, 0, 0, 0));
        refs.historyList.setForeground(NightTheme.INK);
        refs.historyList.setBorder(new EmptyBorder(4, 6, 4, 6));
        refs.historyList.setToolTipText("本機最近排程成功／失敗紀錄（不含取消）");
        refs.historyList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                boolean latest = index == 0;
                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(isSelected || latest);
                }
                if (isSelected) {
                    c.setBackground(NightTheme.SELECT);
                    c.setForeground(NightTheme.PINK_INK);
                } else if (latest) {
                    c.setBackground(new Color(78, 58, 128));
                    c.setForeground(NightTheme.INK);
                    c.setFont(list.getFont().deriveFont(Font.BOLD));
                } else {
                    c.setBackground(NightTheme.FIELD);
                    c.setForeground(NightTheme.INK);
                    c.setFont(list.getFont());
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(refs.historyList);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225, 160)));
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        // 列文字很長時只在這塊左右捲，不要把整頁撐寬、撐高。
        scroll.setPreferredSize(new Dimension(120, refs.historyList.getFixedCellHeight() * 2));
        scroll.setMinimumSize(new Dimension(80, refs.historyList.getFixedCellHeight() * 2));
        panel.add(scroll, BorderLayout.CENTER);

        refs.emptyLabel = new JLabel("尚無執行紀錄；排程或「立即執行」完成後會顯示在此", SwingConstants.CENTER);
        refs.emptyLabel.setFont(mainFont);
        refs.emptyLabel.setForeground(NightTheme.MUTED);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.setBorder(new EmptyBorder(6, 0, 4, 0));
        south.add(refs.emptyLabel, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        refs.clearButton = new JButton("清除紀錄");
        refs.clearButton.setFont(boldFont);
        refs.clearButton.setToolTipText("清空本機執行歷史（不影響目前排程）");
        actions.add(refs.clearButton);
        south.add(actions, BorderLayout.EAST);
        panel.add(south, BorderLayout.SOUTH);

        return panel;
    }

    public static class CheckInHistoryRefs {
        public DefaultListModel<String> listModel;
        public JList<String> historyList;
        public JLabel emptyLabel;
        public JButton clearButton;
    }

    private static SlotCardRefs createSlotCard(String title, Font mainFont, Font boldFont) {
        SlotCardRefs refs = new SlotCardRefs();
        refs.panel = translucentPanel();
        refs.panel.setLayout(new BorderLayout(0, 4));
        refs.panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(NightTheme.EDGE, 1, true),
                new EmptyBorder(12, 12, 16, 12)));

        refs.nameField = new JTextField(title, 16);
        refs.nameField.setFont(boldFont);
        refs.nameField.setToolTipText("這個排程的名稱。改完後，本機和後台的任務卡都會顯示這個名字。最多 24 個字。");
        lockFieldHeight(refs.nameField);

        JLabel nameLabel = new JLabel("名稱");
        nameLabel.setFont(boldFont);
        nameLabel.setForeground(NightTheme.INK);
        JPanel nameRow = new JPanel(new GridBagLayout());
        nameRow.setOpaque(false);
        GridBagConstraints nameGbc = new GridBagConstraints();
        nameGbc.gridx = 0;
        nameGbc.anchor = GridBagConstraints.WEST;
        nameGbc.insets = new Insets(0, 0, 0, 8);
        nameRow.add(nameLabel, nameGbc);
        nameGbc.gridx = 1;
        nameGbc.weightx = 1.0;
        nameGbc.fill = GridBagConstraints.HORIZONTAL;
        nameRow.add(refs.nameField, nameGbc);

        JPanel settings = new JPanel(new GridBagLayout());
        settings.setOpaque(false);
        GridBagConstraints sgbc = new GridBagConstraints();
        sgbc.anchor = GridBagConstraints.CENTER;
        sgbc.insets = new Insets(4, 2, 4, 8);

        refs.enabledCheckBox = new JCheckBox("啟用", true);
        refs.enabledCheckBox.setOpaque(false);
        refs.enabledCheckBox.setFont(boldFont);
        refs.enabledCheckBox.setToolTipText("勾選後開始自動排程，並鎖定時分與目標網址／Selector；取消勾選後才能修改時間");

        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format("%02d", i);
        refs.hourCombo = new JComboBox<>(hours);
        refs.hourCombo.setFont(mainFont);
        refs.hourCombo.setPrototypeDisplayValue("00");
        refs.hourCombo.setToolTipText("預定執行的小時（00–23）");
        lockComboSize(refs.hourCombo);

        String[] minutes = new String[60];
        for (int i = 0; i < 60; i++) minutes[i] = String.format("%02d", i);
        refs.minuteCombo = new JComboBox<>(minutes);
        refs.minuteCombo.setFont(mainFont);
        refs.minuteCombo.setPrototypeDisplayValue("00");
        refs.minuteCombo.setToolTipText("預定執行的分鐘（00–59）");
        lockComboSize(refs.minuteCombo);

        refs.randomOffsetCheckBox = new JCheckBox("±5 分隨機", true);
        refs.randomOffsetCheckBox.setOpaque(false);
        refs.randomOffsetCheckBox.setFont(mainFont);
        refs.randomOffsetCheckBox.setForeground(NightTheme.PINK);
        refs.randomOffsetCheckBox.setToolTipText("在設定時間前後隨機 ±5 分鐘，避免每天固定同一秒執行");

        sgbc.gridx = 0;
        sgbc.gridy = 0;
        sgbc.gridwidth = 1;
        sgbc.weightx = 0.0;
        sgbc.fill = GridBagConstraints.NONE;
        settings.add(refs.enabledCheckBox, sgbc);
        sgbc.gridx = 1;
        settings.add(refs.hourCombo, sgbc);
        sgbc.gridx = 2;
        JLabel hourUnit = new JLabel("時");
        hourUnit.setFont(mainFont);
        settings.add(hourUnit, sgbc);
        sgbc.gridx = 3;
        settings.add(refs.minuteCombo, sgbc);
        sgbc.gridx = 4;
        JLabel minUnit = new JLabel("分");
        minUnit.setFont(mainFont);
        settings.add(minUnit, sgbc);
        sgbc.gridx = 5;
        settings.add(refs.randomOffsetCheckBox, sgbc);
        sgbc.gridx = 6;
        sgbc.weightx = 1.0;
        sgbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel timeSpacer = new JPanel();
        timeSpacer.setOpaque(false);
        settings.add(timeSpacer, sgbc);
        sgbc.weightx = 0.0;
        sgbc.fill = GridBagConstraints.NONE;

        refs.statusLabel = createSlotMetricLabel(mainFont, "—");
        refs.countdownLabel = createSlotMetricLabel(mainFont, "—");
        refs.triggerLabel = createSlotMetricLabel(mainFont, "—");
        refs.resultLabel = createSlotMetricLabel(mainFont, "—");
        refs.lockedSettingsLabel = createSlotMetricLabel(mainFont, "—");

        JPanel statusGrid = new JPanel(new GridBagLayout());
        statusGrid.setOpaque(false);
        GridBagConstraints mgbc = new GridBagConstraints();
        mgbc.anchor = GridBagConstraints.WEST;
        mgbc.fill = GridBagConstraints.HORIZONTAL;
        mgbc.insets = new Insets(6, 0, 6, 12);
        mgbc.weightx = 0.25;
        mgbc.gridy = 0;
        mgbc.gridwidth = 1;
        mgbc.gridx = 0;
        statusGrid.add(createMetricCell("狀態", refs.statusLabel, mainFont), mgbc);
        mgbc.gridx = 1;
        statusGrid.add(createMetricCell("倒數", refs.countdownLabel, mainFont), mgbc);
        mgbc.gridx = 2;
        statusGrid.add(createMetricCell("預計觸發", refs.triggerLabel, mainFont), mgbc);
        mgbc.gridx = 3;
        mgbc.insets = new Insets(2, 0, 2, 0);
        statusGrid.add(createMetricCell("結果", refs.resultLabel, mainFont), mgbc);
        mgbc.gridy = 1;
        mgbc.gridx = 0;
        mgbc.gridwidth = 4;
        mgbc.weightx = 1.0;
        mgbc.insets = new Insets(2, 0, 2, 0);
        statusGrid.add(createMetricCell("鎖定設定", refs.lockedSettingsLabel, mainFont), mgbc);

        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, nameRow.getPreferredSize().height));
        settings.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.setMaximumSize(new Dimension(Integer.MAX_VALUE, settings.getPreferredSize().height));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(nameRow);
        heading.add(Box.createVerticalStrut(8));
        heading.add(settings);

        JPanel mainCol = new JPanel(new BorderLayout(0, 12));
        mainCol.setOpaque(false);
        mainCol.add(heading, BorderLayout.NORTH);
        mainCol.add(statusGrid, BorderLayout.CENTER);

        refs.panel.add(mainCol, BorderLayout.CENTER);
        return refs;
    }

    private static JLabel createSlotMetricLabel(Font font, String text) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    private static JPanel createMetricCell(String title, JLabel value, Font font) {
        JPanel cell = new JPanel(new BorderLayout(0, 0));
        cell.setOpaque(false);
        cell.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        titleLabel.setFont(new Font(font.getName(), Font.BOLD, 12));
        titleLabel.setForeground(NightTheme.MUTED);
        value.setFont(new Font(font.getName(), Font.PLAIN, 13));
        value.setHorizontalAlignment(SwingConstants.LEFT);
        cell.add(titleLabel, BorderLayout.NORTH);
        cell.add(value, BorderLayout.CENTER);
        return cell;
    }

    public static class SlotPanelRefs {
        public SlotCardRefs workIn;
        public JComboBox<String> urlCombo;
        public JComboBox<String> buttonIdCombo;
        public JComboBox<String> browserCombo;
        public JButton executeNowButton;
        public JButton skipDatesButton;
    }

    public static class SlotCardRefs {
        public JPanel panel;
        public JTextField nameField;
        public JCheckBox enabledCheckBox;
        public JComboBox<String> hourCombo;
        public JComboBox<String> minuteCombo;
        public JCheckBox randomOffsetCheckBox;
        public JLabel statusLabel;
        public JLabel countdownLabel;
        public JLabel triggerLabel;
        public JLabel resultLabel;
        public JLabel lockedSettingsLabel;
    }

    // ==================== 分組 3: 系統日誌 ====================

    /**
     * 建立系統日誌面板
     */
    public static JPanel createLogPanel(LogPanelRefs refs, Font mainFont, Font boldFont) {
        JPanel logPanel = createGroupPanel("系統日誌 (Console Log)", boldFont);
        logPanel.setLayout(new BorderLayout(0, 4));

        refs.logTextArea = new JTextArea();
        refs.logTextArea.setEditable(false);
        refs.logTextArea.setLineWrap(true);
        refs.logTextArea.setWrapStyleWord(true);
        refs.logTextArea.setBackground(new Color(15, 23, 42));
        refs.logTextArea.setForeground(new Color(56, 189, 248));
        refs.logTextArea.setCaretColor(Color.WHITE);
        refs.logTextArea.setFont(UiFonts.latinPlain(12));
        refs.logTextArea.setMargin(new Insets(6, 8, 6, 8));

        JScrollPane scrollPane = new JScrollPane(refs.logTextArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        logPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel logActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        refs.clearLogButton = new JButton("清除 Log");
        refs.clearLogButton.setFont(boldFont);
        refs.clearLogButton.setToolTipText("清空日誌內容");
        refs.clearLogButton.addActionListener(e -> refs.logTextArea.setText(""));
        logActionPanel.add(refs.clearLogButton);
        logPanel.add(logActionPanel, BorderLayout.SOUTH);

        return logPanel;
    }

    /** 日誌面板的元件引用容器 */
    public static class LogPanelRefs {
        public JTextArea logTextArea;
        public JButton clearLogButton;
    }

    /** FlowLayout 列：只鎖定高度，避免被 BoxLayout 壓扁 */
    private static JPanel formRow(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Component component : components) {
            row.add(component);
        }
        int h = Math.max(row.getPreferredSize().height, 36);
        row.setMinimumSize(new Dimension(0, h));
        row.setPreferredSize(new Dimension(row.getPreferredSize().width, h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return row;
    }

    /** Selector + 瀏覽器 + 立即執行同一列；macOS 原生 ComboBox 需 BasicComboBoxUI 才與 JTextField 對齊 */
    private static JPanel selectorBrowserActionRow(
            Font labelFont, Font fieldFont,
            JComponent selectorField, JComboBox<?> browserCombo, JButton executeButton) {
        final int rowH = 28;
        final int gap = 8;

        selectorField.setFont(fieldFont);
        browserCombo.setFont(fieldFont);
        if (!(selectorField instanceof JComboBox)) {
            selectorField.setFont(fieldFont);
        }
        browserCombo.setFont(fieldFont);
        useTextFieldAlignedCombo(browserCombo);

        Dimension selectorDim = new Dimension(240, rowH);
        lockComponentSize(selectorField, selectorDim);

        int browserW = Math.max(browserCombo.getPreferredSize().width, 148);
        lockComponentSize(browserCombo, new Dimension(browserW, rowH));

        Dimension btnDim = new Dimension(96, rowH);
        lockComponentSize(executeButton, btnDim);
        executeButton.setMargin(new Insets(0, 8, 0, 8));

        JLabel selectorLabel = labeled(labelFont, "Selector：");
        JLabel browserLabel = labeled(labelFont, "瀏覽器：");

        FlowLayout flow = new FlowLayout(FlowLayout.LEFT, gap, 0);
        flow.setAlignOnBaseline(true);
        JPanel content = new JPanel(flow);
        content.setOpaque(false);
        content.add(selectorLabel);
        content.add(selectorField);
        content.add(browserLabel);
        content.add(browserCombo);
        content.add(executeButton);

        JPanel row = new JPanel(new BorderLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setOpaque(false);
        row.add(content, BorderLayout.WEST);

        int h = rowH + 4;
        row.setMinimumSize(new Dimension(0, h));
        row.setPreferredSize(new Dimension(row.getPreferredSize().width, h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return row;
    }

    /** 與 JTextField 同列時，避免 macOS Aqua ComboBox 視覺偏高，同時保留較自然的外觀 */
    private static void useTextFieldAlignedCombo(JComboBox<?> combo) {
        combo.setOpaque(true);
        combo.setBackground(Color.WHITE);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(0, 6, 0, 4)));
        combo.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton button = new JButton("\u25BE");
                button.setFont(UiFonts.latinPlain(11));
                button.setFocusable(false);
                button.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(226, 232, 240)));
                button.setContentAreaFilled(true);
                button.setBackground(new Color(248, 250, 252));
                button.setForeground(new Color(51, 65, 85));
                button.setMargin(new Insets(0, 0, 0, 0));
                return button;
            }
        });
    }

    private static void lockComponentSize(JComponent component, Dimension size) {
        component.setPreferredSize(size);
        component.setMinimumSize(size);
        component.setMaximumSize(size);
    }

    /** 標籤 + 可拉寬輸入欄（網址列） */
    private static JPanel formRowStretch(JComponent label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(8, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        lockFieldHeight(field);
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        int h = Math.max(28, Math.max(label.getPreferredSize().height, field.getPreferredSize().height) + 8);
        row.setMinimumSize(new Dimension(120, h));
        row.setPreferredSize(new Dimension(400, h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return row;
    }

    /** 鎖定下拉選單尺寸，避免窄欄位擠壓時分選單 */
    private static void lockComboSize(JComboBox<?> combo) {
        Dimension size = combo.getPreferredSize();
        combo.setMinimumSize(size);
        combo.setPreferredSize(size);
        combo.setMaximumSize(size);
    }

    /** 只鎖定高度，寬度交給版面配置 */
    private static void lockFieldHeight(JComponent field) {
        int h = Math.max(field.getPreferredSize().height, 26);
        int w = Math.max(field.getPreferredSize().width, 80);
        field.setMinimumSize(new Dimension(80, h));
        field.setPreferredSize(new Dimension(w, h));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    private static JLabel labeled(Font font, String text) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        return label;
    }

    // ==================== 共用元件 ====================

    /**
     * 建立可折疊的分組面板
     */
    public static JPanel createCollapsibleGroupPanel(String title, JPanel contentPanel, Font titleFont, boolean startCollapsed) {
        JPanel outerPanel = translucentPanel();
        outerPanel.setLayout(new BorderLayout(0, 2));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerPanel.setBackground(NightTheme.HEADER);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(NightTheme.EDGE, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleFont);
        titleLabel.setForeground(NightTheme.INK);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JLabel toggleLabel = new JLabel(startCollapsed ? "► 點擊展開設定" : "▼ 點擊折疊收起");
        toggleLabel.setFont(UiFonts.chineseBold(12));
        toggleLabel.setForeground(NightTheme.PINK);
        headerPanel.add(toggleLabel, BorderLayout.EAST);

        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(10, 12, 10, 12));

        if (startCollapsed) {
            contentPanel.setVisible(false);
        }

        headerPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                boolean visible = !contentPanel.isVisible();
                contentPanel.setVisible(visible);
                toggleLabel.setText(visible ? "▼ 點擊折疊收起" : "► 點擊展開設定");
                SwingUtilities.invokeLater(() -> {
                    outerPanel.revalidate();
                    outerPanel.repaint();
                    Container parent = outerPanel.getParent();
                    if (parent != null) {
                        parent.revalidate();
                        parent.repaint();
                    }
                    Window win = SwingUtilities.getWindowAncestor(outerPanel);
                    if (win != null) {
                        win.revalidate();
                        win.repaint();
                    }
                });
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                headerPanel.setBackground(new Color(72, 56, 122));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                headerPanel.setBackground(NightTheme.HEADER);
            }
        });

        outerPanel.add(headerPanel, BorderLayout.NORTH);
        outerPanel.add(contentPanel, BorderLayout.CENTER);
        return outerPanel;
    }

    /**
     * 建立帶標題框線的分組面板
     */
    public static JPanel createGroupPanel(String title, Font titleFont) {
        JPanel panel = translucentPanel();
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(NightTheme.EDGE, 1, true),
                title, TitledBorder.LEFT, TitledBorder.TOP,
                titleFont, NightTheme.INK);
        panel.setBorder(new CompoundBorder(titledBorder, new EmptyBorder(8, 12, 16, 12)));
        return panel;
    }

    /** 半透明底，讓自訂背景圖能透出一點，同時維持區塊可讀性 */
    private static JPanel translucentPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setBackground(NightTheme.PANEL);
        return panel;
    }
}
