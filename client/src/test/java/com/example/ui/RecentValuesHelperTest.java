package com.example.ui;

import org.junit.Assume;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class RecentValuesHelperTest {

    @Test
    public void createComboUsesHeavyweightPopup() throws Exception {
        AtomicReference<JComboBox<String>> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> combo = RecentValuesHelper.createCombo(
                    new Font(Font.SANS_SERIF, Font.PLAIN, 13),
                    "https://example.com/path",
                    "test");
            combo.addItem("https://another-long-url.example.com/abc");
            ref.set(combo);
        });

        JComboBox<String> combo = ref.get();
        assertNotNull(combo);
        assertFalse(combo.isLightWeightPopupEnabled());
        assertTrue(combo.isEditable());
    }

    @Test
    public void popupCanOpenWithoutHorizontalOverflow() throws Exception {
        Assume.assumeFalse("Headless JVM cannot show combo popup", GraphicsEnvironment.isHeadless());

        AtomicReference<JComboBox<String>> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            JComboBox<String> combo = RecentValuesHelper.createCombo(
                    new Font(Font.SANS_SERIF, Font.PLAIN, 13),
                    "https://example.com/path",
                    "test");
            combo.addItem("https://very-long-url.example.com/segment/one/two/three");
            frame.add(combo);
            frame.pack();
            frame.setLocation(80, 80);
            frame.setVisible(true);
            ref.set(combo);
        });

        JComboBox<String> combo = ref.get();
        SwingUtilities.invokeAndWait(() -> {
            combo.showPopup();
            combo.hidePopup();
        });
    }

    @Test
    public void snapshotHistoryReflectsComboItems() throws Exception {
        AtomicReference<JComboBox<String>> ref = new AtomicReference<>();
        List<String> history = new ArrayList<>();
        history.add("https://a.example.com");
        history.add("https://b.example.com");

        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> combo = RecentValuesHelper.createCombo(
                    new Font(Font.SANS_SERIF, Font.PLAIN, 13), "", "test");
            RecentValuesHelper.applyHistory(combo, history, "https://a.example.com");
            ref.set(combo);
        });

        List<String> snapshot = RecentValuesHelper.snapshotHistory(ref.get());
        assertEquals(2, snapshot.size());
        assertTrue(snapshot.contains("https://a.example.com"));
        assertTrue(snapshot.contains("https://b.example.com"));
    }
}
