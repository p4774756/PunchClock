package com.example.server.store;

import java.io.IOException;
import java.nio.file.Path;

/** 把已經寫好的 PowerPoint 檔轉成 PDF 位元組。測試可換上假的實作。 */
public interface OfficePdfConverter {

    byte[] convert(Path sourceFile) throws IOException, InterruptedException;
}
