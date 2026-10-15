package cn.zbx1425.mtrsteamloco.render.rail;

import cn.zbx1425.sowcerext.model.PreparedUpload;
import cn.zbx1425.sowcer.util.OffHeapAllocator;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded admission and ownership from background preparation through render-thread upload. */
final class RailBuildScheduler {
    static final int MAX_BUILD_BACKLOG = 32;
    static final int MAX_PENDING_UPLOADS = 32;
    static final long NATIVE_BUDGET = 64L * 1024 * 1024;
    static final int WORKERS = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    static final Logger LOGGER = LoggerFactory.getLogger("YLTE Rail Upload");
    private static final boolean LOG_DIAGNOSTICS = Boolean.getBoolean("ylte.railDiagnostics");
    private static long lastDiagnosticsNanos;
    static final BoundedUploadQueue UPLOADS = new BoundedUploadQueue();
    static final ThreadPoolExecutor EXECUTOR;
    private static long reservedBytes, buildingReservedBytes, stagedBytes, pendingBytes, peakReservedBytes, oversizedJobs;
    private static int reservations;

    static {
        AtomicInteger ids = new AtomicInteger();
        EXECUTOR = new ThreadPoolExecutor(WORKERS, WORKERS, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(MAX_BUILD_BACKLOG), action -> {
                    Thread thread = new Thread(action, "YLTE rail build " + ids.incrementAndGet());
                    thread.setDaemon(true); return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        EXECUTOR.allowCoreThreadTimeOut(true);
    }

    /** An oversized single chunk is allowed only when every other reservation has drained. */
    static synchronized Reservation reserve(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("Negative rail upload estimate");
        if (reservations >= MAX_PENDING_UPLOADS || (reservations != 0 &&
                (bytes > NATIVE_BUDGET || reservedBytes > NATIVE_BUDGET - bytes))) return null;
        Reservation reservation = new Reservation(bytes);
        reservedBytes = Math.addExact(reservedBytes, bytes);
        buildingReservedBytes = Math.addExact(buildingReservedBytes, bytes);
        reservations++;
        peakReservedBytes = Math.max(peakReservedBytes, reservedBytes);
        if (bytes > NATIVE_BUDGET) oversizedJobs++;
        return reservation;
    }

    static synchronized String diagnostics() {
        return "workers=" + EXECUTOR.getActiveCount() + "/" + WORKERS
                + ", builds=" + EXECUTOR.getQueue().size() + "/" + MAX_BUILD_BACKLOG
                + ", uploads=" + UPLOADS.size() + "/" + MAX_PENDING_UPLOADS
                + ", nativeReserved=" + reservedBytes + "/" + NATIVE_BUDGET
                + ", buildingReserved=" + buildingReservedBytes + ", nativeStaged=" + stagedBytes
                + ", pendingBytes=" + pendingBytes
                + ", nativeAllocated=" + OffHeapAllocator.allocatedBytes()
                + ", nativeAllocatedPeak=" + OffHeapAllocator.peakAllocatedBytes()
                + ", nativePeak=" + peakReservedBytes + ", oversizedJobs=" + oversizedJobs;
    }

    /** Off by default; enables bounded, coordinate-free measurements during a real-world A/B run. */
    static void logDiagnostics() {
        if (!LOG_DIAGNOSTICS) return;
        long now = System.nanoTime();
        if (lastDiagnosticsNanos == 0 || now - lastDiagnosticsNanos >= 5_000_000_000L) {
            lastDiagnosticsNanos = now;
            LOGGER.info("Rail async pipeline: {}", diagnostics());
        }
    }

    static final class Reservation implements AutoCloseable {
        final long bytes;
        private boolean closed;
        private boolean prepared, queued;
        private long actualBytes;
        Reservation(long bytes) { this.bytes = bytes; }
        void prepared(long actual) {
            synchronized (RailBuildScheduler.class) {
                if (closed || prepared) return;
                prepared = true; actualBytes = actual;
                buildingReservedBytes -= bytes; stagedBytes += actual;
            }
        }
        void queued() {
            synchronized (RailBuildScheduler.class) {
                if (closed || queued) return;
                queued = true; pendingBytes += actualBytes;
            }
        }
        void dequeued() {
            synchronized (RailBuildScheduler.class) {
                if (!queued) return;
                queued = false; pendingBytes -= actualBytes;
            }
        }
        @Override public void close() {
            synchronized (RailBuildScheduler.class) {
                if (closed) return;
                closed = true; reservedBytes -= bytes; reservations--;
                if (prepared) stagedBytes -= actualBytes; else buildingReservedBytes -= bytes;
                if (queued) { queued = false; pendingBytes -= actualBytes; }
            }
        }
    }

    static final class Task<T> implements Runnable, AutoCloseable {
        private final RailChunkBase owner;
        private final long generation;
        private final Reservation reservation;
        private Supplier<PreparedUpload<T>> build;
        private Consumer<PreparedUpload<T>> upload;
        private PreparedUpload<T> prepared;
        private Thread worker;
        private boolean cancelled, finished, uploading;
        private final UploadCall renderTask = new UploadCall();

        Task(RailChunkBase owner, long generation, Reservation reservation,
                Supplier<PreparedUpload<T>> build, Consumer<PreparedUpload<T>> upload) {
            this.owner = owner; this.generation = generation; this.reservation = reservation;
            this.build = build; this.upload = upload;
        }

        private final class UploadCall implements Runnable, AutoCloseable {
            @Override public void run() { runUpload(); }
            @Override public void close() { Task.this.close(); }
        }

        @Override public void run() {
            synchronized (this) {
                if (finished || cancelled) return;
                worker = Thread.currentThread();
            }
            Throwable failure = null;
            boolean queued = false;
            PreparedUpload<T> result = null;
            try {
                owner.checkBuildCurrent(generation);
                result = build.get();
                if (result.byteSize() > reservation.bytes)
                    throw new IllegalStateException("Rail upload exceeded its pre-allocation estimate");
                synchronized (this) {
                    owner.checkBuildCurrent(generation);
                    if (cancelled) throw new CancellationException();
                    prepared = result; result = null;
                    reservation.prepared(prepared.byteSize());
                    queued = UPLOADS.offer(renderTask);
                    if (queued) reservation.queued();
                    if (!queued) throw new CancellationException("Rail upload queue full");
                }
            } catch (RuntimeException | Error ex) { failure = ex; }
            finally {
                boolean discard;
                synchronized (this) {
                    worker = null; build = null;
                    // close() can remove a newly enqueued upload before this worker exits.
                    discard = !queued || (cancelled && !uploading);
                    if (cancelled && failure == null) failure = new CancellationException();
                }
                if (result != null) {
                    try { result.close(); } catch (RuntimeException | Error cleanup) {
                        if (failure != null) failure.addSuppressed(cleanup); else failure = cleanup;
                    }
                }
                if (discard) finish(failure);
            }
        }

        private void runUpload() {
            synchronized (this) {
                if (finished || cancelled) return;
                uploading = true;
                reservation.dequeued();
            }
            Throwable failure = null;
            try {
                synchronized (owner.lifecycleLock) {
                    owner.checkBuildCurrent(generation);
                    upload.accept(prepared);
                }
            } catch (RuntimeException | Error ex) { failure = ex; }
            finally { finish(failure); }
        }

        @Override public void close() {
            boolean release;
            synchronized (this) {
                if (finished) return;
                cancelled = true;
                if (worker != null) worker.interrupt();
                release = worker == null && !uploading;
            }
            EXECUTOR.remove(this);
            UPLOADS.remove(renderTask);
            if (release) finish(new CancellationException());
        }

        private void finish(Throwable failure) {
            PreparedUpload<T> owned;
            synchronized (this) {
                if (finished) return;
                finished = true; owned = prepared; prepared = null; build = null; upload = null;
            }
            try {
                if (owned != null) owned.close();
            } catch (RuntimeException | Error cleanup) {
                if (failure != null) failure.addSuppressed(cleanup); else failure = cleanup;
            } finally {
                reservation.close();
                owner.buildFinished(this, generation, failure);
            }
        }
    }

    /** Retains the legacy ConcurrentLinkedQueue field descriptor while bounding ordinary offer/add. */
    static final class BoundedUploadQueue extends ConcurrentLinkedQueue<Runnable> {
        @Override public synchronized boolean offer(Runnable task) {
            if (size() >= MAX_PENDING_UPLOADS) return false;
            return super.offer(task);
        }
        @Override public boolean add(Runnable task) {
            if (!offer(task)) throw new IllegalStateException("Rail upload queue full");
            return true;
        }
        @Override public boolean addAll(Collection<? extends Runnable> tasks) {
            boolean changed = false;
            for (Runnable task : tasks) { add(task); changed = true; }
            return changed;
        }
        @Override public void clear() {
            Runnable task;
            while ((task = poll()) != null) {
                if (task instanceof AutoCloseable owned) {
                    try { owned.close(); }
                    catch (Exception | Error ex) { LOGGER.warn("Failed discarding rail upload", ex); }
                }
            }
        }
    }
}
