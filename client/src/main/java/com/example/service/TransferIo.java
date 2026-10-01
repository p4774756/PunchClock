package com.example.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

    /** 使用者取消傳檔。 */
    public static final class CancelledException extends IOException {
        public CancelledException() {
            super("已取消");
        }
    }

    public static void copy(InputStream in, OutputStream out, long total, Progress progress)
            throws IOException {
        copy(in, out, total, progress, null);
    }

    public static void copy(InputStream in, OutputStream out, long total, Progress progress,
                            TransferCancel cancel) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        long transferred = 0L;
        long lastReported = -REPORT_EVERY;
        int n;
        while (true) {
            if (cancel != null) {
                cancel.throwIfCancelled();
            }
            n = in.read(buf);
            if (n < 0) {
                break;
            }
            if (cancel != null) {
                cancel.throwIfCancelled();
            }
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
        return ofFile(path, progress, null);
    }

    /** 取消時以 {@link CancelledException} 結束 body，HttpClient 會中止這次請求。 */
    public static HttpRequest.BodyPublisher ofFile(Path path, Progress progress, TransferCancel cancel)
            throws IOException {
        return ofFileRange(path, 0L, Files.size(path), progress, cancel);
    }

    /** 只送出檔案中 [offset, offset + length) 這段（分段上傳用）；進度以這段為單位回報。 */
    public static HttpRequest.BodyPublisher ofFileRange(Path path, long offset, long length,
                                                        Progress progress, TransferCancel cancel) {
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("offset/length must not be negative");
        }
        return new HttpRequest.BodyPublisher() {
            @Override
            public long contentLength() {
                return length;
            }

            @Override
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                if (subscriber == null) {
                    throw new NullPointerException("subscriber");
                }
                FileSubscription subscription =
                        new FileSubscription(path, offset, length, progress, cancel, subscriber);
                subscriber.onSubscribe(subscription);
                if (cancel != null) {
                    cancel.onCancel(subscription::abort);
                }
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
                if (transferred < lastReported
                        || transferred - lastReported >= REPORT_EVERY
                        || (total > 0 && transferred >= total)) {
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
        private final long offset;
        private final long total;
        private final Progress progress;
        private final TransferCancel cancel;
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final Object lock = new Object();
        private InputStream in;
        private long transferred;
        private long demand;
        private boolean completed;

        FileSubscription(Path path, long offset, long total, Progress progress, TransferCancel cancel,
                         Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.path = path;
            this.offset = offset;
            this.total = total;
            this.progress = progress;
            this.cancel = cancel;
            this.subscriber = subscriber;
        }

        void abort() {
            synchronized (lock) {
                fail(new CancelledException());
            }
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
                if (cancel != null && cancel.isCancelled()) {
                    fail(new CancelledException());
                    return;
                }
                try {
                    if (in == null) {
                        SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ);
                        channel.position(offset);
                        in = Channels.newInputStream(channel);
                        if (progress != null) {
                            progress.onProgress(0L, total);
                        }
                    }
                    long remaining = total - transferred;
                    byte[] buf = new byte[(int) Math.min(BUFFER_SIZE, Math.max(remaining, 1L))];
                    int read = remaining <= 0 ? -1 : in.read(buf);
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
