package com.example.ui.hud;

/**
 * 角色演出。待機時自己眨眼；揮手、微笑、收到訊息播完就回到待機。
 * 之後排程或聊天要觸發表情，呼叫 {@link #play(int, double)} 即可。
 */
public final class HeroMotion {

    private static final double BLINK_EVERY = 3.6;
    private static final double BLINK_HOLD = 0.22;

    private int gesture = AnimeCast.IDLE;
    private double gestureLeft;
    private double phase;

    public void tick(double dt) {
        if (dt <= 0) {
            return;
        }
        phase += dt;
        if (gestureLeft > 0) {
            gestureLeft = Math.max(0, gestureLeft - dt);
            if (gestureLeft == 0) {
                gesture = AnimeCast.IDLE;
            }
        }
    }

    /** 播一格角色表上的表情，秒數到了回到待機。眨眼不用呼叫。 */
    public void play(int frame, double seconds) {
        gesture = frame;
        gestureLeft = Math.max(0.2, seconds);
    }

    public int frame() {
        if (gestureLeft > 0 && gesture != AnimeCast.IDLE) {
            return gesture;
        }
        double cycle = phase % BLINK_EVERY;
        if (cycle > BLINK_EVERY - BLINK_HOLD) {
            return AnimeCast.BLINK;
        }
        return AnimeCast.IDLE;
    }
}
