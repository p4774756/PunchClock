package com.example.service;

/** 收到同事的 21 點邀請時，依本機和這位同事目前那一場決定怎麼處理。 */
public final class BlackjackInvitePolicy {

    public enum Decision {
        /** 同一個 roomId 又收到一次，忽略。 */
        DUPLICATE,
        /** 本機沒有進行中的局，開新局（或取代已結束的那場）。 */
        OPEN,
        /** 雙方同時邀請，對方的 roomId 較小，改用對方那一場。 */
        SWITCH_TO_PEER,
        /** 雙方同時邀請，本機的 roomId 較小，保留本機；對方收到本機邀請後會改用這場，所以不回覆。 */
        KEEP_LOCAL,
        /** 本機已有對戰或還在等午餐，回覆 decline，避免對方一直停在等待接受。 */
        DECLINE_BUSY
    }

    private BlackjackInvitePolicy() {
    }

    public static Decision onInvite(BlackjackSession existing, String inviteRoomId) {
        if (existing == null) {
            return Decision.OPEN;
        }
        if (existing.roomId().equals(inviteRoomId)) {
            return Decision.DUPLICATE;
        }
        if (!isBusy(existing)) {
            return Decision.OPEN;
        }
        if (existing.phase() == BlackjackSession.Phase.INVITING) {
            // roomId 較小的那場勝出，兩邊各自判斷結果一致
            return inviteRoomId.compareTo(existing.roomId()) < 0 ? Decision.SWITCH_TO_PEER : Decision.KEEP_LOCAL;
        }
        return Decision.DECLINE_BUSY;
    }

    public static boolean isBusy(BlackjackSession session) {
        BlackjackSession.Phase phase = session.phase();
        if (phase == BlackjackSession.Phase.INVITING
                || phase == BlackjackSession.Phase.INVITED
                || phase == BlackjackSession.Phase.PLAYING) {
            return true;
        }
        LunchChallenge lunch = session.lunch();
        return phase == BlackjackSession.Phase.SETTLED && lunch != null
                && lunch.status() == LunchChallenge.Status.AWAITING_CHOICE;
    }
}
