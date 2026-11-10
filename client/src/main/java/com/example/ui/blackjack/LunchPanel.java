package com.example.ui.blackjack;

import com.example.BlackjackProtocol;
import com.example.ui.CandyButton;
import com.example.ui.CandyTextField;
import com.example.ui.UiFonts;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Consumer;

/**
 * 午餐爭奪戰結算後的小卡：勝者輸入午餐、敗者等待、雙方看到最後結果。
 * 只管畫面與輸入；能不能送、送到哪由外面決定。
 */
public final class LunchPanel extends JPanel {

    public enum View {
        CHOOSE, WAIT, DECIDED, DRAW, ABANDONED
    }

    private static final Color CARD_TOP = new Color(0xFF, 0xF5, 0xE4);
    private static final Color CARD_BOTTOM = new Color(0xFF, 0xE2, 0xEC);
    private static final Color INK = new Color(0x4A, 0x22, 0x3C);
    private static final Color SUB = new Color(0x7A, 0x52, 0x6C);
    private static final Color WARN = new Color(0xC2, 0x3B, 0x5E);

    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel();
    private final CandyTextField field = new CandyTextField("", 16);
    private final CandyButton confirm = new CandyButton("確認選擇", CandyButton.Variant.PRIMARY, CandyButton.Size.MEDIUM);
    private final JPanel inputRow = new JPanel(new BorderLayout(8, 0));

    public LunchPanel(Consumer<String> onSubmit) {
        super(new BorderLayout(0, 6));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 14, 10, 14));

        title.setFont(UiFonts.chineseBold(15));
        title.setForeground(INK);
        title.setIcon(PixelArt.bentoIcon(2));
        title.setIconTextGap(8);
        detail.setFont(UiFonts.chinesePlain(12));
        detail.setForeground(SUB);

        field.setFont(UiFonts.chinesePlain(14));
        field.setToolTipText("餐廳或餐點，最多 " + BlackjackProtocol.MAX_LUNCH_CHARS + " 個字");
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new LengthFilter(BlackjackProtocol.MAX_LUNCH_CHARS));
        confirm.setFont(UiFonts.chineseBold(13));
        Runnable submit = () -> {
            if (confirm.isEnabled()) {
                onSubmit.accept(field.getText());
            }
        };
        confirm.addActionListener(e -> submit.run());
        field.addActionListener(e -> submit.run());

        inputRow.setOpaque(false);
        inputRow.add(field, BorderLayout.CENTER);
        inputRow.add(confirm, BorderLayout.EAST);

        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(detail, BorderLayout.CENTER);
        add(text, BorderLayout.NORTH);
        add(inputRow, BorderLayout.CENTER);
    }

    /**
     * @param winnerId 顯示用的勝者稱呼（例如「你」或「【小明】」）；null 代表對方
     * @param busy    勝者的選擇正在送出
     * @param locked  上次送失敗的名字；不為 null 時欄位鎖住，只能用同一個名字重送
     * @param warning 上次送出失敗等提示；null 代表沒有
     */
    public void show(View view, String winnerId, String choice, boolean busy, String locked, String warning) {
        inputRow.setVisible(view == View.CHOOSE);
        if (locked != null && !locked.equals(field.getText())) {
            field.setText(locked);
        }
        field.setEnabled(!busy && locked == null);
        confirm.setEnabled(!busy);
        confirm.setText(locked != null ? "再送一次" : "確認選擇");
        String who = winnerId == null ? "對方" : winnerId;
        String sub;
        switch (view) {
            case CHOOSE:
                title.setText("恭喜！今天午餐由你決定！");
                sub = busy ? "送出中，約 15 秒內隨心跳送達…" : "輸入餐廳或餐點，按確認後雙方都會看到";
                break;
            case WAIT:
                title.setText("今天午餐由對方決定！");
                sub = "等待" + who + "送出選擇（隨心跳約 15 秒）";
                break;
            case DECIDED:
                title.setText("今天午餐：" + (choice == null ? "" : choice));
                sub = "由" + who + "決定！結果已寫進聊天紀錄";
                break;
            case DRAW:
                title.setText("平手！午餐還沒有主人");
                sub = "按「再戰一局」重新決定";
                break;
            default:
                title.setText("這次午餐沒有決定");
                sub = "有人先離開了，可以按「再戰一局」重來";
                break;
        }
        if (warning != null && !warning.isEmpty()) {
            detail.setForeground(WARN);
            detail.setText(warning);
        } else {
            detail.setForeground(SUB);
            detail.setText(sub);
        }
        revalidate();
        repaint();
    }

    public void focusInput() {
        if (inputRow.isVisible()) {
            field.requestFocusInWindow();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth() - 1;
        int h = getHeight() - 1;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(1, 3, w - 1, h - 2, 18, 18);
        g.setPaint(new GradientPaint(0, 0, CARD_TOP, 0, h, CARD_BOTTOM));
        g.fillRoundRect(0, 0, w, h - 2, 18, 18);
        g.setColor(new Color(0xE9, 0xA5, 0xBE));
        g.drawRoundRect(0, 0, w, h - 2, 18, 18);
        g.dispose();
    }

    /** 以 code point 限制長度，貼上長字串也不會超過協定上限。 */
    private static final class LengthFilter extends DocumentFilter {
        private final int max;

        LengthFilter(int max) {
            this.max = max;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null) {
                super.replace(fb, offset, length, null, attrs);
                return;
            }
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            int kept = current.codePointCount(0, current.length())
                    - current.codePointCount(offset, offset + length);
            int room = max - kept;
            if (room <= 0) {
                return;
            }
            int cut = text.codePointCount(0, text.length()) <= room
                    ? text.length()
                    : text.offsetByCodePoints(0, room);
            super.replace(fb, offset, length, text.substring(0, cut), attrs);
        }
    }
}
