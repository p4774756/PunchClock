package com.example.ui;

import javax.swing.JTextField;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicPasswordFieldUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JPasswordField;

/**
 * 深色圓角輸入框。文件、選取、輸入法與密碼遮蔽都沿用 Swing 文字元件，只換畫法。
 */
public class CandyTextField extends JTextField {

    public static final String MARK = "candy.field";
    static final String CHROME = "candy.field.chrome";
    private static final String FOCUS_WATCH = "candy.field.focus";

    public CandyTextField(String text, int columns) {
        super(stripMacPlaceholder(text), columns);
        install(this, true);
    }

    @Override
    public void updateUI() {
        install(this, true);
    }

    /**
     * macOS 輸入法在點進欄位時會送 U+FFFC。留在文件裡的話，游標停住，後面的字也打不進去。
     */
    @Override
    public void replaceSelection(String content) {
        String clean = stripMacPlaceholder(content);
        if ((clean == null || clean.isEmpty()) && content != null && !content.isEmpty()) {
            return;
        }
        super.replaceSelection(clean);
    }

    static String stripMacPlaceholder(String content) {
        if (content == null || content.indexOf('\uFFFC') < 0) {
            return content;
        }
        StringBuilder kept = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c != '\uFFFC') {
                kept.append(c);
            }
        }
        return kept.toString();
    }

    /**
     * 多行文字只換顏色與內距，不換成單行的 {@code FieldUI}，換行和自訂繪製才不會壞。
     */
    public static void dressArea(JTextComponent area) {
        area.putClientProperty(MARK, Boolean.TRUE);
        area.putClientProperty(CHROME, Boolean.FALSE);
        area.setOpaque(true);
        area.setBackground(CandyTheme.FIELD);
        area.setForeground(CandyTheme.INK);
        area.setCaretColor(CandyTheme.INK);
        area.setDisabledTextColor(CandyTheme.MUTED);
        area.setSelectionColor(CandyTheme.PINK);
        area.setSelectedTextColor(CandyTheme.PINK_INK);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
    }

    public static void install(JTextComponent field, boolean chrome) {
        field.putClientProperty(MARK, Boolean.TRUE);
        field.putClientProperty(CHROME, chrome);
        if (field instanceof JPasswordField) {
            if (!(field.getUI() instanceof PasswordUI)) {
                field.setUI(new PasswordUI());
            }
        } else if (!(field.getUI() instanceof FieldUI)) {
            field.setUI(new FieldUI());
        }
        field.setOpaque(false);
        field.setForeground(CandyTheme.INK);
        field.setCaretColor(CandyTheme.INK);
        field.setDisabledTextColor(CandyTheme.MUTED);
        field.setSelectionColor(CandyTheme.PINK);
        field.setSelectedTextColor(CandyTheme.PINK_INK);
        field.setMargin(new Insets(0, 0, 0, 0));
        if (chrome) {
            field.setBorder(new ChromeBorder());
            CandyTheme.watch(field);
            watchFocus(field);
        } else {
            field.setBorder(new EmptyBorder(0, 0, 0, 0));
        }
    }

    private static void watchFocus(JTextComponent field) {
        if (Boolean.TRUE.equals(field.getClientProperty(FOCUS_WATCH))) {
            return;
        }
        field.putClientProperty(FOCUS_WATCH, Boolean.TRUE);
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.repaint();
            }
        });
    }

    private static boolean chrome(JTextComponent field) {
        return Boolean.TRUE.equals(field.getClientProperty(CHROME));
    }

    private static void paintBody(Graphics g, JTextComponent field) {
        if (!chrome(field)) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        CandyDraw.field(g2, field.getWidth(), field.getHeight(),
                CandyTheme.hover(field), field.isFocusOwner(), field.isEnabled(), false);
    }

    static final class FieldUI extends BasicTextFieldUI {
        @Override
        protected void paintSafely(Graphics g) {
            // BasicTextUI 只在不透明時才畫背景。圓角欄位是透明的，否則外框不會出現。
            JTextComponent field = getComponent();
            if (field != null && chrome(field)) {
                paintBody(g, field);
            }
            super.paintSafely(g);
        }

        @Override
        protected void paintBackground(Graphics g) {
            JTextComponent field = getComponent();
            if (field != null && chrome(field)) {
                return;
            }
            super.paintBackground(g);
        }
    }

    static final class PasswordUI extends BasicPasswordFieldUI {
        @Override
        protected void paintSafely(Graphics g) {
            JTextComponent field = getComponent();
            if (field != null && chrome(field)) {
                paintBody(g, field);
            }
            super.paintSafely(g);
        }

        @Override
        protected void paintBackground(Graphics g) {
            JTextComponent field = getComponent();
            if (field != null && chrome(field)) {
                return;
            }
            super.paintBackground(g);
        }
    }

    /** 只描邊，不蓋住文字。內距固定，聚焦時文字不會跳動。 */
    static final class ChromeBorder extends AbstractBorder {
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(7, 12, 7, 12);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.top = 7;
            insets.left = 12;
            insets.bottom = 7;
            insets.right = 12;
            return insets;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            // 填色在文字底下，這裡不再畫第二層，避免蓋住游標。
        }
    }
}
