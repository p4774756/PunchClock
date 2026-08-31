package com.example.ui;

import com.example.model.CheckInTask;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任務編輯/重新排定對話框
 * 負責顯示表單 UI 和驗證輸入，確認後透過 callback 回傳結果
 */
public class TaskEditDialog extends JDialog {

    /**
     * 對話框確認後的回傳結果
     */
    public static class Result {
        public final String name;
        public final String targetUrl;
        public final String buttonId;
        public final LocalDateTime targetTime;
        public final boolean useRandomOffset;
        public final String browserType;

        public Result(String name, String targetUrl, String buttonId,
                      LocalDateTime targetTime, boolean useRandomOffset, String browserType) {
            this.name = name;
            this.targetUrl = targetUrl;
            this.buttonId = buttonId;
            this.targetTime = targetTime;
            this.useRandomOffset = useRandomOffset;
            this.browserType = browserType;
        }
    }

    private Result result = null;

    /**
     * 建立並顯示任務編輯/重新排定對話框
     *
     * @param owner      父視窗
     * @param sourceTask 來源任務
     * @param isReuse    true=重新排定（建立新任務），false=編輯現有任務
     */
    public TaskEditDialog(JFrame owner, CheckInTask sourceTask, boolean isReuse) {
        super(owner, isReuse ? "重新排定任務" : "編輯任務", true);
        setSize(520, 420);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        Font dialogFont = UiFonts.chinesePlain(13);
        Font dialogBoldFont = UiFonts.chineseBold(13);
        Font dialogFieldFont = UiFonts.latinPlain(13);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(12, 16, 8, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 6, 5, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        // 任務名稱
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0.0;
        JLabel dlgNameLabel = new JLabel("任務名稱：");
        dlgNameLabel.setFont(dialogFont);
        formPanel.add(dlgNameLabel, gc);

        gc.gridx = 1; gc.gridy = 0; gc.weightx = 1.0;
        JTextField dlgNameField = new JTextField(sourceTask.getName());
        dlgNameField.setFont(dialogFont);
        formPanel.add(dlgNameField, gc);

        // 目標網址
        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0.0;
        JLabel dlgUrlLabel = new JLabel("目標網址：");
        dlgUrlLabel.setFont(dialogFont);
        formPanel.add(dlgUrlLabel, gc);

        gc.gridx = 1; gc.gridy = 1; gc.weightx = 1.0;
        JTextField dlgUrlField = new JTextField(sourceTask.getTargetUrl());
        dlgUrlField.setFont(dialogFieldFont);
        formPanel.add(dlgUrlField, gc);

        // 按鈕 Selector
        gc.gridx = 0; gc.gridy = 2; gc.weightx = 0.0;
        JLabel dlgBtnLabel = new JLabel("按鈕 Selector：");
        dlgBtnLabel.setFont(dialogFont);
        formPanel.add(dlgBtnLabel, gc);

        gc.gridx = 1; gc.gridy = 2; gc.weightx = 1.0;
        JTextField dlgBtnField = new JTextField(sourceTask.getButtonId());
        dlgBtnField.setFont(dialogFieldFont);
        formPanel.add(dlgBtnField, gc);

        // 排程日期
        gc.gridx = 0; gc.gridy = 3; gc.weightx = 0.0;
        JLabel dlgDateLabel = new JLabel("排程日期：");
        dlgDateLabel.setFont(dialogFont);
        formPanel.add(dlgDateLabel, gc);

        gc.gridx = 1; gc.gridy = 3; gc.weightx = 1.0;
        DatePickerSettings dlgDateSettings = new DatePickerSettings();
        dlgDateSettings.setAllowKeyboardEditing(true);
        DatePicker dlgDatePicker = new DatePicker(dlgDateSettings);
        LocalDateTime sourceTime = sourceTask.getTargetTime();
        if (isReuse) {
            dlgDatePicker.setDate(LocalDate.now().plusDays(1));
        } else if (sourceTime != null) {
            dlgDatePicker.setDate(sourceTime.toLocalDate());
        } else {
            dlgDatePicker.setDateToToday();
        }
        JButton dlgToggleBtn = dlgDatePicker.getComponentToggleCalendarButton();
        dlgToggleBtn.setPreferredSize(new Dimension(0, 0));
        dlgToggleBtn.setBorder(null);
        dlgDatePicker.getComponentDateTextField().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                SwingUtilities.invokeLater(dlgDatePicker::openPopup);
            }
        });
        formPanel.add(dlgDatePicker, gc);

        // 排程時間
        gc.gridx = 0; gc.gridy = 4; gc.weightx = 0.0;
        JLabel dlgTimeLabel = new JLabel("⏰ 排程時間：");
        dlgTimeLabel.setFont(dialogFont);
        formPanel.add(dlgTimeLabel, gc);

        gc.gridx = 1; gc.gridy = 4; gc.weightx = 1.0;
        JPanel dlgTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        String[] dlgHours = new String[24];
        for (int i = 0; i < 24; i++) dlgHours[i] = String.format("%02d", i);
        JComboBox<String> dlgHourCombo = new JComboBox<>(dlgHours);
        dlgHourCombo.setFont(dialogFont);

        String[] dlgMinutes = new String[60];
        for (int i = 0; i < 60; i++) dlgMinutes[i] = String.format("%02d", i);
        JComboBox<String> dlgMinuteCombo = new JComboBox<>(dlgMinutes);
        dlgMinuteCombo.setFont(dialogFont);

        if (sourceTime != null) {
            dlgHourCombo.setSelectedIndex(sourceTime.getHour());
            dlgMinuteCombo.setSelectedIndex(sourceTime.getMinute());
        }

        dlgTimePanel.add(dlgHourCombo);
        dlgTimePanel.add(new JLabel("時"));
        dlgTimePanel.add(dlgMinuteCombo);
        dlgTimePanel.add(new JLabel("分"));
        formPanel.add(dlgTimePanel, gc);

        // 隨機偏移
        gc.gridx = 0; gc.gridy = 5; gc.weightx = 0.0;
        JLabel dlgRandomLabel = new JLabel("隨機偏移：");
        dlgRandomLabel.setFont(dialogFont);
        formPanel.add(dlgRandomLabel, gc);

        gc.gridx = 1; gc.gridy = 5; gc.weightx = 1.0;
        JCheckBox dlgRandomCheckBox = new JCheckBox("啟用前後 ±5 分鐘隨機打卡", sourceTask.isUseRandomOffset());
        dlgRandomCheckBox.setFont(dialogBoldFont);
        dlgRandomCheckBox.setForeground(new Color(147, 51, 234));
        formPanel.add(dlgRandomCheckBox, gc);

        // 瀏覽器
        gc.gridx = 0; gc.gridy = 6; gc.weightx = 0.0;
        JLabel dlgBrowserLabel = new JLabel("瀏覽器：");
        dlgBrowserLabel.setFont(dialogFont);
        formPanel.add(dlgBrowserLabel, gc);

        gc.gridx = 1; gc.gridy = 6; gc.weightx = 1.0;
        String[] browserOptions = BROWSER_OPTIONS;
        JComboBox<String> dlgBrowserCombo = new JComboBox<>(browserOptions);
        dlgBrowserCombo.setFont(dialogFont);
        TaskEditDialog.attachBrowserTooltips(dlgBrowserCombo);
        String bt = sourceTask.getBrowserType();
        if ("chrome".equals(bt)) dlgBrowserCombo.setSelectedIndex(1);
        else if ("msedge".equals(bt)) dlgBrowserCombo.setSelectedIndex(0);
        else if ("chromium".equals(bt)) dlgBrowserCombo.setSelectedIndex(2);
        else if ("firefox".equals(bt)) dlgBrowserCombo.setSelectedIndex(3);
        else if ("webkit".equals(bt)) dlgBrowserCombo.setSelectedIndex(4);
        formPanel.add(dlgBrowserCombo, gc);

        add(formPanel, BorderLayout.CENTER);

        // 底部按鈕
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton confirmButton = new JButton(isReuse ? "確認排定" : "儲存變更");
        JButton cancelButton = new JButton("取消");
        confirmButton.setFont(dialogBoldFont);
        cancelButton.setFont(dialogFont);
        confirmButton.setBackground(new Color(37, 99, 235));
        confirmButton.setForeground(Color.BLACK);

        cancelButton.addActionListener(e -> dispose());
        confirmButton.addActionListener(e -> {
            String newName = dlgNameField.getText().trim();
            if (newName.isEmpty()) newName = "打卡任務";

            String newUrl = dlgUrlField.getText().trim();
            if (newUrl.isEmpty()) {
                JOptionPane.showMessageDialog(this, "請輸入目標打卡網址！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String newBtnId = dlgBtnField.getText().trim();
            if (newBtnId.isEmpty()) {
                JOptionPane.showMessageDialog(this, "請輸入打卡按鈕 Selector！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            LocalDate newDate = dlgDatePicker.getDate();
            if (newDate == null) {
                JOptionPane.showMessageDialog(this, "請選擇有效的日期！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int newHour = Integer.parseInt((String) dlgHourCombo.getSelectedItem());
            int newMinute = Integer.parseInt((String) dlgMinuteCombo.getSelectedItem());
            LocalDateTime newTargetTime = newDate.atTime(newHour, newMinute, 0);

            boolean newUseRandom = dlgRandomCheckBox.isSelected();
            String newBrowserType = parseBrowserType((String) dlgBrowserCombo.getSelectedItem());

            result = new Result(newName, newUrl, newBtnId, newTargetTime, newUseRandom, newBrowserType);
            dispose();
        });

        buttonPanel.add(cancelButton);
        buttonPanel.add(confirmButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * 顯示對話框並等待使用者操作，回傳結果（取消時為 null）
     */
    public Result showDialog() {
        setVisible(true); // modal，會 block 直到 dispose
        return result;
    }

    /**
     * 將 UI 瀏覽器顯示名稱轉換為內部代碼
     */
    public static final String[] BROWSER_OPTIONS = {
            "Edge（本機）",
            "Chrome（本機）",
            "Chromium（內建）",
            "Firefox（內建）",
            "WebKit（內建）"
    };

    public static String browserTooltip(String choice) {
        switch (parseBrowserType(choice)) {
            case "chrome":
                return "開啟電腦已安裝的 Google Chrome，可沿用書籤、Cookie 與登入狀態。";
            case "chromium":
                return "由打卡工具啟動內附的 Chromium，獨立視窗，不影響平常使用的瀏覽器。";
            case "firefox":
                return "由打卡工具啟動內附的 Firefox，獨立視窗，不影響平常使用的瀏覽器。";
            case "webkit":
                return "由打卡工具啟動內附的 WebKit（Safari 核心），獨立視窗，主要供 macOS 測試。";
            default:
                return "開啟電腦已安裝的 Microsoft Edge，可沿用書籤、Cookie 與登入狀態。";
        }
    }

    public static void attachBrowserTooltips(JComboBox<String> combo) {
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    setToolTipText(browserTooltip(value.toString()));
                }
                return this;
            }
        });
        Runnable refreshTooltip = () -> {
            Object selected = combo.getSelectedItem();
            combo.setToolTipText(selected != null ? browserTooltip(selected.toString()) : null);
        };
        combo.addActionListener(e -> refreshTooltip.run());
        combo.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (!(combo.getUI() instanceof javax.swing.plaf.basic.BasicComboBoxUI)) {
                    refreshTooltip.run();
                    return;
                }
                javax.swing.plaf.basic.ComboPopup popup =
                        ((javax.swing.plaf.basic.BasicComboBoxUI) combo.getUI()).getPopup();
                if (!combo.isPopupVisible() || popup == null) {
                    refreshTooltip.run();
                    return;
                }
                javax.swing.JList<?> list = popup.getList();
                java.awt.Point p = javax.swing.SwingUtilities.convertPoint(combo, e.getPoint(), list);
                int index = list.locationToIndex(p);
                if (index >= 0) {
                    combo.setToolTipText(browserTooltip(String.valueOf(list.getModel().getElementAt(index))));
                } else {
                    refreshTooltip.run();
                }
            }
        });
        refreshTooltip.run();
    }

    public static String normalizeBrowserChoice(String choice) {
        if (choice == null || choice.isBlank()) {
            return BROWSER_OPTIONS[0];
        }
        switch (parseBrowserType(choice)) {
            case "chrome":
                return BROWSER_OPTIONS[1];
            case "chromium":
                return BROWSER_OPTIONS[2];
            case "firefox":
                return BROWSER_OPTIONS[3];
            case "webkit":
                return BROWSER_OPTIONS[4];
            default:
                return BROWSER_OPTIONS[0];
        }
    }

    public static String parseBrowserType(String selectedBrowserStr) {
        if (selectedBrowserStr == null) return "msedge";
        if (selectedBrowserStr.contains("Chrome")) return "chrome";
        if (selectedBrowserStr.contains("Edge")) return "msedge";
        if (selectedBrowserStr.contains("Firefox")) return "firefox";
        if (selectedBrowserStr.contains("WebKit")) return "webkit";
        if (selectedBrowserStr.contains("Chromium")) return "chromium";
        return "msedge";
    }
}
