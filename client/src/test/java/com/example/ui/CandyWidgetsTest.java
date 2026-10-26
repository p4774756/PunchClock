package com.example.ui;

import com.example.model.CheckInHistoryEntry;
import com.example.model.TaskStatus;
import org.junit.Test;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CandyWidgetsTest {

    @Test
    public void checkBoxKeepsSelectionAndClick() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CandyCheckBox box = new CandyCheckBox("啟用", true);
            int[] events = {0};
            box.addItemListener(event -> events[0]++);
            assertTrue(box.isSelected());
            box.doClick();
            assertFalse(box.isSelected());
            assertTrue(events[0] >= 1);
            box.setEnabled(false);
            box.doClick();
            assertFalse(box.isSelected());
            box.setEnabled(true);
            box.setSelected(true);
            assertTrue(box.isSelected());
            assertTrue(box.getUI().getClass().getName().contains("CandyCheckBox"));
        });
    }

    @Test
    public void comboKeepsModelAfterThemeApply() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> hours = new JComboBox<>(new String[] {"00", "01", "13"});
            hours.setFont(UiFonts.latinPlain(13));
            CandyComboBox.install(hours);
            int[] actions = {0};
            hours.addActionListener(event -> actions[0]++);
            hours.setSelectedIndex(2);
            assertEquals("13", hours.getSelectedItem());
            assertTrue(actions[0] >= 1);
            JPanel panel = new JPanel();
            panel.add(hours);
            NightTheme.apply(panel);
            assertTrue(hours.getUI().getClass().getName().contains("CandyComboBox"));
            assertEquals("13", hours.getSelectedItem());
            assertTrue(hours.getPreferredSize().height <= 42);
            assertTrue(hours.getPreferredSize().width < 160);
        });
    }

    @Test
    public void editableComboKeepsEditorAndChineseText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> combo = RecentValuesHelper.createCombo(
                    UiFonts.latinPlain(13), "https://example.com", "網址");
            ComponentHold editor = new ComponentHold(combo.getEditor().getEditorComponent());
            CandyComboBox.dress(combo);
            assertSame(editor.component, combo.getEditor().getEditorComponent());
            JTextField field = (JTextField) combo.getEditor().getEditorComponent();
            int[] edits = {0};
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    edits[0]++;
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    edits[0]++;
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                }
            });
            field.setText("https://example.com/中文");
            assertEquals("https://example.com/中文", field.getText());
            assertTrue(edits[0] > 0);
            JPanel panel = new JPanel();
            panel.add(combo);
            NightTheme.apply(panel);
            JTextField after = (JTextField) combo.getEditor().getEditorComponent();
            assertSame(field, after);
            after.setText("皮卡丘");
            assertEquals("皮卡丘", after.getText());
            assertTrue(Boolean.TRUE.equals(combo.getClientProperty(CandyComboBox.MARK)));
        });
    }

    @Test
    public void textFieldKeepsSelectionAndPasswordMask() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CandyTextField field = new CandyTextField("每日排程", 12);
            field.setFont(UiFonts.chineseBold(13));
            field.setText("皮卡丘");
            field.selectAll();
            assertEquals("皮卡丘", field.getSelectedText());
            JPanel panel = new JPanel();
            panel.add(field);
            NightTheme.apply(panel);
            assertTrue(field.getUI().getClass().getName().contains("CandyTextField"));
            assertEquals("皮卡丘", field.getText());
            assertTrue(field.getBorder().getClass().getName().contains("CandyTextField"));

            JPasswordField password = new JPasswordField("secret");
            char echo = password.getEchoChar();
            CandyTextField.install(password, true);
            assertEquals(echo, password.getEchoChar());
            assertEquals("secret", new String(password.getPassword()));
            assertFalse(password.getEchoChar() == 0);
        });
    }

    @Test
    public void scrollBarHidesArrowButtonsAndStaysOnSchedulePageOnly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JScrollPane schedule = (JScrollPane) NightTheme.page(new JPanel(), true);
            assertTrue(schedule.getVerticalScrollBar().getUI().getClass().getName().contains("CandyScrollBar"));
            assertEquals(CandyTheme.SCROLL_WIDTH, schedule.getVerticalScrollBar().getPreferredSize().width);
            assertEquals(0, schedule.getVerticalScrollBar().getComponent(0).getPreferredSize().width);
            JScrollPane other = (JScrollPane) NightTheme.page(new JPanel(), false);
            assertFalse(other.getVerticalScrollBar().getUI().getClass().getName().contains("CandyScrollBar"));
        });
    }

    @Test
    public void historyKeepsEveryFieldAndDropsTheHorizontalBar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String detail = "R".repeat(120);
            CheckInHistoryEntry entry = new CheckInHistoryEntry(
                    LocalDateTime.of(2026, 10, 26, 17, 0),
                    "每日排程",
                    TaskStatus.SUCCESS,
                    detail,
                    true);
            String line = entry.toDisplayLine();
            assertTrue(line.contains(detail));
            assertTrue(line.contains("立即執行"));
            String[] columns = CandyHistoryColumns.split(line);
            assertEquals("2026-10-26 17:00:00", columns[0]);
            assertEquals("每日排程", columns[1]);
            assertTrue(columns[2].contains("成功"));
            assertEquals("立即執行", columns[3]);
            assertEquals(detail, columns[4]);
            String tip = CandyHistoryColumns.tip(line);
            assertTrue(tip.contains(detail));
            assertTrue(tip.contains("每日排程"));
            assertEquals("只有一行", CandyHistoryColumns.split("只有一行")[4]);

            Fonts fonts = fonts();
            PanelFactory.CheckInHistoryRefs refs = new PanelFactory.CheckInHistoryRefs();
            PanelFactory.createCheckInHistoryPanel(refs, fonts.main, fonts.bold);
            refs.listModel.addElement(line);
            JScrollPane scroll = (JScrollPane) refs.historyList.getParent().getParent();
            assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
            assertTrue(Boolean.TRUE.equals(scroll.getClientProperty(CandyScrollBar.MARK)));
            NightTheme.apply(scroll.getParent());
            assertTrue(scroll.getVerticalScrollBar().getUI().getClass().getName().contains("CandyScrollBar"));
            assertEquals(line, refs.historyList.getModel().getElementAt(0));
        });
    }

    @Test
    public void schedulePageKeepsCandyButtonsAndCompactCombos() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fonts fonts = fonts();
            PanelFactory.SlotPanelRefs refs = new PanelFactory.SlotPanelRefs();
            JPanel root = PanelFactory.createSlotPanel(refs, fonts.main, fonts.bold, fonts.field);
            assertTrue(refs.executeNowButton.getUI().getClass().getName().contains("CandyButton"));
            assertEquals(240, refs.buttonIdCombo.getPreferredSize().width);
            assertTrue(refs.browserCombo.getPreferredSize().width >= 148);
            assertTrue(refs.workIn.hourCombo.getPreferredSize().height <= 42);
            assertTrue(refs.workIn.hourCombo.getPreferredSize().width >= 84);
            assertTrue(refs.workIn.minuteCombo.getPreferredSize().width >= 84);
            assertTrue(refs.workIn.nameField.getPreferredSize().height <= 42);
            assertTrue(refs.workIn.enabledCheckBox instanceof CandyCheckBox);
            assertTrue(refs.workIn.randomOffsetCheckBox instanceof CandyCheckBox);
            NightTheme.apply(root);
            assertTrue(refs.executeNowButton.getUI().getClass().getName().contains("CandyButton"));
            assertTrue(refs.browserCombo.getUI().getClass().getName().contains("CandyComboBox"));
            assertTrue(refs.workIn.nameField.getUI().getClass().getName().contains("CandyTextField"));
            assertTrue(refs.workIn.panel instanceof CandyPanel);
        });
    }

    @Test
    public void installKeepsEditableEditorAndPaddedRendererText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> combo = new JComboBox<>(new String[] {"甲", "乙"});
            combo.setEditable(true);
            combo.setFont(UiFonts.chinesePlain(13));
            CandyComboBox.install(combo);
            JTextField editor = (JTextField) combo.getEditor().getEditorComponent();
            editor.setText("皮卡丘");
            JPanel panel = new JPanel();
            panel.add(combo);
            NightTheme.apply(panel);
            JTextField after = (JTextField) combo.getEditor().getEditorComponent();
            assertSame(editor, after);
            assertEquals("皮卡丘", after.getText());
            assertTrue(combo.getUI().getClass().getName().contains("CandyComboBox"));

            JComboBox<Integer> day = new JComboBox<>();
            day.addItem(9);
            day.setRenderer(new javax.swing.DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(
                        javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    String text = value instanceof Integer
                            ? String.format("%02d", (Integer) value) : "";
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
                }
            });
            CandyComboBox.install(day);
            assertEquals("09", CandyComboBox.shownText(day, day.getSelectedItem()));
        });
    }

    @Test
    public void dressAreaKeepsMultilineUi() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea("日誌一行\n第二行");
            area.setLineWrap(true);
            javax.swing.plaf.TextUI ui = area.getUI();
            CandyTextField.dressArea(area);
            NightTheme.apply(area);
            assertSame(ui, area.getUI());
            assertTrue(area.getLineWrap());
            assertEquals("日誌一行\n第二行", area.getText());
            assertEquals(CandyTheme.FIELD, area.getBackground());
        });
    }

    @Test
    public void otherPagesKeepCandyControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fonts fonts = fonts();
            PanelFactory.ServerConfigRefs settings = new PanelFactory.ServerConfigRefs();
            PanelFactory.createServerConfigBody(settings, fonts.main, fonts.bold, fonts.field);
            assertTrue(settings.enableServerCheckBox instanceof CandyCheckBox);
            assertTrue(settings.trustAllSslCheckBox instanceof CandyCheckBox);
            assertTrue(settings.relayEnabledCheckBox instanceof CandyCheckBox);
            assertTrue(settings.heartbeatTokenField.getUI().getClass().getName().contains("CandyTextField"));
            assertEquals("punchclock-dev-secret", new String(settings.heartbeatTokenField.getPassword()));
            assertTrue(Boolean.TRUE.equals(settings.serverUrlCombo.getClientProperty(CandyComboBox.MARK)));
            JTextField urlEditor = (JTextField) settings.serverUrlCombo.getEditor().getEditorComponent();
            urlEditor.setText("https://example.com/中文");
            assertEquals("https://example.com/中文", urlEditor.getText());
            assertTrue(Boolean.TRUE.equals(settings.relayHelpArea.getClientProperty(CandyTextField.MARK)));
            assertTrue(settings.relayHelpArea.getLineWrap());
            assertFalse(settings.relayHelpArea.getUI().getClass().getName().contains("CandyTextField"));

            PanelFactory.PeerInteractionRefs peers = new PanelFactory.PeerInteractionRefs();
            JPanel peer = PanelFactory.createPeerInteractionPanel(peers, fonts.main, fonts.bold);
            assertEquals(280, peers.clientIdCombo.getPreferredSize().width);
            assertTrue(peers.clientIdCombo.getPreferredSize().height >= 30);
            assertTrue(peers.openChatButton.getUI().getClass().getName().contains("CandyButton"));
            assertTrue(peers.clearFileButton.getUI().getClass().getName().contains("CandyButton"));
            assertTrue(Boolean.TRUE.equals(peers.peerTableScroll.getClientProperty(CandyScrollBar.MARK)));
            NightTheme.apply(peer);
            assertTrue(peers.clientIdCombo.getUI().getClass().getName().contains("CandyComboBox"));
            assertEquals(280, peers.clientIdCombo.getPreferredSize().width);
            JTextField idEditor = (JTextField) peers.clientIdCombo.getEditor().getEditorComponent();
            idEditor.setText("皮卡丘");
            assertEquals("皮卡丘", idEditor.getText());

            PanelFactory.LogPanelRefs logs = new PanelFactory.LogPanelRefs();
            JPanel log = PanelFactory.createLogPanel(logs, fonts.main, fonts.bold);
            assertTrue(log instanceof CandyPanel);
            assertTrue(logs.clearLogButton.getUI().getClass().getName().contains("CandyButton"));
            logs.logTextArea.setText("hello");
            logs.clearLogButton.doClick();
            assertEquals("", logs.logTextArea.getText());
            assertTrue(logs.logTextArea.getLineWrap());
            JScrollPane logScroll = (JScrollPane) logs.logTextArea.getParent().getParent();
            NightTheme.apply(log);
            assertTrue(logScroll.getVerticalScrollBar().getUI().getClass().getName().contains("CandyScrollBar"));
        });
    }

    private static Fonts fonts() {
        Fonts fonts = new Fonts();
        fonts.main = UiFonts.chinesePlain(13);
        fonts.bold = UiFonts.chineseBold(13);
        fonts.field = UiFonts.latinPlain(13);
        return fonts;
    }

    private static final class Fonts {
        java.awt.Font main;
        java.awt.Font bold;
        java.awt.Font field;
    }

    private static final class ComponentHold {
        final java.awt.Component component;

        ComponentHold(java.awt.Component component) {
            this.component = component;
        }
    }
}
