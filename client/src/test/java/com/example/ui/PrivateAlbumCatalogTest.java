package com.example.ui;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PrivateAlbumCatalogTest {

    @Test
    public void listsBundledJpegResources() {
        List<String> names = PrivateAlbumCatalog.listImageFileNames(PrivateAlbumCatalogTest.class);
        assertFalse(names.isEmpty());
        assertTrue(names.size() >= 6);
        for (String name : names) {
            assertTrue(PrivateAlbumCatalog.isImageFileName(name));
            assertNotNull(PrivateAlbumCatalog.loadImage(PrivateAlbumCatalogTest.class, name));
        }
    }

    @Test
    public void rejectsNonImageNames() {
        assertFalse(PrivateAlbumCatalog.isImageFileName("notes.txt"));
        assertFalse(PrivateAlbumCatalog.isImageFileName(null));
        assertTrue(PrivateAlbumCatalog.isImageFileName("a.PNG"));
    }
}