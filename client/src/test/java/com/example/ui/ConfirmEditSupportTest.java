package com.example.ui;

import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfirmEditSupportTest {

    @Test
    public void formatCounterUsesCodePointsAndEmptyWhenNoMax() {
        assertEquals("0/20", ConfirmEditSupport.formatCounter("", 20));
        assertEquals("6/20", ConfirmEditSupport.formatCounter("忙碌中請留言", 20));
        assertEquals("2/12", ConfirmEditSupport.formatCounter("🙂🙂", 12));
        assertEquals("", ConfirmEditSupport.formatCounter("hello", 0));
    }

    @Test
    public void dirtyAndRevertRestoreCommittedValue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField field = new JTextField();
            ConfirmEditSupport.setCommitted(field, "原本");
            field.setText("改過了");
            assertTrue(ConfirmEditSupport.isDirty(field.getText(), ConfirmEditSupport.getCommitted(field)));
            ConfirmEditSupport.revertText(field);
            assertEquals("原本", field.getText());
            assertFalse(ConfirmEditSupport.isDirty(field.getText(), ConfirmEditSupport.getCommitted(field)));
        });
    }

    @Test
    public void syncButtonsEnableConfirmOnlyWhenDirtyAndSavable() {
        JButton confirm = new JButton();
        JButton cancel = new JButton();
        ConfirmEditSupport.syncButtons(confirm, cancel, false, false);
        assertFalse(confirm.isEnabled());
        assertFalse(cancel.isEnabled());
        ConfirmEditSupport.syncButtons(confirm, cancel, true, false);
        assertFalse(confirm.isEnabled());
        assertTrue(cancel.isEnabled());
        ConfirmEditSupport.syncButtons(confirm, cancel, true, true);
        assertTrue(confirm.isEnabled());
        assertTrue(cancel.isEnabled());
    }

    @Test
    public void escapeRevertsAndCounterPaintsInsideField() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Font font = UiFonts.chinesePlain(12);
            JTextField field = new JTextField("原本的名字");
            CandyTextField.install(field, true);
            ConfirmEditSupport.attachCounter(field, 24);
            ConfirmEditSupport.setCommitted(field, "原本的名字");
            JButton cancel = ConfirmEditSupport.cancelButton(font);
            final boolean[] cancelled = {false};
            cancel.addActionListener(e -> {
                cancelled[0] = true;
                ConfirmEditSupport.revertText(field);
            });
            ConfirmEditSupport.bindEscape(field, cancel::doClick);
            field.setText("新名字");
            javax.swing.Action escape = field.getActionMap().get("confirm-edit-cancel");
            assertTrue(escape != null);
            escape.actionPerformed(new java.awt.event.ActionEvent(field, 0, "esc"));
            assertTrue(cancelled[0]);
            assertEquals("原本的名字", field.getText());
            field.setSize(new Dimension(220, 32));
            javax.swing.JPanel wrap = new javax.swing.JPanel(new BorderLayout());
            wrap.add(field, BorderLayout.CENTER);
            wrap.setSize(220, 32);
            wrap.doLayout();
            assertEquals("5/24", ConfirmEditSupport.formatCounter(field.getText(), 24));
        });
    }
}
