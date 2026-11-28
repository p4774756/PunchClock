package com.example.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PrivateAlbumNavigationTest {

    @Test
    public void previousIndexStopsAtZero() {
        assertEquals(0, PrivateAlbumNavigation.previousIndex(0, 8));
        assertEquals(2, PrivateAlbumNavigation.previousIndex(3, 8));
    }

    @Test
    public void nextIndexStopsAtLast() {
        assertEquals(7, PrivateAlbumNavigation.nextIndex(7, 8));
        assertEquals(1, PrivateAlbumNavigation.nextIndex(0, 8));
    }

    @Test
    public void clampIndexGuardsEmptyAndBounds() {
        assertEquals(-1, PrivateAlbumNavigation.clampIndex(0, 0));
        assertEquals(0, PrivateAlbumNavigation.clampIndex(-2, 5));
        assertEquals(4, PrivateAlbumNavigation.clampIndex(99, 5));
    }
}