package com.example.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * 傳檔進度：串流複製與帶進度的 HTTP BodyPublisher。
 */
public final class TransferIo {

    private static final int BUFFER_SIZE = 64 * 1024;
    /** 約每 64KB 或完成時回報一次，上傳進度較好跟手。 */
    private static final long REPORT_EVERY = 64L * 1024L;

    private TransferIo() {
    }

    @FunctionalInterface
    public interface Progress {
        void onProgress(long transferred, long total);
    }

    public static void copy(InputStream in, OutputStream out, long total, Progress progress)
            throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        long transferred = 0L;
        long lastReported = -REPORT_EVERY;
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
            transferred += n;
            if (progress != null && (transferred - lastReported >= REPORT_EVERY || n == 0)) {
                lastReported = transferred;
                progress.onProgress(transferred, total);
            }
        }
        if (progress != null) {
            progress.onProgress(transferred, total > 0 ? total : transferred);
        }
    }

    public static HttpRequest.BodyPublisher ofFile(Path path, Progress progress) throws IOException {
        long size = Files.size(path);
        return new HttpRequest.BodyPublisher() {
            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                if (subscriber == null) {
                    throw new NullPointerException("subscriber");
                }
                subscriber.onSubscribe(new FileSubscription(path, size, progress, subscriber));
            }
        };
    }

    public static Progress throttle(Progress progress) {
        if (progress == null) {
            return (t, total) -> {
            };
        }
        return new Progress() {
            private long lastReported = -REPORT_EVERY;

            @Override
            public void onProgress(long transferred, long total) {
                if (transferred - lastReported >= REPORT_EVERY || (total > 0 && transferred >= total)) {
                    lastReported = transferred;
                    progress.onProgress(transferred, total);
                }
            }
        };
    }

    /** 測試用：把 BiConsumer 包成 Progress。 */
    public static Progress from(BiConsumer<Long, Long> consumer) {
        if (consumer == null) {
            return (t, total) -> {
            };
        }
        return consumer::accept;
    }

    private static final class FileSubscription implements Flow.Subscription {
        private final Path path;
        private final long total;
        private final Progress progress;
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final Object lock = new Object();
        private InputStream in;
        private long transferred;
        private long demand;
        private boolean completed;

        FileSubscription(Path path, long total, Progress progress,
                         Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.path = path;
            this.total = total;
            this.progress = progress;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                fail(new IllegalArgumentException("request must be positive"));
                return;
            }
            synchronized (lock) {
                if (cancelled.get() || completed) {
                    return;
                }
                demand += n;
                if (demand < 0) {
                    demand = Long.MAX_VALUE;
                }
                drain();
            }
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            closeQuietly();
        }

        private void drain() {
            while (demand > 0 && !cancelled.get() && !completed) {
                try {
                    if (in == null) {
                        in = Files.newInputStream(path);
                        if (progress != null) {
                            progress.onProgress(0L, total);
                        }
                    }
                    byte[] buf = new byte[BUFFER_SIZE];
                    int read = in.read(buf);
                    if (read < 0) {
                        completed = true;
                        closeQuietly();
                        if (progress != null) {
                            progress.onProgress(transferred, total);
                        }
                        subscriber.onComplete();
                        return;
                    }
                    demand--;
                    transferred += read;
                    if (progress != null) {
                        progress.onProgress(transferred, total);
                    }
                    subscriber.onNext(ByteBuffer.wrap(buf, 0, read));
                } catch (IOException ex) {
                    fail(ex);
                    return;
                } catch (RuntimeException ex) {
                    fail(ex);
                    return;
                }
            }
        }

        private void fail(Throwable error) {
            if (completed || cancelled.getAndSet(true)) {
                return;
            }
            completed = true;
            closeQuietly();
            subscriber.onError(error);
        }

        private void closeQuietly() {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // best-effort
                }
                in = null;
            }
        }
    }
}
