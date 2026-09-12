package com.example.ui;

import org.junit.Assume;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WindowChromeTest {

    @Test
    public void installProvidesSliderAndBody() throws Exception {
        Assume.assumeFalse("headless JVM cannot create frames", GraphicsEnvironment.isHeadless());

        JFrame[] holder = new JFrame[1];
        WindowChrome.Controls[] controls = new WindowChrome.Controls[1];
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            holder[0] = frame;
            controls[0] = WindowChrome.install(
                    frame, "透明度測試", null, new Font(Font.SANS_SERIF, Font.BOLD, 13),
                    new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        });
        try {
            assertNotNull(controls[0]);
            assertNotNull(controls[0].transparencySlider);
            assertEquals(0, controls[0].transparencySlider.getMinimum());
            assertEquals(WindowOpacity.MAX_TRANSPARENCY_PERCENT, controls[0].transparencySlider.getMaximum());
            JPanel body = WindowChrome.bodyOf(holder[0]);
            assertNotNull(body);
            assertTrue(holder[0].isUndecorated());
        } finally {
            SwingUtilities.invokeAndWait(() -> holder[0].dispose());
        }
    }
}
