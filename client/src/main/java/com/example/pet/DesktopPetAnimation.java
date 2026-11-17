package com.example.pet;

import java.util.Random;

/**
 * 桌面寵物的動作。不含視窗與計時器，由視窗在 EDT 上逐格呼叫 {@link #tick()}。
 * <p>
 * 喵露待機：呼吸每張 300ms，眨眼每張 150ms。
 * 動作優先序是拖曳、養成動作（餵食／摸頭／玩耍）、揮手、Cursor 狀態、走路、坐下、趴睡、待機，全部由這一個 tick 推進。
 * 拖曳另有 {@link CarryPhase}：抓起、掙扎、放下，不混進一般動作的 {@link Mode}。
 * Cursor 的思考／忙碌／困擾蓋在一般閒置行為上，被拎著時不打斷，放開或揮手結束後再回到對應狀態。
 * 養成開啟時低數值會偏向趴睡／困擾／坐下。還沒有合格影格的動作由畫面改畫待機。
 * {@link #column()} 與 {@link #row()} 只給待機素材也載入失敗時的像素角色。
 */
public final class DesktopPetAnimation {

    public enum Mode {
        IDLE, WALK, REACT, SIT, SLEEP, THINK, BUSY, WORRY, FEED, PAT, PLAY
    }

    /** 被拎著的階段。放下回彈期間仍算被拎著，畫面依階段選圖。 */
    public enum CarryPhase {
        NONE, GRAB, STRUGGLE, RELEASE
    }

    public static final int TICK_MS = 50;
    public static final int IDLE_TICKS = 70;
    public static final int WALK_TICKS = 110;
    public static final int REACT_TICKS = 28;
    /** 餵食／摸頭／玩耍約與揮手同長。 */
    public static final int CARE_TICKS = 28;
    /** 短暫困擾約與揮手同長，結束後回到 Cursor 維持狀態。 */
    public static final int WORRY_TICKS = 28;
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
    /** 抓起約 200ms，兩張各 100ms，之後進入掙扎。 */
    public static final int GRAB_TICKS = 4;
    /** 放開後落地回彈約 300ms，三張各 100ms，結束回待機。 */
    public static final int RELEASE_TICKS = 6;
    public static final double MAX_LEAN_DEGREES = 10;
    /** 大約每秒 700 像素就傾到上限。 */
    static final double SPEED_FOR_FULL_LEAN = 700;
    static final double SPEED_BLEND = 0.45;
    static final double SPEED_DECAY = 0.62;
    static final double LEAN_EASE = 0.5;

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
    private CarryPhase carryPhase = CarryPhase.NONE;
    private int phaseTicks;
    private int idleExits;
    private boolean handDown;
    private boolean settling;
    private int settleTicks;
    private double settleFrom;
    private double lean;
    private double smoothedSpeed;
    private boolean speedFresh;
    /** Cursor 進行中要維持的狀態（THINK／BUSY）；null 表示沒有。 */
    private Mode cursorHold;
    private boolean careIdle;
    private int careHunger = DesktopPetCare.DEFAULT;
    private int careMood = DesktopPetCare.DEFAULT;
    private int careEnergy = DesktopPetCare.DEFAULT;

    public DesktopPetAnimation() {
        this(new Random());
    }

    DesktopPetAnimation(Random random) {
        this.random = random;
        resetBlinkDelay();
        updateFrame();
    }

    /** 養成開啟時用數值偏向閒置；關閉時清掉偏向。 */
    public void setCareIdle(boolean enabled, int hunger, int mood, int energy) {
        careIdle = enabled;
        careHunger = hunger;
        careMood = mood;
        careEnergy = energy;
    }

    public void tick() {
        if (bubbleTicks > 0 && --bubbleTicks == 0) {
            bubble = null;
            notice = false;
        }
        if (settling) {
            settleTicks++;
            double progress = settleTicks / (double) RELEASE_TICKS;
            if (progress >= 1) {
                lean = 0;
                settling = false;
                setCarried(false);
                return;
            }
            lean = releaseLean(settleFrom, progress);
            updateFrame();
            return;
        }
        updateMilu();
        if (carried) {
            carriedTicks++;
            phaseTicks++;
            if (carryPhase == CarryPhase.GRAB && phaseTicks >= GRAB_TICKS) {
                carryPhase = CarryPhase.STRUGGLE;
                phaseTicks = 0;
            }
            if (!speedFresh) {
                smoothedSpeed *= SPEED_DECAY;
                leanToward(targetLean(smoothedSpeed));
            }
            speedFresh = false;
            updateFrame();
            return;
        }
        poseTicks++;
        if (isCarePose() && poseTicks >= CARE_TICKS) {
            resumeAfterInterrupt();
        } else if (mode == Mode.REACT && poseTicks >= REACT_TICKS) {
            resumeAfterInterrupt();
        } else if (mode == Mode.WORRY && poseTicks >= WORRY_TICKS) {
            resumeAfterInterrupt();
        } else if (mode == Mode.IDLE && poseTicks >= IDLE_TICKS) {
            Mode next = nextAfterIdle();
            idleExits++;
            enter(next);
        } else if (mode == Mode.SIT && poseTicks >= SIT_TICKS) {
            enter(shouldSleep() ? Mode.SLEEP : Mode.IDLE);
        } else if (mode == Mode.SLEEP && poseTicks >= SLEEP_TICKS) {
            enter(Mode.IDLE);
        } else if (mode == Mode.WALK && poseTicks >= WALK_TICKS) {
            enter(Mode.IDLE);
        }
        updateFrame();
    }

    /**
     * 設定或清除 Cursor 維持狀態。被拖曳時只記住，放開後再套用。
     * {@code hold} 僅接受 {@link Mode#THINK}、{@link Mode#BUSY} 或 null。
     */
    public void setCursorHold(Mode hold) {
        if (hold != null && hold != Mode.THINK && hold != Mode.BUSY) {
            hold = Mode.BUSY;
        }
        cursorHold = hold;
        if (carried || settling) {
            return;
        }
        if (mode == Mode.REACT || mode == Mode.WORRY || isCarePose()) {
            return;
        }
        applyCursorHoldNow();
    }

    public Mode cursorHold() {
        return cursorHold;
    }

    /** 短暫困擾；被拖著時忽略。結束後回到 {@link #cursorHold()}。 */
    public boolean flashWorry(String phrase) {
        if (carried || settling) {
            return false;
        }
        if (phrase != null && !phrase.isEmpty()) {
            notice = false;
            bubble = phrase;
            bubbleTicks = BUBBLE_TICKS;
        }
        enter(Mode.WORRY);
        updateFrame();
        return true;
    }

    /** 揮手慶祝；被拖著時忽略。結束後回到 {@link #cursorHold()} 或待機。 */
    public boolean celebrate(String phrase) {
        if (carried || settling) {
            return false;
        }
        notice = false;
        if (phrase != null && !phrase.isEmpty()) {
            bubble = phrase;
            bubbleTicks = NOTICE_TICKS;
        }
        enter(Mode.REACT);
        updateFrame();
        return true;
    }

    /**
     * 養成動作：餵食／摸頭／玩耍。被拖著時忽略。
     * {@code mode} 僅接受 {@link Mode#FEED}、{@link Mode#PAT}、{@link Mode#PLAY}。
     */
    public boolean playCare(Mode mode, String phrase) {
        if (carried || settling) {
            return false;
        }
        if (mode != Mode.FEED && mode != Mode.PAT && mode != Mode.PLAY) {
            mode = Mode.PAT;
        }
        notice = false;
        if (phrase != null && !phrase.isEmpty()) {
            bubble = phrase;
            bubbleTicks = BUBBLE_TICKS;
        }
        enter(mode);
        updateFrame();
        return true;
    }

    public boolean isCarePose() {
        return mode == Mode.FEED || mode == Mode.PAT || mode == Mode.PLAY;
    }

    public void clearCursorHold() {
        cursorHold = null;
        if (carried || settling) {
            return;
        }
        if (mode == Mode.THINK || mode == Mode.BUSY || mode == Mode.WORRY) {
            enter(Mode.IDLE);
            updateFrame();
        }
    }

    /** 固定短句泡泡，不改動作。提醒泡泡顯示中時不覆蓋。 */
    public void showCursorPhrase(String phrase) {
        if (carried || phrase == null || phrase.isEmpty() || notice) {
            return;
        }
        bubble = phrase;
        bubbleTicks = BUBBLE_TICKS;
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

    /** 拎起時從抓起重新開始；放下回彈還沒結束就再被拎起，舊的回彈直接中止。 */
    public void setCarried(boolean carried) {
        if (carried && (!this.carried || settling)) {
            carriedTicks = 0;
            lean = 0;
            smoothedSpeed = 0;
            speedFresh = false;
            settling = false;
            settleTicks = 0;
            carryPhase = CarryPhase.GRAB;
            phaseTicks = 0;
        }
        this.carried = carried;
        if (!carried) {
            carryPhase = CarryPhase.NONE;
            phaseTicks = 0;
            settling = false;
            settleTicks = 0;
            lean = 0;
            smoothedSpeed = 0;
            speedFresh = false;
            blinking = false;
            blinkStep = 0;
            blinkHold = 0;
            resumeAfterInterrupt();
        }
        updateFrame();
    }

    /** 按下但還沒超過拖曳門檻。停住走路，姿勢先留著。 */
    public void setHandDown(boolean down) {
        handDown = down;
    }

    /**
     * 拖曳中的水平位移。正值往右，圖像往同方向傾，角度不超過 ±10°。
     * 第一筆在視窗端略過，避免門檻那一下被當成速度。
     */
    public void noteCarryMotion(int deltaX, long deltaNanos) {
        if (!carried || settling || deltaNanos <= 0) {
            return;
        }
        double perSecond = deltaX / (deltaNanos / 1_000_000_000.0);
        smoothedSpeed = smoothedSpeed * (1 - SPEED_BLEND) + perSecond * SPEED_BLEND;
        speedFresh = true;
        leanToward(targetLean(smoothedSpeed));
    }

    /** 放開。抓起還沒播完也直接落地，約 300ms 回彈後回待機。 */
    public void releaseCarry() {
        if (!carried || settling) {
            return;
        }
        settling = true;
        settleTicks = 0;
        carryPhase = CarryPhase.RELEASE;
        phaseTicks = 0;
        settleFrom = lean;
        smoothedSpeed = 0;
        speedFresh = false;
    }

    public boolean isSettling() {
        return settling;
    }

    /** 順時針為正。往右拖時為正，身體跟著移動方向倒。 */
    public double leanDegrees() {
        return lean;
    }

    static double targetLean(double pixelsPerSecond) {
        double degrees = pixelsPerSecond / SPEED_FOR_FULL_LEAN * MAX_LEAN_DEGREES;
        return Math.max(-MAX_LEAN_DEGREES, Math.min(MAX_LEAN_DEGREES, degrees));
    }

    static double releaseLean(double startDegrees, double progress) {
        double u = Math.max(0, Math.min(1, progress));
        double overshoot = -0.4 * startDegrees;
        if (u < 0.5) {
            double t = u / 0.5;
            double s = t * t * (3 - 2 * t);
            return startDegrees + (overshoot - startDegrees) * s;
        }
        double t = (u - 0.5) / 0.5;
        double s = t * t * (3 - 2 * t);
        return overshoot * (1 - s);
    }

    public void turnAround() {
        direction = -direction;
    }

    /** 這一格要往右（負值往左）移動的像素。站住或被拖著時為 0。 */
    public int stepX() {
        if (carried || handDown || mode != Mode.WALK) {
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

    public CarryPhase carryPhase() {
        return carryPhase;
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

    /** 走路、揮手、思考、忙碌、困擾、拖曳共用的影格序號。每張約 150ms。 */
    public int actionFrame(int count) {
        if (count <= 1) {
            return 0;
        }
        int ticks = carried ? carriedTicks : poseTicks;
        return Math.floorMod(ticks / ACTION_HOLD_TICKS, count);
    }

    public boolean isCursorPose() {
        return mode == Mode.THINK || mode == Mode.BUSY || mode == Mode.WORRY;
    }

    /**
     * 拖曳階段的影格序號。抓起與放下各播一次、停在最後一張；掙扎每張約 150ms 循環。
     * 不在拖曳階段時回傳 0。
     */
    public int carryFrame(int count) {
        if (count <= 1) {
            return 0;
        }
        if (carryPhase == CarryPhase.GRAB) {
            return Math.min(count - 1, phaseTicks * count / GRAB_TICKS);
        }
        if (carryPhase == CarryPhase.RELEASE) {
            return Math.min(count - 1, settleTicks * count / RELEASE_TICKS);
        }
        if (carryPhase == CarryPhase.STRUGGLE) {
            return Math.floorMod(phaseTicks / ACTION_HOLD_TICKS, count);
        }
        return 0;
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

    private void resumeAfterInterrupt() {
        applyCursorHoldNow();
    }

    private void applyCursorHoldNow() {
        if (cursorHold == Mode.THINK || cursorHold == Mode.BUSY) {
            enter(cursorHold);
        } else {
            enter(Mode.IDLE);
        }
        updateFrame();
    }

    private void updateFrame() {
        if (carried) {
            frameColumn = 3;
            frameRow = 0;
            bob = -6;
            return;
        }
        if (mode == Mode.REACT || mode == Mode.WORRY || isCarePose()) {
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
        if (mode == Mode.THINK || mode == Mode.BUSY) {
            bob = (poseTicks / 8) % 2 == 0 ? 0 : -1;
            frameRow = 0;
            frameColumn = 0;
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

    private Mode nextAfterIdle() {
        if (careIdle && careEnergy < 25 && random.nextInt(3) == 0) {
            return Mode.SLEEP;
        }
        if (careIdle && (careHunger < 25 || careMood < 25)) {
            if (random.nextInt(3) == 0) {
                return Mode.WORRY;
            }
            return Mode.SIT;
        }
        return shouldSit() ? Mode.SIT : Mode.WALK;
    }

    private boolean shouldSit() {
        if (careIdle && (careHunger < 25 || careMood < 25)) {
            return random.nextInt(4) != 0;
        }
        if (careIdle && careEnergy < 25) {
            return random.nextInt(3) != 0;
        }
        return idleExits >= 1 && random.nextInt(2) == 0;
    }

    private boolean shouldSleep() {
        if (careIdle && careEnergy < 25) {
            return random.nextInt(4) != 0;
        }
        if (careIdle && (careHunger < 25 || careMood < 25)) {
            return random.nextInt(4) == 0;
        }
        return random.nextInt(2) == 0;
    }

    private void updateMilu() {
        if (carried || mode == Mode.WALK || mode == Mode.REACT || isCarePose() || isCursorPose()) {
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

    private void leanToward(double target) {
        lean += (target - lean) * LEAN_EASE;
        if (Math.abs(lean) < 0.05 && Math.abs(target) < 0.05) {
            lean = 0;
        }
    }

    private void resetBlinkDelay() {
        int span = BLINK_MAX_TICKS - BLINK_MIN_TICKS + 1;
        ticksUntilBlink = BLINK_MIN_TICKS + random.nextInt(span);
    }
}
