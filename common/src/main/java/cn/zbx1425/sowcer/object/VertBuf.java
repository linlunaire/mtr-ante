package cn.zbx1425.sowcer.object;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/** Owned immutable upload data. Minecraft performs backend GPU uploads when the captured frame is drawn. */
public class VertBuf implements Closeable {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    public static final int USAGE_STATIC_DRAW = 0x88E4, USAGE_DYNAMIC_DRAW = 0x88E8, USAGE_STREAM_DRAW = 0x88E0;
    public volatile int id = NEXT_ID.getAndIncrement();
    private ByteBuffer data;
    private int references = 1;
    private boolean ownerClosed;
    private long revision;

    public void upload(ByteBuffer buffer, int usage) { upload(buffer, buffer.capacity(), usage); }

    public synchronized void upload(ByteBuffer buffer, int size, int usage) {
        if (ownerClosed) throw new IllegalStateException("Upload to a closed buffer");
        if (size < 0 || size > buffer.capacity()) throw new IllegalArgumentException("Invalid buffer byte count: " + size);
        if (usage != USAGE_STATIC_DRAW && usage != USAGE_DYNAMIC_DRAW && usage != USAGE_STREAM_DRAW) throw new IllegalArgumentException("Invalid buffer usage");
        final ByteBuffer input = buffer.duplicate().order(buffer.order());
        input.clear().limit(size);
        final ByteBuffer copy = ByteBuffer.allocate(size).order(buffer.order());
        copy.put(input).flip();
        data = copy.asReadOnlyBuffer().order(copy.order());
        revision++;
    }

    /** Pair the bytes with their upload revision under the same lock. */
    synchronized Upload snapshotUpload() { return new Upload(snapshot(), revision); }

    record Upload(ByteBuffer data, long revision) { }

    public synchronized ByteBuffer snapshot() {
        if (references == 0 || data == null) throw new IllegalStateException("Buffer is closed or not uploaded");
        return data.asReadOnlyBuffer().order(data.order());
    }

    synchronized void retain() {
        if (references == 0) throw new IllegalStateException("Retain of a released buffer");
        references++;
    }

    synchronized void release() {
        if (references == 0) throw new IllegalStateException("Buffer released twice");
        if (--references == 0) { data = null; id = 0; }
    }

    @Override public synchronized void close() {
        if (!ownerClosed) { ownerClosed = true; release(); }
    }
}
