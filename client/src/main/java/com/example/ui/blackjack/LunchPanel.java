package com.example.ui.blackjack;

import com.example.BlackjackProtocol;
import com.example.ui.CandyTextField;
import com.example.ui.CandyTheme;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.Timer;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;

/**
 * 午餐爭奪戰結算後，牌桌中間的金邊餐點公告板：勝者輸入午餐、敗者等待、雙方看到同一個結果。
 * 只管畫面與輸入；能不能送、送到哪由外面決定。輸入欄只在勝者的 {@link View#CHOOSE} 出現，其他狀態完全不顯示。
 */
public final class LunchPanel extends JPanel implements CasinoTheme.Scalable {

    public enum View {
        CHOOSE, WAIT, DECIDED, DRAW, ABANDONED
    }

    private static final Color HEAD_INK = new Color(0x8A, 0x0E, 0x24);
    private static final Color SUB_INK = new Color(0x6A, 0x44, 0x30);
    private static final Color WARN = new Color(0xC2, 0x1E, 0x3A);
    private static final double DESIGN_H = 236;

    static final String PLACEHOLDER = "輸入今天想吃的午餐…";

    private final MenuField field = new MenuField();
    private final CasinoButton confirm = new CasinoButton("確認選擇", null, CasinoIcons.CHECK,
            CasinoButton.Tone.GREEN, CasinoButton.Kind.SMALL);
    private final Timer dots = new Timer(420, e -> repaint());
    private Consumer<String> onSubmit = text -> {
    };
    private float scale = 1f;
    private View view = View.WAIT;
    private String winner = "對方";
    private String choice = "";
    private String scores = "";
    private String warning;
    private boolean busy;

    public LunchPanel() {
        super(null);
        setOpaque(false);
        field.setToolTipText("餐廳或餐點，最多 " + BlackjackProtocol.MAX_LUNCH_CHARS + " 個字");
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new LengthFilter(BlackjackProtocol.MAX_LUNCH_CHARS));
        confirm.setToolTipText("送出午餐選擇，雙方都會看到同一個名稱");
        Runnable submit = () -> {
            if (confirm.isEnabled() && confirm.isVisible()) {
                onSubmit.accept(field.getText());
            }
        };
        confirm.addActionListener(e -> submit.run());
        field.addActionListener(e -> submit.run());
        add(field);
        add(confirm);
        applyFonts();
    }

    public void onSubmit(Consumer<String> handler) {
        onSubmit = handler == null ? text -> {
        } : handler;
    }

    /**
     * @param winnerId 顯示用的勝者稱呼（例如「你」或「【小明】」）；null 代表對方
     * @param scores  最終點數一行，例如「你 18 點 ： 對方 17 點」
     * @param busy    勝者的選擇正在送出
     * @param locked  上次送失敗的名字；不為 null 時欄位鎖住，只能用同一個名字重送
     * @param warning 上次送出失敗等提示；null 代表沒有
     */
    public void show(View view, String winnerId, String choice, String scores, boolean busy, String locked,
                     String warning) {
        this.view = view;
        this.winner = winnerId == null ? "對方" : winnerId;
        this.choice = choice == null ? "" : choice;
        this.scores = scores == null ? "" : scores;
        this.warning = warning == null || warning.isEmpty() ? null : warning;
        this.busy = busy;
        boolean choosing = view == View.CHOOSE;
        field.setVisible(choosing);
        confirm.setVisible(choosing);
        if (locked != null && !locked.equals(field.getText())) {
            field.setText(locked);
        }
        field.setEnabled(choosing && !busy && locked == null);
        confirm.setEnabled(choosing && !busy);
        confirm.setText(locked != null ? "再送一次" : "確認選擇");
        syncTimer();
        revalidate();
        repaint();
    }

    public void focusInput() {
        if (field.isVisible()) {
            field.requestFocusInWindow();
        }
    }

    @Override
    public void setUiScale(float value) {
        scale = value;
        applyFonts();
        revalidate();
        repaint();
    }

    private void applyFonts() {
        field.setFont(CasinoTheme.chinese(true, scale, 18));
        field.setPadding(CasinoTheme.px(scale, 14));
    }

    JTextField input() {
        return field;
    }

    JButton confirmButton() {
        return confirm;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(CasinoTheme.px(scale, 640), CasinoTheme.px(scale, DESIGN_H));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        syncTimer();
    }

    @Override
    public void removeNotify() {
        dots.stop();
        super.removeNotify();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        syncTimer();
    }

    private void syncTimer() {
        if (view == View.WAIT || busy) {
            if (isDisplayable() && isVisible()) {
                dots.start();
            }
        } else {
            dots.stop();
        }
    }

    /** 公告板外框（不含左右裝飾）；輸入欄與文字都以它為準。 */
    private RoundRectangle2D.Double board() {
        double s = scale;
        double side = 104 * s;
        double bw = Math.min(560 * s, getWidth() - side * 2);
        double top = 18 * s;
        return new RoundRectangle2D.Double((getWidth() - bw) / 2, top, bw, getHeight() - top - 6 * s, 26 * s, 26 * s);
    }

    private double paperInset() {
        return 11 * scale;
    }

    @Override
    public void doLayout() {
        RoundRectangle2D.Double b = board();
        double s = scale;
        double pad = paperInset() + 22 * s;
        int fieldH = CasinoTheme.px(scale, 44);
        java.awt.Dimension button = confirm.getPreferredSize();
        int gap = CasinoTheme.px(scale, 10);
        int rowY = (int) Math.round(b.y + 104 * s);
        int left = (int) Math.round(b.x + pad);
        int right = (int) Math.round(b.x + b.width - pad);
        int buttonX = right - button.width;
        confirm.setBounds(buttonX, rowY + (fieldH - button.height) / 2, button.width, button.height);
        field.setBounds(left, rowY, Math.max(CasinoTheme.px(scale, 80), buttonX - gap - left), fieldH);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
        double s = scale;
        RoundRectangle2D.Double b = board();

        g.setColor(new Color(10, 0, 8, 120));
        g.fill(new RoundRectangle2D.Double(b.x + 2 * s, b.y + 6 * s, b.width, b.height, b.arcwidth, b.archeight));
        g.setPaint(new GradientPaint(0, (float) b.y, CasinoTheme.WINE, 0, (float) (b.y + b.height), CasinoTheme.WINE_DARK));
        g.fill(b);
        CasinoTheme.goldFrame(g, b, (float) (3.2 * s));

        double inset = paperInset();
        RoundRectangle2D.Double paper = new RoundRectangle2D.Double(b.x + inset, b.y + inset,
                b.width - inset * 2, b.height - inset * 2, b.arcwidth * 0.6, b.archeight * 0.6);
        g.setPaint(new GradientPaint(0, (float) paper.y, CasinoTheme.CREAM, 0, (float) (paper.y + paper.height),
                CasinoTheme.CREAM_DEEP));
        g.fill(paper);
        g.setColor(CasinoTheme.GOLD_DEEP);
        g.setStroke(new BasicStroke((float) Math.max(1, 1.2 * s)));
        g.draw(paper);
        double dash = 6 * s;
        g.setColor(new Color(0xC9, 0x9A, 0x48, 150));
        g.setStroke(new BasicStroke((float) Math.max(1, s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                new float[]{(float) dash, (float) (dash * 0.8)}, 0f));
        double di = 7 * s;
        g.draw(new RoundRectangle2D.Double(paper.x + di, paper.y + di, paper.width - di * 2, paper.height - di * 2,
                paper.arcwidth * 0.7, paper.archeight * 0.7));
        g.setStroke(new BasicStroke(1f));

        double cx = b.x + b.width / 2;
        double ribbonW = Math.min(240 * s, b.width * 0.5);
        double ribbonH = 34 * s;
        CasinoPaint.ribbon(g, cx, b.y - 12 * s, ribbonW, ribbonH, scale);
        Font ribbonFont = CasinoTheme.titleChinese("今日午餐", scale, 19);
        FontMetrics rm = g.getFontMetrics(ribbonFont);
        float rb = (float) (b.y - 12 * s + ribbonH / 2 + rm.getAscent() / 2.0 - rm.getDescent() / 2.0);
        g.setFont(ribbonFont);
        g.setColor(new Color(0x4A, 0x06, 0x12));
        g.drawString("今日午餐", (float) (cx - rm.stringWidth("今日午餐") / 2.0), rb + (float) s);
        g.setPaint(new GradientPaint(0, rb - rm.getAscent(), Color.WHITE, 0, rb, CasinoTheme.GOLD_LIGHT));
        g.drawString("今日午餐", (float) (cx - rm.stringWidth("今日午餐") / 2.0), rb);

        double room = paper.width - 44 * s;
        switch (view) {
            case CHOOSE:
                paintHeadline(g, "恭喜獲勝！今天午餐由你決定", cx, b.y + 52 * s, room);
                paintLine(g, scores, cx, b.y + 80 * s, room, SUB_INK, 15);
                String hint = busy ? "送出中，約 15 秒內隨心跳送達" + dotText()
                        : "最多 " + BlackjackProtocol.MAX_LUNCH_CHARS + " 字・按確認後雙方都會看到同一個名稱";
                paintLine(g, warning != null ? warning : hint, cx, b.y + 172 * s, room,
                        warning != null ? WARN : SUB_INK, 13);
                break;
            case WAIT:
                paintHeadline(g, "今天午餐由" + winner + "決定", cx, b.y + 52 * s, room);
                paintLine(g, scores, cx, b.y + 80 * s, room, SUB_INK, 15);
                paintCloche(g, cx, b.y + 140 * s, 30 * s);
                paintLine(g, "等待" + winner + "選擇午餐（隨心跳約 15 秒）" + dotText(), cx, b.y + 176 * s, room,
                        SUB_INK, 14);
                break;
            case DECIDED:
                paintLine(g, "今天午餐吃", cx, b.y + 50 * s, room, SUB_INK, 15);
                paintDish(g, cx, b.y + 64 * s, room);
                paintLine(g, "由" + winner + "決定・結果已寫進聊天紀錄", cx, b.y + 180 * s, room, SUB_INK, 13);
                break;
            case DRAW:
                paintHeadline(g, "平手！午餐還沒有主人", cx, b.y + 70 * s, room);
                paintLine(g, "按下方「再戰一局」重新決定", cx, b.y + 104 * s, room, SUB_INK, 15);
                break;
            default:
                paintHeadline(g, "這次午餐沒有決定", cx, b.y + 70 * s, room);
                paintLine(g, "有人先離開了，可以按下方「再戰一局」重來", cx, b.y + 104 * s, room, SUB_INK, 15);
                if (warning != null) {
                    paintLine(g, warning, cx, b.y + 134 * s, room, WARN, 13);
                }
                break;
        }

        double sideCx = b.x - 52 * s;
        int unit = (int) Math.max(2, Math.round(64 * s / PixelArt.width(PixelArt.BENTO)));
        int bentoW = PixelArt.width(PixelArt.BENTO) * unit;
        if (sideCx - bentoW / 2.0 >= 0) {
            int bentoH = PixelArt.height(PixelArt.BENTO) * unit;
            int bx = (int) Math.round(sideCx - bentoW / 2.0);
            int by = (int) Math.round(b.y + b.height - bentoH - 26 * s);
            g.setColor(new Color(0, 0, 0, 60));
            g.fill(new Ellipse2D.Double(bx - 4 * s, by + bentoH + 4 * s, bentoW + 8 * s, 10 * s));
            PixelArt.drawShadowed(g, PixelArt.BENTO, bx, by, unit);
        }
        double catCx = b.x + b.width + 50 * s;
        if (catCx + 40 * s <= getWidth()) {
            StandInArt.waiterCat(g, catCx, b.y + b.height - 2 * s, 118 * s);
        }
        g.dispose();
    }

    private void paintHeadline(Graphics2D g, String text, double cx, double baseline, double room) {
        Font font = CasinoTheme.titleChinese(text, scale, 23);
        FontMetrics fm = g.getFontMetrics(font);
        String fitted = CandyTheme.fit(text, fm, (int) room);
        g.setFont(font);
        g.setColor(HEAD_INK);
        g.drawString(fitted, (float) (cx - fm.stringWidth(fitted) / 2.0), (float) baseline);
    }

    private void paintLine(Graphics2D g, String text, double cx, double baseline, double room, Color color, double size) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Font font = CasinoTheme.chinese(false, scale, size);
        FontMetrics fm = g.getFontMetrics(font);
        String fitted = CandyTheme.fit(text, fm, (int) room);
        g.setFont(font);
        g.setColor(color);
        g.drawString(fitted, (float) (cx - fm.stringWidth(fitted) / 2.0), (float) baseline);
    }

    /** 餐點名稱：最多兩行置中，太長以「…」收尾；字太多時先縮小一級。 */
    private void paintDish(Graphics2D g, double cx, double top, double room) {
        double size = 30;
        Font font = CasinoTheme.titleChinese(choice, scale, size);
        FontMetrics fm = g.getFontMetrics(font);
        if (fm.stringWidth(choice) > room * 1.6) {
            font = CasinoTheme.titleChinese(choice, scale, 24);
            fm = g.getFontMetrics(font);
        }
        List<String> lines = CasinoPaint.wrap(choice, fm, (int) room, 2);
        double lineH = fm.getHeight() * 0.98;
        double block = lineH * lines.size();
        double area = 96 * scale;
        double y = top + (area - block) / 2 + fm.getAscent();
        g.setFont(font);
        for (String line : lines) {
            float x = (float) (cx - fm.stringWidth(line) / 2.0);
            g.setColor(new Color(255, 255, 255, 160));
            g.drawString(line, x, (float) y + 1.5f);
            g.setColor(HEAD_INK);
            g.drawString(line, x, (float) y);
            y += lineH;
        }
    }

    private static void paintCloche(Graphics2D g, double cx, double baseY, double r) {
        if (CasinoPaint.assetFit(g, CasinoAssets.CLOCHE, cx, baseY + r * 0.12, r * 2.4, r * 1.55)) {
            return;
        }
        g.setColor(new Color(0xB5, 0x80, 0x2C, 90));
        g.fill(new Ellipse2D.Double(cx - r * 1.3, baseY - r * 0.12, r * 2.6, r * 0.34));
        Path2D dome = new Path2D.Double();
        dome.moveTo(cx - r, baseY);
        dome.curveTo(cx - r, baseY - r * 1.3, cx + r, baseY - r * 1.3, cx + r, baseY);
        dome.closePath();
        g.setPaint(CasinoTheme.gold((float) (baseY - r * 1.1), (float) baseY));
        g.fill(dome);
        g.setColor(CasinoTheme.GOLD_DARK);
        g.draw(dome);
        g.setPaint(CasinoTheme.gold((float) (baseY - r * 1.2), (float) (baseY - r)));
        g.fill(new Ellipse2D.Double(cx - r * 0.14, baseY - r * 1.22, r * 0.28, r * 0.28));
        g.fill(new RoundRectangle2D.Double(cx - r * 1.2, baseY - r * 0.04, r * 2.4, r * 0.16, r * 0.16, r * 0.16));
        g.setColor(new Color(255, 255, 255, 150));
        g.fill(new Ellipse2D.Double(cx - r * 0.6, baseY - r * 0.8, r * 0.22, r * 0.4));
        g.setColor(new Color(0x8A, 0x0E, 0x24));
        Font q = new Font(Font.SERIF, Font.BOLD, (int) Math.max(10, r * 0.8));
        FontMetrics qm = g.getFontMetrics(q);
        g.setFont(q);
        g.drawString("?", (float) (cx - qm.stringWidth("?") / 2.0), (float) (baseY - r * 0.22));
    }

    private String dotText() {
        int n = (int) ((System.currentTimeMillis() / 420) % 4);
        return "...".substring(0, n) + "   ".substring(n);
    }

    /**
     * 公告板上的淺底深字輸入欄。沿用 {@link CandyTextField} 的輸入法修正，只換外觀；
     * 提示文字畫在欄位上、不放進文件，所以 {@code getText()} 不會拿到它。
     */
    static final class MenuField extends CandyTextField {
        private static final Color PAPER = new Color(0xFF, 0xFD, 0xF6);
        private static final Color PAPER_OFF = new Color(0xF1, 0xE6, 0xD2);
        private static final Color INK = new Color(0x2E, 0x0A, 0x14);
        private static final Color HINT = new Color(0x8C, 0x6E, 0x5C);
        private static final Color RIM = new Color(0xB5, 0x80, 0x2C);
        private static final Color FOCUS = new Color(0xA0, 0x12, 0x30);
        private int padding = 14;

        MenuField() {
            super("", 16);
            restyle();
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    repaint();
                }
            });
        }

        @Override
        public void updateUI() {
            super.updateUI();
            restyle();
        }

        /** 父類別建構子會再套一次深色外框，所以建構後也要再換回來。 */
        private void restyle() {
            CandyTextField.install(this, false);
            setForeground(INK);
            setCaretColor(FOCUS);
            setDisabledTextColor(new Color(0x5A, 0x3A, 0x30));
            setSelectionColor(new Color(0xF6, 0xC9, 0x6A));
            setSelectedTextColor(INK);
            setBorder(new EmptyBorder(0, padding, 0, padding));
        }

        void setPadding(int value) {
            padding = value;
            setBorder(new EmptyBorder(0, padding, 0, padding));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
            double w = getWidth();
            double h = getHeight();
            double arc = h * 0.5;
            RoundRectangle2D.Double body = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, arc, arc);
            g.setColor(isEnabled() ? PAPER : PAPER_OFF);
            g.fill(body);
            g.setColor(new Color(0x6E, 0x48, 0x14, 40));
            g.fill(new RoundRectangle2D.Double(2, 2, w - 4, h * 0.22, arc * 0.6, arc * 0.6));
            boolean focused = isFocusOwner() && isEnabled();
            g.setColor(focused ? FOCUS : RIM);
            g.setStroke(new BasicStroke(focused ? 2.4f : 1.6f));
            g.draw(body);
            g.dispose();
            super.paintComponent(graphics);
            if (getDocument().getLength() == 0 && isEnabled()) {
                Graphics2D hint = CasinoTheme.quality((Graphics2D) graphics);
                hint.setFont(getFont().deriveFont(Font.PLAIN));
                FontMetrics fm = hint.getFontMetrics();
                hint.setColor(HINT);
                int room = getWidth() - getInsets().left - getInsets().right;
                hint.drawString(CandyTheme.fit(PLACEHOLDER, fm, room), getInsets().left,
                        (float) (h / 2 + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
                hint.dispose();
            }
        }
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
