package com.example.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 編輯上班／下班共用的跳過日期。取消時回傳 null。
 * <p>
 * 日期用年月日下拉，不用 LGoodDatePicker。Mac 上那個月曆彈出層會疊字，
 * 日期欄也會畫成「年 月9日」。
 */
public class SkipDatesDialog extends JDialog {

    private static final DateTimeFormatter LABEL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd（E）", Locale.TAIWAN);

    private final JComboBox<Integer> yearCombo;
    private final JComboBox<Integer> monthCombo;
    private final JComboBox<Integer> dayCombo;
    private final DefaultListModel<LocalDate> listModel;
    private final JList<LocalDate> dateList;
    private boolean confirmed;
    private boolean adjustingDays;

    private SkipDatesDialog(Frame owner, List<LocalDate> initial) {
        super(owner, "跳過日期", true);
        setLayout(new BorderLayout(8, 8));

        Font font = UiFonts.chinesePlain(13);
        Font bold = UiFonts.chineseBold(13);
        Font fieldFont = UiFonts.latinPlain(13);
        LocalDate today = LocalDate.now();

        JLabel hint = new JLabel("這些日期不打卡，上班與下班一起跳過。時分維持原設定。");
        hint.setFont(font);
        hint.setBorder(new EmptyBorder(8, 12, 0, 12));
        add(hint, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBorder(new EmptyBorder(8, 12, 4, 12));

        int maxYear = today.getYear() + 1;
        if (initial != null) {
            for (LocalDate date : initial) {
                if (date != null && date.getYear() > maxYear) {
                    maxYear = date.getYear();
                }
            }
        }
        yearCombo = numberCombo(today.getYear(), maxYear, today.getYear(), false, fieldFont);
        monthCombo = numberCombo(1, 12, today.getMonthValue(), true, fieldFont);
        dayCombo = numberCombo(1, 31, today.getDayOfMonth(), true, fieldFont);
        refreshDays(today.getDayOfMonth());
        yearCombo.addActionListener(e -> refreshDays(selectedDay()));
        monthCombo.addActionListener(e -> refreshDays(selectedDay()));

        JButton addButton = new JButton("加入");
        addButton.setFont(bold);
        addButton.addActionListener(e -> addSelectedDate());

        JPanel addRow = new JPanel();
        addRow.setLayout(new BoxLayout(addRow, BoxLayout.X_AXIS));
        addRow.add(yearCombo);
        addRow.add(unitLabel("年", font));
        addRow.add(Box.createHorizontalStrut(8));
        addRow.add(monthCombo);
        addRow.add(unitLabel("月", font));
        addRow.add(Box.createHorizontalStrut(8));
        addRow.add(dayCombo);
        addRow.add(unitLabel("日", font));
        addRow.add(Box.createHorizontalStrut(12));
        addRow.add(addButton);
        center.add(addRow, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        if (initial != null) {
            for (LocalDate date : initial) {
                if (date != null && !listModel.contains(date)) {
                    listModel.addElement(date);
                }
            }
        }
        dateList = new JList<>(listModel);
        dateList.setFont(font);
        dateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dateList.setVisibleRowCount(6);
        dateList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String text = value instanceof LocalDate ? LABEL.format((LocalDate) value) : String.valueOf(value);
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        JScrollPane scroll = new JScrollPane(dateList);
        scroll.setPreferredSize(new Dimension(380, 150));
        center.add(scroll, BorderLayout.CENTER);

        JButton removeButton = new JButton("移除");
        removeButton.setFont(bold);
        removeButton.addActionListener(e -> removeSelected());
        JButton clearButton = new JButton("清除全部");
        clearButton.setFont(bold);
        clearButton.addActionListener(e -> clearAll());

        JPanel listActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        listActions.setOpaque(false);
        listActions.add(removeButton);
        listActions.add(clearButton);
        center.add(listActions, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JButton okButton = new JButton("完成");
        okButton.setFont(bold);
        okButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        JButton cancelButton = new JButton("取消");
        cancelButton.setFont(font);
        cancelButton.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBorder(new EmptyBorder(0, 12, 10, 12));
        buttons.add(cancelButton);
        buttons.add(okButton);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(addButton);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    /**
     * @return 確認後的日期（已排序）；取消為 null
     */
    public static List<LocalDate> showDialog(Frame owner, List<LocalDate> initial) {
        SkipDatesDialog dialog = new SkipDatesDialog(owner, initial);
        dialog.setVisible(true);
        if (!dialog.confirmed) {
            return null;
        }
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < dialog.listModel.size(); i++) {
            dates.add(dialog.listModel.get(i));
        }
        dates.sort(LocalDate::compareTo);
        return dates;
    }

    private void addSelectedDate() {
        LocalDate date = selectedDate();
        if (date == null) {
            UiFonts.showWarning(this, "請選擇有效的日期。", "跳過日期");
            return;
        }
        if (date.isBefore(LocalDate.now())) {
            UiFonts.showWarning(this, "不能加入今天以前的日期。", "跳過日期");
            return;
        }
        if (listModel.contains(date)) {
            UiFonts.showWarning(this, "這個日期已在清單中。", "跳過日期");
            return;
        }
        int index = 0;
        while (index < listModel.size() && listModel.get(index).isBefore(date)) {
            index++;
        }
        listModel.add(index, date);
        dateList.setSelectedIndex(index);
    }

    private void removeSelected() {
        int index = dateList.getSelectedIndex();
        if (index < 0) {
            UiFonts.showWarning(this, "請先選取要移除的日期。", "跳過日期");
            return;
        }
        listModel.remove(index);
        if (!listModel.isEmpty()) {
            dateList.setSelectedIndex(Math.min(index, listModel.size() - 1));
        }
    }

    private void clearAll() {
        if (listModel.isEmpty()) {
            return;
        }
        int confirm = UiFonts.showConfirm(
                this,
                "確定清除全部跳過日期？",
                "跳過日期",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            listModel.clear();
        }
    }

    private LocalDate selectedDate() {
        Integer year = (Integer) yearCombo.getSelectedItem();
        Integer month = (Integer) monthCombo.getSelectedItem();
        Integer day = (Integer) dayCombo.getSelectedItem();
        if (year == null || month == null || day == null) {
            return null;
        }
        int length = YearMonth.of(year, month).lengthOfMonth();
        if (day < 1 || day > length) {
            return null;
        }
        return LocalDate.of(year, month, day);
    }

    private int selectedDay() {
        Integer day = (Integer) dayCombo.getSelectedItem();
        return day == null ? 1 : day;
    }

    private void refreshDays(int preferredDay) {
        if (adjustingDays) {
            return;
        }
        Integer year = (Integer) yearCombo.getSelectedItem();
        Integer month = (Integer) monthCombo.getSelectedItem();
        if (year == null || month == null) {
            return;
        }
        int length = YearMonth.of(year, month).lengthOfMonth();
        int day = Math.min(Math.max(preferredDay, 1), length);
        adjustingDays = true;
        try {
            dayCombo.removeAllItems();
            for (int d = 1; d <= length; d++) {
                dayCombo.addItem(d);
            }
            dayCombo.setSelectedItem(day);
        } finally {
            adjustingDays = false;
        }
    }

    private static JLabel unitLabel(String text, Font font) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setBorder(new EmptyBorder(0, 4, 0, 0));
        return label;
    }

    private static JComboBox<Integer> numberCombo(int from, int to, int selected, boolean pad2, Font font) {
        JComboBox<Integer> combo = new JComboBox<>();
        for (int i = from; i <= to; i++) {
            combo.addItem(i);
        }
        combo.setFont(font);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String text = "";
                if (value instanceof Integer) {
                    int number = (Integer) value;
                    text = pad2 ? String.format("%02d", number) : Integer.toString(number);
                }
                Component component = super.getListCellRendererComponent(
                        list, text, index, isSelected, cellHasFocus);
                component.setFont(font);
                return component;
            }
        });
        combo.setSelectedItem(selected);
        combo.setPrototypeDisplayValue(pad2 ? 31 : 2099);
        Dimension size = combo.getPreferredSize();
        combo.setMinimumSize(size);
        combo.setPreferredSize(size);
        combo.setMaximumSize(size);
        return combo;
    }
}
