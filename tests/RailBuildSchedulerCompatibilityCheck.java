package cn.zbx1425.mtrsteamloco.render.rail;

import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises the real scheduler and extracted production chunk methods without starting a game. */
public final class RailBuildSchedulerCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        checkScale();
        checkLifetime();
        checkFailures();
        checkWorkers();
        checkCloseDuringBuild();
        checkChunkMethods(Path.of(args[0]));
        System.out.println("PASS: 1000 rail builds; at most 4 admitted including queued uploads, 2 workers; saturation, failures, close/reload and native-buffer cleanup");
    }

    private static void checkScale() {
        ManualExecutor worker = new ManualExecutor();
        RailBuildScheduler scheduler = new RailBuildScheduler(worker, 4);
        int[] prepared = {0}, uploaded = {0}, discarded = {0}, finished = {0};
        int admitted = 0;
        for (int frame = 0; finished[0] < 1000; frame++) {
            int before = admitted;
            while (admitted < 1000 && scheduler.trySchedule(() -> prepared[0]++, () -> new RailBuildScheduler.Upload() {
                @Override public void upload() { uploaded[0]++; }
                @Override public void close() { discarded[0]++; }
            }, () -> true, () -> finished[0]++, error -> { throw new AssertionError(error); })) admitted++;
            require(admitted - finished[0] <= 4, "In-flight cap exceeded");
            require(prepared[0] == admitted, "Rejected work ran prepare");
            worker.runAll();
            if (admitted < 1000) require(!scheduler.trySchedule(() -> { throw new AssertionError("Completed work lost its slot before upload"); }, () -> () -> {}, () -> true, () -> {}, error -> { throw new AssertionError(error); }), "Completed work did not retain admission");
            int prior = finished[0];
            require(scheduler.uploadOne() && finished[0] == prior + 1, "One frame did not process exactly one result");
            require(frame < 1001 && admitted >= before, "Work did not progress");
        }
        require(uploaded[0] == 1000 && discarded[0] == 1000 && !scheduler.uploadOne(), "Work was lost or duplicated");
    }

    private static void checkLifetime() {
        ManualExecutor worker = new ManualExecutor();
        RailBuildScheduler scheduler = new RailBuildScheduler(worker, 1);
        AtomicBoolean current = new AtomicBoolean(true);
        int[] built = {0}, uploads = {0}, freed = {0};
        java.util.function.Supplier<RailBuildScheduler.Upload> build = () -> {
            built[0]++;
            return new RailBuildScheduler.Upload() {
                @Override public void upload() { uploads[0]++; }
                @Override public void close() { freed[0]++; }
            };
        };
        require(scheduler.trySchedule(() -> {}, build, current::get, () -> {}, error -> { throw new AssertionError(error); }), "First build rejected");
        worker.runAll();
        current.set(false);
        scheduler.discardStale();
        require(uploads[0] == 0 && freed[0] == 1, "Closed chunk uploaded or leaked its completed result");
        require(!scheduler.uploadOne(), "Closed completed result remained queued");
        current.set(true);
        scheduler.trySchedule(() -> {}, build, current::get, () -> {}, error -> { throw new AssertionError(error); });
        current.set(false);
        scheduler.discardStale();
        worker.runAll();
        require(!scheduler.uploadOne(), "Closed queued worker still published a result");
        require(built[0] == 1 && freed[0] == 1, "Already closed chunk still built geometry");
        current.set(true);
        scheduler.trySchedule(() -> {}, () -> {
            RailBuildScheduler.Upload result = build.get();
            current.set(false);
            scheduler.discardStale(); // close while the worker owns staging, before publication
            return result;
        }, current::get, () -> { throw new AssertionError("Worker ran render-thread finished callback after close"); }, error -> { throw new AssertionError(error); });
        worker.runAll();
        require(uploads[0] == 0 && freed[0] == 2 && !scheduler.uploadOne(), "Close-during-build leaked or published staging");
        // A reload replaces closed chunk identities; new work can use the released slot.
        require(scheduler.trySchedule(() -> {}, build, () -> true, () -> {}, error -> { throw new AssertionError(error); }), "Reload did not recover admission");
        worker.runAll();
        scheduler.uploadOne();
        require(uploads[0] == 1 && freed[0] == 3, "Reload result was not uploaded exactly once");
    }

    private static void checkFailures() {
        for (int failureAt = 0; failureAt < 6; failureAt++) {
            final int failure = failureAt;
            ManualExecutor worker = new ManualExecutor();
            RailBuildScheduler scheduler = new RailBuildScheduler(worker, 1);
            int[] finished = {0}, freed = {0}, errors = {0};
            try {
                scheduler.trySchedule(() -> { if (failure == 0) throw new IllegalStateException("prepare"); }, () -> {
                    if (failure == 1) throw new IllegalStateException("build");
                    return new RailBuildScheduler.Upload() {
                        @Override public void upload() { if (failure == 2 || failure == 4) throw new IllegalStateException("upload"); }
                        @Override public void close() { freed[0]++; if (failure == 3) throw new IllegalStateException("close"); }
                    };
                }, () -> true, () -> { finished[0]++; if (failure == 5) throw new IllegalStateException("finished callback"); }, error -> {
                    errors[0]++; if (failure == 4) throw new IllegalStateException("failure callback");
                });
                worker.runAll();
                scheduler.uploadOne();
            } catch (IllegalStateException expected) {
                require(failure >= 4, "Unexpected escaping failure");
            }
            require(finished[0] == 1 && freed[0] == (failure >= 2 ? 1 : 0), "Failure lost finish/disposal callback: " + failure);
            require(errors[0] == (failure < 5 ? 1 : 0), "Failure not reported exactly once");
            require(scheduler.trySchedule(() -> {}, () -> () -> {}, () -> true, () -> {}, error -> {}), "Failure leaked admission: " + failure);
            worker.runAll();
            scheduler.uploadOne();
        }
        int[] rejected = {0};
        RailBuildScheduler rejecting = new RailBuildScheduler(task -> { throw new java.util.concurrent.RejectedExecutionException(); }, 1);
        for (int i = 0; i < 3; i++) require(rejecting.trySchedule(() -> {}, () -> () -> {}, () -> true, () -> rejected[0]++, error -> {}), "Executor rejection leaked admission");
        require(rejected[0] == 3, "Rejected tasks did not finish");

        RailBuildScheduler inline = new RailBuildScheduler(Runnable::run, 1);
        AtomicBoolean current = new AtomicBoolean(true);
        try {
            inline.trySchedule(() -> {}, () -> {
                current.set(false);
                return new RailBuildScheduler.Upload() {
                    @Override public void upload() { throw new AssertionError("Stale upload"); }
                    @Override public void close() { throw new IllegalStateException("stale staging cleanup"); }
                };
            }, current::get, () -> { throw new AssertionError("Stale worker callback"); }, error -> {});
            throw new AssertionError("Inline cleanup failure was lost");
        } catch (IllegalStateException expected) {}
        require(inline.trySchedule(() -> {}, () -> () -> {}, () -> true, () -> {}, error -> {}), "Inline cleanup leaked its slot");
        require(!inline.trySchedule(() -> {}, () -> () -> {}, () -> true, () -> {}, error -> {}), "Inline cleanup released its slot twice");
        inline.uploadOne();
    }

    private static void checkWorkers() throws Exception {
        var pool = RailBuildScheduler.newWorkerPool();
        CountDownLatch entered = new CountDownLatch(2), unblock = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger(), finished = new AtomicInteger();
        RailBuildScheduler scheduler = new RailBuildScheduler(pool, 4);
        try {
            for (int i = 0; i < 4; i++) require(scheduler.trySchedule(() -> {}, () -> {
                peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                entered.countDown();
                try {
                    if (!unblock.await(5, TimeUnit.SECONDS)) throw new AssertionError("Worker gate timed out");
                    return () -> {};
                } catch (InterruptedException error) { throw new AssertionError(error); }
                finally { active.decrementAndGet(); }
            }, () -> true, finished::incrementAndGet, error -> { throw new AssertionError(error); }), "Four jobs not admitted");
            require(entered.await(5, TimeUnit.SECONDS), "Two workers did not run");
            require(active.get() == 2 && !scheduler.trySchedule(() -> {}, () -> () -> {}, () -> true, () -> {}, error -> {}), "Worker or total limit exceeded");
            unblock.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (finished.get() < 4 && System.nanoTime() < deadline) {
                if (!scheduler.uploadOne()) Thread.sleep(1);
            }
            require(finished.get() == 4 && peak.get() == 2, "Worker limit or completion failed");
        } finally { unblock.countDown(); pool.shutdownNow(); }
    }

    private static void checkCloseDuringBuild() throws Exception {
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        RailBuildScheduler scheduler = new RailBuildScheduler(pool, 1);
        try {
            for (int iteration = 0; iteration < 32; iteration++) {
                CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
                AtomicBoolean current = new AtomicBoolean(true);
                AtomicInteger freed = new AtomicInteger();
                require(scheduler.trySchedule(() -> {}, () -> {
                    started.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Build did not resume");
                    } catch (InterruptedException error) { throw new AssertionError(error); }
                    return new RailBuildScheduler.Upload() {
                        @Override public void upload() { throw new AssertionError("Closed build was uploaded"); }
                        @Override public void close() { freed.incrementAndGet(); }
                    };
                }, current::get, () -> { throw new AssertionError("Closed worker invoked render-thread callback"); }, error -> { throw new AssertionError(error); }), "Closed build leaked its slot");
                require(started.await(5, TimeUnit.SECONDS), "Worker never entered build");
                current.set(false);
                scheduler.discardStale();
                release.countDown();
                // Same executor barrier: the publication/disposal finally block has completed.
                pool.submit(() -> {}).get(5, TimeUnit.SECONDS);
                require(freed.get() == 1 && !scheduler.uploadOne(), "Close during build retained staging without another draw");
            }
        } finally { pool.shutdownNow(); }
    }

    private static void checkChunkMethods(Path root) throws Exception {
        Path rail = root.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/render/rail");
        String base = Files.readString(rail.resolve("RailChunkBase.java"));
        String instanced = Files.readString(rail.resolve("InstancedRailChunk.java"));
        String mesh = Files.readString(rail.resolve("MeshBuildingRailChunk.java"));
        require(method(instanced, "public void close(").contains("closeBuild();") && method(mesh, "public void close(").contains("closeBuild();"), "Chunk close does not invalidate builds");
        require(method(mesh, "public void rebuildBuffer(").contains("rebuildAsync("), "Mesh rebuild bypassed admission");
        String source = "package cn.zbx1425.mtrsteamloco.render.rail;\nimport java.util.*; import java.util.function.*; import java.nio.*; import java.io.*;\npublic class RailBuildChunkProbe {\n"
                + CHUNK_FIXTURE + "\nstatic class Chunk {\n"
                + "boolean closed, bufferBuilding, bufferBuilt, isDirty = true; long chunkId; String modelKey = \"test\"; Map<BakedRail, ArrayList<Matrix4f>> containingRails = new HashMap<>(), containingRailsWriting = new HashMap<>();\n"
                + method(base, "protected final void rebuildAsync(") + method(base, "protected final void closeBuild(")
                + "void setBoundingBox(float min, float max) {}\n}\n"
                + "static class Instanced extends Chunk { Object vertArrays = new Object(); InstanceBuf instanceBuf = new InstanceBuf();\n"
                + method(instanced, "public void rebuildBuffer(") + "}\n" + CHUNK_CHECKS + "\n}";
        Path output = Files.createTempDirectory("ante-rail-build-check-");
        try {
            var unit = new SimpleJavaFileObject(URI.create("string:///RailBuildChunkProbe.java"), javax.tools.JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
            };
            try (var manager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {
                require(ToolProvider.getSystemJavaCompiler().getTask(null, manager, null, List.of("-proc:none", "-classpath", System.getProperty("java.class.path"), "-d", output.toString()), null, List.of(unit)).call(), "Production chunk method fixture failed to compile");
            }
            // Include the helper in this loader: package-private access also requires the same loader.
            try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL(), RailBuildScheduler.class.getProtectionDomain().getCodeSource().getLocation()}, ClassLoader.getPlatformClassLoader())) {
                loader.loadClass("cn.zbx1425.mtrsteamloco.render.rail.RailBuildChunkProbe").getMethod("check").invoke(null);
            }
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static String method(String source, String signature) {
        int start = source.lastIndexOf(signature);
        require(start >= 0, "Missing production method " + signature);
        int end = source.indexOf('{', start) + 1, depth = 1;
        while (depth > 0 && end < source.length()) {
            char c = source.charAt(end++);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        require(depth == 0, "Unterminated production method");
        return source.substring(start, end) + "\n";
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { tasks.add(task); }
        void runAll() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }

    private static final String CHUNK_FIXTURE = """
        static RailBuildScheduler BUILD_SCHEDULER = new RailBuildScheduler(Runnable::run, 4);
        static int allocated, freed, uploaded, errors;
        static boolean failPacking, failUpload;
        static class Main { static Logger LOGGER = new Logger(); }
        static class Logger { void error(String message, Object... args) { errors++; } }
        static class OffHeapAllocator {
            static Set<ByteBuffer> buffers = Collections.newSetFromMap(new IdentityHashMap<>());
            static ByteBuffer allocate(int size) { ByteBuffer b = ByteBuffer.allocate(size); allocated++; buffers.add(b); return b; }
            static void free(ByteBuffer buffer) { if (!buffers.remove(buffer)) throw new AssertionError("Double free"); freed++; }
        }
        static class Mapping { int strideInstance = 72, paddingInstance; }
        static Mapping RAIL_MAPPING = new Mapping();
        static class BakedRail { int color; }
        static class Vector3f { float x() { return 0; } float y() { return 0; } float z() { return 0; } }
        static class Matrix4f { Vector3f getTranslationPart() { return new Vector3f(); } void store(FloatBuffer target) { for(int i = 0; i < 16; i++) target.put(0); } }
        static class BlockPos { BlockPos(int x, int y, int z) {} }
        static class Mth { static int floor(double value) { return (int) Math.floor(value); } }
        enum LightLayer { BLOCK, SKY }
        static class Level { int getBrightness(LightLayer layer, BlockPos pos) { if (failPacking) throw new IllegalStateException("world unloaded"); return 15; } }
        static class LightCoordsUtil { static int pack(int a, int b) { return a | b << 16; } }
        static class VertBuf { static int USAGE_DYNAMIC_DRAW = 1; }
        static class InstanceBuf { int size; void upload(ByteBuffer b, int usage) { if (failUpload) throw new IllegalStateException("upload"); uploaded++; } }
        static class ByteBufferOutputStream { final ByteBuffer buffer; ByteBufferOutputStream(ByteBuffer b, boolean grow) { buffer = b; } }
        static class LittleEndianDataOutputStream {
            final ByteBuffer buffer;
            LittleEndianDataOutputStream(ByteBufferOutputStream stream) { buffer = stream.buffer.order(ByteOrder.LITTLE_ENDIAN); }
            void writeInt(int v) throws IOException { buffer.putInt(v); }
            void write(byte[] bytes) throws IOException { buffer.put(bytes); }
            void writeByte(int b) throws IOException { buffer.put((byte)b); }
        }
        static void require(boolean value, String text) { if (!value) throw new AssertionError(text); }
        static Instanced chunk() { Instanced chunk = new Instanced(); chunk.containingRailsWriting.put(new BakedRail(), new ArrayList<>(List.of(new Matrix4f()))); return chunk; }
        """;

    private static final String CHUNK_CHECKS = """
        public static void check() {
            Level world = new Level();
            Instanced[] chunks = new Instanced[1000];
            for (int i = 0; i < chunks.length; i++) { chunks[i] = chunk(); chunks[i].rebuildBuffer(world); }
            require(allocated == 4 && freed == 0, "1000 chunks were not admission bounded");
            for (int i = 0; i < 4; i++) require(chunks[i].bufferBuilding && !chunks[i].isDirty && !chunks[i].bufferBuilt, "Admitted chunk state wrong");
            for (int i = 4; i < 1000; i++) require(!chunks[i].bufferBuilding && chunks[i].isDirty && !chunks[i].bufferBuilt && chunks[i].containingRails.isEmpty(), "Saturated chunk was mutated");
            chunks[0].closeBuild(); // Pending native buffer must be freed, not uploaded.
            require(freed == 1 && uploaded == 0 && !chunks[0].bufferBuilt, "Closed native result was retained without another frame");
            chunks[4].rebuildBuffer(world);
            for (int i = 0; i < 4; i++) BUILD_SCHEDULER.uploadOne();
            require(allocated == freed && uploaded == 4, "Successful uploads leaked native storage");
            for (int i = 1; i <= 4; i++) require(chunks[i].bufferBuilt && !chunks[i].bufferBuilding, "Successful upload not published");

            failPacking = true;
            Instanced broken = chunk(); broken.rebuildBuffer(world);
            require(allocated == freed, "Native storage leaked during packing exception");
            BUILD_SCHEDULER.uploadOne();
            require(broken.isDirty && !broken.bufferBuilding && !broken.bufferBuilt && errors == 1, "Packing failure did not become retryable");
            failPacking = false; failUpload = true;
            broken.rebuildBuffer(world); BUILD_SCHEDULER.uploadOne();
            require(allocated == freed && broken.isDirty && !broken.bufferBuilding && errors == 2, "Upload failure leaked storage or build state");
            failUpload = false;
            broken.rebuildBuffer(world); BUILD_SCHEDULER.uploadOne();
            require(broken.bufferBuilt && !broken.isDirty && allocated == freed, "Failed chunk could not retry");

            // Reload while native results wait; each old chunk is closed by clearRail().
            for (int i = 0; i < 4; i++) { chunks[i] = chunk(); chunks[i].rebuildBuffer(world); chunks[i].closeBuild(); }
            int before = uploaded;
            require(!BUILD_SCHEDULER.uploadOne(), "Reload retained stale results until another draw");
            require(uploaded == before && allocated == freed, "Reload revived or leaked old native results");
            Instanced fresh = chunk(); fresh.rebuildBuffer(world); BUILD_SCHEDULER.uploadOne();
            require(fresh.bufferBuilt && allocated == freed, "Reload exhausted admission");
        }
        """;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
