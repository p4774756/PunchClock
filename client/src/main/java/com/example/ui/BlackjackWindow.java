package com.example.ui;

import com.example.BlackjackProtocol;
import com.example.BlackjackRules;
import com.example.service.BlackjackSession;
import com.example.service.LunchChallenge;
import com.example.ui.blackjack.BlackjackScene;
import com.example.ui.blackjack.BlackjackTablePanel;
import com.example.ui.blackjack.CasinoButton;
import com.example.ui.blackjack.CasinoFooter;
import com.example.ui.blackjack.CasinoHeader;
import com.example.ui.blackjack.CasinoIcons;
import com.example.ui.blackjack.CasinoStage;
import com.example.ui.blackjack.CasinoTheme;
import com.example.ui.blackjack.HandView;
import com.example.ui.blackjack.LunchPanel;
import com.example.ui.blackjack.PlayerBadge;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/**
 * 兩人 21 點視窗：賭場牌桌、操作鈕與底部功能列。遊戲規則全在 {@link BlackjackSession}，
 * 這裡只把狀態畫出來、把按鈕動作轉成協定字串交給 {@link Host} 送出。
 */
public final class BlackjackWindow extends JFrame {

    public interface Host {
        void sendPayload(String peerId, String payload, Consumer<Boolean> done);

        void gameClosed(BlackjackWindow window);

        /** 玩家頭像；沒有就回 null，改畫預設角色。 */
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

    private static final Color WARN_INK = new Color(0xFF, 0xB4, 0xBE);
    private static final Color WIN_INK = new Color(0xB6, 0xF2, 0xC4);
    private static final Color LOSE_INK = new Color(0xFF, 0xC0, 0xC8);
    private static final int MIN_W = 720;
    private static final int MIN_H = 680;

    private final String peerId;
    private final Host host;
    private BlackjackSession session;

    private final CasinoStage stage;
    private final CasinoHeader header;
    private final BlackjackTablePanel table;
    private final CasinoFooter footer;
    private final CasinoButton acceptButton;
    private final CasinoButton declineButton;
    private final CasinoButton hitButton;
    private final CasinoButton standButton;
    private final CasinoButton resendButton;
    private final CasinoButton cancelButton;
    private final CasinoButton rematchButton;
    private final CasinoButton closeButton;
    private final LunchPanel lunchPanel;

    private BlackjackSession renderedSession;
    private boolean sending;
    private String failedPayload;
    private boolean cancelledByMe;
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

        acceptButton = large("接受挑戰", "(Accept)", CasinoIcons.CHECK, CasinoButton.Tone.GREEN);
        declineButton = large("婉拒", "(Decline)", CasinoIcons.CLOSE, CasinoButton.Tone.RED);
        hitButton = large("要牌", "(Hit)", CasinoIcons.CARDS, CasinoButton.Tone.GREEN);
        standButton = large("停牌", "(Stand)", CasinoIcons.HAND, CasinoButton.Tone.RED);
        resendButton = small("重送", CasinoIcons.SEND, CasinoButton.Tone.RED);
        cancelButton = small("取消邀請", CasinoIcons.REFRESH, CasinoButton.Tone.PURPLE);
        rematchButton = small("再戰一局", CasinoIcons.REFRESH, CasinoButton.Tone.PURPLE);
        closeButton = small("關閉", CasinoIcons.CLOSE, CasinoButton.Tone.PURPLE);
        rematchButton.setToolTipText("開新的一局，牌堆重洗，模式沿用這一局");
        cancelButton.setToolTipText("收回這次邀請，對方收到後會關閉邀請（約 15 秒）");
        acceptButton.setToolTipText("接受後雙方同時開局");
        hitButton.setToolTipText("再拿一張牌（超過 21 點就爆牌）");
        standButton.setToolTipText("不再拿牌，換對方行動");
        resendButton.setToolTipText("上一個動作沒送到雲端，再送一次");

        acceptButton.addActionListener(e -> onAccept());
        declineButton.addActionListener(e -> onDecline());
        hitButton.addActionListener(e -> onHit());
        standButton.addActionListener(e -> onStand());
        cancelButton.addActionListener(e -> onCancelInvite());
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

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 34, 0));
        actions.setOpaque(false);
        actions.add(acceptButton);
        actions.add(declineButton);
        actions.add(hitButton);
        actions.add(standButton);

        header = new CasinoHeader();
        table = new BlackjackTablePanel();
        lunchPanel = table.lunchBoard();
        lunchPanel.onSubmit(this::onLunchSubmit);
        footer = new CasinoFooter(resendButton, cancelButton, rematchButton, closeButton);
        stage = new CasinoStage(header, table, actions, footer);
        setContentPane(stage);
        sizeToScreen();

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

    /** 預設用設計尺寸，但不超過螢幕可用範圍的 90%；最小 720×680。 */
    private void sizeToScreen() {
        float scale = 1f;
        try {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            scale = Math.min(1f, Math.min(screen.width * 0.9f / CasinoTheme.DESIGN_W,
                    screen.height * 0.9f / CasinoTheme.DESIGN_H));
        } catch (RuntimeException ignored) {
            // 無螢幕環境（測試）就用設計尺寸
        }
        int w = Math.max(MIN_W, Math.round(CasinoTheme.DESIGN_W * scale));
        int h = Math.max(MIN_H, Math.round(CasinoTheme.DESIGN_H * scale));
        stage.setPreferredSize(new Dimension(w, h));
        pack();
        Insets insets = getInsets();
        setMinimumSize(new Dimension(MIN_W + insets.left + insets.right, MIN_H + insets.top + insets.bottom));
        setLocationByPlatform(true);
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
        cancelledByMe = false;
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
            switch (settleLunchSend(session, Boolean.TRUE.equals(ok), text)) {
                case DECIDED:
                    lunchRetryText = null;
                    refresh();
                    host.lunchDecided(this, session.lunch());
                    break;
                case ENDED:
                    lunchRetryText = null;
                    lunchWarning = null;
                    refresh();
                    break;
                default:
                    lunchRetryText = text;
                    lunchWarning = "沒有送出，對方可能還不知道。請確認雲端連線後按「再送一次」";
                    refresh();
                    break;
            }
        });
    }

    enum LunchSendResult { DECIDED, ENDED, RETRY }

    /** 午餐送出的回呼結果；送出期間對方已離開（這場關閉或午餐作廢）時沒有可重送的對象。 */
    static LunchSendResult settleLunchSend(BlackjackSession session, boolean ok, String text) {
        if (ok && session.confirmLunchSent(text)) {
            return LunchSendResult.DECIDED;
        }
        LunchChallenge lunch = session.lunch();
        if (session.phase() != BlackjackSession.Phase.SETTLED || lunch == null
                || lunch.status() != LunchChallenge.Status.AWAITING_CHOICE) {
            return LunchSendResult.ENDED;
        }
        return LunchSendResult.RETRY;
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

    /** 收回還沒被接受的邀請：沿用既有的中止訊息，視窗留著可以重新邀請。 */
    private void onCancelInvite() {
        if (session.phase() != BlackjackSession.Phase.INVITING) {
            return;
        }
        String payload = session.abort();
        if (payload == null) {
            return;
        }
        cancelledByMe = true;
        send(payload);
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
        BlackjackScene scene = BlackjackScene.of(session);
        boolean freshSession = session != renderedSession;
        renderedSession = session;
        if (freshSession) {
            table.clearResult();
            if (resultDelay != null) {
                resultDelay.stop();
            }
            Image peerAvatar = host.avatarOf(peerId);
            Image myAvatar = host.avatarOf(session.localId());
            table.peerBadge().setPlayer(peerId, peerAvatar);
            table.myBadge().setPlayer(session.localId(), myAvatar);
            table.waitingView().setPlayers(session.localId(), myAvatar, peerId, peerAvatar);
        }
        table.setWaiting(scene.showsWaitingRoom());
        boolean dealt = !session.myHand().isEmpty();
        refreshHands(freshSession, dealt);
        boolean finished = phase == BlackjackSession.Phase.SETTLED || phase == BlackjackSession.Phase.CLOSED;
        refreshBadge(table.myBadge(), dealt, finished, session.myScore(), session.myStood(),
                phase == BlackjackSession.Phase.PLAYING && session.myTurn());
        refreshBadge(table.peerBadge(), dealt, finished, session.peerScore(), session.peerStood(),
                phase == BlackjackSession.Phase.PLAYING && !session.myTurn() && !session.peerStood());
        table.myHand().setDimmed(BlackjackRules.isBust(session.myHand()));
        table.peerHand().setDimmed(BlackjackRules.isBust(session.peerHand()));
        table.center().setDeckLeft(session.deckLeft());
        refreshWaiting(scene);
        refreshCenter(phase);
        refreshButtons(phase);
        refreshHeader();
        refreshFooter(phase, scene);
        refreshLunch(phase);
        refreshResult(phase);
        stage.actionsChanged();
    }

    private void refreshWaiting(BlackjackScene scene) {
        String challenge = session.isLunch() ? "午餐爭奪戰" : "21 點挑戰";
        switch (scene) {
            case WAITING:
                if (failedPayload != null) {
                    table.waitingView().setMessage("邀請沒有送出", "請確認雲端連線後按下方「重送」", false);
                } else {
                    table.waitingView().setMessage("已向「" + peerId + "」發出" + challenge + "！",
                            sending ? "邀請傳送中…" : "等待對方接受…（約 15 秒內送達）", true);
                }
                break;
            case INVITED:
                table.waitingView().setMessage("「" + peerId + "」向你發起" + challenge + "！",
                        session.isLunch() ? "贏家決定今天午餐・接受後雙方同時開局" : "全明牌比點數・接受後雙方同時開局",
                        false);
                break;
            case CLOSED:
                if (cancelledByMe) {
                    table.waitingView().setMessage("已取消邀請",
                            failedPayload != null ? "取消訊息沒有送出，請按「重送」" : "想再挑戰可以按「重新邀請」", false);
                } else {
                    table.waitingView().setMessage(session.status().isEmpty() ? "這次沒有開局" : session.status(),
                            "想再挑戰可以按「重新邀請」", false);
                }
                break;
            default:
                break;
        }
    }

    private void refreshLunch(BlackjackSession.Phase phase) {
        LunchChallenge lunch = session.lunch();
        boolean ended = phase == BlackjackSession.Phase.SETTLED || phase == BlackjackSession.Phase.CLOSED;
        // 平手由結算面板說明（雙方對稱），不另開公告板
        if (lunch == null || !ended || lunch.status() == LunchChallenge.Status.PLAYING
                || lunch.status() == LunchChallenge.Status.DRAW || session.myHand().isEmpty()) {
            table.showLunchBoard(false);
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
        String scores = "你 " + session.myScore() + " 點 ： " + peerId + " " + session.peerScore() + " 點";
        lunchPanel.show(view, winner, lunch.choice(), scores, lunchSending, lunchRetryText, lunchWarning);
        boolean appeared = !lunchPanel.isVisible() || renderedLunch != lunch.status();
        table.showLunchBoard(true);
        renderedLunch = lunch.status();
        if (appeared && view == LunchPanel.View.CHOOSE) {
            lunchPanel.focusInput();
        }
    }

    private void refreshHands(boolean freshSession, boolean dealt) {
        HandView mine = table.myHand();
        HandView theirs = table.peerHand();
        mine.setSlots(0);
        theirs.setSlots(0);
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

    private static void refreshBadge(PlayerBadge badge, boolean dealt, boolean finished, int score, boolean stood,
                                     boolean active) {
        badge.setScore(dealt ? score : -1);
        badge.setActive(active);
        if (!dealt) {
            badge.setTag(null);
        } else if (score > 21) {
            badge.setTag(PlayerBadge.Tag.BUST, finished);
        } else if (score == 21) {
            badge.setTag(PlayerBadge.Tag.TWENTY_ONE, finished);
        } else if (stood) {
            badge.setTag(PlayerBadge.Tag.STAND, finished);
        } else {
            badge.setTag(null, finished);
        }
    }

    private void refreshCenter(BlackjackSession.Phase phase) {
        String status = session.status();
        if (failedPayload != null) {
            status = "送出失敗，請確認雲端連線後按「重送」";
        }
        switch (phase) {
            case PLAYING:
                table.center().setStatus(status);
                if (sending) {
                    table.center().setTurn("送出中", false, true);
                } else if (session.myTurn()) {
                    table.center().setTurn("輪到你出牌！", true, false);
                } else {
                    table.center().setTurn("等待「" + peerId + "」出牌", false, true);
                }
                break;
            case SETTLED:
                table.center().setStatus("");
                table.center().setTurn("", false, false);
                break;
            default:
                boolean decided = session.outcome() != BlackjackSession.Outcome.NONE;
                table.center().setStatus(decided ? "" : status);
                table.center().setTurn(decided ? "" : "對戰結束", false, false);
                break;
        }
    }

    private void refreshButtons(BlackjackSession.Phase phase) {
        boolean invited = phase == BlackjackSession.Phase.INVITED;
        boolean playing = phase == BlackjackSession.Phase.PLAYING;
        boolean ended = phase == BlackjackSession.Phase.SETTLED || phase == BlackjackSession.Phase.CLOSED;
        acceptButton.setVisible(invited);
        declineButton.setVisible(invited);
        hitButton.setVisible(playing);
        standButton.setVisible(playing);
        boolean idle = !sending && failedPayload == null;
        acceptButton.setEnabled(idle && session.canAccept());
        declineButton.setEnabled(idle && session.canDecline());
        hitButton.setEnabled(idle && playing && session.canHit());
        standButton.setEnabled(idle && playing && session.canStand());
        resendButton.setVisible(failedPayload != null);
        resendButton.setEnabled(!sending);
        cancelButton.setVisible(phase == BlackjackSession.Phase.INVITING);
        cancelButton.setEnabled(!sending);
        LunchChallenge lunch = session.lunch();
        boolean lunchPending = lunch != null && lunch.status() == LunchChallenge.Status.AWAITING_CHOICE;
        rematchButton.setText(session.myHand().isEmpty() ? "重新邀請" : "再戰一局");
        rematchButton.setVisible(ended && !lunchPending);
        rematchButton.setEnabled(canRematch() && !sending);
    }

    private void refreshHeader() {
        if (session.isLunch()) {
            header.setMode(true, "午餐爭奪戰", "贏家決定今天午餐！");
        } else if (session.downgraded()) {
            header.setMode(false, "普通對戰", "對方版本不支援午餐");
        } else {
            header.setMode(false, "普通對戰", "最接近 21 點者勝");
        }
    }

    private void refreshFooter(BlackjackSession.Phase phase, BlackjackScene scene) {
        footer.setMode(session.isLunch(), session.isLunch() ? "午餐爭奪戰" : "普通對戰");
        String message;
        Color color = CasinoTheme.TEXT;
        if (failedPayload != null) {
            message = "動作沒有送出，對方還看不到";
            color = WARN_INK;
        } else if (sending) {
            message = "傳送中，約 15 秒內隨心跳送達";
        } else if (phase == BlackjackSession.Phase.SETTLED) {
            message = BlackjackScene.resultTitle(session.outcome())
                    + "  你 " + session.myScore() + " ：對方 " + session.peerScore();
            color = outcomeInk();
        } else if (phase == BlackjackSession.Phase.PLAYING && !session.myTurn()) {
            message = "等待對方出牌（隨心跳約 15 秒）";
        } else if (scene == BlackjackScene.CLOSED || phase == BlackjackSession.Phase.CLOSED) {
            message = cancelledByMe ? "邀請已取消" : session.status();
        } else if (session.isLunch()) {
            message = "雙人對戰・21 點・贏家決定今天午餐";
        } else if (session.downgraded()) {
            message = "對方版本不支援午餐，改為普通對戰";
        } else {
            message = "雙人對戰・21 點・全明牌，邀請方先要牌";
        }
        footer.setMessage(message, color);
    }

    private void refreshResult(BlackjackSession.Phase phase) {
        BlackjackScene.Verdict result = BlackjackScene.verdict(session);
        boolean ended = phase == BlackjackSession.Phase.SETTLED || phase == BlackjackSession.Phase.CLOSED;
        if (!ended || result == BlackjackScene.Verdict.NONE) {
            table.clearResult();
            return;
        }
        String detail = resultDetail(result);
        int mine = session.myScore();
        int theirs = session.peerScore();
        if (result == table.result()) {
            table.showResult(result, mine, theirs, detail);
            return;
        }
        boolean animating = table.myHand().isAnimating() || table.peerHand().isAnimating();
        if (resultDelay != null) {
            resultDelay.stop();
        }
        if (!animating) {
            table.showResult(result, mine, theirs, detail);
            return;
        }
        BlackjackSession forSession = session;
        resultDelay = new Timer(HandView.DEAL_MS + 120, e -> {
            if (forSession == session && session.outcome() != BlackjackSession.Outcome.NONE) {
                table.showResult(result, mine, theirs, detail);
            }
        });
        resultDelay.setRepeats(false);
        resultDelay.start();
    }

    /** 結算面板中間的說明；點數已畫在頭像下方，這裡只講怎麼分出勝負。 */
    private String resultDetail(BlackjackScene.Verdict verdict) {
        boolean peerBust = BlackjackRules.isBust(session.peerHand());
        if (session.isLunch() && verdict == BlackjackScene.Verdict.DRAW) {
            return "平手不算，再戰一局決定午餐";
        }
        switch (verdict) {
            case WIN:
                return peerBust ? "對方爆牌（" + session.peerScore() + " 點），這局你贏了" : "你最接近 21 點，贏下這局";
            case BUST:
                return "超過 21 點（" + session.myScore() + " 點），這局算輸";
            case LOSE:
                return "對方更接近 21 點，下一局再扳回來";
            default:
                return "雙方同為 " + session.myScore() + " 點，不分勝負";
        }
    }

    private Color outcomeInk() {
        switch (session.outcome()) {
            case WIN:
                return WIN_INK;
            case LOSE:
                return LOSE_INK;
            default:
                return CasinoTheme.PURPLE_LIGHT;
        }
    }

    private static CasinoButton large(String text, String sub, CasinoIcons icon, CasinoButton.Tone tone) {
        return new CasinoButton(text, sub, icon, tone, CasinoButton.Kind.LARGE);
    }

    private static CasinoButton small(String text, CasinoIcons icon, CasinoButton.Tone tone) {
        return new CasinoButton(text, null, icon, tone, CasinoButton.Kind.SMALL);
    }
}
