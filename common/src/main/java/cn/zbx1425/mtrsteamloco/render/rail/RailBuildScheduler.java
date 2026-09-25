package cn.zbx1425.mtrsteamloco.render.rail;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounds both background work and completed geometry waiting for the render thread. */
final class RailBuildScheduler {
    static final int WORKER_COUNT = 2;
    static final int MAX_IN_FLIGHT = 4;

    private final Executor executor;
    private final Semaphore capacity;
    private final ConcurrentLinkedQueue<Completion> completed = new ConcurrentLinkedQueue<>();

    RailBuildScheduler() {
        this(newWorkerPool(), MAX_IN_FLIGHT);
    }

    RailBuildScheduler(Executor executor, int maxInFlight) {
        if (maxInFlight < 1) throw new IllegalArgumentException("maxInFlight must be positive");
        this.executor = executor;
        capacity = new Semaphore(maxInFlight);
    }

    static ExecutorService newWorkerPool() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(WORKER_COUNT, task -> {
            Thread thread = new Thread(task, "ANTE rail builder " + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    // prepare and the completion callbacks run on the render thread. Saturation does not run prepare.
    boolean trySchedule(Runnable prepare, Supplier<Upload> build, BooleanSupplier current,
                        Runnable finished, Consumer<Throwable> failed) {
        if (!capacity.tryAcquire()) return false;
        AtomicBoolean started = new AtomicBoolean();
        try {
            prepare.run();
            executor.execute(() -> {
                started.set(true);
                Upload upload = null;
                Throwable error = null;
                try {
                    if (current.getAsBoolean()) upload = Objects.requireNonNull(build.get());
                } catch (RuntimeException | Error failure) {
                    error = failure;
                }
                synchronized (completed) {
                    // close/discard uses this same lock: a result cannot slip into the queue after close.
                    if (current.getAsBoolean()) {
                        completed.offer(new Completion(upload, error, current, finished, failed));
                        return;
                    }
                }
                // Staging storage only; never upload or invoke render-thread callbacks here.
                try {
                    if (upload != null) upload.close();
                } finally {
                    capacity.release();
                }
            });
        } catch (RuntimeException | Error error) {
            // An inline executor can propagate worker cleanup failure after its slot was released.
            if (started.get()) throw error;
            finish(new Completion(null, error, current, finished, failed));
        }
        return true;
    }

    boolean uploadOne() {
        Completion completion;
        synchronized (completed) {
            completion = completed.poll();
        }
        if (completion == null) return false;
        finish(completion);
        return true;
    }

    void discardStale() {
        synchronized (completed) {
            Throwable failure = null;
            for (var iterator = completed.iterator(); iterator.hasNext();) {
                Completion completion = iterator.next();
                if (!completion.current.getAsBoolean()) {
                    iterator.remove();
                    try {
                        finish(completion);
                    } catch (RuntimeException | Error error) {
                        if (failure == null) failure = error;
                        else if (failure != error) failure.addSuppressed(error);
                    }
                }
            }
            if (failure instanceof RuntimeException error) throw error;
            if (failure instanceof Error error) throw error;
        }
    }

    private void finish(Completion completion) {
        Throwable failure = completion.error;
        try {
            try {
                if (failure == null && completion.upload != null && completion.current.getAsBoolean()) {
                    completion.upload.upload();
                }
            } catch (RuntimeException | Error error) {
                failure = error;
            } finally {
                if (completion.upload != null) {
                    try {
                        completion.upload.close();
                    } catch (RuntimeException | Error error) {
                        if (failure == null) failure = error;
                        else if (failure != error) failure.addSuppressed(error);
                    }
                }
            }
            if (failure != null && completion.current.getAsBoolean()) completion.failed.accept(failure);
        } finally {
            try {
                completion.finished.run();
            } finally {
                capacity.release();
            }
        }
    }

    interface Upload extends AutoCloseable {
        void upload();
        // Releases staging storage only. May run on a worker when the owning chunk was closed.
        @Override default void close() {}
    }

    private record Completion(Upload upload, Throwable error, BooleanSupplier current,
                              Runnable finished, Consumer<Throwable> failed) {}
}
