package com.example.ui;

import com.example.service.ConfigPersistenceService;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.JFrame;
import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WindowOpacityTest {

    @Test
    public void transparencyPercentRangeMatchesConfig() {
        assertEquals(
                ConfigPersistenceService.MAX_WINDOW_TRANSPARENCY_PERCENT,
                WindowOpacity.MAX_TRANSPARENCY_PERCENT);
    }

    @Test
    public void clampTransparencyPercent() {
        assertEquals(0, WindowOpacity.clampTransparencyPercent(-10));
        assertEquals(0, WindowOpacity.clampTransparencyPercent(0));
        assertEquals(20, WindowOpacity.clampTransparencyPercent(20));
        assertEquals(60, WindowOpacity.clampTransparencyPercent(60));
        assertEquals(60, WindowOpacity.clampTransparencyPercent(100));
    }

    @Test
    public void percentMapsToOpacity() {
        assertEquals(1.00f, WindowOpacity.toOpacity(0), 0.001f);
        assertEquals(0.80f, WindowOpacity.toOpacity(20), 0.001f);
        assertEquals(0.40f, WindowOpacity.toOpacity(60), 0.001f);
        assertEquals(0.40f, WindowOpacity.toOpacity(90), 0.001f);
    }

    @Test
    public void opacityMapsBackToPercent() {
        assertEquals(0, WindowOpacity.toTransparencyPercent(1.00f));
        assertEquals(20, WindowOpacity.toTransparencyPercent(0.80f));
        assertEquals(60, WindowOpacity.toTransparencyPercent(0.40f));
        assertEquals(0, WindowOpacity.toTransparencyPercent(0f));
        assertEquals(0, WindowOpacity.toTransparencyPercent(Float.NaN));
    }

    @Test
    public void formatPercentLabel() {
        assertEquals("不透明", WindowOpacity.formatPercentLabel(0));
        assertEquals("15%", WindowOpacity.formatPercentLabel(15));
        assertEquals("60%", WindowOpacity.formatPercentLabel(99));
    }

    @Test
    public void applyNullWindowFails() {
        assertFalse(WindowOpacity.apply(null, 0.8f));
        assertFalse(WindowOpacity.applyTransparencyPercent(null, 20));
    }

    @Test
    public void applyOnUndecoratedFrameWhenCompositorAllows() {
        Assume.assumeFalse("headless JVM cannot realize frames", GraphicsEnvironment.isHeadless());
        Assume.assumeTrue("no TRANSLUCENT compositor", WindowOpacity.isTranslucencySupported());

        JFrame frame = new JFrame("opacity-test");
        try {
            frame.setUndecorated(true);
            frame.setSize(200, 80);
            assertTrue(WindowOpacity.applyTransparencyPercent(frame, 20));
            assertEquals(0.80f, frame.getOpacity(), 0.001f);
            assertTrue(WindowOpacity.applyTransparencyPercent(frame, 0));
            assertEquals(1.00f, frame.getOpacity(), 0.001f);
        } finally {
            frame.dispose();
        }
    }

    @Test
    public void applyOnDecoratedFrameFailsWhenTransparent() {
        Assume.assumeFalse("headless JVM cannot realize frames", GraphicsEnvironment.isHeadless());

        JFrame frame = new JFrame("decorated-opacity-test");
        try {
            frame.setSize(200, 80);
            assertFalse(WindowOpacity.applyTransparencyPercent(frame, 20));
        } finally {
            frame.dispose();
        }
    }
}
