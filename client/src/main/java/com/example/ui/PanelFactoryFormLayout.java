package com.example.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 設定與任務面板的換行、彈性橫列與直向欄排版輔助。
 */
final class PanelFactoryFormLayout {

    private PanelFactoryFormLayout() {
    }

    static JPanel shrinkRow(int gap) {
        return new ShrinkRow(gap);
    }

    static JPanel sameLineRow(Component... kids) {
        return new SameLineRow(kids);
    }

    static final class ShrinkRow extends JPanel {
        ShrinkRow(int gap) {
            setOpaque(false);
            setLayout(new ShrinkLayout(gap));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }
    }

    static final class ShrinkLayout implements LayoutManager {
        private final int gap;

        ShrinkLayout(int gap) {
            this.gap = gap;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return measure(parent, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Dimension size = measure(parent, false);
            size.width = 0;
            return size;
        }

        private Dimension measure(Container parent, boolean preferred) {
            int width = gap;
            int height = 0;
            for (Component child : parent.getComponents()) {
                if (!child.isVisible()) {
                    continue;
                }
                Dimension size = preferred ? child.getPreferredSize() : child.getMinimumSize();
                int childW = size.width;
                if (preferred && isFlex(child) && childW > 168) {
                    childW = 168;
                }
                width += childW + gap;
                height = Math.max(height, size.height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right,
                    Math.max(24, height + insets.top + insets.bottom));
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int innerH = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            int innerW = parent.getWidth() - insets.left - insets.right;
            int visible = 0;
            int fixed = 0;
            Component flex = null;
            for (Component child : parent.getComponents()) {
                if (!child.isVisible()) {
                    continue;
                }
                visible++;
                if (flex == null && isFlex(child)) {
                    flex = child;
                    continue;
                }
                fixed += child.getPreferredSize().width;
            }
            int gaps = gap * (visible + 1);
            int flexW = innerW - fixed - gaps;
            if (flex != null) {
                int max = flex.getMaximumSize().width;
                if (max > 0 && max < Integer.MAX_VALUE / 4 && flexW > max) {
                    flexW = max;
                }
                if (flexW < 0) {
                    flexW = 0;
                }
            }
            int x = insets.left + gap;
            for (Component child : parent.getComponents()) {
                if (!child.isVisible()) {
                    continue;
                }
                int width = child == flex ? flexW : child.getPreferredSize().width;
                int height = Math.min(innerH, Math.max(child.getPreferredSize().height, 1));
                int y = insets.top + Math.max(0, (innerH - height) / 2);
                child.setBounds(x, y, Math.max(0, width), height);
                x += Math.max(0, width) + gap;
            }
        }

        static boolean isFlex(Component child) {
            return child instanceof JComponent
                    && Boolean.TRUE.equals(((JComponent) child).getClientProperty("flex"));
        }
    }

    /** 預設寬放不下三句全文時，均分成同一列，在各自的格子裡折行。 */
    static final class SameLineRow extends JPanel {
        SameLineRow(Component... kids) {
            super(new GridLayout(1, Math.max(1, kids.length), 4, 0));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            for (Component kid : kids) {
                add(kid);
            }
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    int width = getWidth();
                    Object seen = getClientProperty("same.width");
                    if (seen instanceof Integer && (Integer) seen == width) {
                        return;
                    }
                    putClientProperty("same.width", width);
                    publishColumn();
                    Container parent = getParent();
                    if (parent != null) {
                        parent.revalidate();
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            publishColumn();
            int height = 0;
            for (Component kid : getComponents()) {
                if (kid.isVisible()) {
                    height = Math.max(height, kid.getPreferredSize().height);
                }
            }
            int width = getWidth() > 1 ? getWidth() : 240;
            return new Dimension(width, Math.max(24, height));
        }

        @Override
        public Dimension getMinimumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(0, pref.height);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, pref.height);
        }

        private void publishColumn() {
            int cols = Math.max(1, getComponentCount());
            int width = getWidth();
            if (width <= 1 && getParent() != null && getParent().getWidth() > 1) {
                Insets insets = getParent().getInsets();
                width = getParent().getWidth() - insets.left - insets.right;
            }
            if (width <= 1) {
                width = 264;
            }
            int col = Math.max(48, (width - 4 * Math.max(0, cols - 1)) / cols);
            for (Component kid : getComponents()) {
                if (kid instanceof CompactShellPanels.WrappingLabel) {
                    ((CompactShellPanels.WrappingLabel) kid).setWrapWidth(col);
                }
                if (kid instanceof JComponent) {
                    ((JComponent) kid).putClientProperty(CandyCheckBox.WRAP_COL, col);
                }
            }
        }
    }

    /**
     * 橫向排列，寬度不夠就換行，高度跟著目前寬度長高。
     * 不要在這裡把高度鎖成組裝當下的一行，否則第二列會被裁掉。
     */
    static JPanel wrapFlowRow(int hgap, int vgap, Component... components) {
        JPanel row = new WrappingRow(hgap, vgap);
        for (Component component : components) {
            row.add(component);
        }
        return row;
    }

    /** 時與分貼在下拉旁邊，不要再被外層間距拆開。 */
    static JPanel inlinePair(Component left, Component right) {
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
    static void installSkipDatesPlacement(JPanel scheduleRow, JPanel nameRow, JButton skip) {
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

    static boolean skipFitsOnSchedule(JPanel scheduleRow, JButton skip, int width) {
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
    static int relayoutDepth;

    /**
     * 直向內容。父層先依短文高度排好時，這裡改用目前寬度的實際高度，再把後面的區塊往下推。
     */
    static JPanel fittingColumn() {
        JPanel column = new JPanel() {
            @Override
            public void doLayout() {
                if (getWidth() > 1 && relayoutDepth == 0) {
                    int extra = getPreferredSize().height - getHeight();
                    if (extra > 1) {
                        relayoutDepth++;
                        try {
                            setBounds(getX(), getY(), getWidth(), getHeight() + extra);
                            pushDown(this, extra);
                        } finally {
                            relayoutDepth--;
                        }
                    }
                }
                super.doLayout();
            }
        };
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        return column;
    }

    /** 這一列長高之後，把同層後面的元件往下推，並把高度一路補到捲動內容。 */
    static void pushDown(Component grown, int extra) {
        Container parent = grown.getParent();
        if (parent == null || extra <= 0) {
            return;
        }
        // 分頁內容是疊在同一塊上的，不能把其他分頁往下挪。
        if (!(parent instanceof JTabbedPane)) {
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

    static final class WrappingRow extends JPanel {
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
    static JPanel formRow(Component... components) {
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
    static void pinRow(JComponent row) {
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension pref = row.getPreferredSize();
        int h = Math.max(pref.height, 24);
        row.setMinimumSize(new Dimension(0, h));
        row.setPreferredSize(new Dimension(Math.min(pref.width, 300), h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    /** ID、瀏覽器、立即執行各佔一列，380px 內看得到也能點。 */
    static JPanel selectorBrowserActionRow(
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
        column.add(stackedField(labeled(labelFont, "ID："), selectorField));
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

    static JPanel stackedField(JLabel label, JComponent field) {
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

    static void lockComponentSize(JComponent component, Dimension size) {
        component.setPreferredSize(size);
        component.setMinimumSize(size);
        component.setMaximumSize(size);
    }

    /** 標籤在上、輸入欄在下，欄位吃滿小方塊寬度。 */
    static JPanel formRowStretch(JComponent label, JComponent field) {
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
    static void lockComboSize(JComboBox<?> combo, int minWidth) {
        Dimension size = combo.getPreferredSize();
        size = new Dimension(Math.max(size.width, minWidth), Math.max(size.height, 30));
        combo.setMinimumSize(size);
        combo.setPreferredSize(size);
        combo.setMaximumSize(size);
    }

    /** 只鎖定高度，寬度交給版面配置 */
    static void lockFieldHeight(JComponent field) {
        int h = Math.max(field.getPreferredSize().height, 26);
        int w = Math.max(field.getPreferredSize().width, 80);
        field.setMinimumSize(new Dimension(80, h));
        field.setPreferredSize(new Dimension(w, h));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    static JLabel labeled(Font font, String text) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        return label;
    }

    // ==================== 共用元件 ====================

    /**
     * 建立可折疊的分組面板
     */
}
