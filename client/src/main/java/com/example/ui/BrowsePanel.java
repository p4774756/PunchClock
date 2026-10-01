package com.example.ui;

import com.example.service.WebBrowserService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 桌面端「瀏覽」分頁：用 Playwright 內建 Chromium 開網頁（可略過公司 Proxy 直連）。
 */
public final class BrowsePanel extends JPanel {

    public static final String TAB_LABEL = "瀏覽";
    public static final String DEFAULT_URL = "https://www.google.com.tw";

    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color TITLE = new Color(30, 41, 59);
    private static final Color BORDER = new Color(203, 213, 225, 160);
    private static final Color SURFACE = new Color(248, 250, 252, 140);

    private final WebBrowserService browserService;
    private final BooleanSupplier trustAllSsl;
    private final Runnable persistSettings;
    private final Consumer<String> logger;

    private final JComboBox<String> urlCombo;
    private final JCheckBox directCheckBox = new JCheckBox("直連（略過公司 Proxy）", true);
    private final JButton openButton = new JButton("開啟");
    private final JButton closeButton = new JButton("關閉瀏覽器");
    private final JLabel statusLabel = new JLabel("瀏覽器未開啟");
    private final JTextArea helpArea = new JTextArea();

    public BrowsePanel(Font mainFont, Font boldFont, Font fieldFont,
                       WebBrowserService browserService,
                       BooleanSupplier trustAllSsl,
                       Runnable persistSettings,
                       Consumer<String> logger) {
        this.browserService = browserService;
        this.trustAllSsl = trustAllSsl;
        this.persistSettings = persistSettings;
        this.logger = logger;

        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(8, 4, 8, 4));

        urlCombo = RecentValuesHelper.createCombo(fieldFont, DEFAULT_URL, "要開啟的網址；不加 http 會自動補 https://");

        JPanel group = PanelFactory.createGroupPanel("簡易瀏覽（內建 Chromium）", boldFont);
        group.setLayout(new BorderLayout(0, 6));
        group.add(controls(mainFont, boldFont), BorderLayout.NORTH);
        group.add(helpScroll(), BorderLayout.CENTER);
        add(group, BorderLayout.CENTER);

        refreshState();
    }

    private JPanel controls(Font mainFont, Font boldFont) {
        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

        JPanel urlRow = new JPanel(new BorderLayout(8, 4));
        urlRow.setOpaque(false);
        urlRow.setAlignmentX(LEFT_ALIGNMENT);
        JLabel urlLabel = new JLabel("網址：");
        urlLabel.setFont(mainFont);
        urlRow.add(urlLabel, BorderLayout.WEST);
        urlRow.add(urlCombo, BorderLayout.CENTER);
        urlRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JPanel actions = new JPanel(new WrapLayout(WrapLayout.LEFT, 6, 4));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);

        directCheckBox.setFont(mainFont);
        directCheckBox.setOpaque(false);
        directCheckBox.setToolTipText("勾選：不走公司 Proxy，跟心跳相同路線；下次啟動瀏覽器時生效");
        directCheckBox.addActionListener(e -> persistQuietly());

        openButton.setFont(boldFont);
        openButton.setMargin(new Insets(2, 14, 2, 14));
        openButton.setToolTipText("瀏覽器未開啟時啟動；已開啟時在新分頁開這個網址");
        openButton.addActionListener(e -> openUrl());

        closeButton.setFont(mainFont);
        closeButton.setMargin(new Insets(2, 10, 2, 10));
        closeButton.addActionListener(e -> {
            browserService.close();
            statusLabel.setText("正在關閉瀏覽器…");
        });

        urlCombo.addActionListener(e -> {
            if ("comboBoxEdited".equals(e.getActionCommand())) {
                openUrl();
            }
        });

        statusLabel.setFont(mainFont);
        statusLabel.setForeground(MUTED);

        actions.add(directCheckBox);
        actions.add(openButton);
        actions.add(closeButton);
        actions.add(statusLabel);

        north.add(urlRow);
        north.add(Box.createVerticalStrut(4));
        north.add(actions);
        return north;
    }

    private JScrollPane helpScroll() {
        helpArea.setEditable(false);
        helpArea.setLineWrap(true);
        helpArea.setWrapStyleWord(true);
        helpArea.setFont(UiFonts.chinesePlain(12));
        helpArea.setOpaque(true);
        helpArea.setBackground(SURFACE);
        helpArea.setForeground(TITLE);
        helpArea.setMargin(new Insets(8, 10, 8, 10));
        helpArea.setText(""
                + "開的是 Playwright 內建的 Chromium，不是電腦上的 Edge／Chrome。\n"
                + "視窗有自己的網址列，可直接輸入網址、開新分頁；關掉所有分頁即結束。\n"
                + "\n"
                + "・直連：略過公司 Proxy，走跟心跳相同的路線（變更後需重開瀏覽器）。\n"
                + "・「雲端設定」勾選「信任所有 SSL」時會忽略憑證錯誤（公司 SSL 攔截）。\n"
                + "・登入狀態與 Cookie 保存在 ~/.punchclock/browser-profile。\n"
                + "\n"
                + "提醒：公司會解開 HTTPS 檢查流量，這個瀏覽器的內容一樣看得到。");
        helpArea.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(helpArea);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        return scroll;
    }

    private void openUrl() {
        String raw = RecentValuesHelper.getValue(urlCombo);
        String url = WebBrowserService.normalizeUrl(raw);
        if (url.isEmpty()) {
            statusLabel.setText("網址無效（僅支援 http／https）");
            return;
        }
        boolean wasRunning = browserService.isRunning();
        boolean ignoreHttpsErrors = trustAllSsl != null && trustAllSsl.getAsBoolean();
        browserService.open(url, directCheckBox.isSelected(), ignoreHttpsErrors, logger,
                () -> SwingUtilities.invokeLater(this::refreshState));
        if (wasRunning) {
            statusLabel.setText("已在新分頁開啟");
        }
        persistQuietly();
    }

    private void refreshState() {
        boolean running = browserService.isRunning();
        closeButton.setEnabled(running);
        directCheckBox.setEnabled(!running);
        statusLabel.setText(running ? "瀏覽器執行中" : "瀏覽器未開啟");
    }

    public String getUrl() {
        return RecentValuesHelper.getValue(urlCombo);
    }

    public boolean isDirect() {
        return directCheckBox.isSelected();
    }

    public void applySettings(String url, List<String> recentUrls, boolean direct, Runnable onHistoryChanged) {
        String value = url != null && !url.isBlank() ? url : DEFAULT_URL;
        RecentValuesHelper.applyHistory(urlCombo, recentUrls, value);
        RecentValuesHelper.bindHistoryMenu(urlCombo, recentUrls, onHistoryChanged);
        directCheckBox.setSelected(direct);
    }

    private void persistQuietly() {
        if (persistSettings != null) {
            persistSettings.run();
        }
    }
}
