package com.example.pet;

import org.junit.Test;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Issue682DesktopPetHungryTest {

    @Test
    public void hungryFramesPassCareClipAndEdgeStillBlocksLooseMode() throws Exception {
        for (int i = 1; i <= 4; i++) {
            String path = "/pet/milu/hungry/0" + i + ".png";
            BufferedImage image = DesktopPetSpriteLoader.read(path);
            assertNotNull(path, image);
            assertTrue(path, DesktopPetSpriteLoader.usable(image, true));
            if (i <= 3) {
                assertFalse(path, DesktopPetSpriteLoader.usable(image, false));
            }
        }
    }

    @Test
    public void hungryTicksLongerThanWorryFlash() {
        assertTrue(DesktopPetAnimation.HUNGRY_TICKS > DesktopPetAnimation.WORRY_TICKS);
    }

    @Test
    public void lowHungerEntersHungryAnimation() {
        DesktopPetAnimation animation = new DesktopPetAnimation(new Random(99));
        animation.setCareIdle(true, 10, 80, 80);
        boolean hungry = false;
        for (int i = 0; i <= DesktopPetAnimation.IDLE_TICKS * 4; i++) {
            animation.tick();
            if (animation.mode() == DesktopPetAnimation.Mode.HUNGRY) {
                hungry = true;
                break;
            }
        }
        assertTrue(hungry);
    }

    @Test
    public void hungryBubblePhrasesHaveNoEmoji() {
        for (String phrase : DesktopPetAnimation.HUNGRY_BUBBLE_PHRASES) {
            assertFalse(phrase, phrase.codePoints().anyMatch(Issue682DesktopPetHungryTest::isEmoji));
        }
    }

    @Test
    public void feedClearsHungryBubble() {
        DesktopPetAnimation animation = new DesktopPetAnimation(new Random(3));
        animation.setCareIdle(true, 12, 80, 80);
        for (int i = 0; i < 5; i++) {
            animation.tick();
        }
        assertTrue(animation.isHungryBubbleActive());
        assertTrue(DesktopPetAnimation.isHungryBubblePhrase(animation.bubble()));
        animation.playCare(DesktopPetAnimation.Mode.FEED, null);
        assertFalse(animation.isHungryBubbleActive());
        assertNull(animation.bubble());
    }

    @Test
    public void loadedHungrySpritesCycleInFrames() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        assertEquals(4, frames.hungryCount());
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.flashHungry();
        assertEquals(frames.hungry(0), frames.imageFor(animation));
        for (int i = 0; i < DesktopPetAnimation.ACTION_HOLD_TICKS; i++) {
            animation.tick();
        }
        assertEquals(frames.hungry(1), frames.imageFor(animation));
    }

    private static boolean isEmoji(int codePoint) {
        return codePoint > 0xFFFF
                || (codePoint >= 0x1F300 && codePoint <= 0x1FAFF)
                || (codePoint >= 0x2600 && codePoint <= 0x27BF);
    }
}