package com.example.ui.hud;

import java.util.Random;

/**
 * 角色演出。待機時以不固定的間隔眨眼；揮手、微笑、收到訊息播完就回到待機。
 * 排程或聊天要觸發表情，呼叫 {@link #play(int, double)}。
 */
public final class HeroMotion {

    private static final double BLINK_HOLD = 0.22;
    private static final double BLINK_GAP_MIN = 2.6;
    private static final double BLINK_GAP_MAX = 6.4;

    private final Random random = new Random();
    private int gesture = AnimeCast.IDLE;
    private double gestureLeft;
    private double untilBlink = nextGap();
    private boolean blinking;

    public void tick(double dt) {
        if (dt <= 0) {
            return;
        }
        if (gestureLeft > 0) {
            gestureLeft = Math.max(0, gestureLeft - dt);
            if (gestureLeft == 0) {
                gesture = AnimeCast.IDLE;
            }
            return;
        }
        untilBlink -= dt;
        if (untilBlink > 0) {
            return;
        }
        if (blinking) {
            blinking = false;
            untilBlink = nextGap();
        } else {
            blinking = true;
            untilBlink = BLINK_HOLD;
        }
    }

    /** 播一格角色表上的表情，秒數到了回到待機。眨眼不用呼叫。 */
    public void play(int frame, double seconds) {
        gesture = frame;
        gestureLeft = Math.max(0.2, seconds);
        blinking = false;
        if (untilBlink < BLINK_GAP_MIN) {
            untilBlink = nextGap();
        }
    }

    public int frame() {
        if (gestureLeft > 0 && gesture != AnimeCast.IDLE) {
            return gesture;
        }
        return blinking ? AnimeCast.BLINK : AnimeCast.IDLE;
    }

    private double nextGap() {
        return BLINK_GAP_MIN + random.nextDouble() * (BLINK_GAP_MAX - BLINK_GAP_MIN);
    }
}
