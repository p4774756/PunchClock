package com.example.pet;

import java.util.Random;

/**
 * 桌面寵物的動作。不含視窗與計時器，由視窗在 EDT 上逐格呼叫 {@link #tick()}。
 * <p>
 * 喵露待機：呼吸每張 300ms，眨眼每張 150ms。走路、揮手、坐下、睡覺、拖曳還沒有專用影格，
 * 這些狀態仍沿用待機圖。{@link #column()} 與 {@link #row()} 只給素材載入失敗時的像素角色。
 */
public final class DesktopPetAnimation {

    public enum Mode {
        IDLE, WALK, REACT
    }

    public static final int TICK_MS = 50;
    public static final int IDLE_TICKS = 70;
    public static final int WALK_TICKS = 110;
    public static final int REACT_TICKS = 28;
    public static final int BUBBLE_TICKS = 40;
    public static final int STEP = 2;
    static final int BREATH_HOLD_TICKS = 300 / TICK_MS;
    static final int BLINK_HOLD_TICKS = 150 / TICK_MS;
    static final int BLINK_MIN_TICKS = 3000 / TICK_MS;
    static final int BLINK_MAX_TICKS = 7000 / TICK_MS;

    static final String[] PHRASES = {
            "嗨",
            "我在這",
            "今天也要記得打卡",
            "拖我去別的地方吧"
    };

    private Mode mode = Mode.IDLE;
    private int poseTicks;
    private int direction = 1;
    private boolean carried;
    private int frameColumn;
    private int frameRow;
    private int bob;
    private String bubble;
    private int bubbleTicks;
    private int pokes;
    private final Random random;
    private int miluFrame;
    private int miluHold;
    private boolean blinking;
    private int blinkStep;
    private int blinkHold;
    private int ticksUntilBlink;

    public DesktopPetAnimation() {
        this(new Random());
    }

    DesktopPetAnimation(Random random) {
        this.random = random;
        resetBlinkDelay();
        updateFrame();
    }

    public void tick() {
        if (bubbleTicks > 0 && --bubbleTicks == 0) {
            bubble = null;
        }
        updateMilu();
        if (carried) {
            updateFrame();
            return;
        }
        poseTicks++;
        if (mode == Mode.REACT && poseTicks >= REACT_TICKS) {
            enter(Mode.IDLE);
        } else if (mode == Mode.IDLE && poseTicks >= IDLE_TICKS) {
            enter(Mode.WALK);
        } else if (mode == Mode.WALK && poseTicks >= WALK_TICKS) {
            enter(Mode.IDLE);
        }
        updateFrame();
    }

    /** 點一下：停下、揮手，並顯示一句話。被拖著時忽略。 */
    public void poke() {
        if (carried) {
            return;
        }
        bubble = PHRASES[pokes % PHRASES.length];
        pokes++;
        bubbleTicks = BUBBLE_TICKS;
        enter(Mode.REACT);
        updateFrame();
    }

    public void setCarried(boolean carried) {
        this.carried = carried;
        updateFrame();
    }

    public void turnAround() {
        direction = -direction;
    }

    /** 這一格要往右（負值往左）移動的像素。站住或被拖著時為 0。 */
    public int stepX() {
        if (carried || mode != Mode.WALK) {
            return 0;
        }
        return direction * STEP;
    }

    public Mode mode() {
        return mode;
    }

    public boolean isCarried() {
        return carried;
    }

    public boolean facingRight() {
        return direction > 0;
    }

    public int column() {
        return frameColumn;
    }

    public int row() {
        return frameRow;
    }

    public int bob() {
        return bob;
    }

    public String bubble() {
        return bubble;
    }

    /** 0–3 對應 breath_01 … breath_04。眨眼時仍停在進入眨眼前的呼吸格。 */
    public int miluBreathIndex() {
        return miluFrame;
    }

    public boolean miluBlinking() {
        return blinking && !carried;
    }

    /** 0 blink_01、1 blink_02、2 blink_01、3 breath_01。 */
    public int miluBlinkStep() {
        return blinkStep;
    }

    int ticksUntilBlink() {
        return ticksUntilBlink;
    }

    /** 下一次 tick 從 blink_01 開始。只在呼吸停在第一張時使用。 */
    void armBlinkNow() {
        miluFrame = 0;
        miluHold = 0;
        ticksUntilBlink = 0;
        blinking = false;
    }

    void forceWalk(int direction) {
        this.direction = direction >= 0 ? 1 : -1;
        enter(Mode.WALK);
        updateFrame();
    }

    private void enter(Mode next) {
        mode = next;
        poseTicks = 0;
    }

    private void updateFrame() {
        if (carried) {
            frameColumn = 3;
            frameRow = 0;
            bob = -6;
            return;
        }
        if (mode == Mode.REACT) {
            int phase = poseTicks % 16;
            frameRow = 1;
            bob = -2;
            if (phase < 4) {
                frameColumn = 0;
            } else if (phase < 8) {
                frameColumn = 1;
            } else if (phase < 12) {
                frameColumn = 2;
            } else {
                frameColumn = 1;
            }
            return;
        }
        if (mode == Mode.WALK) {
            bob = (poseTicks / 4) % 2 == 0 ? 0 : -4;
        } else {
            bob = (poseTicks / 16) % 2 == 0 ? 0 : -1;
        }
        int blink = poseTicks % 48;
        frameRow = 0;
        if (blink == 20 || blink == 23) {
            frameColumn = 1;
        } else if (blink == 21 || blink == 22) {
            frameColumn = 2;
        } else {
            frameColumn = 0;
        }
    }

    private void updateMilu() {
        if (carried) {
            blinking = false;
            blinkStep = 0;
            blinkHold = 0;
            return;
        }
        if (blinking) {
            advanceBlink();
            return;
        }
        if (readyToBlink()) {
            beginBlink();
            return;
        }
        if (ticksUntilBlink > 0) {
            ticksUntilBlink--;
        }
        miluHold++;
        if (miluHold >= BREATH_HOLD_TICKS) {
            miluHold = 0;
            miluFrame = (miluFrame + 1) % 4;
        }
        if (readyToBlink()) {
            beginBlink();
        }
    }

    private boolean readyToBlink() {
        return ticksUntilBlink == 0 && miluFrame == 0 && miluHold == 0;
    }

    private void beginBlink() {
        blinking = true;
        blinkStep = 0;
        blinkHold = 0;
    }

    private void advanceBlink() {
        blinkHold++;
        if (blinkHold < BLINK_HOLD_TICKS) {
            return;
        }
        blinkHold = 0;
        blinkStep++;
        if (blinkStep < 4) {
            return;
        }
        blinking = false;
        blinkStep = 0;
        miluFrame = 0;
        miluHold = 0;
        resetBlinkDelay();
    }

    private void resetBlinkDelay() {
        int span = BLINK_MAX_TICKS - BLINK_MIN_TICKS + 1;
        ticksUntilBlink = BLINK_MIN_TICKS + random.nextInt(span);
    }
}
