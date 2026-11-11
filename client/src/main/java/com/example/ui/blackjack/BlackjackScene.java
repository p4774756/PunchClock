package com.example.ui.blackjack;

import com.example.BlackjackRules;
import com.example.service.BlackjackSession;
import com.example.service.LunchChallenge;

/**
 * 從 session 判斷現在該顯示哪個畫面。純函式，不碰 Swing，方便測「輸的人不會看到 YOU WIN」這類規則。
 */
public enum BlackjackScene {
    /** 已發出邀請，等對方接受 */
    WAITING,
    /** 收到邀請，等自己決定 */
    INVITED,
    /** 對戰中 */
    PLAYING,
    /** 已分勝負（普通對戰，或午餐模式平手／已放棄） */
    RESULT,
    /** 午餐模式：我贏了，要選午餐 */
    LUNCH_CHOOSE,
    /** 午餐模式：對方贏了，等對方選 */
    LUNCH_WAIT,
    /** 午餐模式：午餐已決定 */
    LUNCH_DONE,
    /** 還沒開局就結束（婉拒、取消、對方離開） */
    CLOSED;

    public static BlackjackScene of(BlackjackSession session) {
        BlackjackSession.Phase phase = session.phase();
        switch (phase) {
            case INVITING:
                return WAITING;
            case INVITED:
                return INVITED;
            case PLAYING:
                return PLAYING;
            default:
                break;
        }
        if (session.myHand().isEmpty() && session.peerHand().isEmpty()) {
            return CLOSED;
        }
        LunchChallenge lunch = session.lunch();
        if (lunch != null && session.outcome() != BlackjackSession.Outcome.NONE) {
            switch (lunch.status()) {
                case AWAITING_CHOICE:
                    return lunch.canChoose(session.localId()) ? LUNCH_CHOOSE : LUNCH_WAIT;
                case DECIDED:
                    return LUNCH_DONE;
                default:
                    break;
            }
        }
        return RESULT;
    }

    /** 還沒有牌桌可看（邀請前後）時用等待畫面。 */
    public boolean showsWaitingRoom() {
        return this == WAITING || this == INVITED || this == CLOSED;
    }

    /** 結算面板要畫的結果。爆牌是「自己爆掉而輸」；對方爆牌時自己是 {@link #WIN}。 */
    public enum Verdict {
        WIN, LOSE, BUST, DRAW, NONE
    }

    /** 依本機實際勝負與手牌判斷，不看狀態文字。 */
    public static Verdict verdict(BlackjackSession session) {
        switch (session.outcome()) {
            case WIN:
                return Verdict.WIN;
            case LOSE:
                return BlackjackRules.isBust(session.myHand()) ? Verdict.BUST : Verdict.LOSE;
            case DRAW:
                return Verdict.DRAW;
            default:
                return Verdict.NONE;
        }
    }

    /** 結果標題：只依本機實際勝負決定，輸家與平手絕不會拿到勝利字樣。 */
    public static String resultTitle(BlackjackSession.Outcome outcome) {
        switch (outcome) {
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
}
