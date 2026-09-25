package cn.zbx1425.mtrsteamloco.compatibility;

import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Executes the current source methods with isolated world doubles; does not launch or weave Minecraft. */
public final class ServerTickWorkCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        Path ante = Path.of(args[0]);
        Path mtr = Path.of(args[1]);
        boolean baseline = args.length > 2 && args[2].equals("baseline");
        String train = Files.readString(ante.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/mixin/TrainMixin.java"));
        String original = Files.readString(mtr.resolve("common/src/main/java/mtr/data/Train.java"));
        String railway = Files.readString(ante.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/mixin/RailwayDataMixin.java"));
        String rotation = railway.substring(railway.indexOf("updateNearbyTrains.startTick();") + "updateNearbyTrains.startTick();".length(),
                railway.indexOf("schedulesForPlatform.clear();"));
        String source = "import java.util.*; public class ServerTickProbe {\n"
                + FIXTURE
                + method(train, "protected void _calculateCar(")
                + method(original, "protected final void calculateCar(")
                // The original supported MTR caller checked distance inside scanDoors, not outside it.
                + method(original, "protected final void calculateCar(").replace("void calculateCar(", "void legacyCalculateCar(")
                    .replace("final boolean shouldSkipBlockScan = skipScanBlocks(world, x, y, z);", "")
                    .replace("!shouldSkipBlockScan && ", "")
                + method(train, "private void onScanDoors(")
                + method(train, "private boolean checkEyeCandy(")
                + "\nvoid rotate() {" + rotation + "}\n"
                + CHECKS + "\n}";
        Path output = Files.createTempDirectory("ante-server-tick-check-");
        try {
            var unit = new SimpleJavaFileObject(URI.create("string:///ServerTickProbe.java"), javax.tools.JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
            };
            try (var manager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {
                boolean compiled = ToolProvider.getSystemJavaCompiler().getTask(null, manager, null,
                        List.of("-proc:none", "-d", output.toString()), null, List.of(unit)).call();
                if (!compiled) throw new AssertionError("Current source method fixture failed to compile");
            }
            try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()})) {
                loader.loadClass("ServerTickProbe").getMethod("check", boolean.class).invoke(null, baseline);
            }
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("PASS: extracted ANTE/MTR car and door methods preserve server/client skip, zero-door close, vanilla/Metropolis/EyeCandy platform behavior, legacy caller fallback and exception cleanup; two-tick occupation history "
                + (baseline ? "baseline characterized" : "retained with one ANTE proximity query and two reused maps"));
    }

    private static String method(String source, String declaration) {
        int start = source.indexOf(declaration);
        if (start < 0) throw new AssertionError("Missing source method: " + declaration);
        int opening = source.indexOf('{', start), depth = 1, end = opening + 1;
        while (depth > 0 && end < source.length()) {
            char value = source.charAt(end++);
            if (value == '{') depth++;
            if (value == '}') depth--;
        }
        if (depth != 0) throw new AssertionError("Unterminated source method: " + declaration);
        return source.substring(start, end) + "\n";
    }

    private static final String FIXTURE = """
            float doorValue;
            boolean doorTarget;
            boolean anteDoorScanPrechecked;
            int proximityChecks, platformWrites;
            final List<Map<UUID, Long>> trainPositions = new ArrayList<>(List.of(new HashMap<>(), new HashMap<>()));
            static Class<?> IBlockPlatformClass = MetropolisPlatform.class;
            static double getAverage0(double a, double b) { return (a + b) / 2; }
            static double getAverage(double a, double b) { return (a + b) / 2; }
            double asin(double value) { return Math.asin(value); }
            boolean skipScanBlocks(Level world, double x, double y, double z) {
                proximityChecks++;
                return world.isClientSide() ? doorValue == 0 : !world.playerNearby;
            }
            boolean openDoors(Level world, Block block, BlockPos pos, int dwellTicks) {
                platformWrites++;
                world.lastDoorValue = doorValue;
                return world.isClientSide();
            }
            boolean scanDoors(Level world, double x, double y, double z, float yaw, float pitch, double halfSpacing, int dwellTicks) {
                CallbackInfoReturnable<Boolean> result = new CallbackInfoReturnable<>();
                onScanDoors(world, x, y, z, yaw, pitch, halfSpacing, dwellTicks, result);
                return result.value;
            }
            interface CCB { void calculateCarCallback(double x, double y, double z, float yaw, float pitch, double length, boolean left, boolean right); }
            interface CalculateCarCallback extends CCB { }
            static class CallbackInfoReturnable<T> { T value; void setReturnValue(T value) { this.value = value; } }
            static class Mth { static double atan2(double a, double b) { return Math.atan2(a, b); } }
            record BlockPos(int x, int y, int z) { BlockPos offset(int x, int y, int z) { return new BlockPos(this.x + x, this.y + y, this.z + z); } }
            static class RailwayData { static BlockPos newBlockPos(double x, double y, double z) { return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)); } }
            static class Vec3 {
                final double x, y, z;
                Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
                double distanceTo(Vec3 other) { return Math.sqrt(Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2) + Math.pow(z - other.z, 2)); }
                Vec3 yRot(float angle) { return new Vec3(x * Math.cos(angle) + z * Math.sin(angle), y, z * Math.cos(angle) - x * Math.sin(angle)); }
                Vec3 xRot(float angle) { return new Vec3(x, y * Math.cos(angle) + z * Math.sin(angle), z * Math.cos(angle) - y * Math.sin(angle)); }
            }
            static class Block { }
            static class BlockPlatform extends Block { }
            static class BlockPSDAPGBase extends Block { }
            static class MetropolisPlatform extends Block { }
            static class BlockEntity { }
            static class BlockEyeCandy extends Block {
                static class BlockEntityEyeCandy extends BlockEntity {
                    boolean platform = true, target;
                    float value = -1;
                    boolean isPlatform() { return platform; }
                    void setDoorTarget(boolean target) { this.target = target; }
                    void setDoorValue(float value) { this.value = value; }
                }
            }
            record BlockState(Block block) { Block getBlock() { return block; } }
            static class Level {
                boolean client, playerNearby = true;
                boolean throwOnRead;
                int reads;
                float lastDoorValue = -1;
                final Map<BlockPos, Block> blocks = new HashMap<>();
                final Map<BlockPos, BlockEntity> entities = new HashMap<>();
                boolean isClientSide() { return client; }
                BlockState getBlockState(BlockPos pos) { if (throwOnRead) throw new IllegalStateException("fixture read failure"); reads++; return new BlockState(blocks.getOrDefault(pos, new Block())); }
                BlockEntity getBlockEntity(BlockPos pos) { return entities.get(pos); }
            }
            """;

    private static final String CHECKS = """
            public static void check(boolean baseline) {
                var failures = new ArrayList<String>();
                for (int caller = 0; caller < 3; caller++) {
                    for (boolean client : new boolean[]{false, true}) {
                        for (float value : new float[]{0, 1}) {
                            for (boolean nearby : new boolean[]{false, true}) {
                                for (Block platform : new Block[]{new BlockPlatform(), new BlockPSDAPGBase(), new MetropolisPlatform(), new BlockEyeCandy()}) {
                                    ServerTickProbe probe = new ServerTickProbe();
                                    probe.doorValue = value;
                                    probe.doorTarget = value != 0;
                                    Level world = new Level();
                                    world.client = client;
                                    world.playerNearby = nearby;
                                    BlockPos position = new BlockPos(1, 1, 0);
                                    world.blocks.put(position, platform);
                                    var eyeCandy = new BlockEyeCandy.BlockEntityEyeCandy();
                                    world.entities.put(position, eyeCandy);
                                    Vec3[] positions = {new Vec3(0, 0, -2), new Vec3(0, 0, 2)};
                                    boolean[] doors = new boolean[2];
                                    CalculateCarCallback callback = (x, y, z, yaw, pitch, length, left, right) -> { doors[0] = left; doors[1] = right; };
                                    if (caller == 1) probe.calculateCar(world, positions, 0, 100, callback);
                                    else if (caller == 2) probe.legacyCalculateCar(world, positions, 0, 100, callback);
                                    else probe._calculateCar(world, positions, 0, 100, callback::calculateCarCallback);
                                    boolean skipped = client ? value == 0 : !nearby;
                                    require((world.reads == 0) == skipped, "Server/client block-scan guard changed");
                                    require(doors[1] == (!skipped && value > 0), "Platform boarding-side result changed");
                                    if (!skipped && !(platform instanceof BlockEyeCandy)) {
                                        require(probe.platformWrites > 0 && world.lastDoorValue == value, "Platform open/close side effect lost, including value zero");
                                    }
                                    if (platform instanceof BlockEyeCandy) {
                                        require(eyeCandy.value == (!skipped && client ? value : -1), "EyeCandy update side changed");
                                    }
                                    int expected = caller == 0 ? (baseline ? 2 : 1) : caller == 1 ? (skipped ? 1 : 3) : 2;
                                    if (probe.proximityChecks != expected) failures.add("caller=" + caller + ": proximity checks=" + probe.proximityChecks + ", expected=" + expected);
                                }
                            }
                        }
                    }
                }
                ServerTickProbe interrupted = new ServerTickProbe();
                Level failingWorld = new Level();
                failingWorld.throwOnRead = true;
                try {
                    interrupted._calculateCar(failingWorld, new Vec3[]{new Vec3(0, 0, -2), new Vec3(0, 0, 2)}, 0, 100,
                            (x, y, z, yaw, pitch, length, left, right) -> { });
                    throw new AssertionError("Fixture exception was swallowed");
                } catch (IllegalStateException expected) { }
                require(!interrupted.anteDoorScanPrechecked, "Prechecked door scope leaked after exception");
                failingWorld.throwOnRead = false;
                failingWorld.playerNearby = false;
                interrupted.scanDoors(failingWorld, 0, 1, 0, 0, 0, 2, 100);
                require(failingWorld.reads == 0, "Fallback guard was lost after interrupted car calculation");
                ServerTickProbe probe = new ServerTickProbe();
                Map<UUID, Long> oldest = probe.trainPositions.get(0), previous = probe.trainPositions.get(1);
                UUID stale = new UUID(0, 1), live = new UUID(0, 2), next = new UUID(0, 3);
                oldest.put(stale, 10L);
                previous.put(live, 20L);
                probe.rotate();
                require(probe.trainPositions.size() == 2 && probe.trainPositions.get(0) == previous, "Previous occupancy frame lost");
                require(probe.trainPositions.get(0).get(live) == 20L && probe.trainPositions.get(1).isEmpty(), "Expired occupancy retained or live occupancy cleared");
                if (!baseline && probe.trainPositions.get(1) != oldest) failures.add("Oldest occupancy map was allocated again");
                probe.trainPositions.get(1).put(next, 30L);
                probe.rotate();
                require(probe.trainPositions.get(0).get(next) == 30L && probe.trainPositions.get(1).isEmpty(), "Two-tick occupation history changed");
                if (!baseline && probe.trainPositions.get(1) != previous) failures.add("Second occupancy map was allocated again");
                if (!failures.isEmpty()) throw new AssertionError(String.join("; ", new LinkedHashSet<>(failures)));
            }
            static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
            """;
}
