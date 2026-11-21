package com.example.ui;

import org.junit.Test;

import java.awt.Image;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class AppIconImagesTest {

    @Test
    public void loadsIconsHeadless() {
        List<Image> icons = AppIconImages.loadAll();
        assertFalse(icons.isEmpty());
        assertNotNull(AppIconImages.primary());
    }
}