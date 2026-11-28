package com.example.ui;

import org.junit.Test;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 設定外觀分頁精簡與分頁／勾選框對比度（#776）。 */
public class Issue776SettingsAppearanceTest {

    @Test
    public void appearanceTabKeepsDesktopPetThenDockOnly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
            JTabbedPane tabs = PanelFactory.createServerConfigBody(
                    refs,
                    UiFonts.chinesePlain(13),
                    UiFonts.chineseBold(13),
                    UiFonts.latinPlain(13));
            tabs.setSelectedIndex(PanelFactory.SETTINGS_APPEARANCE_TAB);

            List<CandyPanel> sections = appearanceSections(tabs);
            assertEquals(3, sections.size());
            assertTrue(containsDescendant(sections.get(0), refs.desktopPetSwitch));
            assertTrue(containsDescendant(sections.get(1), refs.dockIconSwimsuitSwitch));
            assertTrue(containsDescendant(sections.get(2), refs.privateAlbumVisibleSwitch));

            assertNull(refs.windowTransparencySlider);
            assertNull(refs.homeIllustrationCombo);
            assertNull(refs.checkInUiVisibleSwitch);
            assertEquals("桌面寵物與 Dock 圖示", tabs.getToolTipTextAt(PanelFactory.SETTINGS_APPEARANCE_TAB));
        });
    }

    @Test
    public void selectedTabAndCheckboxMeetContrastTargets() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            NightTheme.installLook();
            Object selected = UIManager.get("TabbedPane.selected");
            assertTrue(selected instanceof Color);
            assertContrastAtLeast((Color) selected, NightTheme.INK, 4.5);

            CandyCheckBox box = new CandyCheckBox("啟用雲端單向狀態回報", true);
            box.setFont(UiFonts.chineseBold(13));
            assertContrastAtLeast(CandyTheme.PINK_DEEP, CandyTheme.PINK_INK, 4.5);

            CandyCheckBox trust = new CandyCheckBox("信任所有 SSL（除錯）", true);
            trust.setFont(UiFonts.chinesePlain(13));
            assertTrue(trust instanceof JCheckBox);
        });
    }

    private static List<CandyPanel> appearanceSections(JTabbedPane tabs) {
        JPanel page = (JPanel) tabs.getSelectedComponent();
        JPanel stack = (JPanel) page.getComponent(0);
        List<CandyPanel> out = new ArrayList<>();
        for (Component child : stack.getComponents()) {
            if (child instanceof CandyPanel) {
                out.add((CandyPanel) child);
            }
        }
        return out;
    }

    private static boolean containsDescendant(Container root, Component target) {
        if (root == null || target == null) {
            return false;
        }
        for (Component child : root.getComponents()) {
            if (child == target) {
                return true;
            }
            if (child instanceof Container && containsDescendant((Container) child, target)) {
                return true;
            }
        }
        return false;
    }

    private static void assertContrastAtLeast(Color a, Color b, double min) {
        double ratio = contrast(a, b);
        assertTrue("contrast " + ratio + " < " + min, ratio >= min - 0.01);
    }

    private static double contrast(Color a, Color b) {
        double l1 = relativeLuminance(a);
        double l2 = relativeLuminance(b);
        double lighter = Math.max(l1, l2) + 0.05;
        double darker = Math.min(l1, l2) + 0.05;
        return lighter / darker;
    }

    private static double relativeLuminance(Color color) {
        double r = channel(color.getRed());
        double g = channel(color.getGreen());
        double b = channel(color.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}