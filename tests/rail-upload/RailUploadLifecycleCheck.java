package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.data.RailModelProperties;
import cn.zbx1425.mtrsteamloco.render.rail.MeshBuildingRailChunk;
import cn.zbx1425.mtrsteamloco.render.rail.InstancedRailChunk;
import cn.zbx1425.mtrsteamloco.render.rail.BakedRail;
import cn.zbx1425.mtrsteamloco.render.rail.RailRenderDispatcher;
import cn.zbx1425.mtrsteamloco.data.RailModelRegistry;
import cn.zbx1425.mtrsteamloco.render.rail.RailChunkBase;
import cn.zbx1425.sowcerext.model.RawModel;
import cn.zbx1425.sowcer.model.Model;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.object.InstanceBuf;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import sun.misc.Unsafe;
import mtr.data.Rail;
import mtr.data.RailType;
import mtr.data.TransportMode;
import net.minecraft.world.phys.Vec3;
import cn.zbx1425.sowcerext.model.RawMesh;
import cn.zbx1425.sowcerext.model.Vertex;
import cn.zbx1425.sowcerext.model.Face;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.batch.MaterialProp;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Runs real rail build/close/upload methods. Only model registry/config initialization is replaced. */
public final class RailUploadLifecycleCheck {
    private static final String HOOK = "cn/zbx1425/mtrsteamloco/compatibility/RailUploadLifecycleCheck";
    private static int failures, assertions;
    private static final IdentityHashMap<ByteBuffer, Integer> nativeLive = new IdentityHashMap<>();
    private static final Set<Integer> gpuLive = new HashSet<>();
    private static final HashMap<Integer, byte[]> gpuBytes = new HashMap<>();
    private static int nativeAllocations, gpuCreates;
    private static boolean failAllocation, failUpload;
    private static long nativePeak;
    private static volatile int positionReads;
    private static volatile boolean boundPositionReads, triangleModel;
    private static volatile float interval = 1;
    private static final Unsafe UNSAFE;
    static {
        try { Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); UNSAFE = (Unsafe) field.get(null); }
        catch (ReflectiveOperationException ex) { throw new ExceptionInInitializerError(ex); }
    }

    public static RailModelProperties property() {
        RailModelProperties prop = new RailModelProperties("test", null, null, 1, 0, null, "test");
        prop.repeatInterval = interval;
        prop.rawModel = new RawModel(); prop.uploadedModel = new Model();
        if (triangleModel) {
            RawMesh mesh = new RawMesh(new MaterialProp());
            for (int i = 0; i < 3; i++) mesh.vertices.add(new Vertex(new Vector3f(i, 0, 0), new Vector3f(0, 1, 0)));
            mesh.faces.add(new Face(new int[]{0, 1, 2})); prop.rawModel.append(mesh);
        }
        return prop;
    }
    public static Vec3 railPosition(double distance) {
        int reads = ++positionReads;
        if (boundPositionReads && reads > 8) throw new AssertionError("non-terminating rail repeat loop capped at 8 position queries");
        return new Vec3(distance, 0, 0);
    }
    public static synchronized ByteBuffer allocate(int bytes) {
        if (failAllocation) { failAllocation = false; throw new OutOfMemoryError("injected rail allocation failure"); }
        nativeAllocations++;
        ByteBuffer buffer = ByteBuffer.allocate(bytes).order(ByteOrder.nativeOrder()); nativeLive.put(buffer, bytes);
        nativePeak = Math.max(nativePeak, allocatedBytes()); return buffer;
    }
    public static synchronized void free(ByteBuffer buffer) {
        require(nativeLive.remove(buffer) != null, "each native rail buffer freed exactly once");
    }
    public static synchronized long allocatedBytes() { return nativeLive.values().stream().mapToLong(Integer::longValue).sum(); }
    public static synchronized long peakAllocatedBytes() { return nativePeak; }
    public static synchronized int create() { int id = ++gpuCreates; gpuLive.add(id); return id; }
    public static synchronized void upload(int id, ByteBuffer bytes) {
        if (failUpload) { failUpload = false; throw new IllegalStateException("injected rail GPU upload failure"); }
        require(nativeLive.containsKey(bytes), "GPU upload receives live owned native data");
        byte[] payload = new byte[bytes.capacity()]; bytes.duplicate().clear().get(payload); gpuBytes.put(id, payload);
    }
    public static synchronized void delete(int id) { if (id != 0) require(gpuLive.remove(id), "each rail GPU buffer deleted exactly once"); }
    private static Object field(Class<?> type, String name, Object owner) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    public static synchronized void require(boolean ok, String message) {
        assertions++; if (!ok) { failures++; System.err.println("FAIL " + message); }
    }
    private static BakedRail rail(long chunk, Matrix4f transform, int color) throws Exception {
        BakedRail rail = (BakedRail) UNSAFE.allocateInstance(BakedRail.class);
        rail.coveredChunks = new HashMap<>(); rail.color = color;
        rail.coveredChunks.put(chunk, new ArrayList<>(java.util.List.of(transform))); return rail;
    }
    private static void pending(ConcurrentLinkedQueue<Runnable> queue) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (queue.isEmpty() && System.nanoTime() < deadline) Thread.sleep(1);
        require(!queue.isEmpty(), "background rail reaches owned upload queue");
    }
    private static void idle(RailChunkBase chunk) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (chunk.bufferBuilding && System.nanoTime() < deadline) Thread.sleep(1);
        require(!chunk.bufferBuilding, "failure/cancellation never leaves bufferBuilding stuck");
    }
    private static void retryNow(RailChunkBase chunk) throws Exception {
        Field field = RailChunkBase.class.getDeclaredField("retryAfterNanos"); field.setAccessible(true); field.setLong(chunk, 0);
    }
    private static void drainedNative() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((allocatedBytes() != 0 || !RailChunkBase.getAsyncDiagnostics().contains("nativeReserved=0/"))
                && System.nanoTime() < deadline) Thread.sleep(1);
        require(allocatedBytes() == 0 && RailChunkBase.getAsyncDiagnostics().contains("nativeReserved=0/"),
                "cancelled native workers eventually release bytes and reservations");
    }
    private static Rail shortRail(double length) throws Exception {
        Rail rail = (Rail) UNSAFE.allocateInstance(Rail.class);
        Field end = Rail.class.getDeclaredField("tEnd1"); end.setAccessible(true); end.setDouble(rail, length);
        Field type = Rail.class.getDeclaredField("railType"); type.setAccessible(true); type.set(rail, RailType.WOODEN);
        Field transport = Rail.class.getDeclaredField("transportMode"); transport.setAccessible(true); transport.set(rail, TransportMode.TRAIN);
        return rail;
    }
    private static void degenerateRails(ConcurrentLinkedQueue<Runnable> queue) throws Exception {
        triangleModel = true; boundPositionReads = true;
        RailModelRegistry.ELEMENTS = new HashMap<>(java.util.Map.of("test", property()));
        for (double length : new double[]{0, 0.01, Double.POSITIVE_INFINITY, Double.NaN}) {
            positionReads = 0; interval = 1;
            try {
                BakedRail baked = new BakedRail(shortRail(length));
                require(baked.coveredChunks.isEmpty() && positionReads == 0, "invalid/zero BakedRail length produces empty geometry: " + length);
            } catch (AssertionError oldInfiniteLoop) { require(false, "BakedRail invalid length loop did not terminate: " + length); }
        }
        for (float invalidInterval : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            positionReads = 0; interval = invalidInterval;
            try {
                BakedRail baked = new BakedRail(shortRail(10));
                require(baked.coveredChunks.isEmpty() && positionReads == 0, "invalid BakedRail repeat interval produces empty geometry: " + invalidInterval);
            } catch (AssertionError oldInfiniteLoop) { require(false, "BakedRail invalid interval loop did not terminate: " + invalidInterval); }
        }
        cn.zbx1425.mtrsteamloco.ClientConfig.enableRailDeform = true; interval = 1;
        Level world = (ClientLevel) UNSAFE.allocateInstance(ClientLevel.class);
        for (double length : new double[]{0.0001, 0, Double.POSITIVE_INFINITY, Double.NaN}) {
            positionReads = 0;
            var constructor = MeshBuildingRailChunk.class.getDeclaredConstructor(Long.class, String.class); constructor.setAccessible(true);
            MeshBuildingRailChunk chunk = constructor.newInstance(0L, "test");
            BakedRail baked = rail(0, new Matrix4f(), 0);
            Field value = BakedRail.class.getDeclaredField("rail"); value.setAccessible(true); value.set(baked, shortRail(length));
            chunk.addRail(baked); int allocations = nativeAllocations;
            chunk.rebuildBuffer(world);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (chunk.bufferBuilding && queue.isEmpty() && System.nanoTime() < deadline) Thread.sleep(1);
            RailChunkBase.upload(); idle(chunk);
            require(chunk.bufferBuilt && !chunk.isDirty && positionReads == 0 && nativeAllocations == allocations,
                    "invalid/zero deform length completes empty without native memory: " + length);
            chunk.close(); drainedNative();
        }
        for (float invalidInterval : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            positionReads = 0; interval = invalidInterval;
            var constructor = MeshBuildingRailChunk.class.getDeclaredConstructor(Long.class, String.class); constructor.setAccessible(true);
            MeshBuildingRailChunk chunk = constructor.newInstance(0L, "test");
            BakedRail baked = rail(0, new Matrix4f(), 0);
            Field value = BakedRail.class.getDeclaredField("rail"); value.setAccessible(true); value.set(baked, shortRail(10));
            chunk.addRail(baked); int allocations = nativeAllocations;
            chunk.rebuildBuffer(world); pending(queue); RailChunkBase.upload(); idle(chunk);
            require(chunk.bufferBuilt && !chunk.isDirty && positionReads == 0 && nativeAllocations == allocations,
                    "invalid deform interval completes empty without native memory: " + invalidInterval);
            chunk.close(); drainedNative();
        }
        boundPositionReads = false; interval = 1; positionReads = 0;
        BakedRail normal = new BakedRail(shortRail(10));
        require(normal.coveredChunks.values().stream().mapToInt(ArrayList::size).sum() == 10 && positionReads == 11,
                "positive finite BakedRail preserves normal ten-piece geometry and old tolerance");
        var constructor = MeshBuildingRailChunk.class.getDeclaredConstructor(Long.class, String.class); constructor.setAccessible(true);
        MeshBuildingRailChunk normalChunk = constructor.newInstance(0L, "test"); normalChunk.addRail(normal);
        positionReads = 0; int allocations = nativeAllocations;
        normalChunk.rebuildBuffer(world); pending(queue);
        require(positionReads > 8 && nativeAllocations > allocations && allocatedBytes() > 0,
                "positive finite deform still creates real staged triangle geometry within its estimate");
        normalChunk.close(); drainedNative();
        triangleModel = false; boundPositionReads = false; interval = 1;
        cn.zbx1425.mtrsteamloco.ClientConfig.enableRailDeform = false;
    }
    private static void instances(ConcurrentLinkedQueue<Runnable> queue) throws Exception {
        Level world = (ClientLevel) UNSAFE.allocateInstance(ClientLevel.class);
        int beforeCreates = gpuCreates;
        InstancedRailChunk chunk = new InstancedRailChunk(0L, "test");
        require(gpuCreates == beforeCreates, "instanced chunk defers GPU allocation until admitted upload");
        Matrix4f transform = Matrix4f.translation(2, 5, 9);
        BakedRail rail = rail(0L, transform, 0x11223344); chunk.addRail(rail);
        chunk.rebuildBuffer(world); pending(queue);
        require(allocatedBytes() == 72, "one instance stages exactly color/light/matrix bytes");
        require(RailChunkBase.getAsyncDiagnostics().contains("pendingBytes=72"), "diagnostics reports real staged queue bytes");
        transform.translate(100, 100, 100);
        RailChunkBase.upload(); idle(chunk);
        InstanceBuf first = (InstanceBuf) field(InstancedRailChunk.class, "instanceBuf", chunk);
        byte[] bytes = gpuBytes.get(first.id); ByteBuffer payload = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        require(payload.getInt(0) == 0x11223344, "instanced color layout unchanged");
        require(payload.getInt(4) == (7 << 20 | 7 << 4), "instanced light layout unchanged");
        require(payload.getFloat(8 + 12 * 4) == 2 && payload.getFloat(8 + 13 * 4) == 5
                && payload.getFloat(8 + 14 * 4) == 9, "worker consumes immutable transform snapshot");
        require(chunk.bufferBuilt && first.size == 1 && allocatedBytes() == 0, "successful upload publishes one instance and frees native data");
        failUpload = true; chunk.isDirty = true; chunk.rebuildBuffer(world); pending(queue); RailChunkBase.upload(); idle(chunk);
        require(field(InstancedRailChunk.class, "instanceBuf", chunk) == first && gpuLive.contains(first.id),
                "failed replacement preserves previous live GPU buffer");
        require(chunk.isDirty && allocatedBytes() == 0 && gpuLive.size() == 1, "failed GPU upload releases native/new GPU resources and remains dirty");
        int attempts = nativeAllocations; chunk.rebuildBuffer(world);
        require(nativeAllocations == attempts && !chunk.bufferBuilding, "failed build backs off rather than retrying every frame");
        retryNow(chunk); failAllocation = true; chunk.rebuildBuffer(world); idle(chunk);
        require(field(InstancedRailChunk.class, "instanceBuf", chunk) == first && allocatedBytes() == 0,
                "malloc failure retains old GPU buffer and clears build state");
        retryNow(chunk); chunk.rebuildBuffer(world); pending(queue); chunk.close();
        drainedNative();
        require(allocatedBytes() == 0 && queue.isEmpty() && gpuLive.isEmpty(), "unload cancels pending instance upload and closes prior GPU buffer");
        chunk.close(); RailChunkBase.upload();
        require(field(InstancedRailChunk.class, "instanceBuf", chunk) == null && !chunk.bufferBuilding,
                "repeated close cannot reinstall or double delete instance resources");
        for (int i = 0; i < 100; i++) {
            InstancedRailChunk teleport = new InstancedRailChunk(0L, "test");
            teleport.addRail(rail(0L, new Matrix4f(), i)); teleport.rebuildBuffer(world); pending(queue); teleport.close();
            drainedNative();
        }
        require(allocatedBytes() == 0 && gpuLive.isEmpty() && queue.isEmpty(), "100 unload-before-upload cycles leave no native/GPU tasks");
        InstancedRailChunk empty = new InstancedRailChunk(0L, "test");
        empty.rebuildBuffer(null); pending(queue); RailChunkBase.upload(); idle(empty); empty.close();
        require(empty.bufferBuilt && allocatedBytes() == 0, "empty chunk completes without zero-byte malloc or hanging state");
        InstancedRailChunk disconnected = new InstancedRailChunk(0L, "test");
        disconnected.addRail(rail(0L, new Matrix4f(), 1)); disconnected.rebuildBuffer(world); pending(queue);
        RailModelRegistry.ELEMENTS = new HashMap<>(java.util.Map.of("test", property()));
        RailRenderDispatcher dispatcher = new RailRenderDispatcher();
        @SuppressWarnings("unchecked") var maps = (HashMap<String, HashMap<Long, RailChunkBase>>) field(RailRenderDispatcher.class, "railChunkMap", dispatcher);
        maps.put("test", new HashMap<>(java.util.Map.of(0L, disconnected)));
        @SuppressWarnings("unchecked") var list = (java.util.List<RailChunkBase>) field(RailRenderDispatcher.class, "railChunkList", dispatcher);
        list.add(disconnected); dispatcher.clearRail(); drainedNative(); RailChunkBase.upload();
        require(list.isEmpty() && queue.isEmpty() && allocatedBytes() == 0 && !disconnected.bufferBuilding,
                "actual dispatcher world/resource clear cancels pending native/GPU work");
    }
    public static void main(String[] args) throws Exception {
        ExecutorService executor = (ExecutorService) field(RailChunkBase.class, "EXECUTOR", null);
        @SuppressWarnings("unchecked") ConcurrentLinkedQueue<Runnable> queue =
                (ConcurrentLinkedQueue<Runnable>) field(RailChunkBase.class, "UPLOAD_QUEUE", null);
        try {
            var constructor = MeshBuildingRailChunk.class.getDeclaredConstructor(Long.class, String.class);
            constructor.setAccessible(true);
            MeshBuildingRailChunk chunk = constructor.newInstance(0L, "test");
            chunk.rebuildBuffer(null);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (queue.isEmpty() && System.nanoTime() < deadline) Thread.sleep(1);
            require(!queue.isEmpty(), "real build reaches pending upload");
            chunk.close();
            RailChunkBase.upload();
            require(field(MeshBuildingRailChunk.class, "uploadedCombinedModel", chunk) == null,
                    "closed chunk must not install GPU model from its pending task");
            require(!chunk.bufferBuilding, "closed chunk must finish/cancel its build state");
            instances(queue);
            cn.zbx1425.mtrsteamloco.render.rail.RailBuildBudgetCheck.run();
            degenerateRails(queue);
            queue.clear();
            int accepted = 0;
            for (int i = 0; i < 48; i++) if (queue.offer(() -> {})) accepted++;
            require(accepted <= 32 && queue.size() <= 32, "pending upload count bounded at 32, actual=" + accepted);
            queue.clear();
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(8);
            for (int i = 0; i < 48; i++) {
                try { executor.execute(() -> { started.countDown(); try { release.await(); }
                    catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); } }); }
                catch (RejectedExecutionException expectedAtCapacity) { }
            }
            started.await(200, TimeUnit.MILLISECONDS);
            require(executor instanceof ThreadPoolExecutor && ((ThreadPoolExecutor) executor).getMaximumPoolSize() <= 4,
                    "build workers bounded at 4");
            if (executor instanceof ThreadPoolExecutor pool) {
                require(pool.getLargestPoolSize() <= 4, "live worker peak bounded, actual=" + pool.getLargestPoolSize());
                require(pool.getQueue().size() <= 32, "build backlog bounded");
            }
            release.countDown();
        } finally { queue.clear(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS); }
        System.out.println("Rail upload lifecycle: " + assertions + " assertions, " + failures + " failures");
        if (failures != 0) throw new AssertionError("Rail upload lifecycle regressions: " + failures);
    }
}
