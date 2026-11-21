package com.example.ui;

import com.example.model.WorkSlot;
import com.example.service.CheckInHistoryService;
import com.example.service.PeerAvatar;
import com.example.service.WindowBackground;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.text.View;

/**
 * UI 面板工廠 — 負責建構各區塊的 Swing 面板
 * 將 UI 佈局邏輯從 App 主類別中抽離，減少構造函式臃腫度
 */
public class PanelFactory {

    /** 裝置互動分頁／面板標題 */
    public static final String PEER_TAB_LABEL = "好友與傳檔";

    /** 設定分頁的「連線」子分頁；雲端欄位在這一頁。 */
    public static final int SETTINGS_CONNECTION_TAB = 0;

    /** 執行紀錄同時看得到的筆數。最近十筆裡先露出這幾列，其餘再捲。 */
    static final int HISTORY_VISIBLE_ROWS = 5;

    // ==================== 分組 1: 設定 ====================

    /**
     * 設定頁的子分頁：連線（雲端連線與程式更新）、外觀、中繼。
     * 呼叫端需透過 refs 取得元件引用。
     */
    public static JTabbedPane createServerConfigBody(ServerConfigRefs refs, Font mainFont, Font boldFont, Font fieldFont) {
        refs.heartbeatTokenField = new JPasswordField("punchclock-dev-secret");
        refs.heartbeatTokenField.setFont(fieldFont);
        refs.heartbeatTokenField.setColumns(18);
        refs.heartbeatTokenField.setToolTipText("與伺服器約定的認證 Token，需與後端設定一致；雲端連線中會鎖定，請先取消「啟用雲端」再修改");
        CandyTextField.install(refs.heartbeatTokenField, true);

        refs.serverUrlCombo = RecentValuesHelper.createCombo(
                fieldFont, "http://localhost:3000",
                "心跳伺服器網址，例如 https://xxx.onrender.com；雲端連線中會鎖定，請先取消「啟用雲端」再修改");
        refs.serverUrlCombo.setPrototypeDisplayValue("http://localhost:3000");
        CandyComboBox.dress(refs.serverUrlCombo);

        lockFieldHeight(refs.heartbeatTokenField);
        lockFieldHeight(refs.serverUrlCombo);

        JLabel tokenLabel = new JLabel("心跳 Token：");
        tokenLabel.setFont(mainFont);
        JLabel serverUrlLabel = new JLabel("Server 雲端網址：");
        serverUrlLabel.setFont(mainFont);

        refs.enableServerCheckBox = new CandyCheckBox("啟用雲端單向狀態回報", false);
        refs.enableServerCheckBox.setFont(boldFont);
        refs.enableServerCheckBox.setToolTipText("開啟後定期回報本機執行狀態到雲端 Dashboard");
        refs.trustAllSslCheckBox = new CandyCheckBox("信任所有 SSL（除錯）", false);
        refs.trustAllSslCheckBox.setFont(mainFont);
        refs.trustAllSslCheckBox.setToolTipText("預設關閉。啟用雲端時會鎖定；需先取消雲端才能修改。僅本機自簽憑證除錯用。");
        refs.checkForUpdatesCheckBox = new CandyCheckBox("自動檢查更新", true);
        refs.checkForUpdatesCheckBox.setFont(mainFont);
        refs.checkForUpdatesCheckBox.setToolTipText(
                "開啟後，雲端連線時會在背景檢查。有新版只顯示在下方，不會跳出視窗。");
        refs.heartbeatStatusLabel = new JLabel("[離線] 未連線 (已停用)", SwingConstants.LEFT);
        refs.heartbeatStatusLabel.setFont(boldFont);
        refs.heartbeatStatusLabel.setForeground(NightTheme.MUTED);
        refs.heartbeatStatusLabel.setBorder(null);

        JPanel cloud = new JPanel();
        cloud.setOpaque(false);
        cloud.setLayout(new BoxLayout(cloud, BoxLayout.Y_AXIS));
        cloud.add(formRow(tokenLabel, refs.heartbeatTokenField));
        cloud.add(Box.createVerticalStrut(10));
        cloud.add(formRowStretch(serverUrlLabel, refs.serverUrlCombo));
        cloud.add(Box.createVerticalStrut(10));
        // 預設內容寬約 288px，13px 三項約 470px。間距交給核取方塊自己的內距，字級再往下收到放得下為止。
        JPanel cloudFlags = wrapFlowRow(0, 2,
                refs.enableServerCheckBox,
                refs.trustAllSslCheckBox,
                refs.heartbeatStatusLabel);
        installSingleLineFont(cloudFlags, 13, 7);
        cloud.add(cloudFlags);

        refs.updateStatusTitle = new JLabel("尚未檢查更新");
        refs.updateStatusTitle.setFont(boldFont);
        refs.updateStatusTitle.setForeground(NightTheme.MUTED);
        refs.updateStatusTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        refs.updateStatusTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                refs.updateStatusTitle.getPreferredSize().height));

        refs.updateCheckedAtLabel = new JLabel("尚未檢查過");
        refs.updateCheckedAtLabel.setFont(UiFonts.chinesePlain(12));
        refs.updateCheckedAtLabel.setForeground(NightTheme.MUTED);
        refs.updateCheckedAtLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        refs.updateCheckedAtLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                refs.updateCheckedAtLabel.getPreferredSize().height));

        refs.updateStatusDetail = new JTextArea("有新版時會顯示在這裡。");
        refs.updateStatusDetail.setEditable(false);
        refs.updateStatusDetail.setFocusable(false);
        refs.updateStatusDetail.setOpaque(false);
        refs.updateStatusDetail.setLineWrap(true);
        refs.updateStatusDetail.setWrapStyleWord(true);
        refs.updateStatusDetail.setFont(UiFonts.chinesePlain(12));
        refs.updateStatusDetail.setForeground(NightTheme.MUTED);
        refs.updateStatusDetail.setBorder(new EmptyBorder(0, 2, 0, 2));
        refs.updateStatusDetail.setAlignmentX(Component.LEFT_ALIGNMENT);
        fitRelayHelp(refs.updateStatusDetail);

        refs.checkUpdateButton = candyButton("檢查更新", CandyButton.Variant.SECONDARY, mainFont,
                "立刻向伺服器問一次有沒有新版本");
        refs.applyUpdateButton = candyButton("現在更新", CandyButton.Variant.PRIMARY, boldFont,
                "下載並重新啟動。設定與排程會保留。");
        refs.applyUpdateButton.setEnabled(false);

        JPanel updateActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        updateActions.setOpaque(false);
        updateActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        updateActions.add(refs.checkUpdateButton);
        updateActions.add(refs.applyUpdateButton);
        Dimension updateActionsPref = updateActions.getPreferredSize();
        updateActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, updateActionsPref.height));

        JPanel updates = new JPanel();
        updates.setOpaque(false);
        updates.setLayout(new BoxLayout(updates, BoxLayout.Y_AXIS));
        updates.add(formRow(refs.checkForUpdatesCheckBox));
        updates.add(Box.createVerticalStrut(6));
        updates.add(refs.updateStatusTitle);
        updates.add(Box.createVerticalStrut(2));
        updates.add(refs.updateCheckedAtLabel);
        updates.add(Box.createVerticalStrut(4));
        updates.add(refs.updateStatusDetail);
        updates.add(Box.createVerticalStrut(12));
        updates.add(updateActions);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UiFonts.chineseBold(13));
        tabs.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        tabs.setUI(new BasicTabbedPaneUI());
        tabs.setOpaque(false);
        tabs.setBackground(new Color(0, 0, 0, 0));
        tabs.addTab("連線", settingsPage(
                settingsSection("雲端連線", boldFont, cloud),
                settingsSection("程式更新", boldFont, updates)));
        tabs.addTab("外觀", settingsPage(
                settingsSection("桌面寵物", boldFont, desktopPetRow(refs, mainFont, boldFont))));
        tabs.addTab("中繼", settingsPage(
                settingsSection("中繼 Proxy", boldFont, relayProxyRow(refs, mainFont, boldFont))));
        tabs.setToolTipTextAt(SETTINGS_CONNECTION_TAB, "雲端連線與程式更新");
        tabs.setToolTipTextAt(1, "桌面寵物");
        tabs.setToolTipTextAt(2, "讓區網內的同事經由這台電腦連到雲端");
        tabs.setSelectedIndex(SETTINGS_CONNECTION_TAB);
        return tabs;
    }

    /** 子分頁內容貼在上方，下方留白，背景圖才透得出來。 */
    private static JPanel settingsPage(JComponent... sections) {
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBorder(new EmptyBorder(4, 4, 12, 8));
        boolean first = true;
        for (JComponent section : sections) {
            if (!first) {
                stack.add(Box.createVerticalStrut(12));
            }
            first = false;
            stack.add(section);
        }
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(stack, BorderLayout.NORTH);
        return page;
    }

    /** 設定區的圓角卡片。標題畫在框上，內容貼在標題下面。 */
    private static JPanel settingsSection(String title, Font titleFont, JComponent body) {
        CandyPanel section = new CandyPanel(title, titleFont);
        section.setLayout(new BorderLayout());
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(body, BorderLayout.CENTER);
        return section;
    }

    private static JPanel relayProxyRow(ServerConfigRefs refs, Font mainFont, Font boldFont) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setAlignmentX(Component.LEFT_ALIGNMENT);

        refs.relayEnabledCheckBox = new CandyCheckBox("啟用中繼 Proxy", false);
        refs.relayEnabledCheckBox.setFont(boldFont);
        refs.relayEnabledCheckBox.setToolTipText("讓區網內連不到 Server 的同事，把 Server 網址設成你的 IP，透過你連到雲端");

        JLabel portLabel = new JLabel("埠號：");
        portLabel.setFont(mainFont);

        refs.relayPortSpinner = new JSpinner(new SpinnerNumberModel(8888, 1024, 65535, 1));
        refs.relayPortSpinner.setFont(mainFont);
        refs.relayPortSpinner.setToolTipText("中繼服務監聽的埠號（預設 8888）");
        JComponent editor = refs.relayPortSpinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField portField = ((JSpinner.DefaultEditor) editor).getTextField();
            portField.setColumns(5);
            CandyTextField.install(portField, true);
        }

        refs.relayStatusLabel = new JLabel("未啟動");
        refs.relayStatusLabel.setFont(mainFont);
        refs.relayStatusLabel.setForeground(NightTheme.MUTED);

        JPanel enableRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        enableRow.setOpaque(false);
        enableRow.add(refs.relayEnabledCheckBox);
        JPanel portRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        portRow.setOpaque(false);
        portRow.add(portLabel);
        portRow.add(refs.relayPortSpinner);
        portRow.add(refs.relayStatusLabel);
        pinRow(enableRow);
        pinRow(portRow);

        refs.relayHelpArea = new JTextArea();
        refs.relayHelpArea.setEditable(false);
        refs.relayHelpArea.setLineWrap(true);
        refs.relayHelpArea.setWrapStyleWord(true);
        refs.relayHelpArea.setFont(UiFonts.chinesePlain(12));
        CandyTextField.dressArea(refs.relayHelpArea);
        refs.relayHelpArea.setForeground(NightTheme.MUTED);
        refs.relayHelpArea.setText(""
                + "同事連不到 Server，但你可以？試試中繼 Proxy！\n"
                + "啟用後，同事把「Server 雲端網址」改成 http://你的IP:8888\n"
                + "你的 App 會幫同事把請求轉發到真正的 Server。");
        fitRelayHelp(refs.relayHelpArea);

        refs.relayHelpArea.setColumns(18);
        refs.relayHelpArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(enableRow);
        column.add(Box.createVerticalStrut(4));
        column.add(portRow);
        column.add(Box.createVerticalStrut(4));
        column.add(refs.relayHelpArea);

        return column;
    }

    private static JPanel backgroundRow(ServerConfigRefs refs, Font mainFont, Font boldFont) {
        refs.backgroundPreview = new JLabel();
        refs.backgroundPreview.setPreferredSize(new Dimension(
                WindowBackground.PREVIEW_WIDTH, WindowBackground.PREVIEW_HEIGHT));
        refs.backgroundPreview.setMinimumSize(refs.backgroundPreview.getPreferredSize());
        refs.backgroundPreview.setToolTipText("程式視窗背景預覽；實際會等比裁切填滿視窗");
        refs.backgroundPreview.setIcon(WindowBackground.previewIcon(null));

        CandyButton choose = new CandyButton("選擇背景", CandyButton.Variant.PRIMARY, CandyButton.Size.SMALL);
        choose.setFont(boldFont);
        choose.setToolTipText("選擇 JPG／PNG／GIF 作為程式背景");
        refs.chooseBackgroundButton = choose;
        CandyButton clear = new CandyButton("還原預設", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        clear.setFont(mainFont);
        clear.setToolTipText("清除自訂背景，還原預設底色");
        clear.setEnabled(false);
        refs.clearBackgroundButton = clear;

        JPanel previewRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        previewRow.setOpaque(false);
        previewRow.add(refs.backgroundPreview);
        JPanel backgroundButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        backgroundButtons.setOpaque(false);
        backgroundButtons.add(refs.chooseBackgroundButton);
        backgroundButtons.add(refs.clearBackgroundButton);
        pinRow(previewRow);
        pinRow(backgroundButtons);

        refs.backgroundBlurSlider = new JSlider(0, WindowBackground.MAX_BLUR_PERCENT, 0);
        refs.backgroundBlurSlider.setOpaque(false);
        refs.backgroundBlurSlider.setFocusable(false);
        refs.backgroundBlurSlider.setPreferredSize(new Dimension(160, 22));
        refs.backgroundBlurSlider.setToolTipText("背景圖模糊程度；向右越糊，方便閱讀前景文字");
        refs.backgroundBlurValueLabel = new JLabel("0%");
        refs.backgroundBlurValueLabel.setFont(mainFont);
        refs.backgroundBlurValueLabel.setForeground(NightTheme.MUTED);
        refs.backgroundBlurValueLabel.setPreferredSize(new Dimension(40, 22));

        refs.backgroundOpacitySlider = new JSlider(0, WindowBackground.MAX_OPACITY_PERCENT,
                WindowBackground.DEFAULT_OPACITY_PERCENT);
        refs.backgroundOpacitySlider.setOpaque(false);
        refs.backgroundOpacitySlider.setFocusable(false);
        refs.backgroundOpacitySlider.setPreferredSize(new Dimension(160, 22));
        refs.backgroundOpacitySlider.setToolTipText("背景圖不透明度；向右越清楚，向左越淡");
        refs.backgroundOpacityValueLabel = new JLabel(WindowBackground.DEFAULT_OPACITY_PERCENT + "%");
        refs.backgroundOpacityValueLabel.setFont(mainFont);
        refs.backgroundOpacityValueLabel.setForeground(NightTheme.MUTED);
        refs.backgroundOpacityValueLabel.setPreferredSize(new Dimension(40, 22));

        JPanel blurRow = sliderRow(mainFont, "模糊：", refs.backgroundBlurSlider, refs.backgroundBlurValueLabel);
        JPanel opacityRow = sliderRow(mainFont, "不透明度：", refs.backgroundOpacitySlider, refs.backgroundOpacityValueLabel);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        blurRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        opacityRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(previewRow);
        column.add(backgroundButtons);
        column.add(Box.createVerticalStrut(4));
        column.add(blurRow);
        column.add(Box.createVerticalStrut(2));
        column.add(opacityRow);
        setBackgroundEffectControlsEnabled(refs, false);
        return column;
    }

    private static JPanel desktopPetRow(ServerConfigRefs refs, Font mainFont, Font boldFont) {
        refs.desktopPetSwitch = new CandySwitch();
        refs.desktopPetSwitch.setFont(boldFont);
        refs.desktopPetSwitch.getAccessibleContext().setAccessibleName("桌面寵物");
        String tip = "開啟後在桌面顯示小角色。主視窗最小化時仍會留下；關閉程式時一起結束。";
        refs.desktopPetSwitch.setToolTipText(tip);

        JLabel title = new JLabel("桌面寵物");
        title.setFont(boldFont);
        title.setLabelFor(refs.desktopPetSwitch);
        title.setToolTipText(tip);
        title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        title.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (refs.desktopPetSwitch.isEnabled()) {
                    refs.desktopPetSwitch.doClick();
                }
            }
        });

        JLabel hint = new CompactShellPanels.WrappingLabel(
                "可拖曳移動，點一下會打招呼。主視窗最小化時仍會留在桌面。");
        hint.setFont(mainFont);
        hint.setForeground(NightTheme.MUTED);

        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        line.setOpaque(false);
        line.add(refs.desktopPetSwitch);
        line.add(title);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(line);
        column.add(Box.createVerticalStrut(4));
        column.add(hint);
        column.add(Box.createVerticalStrut(6));
        refs.desktopPetCare = new CandySwitch();
        refs.desktopPetCare.setSelected(true);
        column.add(petOption(refs.desktopPetCare, "養成",
                "飽足、心情、精神會隨時間下降。點喵露可餵食、摸頭、玩耍。預設開啟。", boldFont));
        refs.desktopPetSwitch.addItemListener(e -> applyDesktopPetCareEnabled(refs));
        applyDesktopPetCareEnabled(refs);
        return column;
    }

    /** 總開關關掉時，養成不能改，原來的勾選留著。 */
    private static void applyDesktopPetCareEnabled(ServerConfigRefs refs) {
        if (refs == null || refs.desktopPetSwitch == null) {
            return;
        }
        boolean on = refs.desktopPetSwitch.isSelected();
        setPetOptionEnabled(refs.desktopPetCare, on);
    }

    private static void setPetOptionEnabled(JToggleButton toggle, boolean enabled) {
        if (toggle == null) {
            return;
        }
        toggle.setEnabled(enabled);
        Container parent = toggle.getParent();
        if (parent == null) {
            return;
        }
        Cursor cursor = Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
        for (Component child : parent.getComponents()) {
            if (child == toggle) {
                continue;
            }
            child.setEnabled(enabled);
            child.setCursor(cursor);
        }
    }

    private static JPanel petOption(JToggleButton toggle, String title, String tip, Font font) {
        toggle.setFont(font);
        toggle.getAccessibleContext().setAccessibleName(title);
        toggle.setToolTipText(tip);
        JLabel label = new JLabel(title);
        label.setFont(font);
        label.setLabelFor(toggle);
        label.setToolTipText(tip);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (toggle.isEnabled()) {
                    toggle.doClick();
                }
            }
        });
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        line.setOpaque(false);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setBorder(new EmptyBorder(1, 18, 1, 0));
        line.add(toggle);
        line.add(label);
        return line;
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

    /** 說明只有幾行，依折行後的實際高度撐開，不另開捲動。文字或寬度變了要重算。 */
    public static void fitRelayHelp(JTextArea area) {
        if (area == null) {
            return;
        }
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        if (area.getColumns() <= 0) {
            area.setColumns(18);
        }
        ensureRelayHelpRefit(area);
        applyRelayHelpHeight(area);
    }

    private static void ensureRelayHelpRefit(JTextArea area) {
        if (Boolean.TRUE.equals(area.getClientProperty("relayHelpListener"))) {
            return;
        }
        area.putClientProperty("relayHelpListener", Boolean.TRUE);
        area.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyRelayHelpHeight(area);
            }
        });
    }

    /**
     * 清掉上次鎖死的 preferred size，再用目前寬度量折行高度。
     * 否則第二次呼叫會一直拿到舊高度，長文被裁掉。
     */
    private static void applyRelayHelpHeight(JTextArea area) {
        if (area == null || Boolean.TRUE.equals(area.getClientProperty("relayHelpBusy"))) {
            return;
        }
        int width = area.getWidth();
        if (width <= 1 && area.getParent() != null && area.getParent().getWidth() > 1) {
            Insets parentInsets = area.getParent().getInsets();
            width = area.getParent().getWidth() - parentInsets.left - parentInsets.right;
        }
        if (width <= 1) {
            int columns = Math.max(1, area.getColumns());
            width = Math.min(300, Math.max(160, columns * 9));
        }
        String token = width + "\n" + area.getText();
        if (token.equals(area.getClientProperty("relayHelpToken"))) {
            return;
        }
        area.putClientProperty("relayHelpBusy", Boolean.TRUE);
        try {
            area.setPreferredSize(null);
            area.setMinimumSize(null);
            area.setMaximumSize(null);
            Insets insets = area.getInsets();
            int inner = Math.max(8, width - insets.left - insets.right);
            View root = area.getUI().getRootView(area);
            root.setSize(inner, Integer.MAX_VALUE);
            int textH = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS));
            int height = Math.max(36, textH + insets.top + insets.bottom + 8);
            area.setMinimumSize(new Dimension(0, height));
            area.setPreferredSize(new Dimension(width, height));
            area.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            area.putClientProperty("relayHelpToken", token);
            Container parent = area.getParent();
            if (parent != null) {
                parent.revalidate();
            }
        } finally {
            area.putClientProperty("relayHelpBusy", Boolean.FALSE);
        }
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
        public JLabel updateStatusTitle;
        public JLabel updateCheckedAtLabel;
        public JTextArea updateStatusDetail;
        public JButton checkUpdateButton;
        public JButton applyUpdateButton;
        public JLabel heartbeatStatusLabel;
        public JLabel backgroundPreview;
        public JButton chooseBackgroundButton;
        public JButton clearBackgroundButton;
        public JSlider backgroundBlurSlider;
        public JLabel backgroundBlurValueLabel;
        public JSlider backgroundOpacitySlider;
        public JLabel backgroundOpacityValueLabel;
        public JToggleButton desktopPetSwitch;
        public JToggleButton desktopPetNotifyMessage;
        public JToggleButton desktopPetNotifyFile;
        public JToggleButton desktopPetNotifyTask;
        public JToggleButton desktopPetNotifySound;
        public JToggleButton desktopPetCursorReact;
        public JToggleButton desktopPetCare;
        // 中繼 Proxy
        public JCheckBox relayEnabledCheckBox;
        public JSpinner relayPortSpinner;
        public JLabel relayStatusLabel;
        public JTextArea relayHelpArea;
    }

    // ==================== 裝置互動 ====================

    /** 好友列表「最愛」欄的 model 索引（最後一欄，不影響第 0 欄的裝置 ID）。 */
    public static final int PEER_FAVORITE_COLUMN = 4;
    public static final String PEER_FAVORITE_ON = "★";
    public static final String PEER_FAVORITE_OFF = "☆";
    private static final Color PEER_FAVORITE_GOLD = new Color(255, 208, 96);

    public static JPanel createPeerInteractionPanel(PeerInteractionRefs refs, Font mainFont, Font boldFont) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(4, 0, 0, 0));

        JLabel hint = new CompactShellPanels.WrappingLabel(
                "只顯示同一伺服器上的好友（不含本機，每 15 秒隨心跳更新）");
        hint.setFont(mainFont);
        hint.setForeground(NightTheme.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        refs.peerHintLabel = hint;

        refs.peerTableModel = new javax.swing.table.DefaultTableModel(
                new Object[]{"裝置 ID", "狀態", "等待任務", "版本", "最愛"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        refs.peerTable = new JTable(refs.peerTableModel) {
            private boolean layingOutPeerColumns;

            @Override
            public void doLayout() {
                if (layingOutPeerColumns) {
                    return;
                }
                if (getWidth() <= 0 || getColumnCount() != 5) {
                    super.doLayout();
                    return;
                }
                layingOutPeerColumns = true;
                try {
                    layoutPeerColumns(this);
                } finally {
                    layingOutPeerColumns = false;
                }
            }
        };
        refs.peerTable.setFont(mainFont);
        refs.peerTable.setRowHeight(44);
        refs.peerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refs.peerTable.getTableHeader().setFont(boldFont);
        refs.peerTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        // 真正的欄寬在 doLayout 用表頭 renderer 的 preferred 寬重算。這裡先給一個放得下欄名的初值。
        int[] peerColW = {120, 48, 76, 48, 48};
        for (int i = 0; i < peerColW.length; i++) {
            javax.swing.table.TableColumn column = refs.peerTable.getColumnModel().getColumn(i);
            column.setMinWidth(28);
            column.setMaxWidth(Integer.MAX_VALUE);
            column.setPreferredWidth(peerColW[i]);
        }
        installSelfPeerRowRenderer(refs.peerTable, mainFont, boldFont);

        JScrollPane tableScroll = new JScrollPane(refs.peerTable);
        tableScroll.setPreferredSize(new Dimension(200, 160));
        tableScroll.setMinimumSize(new Dimension(80, 120));
        tableScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        markCandyScroll(tableScroll);
        refs.peerTableScroll = tableScroll;

        refs.peerStatusLabel = new CompactShellPanels.WrappingLabel("尚未取得好友列表（請先啟用雲端狀態回報）");
        refs.peerStatusLabel.setFont(mainFont);
        refs.peerStatusLabel.setForeground(NightTheme.MUTED);
        refs.peerStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        refs.openCloudSettingsButton = candyButton(
                "前往設定", CandyButton.Variant.PRIMARY, boldFont, "切換至「設定」的「連線」子分頁以啟用連線");
        refs.openCloudSettingsButton.setVisible(false);
        refs.openCloudSettingsButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        refs.avatarPreview = new JLabel();
        refs.avatarPreview.setPreferredSize(new Dimension(44, 44));
        refs.avatarPreview.setMinimumSize(new Dimension(44, 44));
        refs.avatarPreview.setToolTipText("傳送訊息或戳一下時，對方對話框會顯示這張大頭照");

        refs.chooseAvatarButton = candyButton(
                "選擇大頭照", CandyButton.Variant.SECONDARY, boldFont, "從本機選一張照片，對方收到訊息／戳一下時會看到");

        refs.clearAvatarButton = candyButton(
                "還原預設", CandyButton.Variant.SECONDARY, mainFont, "改回使用 App 圖示");

        JLabel avatarLabel = new JLabel("大頭照：");
        avatarLabel.setFont(mainFont);
        JPanel avatarRow = wrapFlowRow(4, 4,
                avatarLabel, refs.avatarPreview, refs.chooseAvatarButton, refs.clearAvatarButton);
        refs.avatarRow = avatarRow;

        String[] workerOptions = {"company-worker", "company-worker2", "company-worker3", "company-worker4"};
        refs.clientIdCombo = new JComboBox<>(workerOptions);
        refs.clientIdCombo.setEditable(true);
        refs.clientIdCombo.setFont(mainFont);
        refs.clientIdCombo.setPrototypeDisplayValue("company-worker4");
        refs.clientIdCombo.setToolTipText(
                "好友看到的本機名稱。可下拉選擇或自行輸入；雲端連線中也可直接修改。"
                        + " 按 Enter 或「套用」後立即生效並儲存");
        CandyComboBox.install(refs.clientIdCombo);
        Component idEditor = refs.clientIdCombo.getEditor().getEditorComponent();
        if (idEditor != null) {
            idEditor.setFont(mainFont);
        }
        int idHeight = Math.max(refs.clientIdCombo.getPreferredSize().height, 30);
        refs.clientIdCombo.setMinimumSize(new Dimension(120, idHeight));
        refs.clientIdCombo.setPreferredSize(new Dimension(148, idHeight));
        refs.clientIdCombo.setMaximumSize(new Dimension(148, idHeight));

        refs.clientIdApplyButton = candyButton(
                "套用", CandyButton.Variant.SECONDARY, mainFont, "按 Enter 或「套用」後立即生效並儲存");
        refs.clientIdApplyButton.setEnabled(false);
        refs.clientIdSavedLabel = new JLabel("✓ 已儲存");
        refs.clientIdSavedLabel.setFont(mainFont);
        refs.clientIdSavedLabel.setForeground(new Color(144, 230, 176));
        refs.clientIdSavedLabel.setVisible(false);
        JLabel idLabel = new JLabel("裝置 ID：");
        idLabel.setFont(mainFont);
        JPanel idRow = wrapFlowRow(4, 4,
                idLabel, refs.clientIdCombo, refs.clientIdApplyButton, refs.clientIdSavedLabel);

        refs.openChatButton = candyButton(
                "開啟聊天", CandyButton.Variant.PRIMARY, boldFont,
                "和選取的好友開一個小聊天視窗，可傳訊息、圖片、檔案或戳一下（雙擊好友也可以）");
        refs.openChatButton.setEnabled(false);
        JLabel chatHint = new CompactShellPanels.WrappingLabel(
                "雙擊好友，或選取後按「開啟聊天」；離線排下面，☆ 在同一組排最上面");
        chatHint.setFont(mainFont);
        chatHint.setForeground(NightTheme.MUTED);
        JPanel chatButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chatButtonRow.setOpaque(false);
        chatButtonRow.add(refs.openChatButton);
        pinRow(chatButtonRow);
        JPanel chatRow = new JPanel();
        chatRow.setOpaque(false);
        chatRow.setLayout(new BoxLayout(chatRow, BoxLayout.Y_AXIS));
        chatRow.add(chatButtonRow);
        chatHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        chatRow.add(chatHint);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(chatRow, BorderLayout.NORTH);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        north.add(idRow);
        north.add(Box.createVerticalStrut(8));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        refs.peerStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        refs.openCloudSettingsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(hint);
        north.add(Box.createVerticalStrut(2));
        north.add(refs.peerStatusLabel);
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

    /** 好友列：圓形頭像、在線點、滑過變色。裝置 ID 仍放在第一欄的文字，選取邏輯不變。 */
    private static void installSelfPeerRowRenderer(JTable table, Font mainFont, Font boldFont) {
        Color selfBg = CandyTheme.ROW_LATEST;
        Color hoverBg = CandyTheme.ROW_HOT;
        java.util.Map<String, Icon> faces = new java.util.HashMap<>();
        javax.swing.table.DefaultTableCellRenderer renderer = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                boolean self = isSelfPeerRow(tbl, row);
                boolean hover = !isSelected && row == hoverRow(tbl);
                boolean favoriteColumn = column == PEER_FAVORITE_COLUMN;
                if (c instanceof JLabel) {
                    JLabel label = (JLabel) c;
                    label.setFont(self && column == 0 ? boldFont : mainFont);
                    label.setBorder(new EmptyBorder(4, column == 0 ? 4 : 4, 4, column == 0 ? 2 : 4));
                    label.setIcon(column == 0 ? friendFace(tbl, row, faces) : null);
                    label.setIconTextGap(column == 0 ? 4 : 8);
                    label.setHorizontalAlignment(favoriteColumn ? SwingConstants.CENTER : SwingConstants.LEADING);
                    if (favoriteColumn) {
                        label.setFont(UiFonts.chineseBold(16));
                    }
                    if (column == 0) {
                        fitPeerIdLabel(label, tbl, self, boldFont, mainFont);
                    }
                }
                if (isSelected) {
                    c.setBackground(CandyTheme.ROW_SELECT);
                    c.setForeground(CandyTheme.INK);
                } else if (hover) {
                    c.setBackground(hoverBg);
                    c.setForeground(CandyTheme.INK);
                } else if (self) {
                    c.setBackground(selfBg);
                    c.setForeground(CandyTheme.INK);
                } else {
                    c.setBackground(row % 2 == 0 ? CandyTheme.ROW : CandyTheme.ROW_ALT);
                    c.setForeground(CandyTheme.INK);
                }
                if (c instanceof JComponent) {
                    JComponent widget = (JComponent) c;
                    widget.setOpaque(true);
                    String tip = value == null ? null : String.valueOf(value);
                    if (favoriteColumn) {
                        boolean on = PEER_FAVORITE_ON.equals(tip);
                        widget.setToolTipText(on ? "最愛（點一下取消）" : "點一下加入最愛");
                        if (on) {
                            c.setForeground(PEER_FAVORITE_GOLD);
                        } else if (!isSelected) {
                            c.setForeground(NightTheme.MUTED);
                        }
                    } else {
                        widget.setToolTipText(tip == null || tip.isEmpty() ? null : tip);
                    }
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

    /**
     * 後面四欄至少放下表頭（套用 NightTheme 之後的 header renderer，含左右 padding）。
     * 多出來的寬度給裝置 ID；表太窄時先縮裝置 ID（允許省略），最後才壓縮後面四欄。
     */
    private static void layoutPeerColumns(JTable table) {
        int width = table.getWidth();
        if (table.getColumnCount() != 5) {
            return;
        }
        int[] tail = new int[4];
        int tailSum = 0;
        for (int i = 0; i < tail.length; i++) {
            tail[i] = peerColumnPreferredWidth(table, i + 1);
            tailSum += tail[i];
        }
        if (width >= tailSum + 28) {
            applyPeerColumnWidths(table, width - tailSum, tail);
            return;
        }
        int[] sized = new int[tail.length];
        int budget = Math.max(0, width - 28);
        int overflow = Math.max(0, tailSum - budget);
        for (int i = 0; i < sized.length; i++) {
            sized[i] = tail[i];
        }
        for (int i = sized.length - 1; i >= 0 && overflow > 0; i--) {
            int reducible = Math.max(0, sized[i] - 28);
            int cut = Math.min(reducible, overflow);
            sized[i] -= cut;
            overflow -= cut;
        }
        int used = 0;
        for (int value : sized) {
            used += value;
        }
        applyPeerColumnWidths(table, Math.max(28, width - used), sized);
    }

    /** 表頭 renderer 的 preferred 寬。資料欄再取目前列裡最寬的一格。裝置 ID 只看表頭，剩下的寬度給它。 */
    private static int peerColumnPreferredWidth(JTable table, int columnIndex) {
        int width = peerHeaderPreferredWidth(table, columnIndex);
        if (columnIndex == 0) {
            return width;
        }
        for (int row = 0; row < table.getRowCount(); row++) {
            Component cell = table.prepareRenderer(table.getCellRenderer(row, columnIndex), row, columnIndex);
            if (cell != null) {
                width = Math.max(width, cell.getPreferredSize().width);
            }
        }
        return Math.max(28, width);
    }

    private static int peerHeaderPreferredWidth(JTable table, int columnIndex) {
        javax.swing.table.JTableHeader header = table.getTableHeader();
        if (header == null || header.getDefaultRenderer() == null) {
            return 28;
        }
        javax.swing.table.TableColumn column = table.getColumnModel().getColumn(columnIndex);
        Component painted = header.getDefaultRenderer().getTableCellRendererComponent(
                table, column.getHeaderValue(), false, false, -1, columnIndex);
        if (painted == null) {
            return 28;
        }
        return Math.max(28, painted.getPreferredSize().width);
    }

    private static void applyPeerColumnWidths(JTable table, int idWidth, int[] tail) {
        javax.swing.table.TableColumnModel model = table.getColumnModel();
        setPeerColumnWidth(model.getColumn(0), Math.max(28, idWidth));
        for (int i = 0; i < tail.length; i++) {
            setPeerColumnWidth(model.getColumn(i + 1), Math.max(28, tail[i]));
        }
    }

    private static void setPeerColumnWidth(javax.swing.table.TableColumn column, int width) {
        if (column.getWidth() != width) {
            column.setWidth(width);
        }
        if (column.getPreferredWidth() != width) {
            column.setPreferredWidth(width);
        }
    }

    private static void fitPeerIdLabel(JLabel label, JTable table, boolean self, Font boldFont, Font mainFont) {
        int available = table.getColumnModel().getColumn(0).getWidth();
        if (available <= 0) {
            return;
        }
        int size = mainFont.getSize();
        while (size > 10 && label.getPreferredSize().width > available) {
            size--;
            label.setFont(self ? UiFonts.chineseBold(size) : UiFonts.chinesePlain(size));
        }
        if (label.getPreferredSize().width > available && label.getIcon() != null) {
            label.setIcon(null);
            size = mainFont.getSize();
            label.setFont(self ? boldFont : mainFont);
            while (size > 10 && label.getPreferredSize().width > available) {
                size--;
                label.setFont(self ? UiFonts.chineseBold(size) : UiFonts.chinesePlain(size));
            }
        }
    }

    private static Icon friendFaceIcon(String id, String encoded, boolean online) {
        int size = 18;
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
                g.setFont(UiFonts.chineseBold(11));
                String letter = faceLetter(id);
                FontMetrics metrics = g.getFontMetrics();
                int x = (size - metrics.stringWidth(letter)) / 2;
                int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(letter, x, y);
            }
            int dot = 7;
            g.setColor(new Color(28, 20, 48));
            g.fillOval(size - dot - 1, size - dot - 1, dot, dot);
            g.setColor(online ? new Color(120, 210, 150) : new Color(120, 108, 148));
            g.fillOval(size - dot + 1, size - dot + 1, dot - 4, dot - 4);
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
        public JButton openCloudSettingsButton;
        public JLabel peerHintLabel;
        public JLabel peerStatusLabel;
        public JComboBox<String> clientIdCombo;
        public JButton clientIdApplyButton;
        public JLabel clientIdSavedLabel;
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
        CandyComboBox.dress(refs.urlCombo);
        refs.urlCombo.setPrototypeDisplayValue("https://www.msn.com/zh-tw");
        refs.buttonIdCombo = RecentValuesHelper.createCombo(
                fieldFont, "finance",
                "要點擊的按鈕 CSS Selector 或 id；已啟用槽位使用啟用當下鎖定的值");
        CandyComboBox.dress(refs.buttonIdCombo);
        refs.buttonIdCombo.setPrototypeDisplayValue("#finance");
        refs.browserCombo = new JComboBox<>(TaskEditDialog.BROWSER_OPTIONS);
        refs.browserCombo.setFont(fieldFont);
        refs.browserCombo.setPrototypeDisplayValue("Chromium（內建）");
        CandyComboBox.install(refs.browserCombo);
        TaskEditDialog.attachBrowserTooltips(refs.browserCombo);
        refs.executeNowButton = new CandyButton("立即執行", CandyButton.Variant.PRIMARY, CandyButton.Size.MEDIUM);
        refs.executeNowButton.setFont(boldFont);
        refs.executeNowButton.setToolTipText("使用上方共用設定立即測試，不會變更已啟用槽位的鎖定設定");

        lockFieldHeight(refs.urlCombo);
        sharedContent.add(formRowStretch(labeled(mainFont, "目標網址："), refs.urlCombo));
        sharedContent.add(Box.createVerticalStrut(14));
        sharedContent.add(selectorBrowserActionRow(mainFont, fieldFont,
                refs.buttonIdCombo, refs.browserCombo, refs.executeNowButton));

        JPanel shared = createCollapsibleGroupPanel("共用排程設定", sharedContent, boldFont, false);

        refs.skipDatesButton = new CandyButton("跳過日期", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        refs.skipDatesButton.setFont(mainFont);
        refs.skipDatesButton.setToolTipText("指定日期不執行；時分維持原設定");

        refs.workIn = createSlotCard(WorkSlot.WORK_IN_NAME, mainFont, boldFont, refs.skipDatesButton);

        GridBagConstraints rootGbc = new GridBagConstraints();
        rootGbc.gridx = 0;
        rootGbc.weightx = 1.0;
        rootGbc.fill = GridBagConstraints.HORIZONTAL;
        rootGbc.insets = new Insets(0, 0, 12, 0);

        rootGbc.gridy = 0;
        root.add(shared, rootGbc);
        rootGbc.gridy = 1;
        rootGbc.insets = new Insets(4, 0, 12, 0);
        root.add(refs.workIn.panel, rootGbc);

        return root;
    }

    /**
     * 打卡歷史簡易列表（最近 5～10 筆），放在排程卡片下方。
     */
    public static JPanel createCheckInHistoryPanel(
            CheckInHistoryRefs refs, Font mainFont, Font boldFont) {
        JPanel panel = new CandyPanel(
                "執行記錄（最近 " + CheckInHistoryService.MAX_ENTRIES + " 筆）", boldFont);
        panel.setLayout(new BorderLayout(0, 10));

        refs.listModel = new DefaultListModel<>();
        refs.historyList = new CandyHistoryColumns.View(refs.listModel);
        refs.historyList.setFont(mainFont);
        refs.historyList.setVisibleRowCount(HISTORY_VISIBLE_ROWS);
        refs.historyList.setToolTipText("本機最近排程成功／失敗紀錄（不含取消）");

        JPanel historyBody = CandyHistoryColumns.wrapWithDetail(refs.historyList);
        JScrollPane scroll = new JScrollPane(historyBody);
        CandyHistoryColumns.attach(scroll, boldFont);
        // 欄位在格子裡省略，完整內容在提示。不要為了長字把整頁撐寬。
        // 高度含欄位標題，整頁仍一起捲；標題若算進列高，畫面上就只剩一筆。
        int rowHeight = refs.historyList.getFixedCellHeight();
        int header = scroll.getColumnHeader() == null
                ? 0
                : scroll.getColumnHeader().getPreferredSize().height;
        scroll.setPreferredSize(new Dimension(120, header + rowHeight * HISTORY_VISIBLE_ROWS));
        scroll.setMinimumSize(new Dimension(80, header + rowHeight * 4));
        panel.add(scroll, BorderLayout.CENTER);

        refs.emptyLabel = new CompactShellPanels.WrappingLabel(
                "尚無執行紀錄；排程或「立即執行」完成後會顯示在此");
        refs.emptyLabel.setHorizontalAlignment(SwingConstants.LEFT);
        refs.emptyLabel.setFont(mainFont);
        refs.emptyLabel.setForeground(NightTheme.MUTED);

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBorder(new EmptyBorder(6, 0, 4, 0));
        refs.emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(refs.emptyLabel);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        refs.clearButton = new CandyButton("清除紀錄", CandyButton.Variant.DANGER, CandyButton.Size.MEDIUM);
        refs.clearButton.setFont(boldFont);
        refs.clearButton.setToolTipText("清空本機執行歷史（不影響目前排程）");
        actions.add(refs.clearButton);
        pinRow(actions);
        south.add(actions);
        panel.add(south, BorderLayout.SOUTH);

        return panel;
    }

    public static class CheckInHistoryRefs {
        public DefaultListModel<String> listModel;
        public CandyHistoryColumns.View historyList;
        public JLabel emptyLabel;
        public JButton clearButton;
    }

    private static SlotCardRefs createSlotCard(String title, Font mainFont, Font boldFont, JButton skipDatesButton) {
        SlotCardRefs refs = new SlotCardRefs();
        refs.panel = new CandyPanel();
        refs.panel.setLayout(new BorderLayout(0, 4));

        refs.nameField = new CandyTextField(title, 10);
        refs.nameField.setFont(mainFont);
        refs.nameField.setToolTipText("這個排程的名稱。改完後，本機和後台的任務卡都會顯示這個名字。最多 24 個字。");
        int nameHeight = Math.max(30, refs.nameField.getPreferredSize().height);
        lockComponentSize(refs.nameField, new Dimension(136, nameHeight));
        refs.nameApplyButton = candyButton("套用", CandyButton.Variant.SECONDARY, mainFont,
                "儲存排程名稱（也可按 Enter）");
        refs.nameApplyButton.setEnabled(false);
        refs.nameSavedLabel = new JLabel("已儲存");
        refs.nameSavedLabel.setFont(mainFont);
        refs.nameSavedLabel.setForeground(new Color(144, 230, 176));
        refs.nameSavedLabel.setVisible(false);
        JPanel nameRow = wrapFlowRow(4, 4,
                labeled(mainFont, "名稱"), refs.nameField, refs.nameApplyButton, refs.nameSavedLabel);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        refs.enabledCheckBox = new CandyCheckBox("啟用", true);
        refs.enabledCheckBox.setFont(boldFont);
        refs.enabledCheckBox.setToolTipText("勾選後開始自動排程，並鎖定時分與目標網址／Selector；取消勾選後才能修改時間");

        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format("%02d", i);
        refs.hourCombo = new JComboBox<>(hours);
        refs.hourCombo.setFont(mainFont);
        refs.hourCombo.setPrototypeDisplayValue("00");
        refs.hourCombo.setToolTipText("預定執行的小時（00–23）");
        CandyComboBox.install(refs.hourCombo);
        lockComboSize(refs.hourCombo, 56);

        String[] minutes = new String[60];
        for (int i = 0; i < 60; i++) minutes[i] = String.format("%02d", i);
        refs.minuteCombo = new JComboBox<>(minutes);
        refs.minuteCombo.setFont(mainFont);
        refs.minuteCombo.setPrototypeDisplayValue("00");
        refs.minuteCombo.setToolTipText("預定執行的分鐘（00–59）");
        CandyComboBox.install(refs.minuteCombo);
        lockComboSize(refs.minuteCombo, 56);

        refs.randomOffsetCheckBox = new CandyCheckBox("±5 分隨機", true);
        refs.randomOffsetCheckBox.setFont(mainFont);
        refs.randomOffsetCheckBox.setToolTipText("在設定時間前後隨機 ±5 分鐘，避免每天固定同一秒執行");

        JLabel hourUnit = new JLabel("時");
        hourUnit.setFont(mainFont);
        JLabel minUnit = new JLabel("分");
        minUnit.setFont(mainFont);
        JPanel hourGroup = inlinePair(refs.hourCombo, hourUnit);
        JPanel minuteGroup = inlinePair(refs.minuteCombo, minUnit);
        JPanel scheduleRow = wrapFlowRow(1, 4,
                refs.enabledCheckBox,
                refs.randomOffsetCheckBox,
                hourGroup,
                minuteGroup);
        if (skipDatesButton != null) {
            nameRow.add(skipDatesButton);
            installSkipDatesPlacement(scheduleRow, nameRow, skipDatesButton);
        }

        JPanel settings = new JPanel();
        settings.setOpaque(false);
        settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
        settings.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(scheduleRow);

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
        mgbc.weightx = 0.5;
        mgbc.gridx = 0;
        statusGrid.add(createMetricCell("狀態", refs.statusLabel, mainFont), mgbc);
        mgbc.gridx = 1;
        statusGrid.add(createMetricCell("倒數", refs.countdownLabel, mainFont), mgbc);
        mgbc.gridy = 1;
        mgbc.gridx = 0;
        statusGrid.add(createMetricCell("預計觸發", refs.triggerLabel, mainFont), mgbc);
        mgbc.gridx = 1;
        mgbc.insets = new Insets(2, 0, 2, 0);
        statusGrid.add(createMetricCell("結果", refs.resultLabel, mainFont), mgbc);
        mgbc.gridy = 2;
        mgbc.gridx = 0;
        mgbc.gridwidth = 2;
        mgbc.weightx = 1.0;
        mgbc.insets = new Insets(2, 0, 2, 0);
        statusGrid.add(createMetricCell("鎖定設定", refs.lockedSettingsLabel, mainFont), mgbc);
        int statusH = statusGrid.getPreferredSize().height;
        statusGrid.setMinimumSize(new Dimension(0, statusH));
        statusGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, statusH));

        settings.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel mainCol = new JPanel(new BorderLayout(0, 12));
        mainCol.setOpaque(false);
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(nameRow);
        head.add(Box.createVerticalStrut(8));
        head.add(settings);
        mainCol.add(head, BorderLayout.NORTH);
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
        public JButton nameApplyButton;
        public JLabel nameSavedLabel;
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
        refs.logTextArea.setFont(UiFonts.latinPlain(12));
        CandyTextField.dressArea(refs.logTextArea);

        JScrollPane scrollPane = new JScrollPane(refs.logTextArea);
        markCandyScroll(scrollPane);
        logPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel logActionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        logActionPanel.setOpaque(false);
        refs.clearLogButton = candyButton("清除 Log", CandyButton.Variant.DANGER, boldFont, "清空日誌內容");
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

    /**
     * 寬度夠就維持原本字級；不夠就逐級縮小，盡量排成同一橫列。
     * 最小字級仍放不下就換行，不再縮到看不清。
     */
    private static void installSingleLineFont(JPanel row, int maxSize, int minSize) {
        Component[] kids = row.getComponents();
        Font[] fonts = new Font[kids.length];
        for (int i = 0; i < kids.length; i++) {
            fonts[i] = kids[i].getFont();
        }
        row.putClientProperty("wrap.fit", (Runnable) () -> {
            if (Boolean.TRUE.equals(row.getClientProperty("lineFitBusy"))) {
                return;
            }
            int width = row.getWidth();
            Object hinted = row.getClientProperty("wrap.width");
            if (hinted instanceof Integer && (Integer) hinted > 0) {
                width = (Integer) hinted;
            } else if (row.getLayout() instanceof WrapLayout) {
                width = ((WrapLayout) row.getLayout()).targetWidth(row);
            }
            if (width <= 0 || width == Integer.MAX_VALUE) {
                return;
            }
            row.putClientProperty("lineFitBusy", Boolean.TRUE);
            try {
                int chosen = minSize;
                for (int size = maxSize; size >= minSize; size--) {
                    applyPointSize(kids, fonts, size);
                    if (singleLineOuterWidth(row) <= width) {
                        chosen = size;
                        break;
                    }
                }
                applyPointSize(kids, fonts, chosen);
            } finally {
                row.putClientProperty("lineFitBusy", Boolean.FALSE);
            }
        });
    }

    private static void applyPointSize(Component[] kids, Font[] fonts, int size) {
        for (int i = 0; i < kids.length; i++) {
            kids[i].setFont(fonts[i].deriveFont((float) size));
        }
    }

    private static int singleLineOuterWidth(JPanel row) {
        int hgap = 0;
        if (row.getLayout() instanceof FlowLayout) {
            hgap = ((FlowLayout) row.getLayout()).getHgap();
        }
        int used = 0;
        int seen = 0;
        for (Component child : row.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (seen > 0) {
                used += hgap;
            }
            used += child.getPreferredSize().width;
            seen++;
        }
        return used + hgap * 2;
    }

    private static JPanel wrapFlowRow(Component... components) {
        return wrapFlowRow(8, 4, components);
    }

    /**
     * 橫向排列，寬度不夠就換行，高度跟著目前寬度長高。
     * 不要在這裡把高度鎖成組裝當下的一行，否則第二列會被裁掉。
     */
    private static JPanel wrapFlowRow(int hgap, int vgap, Component... components) {
        JPanel row = new WrappingRow(hgap, vgap);
        for (Component component : components) {
            row.add(component);
        }
        return row;
    }

    /** 時與分貼在下拉旁邊，不要再被外層間距拆開。 */
    private static JPanel inlinePair(Component left, Component right) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(left);
        row.add(right);
        pinRow(row);
        return row;
    }

    /**
     * 跳過日期優先跟時分同一列；該列放不下就放到名稱列右側。
     * 寬度在排版後才知道，所以等這一列真的有寬度再決定。
     */
    private static void installSkipDatesPlacement(JPanel scheduleRow, JPanel nameRow, JButton skip) {
        Runnable place = () -> {
            int width = scheduleRow.getWidth();
            JPanel target = (width > 0 && skipFitsOnSchedule(scheduleRow, skip, width))
                    ? scheduleRow : nameRow;
            if (skip.getParent() == target) {
                return;
            }
            if (skip.getParent() != null) {
                skip.getParent().remove(skip);
            }
            target.add(skip);
            scheduleRow.revalidate();
            nameRow.revalidate();
        };
        scheduleRow.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                place.run();
            }
        });
        skip.addPropertyChangeListener("text", event -> place.run());
    }

    private static boolean skipFitsOnSchedule(JPanel scheduleRow, JButton skip, int width) {
        int hgap = 0;
        if (scheduleRow.getLayout() instanceof FlowLayout) {
            hgap = ((FlowLayout) scheduleRow.getLayout()).getHgap();
        }
        int max = width - hgap * 2;
        int used = 0;
        int seen = 0;
        for (Component child : scheduleRow.getComponents()) {
            if (!child.isVisible() || child == skip) {
                continue;
            }
            if (seen > 0) {
                used += hgap;
            }
            used += child.getPreferredSize().width;
            seen++;
        }
        if (seen > 0) {
            used += hgap;
        }
        used += skip.getPreferredSize().width;
        return used <= max;
    }

    /**
     * 換行列。preferred／maximum 高度跟目前寬度走，BoxLayout 才不會把第二列裁掉。
     */
    private static int relayoutDepth;

    /** 這一列長高之後，把同層後面的元件往下推，並把高度一路補到捲動內容。 */
    private static void pushDown(Component grown, int extra) {
        Container parent = grown.getParent();
        if (parent == null) {
            return;
        }
        boolean after = false;
        for (Component kid : parent.getComponents()) {
            if (kid == grown) {
                after = true;
                continue;
            }
            if (after && kid.isVisible()) {
                kid.setLocation(kid.getX(), kid.getY() + extra);
            }
        }
        if (parent instanceof javax.swing.JViewport) {
            javax.swing.JViewport viewport = (javax.swing.JViewport) parent;
            Dimension want = grown.getSize();
            Dimension have = viewport.getViewSize();
            if (have == null || have.height + 1 < want.height) {
                viewport.setViewSize(want);
            }
            return;
        }
        Insets insets = parent.getInsets();
        int bottom = insets.top;
        for (Component kid : parent.getComponents()) {
            if (!kid.isVisible()) {
                continue;
            }
            bottom = Math.max(bottom, kid.getY() + kid.getHeight());
        }
        bottom += insets.bottom;
        int grow = bottom - parent.getHeight();
        if (grow <= 1) {
            return;
        }
        parent.setSize(parent.getWidth(), bottom);
        pushDown(parent, grow);
    }

    private static final class WrappingRow extends JPanel {
        WrappingRow(int hgap, int vgap) {
            super(new WrapLayout(FlowLayout.LEFT, hgap, vgap));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public void doLayout() {
            putClientProperty("wrap.width", getWidth());
            runFit();
            super.doLayout();
            growIfClipped();
        }

        /**
         * 父層問高度時寬度常常還是 0 或上一輪的，先被排成一行。
         * 這裡寬度已經確定，高度不夠就自己長高，並把後面的元件往下推。
         */
        private void growIfClipped() {
            if (relayoutDepth > 0 || getWidth() <= 0) {
                return;
            }
            int need = heightForWidth(getWidth());
            int extra = need - getHeight();
            if (extra <= 1) {
                return;
            }
            relayoutDepth++;
            try {
                setBounds(getX(), getY(), getWidth(), need);
                pushDown(this, extra);
            } finally {
                relayoutDepth--;
            }
        }

        private int heightForWidth(int width) {
            putClientProperty("wrap.width", width);
            runFit();
            if (getLayout() instanceof WrapLayout) {
                return ((WrapLayout) getLayout()).preferredLayoutSize(this, width).height;
            }
            return getPreferredSize().height;
        }

        @Override
        public Dimension getPreferredSize() {
            int width = getLayout() instanceof WrapLayout
                    ? ((WrapLayout) getLayout()).targetWidth(this) : getWidth();
            putClientProperty("wrap.width", width);
            runFit();
            return getLayout().preferredLayoutSize(this);
        }

        private void runFit() {
            Object hook = getClientProperty("wrap.fit");
            if (hook instanceof Runnable) {
                ((Runnable) hook).run();
            }
        }

        @Override
        public Dimension getMinimumSize() {
            Dimension min = getPreferredSize();
            min.width = 0;
            return min;
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension max = getPreferredSize();
            max.width = Integer.MAX_VALUE;
            return max;
        }
    }

    /** 一項一列，避免 380px 寬把勾選與按鈕擠到畫面外。 */
    private static JPanel formRow(Component... components) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        int h = 0;
        int w = 0;
        boolean first = true;
        for (Component component : components) {
            if (!first) {
                row.add(Box.createVerticalStrut(4));
                h += 4;
            }
            first = false;
            if (component instanceof JComponent) {
                ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            row.add(component);
            Dimension pref = component.getPreferredSize();
            h += pref.height;
            w = Math.max(w, pref.width);
        }
        h = Math.max(h, 28);
        row.setMinimumSize(new Dimension(0, h));
        row.setPreferredSize(new Dimension(Math.min(w, 300), h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return row;
    }

    /** 橫列只鎖高度，最小寬度歸零，讓小方塊視窗可以把整列縮進視埠。 */
    private static void pinRow(JComponent row) {
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension pref = row.getPreferredSize();
        int h = Math.max(pref.height, 24);
        row.setMinimumSize(new Dimension(0, h));
        row.setPreferredSize(new Dimension(Math.min(pref.width, 300), h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    /** Selector、瀏覽器、立即執行各佔一列，380px 內看得到也能點。 */
    private static JPanel selectorBrowserActionRow(
            Font labelFont, Font fieldFont,
            JComponent selectorField, JComboBox<?> browserCombo, JButton executeButton) {
        final int rowH = 28;

        selectorField.setFont(fieldFont);
        browserCombo.setFont(fieldFont);
        int fieldH = Math.max(rowH, selectorField.getPreferredSize().height);
        fieldH = Math.max(fieldH, browserCombo.getPreferredSize().height);
        lockComponentSize(selectorField, new Dimension(240, fieldH));

        int browserW = Math.max(browserCombo.getPreferredSize().width, 148);
        browserW = Math.min(browserW, 260);
        lockComponentSize(browserCombo, new Dimension(browserW, fieldH));

        Dimension buttonPref = executeButton.getPreferredSize();
        int buttonW = Math.max(buttonPref.width, 96);
        int buttonH = executeButton instanceof CandyButton ? buttonPref.height : rowH;
        lockComponentSize(executeButton, new Dimension(buttonW, buttonH));
        executeButton.setMargin(new Insets(0, 8, 0, 8));

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(stackedField(labeled(labelFont, "Selector："), selectorField));
        column.add(Box.createVerticalStrut(6));
        column.add(stackedField(labeled(labelFont, "瀏覽器："), browserCombo));
        column.add(Box.createVerticalStrut(6));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(executeButton);
        pinRow(buttonRow);
        column.add(buttonRow);
        return column;
    }

    private static JPanel stackedField(JLabel label, JComponent field) {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(label);
        block.add(Box.createVerticalStrut(2));
        block.add(field);
        int h = label.getPreferredSize().height + field.getPreferredSize().height + 4;
        int w = Math.max(label.getPreferredSize().width, field.getPreferredSize().width);
        block.setMinimumSize(new Dimension(0, h));
        block.setPreferredSize(new Dimension(Math.min(w, 280), h));
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return block;
    }

    private static void lockComponentSize(JComponent component, Dimension size) {
        component.setPreferredSize(size);
        component.setMinimumSize(size);
        component.setMaximumSize(size);
    }

    /** 標籤在上、輸入欄在下，欄位吃滿小方塊寬度。 */
    private static JPanel formRowStretch(JComponent label, JComponent field) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        lockFieldHeight(field);
        row.add(label);
        row.add(Box.createVerticalStrut(2));
        row.add(field);
        int h = label.getPreferredSize().height + field.getPreferredSize().height + 8;
        row.setMinimumSize(new Dimension(0, h));
        row.setPreferredSize(new Dimension(280, h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return row;
    }

    /** 鎖定下拉選單尺寸，避免窄欄位擠壓時分選單。中文數字需要比「00」的測量值更寬。 */
    private static void lockComboSize(JComboBox<?> combo, int minWidth) {
        Dimension size = combo.getPreferredSize();
        size = new Dimension(Math.max(size.width, minWidth), Math.max(size.height, 30));
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
        JPanel outerPanel = new CandyPanel(12, 12, 12, 12);
        outerPanel.setLayout(new BorderLayout(0, 2));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerPanel.setOpaque(true);
        headerPanel.setBackground(CandyTheme.HEADER);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CandyTheme.EDGE),
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
                headerPanel.setBackground(CandyTheme.HEADER_HOT);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                headerPanel.setBackground(CandyTheme.HEADER);
            }
        });

        outerPanel.add(headerPanel, BorderLayout.NORTH);
        outerPanel.add(contentPanel, BorderLayout.CENTER);
        return outerPanel;
    }

    /**
     * 建立帶標題的圓角分組面板。
     */
    public static JPanel createGroupPanel(String title, Font titleFont) {
        CandyPanel panel = new CandyPanel(title, titleFont);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static CandyButton candyButton(String text, CandyButton.Variant variant, Font font, String tip) {
        CandyButton button = new CandyButton(text, variant, CandyButton.Size.SMALL);
        button.setFont(font);
        button.setToolTipText(tip);
        return button;
    }

    private static void markCandyScroll(JScrollPane scroll) {
        scroll.putClientProperty(CandyScrollBar.MARK, Boolean.TRUE);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(CandyTheme.EDGE));
    }
}
