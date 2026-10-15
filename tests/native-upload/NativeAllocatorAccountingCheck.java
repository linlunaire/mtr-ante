package cn.zbx1425.mtrsteamloco.compatibility;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Exercises actual allocator control flow and accounting; instruments only MemoryUtil's native endpoints. */
public final class NativeAllocatorAccountingCheck {
    private static final String HOOK = "cn/zbx1425/mtrsteamloco/compatibility/NativeAllocatorAccountingCheck";
    private static final Map<Long, Long> livePointers = new HashMap<>();
    private static final IdentityHashMap<Buffer, Long> addresses = new IdentityHashMap<>();
    private static final Map<Long, ByteBuffer> pointerBuffers = new HashMap<>();
    private static long nextPointer;
    private static boolean failMalloc, failCopy, failView;
    private static int assertions;

    public static void premain(String unused, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                    ProtectionDomain domain, byte[] bytes) {
                if (!"cn/zbx1425/sowcer/util/OffHeapAllocator".equals(name)) return null;
                ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
                for (var method : node.methods) for (var instruction : method.instructions) {
                    if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals("org/lwjgl/system/MemoryUtil")) continue;
                    String replacement = switch (call.name) {
                        case "getAllocator" -> "allocator";
                        case "memByteBuffer" -> "view";
                        case "memAddress0" -> "address";
                        case "memCopy" -> "copy";
                        default -> null;
                    };
                    if (replacement != null) { call.owner = HOOK; call.name = replacement; }
                }
                ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
            }
        });
    }

    public static MemoryUtil.MemoryAllocator allocator(boolean ignored) {
        return (MemoryUtil.MemoryAllocator)Proxy.newProxyInstance(NativeAllocatorAccountingCheck.class.getClassLoader(),
                new Class<?>[]{MemoryUtil.MemoryAllocator.class}, (proxy, method, args) -> {
                    synchronized (livePointers) {
                        return switch (method.getName()) {
                            case "malloc" -> {
                                if (failMalloc) { failMalloc = false; yield 0L; }
                                long ptr = ++nextPointer; livePointers.put(ptr, ((Number)args[0]).longValue()); yield ptr;
                            }
                            case "free" -> {
                                long ptr = ((Number)args[0]).longValue(); require(livePointers.remove(ptr) != null, "double free"); pointerBuffers.remove(ptr); yield null;
                            }
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    }
                });
    }
    public static ByteBuffer view(long ptr, int bytes) {
        synchronized (livePointers) {
            if (failView) { failView = false; throw new OutOfMemoryError("injected ByteBuffer wrapper failure"); }
            require(livePointers.get(ptr) == bytes, "view size or pointer mismatch");
            ByteBuffer buffer = ByteBuffer.allocate(bytes); addresses.put(buffer, ptr); pointerBuffers.put(ptr, buffer); return buffer;
        }
    }
    public static long address(Buffer buffer) {
        synchronized (livePointers) { return addresses.get(buffer); }
    }
    public static void copy(long source, long destination, long byteSize) {
        synchronized (livePointers) {
            if (failCopy) { failCopy = false; throw new IllegalStateException("injected native copy failure"); }
            require(livePointers.containsKey(source) && livePointers.containsKey(destination), "copy used consumed allocation");
            ByteBuffer input = pointerBuffers.get(source).duplicate(), output = pointerBuffers.get(destination).duplicate();
            input.clear(); output.clear(); input.limit((int)byteSize); output.put(input);
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> allocator = Class.forName("cn.zbx1425.sowcer.util.OffHeapAllocator");
        Method allocate = allocator.getMethod("allocate", int.class), resize = allocator.getMethod("resize", ByteBuffer.class, int.class),
                free = allocator.getMethod("free", ByteBuffer.class), bytes = allocator.getMethod("allocatedBytes"), peak = allocator.getMethod("peakAllocatedBytes");
        require(number(bytes) == 0, "nonzero initial allocator balance");
        ByteBuffer first = (ByteBuffer)allocate.invoke(null, 32), second = (ByteBuffer)allocate.invoke(null, 16);
        require(number(bytes) == 48 && number(peak) == 48, "allocate accounting incorrect");
        first.putInt(0, 0x12345678); first.position(7);
        first = (ByteBuffer)resize.invoke(null, first, 80);
        require(number(bytes) == 96 && number(peak) == 128, "grow accounting or old+new peak incorrect");
        require(first.getInt(0) == 0x12345678 && first.position() == 0, "grow changed native bytes or result cursor");
        first = (ByteBuffer)resize.invoke(null, first, 8);
        require(number(bytes) == 24 && number(peak) == 128 && first.getInt(0) == 0x12345678, "shrink accounting or bytes incorrect");
        failMalloc = true; expectFailure(resize, first, 128);
        require(number(bytes) == 24 && number(peak) == 128 && livePointers.size() == 2, "failed resize allocation changed ownership");
        failMalloc = true; expectFailure(allocate, 1000);
        require(number(bytes) == 24 && livePointers.size() == 2, "failed malloc changed ownership");
        failView = true; expectFailure(allocate, 1000);
        require(number(bytes) == 24 && livePointers.size() == 2, "failed allocate wrapper leaked pointer");
        failView = true; expectFailure(resize, first, 1000);
        require(number(bytes) == 24 && livePointers.size() == 2 && first.getInt(0) == 0x12345678, "failed resize wrapper consumed old allocation");
        failCopy = true; expectFailure(resize, first, 1000);
        require(number(bytes) == 24 && livePointers.size() == 2 && first.getInt(0) == 0x12345678, "failed resize copy consumed old allocation");
        free.invoke(null, first); free.invoke(null, second);
        require(number(bytes) == 0 && livePointers.isEmpty(), "caller cleanup after failed resize double-freed or leaked");
        expectFailure(allocate, -1);
        require(number(bytes) == 0 && livePointers.isEmpty(), "negative allocation reached malloc");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                try { for (int j = 0; j < 100; j++) { ByteBuffer buffer = (ByteBuffer)allocate.invoke(null, 63); free.invoke(null, buffer); } }
                catch (Throwable ex) { failure.set(ex); }
            }); workers[i].start();
        }
        for (Thread worker : workers) worker.join();
        if (failure.get() != null) throw new AssertionError("concurrent allocator accounting", failure.get());
        require(number(bytes) == 0 && livePointers.isEmpty() && number(peak) >= 128, "concurrent balance incorrect");
        System.out.println("PASS: actual OffHeapAllocator accounting and failure ownership, " + assertions + " assertions; four workers x 100 allocations");
    }
    private static long number(Method method) throws Exception { return ((Number)method.invoke(null)).longValue(); }
    private static void expectFailure(Method method, Object... args) throws Exception {
        try { method.invoke(null, args); } catch (InvocationTargetException ex) {
            require(ex.getCause() instanceof OutOfMemoryError || ex.getCause() instanceof IllegalArgumentException || ex.getCause() instanceof IllegalStateException, "unexpected failure " + ex.getCause()); return;
        }
        throw new AssertionError("injected native failure was not observed");
    }
    private static synchronized void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
