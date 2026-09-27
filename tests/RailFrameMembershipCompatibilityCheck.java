package cn.zbx1425.mtrsteamloco.compatibility;

import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Executes production dispatcher methods with lightweight GPU doubles and the real Kotlin membership core. */
public final class RailFrameMembershipCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        Path ante = Path.of(args[0]);
        String dispatcher = Files.readString(ante.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/render/rail/RailRenderDispatcher.java"));
        String registry = Files.readString(ante.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/data/RailModelRegistry.java"));
        String railMixin = Files.readString(ante.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/mixin/RailMixin.java"));
        String reload = method(registry, "public static void reload(");
        require(reload.contains("MainClient.railRenderDispatcher.clearRail();"), "Resource reload no longer clears the dispatcher");
        require(dispatcher.contains("frameRails.mark(rail)"), "Rail registration does not use the production membership core");
        require(dispatcher.contains("frameRails.reconcile(addFrameRail, removeFrameRail)"), "Draw does not use cached membership callbacks");
        require(method(dispatcher, "public void clearRail(").contains("frameRails.clear()"), "Dispatcher clear leaves frame membership alive");

        int fieldsStart = dispatcher.indexOf("private final HashMap<Rail, BakedRail>");
        int fieldsEnd = dispatcher.indexOf("private void addRail(", fieldsStart);
        require(fieldsStart >= 0 && fieldsEnd > fieldsStart, "Missing production dispatcher fields");
        String draw = method(dispatcher, "public void drawRails(");
        int cameraStart = draw.indexOf("Vec3 cameraBlockPos =");
        require(cameraStart > 0, "Missing draw reconciliation boundary");
        // Preserve the actual render-mode transition and membership calls, stopping before GPU rendering.
        String source = "import java.util.*; import java.util.function.Consumer; import io.github.linlunaire.transitcore.collection.FrameMembership;\n"
                + "public class RailFrameMembershipProbe {\n"
                + dispatcher.substring(fieldsStart, fieldsEnd)
                + method(dispatcher, "private void addRail(")
                + method(dispatcher, "private void removeRail(")
                + method(dispatcher, "public boolean registerRail(")
                + method(dispatcher, "public void clearRail(")
                + method(dispatcher, "public static String getModelKeyForRender(")
                + draw.substring(0, cameraStart) + "}\n"
                + FIXTURE.replace("/* RAIL_EQUALITY */", method(railMixin, "public boolean equals(").replace("RailMixin", "Rail")
                        + method(railMixin, "public int hashCode("))
                + CHECKS + "\n}";

        Path output = Files.createTempDirectory("ante-rail-membership-check-");
        try {
            var unit = new SimpleJavaFileObject(URI.create("string:///RailFrameMembershipProbe.java"), javax.tools.JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
            };
            try (var manager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {
                require(ToolProvider.getSystemJavaCompiler().getTask(null, manager, null,
                        List.of("-proc:none", "-classpath", System.getProperty("java.class.path"), "-d", output.toString()),
                        null, List.of(unit)).call(), "Extracted rail dispatcher fixture failed to compile");
            }
            var classpath = new ArrayList<URL>();
            classpath.add(output.toUri().toURL());
            for (String entry : System.getProperty("java.class.path").split(Pattern.quote(java.io.File.pathSeparator))) {
                classpath.add(Path.of(entry).toUri().toURL());
            }
            try (var loader = new URLClassLoader(classpath.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
                Class<?> core = loader.loadClass("io.github.linlunaire.transitcore.collection.FrameMembership");
                require(core.getClassLoader() == loader, "Membership core escaped the isolated class loader");
                require(Arrays.stream(core.getAnnotations()).anyMatch(annotation -> annotation.annotationType().getName().equals("kotlin.Metadata")),
                        "Fixture is not executing the actual Kotlin membership class");
                require(!core.getProtectionDomain().getCodeSource().getLocation().equals(output.toUri().toURL()),
                        "Membership implementation was copied into the fixture");
                try {
                    loader.loadClass("RailFrameMembershipProbe").getMethod("check").invoke(null);
                } catch (InvocationTargetException failure) {
                    if (failure.getCause() instanceof Error error) throw error;
                    if (failure.getCause() instanceof Exception exception) throw exception;
                    throw failure;
                }
                System.out.println("Kotlin membership core: " + core.getProtectionDomain().getCodeSource().getLocation());
            }
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("PASS: extracted rail registration/model filtering and woven equality, duplicate/equal-content merging and hash collisions, add-before-remove, 2/3 mode-switch frame loss/rebuild, exactly-once clear and reload invalidation with the real Kotlin core");
    }

    private static String method(String source, String declaration) {
        int start = source.indexOf(declaration);
        require(start >= 0, "Missing production method: " + declaration);
        int opening = source.indexOf('{', start), depth = 1, end = opening + 1;
        require(opening >= 0, "Missing method body: " + declaration);
        while (depth > 0 && end < source.length()) {
            char value = source.charAt(end++);
            if (value == '{') depth++;
            if (value == '}') depth--;
        }
        require(depth == 0, "Unterminated production method: " + declaration);
        return source.substring(start, end) + "\n";
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final String FIXTURE = """
            static final List<String> events = new ArrayList<>();
            static final List<BakedRail> baked = new ArrayList<>();
            static final List<RailChunkBase> chunks = new ArrayList<>();
            enum RailType { NORMAL, SIDING, NONE }
            enum TransportMode { TRAIN, BOAT }
            interface RailExtraSupplier { String getModelKey(); }
            // ANTE's RailMixin injects serialized-content equality; the plain MTR Rail class is not the runtime contract.
            static final class Rail implements RailExtraSupplier {
                final String name, modelKey;
                final RailType railType;
                final TransportMode transportMode;
                final long chunkId;
                private byte[] dataBytes;
                private int hashCode;
                Rail(String name, String modelKey, RailType railType, TransportMode transportMode, long chunkId) {
                    this.name = name; this.modelKey = modelKey; this.railType = railType;
                    this.transportMode = transportMode; this.chunkId = chunkId;
                }
                public String getModelKey() { return modelKey; }
                /* RAIL_EQUALITY */
                private void createDataBytes() {
                    // Lightweight deterministic packet bytes; equals/hashCode above are the extracted production methods.
                    dataBytes = (name + "|" + modelKey + "|" + railType + "|" + transportMode + "|" + chunkId)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    hashCode = Arrays.hashCode(dataBytes);
                }
            }
            static final class BakedRail {
                final Rail rail;
                final String modelKey;
                final HashMap<Long, Object> coveredChunks = new HashMap<>();
                int disposals;
                BakedRail(Rail rail) {
                    this.rail = rail;
                    modelKey = getModelKeyForRender(rail);
                    if (!modelKey.equals("null")) coveredChunks.put(rail.chunkId, new Object());
                    baked.add(this);
                    events.add("add:" + rail.name);
                }
                void dispose() { disposals++; events.add("remove:" + rail.name); }
            }
            static class RailChunkBase {
                final long chunkId;
                final String modelKey;
                final Set<BakedRail> rails = new HashSet<>();
                int closes;
                RailChunkBase(long chunkId, String modelKey) {
                    this.chunkId = chunkId; this.modelKey = modelKey; chunks.add(this);
                }
                void addRail(BakedRail rail) { rails.add(rail); }
                void removeRail(BakedRail rail) { rails.remove(rail); }
                void close() { closes++; }
            }
            static final class InstancedRailChunk extends RailChunkBase {
                InstancedRailChunk(long chunkId, String modelKey) { super(chunkId, modelKey); }
            }
            static final class MeshBuildingRailChunk extends RailChunkBase {
                MeshBuildingRailChunk(long chunkId, String modelKey) { super(chunkId, modelKey); }
            }
            static final class RailModelRegistry { static final Map<String, Object> ELEMENTS = new HashMap<>(); }
            static final class ClientConfig { static int mode = 2; static int getRailRenderLevel() { return mode; } }
            static final class BatchManager { }
            static final class DrawScheduler { final BatchManager batchManager = new BatchManager(); }
            static final class Matrix4f { }
            static final class Level { }
            void frame() { drawRails(new Level(), new DrawScheduler(), new Matrix4f()); }
            static Rail rail(String name) { return new Rail(name, "custom", RailType.NORMAL, TransportMode.TRAIN, 1); }
            static RailFrameMembershipProbe fresh() {
                events.clear(); baked.clear(); chunks.clear();
                isHoldingRailItem = false;
                ClientConfig.mode = 2;
                RailModelRegistry.ELEMENTS.clear();
                for (String key : List.of("", "null", "custom", "nte_builtin_depot", "nte_builtin_concrete_sleeper")) {
                    RailModelRegistry.ELEMENTS.put(key, new Object());
                }
                var probe = new RailFrameMembershipProbe();
                probe.clearRail();
                return probe;
            }
            static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
            """;

    private static final String CHECKS = """
            public static void check() {
                filters();
                equalityAndOrder();
                modes();
                clearAndReload();
            }
            static void filters() {
                var probe = fresh();
                Rail none = new Rail("none", "custom", RailType.NONE, TransportMode.TRAIN, 1);
                Rail boat = new Rail("boat", "", RailType.NORMAL, TransportMode.BOAT, 1);
                Rail unknownBoat = new Rail("unknownBoat", "missing", RailType.NORMAL, TransportMode.BOAT, 1);
                require(!probe.registerRail(none) && !probe.registerRail(boat) && !probe.registerRail(unknownBoat), "NONE/default non-train filtering changed");
                Rail train = new Rail("train", "missing", RailType.NORMAL, TransportMode.TRAIN, 1);
                Rail siding = new Rail("siding", "", RailType.SIDING, TransportMode.TRAIN, 2);
                Rail customBoat = new Rail("customBoat", "custom", RailType.NORMAL, TransportMode.BOAT, 3);
                Rail hidden = new Rail("hidden", "null", RailType.NORMAL, TransportMode.TRAIN, 4);
                require(getModelKeyForRender(train).equals("nte_builtin_concrete_sleeper"), "Unknown train model fallback changed");
                require(getModelKeyForRender(siding).equals("nte_builtin_depot"), "Siding fallback changed");
                require(getModelKeyForRender(customBoat).equals("custom"), "Explicit registered model lost");
                require(probe.registerRail(train) && probe.registerRail(siding) && probe.registerRail(customBoat), "Valid rails rejected");
                isHoldingRailItem = true;
                require(!probe.registerRail(hidden), "Hidden rail entered ANTE pipeline while holding a rail item");
                isHoldingRailItem = false;
                require(probe.registerRail(hidden), "Hidden model placeholder lost");
                probe.frame();
                require(probe.railRefMap.size() == 4 && !probe.railRefMap.containsKey(none), "Filtered registration leaked into reconciliation");
                require(probe.railRefMap.get(hidden).coveredChunks.isEmpty(), "Hidden rail unexpectedly created geometry");
                probe.clearRail();
            }
            static void equalityAndOrder() {
                var probe = fresh();
                Rail first = rail("same"), equalContents = rail("same"), expired = rail("expired"), replacement = rail("replacement");
                require(first != equalContents && first.equals(equalContents) && first.hashCode() == equalContents.hashCode(), "Woven rail equality fixture is invalid");
                require(probe.registerRail(first) && probe.registerRail(first) && probe.registerRail(equalContents) && probe.registerRail(expired), "Duplicate registration return value changed");
                probe.frame();
                require(baked.size() == 2 && probe.railRefMap.size() == 2, "Duplicate references or equal-content rails rebuilt");
                BakedRail retained = probe.railRefMap.get(first), removed = probe.railRefMap.get(expired);
                require(retained.rail == first, "First representative was replaced by an equal-content registration");
                require(probe.railRefMap.get(equalContents) == retained, "Equal-content rail did not resolve to the retained BakedRail");
                events.clear();
                probe.registerRail(equalContents); probe.registerRail(equalContents); probe.registerRail(expired);
                probe.frame();
                require(events.isEmpty() && probe.railRefMap.get(first) == retained, "Equivalent new instance replaced a retained rail");
                probe.registerRail(first); probe.registerRail(replacement);
                probe.frame();
                require(events.equals(List.of("add:replacement", "remove:expired")), "Reconciliation no longer adds before removing: " + events);
                require(removed.disposals == 1 && retained.disposals == 0 && probe.railRefMap.size() == 2, "Retained/expired lifecycle changed");
                events.clear();
                probe.frame();
                require(probe.railRefMap.isEmpty() && retained.disposals == 1, "Marks leaked into an unregistered next frame");
                require(events.size() == 2 && events.stream().allMatch(event -> event.startsWith("remove:")), "Empty frame generated spurious additions");
                probe.clearRail();
                probe = fresh();
                Rail collisionA = rail("Aa"), collisionB = rail("BB");
                require(!collisionA.equals(collisionB) && collisionA.hashCode() == collisionB.hashCode(), "Distinct same-hash fixture is invalid");
                probe.registerRail(collisionA); probe.registerRail(collisionB); probe.frame();
                require(probe.railRefMap.size() == 2 && baked.size() == 2, "Hash collision merged distinct rails");
                probe.registerRail(collisionB); probe.frame();
                require(probe.railRefMap.size() == 1 && probe.railRefMap.containsKey(collisionB), "Hash collision removed the wrong rail");
                probe.clearRail();
            }
            static void modes() {
                var probe = fresh();
                Rail rail = rail("mode");
                probe.registerRail(rail); probe.frame();
                require(probe.railChunkList.size() == 1 && probe.railChunkList.getFirst() instanceof MeshBuildingRailChunk, "Mode 2 did not create mesh chunk");
                for (int mode : new int[]{3, 2}) {
                    BakedRail old = probe.railRefMap.get(rail);
                    RailChunkBase oldChunk = probe.railChunkList.getFirst();
                    probe.registerRail(rail);
                    ClientConfig.mode = mode;
                    probe.frame();
                    require(probe.railRefMap.isEmpty() && probe.railChunkList.isEmpty(), "Mode switch unexpectedly retained pre-switch frame marks");
                    require(old.disposals == 1 && oldChunk.closes == 1, "Mode switch failed exactly-once disposal");
                    probe.registerRail(rail); probe.frame();
                    require(probe.railRefMap.size() == 1 && probe.railRefMap.get(rail) != old, "Following frame failed to rebuild after mode switch");
                    require((probe.railChunkList.getFirst() instanceof InstancedRailChunk) == (mode == 3), "Following frame used wrong chunk mode");
                }
                probe.clearRail();
                require(baked.stream().allMatch(value -> value.disposals == 1) && chunks.stream().allMatch(value -> value.closes == 1), "Mode transitions leaked or double-closed resources");
            }
            static void clearAndReload() {
                var probe = fresh();
                Rail first = rail("one"), second = new Rail("two", "custom", RailType.NORMAL, TransportMode.TRAIN, 2);
                Rail pending = rail("pending");
                probe.registerRail(first); probe.registerRail(second); probe.frame();
                probe.registerRail(first); probe.registerRail(pending);
                List<BakedRail> oldBaked = List.copyOf(baked);
                List<RailChunkBase> oldChunks = List.copyOf(chunks);
                RailModelRegistry.ELEMENTS.clear();
                RailModelRegistry.ELEMENTS.put("reloaded", new Object());
                probe.clearRail(); // The verified production reload hook calls this exact extracted method.
                probe.clearRail();
                require(oldBaked.stream().allMatch(value -> value.disposals == 1), "Clear/reload did not dispose each BakedRail exactly once");
                require(oldChunks.stream().allMatch(value -> value.closes == 1), "Clear/reload did not close each chunk exactly once");
                require(probe.railRefMap.isEmpty() && probe.railChunkList.isEmpty(), "Clear retained live render objects");
                require(probe.railChunkMap.keySet().equals(Set.of("reloaded")), "Reload kept old registry chunk keys");
                probe.frame();
                require(probe.railRefMap.isEmpty() && baked.size() == oldBaked.size(), "Pending pre-reload marks resurrected old rails");
                Rail newRail = new Rail("new", "reloaded", RailType.NORMAL, TransportMode.TRAIN, 1);
                require(probe.registerRail(newRail), "Reloaded model rejected");
                probe.frame();
                require(probe.railRefMap.size() == 1 && probe.railRefMap.containsKey(newRail), "Reload membership failed to rebuild");
                probe.clearRail();
                require(baked.stream().allMatch(value -> value.disposals == 1) && chunks.stream().allMatch(value -> value.closes == 1), "Reload left leaked or multiply-closed resources");
            }
            """;
}
