package com.example.ui;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MacGraphicsWorkaroundTest {

    @Test
    public void enablesMetalOnMac() {
        Properties props = new Properties();
        props.setProperty("os.name", "Mac OS X");
        assertTrue(MacGraphicsWorkaround.apply(props));
        assertEquals("true", props.getProperty("sun.java2d.metal"));
    }

    @Test
    public void keepsUserChoice() {
        Properties props = new Properties();
        props.setProperty("os.name", "Mac OS X");
        props.setProperty("sun.java2d.metal", "false");
        assertFalse(MacGraphicsWorkaround.apply(props));
        assertEquals("false", props.getProperty("sun.java2d.metal"));
    }

    @Test
    public void leavesOtherPlatformsAlone() {
        Properties windows = new Properties();
        windows.setProperty("os.name", "Windows 11");
        assertFalse(MacGraphicsWorkaround.apply(windows));
        assertNull(windows.getProperty("sun.java2d.metal"));

        Properties linux = new Properties();
        linux.setProperty("os.name", "Linux");
        assertFalse(MacGraphicsWorkaround.apply(linux));
    }
}
