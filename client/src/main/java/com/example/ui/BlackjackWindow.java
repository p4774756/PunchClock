package com.example.ui;

import com.example.BlackjackProtocol;
import com.example.BlackjackRules;
import com.example.service.BlackjackSession;
import com.example.service.LunchChallenge;
import com.example.ui.blackjack.BlackjackTablePanel;
import com.example.ui.blackjack.HandView;
import com.example.ui.blackjack.LunchPanel;
import com.example.ui.blackjack.PixelArt;
import com.example.ui.blackjack.PlayerBadge;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/**
 * 兩人 21 點視窗：牌桌、操作鈕與底部功能列。遊戲規則全在 {@link BlackjackSession}，
 * 這裡只把狀態畫出來、把按鈕動作轉成協定字串交給 {@link Host} 送出。
 */
public final class BlackjackWindow extends JFrame {

    public interface Host {
        void sendPayload(String peerId, String payload, Consumer<Boolean> done);

        void gameClosed(BlackjackWindow window);

        /** 玩家頭像；沒有就回 null，改畫像素小臉。 */
        default Image avatarOf(String clientId) {
            return null;
        }

        /** 牌局結束後按「再戰一局」：由外面開新房間、換進這個視窗並送出邀請。 */
        default void rematch(BlackjackWindow window) {
        }

        /** 午餐已決定（勝者送出成功，或敗者收到結果），雙方各呼叫一次。 */
        default void lunchDecided(BlackjackWindow window, LunchChallenge lunch) {
        }
    }

    private static final Color GOLD = new Color(0xFF, 0xD2, 0x5E);
    private static final Color MINT = new Color(0xB9, 0xF0, 0xD4);
    private static final Color BUST = new Color(0xF5, 0x8C, 0xA0);
    private static final Color WIN_INK = new Color(140, 230, 170);
    private static final Color LOSE_INK = new Color(255, 160, 170);

    private final String peerId;
    private final Host host;
    private BlackjackSession session;

    private final BlackjackTablePanel table;
    private final JButton acceptButton;
    private final JButton declineButton;
    private final JButton hitButton;
    private final JButton standButton;
    private final JButton resendButton;
    private final JButton rematchButton;
    private final JButton closeButton;
    private final Chip modeChip;
    private final JLabel messageLabel;
    private final LunchPanel lunchPanel;

    private BlackjackSession renderedSession;
    private boolean sending;
    private String failedPayload;
    private boolean lunchSending;
    private String lunchRetryText;
    private String lunchWarning;
    private LunchChallenge.Status renderedLunch;
    private Timer resultDelay;
    private boolean closing;

    public BlackjackWindow(String peerId, BlackjackSession session, Host host, Image appIcon) {
        super("21 點 · " + peerId);
        this.peerId = peerId;
        this.host = host;
        this.session = session;
        if (appIcon != null) {
            setIconImage(appIcon);
        }
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        acceptButton = button("接受挑戰", CandyButton.Variant.PRIMARY, CandyButton.Size.LARGE);
        declineButton = button("婉拒", CandyButton.Variant.SECONDARY, CandyButton.Size.LARGE);
        hitButton = button("要牌", CandyButton.Variant.PRIMARY, CandyButton.Size.LARGE);
        standButton = button("停牌", CandyButton.Variant.SECONDARY, CandyButton.Size.LARGE);
        resendButton = button("重送", CandyButton.Variant.DANGER, CandyButton.Size.SMALL);
        rematchButton = button("再戰一局", CandyButton.Variant.PRIMARY, CandyButton.Size.SMALL);
        closeButton = button("關閉", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        rematchButton.setToolTipText("開新的一局，牌堆重洗，模式沿用這一局");
        acceptButton.setToolTipText("接受後雙方同時開局");
        hitButton.setToolTipText("再拿一張牌（超過 21 點就爆牌）");
        standButton.setToolTipText("不再拿牌，換對方行動");
        resendButton.setToolTipText("上一個動作沒送到雲端，再送一次");

        acceptButton.addActionListener(e -> onAccept());
        declineButton.addActionListener(e -> onDecline());
        hitButton.addActionListener(e -> onHit());
        standButton.addActionListener(e -> onStand());
        resendButton.addActionListener(e -> {
            if (failedPayload != null) {
                send(failedPayload);
            }
        });
        rematchButton.addActionListener(e -> {
            if (canRematch()) {
                host.rematch(this);
            }
        });
        closeButton.addActionListener(e -> dispose());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        actions.setOpaque(false);
        actions.add(acceptButton);
        actions.add(declineButton);
        actions.add(hitButton);
        actions.add(standButton);

        table = new BlackjackTablePanel(actions);

        modeChip = new Chip();
        modeChip.setFont(UiFonts.chineseBold(12));
        messageLabel = new JLabel(" ");
        messageLabel.setFont(UiFonts.chinesePlain(13));
        messageLabel.setForeground(CandyTheme.MUTED);

        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        info.setOpaque(false);
        info.add(modeChip);
        info.add(messageLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(resendButton);
        buttons.add(rematchButton);
        buttons.add(closeButton);

        lunchPanel = new LunchPanel(this::onLunchSubmit);
        lunchPanel.setVisible(false);

        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(false);
        bar.add(info, BorderLayout.CENTER);
        bar.add(buttons, BorderLayout.EAST);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(true);
        footer.setBackground(CandyTheme.PANEL);
        footer.setBorder(new EmptyBorder(10, 14, 10, 14));
        footer.add(lunchPanel, BorderLayout.NORTH);
        footer.add(bar, BorderLayout.CENTER);

        JPanel stage = new JPanel(new BorderLayout());
        stage.setOpaque(true);
        stage.setBackground(CandyTheme.NIGHT);
        stage.setBorder(new EmptyBorder(12, 12, 10, 12));
        stage.add(table, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(true);
        root.setBackground(CandyTheme.NIGHT);
        root.add(stage, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);

        setMinimumSize(new Dimension(480, 600));
        setSize(560, 700);
        setLocationByPlatform(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (closing) {
                    return;
                }
                closing = true;
                if (resultDelay != null) {
                    resultDelay.stop();
                }
                String abort = BlackjackWindow.this.session.abort();
                if (abort != null) {
                    host.sendPayload(peerId, abort, ok -> {
                    });
                }
                host.gameClosed(BlackjackWindow.this);
            }
        });

        refresh();
    }

    public String peerId() {
        return peerId;
    }

    public String roomId() {
        return session.roomId();
    }

    public BlackjackSession session() {
        return session;
    }

    /** 同一個視窗換成新的一局（再戰、對方重新邀請）。舊局的畫面與待重送動作一併清掉。 */
    public void replaceSession(BlackjackSession next) {
        this.session = next;
        failedPayload = null;
        sending = false;
        lunchSending = false;
        lunchRetryText = null;
        lunchWarning = null;
        renderedLunch = null;
        refresh();
    }

    /** 牌局已結束（或被中止），而且沒有卡在等勝者選午餐。 */
    public boolean canRematch() {
        BlackjackSession.Phase phase = session.phase();
        if (phase != BlackjackSession.Phase.SETTLED && phase != BlackjackSession.Phase.CLOSED) {
            return false;
        }
        LunchChallenge lunch = session.lunch();
        return !lunchSending && (lunch == null || lunch.status() != LunchChallenge.Status.AWAITING_CHOICE);
    }

    /** 送出這一局的第一個訊息（邀請）。失敗時留在視窗，可以按「重送」。 */
    public void sendOutgoing(String payload) {
        if (payload != null) {
            send(payload);
        }
    }

    public void onRemotePayload(String payload) {
        BlackjackProtocol.Message message = BlackjackProtocol.parse(payload);
        if (message == null) {
            return;
        }
        LunchChallenge lunch = session.lunch();
        LunchChallenge.Status before = lunch == null ? null : lunch.status();
        if (!session.applyRemote(message)) {
            return;
        }
        refresh();
        if (lunch != null && before != LunchChallenge.Status.DECIDED
                && lunch.status() == LunchChallenge.Status.DECIDED) {
            host.lunchDecided(this, lunch);
        }
    }

    private void onLunchSubmit(String typed) {
        if (lunchSending) {
            return;
        }
        // 送失敗時對方可能其實已經收到；重送只能用同一個名字，雙方才不會看到不同的午餐
        String text = lunchRetryText != null ? lunchRetryText : typed;
        String payload = session.lunchPayload(text);
        if (payload == null) {
            lunchWarning = BlackjackProtocol.cleanLunchChoice(text).isEmpty()
                    ? "請先輸入餐廳或餐點名稱"
                    : "現在不能送出午餐選擇";
            refresh();
            return;
        }
        BlackjackSession sentFor = session;
        lunchSending = true;
        lunchWarning = null;
        refresh();
        host.sendPayload(peerId, payload, ok -> {
            if (sentFor != session) {
                return;
            }
            lunchSending = false;
            if (Boolean.TRUE.equals(ok) && session.confirmLunchSent(text)) {
                lunchRetryText = null;
                refresh();
                host.lunchDecided(this, session.lunch());
            } else {
                lunchRetryText = text;
                lunchWarning = "沒有送出，對方可能還不知道。請確認雲端連線後按「再送一次」";
                refresh();
            }
        });
    }

    private void onAccept() {
        String payload = session.accept();
        if (payload == null) {
            return;
        }
        refresh();
        send(payload);
    }

    private void onDecline() {
        String payload = session.decline();
        if (payload == null) {
            return;
        }
        refresh();
        host.sendPayload(peerId, payload, ok -> {
        });
        dispose();
    }

    private void onHit() {
        String payload = session.hit();
        if (payload == null) {
            return;
        }
        refresh();
        send(payload);
    }

    private void onStand() {
        String payload = session.stand();
        if (payload == null) {
            return;
        }
        refresh();
        send(payload);
    }

    private void send(String payload) {
        BlackjackSession sentFor = session;
        sending = true;
        failedPayload = null;
        refresh();
        host.sendPayload(peerId, payload, ok -> {
            if (sentFor != session) {
                return;
            }
            sending = false;
            failedPayload = Boolean.TRUE.equals(ok) ? null : payload;
            refresh();
        });
    }

    private void refresh() {
        BlackjackSession.Phase phase = session.phase();
        boolean freshSession = session != renderedSession;
        renderedSession = session;
        if (freshSession) {
            table.clearResult();
            if (resultDelay != null) {
                resultDelay.stop();
            }
        }

        if (freshSession) {
            table.peerBadge().setPlayer(peerId, host.avatarOf(peerId));
            table.myBadge().setPlayer(session.localId(), host.avatarOf(session.localId()));
        }
        boolean dealt = !session.myHand().isEmpty();
        refreshHands(freshSession, dealt);
        refreshBadge(table.myBadge(), dealt, session.myScore(), session.myStood(),
                phase == BlackjackSession.Phase.PLAYING && session.myTurn());
        refreshBadge(table.peerBadge(), dealt, session.peerScore(), session.peerStood(),
                phase == BlackjackSession.Phase.PLAYING && !session.myTurn() && !session.peerStood());
        table.myHand().setDimmed(BlackjackRules.isBust(session.myHand()));
        table.peerHand().setDimmed(BlackjackRules.isBust(session.peerHand()));
        table.center().setDeckLeft(session.deckLeft());
        refreshCenter(phase);
        refreshButtons(phase);
        refreshFooter(phase);
        refreshLunch(phase);
        refreshResult(phase);
    }

    private void refreshLunch(BlackjackSession.Phase phase) {
        LunchChallenge lunch = session.lunch();
        boolean ended = phase == BlackjackSession.Phase.SETTLED || phase == BlackjackSession.Phase.CLOSED;
        if (lunch == null || !ended || lunch.status() == LunchChallenge.Status.PLAYING) {
            lunchPanel.setVisible(false);
            renderedLunch = null;
            return;
        }
        LunchPanel.View view;
        switch (lunch.status()) {
            case AWAITING_CHOICE:
                view = lunch.canChoose(session.localId()) ? LunchPanel.View.CHOOSE : LunchPanel.View.WAIT;
                break;
            case DECIDED:
                view = LunchPanel.View.DECIDED;
                break;
            case DRAW:
                view = LunchPanel.View.DRAW;
                break;
            default:
                view = LunchPanel.View.ABANDONED;
                break;
        }
        String winner = lunch.winnerId() == null ? null
                : lunch.winnerId().equals(session.localId()) ? "你" : "【" + lunch.winnerId() + "】";
        lunchPanel.show(view, winner, lunch.choice(), lunchSending, lunchRetryText, lunchWarning);
        boolean appeared = !lunchPanel.isVisible() || renderedLunch != lunch.status();
        lunchPanel.setVisible(true);
        renderedLunch = lunch.status();
        if (appeared && view == LunchPanel.View.CHOOSE) {
            lunchPanel.focusInput();
        }
    }

    private void refreshHands(boolean freshSession, boolean dealt) {
        HandView mine = table.myHand();
        HandView theirs = table.peerHand();
        int slots = dealt ? 0 : 2;
        mine.setSlots(slots);
        theirs.setSlots(slots);
        if (freshSession || !dealt) {
            mine.setCards(java.util.List.of(), false, 0, 0);
            theirs.setCards(java.util.List.of(), false, 0, 0);
        }
        if (!dealt) {
            return;
        }
        int step = HandView.STAGGER_MS;
        if (mine.cardCount() == 0 && theirs.cardCount() == 0) {
            // 開局發牌：邀請方、受邀方、邀請方、受邀方
            int myFirst = session.host() ? 0 : step;
            int peerFirst = session.host() ? step : 0;
            mine.setCards(session.myHand(), true, myFirst, step * 2);
            theirs.setCards(session.peerHand(), true, peerFirst, step * 2);
        } else {
            mine.setCards(session.myHand(), true, 0, step);
            theirs.setCards(session.peerHand(), true, 0, step);
        }
    }

    private static void refreshBadge(PlayerBadge badge, boolean dealt, int score, boolean stood, boolean active) {
        badge.setScore(dealt ? score : -1);
        badge.setActive(active);
        if (!dealt) {
            badge.setTag(null, null);
        } else if (score > 21) {
            badge.setTag("爆牌！", BUST);
        } else if (score == 21) {
            badge.setTag("21 點！", GOLD);
        } else if (stood) {
            badge.setTag("停牌", CandyTheme.LAVENDER);
        } else {
            badge.setTag(null, null);
        }
    }

    private void refreshCenter(BlackjackSession.Phase phase) {
        String status = session.status();
        if (failedPayload != null) {
            status = "送出失敗，請確認雲端連線後按「重送」";
        }
        table.center().setStatus(status);
        switch (phase) {
            case INVITING:
                table.center().setTurn("等待對方接受", CandyTheme.LAVENDER, true);
                break;
            case INVITED:
                table.center().setTurn("收到挑戰！", CandyTheme.PINK_HI, false);
                break;
            case PLAYING:
                if (sending) {
                    table.center().setTurn("送出中", MINT, true);
                } else if (session.myTurn()) {
                    table.center().setTurn("輪到你！", GOLD, false);
                } else {
                    table.center().setTurn("對方回合", MINT, true);
                }
                break;
            case SETTLED:
                table.center().setTurn("", null, false);
                break;
            default:
                boolean decided = session.outcome() != BlackjackSession.Outcome.NONE;
                table.center().setTurn(decided ? "" : "對戰結束", CandyTheme.MUTED, false);
                break;
        }
    }

    private void refreshButtons(BlackjackSession.Phase phase) {
        boolean invited = phase == BlackjackSession.Phase.INVITED;
        boolean playing = phase == BlackjackSession.Phase.PLAYING;
        boolean ended = phase == BlackjackSession.Phase.SETTLED || phase == BlackjackSession.Phase.CLOSED;
        acceptButton.setVisible(invited);
        declineButton.setVisible(invited);
        hitButton.setVisible(!invited && !ended);
        standButton.setVisible(!invited && !ended);
        boolean idle = !sending && failedPayload == null;
        acceptButton.setEnabled(idle && session.canAccept());
        declineButton.setEnabled(idle && session.canDecline());
        hitButton.setEnabled(idle && playing && session.canHit());
        standButton.setEnabled(idle && playing && session.canStand());
        resendButton.setVisible(failedPayload != null);
        resendButton.setEnabled(!sending);
        rematchButton.setVisible(ended);
        rematchButton.setEnabled(canRematch());
    }

    private void refreshFooter(BlackjackSession.Phase phase) {
        if (session.isLunch()) {
            modeChip.setIcon(PixelArt.bentoIcon(1));
            modeChip.setChip("午餐爭奪戰", CandyTheme.PINK_HI);
        } else {
            modeChip.setIcon(null);
            modeChip.setChip(session.downgraded() ? "普通對戰（對方版本不支援午餐）" : "普通對戰", CandyTheme.LAVENDER);
        }
        String message;
        Color color = CandyTheme.MUTED;
        if (failedPayload != null) {
            message = "動作沒有送出，對方還看不到";
            color = LOSE_INK;
        } else if (sending) {
            message = "傳送中，約 15 秒內隨心跳送達";
        } else if (phase == BlackjackSession.Phase.SETTLED) {
            message = resultTitle() + "  你 " + session.myScore() + " ：對方 " + session.peerScore();
            color = outcomeInk();
        } else if (phase == BlackjackSession.Phase.PLAYING && !session.myTurn()) {
            message = "等待對方出牌（隨心跳約 15 秒）";
        } else if (phase == BlackjackSession.Phase.CLOSED) {
            message = session.status();
        } else {
            message = "全明牌，邀請方先要牌，停牌後換人";
        }
        messageLabel.setText(message);
        messageLabel.setForeground(color);
    }

    private void refreshResult(BlackjackSession.Phase phase) {
        BlackjackTablePanel.Result result = tableResult();
        boolean ended = phase == BlackjackSession.Phase.SETTLED || phase == BlackjackSession.Phase.CLOSED;
        if (!ended || result == null) {
            table.clearResult();
            return;
        }
        if (result == table.result()) {
            return;
        }
        boolean animating = table.myHand().isAnimating() || table.peerHand().isAnimating();
        String detail = resultDetail();
        String title = resultTitle();
        if (resultDelay != null) {
            resultDelay.stop();
        }
        if (!animating) {
            table.showResult(result, title, detail);
            return;
        }
        BlackjackSession forSession = session;
        resultDelay = new Timer(HandView.DEAL_MS + 120, e -> {
            if (forSession == session && session.outcome() != BlackjackSession.Outcome.NONE) {
                table.showResult(result, title, detail);
            }
        });
        resultDelay.setRepeats(false);
        resultDelay.start();
    }

    private BlackjackTablePanel.Result tableResult() {
        switch (session.outcome()) {
            case WIN:
                return BlackjackTablePanel.Result.WIN;
            case LOSE:
                return BlackjackTablePanel.Result.LOSE;
            case DRAW:
                return BlackjackTablePanel.Result.DRAW;
            default:
                return null;
        }
    }

    private String resultTitle() {
        switch (session.outcome()) {
            case WIN:
                return "YOU WIN!";
            case LOSE:
                return "再接再厲！";
            case DRAW:
                return "平手！再來一局？";
            default:
                return "";
        }
    }

    private String resultDetail() {
        String score = "你 " + session.myScore() + " 點 ： 對方 " + session.peerScore() + " 點";
        if (!session.isLunch()) {
            return score;
        }
        switch (session.outcome()) {
            case WIN:
                return "午餐決定權歸你！  " + score;
            case LOSE:
                return "今天午餐由對方決定  " + score;
            default:
                return "平手不算，再戰一局決定午餐";
        }
    }

    private Color outcomeInk() {
        switch (session.outcome()) {
            case WIN:
                return WIN_INK;
            case LOSE:
                return LOSE_INK;
            default:
                return CandyTheme.LAVENDER;
        }
    }

    private static CandyButton button(String text, CandyButton.Variant variant, CandyButton.Size size) {
        CandyButton button = new CandyButton(text, variant, size);
        button.setFont(size == CandyButton.Size.LARGE ? UiFonts.chineseBold(15) : UiFonts.chinesePlain(13));
        return button;
    }

    /** 底部的小圓角標籤（目前模式）。 */
    static final class Chip extends JLabel {
        private Color fill = CandyTheme.LAVENDER;

        Chip() {
            setOpaque(false);
            setForeground(CandyTheme.PINK_INK);
            setBorder(new EmptyBorder(3, 10, 3, 10));
        }

        void setChip(String text, Color color) {
            setText(text);
            fill = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            int h = getHeight() - 1;
            g.fillRoundRect(0, 0, getWidth() - 1, h, h, h);
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
