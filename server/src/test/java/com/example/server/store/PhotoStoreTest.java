package com.example.server.store;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PhotoStoreTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path storageDir;
    private PhotoStore store;

    @Before
    public void setUp() throws Exception {
        storageDir = tempFolder.newFolder("photos").toPath();
        store = new PhotoStore(storageDir);
    }

    @Test
    public void memoryModeWhenStorageDirIsNull() {
        PhotoStore memStore = new PhotoStore(null);
        assertFalse(memStore.isPersistent());

        byte[] imgData = createTestJpeg(100);
        PhotoStore.UploadResult result = memStore.upload("test.jpg", "image/jpeg", imgData);
        assertTrue(result.ok);
        assertEquals(1, memStore.count());
    }

    @Test
    public void diskModeWhenStorageDirExists() {
        assertTrue(store.isPersistent());
    }

    @Test
    public void uploadStoresFileOnDisk() {
        byte[] imgData = createTestJpeg(500);
        PhotoStore.UploadResult result = store.upload("demo.jpg", "image/jpeg", imgData);
        assertTrue(result.ok);
        assertEquals("上傳成功", result.message);
        assertEquals("demo.jpg", result.photo.filename);
        assertEquals("image/jpeg", result.photo.mimeType);
        assertEquals(imgData.length, result.photo.size);

        File[] files = storageDir.toFile().listFiles((dir, name) -> name.endsWith(".jpg"));
        assertNotNull(files);
        assertEquals(1, files.length);
    }

    @Test
    public void uploadRejectsNonImage() {
        byte[] data = "hello".getBytes();
        PhotoStore.UploadResult result = store.upload("doc.txt", "text/plain", data);
        assertFalse(result.ok);
        assertEquals("僅支援圖片格式", result.message);
    }

    @Test
    public void uploadRejectsOversizedFile() {
        byte[] huge = createTestJpeg(11 * 1024 * 1024);
        PhotoStore.UploadResult result = store.upload("big.jpg", "image/jpeg", huge);
        assertFalse(result.ok);
        assertTrue(result.message.contains("10 MB"));
    }

    @Test
    public void uploadRejectsEmptyFilename() {
        byte[] data = createTestJpeg(100);
        PhotoStore.UploadResult result = store.upload("", "image/jpeg", data);
        assertFalse(result.ok);
        assertEquals("檔名不可為空", result.message);
    }

    @Test
    public void deleteRemovesFileAndEntry() {
        byte[] imgData = createTestJpeg(100);
        PhotoStore.UploadResult result = store.upload("deleteme.png", "image/png", imgData);
        assertTrue(result.ok);
        String photoId = result.photo.id;
        assertEquals(1, store.count());

        assertTrue(store.delete(photoId));
        assertEquals(0, store.count());
        assertNull(store.get(photoId));
    }

    @Test
    public void deleteAllClearsEverything() {
        store.upload("a.jpg", "image/jpeg", createTestJpeg(100));
        store.upload("b.jpg", "image/jpeg", createTestJpeg(100));
        assertEquals(2, store.count());

        int removed = store.deleteAll();
        assertEquals(2, removed);
        assertEquals(0, store.count());
    }

    @Test
    public void snapshotReturnsDataUrls() {
        byte[] imgData = createTestJpeg(50);
        store.upload("snap.gif", "image/gif", imgData);

        List<Map<String, Object>> snapshot = store.snapshot();
        assertEquals(1, snapshot.size());
        Map<String, Object> item = snapshot.get(0);
        assertEquals("snap.gif", item.get("filename"));
        assertEquals("image/gif", item.get("mimeType"));
        assertTrue(((String) item.get("dataUrl")).startsWith("data:image/gif;base64,"));
    }

    @Test
    public void persistsAcrossRestarts() throws Exception {
        byte[] imgData = createTestJpeg(200);
        PhotoStore.UploadResult result = store.upload("persist.jpg", "image/jpeg", imgData);
        assertTrue(result.ok);
        String photoId = result.photo.id;
        assertEquals(1, store.count());

        PhotoStore newStore = new PhotoStore(storageDir);
        assertEquals(1, newStore.count());
        PhotoStore.Photo loaded = newStore.get(photoId);
        assertNotNull(loaded);
        assertEquals("persist.jpg", loaded.filename);
        assertEquals("image/jpeg", loaded.mimeType);

        String base64 = loaded.base64Data();
        assertNotNull(base64);
        assertFalse(base64.isEmpty());
    }

    @Test
    public void indexJsonExistsAfterUpload() {
        store.upload("indexed.jpg", "image/jpeg", createTestJpeg(100));
        Path indexFile = storageDir.resolve("index.json");
        assertTrue(Files.isRegularFile(indexFile));
    }

    @Test
    public void maxPhotosEnforced() {
        for (int i = 0; i < 50; i++) {
            PhotoStore.UploadResult result = store.upload("photo" + i + ".jpg", "image/jpeg", createTestJpeg(10));
            assertTrue("第 " + i + " 張應上傳成功", result.ok);
        }
        PhotoStore.UploadResult overflow = store.upload("overflow.jpg", "image/jpeg", createTestJpeg(10));
        assertFalse(overflow.ok);
        assertTrue(overflow.message.contains("上限"));
    }

    private static byte[] createTestJpeg(int size) {
        byte[] data = new byte[Math.max(size, 10)];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        data[3] = (byte) 0xE0;
        data[data.length - 2] = (byte) 0xFF;
        data[data.length - 1] = (byte) 0xD9;
        return data;
    }
}
