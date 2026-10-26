package com.example.ui;

import com.example.model.CheckInHistoryEntry;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;

/**
 * 執行紀錄分成時間、名稱、狀態、來源、結果。完整內容放在提示，不另砍欄位。
 */
public final class CandyHistoryColumns {

    public static final String[] TITLES = {"時間", "名稱", "狀態", "來源", "結果"};
    private static final int[] WEIGHTS = {176, 120, 132, 84, 160};
    private static final int GAP = 8;
    private static final Pattern SPLIT = Pattern.compile(Pattern.quote(CheckInHistoryEntry.DISPLAY_SEPARATOR));

    private CandyHistoryColumns() {
    }

    public static String[] split(String line) {
        String[] columns = {"", "", "", "", ""};
        if (line == null || line.isEmpty()) {
            return columns;
        }
        String[] raw = SPLIT.split(line, 5);
        if (raw.length < 5) {
            columns[4] = line;
            return columns;
        }
        System.arraycopy(raw, 0, columns, 0, 5);
        return columns;
    }

    /** 五欄寬度，不含欄間距。總和不會超過可用寬度。 */
    public static int[] widths(int total) {
        int inner = Math.max(0, total - GAP * 2 - GAP * 4);
        int sum = 0;
        for (int weight : WEIGHTS) {
            sum += weight;
        }
        int[] widths = new int[5];
        if (inner <= 0 || sum <= 0) {
            return widths;
        }
        int used = 0;
        for (int i = 0; i < 4; i++) {
            widths[i] = Math.max(28, inner * WEIGHTS[i] / sum);
            used += widths[i];
        }
        widths[4] = Math.max(28, inner - used);
        return widths;
    }

    public static String tip(String line) {
        String[] columns = split(line);
        StringBuilder html = new StringBuilder("<html>");
        for (int i = 0; i < TITLES.length; i++) {
            if (i > 0) {
                html.append("<br>");
            }
            html.append(TITLES[i]).append("：").append(escape(columns[i]));
        }
        html.append("</html>");
        return html.toString();
    }

    public static void attach(JScrollPane scroll, Font headerFont) {
        scroll.putClientProperty(CandyScrollBar.MARK, Boolean.TRUE);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getViewport().setBackground(CandyTheme.PANEL);
        JComponent header = new Header();
        header.setFont(headerFont);
        scroll.setColumnHeaderView(header);
        if (scroll.getColumnHeader() != null) {
            scroll.getColumnHeader().setOpaque(false);
        }
    }

    private static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    public static final class View extends JList<String> {
        public View(ListModel<String> model) {
            super(model);
            setOpaque(false);
            setBackground(CandyTheme.PANEL);
            setForeground(CandyTheme.INK);
            setSelectionBackground(CandyTheme.ROW_SELECT);
            setSelectionForeground(CandyTheme.INK);
            setFixedCellHeight(30);
            setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
            setCellRenderer(new Cell());
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    markHover(e);
                }
            });
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    putClientProperty("candy.hover", -1);
                    repaint();
                }
            });
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = locationToIndex(event.getPoint());
            if (index < 0) {
                return getToolTipText();
            }
            java.awt.Rectangle bounds = getCellBounds(index, index);
            if (bounds == null || !bounds.contains(event.getPoint())) {
                return getToolTipText();
            }
            Object value = getModel().getElementAt(index);
            return tip(value == null ? "" : value.toString());
        }

        private void markHover(MouseEvent event) {
            int index = locationToIndex(event.getPoint());
            java.awt.Rectangle bounds = index < 0 ? null : getCellBounds(index, index);
            int next = bounds != null && bounds.contains(event.getPoint()) ? index : -1;
            Object current = getClientProperty("candy.hover");
            if (current instanceof Integer && (Integer) current == next) {
                return;
            }
            putClientProperty("candy.hover", next);
            repaint();
        }
    }

    private static final class Cell extends JComponent implements javax.swing.ListCellRenderer<String> {
        private String[] columns = {"", "", "", "", ""};
        private boolean selected;
        private boolean hover;
        private int index;

        @Override
        public Component getListCellRendererComponent(
                JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            columns = split(value);
            selected = isSelected;
            this.index = index;
            Object hot = list.getClientProperty("candy.hover");
            hover = hot instanceof Integer && (Integer) hot == index;
            setFont(list.getFont());
            setOpaque(false);
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, 30);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = CandyDraw.quality((Graphics2D) graphics);
            java.awt.Color fill = CandyTheme.ROW;
            if (selected) {
                fill = CandyTheme.ROW_SELECT;
            } else if (hover) {
                fill = CandyTheme.ROW_HOT;
            } else if (index == 0) {
                fill = CandyTheme.ROW_LATEST;
            } else if (index % 2 == 1) {
                fill = CandyTheme.ROW_ALT;
            }
            g.setColor(fill);
            g.fillRoundRect(4, 2, Math.max(1, getWidth() - 8), Math.max(1, getHeight() - 4), 8, 8);
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            int[] widths = widths(getWidth());
            int x = GAP;
            int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            for (int i = 0; i < columns.length; i++) {
                g.setColor(colorFor(i));
                g.drawString(CandyTheme.fit(columns[i], metrics, widths[i]), x, y);
                x += widths[i] + GAP;
            }
            g.dispose();
        }

        private java.awt.Color colorFor(int column) {
            if (column != 2 || selected) {
                return CandyTheme.INK;
            }
            String status = columns[2];
            if (status.contains("成功")) {
                return new java.awt.Color(0xC8, 0xF0, 0xD4);
            }
            if (status.contains("失敗")) {
                return CandyTheme.PINK_HI;
            }
            return CandyTheme.INK;
        }
    }

    private static final class Header extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, 24);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = CandyDraw.quality((Graphics2D) graphics);
            g.setColor(CandyTheme.HEADER);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(CandyTheme.EDGE);
            g.drawLine(8, getHeight() - 1, Math.max(8, getWidth() - 8), getHeight() - 1);
            g.setFont(getFont() != null ? getFont().deriveFont(Font.BOLD, 12f) : UiFonts.chineseBold(12));
            g.setColor(CandyTheme.LAVENDER);
            FontMetrics metrics = g.getFontMetrics();
            int[] widths = widths(getWidth());
            int x = GAP;
            int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            for (int i = 0; i < TITLES.length; i++) {
                g.drawString(CandyTheme.fit(TITLES[i], metrics, widths[i]), x, y);
                x += widths[i] + GAP;
            }
            g.dispose();
        }
    }
}
