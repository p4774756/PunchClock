package com.example.ui;

import com.example.model.WorkSlot;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlotNameApplyUiTest {

    @Test
    public void applyEnabledWhenDraftDiffers() {
        assertTrue(SlotNameApplyUi.isApplyEnabled(
                WorkSlot.Kind.WORK_IN, "每日排程2", WorkSlot.WORK_IN_NAME));
        assertFalse(SlotNameApplyUi.isApplyEnabled(
                WorkSlot.Kind.WORK_IN, WorkSlot.WORK_IN_NAME, WorkSlot.WORK_IN_NAME));
    }
}