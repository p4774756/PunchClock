package com.example.pet;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 喵露養成：飽足／心情／精神。純邏輯，可注入時鐘。
 * <p>
 * 動作精靈：{@code /pet/milu/feed/}、{@code /pet/milu/pat/}、{@code /pet/milu/play/}（01–04.png）。
 * 載入失敗時才退回 sit／wave／walk，不要為此畫新圖。
 */
public final class DesktopPetCare {

    public static final int SCHEMA = 1;
    public static final int MIN = 5;
    public static final int MAX = 100;
    public static final int DEFAULT = 70;

    public static final int HUNGER_PER_HOUR = -5;
    public static final int MOOD_PER_HOUR = -3;
    public static final int ENERGY_PER_HOUR = -4;

    public static final long FEED_COOLDOWN_MS = 3L * 60L * 1000L;
    public static final long PAT_COOLDOWN_MS = 1L * 60L * 1000L;
    public static final long PLAY_COOLDOWN_MS = 5L * 60L * 1000L;
    public static final long TASK_COOLDOWN_MS = 30L * 60L * 1000L;
    public static final long CURSOR_COOLDOWN_MS = 10L * 60L * 1000L;

    public enum Action {
        FEED, PAT, PLAY
    }

    public static final class Snapshot {
        public final int hunger;
        public final int mood;
        public final int energy;
        public final long savedAt;
        public final long lastFeedAt;
        public final long lastPatAt;
        public final long lastPlayAt;
        public final long lastTaskAt;
        public final long lastCursorAt;
        public final boolean enabled;
        public final boolean frozen;

        public Snapshot(int hunger, int mood, int energy, long savedAt,
                        long lastFeedAt, long lastPatAt, long lastPlayAt,
                        long lastTaskAt, long lastCursorAt,
                        boolean enabled, boolean frozen) {
            this.hunger = hunger;
            this.mood = mood;
            this.energy = energy;
            this.savedAt = savedAt;
            this.lastFeedAt = lastFeedAt;
            this.lastPatAt = lastPatAt;
            this.lastPlayAt = lastPlayAt;
            this.lastTaskAt = lastTaskAt;
            this.lastCursorAt = lastCursorAt;
            this.enabled = enabled;
            this.frozen = frozen;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String bubble;
        public final long remainingCooldownMs;
        public final Snapshot state;

        Result(boolean ok, String bubble, long remainingCooldownMs, Snapshot state) {
            this.ok = ok;
            this.bubble = bubble;
            this.remainingCooldownMs = remainingCooldownMs;
            this.state = state;
        }
    }

    private final LongSupplier clock;
    private int hunger = DEFAULT;
    private int mood = DEFAULT;
    private int energy = DEFAULT;
    private long savedAt;
    private long lastFeedAt;
    private long lastPatAt;
    private long lastPlayAt;
    private long lastTaskAt;
    private long lastCursorAt;
    private boolean enabled = true;
    private boolean frozen;
    /** 各項尚未湊成 1 點的毫秒，避免 50ms tick 把零頭丟掉。 */
    private long hungerCarryMs;
    private long moodCarryMs;
    private long energyCarryMs;

    public DesktopPetCare() {
        this(System::currentTimeMillis);
    }

    public DesktopPetCare(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.savedAt = this.clock.getAsLong();
    }

    public Snapshot snapshot() {
        return new Snapshot(hunger, mood, energy, savedAt,
                lastFeedAt, lastPatAt, lastPlayAt, lastTaskAt, lastCursorAt,
                enabled, frozen);
    }

    public void restore(Snapshot snap) {
        if (snap == null) {
            resetDefaults(clock.getAsLong());
            return;
        }
        hunger = clamp(snap.hunger);
        mood = clamp(snap.mood);
        energy = clamp(snap.energy);
        savedAt = Math.max(0L, snap.savedAt);
        lastFeedAt = Math.max(0L, snap.lastFeedAt);
        lastPatAt = Math.max(0L, snap.lastPatAt);
        lastPlayAt = Math.max(0L, snap.lastPlayAt);
        lastTaskAt = Math.max(0L, snap.lastTaskAt);
        lastCursorAt = Math.max(0L, snap.lastCursorAt);
        enabled = snap.enabled;
        frozen = snap.frozen;
        clearCarry();
        if (savedAt <= 0L) {
            savedAt = Math.max(0L, clock.getAsLong());
        }
    }

    public void resetDefaults(long now) {
        hunger = DEFAULT;
        mood = DEFAULT;
        energy = DEFAULT;
        savedAt = now;
        lastFeedAt = 0L;
        lastPatAt = 0L;
        lastPlayAt = 0L;
        lastTaskAt = 0L;
        lastCursorAt = 0L;
        enabled = true;
        frozen = false;
        clearCarry();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 養成關閉：更新 savedAt、凍結，關閉期間不補扣。 */
    public void setEnabled(boolean on, long now) {
        long t = now > 0 ? now : clock.getAsLong();
        if (on) {
            if (!enabled) {
                enabled = true;
                frozen = false;
                rebase(t);
            }
            return;
        }
        if (enabled && !frozen) {
            tick(t);
        }
        enabled = false;
        frozen = true;
        rebase(t);
    }

    public Snapshot tick() {
        return tick(clock.getAsLong());
    }

    /**
     * 依真實時間衰減。凍結或關閉時不扣。
     * 各項保留未湊滿 1 點的毫秒餘數，小間隔（例如動畫 50ms）反覆 tick 也會累積。
     * 時鐘回撥（{@code now <= savedAt}）不改數值、也不把 {@code savedAt} 往回寫。
     */
    public Snapshot tick(long now) {
        long t = now > 0 ? now : clock.getAsLong();
        if (!enabled || frozen) {
            return snapshot();
        }
        if (t <= savedAt) {
            return snapshot();
        }
        long elapsed = t - savedAt;
        hungerCarryMs += elapsed;
        moodCarryMs += elapsed;
        energyCarryMs += elapsed;
        hunger = consume(hunger, HUNGER_PER_HOUR, hungerCarryMs);
        hungerCarryMs = leftover(hungerCarryMs, HUNGER_PER_HOUR);
        mood = consume(mood, MOOD_PER_HOUR, moodCarryMs);
        moodCarryMs = leftover(moodCarryMs, MOOD_PER_HOUR);
        energy = consume(energy, ENERGY_PER_HOUR, energyCarryMs);
        energyCarryMs = leftover(energyCarryMs, ENERGY_PER_HOUR);
        savedAt = t;
        return snapshot();
    }

    public Result perform(Action action) {
        return perform(action, clock.getAsLong());
    }

    public Result perform(Action action, long now) {
        long t = now > 0 ? now : clock.getAsLong();
        tick(t);
        if (!enabled || action == null) {
            return new Result(false, null, 0L, snapshot());
        }
        long remaining = cooldownRemaining(action, t);
        if (remaining > 0L) {
            return new Result(false, "還要等一下", remaining, snapshot());
        }
        switch (action) {
            case FEED:
                hunger = clamp(hunger + 25);
                mood = clamp(mood + 5);
                lastFeedAt = t;
                rebase(t);
                return new Result(true, "好好吃～", 0L, snapshot());
            case PAT:
                mood = clamp(mood + 15);
                energy = clamp(energy + 5);
                lastPatAt = t;
                rebase(t);
                return new Result(true, "好舒服～", 0L, snapshot());
            case PLAY:
                mood = clamp(mood + 15);
                energy = clamp(energy + 20);
                hunger = clamp(hunger - 5);
                lastPlayAt = t;
                rebase(t);
                return new Result(true, "玩一玩！", 0L, snapshot());
            default:
                return new Result(false, null, 0L, snapshot());
        }
    }

    public Result onTaskCompleted() {
        return onTaskCompleted(clock.getAsLong());
    }

    public Result onTaskCompleted(long now) {
        long t = now > 0 ? now : clock.getAsLong();
        tick(t);
        if (!enabled) {
            return new Result(false, null, 0L, snapshot());
        }
        long remaining = remaining(lastTaskAt, TASK_COOLDOWN_MS, t);
        if (remaining > 0L) {
            return new Result(false, null, remaining, snapshot());
        }
        mood = clamp(mood + 10);
        lastTaskAt = t;
        rebase(t);
        return new Result(true, null, 0L, snapshot());
    }

    public Result onCursorCelebrate() {
        return onCursorCelebrate(clock.getAsLong());
    }

    public Result onCursorCelebrate(long now) {
        long t = now > 0 ? now : clock.getAsLong();
        tick(t);
        if (!enabled) {
            return new Result(false, null, 0L, snapshot());
        }
        long remaining = remaining(lastCursorAt, CURSOR_COOLDOWN_MS, t);
        if (remaining > 0L) {
            return new Result(false, null, remaining, snapshot());
        }
        mood = clamp(mood + 8);
        lastCursorAt = t;
        rebase(t);
        return new Result(true, null, 0L, snapshot());
    }

    public long cooldownRemaining(Action action, long now) {
        if (action == null) {
            return 0L;
        }
        long t = now > 0 ? now : clock.getAsLong();
        switch (action) {
            case FEED:
                return remaining(lastFeedAt, FEED_COOLDOWN_MS, t);
            case PAT:
                return remaining(lastPatAt, PAT_COOLDOWN_MS, t);
            case PLAY:
                return remaining(lastPlayAt, PLAY_COOLDOWN_MS, t);
            default:
                return 0L;
        }
    }

    public String statusLine() {
        return "飽足 " + hunger + "／心情 " + mood + "／精神 " + energy;
    }

    public static int clamp(int value) {
        if (value < MIN) {
            return MIN;
        }
        if (value > MAX) {
            return MAX;
        }
        return value;
    }

    /** 加分或凍結後，以目前數值為新基準，清掉各項毫秒餘數。 */
    private void rebase(long t) {
        savedAt = t;
        clearCarry();
    }

    private void clearCarry() {
        hungerCarryMs = 0L;
        moodCarryMs = 0L;
        energyCarryMs = 0L;
    }

    private static int consume(int value, int ratePerHour, long carryMs) {
        if (ratePerHour == 0) {
            return value;
        }
        long msPerPoint = msPerPoint(ratePerHour);
        int points = (int) (carryMs / msPerPoint);
        if (points == 0) {
            return value;
        }
        int signed = ratePerHour < 0 ? -points : points;
        return clamp(value + signed);
    }

    private static long leftover(long carryMs, int ratePerHour) {
        if (ratePerHour == 0) {
            return carryMs;
        }
        long msPerPoint = msPerPoint(ratePerHour);
        return carryMs % msPerPoint;
    }

    private static long msPerPoint(int ratePerHour) {
        return 3_600_000L / Math.abs(ratePerHour);
    }

    private static long remaining(long lastAt, long cooldownMs, long now) {
        if (lastAt <= 0L) {
            return 0L;
        }
        long elapsed = now - lastAt;
        if (elapsed >= cooldownMs) {
            return 0L;
        }
        return cooldownMs - elapsed;
    }
}
