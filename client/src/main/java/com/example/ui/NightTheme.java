package com.example.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.Scrollable;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 跟首頁同一套深紫色。只改顏色與可讀性，不改元件做的事。
 */
public final class NightTheme {

    public static final Color INK = new Color(255, 252, 255);
    public static final Color MUTED = new Color(214, 204, 236);
    public static final Color PINK = new Color(255, 122, 168);
    public static final Color PINK_INK = new Color(48, 18, 36);
    public static final Color PANEL = new Color(36, 26, 64);
    public static final Color FIELD = new Color(58, 46, 102);
    public static final Color HEADER = new Color(48, 36, 86);
    public static final Color EDGE = new Color(150, 128, 198);
    public static final Color SELECT = new Color(255, 122, 168);

    private NightTheme() {
    }

    /** 新建元件時採用的顏色。不要動字型。 */
    public static void installLook() {
        UIManager.put("Panel.background", PANEL);
        UIManager.put("Label.foreground", INK);
        UIManager.put("CheckBox.foreground", INK);
        UIManager.put("RadioButton.foreground", INK);
        UIManager.put("Button.background", PINK);
        UIManager.put("Button.foreground", PINK_INK);
        UIManager.put("TextField.background", FIELD);
        UIManager.put("TextField.foreground", INK);
        UIManager.put("TextField.caretForeground", INK);
        UIManager.put("TextField.inactiveForeground", MUTED);
        UIManager.put("PasswordField.background", FIELD);
        UIManager.put("PasswordField.foreground", INK);
        UIManager.put("PasswordField.caretForeground", INK);
        UIManager.put("TextArea.background", FIELD);
        UIManager.put("TextArea.foreground", INK);
        UIManager.put("TextArea.caretForeground", INK);
        UIManager.put("ComboBox.background", FIELD);
        UIManager.put("ComboBox.foreground", INK);
        UIManager.put("ComboBox.selectionBackground", SELECT);
        UIManager.put("ComboBox.selectionForeground", PINK_INK);
        UIManager.put("List.background", FIELD);
        UIManager.put("List.foreground", INK);
        UIManager.put("List.selectionBackground", SELECT);
        UIManager.put("List.selectionForeground", PINK_INK);
        UIManager.put("Table.background", FIELD);
        UIManager.put("Table.foreground", INK);
        UIManager.put("Table.selectionBackground", SELECT);
        UIManager.put("Table.selectionForeground", PINK_INK);
        UIManager.put("Table.gridColor", EDGE);
        UIManager.put("TableHeader.background", HEADER);
        UIManager.put("TableHeader.foreground", INK);
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("Viewport.background", PANEL);
        UIManager.put("ScrollBarUI", NightScrollBarUI.class.getName());
        UIManager.put("ScrollBar.background", new Color(32, 24, 58));
        UIManager.put("ScrollBar.thumb", new Color(206, 150, 198));
        UIManager.put("ScrollBar.track", new Color(32, 24, 58));
        UIManager.put("TabbedPane.background", HEADER);
        UIManager.put("TabbedPane.foreground", INK);
        UIManager.put("TabbedPane.selected", new Color(72, 56, 122));
        UIManager.put("TabbedPane.contentAreaColor", PANEL);
        UIManager.put("TabbedPane.tabAreaBackground", HEADER);
        UIManager.put("TabbedPane.highlight", EDGE);
        UIManager.put("TabbedPane.light", EDGE);
        UIManager.put("TabbedPane.shadow", PANEL);
        UIManager.put("TabbedPane.darkShadow", PANEL);
        UIManager.put("TabbedPane.focus", PINK);
        UIManager.put("Separator.foreground", EDGE);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", INK);
    }

    public static void apply(Component root) {
        style(root);
    }

    /**
     * 包一層捲動。寬度一律跟上視窗，避免差幾個像素就出現橫向捲軸。
     * 高度夠時撐滿；放不下時才往下捲，避免欄位被裁掉。
     */
    public static JComponent page(JComponent body) {
        apply(body);
        PageBody fill = new PageBody(body);
        JScrollPane scroll = new JScrollPane(fill);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(true);
        scroll.setBackground(PANEL);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(PANEL);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        installBars(scroll);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private static void style(Component component) {
        if (component == null) {
            return;
        }
        String type = component.getClass().getName();
        if (type.startsWith("com.github.lgooddatepicker")) {
            return;
        }
        if (component instanceof JButton && SwingUtilitiesAncestorIsCombo(component)) {
            styleFieldButton((JButton) component);
        } else if (component instanceof JButton) {
            styleButton((JButton) component);
        } else if (component instanceof JTextComponent) {
            styleText((JTextComponent) component);
        } else if (component instanceof JComboBox) {
            styleCombo((JComboBox<?>) component);
        } else if (component instanceof JTable) {
            styleTable((JTable) component);
        } else if (component instanceof JScrollPane) {
            styleScroll((JScrollPane) component);
        } else if (component instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) component;
            tabs.setOpaque(true);
            tabs.setBackground(HEADER);
            tabs.setForeground(INK);
        } else if (component instanceof JSeparator) {
            component.setForeground(EDGE);
        } else if (component instanceof JLabel) {
            styleLabel((JLabel) component);
        } else if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            if (!isSemantic(button.getForeground())) {
                button.setForeground(INK);
            }
            button.setOpaque(false);
        } else if (component instanceof JSpinner) {
            component.setBackground(FIELD);
            component.setForeground(INK);
        } else if (component instanceof JPanel) {
            stylePanel((JPanel) component);
        } else if (component instanceof JComponent) {
            JComponent widget = (JComponent) component;
            if (widget.isOpaque() && isLight(widget.getBackground())) {
                widget.setBackground(PANEL);
            }
            recolorTitle(widget);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                style(child);
            }
        }
    }

    private static boolean SwingUtilitiesAncestorIsCombo(Component component) {
        return javax.swing.SwingUtilities.getAncestorOfClass(JComboBox.class, component) != null
                || javax.swing.SwingUtilities.getAncestorOfClass(JSpinner.class, component) != null;
    }

    private static void styleButton(JButton button) {
        if (Boolean.TRUE.equals(button.getClientProperty("night.button"))) {
            return;
        }
        button.putClientProperty("night.button", Boolean.TRUE);
        button.setUI(new PillButtonUI());
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setForeground(PINK_INK);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (!(button.getBorder() instanceof EmptyBorder)) {
            button.setBorder(new EmptyBorder(4, 12, 4, 12));
        }
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.putClientProperty("night.hover", Boolean.TRUE);
                button.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.putClientProperty("night.hover", Boolean.FALSE);
                button.repaint();
            }
        });
    }

    private static void styleFieldButton(JButton button) {
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBackground(FIELD);
        button.setForeground(INK);
        button.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, EDGE));
    }

    private static void styleText(JTextComponent field) {
        Color background = field.getBackground();
        Color foreground = field.getForeground();
        if (background != null && luminance(background) < 80 && isSemantic(foreground)) {
            field.setCaretColor(INK);
            field.setSelectionColor(SELECT);
            field.setSelectedTextColor(PINK_INK);
            return;
        }
        field.setOpaque(true);
        field.setBackground(FIELD);
        field.setForeground(INK);
        field.setCaretColor(INK);
        field.setDisabledTextColor(MUTED);
        field.setSelectionColor(SELECT);
        field.setSelectedTextColor(PINK_INK);
        field.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(EDGE),
                new EmptyBorder(2, 6, 2, 6)));
    }

    private static void styleCombo(JComboBox<?> combo) {
        combo.setOpaque(true);
        combo.setBackground(FIELD);
        combo.setForeground(INK);
        combo.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(EDGE),
                new EmptyBorder(0, 6, 0, 4)));
        boolean alreadyBasic = combo.getUI() != null
                && combo.getUI().getClass().getName().startsWith("com.example.");
        if (!alreadyBasic) {
            combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton button = new JButton("\u25BE");
                    button.setFont(UiFonts.latinPlain(11));
                    button.setFocusable(false);
                    button.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, EDGE));
                    button.setContentAreaFilled(true);
                    button.setBackground(FIELD);
                    button.setForeground(INK);
                    button.setMargin(new java.awt.Insets(0, 0, 0, 0));
                    return button;
                }
            });
        }
        Component editor = combo.getEditor().getEditorComponent();
        if (editor instanceof JTextComponent) {
            JTextComponent text = (JTextComponent) editor;
            text.setOpaque(true);
            text.setBackground(FIELD);
            text.setForeground(INK);
            text.setCaretColor(INK);
            text.setSelectionColor(SELECT);
            text.setSelectedTextColor(PINK_INK);
            text.setBorder(new EmptyBorder(2, 4, 2, 4));
        }
    }

    private static void styleTable(JTable table) {
        table.setOpaque(true);
        table.setBackground(FIELD);
        table.setForeground(INK);
        table.setSelectionBackground(SELECT);
        table.setSelectionForeground(PINK_INK);
        table.setGridColor(EDGE);
        table.setRowHeight(Math.max(table.getRowHeight(), 30));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable source, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component cell = super.getTableCellRendererComponent(
                        source, value, isSelected, hasFocus, row, column);
                cell.setBackground(isSelected ? SELECT : source.getBackground());
                cell.setForeground(isSelected ? PINK_INK : source.getForeground());
                if (cell instanceof JComponent) {
                    ((JComponent) cell).setOpaque(true);
                }
                return cell;
            }
        });
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setOpaque(true);
            header.setBackground(HEADER);
            header.setForeground(INK);
            header.setUI(new javax.swing.plaf.basic.BasicTableHeaderUI());
            header.setDefaultRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(
                        JTable source, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(
                            source, value, isSelected, hasFocus, row, column);
                    label.setOpaque(true);
                    label.setBackground(HEADER);
                    label.setForeground(INK);
                    label.setBorder(new EmptyBorder(6, 8, 6, 8));
                    return label;
                }
            });
        }
    }

    private static void styleScroll(JScrollPane scroll) {
        scroll.setOpaque(true);
        scroll.setBackground(PANEL);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(PANEL);
        installBars(scroll);
        if (scroll.getBorder() != null) {
            scroll.setBorder(BorderFactory.createLineBorder(EDGE));
        }
    }

    private static void installBars(JScrollPane scroll) {
        scroll.getVerticalScrollBar().setUI(new NightScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new NightScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 12));
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
    }

    private static void styleLabel(JLabel label) {
        if (!isSemantic(label.getForeground())) {
            Color current = label.getForeground();
            label.setForeground(current != null && luminance(current) > 80 && luminance(current) < 190
                    ? MUTED : INK);
        }
    }

    private static void stylePanel(JPanel panel) {
        Color background = panel.getBackground();
        if (!panel.isOpaque() || background == null || background.getAlpha() < 250 || isLight(background)) {
            panel.setOpaque(true);
            panel.setBackground(PANEL);
        }
        recolorTitle(panel);
    }

    private static void recolorTitle(JComponent component) {
        if (component.getBorder() instanceof CompoundBorder) {
            CompoundBorder compound = (CompoundBorder) component.getBorder();
            if (compound.getOutsideBorder() instanceof TitledBorder) {
                ((TitledBorder) compound.getOutsideBorder()).setTitleColor(INK);
            }
        } else if (component.getBorder() instanceof TitledBorder) {
            ((TitledBorder) component.getBorder()).setTitleColor(INK);
        }
    }

    private static boolean isLight(Color color) {
        return color != null && luminance(color) > 150;
    }

    private static boolean isSemantic(Color color) {
        if (color == null) {
            return false;
        }
        int max = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
        int min = Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
        return max - min > 80 && max > 150;
    }

    private static int luminance(Color color) {
        return (color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114) / 1000;
    }

    private static final class PillButtonUI extends BasicButtonUI {
        @Override
        public void update(Graphics g, JComponent c) {
            paint(g, c);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton button = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean hover = Boolean.TRUE.equals(button.getClientProperty("night.hover"));
            if (!button.isEnabled()) {
                g2.setColor(new Color(72, 58, 108));
            } else if (hover) {
                g2.setColor(new Color(255, 154, 190));
            } else {
                g2.setColor(PINK);
            }
            g2.fillRoundRect(0, 0, Math.max(0, c.getWidth() - 1), Math.max(0, c.getHeight() - 1), 12, 12);
            String text = button.getText() == null ? "" : button.getText();
            g2.setFont(button.getFont());
            FontMetrics metrics = g2.getFontMetrics();
            int x = Math.max(8, (c.getWidth() - metrics.stringWidth(text)) / 2);
            int y = (c.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.setColor(button.isEnabled() ? PINK_INK : MUTED);
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }

    private static final class PageBody extends JPanel implements Scrollable {
        private PageBody(JComponent body) {
            super(new java.awt.BorderLayout());
            setOpaque(true);
            setBackground(PANEL);
            add(body, java.awt.BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(visibleRect.height - 24, 18);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            Container parent = getParent();
            return parent != null && parent.getWidth() > 0;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            Container parent = getParent();
            return parent != null && getPreferredSize().height <= parent.getHeight();
        }
    }

    /** 捲軸：深紫軌道、粉紫滑塊。給 UIManager 用，所以要 public。 */
    public static final class NightScrollBarUI extends BasicScrollBarUI {
        private static final Color TRACK = new Color(32, 24, 58);
        private static final Color THUMB = new Color(206, 150, 198);
        private static final Color THUMB_HOT = new Color(255, 170, 204);

        @Override
        protected void configureScrollBarColors() {
            thumbColor = THUMB;
            trackColor = TRACK;
            thumbHighlightColor = THUMB;
            thumbLightShadowColor = THUMB;
            thumbDarkShadowColor = THUMB;
            trackHighlightColor = TRACK;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return emptyButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return emptyButton();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(TRACK);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isDragging || isThumbRollover() ? THUMB_HOT : THUMB);
            int arc = Math.min(10, Math.min(thumbBounds.width, thumbBounds.height));
            g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2,
                    Math.max(1, thumbBounds.width - 4), Math.max(1, thumbBounds.height - 4), arc, arc);
            g2.dispose();
        }

        private static JButton emptyButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setFocusable(false);
            button.setOpaque(false);
            button.setBorder(BorderFactory.createEmptyBorder());
            return button;
        }
    }
}
