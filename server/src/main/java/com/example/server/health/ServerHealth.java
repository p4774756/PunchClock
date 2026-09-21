package com.example.server.health;

import com.example.server.store.FileOfferStore;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 後台顯示用的 JVM／主機負載快照。
 */
public final class ServerHealth {

    private final long startedAtMs = System.currentTimeMillis();

    public Map<String, Object> snapshot(FileOfferStore files) {
        Map<String, Object> map = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryMx = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        long heapUsed = memoryMx.getHeapMemoryUsage().getUsed();
        long heapMax = memoryMx.getHeapMemoryUsage().getMax();
        if (heapMax <= 0) {
            heapMax = runtime.maxMemory();
        }
        long heapCommitted = memoryMx.getHeapMemoryUsage().getCommitted();

        map.put("uptimeMs", Math.max(0L, System.currentTimeMillis() - startedAtMs));
        map.put("jvmUptimeMs", Math.max(0L, runtimeMx.getUptime()));
        map.put("cpuCount", runtime.availableProcessors());
        map.put("osName", os.getName() == null ? "" : os.getName());
        map.put("osArch", os.getArch() == null ? "" : os.getArch());
        map.put("loadAverage", os.getSystemLoadAverage());
        map.put("heapUsedBytes", heapUsed);
        map.put("heapCommittedBytes", heapCommitted);
        map.put("heapMaxBytes", heapMax);
        map.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());

        applySunOsMetrics(os, map);
        applyDiskMetrics(map);

        if (files != null) {
            map.put("fileOfferCount", files.size());
            map.put("fileOfferBytes", files.totalBytes());
            map.put("fileOfferMaxBytes", FileOfferStore.MAX_TOTAL_BYTES);
            map.put("fileOfferTtlMs", FileOfferStore.TTL_MS);
        }
        return map;
    }

    /**
     * 量測 {@code java.io.tmpdir} 所在檔案系統（multipart／未來落地暫存多寫這裡）。
     */
    static void applyDiskMetrics(Map<String, Object> map) {
        Path path = tempDirPath();
        map.put("diskPath", path.toString());
        try {
            FileStore store = Files.getFileStore(path);
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            long unallocated = store.getUnallocatedSpace();
            map.put("diskTotalBytes", total > 0 ? total : null);
            map.put("diskUsableBytes", usable >= 0 ? usable : null);
            map.put("diskUnallocatedBytes", unallocated >= 0 ? unallocated : null);
        } catch (Exception ex) {
            map.put("diskTotalBytes", null);
            map.put("diskUsableBytes", null);
            map.put("diskUnallocatedBytes", null);
        }
    }

    static Path tempDirPath() {
        String tmp = System.getProperty("java.io.tmpdir", ".");
        return Paths.get(tmp).toAbsolutePath().normalize();
    }

    private static void applySunOsMetrics(OperatingSystemMXBean os, Map<String, Object> map) {
        if (!(os instanceof com.sun.management.OperatingSystemMXBean)) {
            map.put("processCpuPercent", null);
            map.put("systemCpuPercent", null);
            map.put("osTotalMemoryBytes", null);
            map.put("osFreeMemoryBytes", null);
            return;
        }
        com.sun.management.OperatingSystemMXBean sunOs = (com.sun.management.OperatingSystemMXBean) os;
        map.put("processCpuPercent", percentOrNull(sunOs.getProcessCpuLoad()));
        map.put("systemCpuPercent", percentOrNull(sunOs.getSystemCpuLoad()));
        long total = sunOs.getTotalPhysicalMemorySize();
        long free = sunOs.getFreePhysicalMemorySize();
        map.put("osTotalMemoryBytes", total > 0 ? total : null);
        map.put("osFreeMemoryBytes", free >= 0 ? free : null);
    }

    static Double percentOrNull(double load) {
        if (Double.isNaN(load) || load < 0) {
            return null;
        }
        return Math.round(load * 1000.0) / 10.0;
    }
}
