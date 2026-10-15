package cn.zbx1425.sowcerext.model;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

/** Owns staged native data until one upload or cancellation; the caller owns the uploaded result. */
public interface PreparedUpload<T> extends Supplier<T>, AutoCloseable {

    long byteSize();

    /** Discards staged native data. Never closes a successfully returned GPU object. */
    @Override
    void close();

    static <T> PreparedUpload<T> of(long byteSize, Supplier<T> upload, Runnable discard) {
        return new OwnedPreparedUpload<>(byteSize, upload, discard);
    }
}

final class OwnedPreparedUpload<T> implements PreparedUpload<T> {
    private final long byteSize;
    private Supplier<T> upload;
    private Runnable discard;
    private T result;
    private boolean finished;
    private boolean succeeded;

    OwnedPreparedUpload(long byteSize, Supplier<T> upload, Runnable discard) {
        if (byteSize < 0) throw new IllegalArgumentException("Negative upload size");
        this.byteSize = byteSize;
        this.upload = Objects.requireNonNull(upload);
        this.discard = Objects.requireNonNull(discard);
    }

    @Override
    public long byteSize() { return byteSize; }

    @Override
    public synchronized T get() {
        if (finished) {
            if (succeeded) return result;
            throw new CancellationException("Prepared upload was cancelled or failed");
        }
        finished = true;
        Supplier<T> action = upload;
        upload = null;
        try {
            result = action.get();
            succeeded = true;
            return result;
        } catch (RuntimeException | Error ex) {
            try { release(); } catch (RuntimeException | Error cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        } finally {
            release();
        }
    }

    @Override
    public synchronized void close() {
        finished = true;
        upload = null;
        release();
    }

    private void release() {
        Runnable action = discard;
        discard = null;
        if (action != null) action.run();
    }
}
