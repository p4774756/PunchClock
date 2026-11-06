package com.example.server.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 用 LibreOffice 無介面把 .ppt / .pptx 轉成 PDF。同時只應有一份在跑。 */
public final class LibreOfficePdfConverter implements OfficePdfConverter {

    static final long TIMEOUT_SECONDS = 60L;

    @Override
    public byte[] convert(Path sourceFile) throws IOException, InterruptedException {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            throw new IOException("找不到要轉的簡報");
        }
        Path work = sourceFile.getParent();
        if (work == null) {
            throw new IOException("轉檔失敗");
        }
        Path profile = work.resolve("profile");
        Files.createDirectories(profile);
        String installation = profile.toAbsolutePath().toUri().toString();
        if (!installation.endsWith("/")) {
            installation = installation + "/";
        }
        List<String> command = new ArrayList<>();
        command.add(sofficeCommand());
        command.add("--headless");
        command.add("--nologo");
        command.add("--nofirststartwizard");
        command.add("--norestore");
        command.add("-env:UserInstallation=" + installation);
        command.add("--convert-to");
        command.add("pdf");
        command.add("--outdir");
        command.add(work.toAbsolutePath().toString());
        command.add(sourceFile.toAbsolutePath().toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("HOME", work.toAbsolutePath().toString());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = null;
        try {
            process = builder.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IOException("轉檔超過 60 秒，已中止");
            }
            if (process.exitValue() != 0) {
                throw new IOException("PowerPoint 轉成 PDF 失敗");
            }
        } catch (IOException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (message.contains("Cannot run program") || message.contains("error=2")) {
                throw new IOException("這台伺服器轉不了 PowerPoint");
            }
            throw ex;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        String filename = sourceFile.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String pdfName = (dot > 0 ? filename.substring(0, dot) : filename) + ".pdf";
        Path pdf = work.resolve(pdfName);
        if (!Files.isRegularFile(pdf)) {
            throw new IOException("轉檔沒有產生 PDF");
        }
        return Files.readAllBytes(pdf);
    }

    static String sofficeCommand() {
        String env = System.getenv("SOFFICE_PATH");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        Path mac = Path.of("/Applications/LibreOffice.app/Contents/MacOS/soffice");
        if (Files.isExecutable(mac)) {
            return mac.toString();
        }
        return "soffice";
    }
}
