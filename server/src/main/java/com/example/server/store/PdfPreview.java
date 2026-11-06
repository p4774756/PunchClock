package com.example.server.store;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/** 把 PDF 第一頁畫成縮小的 JPEG，給飲品牆卡片用。 */
public final class PdfPreview {

    static final int TARGET_WIDTH = 480;

    private PdfPreview() {
    }

    public static byte[] firstPageJpeg(Path pdf) {
        if (pdf == null) {
            return null;
        }
        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            if (doc.getNumberOfPages() < 1) {
                return null;
            }
            PDPage page = doc.getPage(0);
            PDRectangle box = page.getCropBox();
            float width = box == null ? 0f : box.getWidth();
            if (width < 1f) {
                width = page.getMediaBox().getWidth();
            }
            if (width < 1f) {
                width = TARGET_WIDTH;
            }
            float scale = Math.min(1.5f, TARGET_WIDTH / width);
            BufferedImage image = new PDFRenderer(doc).renderImage(0, scale, ImageType.RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "jpg", out)) {
                return null;
            }
            return out.toByteArray();
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }
}
