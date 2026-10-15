package cn.zbx1425.sowcer.util;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public class OffHeapAllocator {

    private static final MemoryUtil.MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);
    private static final AtomicLong ALLOCATED_BYTES = new AtomicLong();
    private static final AtomicLong PEAK_ALLOCATED_BYTES = new AtomicLong();

    public static long allocatedBytes() { return ALLOCATED_BYTES.get(); }

    public static long peakAllocatedBytes() { return PEAK_ALLOCATED_BYTES.get(); }

    public static ByteBuffer allocate(int size) {
        if (size < 0) throw new IllegalArgumentException("Negative native allocation size: " + size);
        long ptr = ALLOCATOR.malloc(size);
        if (ptr == 0) throw new OutOfMemoryError();
        ByteBuffer result;
        try {
            result = MemoryUtil.memByteBuffer(ptr, size);
        } catch (RuntimeException | Error ex) {
            ALLOCATOR.free(ptr);
            throw ex;
        }
        recordSizeChange(size);
        return result;
    }

    public static ByteBuffer resize(ByteBuffer buf, int byteSize) {
        // Keep the old allocation valid until every fallible preparation step has succeeded.
        ByteBuffer result = allocate(byteSize);
        try {
            MemoryUtil.memCopy(MemoryUtil.memAddress0(buf), MemoryUtil.memAddress0(result), Math.min(buf.capacity(), byteSize));
            free(buf);
            return result;
        } catch (RuntimeException | Error ex) {
            try { free(result); } catch (RuntimeException | Error cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    public static void free(ByteBuffer buf) {
        long ptr = MemoryUtil.memAddress0(buf);
        ALLOCATOR.free(ptr);
        recordSizeChange(-buf.capacity());
    }

    private static void recordSizeChange(long change) {
        long allocated = ALLOCATED_BYTES.addAndGet(change);
        long peak = PEAK_ALLOCATED_BYTES.get();
        while (allocated > peak && !PEAK_ALLOCATED_BYTES.compareAndSet(peak, allocated)) {
            peak = PEAK_ALLOCATED_BYTES.get();
        }
    }
}
