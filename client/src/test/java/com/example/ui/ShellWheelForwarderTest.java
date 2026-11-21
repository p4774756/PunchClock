package com.example.ui;

import org.junit.Assume;
import org.junit.Test;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShellWheelForwarderTest {

    @Test
    public void forwardsWheelWhenInnerHasNoVerticalOverflow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel tall = new JPanel();
            tall.setPreferredSize(new Dimension(300, 1200));
            JScrollPane outer = CompactShellPanels.wrap(tall);
            outer.setSize(320, 400);
            outer.validate();

            JScrollBar outerBar = outer.getVerticalScrollBar();
            Assume.assumeTrue(outerBar.getMaximum() > outerBar.getVisibleAmount());
            int before = outerBar.getValue();
            MouseWheelEvent event = new MouseWheelEvent(
                    tall,
                    MouseWheelEvent.MOUSE_WHEEL,
                    System.currentTimeMillis(),
                    0,
                    40,
                    40,
                    0,
                    false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                    3,
                    1);
            ShellWheelForwarder.handle(outer, event);
            assertTrue(outerBar.getValue() > before);
        });
    }

    @Test
    public void innerScrollsVerticalWhenOverflowing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel body = new JPanel(new java.awt.BorderLayout());
            body.setPreferredSize(new Dimension(300, 400));
            JPanel tall = new JPanel();
            tall.setPreferredSize(new Dimension(260, 800));
            JScrollPane inner = new JScrollPane(tall);
            inner.setPreferredSize(new Dimension(260, 160));
            body.add(inner, java.awt.BorderLayout.CENTER);
            JScrollPane outer = CompactShellPanels.wrap(body);
            outer.setSize(320, 200);
            outer.doLayout();
            inner.setSize(260, 160);
            inner.doLayout();

            JScrollBar innerBar = inner.getVerticalScrollBar();
            JScrollBar outerBar = outer.getVerticalScrollBar();
            int innerBefore = innerBar.getValue();
            int outerBefore = outerBar.getValue();
            Assume.assumeTrue(innerBar.getMaximum() > innerBar.getVisibleAmount());
            MouseWheelEvent event = new MouseWheelEvent(
                    tall,
                    MouseWheelEvent.MOUSE_WHEEL,
                    System.currentTimeMillis(),
                    0,
                    40,
                    40,
                    0,
                    false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                    3,
                    1);
            ShellWheelForwarder.handle(outer, event);
            assertTrue(innerBar.getValue() > innerBefore);
            assertEquals(outerBefore, outerBar.getValue());
        });
    }
}