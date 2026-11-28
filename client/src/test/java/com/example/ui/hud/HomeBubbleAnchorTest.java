package com.example.ui.hud;

import org.junit.Test;

import java.awt.Rectangle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeBubbleAnchorTest {

    private static final int[][] SIZES = {{380, 520}, {340, 440}};
    private static final int IMG_W = 864;
    private static final int IMG_H = 1152;

    @Test
    public void everyIllustrationHasAnchorSpec() {
        for (HomeIllustration illustration : HomeIllustration.values()) {
            assertTrue(HomeBubbleAnchor.hasSpecForId(illustration.id()));
            HomeBubbleAnchor.Spec spec = HomeBubbleAnchor.specFor(illustration);
            assertTrue(spec.mouthX > 0.2 && spec.mouthX < 0.8);
            assertTrue(spec.mouthY > 0.05 && spec.mouthY < 0.5);
        }
    }

    @Test
    public void bubbleStaysInsideStageForCommonSizes() {
        Rectangle avoid = new Rectangle(300, 8, 64, 26);
        for (HomeIllustration illustration : HomeIllustration.values()) {
            for (int[] size : SIZES) {
                Rectangle stage = new Rectangle(0, 0, size[0], size[1] - ShellDimensions.TAB_BAR_HEIGHT);
                HomeBubbleAnchor.Placement placement = HomeBubbleAnchor.placeBubble(
                        stage, illustration, IMG_W, IMG_H, 220, 48, avoid);
                assertTrue(stage.contains(placement.box));
                assertFalse(placement.box.intersects(avoid));
                assertTrue(placement.mouth.x >= stage.x);
                assertTrue(placement.mouth.y >= stage.y);
            }
        }
    }

    @Test
    public void mouthNormMapsInsideDrawRect() {
        Rectangle stage = new Rectangle(0, 0, 380, 460);
        int crop = HomeBubbleAnchor.sourceTopCropPx(HomeIllustration.CHERRY_PARK_D, IMG_H);
        Rectangle draw = HomeBubbleAnchor.imageDrawRect(
                stage.width, stage.height, IMG_W, IMG_H, crop);
        HomeBubbleAnchor.Spec spec = HomeBubbleAnchor.specFor(HomeIllustration.CHERRY_PARK_D);
        var mouth = HomeBubbleAnchor.imageNormToPanel(
                spec.mouthX, spec.mouthY, draw, IMG_W, IMG_H, crop);
        assertTrue(draw.contains(mouth.x, mouth.y));
    }

    @Test
    public void placeAlbumBubbleAvoidsFaceBox() {
        Rectangle stage = new Rectangle(0, 0, 380, 460);
        Rectangle avoid = new Rectangle(300, 8, 64, 26);
        HomeBubbleAnchor.Spec spec = HomeBubbleAnchor.spec(
                0.70, 0.30, 0.70, 0.18, 0.52, 0.08, 0.36, 0.36);
        HomeBubbleAnchor.Placement placement = HomeBubbleAnchor.placeBubble(
                stage, spec, IMG_W, IMG_H, 0, 160, 36, avoid);
        assertTrue(stage.contains(placement.box));
        assertFalse(placement.box.intersects(avoid));
        Rectangle draw = HomeBubbleAnchor.imageDrawRect(stage.width, stage.height, IMG_W, IMG_H, 0);
        Rectangle face = HomeBubbleAnchor.imageNormRectToPanel(
                spec.faceX, spec.faceY, spec.faceW, spec.faceH, draw, IMG_W, IMG_H, 0);
        Rectangle hit = placement.box.intersection(face);
        assertFalse(
                "bubble " + placement.box + " overlaps face " + face,
                hit.width > 4 && hit.height > 4);
    }

    @Test
    public void albumPlacementRecomputesWhenStageResizes() {
        HomeBubbleAnchor.Spec spec = HomeBubbleAnchor.spec(
                0.25, 0.30, 0.20, 0.12, 0.40, 0.10, 0.30, 0.28);
        HomeBubbleAnchor.Placement small = HomeBubbleAnchor.placeBubble(
                new Rectangle(0, 0, 340, 380), spec, IMG_W, IMG_H, 0, 140, 32, null);
        HomeBubbleAnchor.Placement large = HomeBubbleAnchor.placeBubble(
                new Rectangle(0, 0, 760, 980), spec, IMG_W, IMG_H, 0, 140, 32, null);
        assertTrue(Math.abs(large.mouth.x - small.mouth.x) > 40);
        assertTrue(large.box.x != small.box.x || large.box.y != small.box.y);
    }
}
