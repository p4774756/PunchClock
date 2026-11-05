package com.example.pet;

import java.util.Random;

/**
 * 桌面寵物的動作。不含視窗與計時器，由視窗在 EDT 上逐格呼叫 {@link #tick()}。
 * <p>
 * 喵露待機：呼吸每張 300ms，眨眼每張 150ms。
 * 動作優先序是拖曳、揮手、走路、坐下、趴睡、待機，全部由這一個 tick 推進。
 * 還沒有合格影格的動作由畫面改畫待機。{@link #column()} 與 {@link #row()} 只給待機素材也載入失敗時的像素角色。
 */
public final class DesktopPetAnimation {

    public enum Mode {
        IDLE, WALK, REACT, SIT, SLEEP
    }

    public static final int TICK_MS = 50;
    public static final int IDLE_TICKS = 70;
    public static final int WALK_TICKS = 110;
    public static final int REACT_TICKS = 28;
    public static final int BUBBLE_TICKS = 40;
    /** 事件提醒泡泡約 4 秒，落在 3～5 秒。 */
    public static final int NOTICE_TICKS = 80;
    public static final int STEP = 2;
    static final int BREATH_HOLD_TICKS = 300 / TICK_MS;
    static final int BLINK_HOLD_TICKS = 150 / TICK_MS;
    static final int ACTION_HOLD_TICKS = 150 / TICK_MS;
    static final int BLINK_MIN_TICKS = 3000 / TICK_MS;
    static final int BLINK_MAX_TICKS = 7000 / TICK_MS;
    public static final int SIT_TICKS = 160;
    public static final int SLEEP_TICKS = 240;

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
    private boolean notice;
    private final Random random;
    private int miluFrame;
    private int miluHold;
    private boolean blinking;
    private int blinkStep;
    private int blinkHold;
    private int ticksUntilBlink;
    private int carriedTicks;
    private int idleExits;

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
            notice = false;
        }
        updateMilu();
        if (carried) {
            carriedTicks++;
            updateFrame();
            return;
        }
        poseTicks++;
        if (mode == Mode.REACT && poseTicks >= REACT_TICKS) {
            enter(Mode.IDLE);
        } else if (mode == Mode.IDLE && poseTicks >= IDLE_TICKS) {
            boolean sit = shouldSit();
            idleExits++;
            enter(sit ? Mode.SIT : Mode.WALK);
        } else if (mode == Mode.SIT && poseTicks >= SIT_TICKS) {
            enter(shouldSleep() ? Mode.SLEEP : Mode.IDLE);
        } else if (mode == Mode.SLEEP && poseTicks >= SLEEP_TICKS) {
            enter(Mode.IDLE);
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
        notice = false;
        bubble = PHRASES[pokes % PHRASES.length];
        pokes++;
        bubbleTicks = BUBBLE_TICKS;
        enter(Mode.REACT);
        updateFrame();
    }

    /**
     * 事件提醒：沿用揮手，泡泡只用固定短句。被拖著時不打斷，回傳 false。
     */
    public boolean showNotice(String phrase) {
        if (carried || phrase == null || phrase.isEmpty()) {
            return false;
        }
        notice = true;
        bubble = phrase;
        bubbleTicks = NOTICE_TICKS;
        enter(Mode.REACT);
        updateFrame();
        return true;
    }

    /** 點在提醒泡泡上時收掉這句，不改成打招呼。 */
    public void dismissNotice() {
        if (!notice) {
            return;
        }
        notice = false;
        bubble = null;
        bubbleTicks = 0;
    }

    public boolean isNotice() {
        return notice && bubbleTicks > 0;
    }

    public void setCarried(boolean carried) {
        if (carried && !this.carried) {
            carriedTicks = 0;
        }
        this.carried = carried;
        if (!carried) {
            enter(Mode.IDLE);
            blinking = false;
            blinkStep = 0;
            blinkHold = 0;
        }
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

    /** 走路、揮手、拖曳共用的影格序號。每張約 150ms。 */
    public int actionFrame(int count) {
        if (count <= 1) {
            return 0;
        }
        int ticks = carried ? carriedTicks : poseTicks;
        return Math.floorMod(ticks / ACTION_HOLD_TICKS, count);
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

    private boolean shouldSit() {
        return idleExits >= 1 && random.nextInt(2) == 0;
    }

    private boolean shouldSleep() {
        return random.nextInt(2) == 0;
    }

    private void updateMilu() {
        if (carried || mode == Mode.WALK || mode == Mode.REACT) {
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
