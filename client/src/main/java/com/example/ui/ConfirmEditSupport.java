package com.example.ui;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.IntSupplier;

/**
 * LINE 風格行內編輯：輸入框右側內緣顯示字數，框外貼確認／取消方鈕。
 * 取消會把內容還原成最後確認的值。
 */
public final class ConfirmEditSupport {

    public static final String COUNTER_MAX_KEY = "confirmEdit.max";
    public static final String COUNTER_LENGTH_KEY = "confirmEdit.length";
    public static final String COMMITTED_KEY = "confirmEdit.committed";

    private static final int COUNTER_RIGHT = 40;
    private static final int ICON_BUTTON = 26;
    private static final Color COUNTER_INK = new Color(110, 110, 110);

    private ConfirmEditSupport() {
    }

    /** 以 Unicode 字（code point）計算「目前／上限」。上限 ≤ 0 時回傳空字串。 */
    public static String formatCounter(String text, int max) {
        if (max <= 0) {
            return "";
        }
        return currentLength(text) + "/" + max;
    }

    public static int currentLength(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.codePointCount(0, text.length());
    }

    /** 草稿與已確認值是否不同（不做正規化，由呼叫端先處理）。 */
    public static boolean isDirty(String draft, String committed) {
        String left = draft == null ? "" : draft;
        String right = committed == null ? "" : committed;
        return !left.equals(right);
    }

    public static void setCommitted(JComponent editor, String value) {
        if (editor != null) {
            editor.putClientProperty(COMMITTED_KEY, value == null ? "" : value);
            editor.repaint();
        }
    }

    public static String getCommitted(JComponent editor) {
        if (editor == null) {
            return "";
        }
        Object raw = editor.getClientProperty(COMMITTED_KEY);
        return raw == null ? "" : String.valueOf(raw);
    }

    public static void revertText(JTextComponent field) {
        if (field == null) {
            return;
        }
        String committed = getCommitted(field);
        if (!committed.equals(field.getText())) {
            field.setText(committed);
        }
        field.setCaretPosition(field.getText().length());
    }

    public static void revertCombo(JComboBox<?> combo) {
        if (combo == null) {
            return;
        }
        String committed = getCommitted(combo);
        combo.setSelectedItem(committed);
        Component editor = combo.getEditor() == null ? null : combo.getEditor().getEditorComponent();
        if (editor instanceof JTextComponent) {
            JTextComponent field = (JTextComponent) editor;
            if (!committed.equals(field.getText())) {
                field.setText(committed);
            }
        }
    }

    public static void syncButtons(AbstractButton confirm, AbstractButton cancel, boolean dirty, boolean canSave) {
        if (confirm != null) {
            confirm.setEnabled(canSave);
        }
        if (cancel != null) {
            cancel.setEnabled(dirty);
        }
    }

    public static CandyButton confirmButton(Font font) {
        return glyphButton(font, Glyph.CHECK, "確認", "確認並儲存（Enter）");
    }

    public static CandyButton cancelButton(Font font) {
        return glyphButton(font, Glyph.CROSS, "取消", "取消並還原成原本的內容（Esc）");
    }

    /** 把確認／取消方鈕收成一組，寬度接近舊的「套用」單鈕，窄列才放得下。 */
    public static javax.swing.JPanel buttonCluster(javax.swing.JButton confirm, javax.swing.JButton cancel) {
        javax.swing.JPanel cluster = new javax.swing.JPanel();
        cluster.setOpaque(false);
        cluster.setLayout(new javax.swing.BoxLayout(cluster, javax.swing.BoxLayout.X_AXIS));
        if (confirm != null) {
            cluster.add(confirm);
        }
        cluster.add(javax.swing.Box.createHorizontalStrut(2));
        if (cancel != null) {
            cluster.add(cancel);
        }
        int w = ICON_BUTTON * 2 + 2;
        cluster.setPreferredSize(new Dimension(w, ICON_BUTTON));
        cluster.setMinimumSize(new Dimension(w, ICON_BUTTON));
        cluster.setMaximumSize(new Dimension(w, ICON_BUTTON));
        return cluster;
    }

    public static void attachCounter(JTextComponent field, int maxChars) {
        if (field == null || maxChars <= 0) {
            return;
        }
        field.putClientProperty(COUNTER_MAX_KEY, Integer.valueOf(maxChars));
        field.putClientProperty(COUNTER_LENGTH_KEY, (IntSupplier) () -> currentLength(field.getText()));
        Border current = field.getBorder();
        if (!(current instanceof CounterBorder)) {
            field.setBorder(new CounterBorder(current));
        }
        field.getDocument().addDocumentListener(new SimpleDocListener(field::repaint));
        field.repaint();
    }

    /** Esc 觸發取消；Enter 仍由既有 ActionListener／套用邏輯處理。 */
    public static void bindEscape(JComponent editor, Runnable onCancel) {
        if (editor == null || onCancel == null) {
            return;
        }
        editor.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "confirm-edit-cancel");
        editor.getActionMap().put("confirm-edit-cancel", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onCancel.run();
            }
        });
    }

    public static void bindEscape(JComboBox<?> combo, Runnable onCancel) {
        if (combo == null) {
            return;
        }
        bindEscape((JComponent) combo, onCancel);
        Component editor = combo.getEditor() == null ? null : combo.getEditor().getEditorComponent();
        if (editor instanceof JComponent) {
            bindEscape((JComponent) editor, onCancel);
        }
    }

    private static CandyButton glyphButton(Font font, Glyph glyph, String name, String tip) {
        CandyButton button = new CandyButton("", CandyButton.Variant.SECONDARY, CandyButton.Size.COMPACT);
        if (font != null) {
            button.setFont(font);
        }
        button.setIcon(new GlyphIcon(glyph));
        button.setToolTipText(tip);
        button.getAccessibleContext().setAccessibleName(name);
        Dimension size = new Dimension(ICON_BUTTON, ICON_BUTTON);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setFocusable(true);
        button.setMargin(new Insets(0, 0, 0, 0));
        return button;
    }

    enum Glyph {
        CHECK,
        CROSS
    }

    static final class GlyphIcon implements javax.swing.Icon {
        private final Glyph glyph;

        GlyphIcon(Glyph glyph) {
            this.glyph = glyph;
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean enabled = c == null || c.isEnabled();
            g2.setColor(enabled ? CandyTheme.INK : CandyTheme.MUTED);
            g2.setStroke(new java.awt.BasicStroke(1.8f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            if (glyph == Glyph.CHECK) {
                g2.drawPolyline(new int[] {x + 1, x + 4, x + 11}, new int[] {y + 6, y + 10, y + 2}, 3);
            } else {
                g2.drawLine(x + 2, y + 2, x + 10, y + 10);
                g2.drawLine(x + 10, y + 2, x + 2, y + 10);
            }
            g2.dispose();
        }
    }

    static final class CounterBorder extends AbstractBorder {
        private final Border inner;

        CounterBorder(Border inner) {
            this.inner = inner;
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            if (insets == null) {
                insets = new Insets(0, 0, 0, 0);
            }
            if (inner instanceof AbstractBorder) {
                insets = ((AbstractBorder) inner).getBorderInsets(c, insets);
            } else if (inner != null) {
                Insets from = inner.getBorderInsets(c);
                insets.set(from.top, from.left, from.bottom, from.right);
            } else {
                insets.set(0, 0, 0, 0);
            }
            insets.right = Math.max(insets.right, COUNTER_RIGHT);
            return insets;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return getBorderInsets(c, new Insets(0, 0, 0, 0));
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            if (inner != null) {
                inner.paintBorder(c, g, x, y, width, height);
            }
            if (!(c instanceof JComponent)) {
                return;
            }
            JComponent field = (JComponent) c;
            Object maxObj = field.getClientProperty(COUNTER_MAX_KEY);
            int max = maxObj instanceof Integer ? (Integer) maxObj : 0;
            if (max <= 0) {
                return;
            }
            int length = 0;
            Object lenObj = field.getClientProperty(COUNTER_LENGTH_KEY);
            if (lenObj instanceof IntSupplier) {
                length = ((IntSupplier) lenObj).getAsInt();
            }
            String label = length + "/" + max;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = c.getFont() == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 11)
                    : c.getFont().deriveFont(Font.PLAIN, 11f);
            g2.setFont(font);
            g2.setColor(COUNTER_INK);
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + width - fm.stringWidth(label) - 8;
            int ty = y + (height - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(label, tx, ty);
            g2.dispose();
        }
    }

    static final class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable onChange;

        SimpleDocListener(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }
    }
}
