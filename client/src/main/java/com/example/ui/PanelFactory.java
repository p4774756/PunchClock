package com.example.ui;

import com.example.service.CheckInHistoryService;
import com.example.service.WindowBackground;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;

/**
 * UI 面板工廠 — 負責建構各區塊的 Swing 面板
 * 將 UI 佈局邏輯從 App 主類別中抽離，減少構造函式臃腫度
 */
public class PanelFactory {

    /** 裝置互動分頁／面板標題 */
    public static final String PEER_TAB_LABEL = "(o´・ω・`)σ)Д`)";

    // ==================== 分組 1: 雲端服務與裝置設定 ====================

    /**
     * 建立雲端服務設定面板的內容
     * 回傳建構好的 JPanel，呼叫端需透過 refs 參數取得各元件的引用
     */
    public static JPanel createServerConfigBody(ServerConfigRefs refs, Font mainFont, Font boldFont, Font fieldFont) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        String[] workerOptions = { "company-worker", "company-worker2", "company-worker3", "company-worker4" };
        refs.clientIdCombo = new JComboBox<>(workerOptions);
        refs.clientIdCombo.setEditable(true);
        refs.clientIdCombo.setFont(mainFont);
        refs.clientIdCombo.setPrototypeDisplayValue("company-worker4");
        refs.clientIdCombo.setToolTipText("可下拉選擇預設值，或直接輸入自訂 Worker ID；雲端連線中會鎖定，請先取消「啟用雲端」再修改");

        refs.heartbeatTokenField = new JPasswordField("punchclock-dev-secret");
        refs.heartbeatTokenField.setFont(fieldFont);
        refs.heartbeatTokenField.setColumns(18);
        refs.heartbeatTokenField.setToolTipText("與伺服器約定的認證 Token，需與後端設定一致；雲端連線中會鎖定，請先取消「啟用雲端」再修改");

        refs.serverUrlCombo = RecentValuesHelper.createCombo(
                fieldFont, "http://localhost:3000",
                "心跳伺服器網址，例如 https://xxx.onrender.com；雲端連線中會鎖定，請先取消「啟用雲端」再修改");
        refs.serverUrlCombo.setPrototypeDisplayValue("http://localhost:3000");

        lockFieldHeight(refs.clientIdCombo);
        lockFieldHeight(refs.heartbeatTokenField);
        lockFieldHeight(refs.serverUrlCombo);

        JLabel clientIdLabel = new JLabel("裝置 ID / Worker ID：");
        clientIdLabel.setFont(mainFont);
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
        refs.heartbeatStatusLabel = new JLabel("[離線] 未連線 (已停用)", SwingConstants.LEFT);
        refs.heartbeatStatusLabel.setFont(boldFont);
        refs.heartbeatStatusLabel.setForeground(new Color(100, 116, 139));
        refs.heartbeatStatusLabel.setBorder(new EmptyBorder(0, 12, 0, 12));

        panel.add(formRow(clientIdLabel, refs.clientIdCombo, tokenLabel, refs.heartbeatTokenField));
        panel.add(Box.createVerticalStrut(4));
        panel.add(formRowStretch(serverUrlLabel, refs.serverUrlCombo));
        panel.add(Box.createVerticalStrut(4));
        panel.add(formRow(
                refs.enableServerCheckBox,
                refs.trustAllSslCheckBox,
                refs.heartbeatStatusLabel));
        panel.add(Box.createVerticalStrut(8));
        panel.add(backgroundRow(refs, mainFont, boldFont));

        return panel;
    }

    private static JPanel backgroundRow(ServerConfigRefs refs, Font mainFont, Font boldFont) {
        refs.backgroundPreview = new JLabel();
        refs.backgroundPreview.setPreferredSize(new Dimension(
                WindowBackground.PREVIEW_WIDTH, WindowBackground.PREVIEW_HEIGHT));
        refs.backgroundPreview.setMinimumSize(refs.backgroundPreview.getPreferredSize());
        refs.backgroundPreview.setToolTipText("程式視窗背景預覽；實際會等比裁切填滿視窗");
        refs.backgroundPreview.setIcon(WindowBackground.previewIcon(null));

        refs.chooseBackgroundButton = new JButton("選擇背景");
        refs.chooseBackgroundButton.setFont(boldFont);
        refs.chooseBackgroundButton.setToolTipText("選擇 JPG／PNG／GIF 作為程式背景");
        refs.clearBackgroundButton = new JButton("還原預設");
        refs.clearBackgroundButton.setFont(mainFont);
        refs.clearBackgroundButton.setToolTipText("清除自訂背景，還原預設底色");
        refs.clearBackgroundButton.setEnabled(false);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        JLabel label = new JLabel("程式背景：");
        label.setFont(mainFont);
        top.add(label);
        top.add(refs.backgroundPreview);
        top.add(refs.chooseBackgroundButton);
        top.add(refs.clearBackgroundButton);

        refs.backgroundBlurSlider = new JSlider(0, WindowBackground.MAX_BLUR_PERCENT, 0);
        refs.backgroundBlurSlider.setOpaque(false);
        refs.backgroundBlurSlider.setFocusable(false);
        refs.backgroundBlurSlider.setPreferredSize(new Dimension(160, 22));
        refs.backgroundBlurSlider.setToolTipText("背景圖模糊程度；向右越糊，方便閱讀前景文字");
        refs.backgroundBlurValueLabel = new JLabel("0%");
        refs.backgroundBlurValueLabel.setFont(mainFont);
        refs.backgroundBlurValueLabel.setForeground(new Color(100, 116, 139));
        refs.backgroundBlurValueLabel.setPreferredSize(new Dimension(40, 22));

        refs.backgroundOpacitySlider = new JSlider(0, WindowBackground.MAX_OPACITY_PERCENT,
                WindowBackground.DEFAULT_OPACITY_PERCENT);
        refs.backgroundOpacitySlider.setOpaque(false);
        refs.backgroundOpacitySlider.setFocusable(false);
        refs.backgroundOpacitySlider.setPreferredSize(new Dimension(160, 22));
        refs.backgroundOpacitySlider.setToolTipText("背景圖不透明度；向右越清楚，向左越淡");
        refs.backgroundOpacityValueLabel = new JLabel(WindowBackground.DEFAULT_OPACITY_PERCENT + "%");
        refs.backgroundOpacityValueLabel.setFont(mainFont);
        refs.backgroundOpacityValueLabel.setForeground(new Color(100, 116, 139));
        refs.backgroundOpacityValueLabel.setPreferredSize(new Dimension(40, 22));

        JPanel blurRow = sliderRow(mainFont, "模糊：", refs.backgroundBlurSlider, refs.backgroundBlurValueLabel);
        JPanel opacityRow = sliderRow(mainFont, "透明度：", refs.backgroundOpacitySlider, refs.backgroundOpacityValueLabel);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        blurRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        opacityRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(top);
        column.add(Box.createVerticalStrut(4));
        column.add(blurRow);
        column.add(Box.createVerticalStrut(2));
        column.add(opacityRow);
        setBackgroundEffectControlsEnabled(refs, false);
        return column;
    }

    private static JPanel sliderRow(Font font, String title, JSlider slider, JLabel valueLabel) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setFont(font);
        row.add(label);
        row.add(slider);
        row.add(valueLabel);
        return row;
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

    /** 雲端設定面板的元件引用容器 */
    public static class ServerConfigRefs {
        public JComboBox<String> clientIdCombo;
        public JComboBox<String> serverUrlCombo;
        public JPasswordField heartbeatTokenField;
        public JCheckBox enableServerCheckBox;
        public JCheckBox trustAllSslCheckBox;
        public JLabel heartbeatStatusLabel;
        public JLabel backgroundPreview;
        public JButton chooseBackgroundButton;
        public JButton clearBackgroundButton;
        public JSlider backgroundBlurSlider;
        public JLabel backgroundBlurValueLabel;
        public JSlider backgroundOpacitySlider;
        public JLabel backgroundOpacityValueLabel;
    }

    // ==================== 裝置互動 ====================

    public static JPanel createPeerInteractionPanel(PeerInteractionRefs refs, Font mainFont, Font boldFont) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(4, 0, 0, 0));

        JLabel hint = new JLabel("只顯示同一伺服器上的好友（不含本機，每 15 秒隨心跳更新）");
        hint.setFont(mainFont);
        hint.setForeground(new Color(100, 116, 139));
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
        refs.peerTable.setRowHeight(28);
        refs.peerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refs.peerTable.getTableHeader().setFont(boldFont);
        installSelfPeerRowRenderer(refs.peerTable, mainFont, boldFont);

        JScrollPane tableScroll = new JScrollPane(refs.peerTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        tableScroll.setPreferredSize(new Dimension(400, 180));
        refs.peerTableScroll = tableScroll;

        refs.peerStatusLabel = new JLabel("尚未取得好友列表（請先啟用雲端狀態回報）");
        refs.peerStatusLabel.setFont(mainFont);
        refs.peerStatusLabel.setForeground(new Color(100, 116, 139));

        refs.openCloudSettingsButton = new JButton("前往雲端設定");
        refs.openCloudSettingsButton.setFont(boldFont);
        refs.openCloudSettingsButton.setToolTipText("切換至「雲端設定」分頁以啟用連線");
        refs.openCloudSettingsButton.setVisible(false);

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
        JLabel avatarLabel = new JLabel("大頭照：");
        avatarLabel.setFont(mainFont);
        avatarRow.add(avatarLabel);
        avatarRow.add(refs.avatarPreview);
        avatarRow.add(refs.chooseAvatarButton);
        avatarRow.add(refs.clearAvatarButton);
        refs.avatarRow = avatarRow;

        refs.messageField = new JTextField();
        refs.messageField.setFont(mainFont);
        refs.messageField.setToolTipText("輸入要傳給選中同事的訊息（最多 10000 字）");

        refs.sendMessageButton = new JButton("傳送訊息");
        refs.sendMessageButton.setFont(boldFont);

        refs.pokeButton = new JButton("戳一下");
        refs.pokeButton.setFont(boldFont);
        refs.pokeButton.setToolTipText("發送輕量提醒：對方桌面端會鳴叫、視窗晃動並跳出通知（約 15 秒內隨心跳送達）");

        refs.sendFileButton = new JButton("傳送檔案／資料夾");
        refs.sendFileButton.setFont(boldFont);
        refs.sendFileButton.setToolTipText("傳送任意副檔名的檔案，或整個資料夾（資料夾會壓成 ZIP；"
                + com.example.PeerFileRules.allowedTypesHint()
                + "。過期前可在下方紀錄重複下載或手動清除）");

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionRow.setOpaque(false);
        actionRow.add(new JLabel("訊息："));
        actionRow.getComponent(0).setFont(mainFont);
        actionRow.add(refs.messageField);
        refs.messageField.setPreferredSize(new Dimension(280, 28));
        actionRow.add(refs.sendMessageButton);
        actionRow.add(refs.pokeButton);
        actionRow.add(refs.sendFileButton);

        refs.fileTableModel = new javax.swing.table.DefaultTableModel(
                new Object[]{"方向", "檔名", "對象", "大小", "狀態", "剩餘"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        refs.fileTable = new JTable(refs.fileTableModel);
        refs.fileTable.setFont(mainFont);
        refs.fileTable.setRowHeight(26);
        refs.fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refs.fileTable.getTableHeader().setFont(boldFont);
        refs.fileTable.setToolTipText("過期前可下載；選取一筆後按「下載」或「清除」");

        JScrollPane fileScroll = new JScrollPane(refs.fileTable);
        fileScroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        fileScroll.setPreferredSize(new Dimension(400, 120));
        refs.fileTableScroll = fileScroll;

        refs.fileStatusLabel = new JLabel("傳檔紀錄：尚未連線");
        refs.fileStatusLabel.setFont(mainFont);
        refs.fileStatusLabel.setForeground(new Color(100, 116, 139));

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
        fileTitle.setForeground(new Color(30, 41, 59));
        fileSection.add(fileTitle, BorderLayout.NORTH);
        fileSection.add(fileScroll, BorderLayout.CENTER);
        fileSection.add(fileActionRow, BorderLayout.SOUTH);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(actionRow, BorderLayout.NORTH);
        south.add(fileSection, BorderLayout.CENTER);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        north.add(hint);
        north.add(Box.createVerticalStrut(6));
        north.add(refs.peerStatusLabel);
        north.add(Box.createVerticalStrut(6));
        north.add(refs.openCloudSettingsButton);
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

    /** 本機列淡藍底＋粗體，與其他裝置稍作區隔（選取列仍用系統選取色） */
    private static void installSelfPeerRowRenderer(JTable table, Font mainFont, Font boldFont) {
        Color selfBg = new Color(219, 234, 254);
        Color selfFg = new Color(30, 64, 175);
        Color selfSelectedBg = new Color(147, 197, 253);
        javax.swing.table.DefaultTableCellRenderer renderer = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                boolean self = isSelfPeerRow(tbl, row);
                if (c instanceof JLabel) {
                    JLabel label = (JLabel) c;
                    label.setFont(self && column == 0 ? boldFont : mainFont);
                    label.setBorder(self && column == 0
                            ? new EmptyBorder(0, 6, 0, 4)
                            : new EmptyBorder(0, 4, 0, 4));
                }
                if (isSelected) {
                    if (self) {
                        c.setBackground(selfSelectedBg);
                        c.setForeground(selfFg);
                    }
                } else if (self) {
                    c.setBackground(selfBg);
                    c.setForeground(selfFg);
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
        public JTextField messageField;
        public JButton sendMessageButton;
        public JButton pokeButton;
        public JButton sendFileButton;
        public JButton downloadFileButton;
        public JButton clearFileButton;
        public JTable fileTable;
        public javax.swing.table.DefaultTableModel fileTableModel;
        public JScrollPane fileTableScroll;
        public JLabel fileStatusLabel;
        public JButton openCloudSettingsButton;
        public JLabel peerHintLabel;
        public JLabel peerStatusLabel;
        public JLabel avatarPreview;
        public JButton chooseAvatarButton;
        public JButton clearAvatarButton;
        public JPanel avatarRow;
    }

    // ==================== 分組 2: 雙槽位打卡 ====================

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
        sharedContent.add(Box.createVerticalStrut(4));
        sharedContent.add(selectorBrowserActionRow(mainFont, fieldFont,
                refs.buttonIdCombo, refs.browserCombo, refs.executeNowButton));

        JPanel shared = createCollapsibleGroupPanel("共用排程設定", sharedContent, boldFont, false);

        refs.workIn = createSlotCard("上班排程", mainFont, boldFont);
        refs.workOut = createSlotCard("下班排程", mainFont, boldFont);

        JPanel slotRow = new JPanel(new GridLayout(1, 2, 8, 0));
        slotRow.setOpaque(false);
        slotRow.add(refs.workIn.panel);
        slotRow.add(refs.workOut.panel);

        GridBagConstraints rootGbc = new GridBagConstraints();
        rootGbc.gridx = 0;
        rootGbc.weightx = 1.0;
        rootGbc.fill = GridBagConstraints.HORIZONTAL;
        rootGbc.insets = new Insets(0, 0, 6, 0);

        rootGbc.gridy = 0;
        root.add(shared, rootGbc);
        rootGbc.gridy = 1;
        rootGbc.insets = new Insets(0, 0, 4, 0);
        root.add(slotRow, rootGbc);

        return root;
    }

    /**
     * 打卡歷史簡易列表（最近 5～10 筆），放在雙槽位下方空白區。
     */
    public static JPanel createCheckInHistoryPanel(
            CheckInHistoryRefs refs, Font mainFont, Font boldFont) {
        JPanel panel = createGroupPanel(
                "執行記錄（最近 " + CheckInHistoryService.MAX_ENTRIES + " 筆）", boldFont);
        panel.setLayout(new BorderLayout(0, 4));

        refs.listModel = new DefaultListModel<>();
        refs.historyList = new JList<>(refs.listModel);
        refs.historyList.setFont(mainFont);
        refs.historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refs.historyList.setVisibleRowCount(6);
        refs.historyList.setFixedCellHeight(22);
        refs.historyList.setOpaque(false);
        refs.historyList.setBackground(new Color(0, 0, 0, 0));
        refs.historyList.setForeground(new Color(30, 41, 59));
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
                    c.setBackground(new Color(59, 130, 246, 170));
                    c.setForeground(Color.WHITE);
                } else if (latest) {
                    // 最新一筆：淺藍底＋深藍字，方便一眼辨識
                    c.setBackground(new Color(219, 234, 254));
                    c.setForeground(new Color(30, 64, 175));
                    c.setFont(list.getFont().deriveFont(Font.BOLD));
                } else {
                    c.setBackground(new Color(0, 0, 0, 0));
                    c.setForeground(new Color(30, 41, 59));
                    c.setFont(list.getFont());
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(refs.historyList);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225, 160)));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scroll, BorderLayout.CENTER);

        refs.emptyLabel = new JLabel("尚無執行紀錄；排程或「立即執行」完成後會顯示在此", SwingConstants.CENTER);
        refs.emptyLabel.setFont(mainFont);
        refs.emptyLabel.setForeground(new Color(100, 116, 139));

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
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
        refs.panel = createCompactGroupPanel(title, boldFont);
        refs.panel.setLayout(new BorderLayout(0, 4));

        JPanel settings = new JPanel(new GridBagLayout());
        settings.setOpaque(false);
        GridBagConstraints sgbc = new GridBagConstraints();
        sgbc.anchor = GridBagConstraints.CENTER;
        sgbc.insets = new Insets(0, 0, 0, 4);

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
        refs.randomOffsetCheckBox.setForeground(new Color(147, 51, 234));
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
        settings.add(new JPanel(), sgbc);
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
        mgbc.insets = new Insets(2, 0, 2, 6);
        mgbc.weightx = 0.5;
        mgbc.gridy = 0;
        mgbc.gridx = 0;
        mgbc.gridwidth = 1;
        statusGrid.add(createMetricCell("狀態", refs.statusLabel, mainFont), mgbc);
        mgbc.gridx = 1;
        statusGrid.add(createMetricCell("倒數", refs.countdownLabel, mainFont), mgbc);
        mgbc.gridy = 1;
        mgbc.gridx = 0;
        mgbc.gridwidth = 1;
        mgbc.weightx = 0.5;
        statusGrid.add(createMetricCell("預計觸發", refs.triggerLabel, mainFont), mgbc);
        mgbc.gridx = 1;
        statusGrid.add(createMetricCell("結果", refs.resultLabel, mainFont), mgbc);
        mgbc.gridy = 2;
        mgbc.gridx = 0;
        mgbc.gridwidth = 2;
        mgbc.weightx = 1.0;
        statusGrid.add(createMetricCell("鎖定設定", refs.lockedSettingsLabel, mainFont), mgbc);
        mgbc.gridwidth = 1;
        mgbc.weightx = 0.5;

        JPanel mainCol = new JPanel(new BorderLayout(0, 4));
        mainCol.setOpaque(false);
        mainCol.add(settings, BorderLayout.NORTH);
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
        titleLabel.setFont(new Font(font.getName(), Font.BOLD, 10));
        titleLabel.setForeground(new Color(100, 116, 139));
        value.setFont(new Font(font.getName(), Font.PLAIN, 12));
        value.setHorizontalAlignment(SwingConstants.LEFT);
        cell.add(titleLabel, BorderLayout.NORTH);
        cell.add(value, BorderLayout.CENTER);
        return cell;
    }

    private static JPanel createCompactGroupPanel(String title, Font titleFont) {
        JPanel panel = translucentPanel();
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                title, TitledBorder.LEFT, TitledBorder.TOP,
                titleFont, new Color(30, 41, 59));
        panel.setBorder(new CompoundBorder(titledBorder, new EmptyBorder(2, 6, 4, 6)));
        return panel;
    }

    public static class SlotPanelRefs {
        public SlotCardRefs workIn;
        public SlotCardRefs workOut;
        public JComboBox<String> urlCombo;
        public JComboBox<String> buttonIdCombo;
        public JComboBox<String> browserCombo;
        public JButton executeNowButton;
    }

    public static class SlotCardRefs {
        public JPanel panel;
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
        scrollPane.setPreferredSize(new Dimension(780, 240));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        logPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel logActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        refs.clearLogButton = new JButton("清除 Log");
        refs.clearLogButton.setFont(boldFont);
        refs.clearLogButton.setToolTipText("清空下方日誌內容");
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

        Dimension selectorDim = new Dimension(96, rowH);
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
        headerPanel.setBackground(new Color(241, 245, 249));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(5, 10, 5, 10)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleFont);
        titleLabel.setForeground(new Color(30, 41, 59));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JLabel toggleLabel = new JLabel(startCollapsed ? "► 點擊展開設定" : "▼ 點擊折疊收起");
        toggleLabel.setFont(UiFonts.chineseBold(12));
        toggleLabel.setForeground(new Color(37, 99, 235));
        headerPanel.add(toggleLabel, BorderLayout.EAST);

        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

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
                headerPanel.setBackground(new Color(226, 232, 240));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                headerPanel.setBackground(new Color(241, 245, 249));
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
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                title, TitledBorder.LEFT, TitledBorder.TOP,
                titleFont, new Color(30, 41, 59));
        panel.setBorder(new CompoundBorder(titledBorder, new EmptyBorder(4, 8, 6, 8)));
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
        panel.setBackground(new Color(248, 250, 252, 110));
        return panel;
    }
}
