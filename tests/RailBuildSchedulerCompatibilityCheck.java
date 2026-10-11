package cn.zbx1425.mtrsteamloco.render.rail;

import io.github.linlunaire.transitcore.concurrent.BoundedTaskDispatcher;

import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Exercises production chunk methods against Transit Core; generic scheduler contracts live in that project. */
public final class RailBuildSchedulerCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        checkChunkMethods(Path.of(args[0]));
        System.out.println("PASS: production ANTE chunk methods use external Kotlin dispatch, preserving bounded admission, close/reload and native-buffer cleanup");
    }

    private static void checkChunkMethods(Path root) throws Exception {
        Path rail = root.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/render/rail");
        String base = Files.readString(rail.resolve("RailChunkBase.java"));
        require(base.contains("BoundedTaskDispatcher.newWorkerPool(2, \"ANTE rail builder \"), 4)"), "ANTE worker/admission limits changed");
        String instanced = Files.readString(rail.resolve("InstancedRailChunk.java"));
        String mesh = Files.readString(rail.resolve("MeshBuildingRailChunk.java"));
        require(method(instanced, "public void close(").contains("closeBuild();") && method(mesh, "public void close(").contains("closeBuild();"), "Chunk close does not invalidate builds");
        require(method(mesh, "public void rebuildBuffer(").contains("rebuildAsync("), "Mesh rebuild bypassed admission");
        String source = "package cn.zbx1425.mtrsteamloco.render.rail;\nimport java.util.*; import java.util.function.*; import java.nio.*; import java.io.*;\n"
                + "import io.github.linlunaire.transitcore.concurrent.BoundedTaskDispatcher;\npublic class RailBuildChunkProbe {\n"
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
            // Isolate the real Kotlin core and its runtime alongside the Java caller fixture.
            // A platform parent prevents this check from accidentally reusing an already-loaded helper.
            var classpath = new java.util.ArrayList<java.net.URL>();
            classpath.add(output.toUri().toURL());
            for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
                classpath.add(Path.of(entry).toUri().toURL());
            }
            try (var loader = new URLClassLoader(classpath.toArray(java.net.URL[]::new), ClassLoader.getPlatformClassLoader())) {
                require(loader.loadClass(BoundedTaskDispatcher.class.getName()).getClassLoader() == loader, "Fixture did not load its own Kotlin core");
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

    private static final String CHUNK_FIXTURE = """
        static BoundedTaskDispatcher BUILD_SCHEDULER = new BoundedTaskDispatcher(Runnable::run, 4);
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
