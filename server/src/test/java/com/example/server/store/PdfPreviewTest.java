package com.example.server.store;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PdfPreviewTest {

    @Test
    public void firstPageBecomesAJpeg() throws Exception {
        Path pdf = Files.createTempFile("punchclock-preview-", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(pdf.toFile());
        }
        try {
            byte[] jpeg = PdfPreview.firstPageJpeg(pdf);
            assertNotNull(jpeg);
            assertTrue(jpeg.length > 100);
            assertEquals((byte) 0xff, jpeg[0]);
            assertEquals((byte) 0xd8, jpeg[1]);
        } finally {
            Files.deleteIfExists(pdf);
        }
    }
}
