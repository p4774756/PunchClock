package com.example.ui;

import com.example.BlackjackRules;
import com.example.BlackjackRules.Card;
import com.example.service.BlackjackSession;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * 兩人 21 點小視窗：邀請、出牌、結算。動作經 {@link Host} 送出。
 */
public final class BlackjackWindow extends JFrame {

    public interface Host {
        void sendPayload(String peerId, String payload, Consumer<Boolean> done);

        void gameClosed(BlackjackWindow window);
    }

    private final String peerId;
    private final Host host;
    private BlackjackSession session;

    private final JLabel statusLabel;
    private final JLabel peerHandLabel;
    private final JLabel myHandLabel;
    private final JLabel peerScoreLabel;
    private final JLabel myScoreLabel;
    private final JButton acceptButton;
    private final JButton declineButton;
    private final JButton hitButton;
    private final JButton standButton;
    private final JButton closeButton;
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

        Font plain = UiFonts.chinesePlain(13);
        Font bold = UiFonts.chineseBold(14);
        Font cardFont = UiFonts.chineseBold(18);

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(plain);
        statusLabel.setForeground(CandyTheme.PINK_HI);
        statusLabel.setBorder(new EmptyBorder(8, 12, 8, 12));

        peerScoreLabel = new JLabel("對方", SwingConstants.CENTER);
        peerScoreLabel.setFont(bold);
        peerScoreLabel.setForeground(CandyTheme.MUTED);
        peerHandLabel = new JLabel("—", SwingConstants.CENTER);
        peerHandLabel.setFont(cardFont);
        peerHandLabel.setForeground(CandyTheme.INK);
        peerHandLabel.setOpaque(true);
        peerHandLabel.setBackground(CandyTheme.FIELD);
        peerHandLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CandyTheme.EDGE),
                new EmptyBorder(16, 12, 16, 12)));

        myScoreLabel = new JLabel("你", SwingConstants.CENTER);
        myScoreLabel.setFont(bold);
        myScoreLabel.setForeground(CandyTheme.PINK_HI);
        myHandLabel = new JLabel("—", SwingConstants.CENTER);
        myHandLabel.setFont(cardFont);
        myHandLabel.setForeground(CandyTheme.INK);
        myHandLabel.setOpaque(true);
        myHandLabel.setBackground(CandyTheme.FIELD);
        myHandLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CandyTheme.EDGE),
                new EmptyBorder(16, 12, 16, 12)));

        JPanel board = new JPanel();
        board.setLayout(new BoxLayout(board, BoxLayout.Y_AXIS));
        board.setBorder(new EmptyBorder(8, 16, 8, 16));
        board.setOpaque(false);
        peerScoreLabel.setAlignmentX(CENTER_ALIGNMENT);
        peerHandLabel.setAlignmentX(CENTER_ALIGNMENT);
        myScoreLabel.setAlignmentX(CENTER_ALIGNMENT);
        myHandLabel.setAlignmentX(CENTER_ALIGNMENT);
        peerHandLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        myHandLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        board.add(peerScoreLabel);
        board.add(Box.createVerticalStrut(6));
        board.add(peerHandLabel);
        board.add(Box.createVerticalStrut(18));
        board.add(myScoreLabel);
        board.add(Box.createVerticalStrut(6));
        board.add(myHandLabel);

        acceptButton = new CandyButton("接受", CandyButton.Variant.PRIMARY, CandyButton.Size.MEDIUM);
        declineButton = new CandyButton("拒絕", CandyButton.Variant.SECONDARY, CandyButton.Size.MEDIUM);
        hitButton = new CandyButton("要牌", CandyButton.Variant.PRIMARY, CandyButton.Size.MEDIUM);
        standButton = new CandyButton("停牌", CandyButton.Variant.SECONDARY, CandyButton.Size.MEDIUM);
        closeButton = new CandyButton("結束", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        acceptButton.setFont(plain);
        declineButton.setFont(plain);
        hitButton.setFont(plain);
        standButton.setFont(plain);
        closeButton.setFont(plain);

        acceptButton.addActionListener(e -> onAccept());
        declineButton.addActionListener(e -> onDecline());
        hitButton.addActionListener(e -> onHit());
        standButton.addActionListener(e -> onStand());
        closeButton.addActionListener(e -> dispose());

        JPanel inviteRow = new JPanel(new GridLayout(1, 2, 8, 0));
        inviteRow.setOpaque(false);
        inviteRow.add(acceptButton);
        inviteRow.add(declineButton);

        JPanel playRow = new JPanel(new GridLayout(1, 2, 8, 0));
        playRow.setOpaque(false);
        playRow.add(hitButton);
        playRow.add(standButton);

        JPanel actions = new JPanel(new BorderLayout(8, 8));
        actions.setBorder(new EmptyBorder(8, 16, 12, 16));
        actions.setOpaque(false);
        JPanel topActions = new JPanel();
        topActions.setLayout(new BoxLayout(topActions, BoxLayout.Y_AXIS));
        topActions.setOpaque(false);
        inviteRow.setAlignmentX(LEFT_ALIGNMENT);
        playRow.setAlignmentX(LEFT_ALIGNMENT);
        topActions.add(inviteRow);
        topActions.add(Box.createVerticalStrut(8));
        topActions.add(playRow);
        actions.add(topActions, BorderLayout.CENTER);
        JPanel endRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        endRow.setOpaque(false);
        endRow.add(closeButton);
        actions.add(endRow, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(CandyTheme.NIGHT);
        root.add(statusLabel, BorderLayout.NORTH);
        root.add(board, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        setContentPane(root);

        setMinimumSize(new Dimension(420, 420));
        setSize(460, 460);
        setLocationByPlatform(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (closing) {
                    return;
                }
                closing = true;
                String abort = session.abort();
                if (abort != null) {
                    host.sendPayload(peerId, abort, ok -> {
                    });
                }
                host.gameClosed(BlackjackWindow.this);
            }
        });

        NightTheme.apply(getContentPane());
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

    public void replaceSession(BlackjackSession next) {
        this.session = next;
        refresh();
    }

    public void onRemotePayload(String payload) {
        com.example.BlackjackProtocol.Message message = com.example.BlackjackProtocol.parse(payload);
        if (message == null) {
            return;
        }
        if (session.applyRemote(message)) {
            refresh();
        }
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
        send(payload);
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
        setActionEnabled(false);
        host.sendPayload(peerId, payload, ok -> {
            if (!Boolean.TRUE.equals(ok)) {
                statusLabel.setText("送出失敗，請確認對方在線與雲端連線");
                statusLabel.setForeground(new Color(255, 150, 150));
            }
            refresh();
        });
    }

    private void refresh() {
        statusLabel.setText(session.status());
        statusLabel.setForeground(outcomeColor());

        peerScoreLabel.setText("對方 · " + peerId
                + (session.phase() == BlackjackSession.Phase.PLAYING
                || session.phase() == BlackjackSession.Phase.SETTLED
                ? "（" + session.peerScore() + " 點）" : ""));
        myScoreLabel.setText("你"
                + (session.phase() == BlackjackSession.Phase.PLAYING
                || session.phase() == BlackjackSession.Phase.SETTLED
                ? "（" + session.myScore() + " 點）" : ""));

        peerHandLabel.setText(handText(session.peerHand(), session.phase()));
        myHandLabel.setText(handText(session.myHand(), session.phase()));
        colorHand(peerHandLabel, session.peerHand());
        colorHand(myHandLabel, session.myHand());

        boolean invited = session.canAccept();
        acceptButton.setVisible(invited);
        declineButton.setVisible(invited);
        hitButton.setVisible(!invited);
        standButton.setVisible(!invited);
        hitButton.setEnabled(session.canHit());
        standButton.setEnabled(session.canStand());
        acceptButton.setEnabled(invited);
        declineButton.setEnabled(session.canDecline());
        closeButton.setEnabled(true);
    }

    private void setActionEnabled(boolean enabled) {
        hitButton.setEnabled(enabled && session.canHit());
        standButton.setEnabled(enabled && session.canStand());
        acceptButton.setEnabled(enabled && session.canAccept());
        declineButton.setEnabled(enabled && session.canDecline());
    }

    private Color outcomeColor() {
        switch (session.outcome()) {
            case WIN:
                return new Color(140, 230, 170);
            case LOSE:
                return new Color(255, 150, 150);
            case DRAW:
                return CandyTheme.LAVENDER;
            default:
                return CandyTheme.PINK_HI;
        }
    }

    private static String handText(List<Card> cards, BlackjackSession.Phase phase) {
        if (phase == BlackjackSession.Phase.INVITING || phase == BlackjackSession.Phase.INVITED) {
            return "等待開局…";
        }
        if (cards == null || cards.isEmpty()) {
            return "—";
        }
        return BlackjackRules.formatHand(cards);
    }

    private static void colorHand(JLabel label, List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            label.setForeground(CandyTheme.MUTED);
            return;
        }
        boolean anyRed = false;
        for (Card card : cards) {
            if (card.isRed()) {
                anyRed = true;
                break;
            }
        }
        label.setForeground(anyRed ? new Color(0xFF, 0x9A, 0xB8) : CandyTheme.INK);
    }
}
