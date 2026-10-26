package com.example.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * 遊戲風格下拉。可套在既有的 {@link JComboBox} 上，不換掉資料模型與選取事件。
 * 帶「最近使用、可刪除」的網址／Selector 仍用原本的彈出清單，只換外皮。
 */
public final class CandyComboBox {

    public static final String MARK = "candy.combo";
    public static final String ARROW = "candy.arrow";

    private CandyComboBox() {
    }

    /** 一般下拉。可編輯時一併換輸入框外皮，不換掉編輯器本身。 */
    public static void install(JComboBox<?> combo) {
        combo.putClientProperty(MARK, Boolean.TRUE);
        combo.setLightWeightPopupEnabled(false);
        combo.setUI(new ComboUI());
        JTextComponent editor = editorOf(combo);
        if (editor != null) {
            attachEditor(combo, editor);
        }
        finish(combo, editor);
    }

    /**
     * 可編輯且已有自己彈出清單的下拉。不重設 UI，避免最近使用項目的刪除失效。
     */
    public static void dress(JComboBox<?> combo) {
        combo.putClientProperty(MARK, Boolean.TRUE);
        combo.setOpaque(false);
        combo.setBackground(CandyTheme.FIELD);
        combo.setForeground(CandyTheme.INK);
        combo.setBorder(new EmptyBorder(5, 12, 5, 4));
        JTextComponent editor = editorOf(combo);
        if (editor != null) {
            attachEditor(combo, editor);
        }
        finish(combo, editor);
    }

    /** 收合時顯示的文字。自訂 renderer（例如日期補零）的結果要保留。 */
    static String shownText(JComboBox<?> combo, Object value) {
        if (value == null) {
            return "";
        }
        javax.swing.ListCellRenderer<?> renderer = combo.getRenderer();
        if (renderer != null) {
            try {
                Component painted = renderLabel(renderer, value);
                if (painted instanceof javax.swing.JLabel) {
                    String text = ((javax.swing.JLabel) painted).getText();
                    if (text != null) {
                        return text;
                    }
                }
            } catch (RuntimeException ignored) {
                // 退回物件自己的文字
            }
        }
        return String.valueOf(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Component renderLabel(javax.swing.ListCellRenderer renderer, Object value) {
        return renderer.getListCellRendererComponent(new JList(), value, -1, false, false);
    }

    public static void paintChrome(JComboBox<?> combo, Graphics graphics) {
        int width = combo.getWidth();
        int height = combo.getHeight();
        boolean enabled = combo.isEnabled();
        boolean focused = combo.isFocusOwner() || editorFocused(combo);
        CandyDraw.field((Graphics2D) graphics, width, height, CandyTheme.hover(combo),
                focused, enabled, combo.isPopupVisible());
        if (combo.isEditable()) {
            return;
        }
        Object selected = combo.getSelectedItem();
        String text = shownText(combo, selected);
        Graphics2D g = CandyDraw.quality((Graphics2D) graphics);
        g.setFont(combo.getFont());
        g.setColor(enabled ? CandyTheme.INK : CandyTheme.MUTED);
        FontMetrics metrics = g.getFontMetrics();
        Insets insets = combo.getInsets();
        int textX = insets.left;
        int textWidth = Math.max(8, width - insets.left - 28);
        int textY = (height - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(CandyTheme.fit(text, metrics, textWidth), textX, textY);
        g.dispose();
    }

    static void installArrow(JButton button) {
        button.putClientProperty(ARROW, Boolean.TRUE);
        button.putClientProperty(CandyButton.MARK, Boolean.TRUE);
        button.setUI(new ArrowUI());
        button.setText("");
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusable(false);
        button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createEmptyBorder());
        Dimension size = new Dimension(22, 22);
        button.setPreferredSize(size);
        button.setMinimumSize(new Dimension(18, 18));
    }

    private static void finish(JComboBox<?> combo, JTextComponent editor) {
        combo.setOpaque(false);
        CandyTheme.watch(combo);
        for (Component child : combo.getComponents()) {
            if (child instanceof JButton) {
                installArrow((JButton) child);
                CandyTheme.watch(combo, child);
            }
        }
        if (editor != null) {
            CandyTheme.watch(combo, editor);
        }
    }

    private static boolean editorFocused(JComboBox<?> combo) {
        JTextComponent editor = editorOf(combo);
        return editor != null && editor.isFocusOwner();
    }

    private static void attachEditor(JComboBox<?> combo, JTextComponent editor) {
        CandyTextField.install(editor, false);
        if (Boolean.TRUE.equals(editor.getClientProperty("candy.combo.editor"))) {
            return;
        }
        editor.putClientProperty("candy.combo.editor", Boolean.TRUE);
        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                combo.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                combo.repaint();
            }
        });
    }

    private static JTextComponent editorOf(JComboBox<?> combo) {
        if (!combo.isEditable() || combo.getEditor() == null) {
            return null;
        }
        Component editor = combo.getEditor().getEditorComponent();
        return editor instanceof JTextComponent ? (JTextComponent) editor : null;
    }

    static final class ComboUI extends BasicComboBoxUI {
        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            comboBox.setOpaque(false);
            comboBox.setBackground(CandyTheme.FIELD);
            comboBox.setForeground(CandyTheme.INK);
            comboBox.setBorder(new EmptyBorder(5, 12, 5, 4));
            comboBox.setFocusable(true);
        }

        @Override
        public void update(Graphics g, JComponent c) {
            paint(g, c);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            paintChrome(comboBox, g);
        }

        @Override
        protected JButton createArrowButton() {
            JButton button = new JButton();
            installArrow(button);
            return button;
        }

        @Override
        public int getBaseline(JComponent c, int width, int height) {
            FontMetrics metrics = c.getFontMetrics(c.getFont());
            return (height - metrics.getHeight()) / 2 + metrics.getAscent();
        }

        @Override
        protected ComboPopup createPopup() {
            return new Popup(comboBox);
        }
    }

    static final class ArrowUI extends BasicButtonUI {
        @Override
        public void update(Graphics g, JComponent c) {
            paint(g, c);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            JButton button = (JButton) c;
            float hover = button.getModel().isRollover() ? 1f : 0f;
            if (button.getParent() instanceof JComponent) {
                hover = Math.max(hover, CandyTheme.hover((JComponent) button.getParent()));
            }
            java.awt.Color color = button.isEnabled()
                    ? CandyTheme.mix(CandyTheme.LAVENDER, CandyTheme.PINK_HI, hover)
                    : CandyTheme.MUTED;
            CandyDraw.chevron((Graphics2D) g, c.getWidth(), c.getHeight(), color);
        }
    }

    static final class Popup extends BasicComboPopup {
        @SuppressWarnings("unchecked")
        Popup(JComboBox<?> combo) {
            super((JComboBox<Object>) combo);
        }

        @Override
        protected void configureList() {
            super.configureList();
            list.setFont(comboBox.getFont());
            list.setFixedCellHeight(28);
            list.setBackground(CandyTheme.FIELD);
            list.setForeground(CandyTheme.INK);
            list.setSelectionBackground(CandyTheme.ROW_SELECT);
            list.setSelectionForeground(CandyTheme.INK);
            list.setCellRenderer(new RowRenderer());
        }

        @Override
        protected JScrollPane createScroller() {
            JScrollPane pane = super.createScroller();
            pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            pane.getViewport().setBackground(CandyTheme.FIELD);
            CandyScrollBar.install(pane);
            return pane;
        }

        @Override
        public void show() {
            int width = Math.max(comboBox.getWidth(), 140);
            int rows = Math.min(Math.max(comboBox.getItemCount(), 1), comboBox.getMaximumRowCount());
            Dimension size = new Dimension(width, rows * 28 + 8);
            scroller.setPreferredSize(size);
            setBorder(BorderFactory.createLineBorder(CandyTheme.EDGE));
            setBackground(CandyTheme.FIELD);
            super.show();
        }

        private final class RowRenderer extends javax.swing.DefaultListCellRenderer {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(
                        list, shownText(comboBox, value), index, isSelected, false);
                setOpaque(true);
                setFont(comboBox.getFont());
                setForeground(CandyTheme.INK);
                setBackground(isSelected
                        ? CandyTheme.ROW_SELECT
                        : (index % 2 == 0 ? CandyTheme.FIELD : CandyTheme.ROW_ALT));
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return this;
            }
        }
    }
}
