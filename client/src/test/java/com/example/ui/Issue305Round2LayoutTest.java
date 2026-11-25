package com.example.ui;

import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 第 2 輪：換行列不能再被鎖高裁切，預設內容寬要放得下的項目要在同一列。
 */
public class Issue305Round2LayoutTest {

    @Test
    public void avatarAndDeviceIdStayInsideAtDefaultAndMinContentWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fonts fonts = fonts();
            PanelFactory.PeerInteractionRefs refs = new PanelFactory.PeerInteractionRefs();
            PanelFactory.createPeerInteractionPanel(refs, fonts.main, fonts.bold);

            layoutRow(refs.avatarRow, 269);
            assertInside(refs.avatarRow, "avatar-269");
            assertSameBand(refs.avatarRow, "avatar-269");

            layoutRow(refs.avatarRow, 245);
            assertInside(refs.avatarRow, "avatar-245");
            assertSameBand(refs.avatarRow, "avatar-245");

            JComponent idRow = (JComponent) refs.clientIdCombo.getParent();
            layoutRow(idRow, 269);
            assertInside(idRow, "id-269");
            assertSameBand(idRow, "id-269");

            layoutRow(idRow, 229);
            assertInside(idRow, "id-229");
            assertSameBand(idRow, "id-229");
        });
    }

    @Test
    public void cloudFlagsStayInsideWhenTheyWrap() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fonts fonts = fonts();
            PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
            PanelFactory.createServerConfigBody(refs, fonts.main, fonts.bold, fonts.field);
            refs.heartbeatStatusLabel.setText("[正常] HTTP POST 正常");
            JComponent row = (JComponent) refs.enableServerCheckBox.getParent();
            layoutRow(row, 264);
            assertInside(row, "cloud-264");
            assertSameBand(row, "cloud-264");
            assertEquals(13, refs.enableServerCheckBox.getFont().getSize());
            layoutRow(row, 224);
            assertInside(row, "cloud-224");
            assertSameBand(row, "cloud-224");
        });
    }

    @Test
    public void scheduleTimeRowFitsAndSkipMovesToNameRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fonts fonts = fonts();
            PanelFactory.SlotPanelRefs refs = new PanelFactory.SlotPanelRefs();
            PanelFactory.createSlotPanel(refs, fonts.main, fonts.bold, fonts.field);
            JComponent hourGroup = (JComponent) refs.workIn.hourCombo.getParent();
            JComponent minuteGroup = (JComponent) refs.workIn.minuteCombo.getParent();
            JComponent schedule = (JComponent) hourGroup.getParent();
            JComponent nameRow = (JComponent) refs.workIn.nameField.getParent();

            layoutRow(schedule, 273);
            layoutRow(nameRow, 273);
            assertInside(schedule, "schedule-273");
            assertTrue("hour/minute split " + bounds(hourGroup) + " " + bounds(minuteGroup),
                    Math.abs(hourGroup.getY() - minuteGroup.getY()) < 8);
            assertSameBand(hourGroup, minuteGroup, "schedule-273");
            hourGroup.doLayout();
            minuteGroup.doLayout();
            assertShowsDigits(refs.workIn.hourCombo, "00");
            assertShowsDigits(refs.workIn.minuteCombo, "00");
            assertTrue("random offset should sit to the right of minute",
                    refs.workIn.randomOffsetCheckBox.getX() + 1
                            >= minuteGroup.getX() + minuteGroup.getWidth());
            assertInside(nameRow, "name-273");
            assertTrue("skip parent " + refs.skipDatesButton.getParent(),
                    refs.skipDatesButton.getParent() == nameRow);
            assertTrue("skip right of name " + bounds(refs.skipDatesButton) + " " + bounds(refs.workIn.nameField),
                    refs.skipDatesButton.getX() + refs.skipDatesButton.getWidth()
                            > refs.workIn.nameField.getX() + refs.workIn.nameField.getWidth());
            assertSameBand(refs.workIn.nameField, refs.skipDatesButton, "skip-name-273");

            refs.skipDatesButton.setText("跳過日期（2）");
            layoutRow(schedule, 273);
            layoutRow(nameRow, 273);
            assertSameBand(refs.workIn.nameField, refs.skipDatesButton, "skip2-name-273");
            assertInside(nameRow, "name-skip2-273");

            refs.workIn.nameSavedLabel.setVisible(true);
            layoutRow(nameRow, 273);
            assertInside(nameRow, "name-saved-273");
            assertTrue(refs.workIn.nameSavedLabel.isVisible());
            assertTrue(refs.workIn.nameSavedLabel.getWidth() > 0);
            assertTrue(refs.workIn.nameSavedLabel.getHeight() > 0);

            layoutRow(schedule, 257);
            layoutRow(nameRow, 257);
            assertInside(schedule, "schedule-257");
            assertInside(nameRow, "name-257");
        });
    }

    @Test
    public void fitRelayHelpGrowsWhenTextOrWidthChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea("有新版時會顯示在這裡。");
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(UiFonts.chinesePlain(12));
            PanelFactory.fitRelayHelp(area);
            area.setSize(288, area.getPreferredSize().height);
            PanelFactory.fitRelayHelp(area);
            int shortHeight = area.getPreferredSize().height;
            assertFullyVisible(area, "short-288");

            area.setSize(248, shortHeight);
            PanelFactory.fitRelayHelp(area);
            assertFullyVisible(area, "short-248");

            area.setText("新版 v4.1.0 · abcdef0\n"
                    + "大小 18.4 MB\n"
                    + "這次修改了桌面寵物的視窗層級，並且調整了排程卡片的換行。\n"
                    + "好友傳檔的裝置 ID 改成跟套用同一列。\n"
                    + "設定頁的雲端連線改成橫排，窄的時候再換行。\n"
                    + "中繼說明要看得到最後一句。\n"
                    + "跳過日期移進每日排程。\n"
                    + "名稱套用後會顯示已儲存。\n"
                    + "底部分頁的迷你按鈕跟其他分頁同一套。\n"
                    + "下載檔名改成 SKLbaby.jar。");
            PanelFactory.fitRelayHelp(area);
            assertTrue("long height " + area.getPreferredSize().height + " vs " + shortHeight,
                    area.getPreferredSize().height > shortHeight + 20);
            area.setSize(288, area.getPreferredSize().height);
            PanelFactory.fitRelayHelp(area);
            assertFullyVisible(area, "long-288");
            assertFalse(area.getParent() instanceof javax.swing.JScrollPane);
        });
    }

    private static void layoutRow(JComponent row, int width) {
        row.setSize(new Dimension(width, Math.max(row.getHeight(), 400)));
        Dimension pref = row.getPreferredSize();
        row.setSize(new Dimension(width, Math.max(1, pref.height)));
        row.doLayout();
    }

    private static void assertInside(JComponent row, String label) {
        assertTrue(label + " height " + row.getHeight() + " pref " + row.getPreferredSize().height,
                row.getHeight() + 1 >= row.getPreferredSize().height);
        for (Component child : row.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            assertTrue(label + " " + child + " @" + bounds(child) + " row " + row.getWidth() + "x" + row.getHeight(),
                    child.getX() >= -1
                            && child.getY() >= -1
                            && child.getX() + child.getWidth() <= row.getWidth() + 1
                            && child.getY() + child.getHeight() <= row.getHeight() + 1);
        }
    }

    private static void assertShowsDigits(javax.swing.JComboBox<?> combo, String digits) {
        FontMetrics metrics = combo.getFontMetrics(combo.getFont());
        int textWidth = combo.getWidth() - combo.getInsets().left - 28;
        assertEquals(digits + " in " + combo.getWidth() + " insets " + combo.getInsets().left
                        + " textWidth " + textWidth,
                digits, CandyTheme.fit(digits, metrics, textWidth));
    }

    private static void assertSameBand(Component left, Component right, String label) {
        int top = Math.min(left.getY(), right.getY());
        int bottom = Math.max(left.getY() + left.getHeight(), right.getY() + right.getHeight());
        int tallest = Math.max(left.getHeight(), right.getHeight());
        assertTrue(label + " " + bounds(left) + " " + bounds(right), bottom - top <= tallest + 6);
    }

    private static void assertSameBand(JComponent row, String label) {
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        int tallest = 0;
        for (Component child : row.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            top = Math.min(top, child.getY());
            bottom = Math.max(bottom, child.getY() + child.getHeight());
            tallest = Math.max(tallest, child.getHeight());
        }
        assertTrue(label + " band " + (bottom - top) + " tallest " + tallest
                        + " row " + row.getWidth() + "x" + row.getHeight() + " " + dump(row),
                bottom - top <= tallest + 6);
    }

    private static void assertFullyVisible(JTextArea area, String label) {
        area.setSize(new Dimension(Math.max(1, area.getWidth()), area.getPreferredSize().height));
        try {
            Rectangle2D start2d = area.modelToView2D(0);
            int last = Math.max(0, area.getText().length() - 1);
            Rectangle2D end2d = area.modelToView2D(last);
            Rectangle start = start2d != null ? start2d.getBounds() : null;
            Rectangle end = end2d != null ? end2d.getBounds() : null;
            assertNotNull(start);
            assertNotNull(end);
            assertTrue(label + " end " + end + " area " + area.getWidth() + "x" + area.getHeight(),
                    end.y >= 0 && end.y + end.height <= area.getHeight() + 2);
            assertTrue(label + " end x " + end,
                    end.x + 2 <= area.getWidth() + 2);
        } catch (BadLocationException ex) {
            throw new AssertionError(label, ex);
        }
    }

    private static String dump(JComponent row) {
        StringBuilder text = new StringBuilder();
        for (Component child : row.getComponents()) {
            text.append(" [").append(child.getClass().getSimpleName());
            if (child instanceof javax.swing.JLabel) {
                text.append(':').append(((javax.swing.JLabel) child).getText());
            } else if (child instanceof javax.swing.AbstractButton) {
                text.append(':').append(((javax.swing.AbstractButton) child).getText());
            }
            text.append(' ').append(bounds(child)).append(']');
        }
        return text.toString();
    }

    private static String bounds(Component child) {
        return child.getX() + "," + child.getY() + " " + child.getWidth() + "x" + child.getHeight();
    }

    private static Fonts fonts() {
        return new Fonts(UiFonts.chinesePlain(13), UiFonts.chineseBold(13), UiFonts.latinPlain(13));
    }

    private static final class Fonts {
        final Font main;
        final Font bold;
        final Font field;

        Fonts(Font main, Font bold, Font field) {
            this.main = main;
            this.bold = bold;
            this.field = field;
        }
    }
}
