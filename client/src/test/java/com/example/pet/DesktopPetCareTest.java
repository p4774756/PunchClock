package com.example.pet;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DesktopPetCareTest {

    @Test
    public void defaultsAndClampFloor() {
        AtomicLong now = new AtomicLong(1_000_000L);
        DesktopPetCare care = new DesktopPetCare(now::get);
        DesktopPetCare.Snapshot snap = care.snapshot();
        assertEquals(DesktopPetCare.DEFAULT, snap.hunger);
        assertEquals(DesktopPetCare.DEFAULT, snap.mood);
        assertEquals(DesktopPetCare.DEFAULT, snap.energy);
        assertEquals(DesktopPetCare.MIN, DesktopPetCare.clamp(0));
        assertEquals(DesktopPetCare.MAX, DesktopPetCare.clamp(200));
        assertEquals(42, DesktopPetCare.clamp(42));
    }

    @Test
    public void decaysByElapsedHoursIncludingOfflineGap() {
        AtomicLong now = new AtomicLong(0L);
        DesktopPetCare care = new DesktopPetCare(now::get);
        now.set(3_600_000L);
        DesktopPetCare.Snapshot after = care.tick(now.get());
        assertEquals(DesktopPetCare.DEFAULT + DesktopPetCare.HUNGER_PER_HOUR, after.hunger);
        assertEquals(DesktopPetCare.DEFAULT + DesktopPetCare.MOOD_PER_HOUR, after.mood);
        assertEquals(DesktopPetCare.DEFAULT + DesktopPetCare.ENERGY_PER_HOUR, after.energy);

        now.set(0L);
        care.restore(new DesktopPetCare.Snapshot(
                DesktopPetCare.DEFAULT, DesktopPetCare.DEFAULT, DesktopPetCare.DEFAULT,
                0L, 0L, 0L, 0L, 0L, 0L, true, false));
        now.set(10L * 3_600_000L);
        after = care.tick(now.get());
        assertEquals(DesktopPetCare.DEFAULT + 10 * DesktopPetCare.HUNGER_PER_HOUR, after.hunger);
        assertTrue(after.hunger >= DesktopPetCare.MIN);
    }

    @Test
    public void manySmallTicksMatchOneHourTick() {
        AtomicLong now = new AtomicLong(0L);
        DesktopPetCare stepwise = new DesktopPetCare(now::get);
        long step = 50L;
        long hour = 3_600_000L;
        for (long t = step; t <= hour; t += step) {
            now.set(t);
            stepwise.tick(t);
        }
        assertEquals(65, stepwise.snapshot().hunger);
        assertEquals(67, stepwise.snapshot().mood);
        assertEquals(66, stepwise.snapshot().energy);

        now.set(0L);
        DesktopPetCare once = new DesktopPetCare(now::get);
        now.set(hour);
        once.tick(hour);
        assertEquals(stepwise.snapshot().hunger, once.snapshot().hunger);
        assertEquals(stepwise.snapshot().mood, once.snapshot().mood);
        assertEquals(stepwise.snapshot().energy, once.snapshot().energy);
    }

    @Test
    public void clockRollbackDoesNotChangeValuesOrSavedAt() {
        AtomicLong now = new AtomicLong(0L);
        DesktopPetCare care = new DesktopPetCare(now::get);
        now.set(3_600_000L);
        DesktopPetCare.Snapshot after = care.tick(now.get());
        assertEquals(65, after.hunger);
        assertEquals(3_600_000L, after.savedAt);

        now.set(1_000_000L);
        DesktopPetCare.Snapshot rolled = care.tick(now.get());
        assertEquals(65, rolled.hunger);
        assertEquals(67, rolled.mood);
        assertEquals(66, rolled.energy);
        assertEquals(3_600_000L, rolled.savedAt);
    }

    @Test
    public void decayStopsAtFloor() {
        AtomicLong now = new AtomicLong(0L);
        DesktopPetCare care = new DesktopPetCare(now::get);
        care.restore(new DesktopPetCare.Snapshot(
                DesktopPetCare.MIN + 1, DesktopPetCare.MIN + 1, DesktopPetCare.MIN + 1,
                0L, 0L, 0L, 0L, 0L, 0L, true, false));
        now.set(100L * 3_600_000L);
        DesktopPetCare.Snapshot after = care.tick(now.get());
        assertEquals(DesktopPetCare.MIN, after.hunger);
        assertEquals(DesktopPetCare.MIN, after.mood);
        assertEquals(DesktopPetCare.MIN, after.energy);
    }

    @Test
    public void feedPatPlayEffectsAndCooldowns() {
        AtomicLong now = new AtomicLong(1_000L);
        DesktopPetCare care = new DesktopPetCare(now::get);
        DesktopPetCare.Result feed = care.perform(DesktopPetCare.Action.FEED, now.get());
        assertTrue(feed.ok);
        assertEquals("好好吃～", feed.bubble);
        assertEquals(DesktopPetCare.DEFAULT + 25, feed.state.hunger);
        assertEquals(DesktopPetCare.DEFAULT + 5, feed.state.mood);

        DesktopPetCare.Result again = care.perform(DesktopPetCare.Action.FEED, now.get() + 1_000L);
        assertFalse(again.ok);
        assertEquals("還要等一下", again.bubble);
        assertTrue(again.remainingCooldownMs > 0L);

        now.set(1_000L + DesktopPetCare.FEED_COOLDOWN_MS);
        assertTrue(care.perform(DesktopPetCare.Action.FEED, now.get()).ok);

        now.set(now.get() + 1);
        DesktopPetCare.Result pat = care.perform(DesktopPetCare.Action.PAT, now.get());
        assertTrue(pat.ok);
        assertEquals("好舒服～", pat.bubble);
        int moodAfterPat = pat.state.mood;
        int energyAfterPat = pat.state.energy;

        now.set(now.get() + 1);
        int hungerBeforePlay = care.snapshot().hunger;
        DesktopPetCare.Result play = care.perform(DesktopPetCare.Action.PLAY, now.get());
        assertTrue(play.ok);
        assertEquals("玩一玩！", play.bubble);
        assertEquals(DesktopPetCare.clamp(hungerBeforePlay - 5), play.state.hunger);
        assertEquals(DesktopPetCare.clamp(moodAfterPat + 15), play.state.mood);
        assertEquals(DesktopPetCare.clamp(energyAfterPat + 20), play.state.energy);

        assertFalse(care.perform(DesktopPetCare.Action.PLAY, now.get() + 1_000L).ok);
        now.set(now.get() + DesktopPetCare.PLAY_COOLDOWN_MS);
        assertTrue(care.perform(DesktopPetCare.Action.PLAY, now.get()).ok);
    }

    @Test
    public void taskAndCursorMoodHaveCooldowns() {
        AtomicLong now = new AtomicLong(5_000L);
        DesktopPetCare care = new DesktopPetCare(now::get);
        DesktopPetCare.Result task = care.onTaskCompleted(now.get());
        assertTrue(task.ok);
        assertEquals(DesktopPetCare.DEFAULT + 10, task.state.mood);
        assertFalse(care.onTaskCompleted(now.get() + 1_000L).ok);
        now.set(5_000L + DesktopPetCare.TASK_COOLDOWN_MS);
        int moodBeforeSecond = care.tick(now.get()).mood;
        assertTrue(care.onTaskCompleted(now.get()).ok);
        assertEquals(DesktopPetCare.clamp(moodBeforeSecond + 10), care.snapshot().mood);

        int moodBeforeCursor = care.snapshot().mood;
        DesktopPetCare.Result cursor = care.onCursorCelebrate(now.get());
        assertTrue(cursor.ok);
        assertEquals(DesktopPetCare.clamp(moodBeforeCursor + 8), cursor.state.mood);
        assertFalse(care.onCursorCelebrate(now.get() + 1_000L).ok);
        now.set(now.get() + DesktopPetCare.CURSOR_COOLDOWN_MS);
        assertTrue(care.onCursorCelebrate(now.get()).ok);
    }

    @Test
    public void disableFreezesWithoutDecayingGap() {
        AtomicLong now = new AtomicLong(0L);
        DesktopPetCare care = new DesktopPetCare(now::get);
        now.set(3_600_000L);
        care.tick(now.get());
        int hunger = care.snapshot().hunger;
        care.setEnabled(false, now.get());
        assertFalse(care.isEnabled());
        now.set(now.get() + 20L * 3_600_000L);
        DesktopPetCare.Snapshot frozen = care.tick(now.get());
        assertEquals(hunger, frozen.hunger);
        care.setEnabled(true, now.get());
        assertTrue(care.isEnabled());
        assertEquals(hunger, care.snapshot().hunger);
        now.set(now.get() + 3_600_000L);
        assertEquals(hunger + DesktopPetCare.HUNGER_PER_HOUR, care.tick(now.get()).hunger);
    }

    @Test
    public void preferencesRoundTripAndBadSchemaResets() {
        DesktopPetTest.MemoryPreferences prefs = new DesktopPetTest.MemoryPreferences();
        DesktopPetSettings settings = new DesktopPetSettings(prefs);
        assertTrue(settings.careEnabled());

        AtomicLong now = new AtomicLong(9_000L);
        DesktopPetCare care = new DesktopPetCare(now::get);
        care.perform(DesktopPetCare.Action.FEED, now.get());
        care.onTaskCompleted(now.get());
        settings.saveCare(care.snapshot());

        DesktopPetCare again = new DesktopPetCare(now::get);
        again.restore(settings.loadCare());
        assertEquals(care.snapshot().hunger, again.snapshot().hunger);
        assertEquals(care.snapshot().mood, again.snapshot().mood);
        assertEquals(care.snapshot().lastFeedAt, again.snapshot().lastFeedAt);
        assertEquals(care.snapshot().lastTaskAt, again.snapshot().lastTaskAt);

        prefs.putInt(DesktopPetSettings.KEY_CARE_SCHEMA, 99);
        DesktopPetCare.Snapshot reset = settings.loadCare();
        assertEquals(DesktopPetCare.DEFAULT, reset.hunger);
        assertEquals(DesktopPetCare.DEFAULT, reset.mood);
        assertEquals(DesktopPetCare.DEFAULT, reset.energy);
        assertTrue(reset.enabled);
    }

    @Test
    public void statusLineIsTraditionalChinese() {
        DesktopPetCare care = new DesktopPetCare(() -> 0L);
        assertEquals("飽足 70／心情 70／精神 70", care.statusLine());
    }
}
