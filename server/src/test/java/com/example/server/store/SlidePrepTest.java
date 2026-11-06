package com.example.server.store;

import com.example.server.web.PhotoRoutes;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class SlidePrepTest {

    @Test
    public void classifiesByExtensionBeforeMime() {
        assertEquals(SlidePrep.Kind.IMAGE, SlidePrep.classify("a.PNG", "application/octet-stream"));
        assertEquals(SlidePrep.Kind.IMAGE, SlidePrep.classify("shot", "image/jpeg"));
        assertEquals(SlidePrep.Kind.PDF, SlidePrep.classify("週會.pdf", "application/octet-stream"));
        assertEquals(SlidePrep.Kind.PAGES, SlidePrep.classify("週會.pages", "application/zip"));
        assertEquals(SlidePrep.Kind.PPT, SlidePrep.classify("deck.PPTX", null));
        assertEquals(SlidePrep.Kind.PPT, SlidePrep.classify("old.ppt", null));
        assertEquals(SlidePrep.Kind.REJECT, SlidePrep.classify("notes.txt", "text/plain"));
    }

    @Test
    public void pdfPassesThroughAndPagesPreviewIsExtracted() throws Exception {
        byte[] pdf = samplePdf();
        SlidePrep prep = new SlidePrep(source -> {
            throw new AssertionError("PDF 與 Pages 不該呼叫轉檔");
        });

        SlidePrep.Result direct = prep.prepare("週會.pdf", pdf);
        assertTrue(direct.ok);
        assertArrayEquals(pdf, direct.pdf);

        SlidePrep.Result pages = prep.prepare("週會.pages", pagesZip(pdf));
        assertTrue(pages.message, pages.ok);
        assertArrayEquals(pdf, pages.pdf);

        SlidePrep.Result empty = prep.prepare("空白.pages", pagesZip(new byte[] {1, 2, 3}));
        assertFalse(empty.ok);
        assertEquals("這份 Pages 沒有預覽", empty.message);

        SlidePrep.Result missing = prep.prepare("沒預覽.pages", zipWith("Index/Document.iwa", new byte[] {9}));
        assertFalse(missing.ok);
        assertEquals("這份 Pages 沒有預覽", missing.message);
    }

    @Test
    public void modernPagesUsesEmbeddedPreviewImage() throws Exception {
        byte[] jpeg = new byte[] {
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xc0, 0x00, 0x0b, 0x08,
                0x00, 0x02, 0x00, 0x03, 0x03, 0x01, 0x11, 0x00, (byte) 0xff, (byte) 0xd9
        };
        SlidePrep prep = new SlidePrep(source -> {
            throw new AssertionError("預覽圖不該呼叫 PowerPoint 轉檔");
        });
        SlidePrep.Result result = prep.prepare("報告.pages", zipWith("preview.jpg", jpeg));
        assertTrue(result.message, result.ok);
        assertTrue(SlidePrep.isPdf(result.pdf));
        assertArrayEquals(new int[] {3, 2, 3}, SlidePrep.jpegInfo(jpeg));
        String pdf = new String(result.pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(pdf.contains("/Width 3"));
        assertTrue(pdf.contains("/Height 2"));
        assertTrue(pdf.contains("/Filter /DCTDecode"));
    }

    @Test
    public void powerPointUsesConverterOnceAtATime() throws Exception {
        byte[] pdf = samplePdf();
        AtomicReference<String> seen = new AtomicReference<>();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SlidePrep prep = new SlidePrep(source -> {
            seen.set(source.getFileName().toString());
            inside.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return pdf;
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<SlidePrep.Result> first = pool.submit(() -> prep.prepare("deck.pptx", new byte[] {1, 2, 3}));
            assertTrue(inside.await(5, TimeUnit.SECONDS));
            SlidePrep.Result second = prep.prepare("other.ppt", new byte[] {4});
            assertFalse(second.ok);
            assertEquals("正在轉另一份簡報，請稍後再試", second.message);
            release.countDown();
            SlidePrep.Result done = first.get(5, TimeUnit.SECONDS);
            assertTrue(done.ok);
            assertArrayEquals(pdf, done.pdf);
            assertEquals("input.pptx", seen.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void oversizedSlideIsRejectedWithoutConverting() {
        SlidePrep prep = new SlidePrep(source -> {
            throw new AssertionError("超過上限不該轉檔");
        });
        SlidePrep.Result result = prep.prepare("big.pptx", new byte[(int) PhotoStore.MAX_DOCUMENT_BYTES + 1]);
        assertFalse(result.ok);
        assertEquals("單份簡報不可超過 60 MB", result.message);
    }

    @Test
    public void pdfDownloadNameKeepsTheTitle() {
        String header = PhotoRoutes.inlinePdfDisposition("週會投影.pages");
        assertTrue(header.startsWith("inline;"));
        assertTrue(header.contains("filename*=UTF-8''"));
        assertTrue(header.contains("%E9%80%B1%E6%9C%83%E6%8A%95%E5%BD%B1.pdf"));
        assertFalse(header.contains("\r"));
        assertFalse(header.contains("\n"));
    }

    private static byte[] samplePdf() {
        return "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] pagesZip(byte[] preview) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(raw)) {
            zip.putNextEntry(new ZipEntry("Index/Document.iwa"));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("preview.pdf"));
            zip.write(new byte[] {'n', 'o'});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("QuickLook/Preview.pdf"));
            zip.write(preview);
            zip.closeEntry();
        }
        return raw.toByteArray();
    }

    private static byte[] zipWith(String name, byte[] body) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(raw)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(body);
            zip.closeEntry();
        }
        return raw.toByteArray();
    }
}
