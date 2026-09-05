package com.example.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 可編輯下拉 + 最近使用紀錄（下拉項右側 × 刪除、右鍵管理）。
 */
public final class RecentValuesHelper {

    private static final String PROP_HISTORY = "recentValues.history";
    private static final String PROP_ON_CHANGED = "recentValues.onChanged";
    private static final int DELETE_HIT_WIDTH = 36;
    private static final int POPUP_MIN_WIDTH = 320;
    private static final int POPUP_ROW_HEIGHT = 28;
    /** 下拉至少顯示的行數（項目不足時下方留白，避免只能看到一行） */
    private static final int POPUP_MIN_VISIBLE_ROWS = 6;
    private static final int POPUP_MAX_VISIBLE_ROWS = 10;

    private RecentValuesHelper() {
    }

    public static JComboBox<String> createCombo(Font font, String defaultValue, String tooltip) {
        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);
        combo.setFont(font);
        combo.setToolTipText(tooltip + "（下拉可選最近用過的值；點 × 可刪除）");
        if (defaultValue != null && !defaultValue.isBlank()) {
            combo.addItem(defaultValue);
            combo.setSelectedItem(defaultValue);
        }
        Component editor = combo.getEditor().getEditorComponent();
        if (editor instanceof JTextField) {
            ((JTextField) editor).setFont(font);
        }
        installDeletableHistoryUi(combo);
        return combo;
    }

    public static void bindHistoryMenu(JComboBox<String> combo, List<String> history, Runnable onHistoryChanged) {
        combo.putClientProperty(PROP_HISTORY, history);
        combo.putClientProperty(PROP_ON_CHANGED, onHistoryChanged);
        installDeletableHistoryUi(combo);
        MouseAdapter menuHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            private void showPopup(MouseEvent e) {
                JPopupMenu menu = buildMenu(combo, history, onHistoryChanged);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        };
        combo.addMouseListener(menuHandler);
        Component editor = combo.getEditor().getEditorComponent();
        if (editor != null) {
            editor.addMouseListener(menuHandler);
        }
    }

    public static void applyHistory(JComboBox<String> combo, List<String> history, String currentValue) {
        combo.removeAllItems();
        LinkedHashSet<String> items = new LinkedHashSet<>();
        if (currentValue != null && !currentValue.isBlank()) {
            items.add(currentValue.trim());
        }
        if (history != null) {
            for (String entry : history) {
                if (entry != null && !entry.isBlank()) {
                    items.add(entry.trim());
                }
            }
        }
        for (String item : items) {
            combo.addItem(item);
        }
        setValue(combo, currentValue);
    }

    public static String getValue(JComboBox<String> combo) {
        Object selected = combo.getSelectedItem();
        if (selected != null) {
            String text = selected.toString().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        Component editor = combo.getEditor().getEditorComponent();
        if (editor instanceof JTextField) {
            return ((JTextField) editor).getText().trim();
        }
        return "";
    }

    public static void setValue(JComboBox<String> combo, String value) {
        String normalized = value != null ? value.trim() : "";
        ensureItem(combo, normalized);
        combo.setSelectedItem(normalized);
        Component editor = combo.getEditor().getEditorComponent();
        if (editor instanceof JTextField) {
            ((JTextField) editor).setText(normalized);
        }
    }

    public static void ensureItem(JComboBox<String> combo, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (value.equals(combo.getItemAt(i))) {
                return;
            }
        }
        combo.addItem(value);
    }

    public static void attachTextChangeListener(JComboBox<String> combo, Runnable onChange) {
        Component editor = combo.getEditor().getEditorComponent();
        if (!(editor instanceof JTextField)) {
            combo.addActionListener(e -> onChange.run());
            return;
        }
        JTextField textField = (JTextField) editor;
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange.run();
            }
        };
        textField.getDocument().addDocumentListener(listener);
        combo.addActionListener(e -> onChange.run());
    }

    public static void setEnabled(JComboBox<String> combo, boolean enabled) {
        combo.setEnabled(enabled);
        Component editor = combo.getEditor().getEditorComponent();
        if (editor != null) {
            editor.setEnabled(enabled);
        }
    }

    private static void installDeletableHistoryUi(JComboBox<String> combo) {
        if (Boolean.TRUE.equals(combo.getClientProperty("recentValues.uiInstalled"))) {
            return;
        }
        combo.putClientProperty("recentValues.uiInstalled", Boolean.TRUE);
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        combo.setLightWeightPopupEnabled(false);
        combo.setMaximumRowCount(POPUP_MAX_VISIBLE_ROWS);
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

            @Override
            protected ComboPopup createPopup() {
                return new DeletableHistoryComboPopup(comboBox);
            }
        });
        combo.setOpaque(true);
        combo.setBackground(Color.WHITE);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(0, 6, 0, 4)));
    }

    @SuppressWarnings("unchecked")
    private static void deleteDropdownItem(JComboBox<?> combo, int index) {
        JComboBox<String> stringCombo = (JComboBox<String>) combo;
        if (index < 0 || index >= stringCombo.getItemCount()) {
            return;
        }
        String removed = stringCombo.getItemAt(index);
        if (removed == null) {
            return;
        }
        List<String> history = (List<String>) combo.getClientProperty(PROP_HISTORY);
        Runnable onHistoryChanged = (Runnable) combo.getClientProperty(PROP_ON_CHANGED);
        String current = getValue(stringCombo);
        boolean removedCurrent = removed.equals(current);

        if (history != null) {
            history.removeIf(removed::equals);
        }
        stringCombo.removeItemAt(index);

        if (removedCurrent) {
            String fallback = stringCombo.getItemCount() > 0 ? stringCombo.getItemAt(0) : "";
            setValue(stringCombo, fallback);
        } else {
            setValue(stringCombo, current);
        }
        if (onHistoryChanged != null) {
            onHistoryChanged.run();
        }
    }

    private static final class DeletableHistoryComboPopup extends BasicComboPopup {

        private int hoverDeleteIndex = -1;

        DeletableHistoryComboPopup(JComboBox<Object> combo) {
            super(combo);
        }

        @Override
        protected JScrollPane createScroller() {
            JScrollPane pane = super.createScroller();
            pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            return pane;
        }

        @Override
        protected JList<Object> createList() {
            JList<Object> list = super.createList();
            list.putClientProperty("recentValues.popup", this);
            list.setFixedCellHeight(POPUP_ROW_HEIGHT);
            list.setCellRenderer(new DeleteHistoryListCellRenderer());
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (handleDeleteClick(list, e)) {
                        e.consume();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (handleDeleteClick(list, e)) {
                        e.consume();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHoverDeleteIndex(list, e);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverDeleteIndex = -1;
                    list.repaint();
                }
            });
            list.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHoverDeleteIndex(list, e);
                }
            });
            return list;
        }

        @Override
        public void show() {
            configurePopupSize();
            super.show();
            configurePopupSize();
        }

        private int visibleRowCountFor(int itemCount) {
            return Math.min(POPUP_MAX_VISIBLE_ROWS, Math.max(itemCount, POPUP_MIN_VISIBLE_ROWS));
        }

        private void configurePopupSize() {
            int itemCount = comboBox.getItemCount();
            if (itemCount <= 0) {
                return;
            }
            int popupWidth = comboBox.getWidth();
            if (popupWidth <= 0) {
                popupWidth = POPUP_MIN_WIDTH;
            }
            int visibleRows = visibleRowCountFor(itemCount);
            int height = visibleRows * POPUP_ROW_HEIGHT + 4;
            list.setFixedCellWidth(popupWidth);
            list.setFixedCellHeight(POPUP_ROW_HEIGHT);
            list.setVisibleRowCount(visibleRows);
            scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroller.setVerticalScrollBarPolicy(
                    itemCount > visibleRows
                            ? ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                            : ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            Dimension size = new Dimension(popupWidth, height);
            scroller.setPreferredSize(size);
            scroller.setMinimumSize(size);
            scroller.setMaximumSize(new Dimension(popupWidth, height));
            scroller.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
            setPreferredSize(size);
        }

        private void updateHoverDeleteIndex(JList<?> list, MouseEvent e) {
            int index = list.locationToIndex(e.getPoint());
            int next = isDeleteHit(list, index, e.getPoint()) ? index : -1;
            if (next != hoverDeleteIndex) {
                hoverDeleteIndex = next;
                list.putClientProperty("recentValues.hoverIndex", next);
                list.repaint();
            }
        }

        private boolean handleDeleteClick(JList<?> list, MouseEvent e) {
            int index = list.locationToIndex(e.getPoint());
            if (!isDeleteHit(list, index, e.getPoint())) {
                return false;
            }
            deleteDropdownItem(comboBox, index);
            SwingUtilities.invokeLater(() -> comboBox.showPopup());
            return true;
        }

        private boolean isDeleteHit(JList<?> list, int index, Point point) {
            if (index < 0) {
                return false;
            }
            Rectangle bounds = list.getCellBounds(index, index);
            return bounds != null && point.x >= bounds.x + bounds.width - DELETE_HIT_WIDTH;
        }
    }

    private static final class DeleteHistoryListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setFont(UiFonts.latinPlain(13));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, DELETE_HIT_WIDTH));

            int totalWidth = list.getFixedCellWidth() > 0 ? list.getFixedCellWidth() : POPUP_MIN_WIDTH;
            int textMax = Math.max(60, totalWidth - DELETE_HIT_WIDTH - 16);
            String text = value != null ? value.toString() : "";
            setText(truncateText(text, getFontMetrics(getFont()), textMax));
            setToolTipText(text);

            Integer hoverIndex = (Integer) list.getClientProperty("recentValues.hoverIndex");
            putClientProperty("recentValues.deleteHover", hoverIndex != null && hoverIndex == index);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            boolean deleteHover = Boolean.TRUE.equals(getClientProperty("recentValues.deleteHover"));
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setFont(UiFonts.latinBold(16));
                FontMetrics fm = g2.getFontMetrics();
                String mark = "\u00D7";
                int markWidth = fm.stringWidth(mark);
                int x = getWidth() - DELETE_HIT_WIDTH + (DELETE_HIT_WIDTH - markWidth) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(deleteHover ? new Color(220, 38, 38) : new Color(100, 116, 139));
                g2.drawString(mark, x, y);
            } finally {
                g2.dispose();
            }
        }

        private static String truncateText(String text, FontMetrics metrics, int maxWidth) {
            if (text == null || text.isEmpty() || metrics.stringWidth(text) <= maxWidth) {
                return text != null ? text : "";
            }
            String ellipsis = "…";
            for (int i = text.length() - 1; i > 0; i--) {
                String candidate = text.substring(0, i) + ellipsis;
                if (metrics.stringWidth(candidate) <= maxWidth) {
                    return candidate;
                }
            }
            return ellipsis;
        }
    }

    private static JPopupMenu buildMenu(JComboBox<String> combo, List<String> history, Runnable onHistoryChanged) {
        JPopupMenu menu = new JPopupMenu();
        String current = getValue(combo);

        JMenuItem removeItem = new JMenuItem("從歷史紀錄移除目前值");
        removeItem.setEnabled(history != null && history.contains(current));
        removeItem.addActionListener(e -> {
            if (history == null) {
                return;
            }
            history.removeIf(v -> v.equals(current));
            applyHistory(combo, history, current);
            if (onHistoryChanged != null) {
                onHistoryChanged.run();
            }
        });
        menu.add(removeItem);

        JMenuItem manageItem = new JMenuItem("管理歷史紀錄…");
        manageItem.addActionListener(e -> openManageDialog(combo, history, onHistoryChanged));
        menu.add(manageItem);
        return menu;
    }

    private static void openManageDialog(JComboBox<String> combo, List<String> history, Runnable onHistoryChanged) {
        Window owner = SwingUtilities.getWindowAncestor(combo);
        JDialog dialog = new JDialog(owner, "管理歷史紀錄", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.setContentPane(content);

        DefaultListModel<String> model = new DefaultListModel<>();
        if (history != null) {
            for (String entry : history) {
                model.addElement(entry);
            }
        }
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(UiFonts.latinPlain(13));
        content.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton deleteButton = new JButton("刪除選取");
        deleteButton.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) {
                return;
            }
            String removed = model.remove(idx);
            if (history != null) {
                history.remove(removed);
            }
        });
        actions.add(deleteButton);

        JButton clearButton = new JButton("清空全部");
        clearButton.addActionListener(e -> {
            model.clear();
            if (history != null) {
                history.clear();
            }
        });
        actions.add(clearButton);

        JButton closeButton = new JButton("完成");
        closeButton.addActionListener(e -> dialog.dispose());
        actions.add(closeButton);
        content.add(actions, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setMinimumSize(new Dimension(420, 280));
        dialog.setLocationRelativeTo(owner);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                applyHistory(combo, history, getValue(combo));
                if (onHistoryChanged != null) {
                    onHistoryChanged.run();
                }
            }
        });
        dialog.setVisible(true);
    }

    /** 供測試：從 combo 模型重建 history 列表（不含目前編輯中但未儲存的值） */
    static List<String> snapshotHistory(JComboBox<String> combo) {
        List<String> snapshot = new ArrayList<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            String item = combo.getItemAt(i);
            if (item != null && !item.isBlank()) {
                snapshot.add(item.trim());
            }
        }
        return snapshot;
    }
}
