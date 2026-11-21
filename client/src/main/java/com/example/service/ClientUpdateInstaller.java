package com.example.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Locale;

/**
 * 下載完成後寫一個小腳本：等這個行程結束，再覆蓋 jar 並重新啟動。
 * 正在跑的 JVM 不能可靠地換掉自己的 jar。
 */
public final class ClientUpdateInstaller {

  /** 安裝版啟動器寫入：內建 jar 的絕對路徑。 */
  public static final String BUNDLED_JAR_PROPERTY = "sklbaby.bundled.jar";

  private ClientUpdateInstaller() {}

  public static Path updateDir() {
    if (isPackagedInstall()) {
      return userDataRoot().resolve("updates");
    }
    return Path.of(System.getProperty("user.home", "."), ".punchclock", "updates");
  }

  public static Path userDataRoot() {
    return Path.of(System.getProperty("user.home", "."), ".sklbaby");
  }

  /** 安裝版自動更新的 jar 目標（不寫回安裝目錄）。 */
  public static Path userAppJar() {
    return userDataRoot().resolve("app").resolve("SKLbaby.jar");
  }

  public static boolean isPackagedInstall() {
    String bundled = System.getProperty(BUNDLED_JAR_PROPERTY, "").trim();
    return !bundled.isEmpty();
  }

  /** jpackage 映像內的 jar；非安裝版為 null。 */
  public static Path bundledJar() {
    if (jpackageAppPath() != null) {
      return resolveJarBesideLauncher();
    }
    String raw = System.getProperty(BUNDLED_JAR_PROPERTY, "").trim();
    if (raw.isEmpty()) {
      return null;
    }
    Path path = Path.of(raw);
    return Files.isRegularFile(path) ? path : null;
  }

  private static Path jpackageAppPath() {
    String appPath = System.getProperty("jpackage.app-path", "").trim();
    if (appPath.isEmpty()) {
      return null;
    }
    Path path = Path.of(appPath);
    return Files.isDirectory(path) ? path : null;
  }

  private static Path resolveJarBesideLauncher() {
    try {
      CodeSource source = ClientUpdateInstaller.class.getProtectionDomain().getCodeSource();
      if (source == null || source.getLocation() == null) {
        return null;
      }
      Path path = Path.of(source.getLocation().toURI());
      if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
        return path.toAbsolutePath().normalize();
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  /** 不是用 jar 啟動（例如 IDE）時回 null。 */
  public static Path runningJar() {
    try {
      CodeSource source = ClientUpdateInstaller.class.getProtectionDomain().getCodeSource();
      if (source == null || source.getLocation() == null) {
        return null;
      }
      Path path = Path.of(source.getLocation().toURI());
      if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
        return path;
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

    /**
     * 不是用 jar 啟動（例如 IDE）時，更新後的 jar 放這裡：下載後改用 {@code java -jar} 啟動它，
     * 之後就能像一般 jar 一樣自動更新。
     */
    public static Path defaultJar() {
        return Path.of(System.getProperty("user.home", "."), ".punchclock", "SKLbaby.jar");
    }

    /** 更新要覆蓋的 jar：jar 啟動時是自己，IDE 啟動時是 {@link #defaultJar()}。 */
    public static Path installTarget() {
        if (isPackagedInstall()) {
            return userAppJar();
        }
        Path running = runningJar();
        return running != null ? running : defaultJar();
    }

    public static void relaunch(Path downloadedJar, Path targetJar) throws IOException {
        if (downloadedJar == null || !Files.isRegularFile(downloadedJar)) {
            throw new IOException("下載的 jar 不存在");
        }
        if (targetJar == null) {
            throw new IOException("找不到要更新的 jar");
        }
        Path parent = targetJar.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
            if (!Files.isWritable(parent)) {
                throw new IOException("無法寫入 " + parent);
            }
        }
        Path dir = updateDir();
        Files.createDirectories(dir);
        boolean windows = isWindows();
        Path script = dir.resolve(windows ? "apply-update.bat" : "apply-update.sh");
        Path log = dir.resolve("apply-update.log");
        String pid = Long.toString(ProcessHandle.current().pid());
        String javaBin = ProcessHandle.current().info().command().orElse(defaultJava(windows));
        Files.writeString(script, windows ? windowsScript() : unixScript(), StandardCharsets.UTF_8);
        if (!windows) {
            script.toFile().setExecutable(true);
        }
        ProcessBuilder pb = windows
                ? new ProcessBuilder("cmd", "/c", "start", "\"\"",
                script.toString(), pid, downloadedJar.toString(), targetJar.toString(), javaBin)
                : new ProcessBuilder("/bin/sh", "-c",
                "/usr/bin/nohup /bin/sh " + shQuote(script.toString())
                        + " " + shQuote(pid)
                        + " " + shQuote(downloadedJar.toString())
                        + " " + shQuote(targetJar.toString())
                        + " " + shQuote(javaBin)
                        + " >> " + shQuote(log.toString()) + " 2>&1 &");
        pb.directory(dir.toFile());
        pb.start();
    }

    static String unixScript() {
        return "#!/bin/sh\n"
                + "set -u\n"
                + "pid=\"$1\"\n"
                + "newjar=\"$2\"\n"
                + "target=\"$3\"\n"
                + "java=\"$4\"\n"
                + "log=\"$(dirname \"$0\")/apply-update.log\"\n"
                + "echo \"wait $pid\" >> \"$log\"\n"
                + "n=0\n"
                + "while kill -0 \"$pid\" 2>/dev/null; do\n"
                + "  n=$((n + 1))\n"
                + "  if [ \"$n\" -gt 150 ]; then\n"
                + "    echo \"timeout waiting for $pid\" >> \"$log\"\n"
                + "    exit 1\n"
                + "  fi\n"
                + "  sleep 0.2\n"
                + "done\n"
                + "if command -v xattr >/dev/null 2>&1; then\n"
                + "  xattr -d com.apple.quarantine \"$newjar\" 2>/dev/null || true\n"
                + "fi\n"
                + "mv -f \"$newjar\" \"$target\" || {\n"
                + "  echo \"mv failed\" >> \"$log\"\n"
                + "  exit 1\n"
                + "}\n"
                + "if command -v xattr >/dev/null 2>&1; then\n"
                + "  xattr -d com.apple.quarantine \"$target\" 2>/dev/null || true\n"
                + "fi\n"
                + "echo \"relaunch $target\" >> \"$log\"\n"
                + "nohup \"$java\" -jar \"$target\" >> \"$log\" 2>&1 &\n";
    }

    static String windowsScript() {
        return "@echo off\r\n"
                + "set PID=%~1\r\n"
                + "set NEW=%~2\r\n"
                + "set TARGET=%~3\r\n"
                + "set JAVA=%~4\r\n"
                + ":loop\r\n"
                + "tasklist /FI \"PID eq %PID%\" 2>nul | find \"%PID%\" >nul\r\n"
                + "if not errorlevel 1 (\r\n"
                + "  timeout /t 1 /nobreak >nul\r\n"
                + "  goto loop\r\n"
                + ")\r\n"
                + "move /Y \"%NEW%\" \"%TARGET%\"\r\n"
                + "start \"\" \"%JAVA%\" -jar \"%TARGET%\"\r\n";
    }

    static String shQuote(String value) {
        String raw = value == null ? "" : value;
        return "'" + raw.replace("'", "'\\''") + "'";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String defaultJava(boolean windows) {
        return Path.of(System.getProperty("java.home", ""), "bin", windows ? "java.exe" : "java").toString();
    }
}
