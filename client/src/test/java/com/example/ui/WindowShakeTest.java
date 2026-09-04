package com.example.ui;

import org.junit.Assume;
import org.junit.Test;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WindowShakeTest {

    @Test
    public void shakePathOscillatesAroundOriginThenReturns() {
        Point origin = new Point(400, 200);
        List<Point> path = WindowShake.shakePath(origin);

        assertEquals(WindowShake.OFFSETS_X.length, path.size());
        assertEquals(origin.x + WindowShake.OFFSETS_X[0], path.get(0).x);
        assertEquals(origin.y, path.get(0).y);
        assertEquals(origin, path.get(path.size() - 1));

        int previous = 0;
        for (int i = 0; i < WindowShake.OFFSETS_X.length - 1; i++) {
            int dx = WindowShake.OFFSETS_X[i];
            assertTrue("offset should reverse sign each step", dx * previous <= 0);
            assertTrue("offset should not be zero until the last frame", dx != 0);
            previous = dx;
        }
    }

    @Test
    public void shakePathNullOriginIsEmpty() {
        assertTrue(WindowShake.shakePath(null).isEmpty());
    }

    @Test
    public void canShakeRejectsNull() {
        assertFalse(WindowShake.canShake(null));
    }

    @Test
    public void canShakeRejectsMaximizedFrame() {
        Assume.assumeFalse("headless environment cannot realize AWT frames",
                GraphicsEnvironment.isHeadless());

        Frame frame = new Frame();
        try {
            frame.addNotify();
            assertTrue(WindowShake.canShake(frame));
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                assertFalse(WindowShake.canShake(frame));
            }
        } finally {
            frame.dispose();
        }
    }

    @Test
    public void shakeRestoresOriginalLocationWhenFinished() throws Exception {
        Assume.assumeFalse("headless environment cannot show frames",
                GraphicsEnvironment.isHeadless());

        javax.swing.JFrame frame = new javax.swing.JFrame("shake-test");
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        try {
            frame.setUndecorated(true);
            frame.setSize(240, 120);
            frame.setLocation(160, 140);
            frame.setVisible(true);
            Point origin = frame.getLocation();
            javax.swing.SwingUtilities.invokeAndWait(() ->
                    WindowShake.shake(frame, done::countDown));
            assertTrue("shake should finish synchronously on EDT",
                    done.await(1, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(origin, frame.getLocation());
        } finally {
            frame.dispose();
        }
    }

    @Test
    public void bringToFrontNullIsNoOp() {
        WindowShake.bringToFront(null);
    }
}
