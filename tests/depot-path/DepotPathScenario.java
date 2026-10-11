package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.data.IRoute;
import cn.zbx1425.mtrsteamloco.mixin.PathDataAccessor;
import cn.zbx1425.mtrsteamloco.path.DepotPathGen;
import cn.zbx1425.mtrsteamloco.path.DepotRoutePlan;
import mtr.data.*;
import mtr.mappings.Tuple;
import mtr.path.PathData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Runs in the loader containing actual woven Route and PathData classes. */
public final class DepotPathScenario {
    private static final List<String> packets = new ArrayList<>();
    private static final List<Thread> packetThreads = new ArrayList<>();
    private static Level world;
    private static Thread caller;
    private static boolean throughDepot;
    private static boolean directPlan;
    private static boolean allowInlineWorker;
    private static int assertions;
    private static boolean managed;
    private static final List<Runnable> ownerQueue = new ArrayList<>();

    public static void publish(mtr.path.PathGenerationTask.Request request, java.util.concurrent.Executor ignored, Runnable action) {
        mtr.path.PathGenerationTask.publish(request, ownerQueue::add, action);
    }

    public static void workerContracts() throws Exception {
        managed = true;
        try {
            for (String mode : List.of("success", "failure", "pre-cancel", "interrupt", "mid-cancel", "queued-cancel")) {
                ownerQueue.clear(); packets.clear(); packetThreads.clear();
                Depot depot = new Depot(123, TransportMode.TRAIN);
                depot.corner1 = new Tuple<>(-100, -100); depot.corner2 = new Tuple<>(100, 100);
                var request = new mtr.path.PathGenerationTask.Request();
                Set<Siding> sidings = new HashSet<>();
                if (mode.equals("failure")) sidings.add(new CapturingSiding(11, TransportMode.TRAIN, 0, 4, true));
                if (mode.equals("mid-cancel")) sidings.add(new CancellingSiding());
                AtomicReference<Thread> worker = new AtomicReference<>();
                AtomicReference<Throwable> uncaught = new AtomicReference<>();
                ByteArrayOutputStream errors = new ByteArrayOutputStream(); PrintStream previous = System.err;
                try (PrintStream output = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
                    System.setErr(output);
                    generate(depot, null, new HashMap<>(), sidings, thread -> {
                        worker.set(thread);
                        thread.setUncaughtExceptionHandler((ignored, error) -> uncaught.set(error));
                        mtr.path.PathGenerationTask.register(thread, request);
                        if (mode.equals("pre-cancel")) request.cancel();
                        if (mode.equals("interrupt")) thread.interrupt();
                    });
                    worker.get().join(5000);
                } finally { System.setErr(previous); }
                require(!worker.get().isAlive() && uncaught.get() == null, "Worker failed/hung in " + mode + ": " + uncaught.get());
                boolean computed = mode.equals("success") || mode.equals("failure") || mode.equals("queued-cancel");
                require(ownerQueue.size() == (computed ? 1 : 0) && packets.isEmpty(), "Cancellation enqueued status in " + mode);
                require((errors.size() > 0) == mode.equals("failure"), "Cancellation logged as failure in " + mode);
                if (mode.equals("queued-cancel")) request.cancel();
                ownerQueue.forEach(Runnable::run);
                require(packets.equals(mode.equals("success") ? List.of("123:" + Integer.MAX_VALUE) : mode.equals("failure") ? List.of("123:0") : List.of()), "Unexpected status in " + mode);
            }
            System.out.println("PASS: ANTE worker cancellation + guarded owner notifications" + (throughDepot ? " via real DepotMixin" : ""));
        } finally { managed = false; }
    }

    public static final class CancellingSiding extends Siding {
        CancellingSiding() { super(11, TransportMode.TRAIN, BlockPos.ZERO, new BlockPos(10, 0, 0), 10); }
        @Override public int generateRoute(MinecraftServer server, List<PathData> path, int count, Map<BlockPos, Map<BlockPos, Rail>> rails, SavedRailBase first, SavedRailBase last, boolean repeat, int altitude, boolean fast) {
            throw new java.util.concurrent.CancellationException("fixture cancel");
        }
    }

    public static String run(boolean weaveDepot, boolean planOnly) throws Exception {
        throughDepot = weaveDepot;
        directPlan = planOnly;
        assertions = 0;
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        world = (Level) unsafe.allocateInstance(Class.forName(DepotPathScenario.class.getName() + "$HeightOnlyLevel"));
        caller = Thread.currentThread();
        List<String> records = new ArrayList<>();
        for (String scenario : List.of("same", "opposite", "short", "bridge", "uncached", "cached-missing", "uncached-missing", "siding-failure", "no-routes", "filtered", "high-altitude", "callback-snapshot")) {
            records.add(check(scenario));
        }
        if (directPlan) planContracts();
        else { callbackFailure(); check("callback-reentry"); }
        System.out.println("PASS: " + (directPlan ? "depot plan interface" : "depot worker integration") + ", " + assertions + " assertions");
        return String.join("\n", records) + "\n";
    }

    public static void packet(Level target, long id, int result) {
        require(target == world && id == 123, "Packet changed world/depot identity");
        require(managed ? Thread.currentThread() == caller : Thread.currentThread() != caller || allowInlineWorker, "Completion packet thread ownership changed");
        packets.add(id + ":" + result);
        packetThreads.add(Thread.currentThread());
    }

    private static String check(String scenario) throws Exception {
        packets.clear();
        packetThreads.clear();
        allowInlineWorker = scenario.equals("callback-reentry");
        DataCache cache = new DataCache(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        Depot depot = new Depot(123, TransportMode.TRAIN);
        depot.name = "Fixture";
        depot.cruisingAltitude = scenario.equals("high-altitude") ? 384 : 383;
        depot.corner1 = new Tuple<>(-100, -100);
        depot.corner2 = new Tuple<>(100, 100);
        Platform p1 = new Platform(10, TransportMode.TRAIN, pos(0), pos(1));
        Platform p2 = new Platform(20, TransportMode.TRAIN, pos(2), pos(3));
        Platform p3 = new Platform(30, TransportMode.TRAIN, pos(4), pos(5));
        Platform p4 = new Platform(40, TransportMode.TRAIN, pos(6), pos(7));
        for (Platform platform : List.of(p1, p2, p3, p4)) cache.platformIdMap.put(platform.id, platform);
        List<PathData> firstPath = new ArrayList<>(List.of(part(0, 1, 10, 20, RailType.PLATFORM), part(1, 2, 0, 0, RailType.IRON), part(2, 3, 20, 20, RailType.PLATFORM)));
        List<PathData> secondPath = new ArrayList<>(List.of(part(2, 3, 20, 20, RailType.PLATFORM), part(3, 4, 0, 0, RailType.IRON), part(4, 5, 30, 20, RailType.PLATFORM)));
        List<Platform> secondPlatforms = List.of(p2, p3);
        if (scenario.equals("opposite")) secondPath.set(0, part(3, 2, 20, 20, RailType.PLATFORM));
        if (scenario.equals("short")) secondPath.remove(2);
        if (scenario.equals("bridge")) {
            secondPath = new ArrayList<>(List.of(part(4, 5, 30, 20, RailType.PLATFORM), part(5, 6, 0, 0, RailType.IRON), part(6, 7, 40, 20, RailType.PLATFORM)));
            secondPlatforms = List.of(p3, p4);
        }
        if (scenario.equals("uncached") || scenario.equals("uncached-missing")) firstPath.clear();
        Route firstRoute = route(1, firstPath, p1, p2);
        Route secondRoute = route(2, secondPath, secondPlatforms.toArray(Platform[]::new));
        if (scenario.equals("cached-missing") || scenario.equals("uncached-missing")) firstRoute.platformIds.add(new Route.RoutePlatform(9999));
        cache.routeIdMap.put(1L, firstRoute);
        cache.routeIdMap.put(2L, secondRoute);
        if (!scenario.equals("no-routes")) depot.routeIds.addAll(List.of(1L, 888L, 2L)); // Missing route itself is ignored.
        List<PathData> originals = new ArrayList<>(firstPath);
        originals.addAll(secondPath);
        String originalRecord = describe(originals);
        Map<BlockPos, Map<BlockPos, Rail>> rails = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            RailType type = i % 2 == 0 ? RailType.PLATFORM : RailType.IRON;
            rails.computeIfAbsent(pos(i), ignored -> new LinkedHashMap<>()).put(pos(i + 1), rail(i, i + 1, type));
            rails.computeIfAbsent(pos(i + 1), ignored -> new LinkedHashMap<>()).put(pos(i), rail(i + 1, i, type));
        }
        CapturingSiding low = new CapturingSiding(11, TransportMode.TRAIN, 0, 4, scenario.equals("siding-failure"));
        CapturingSiding high = new CapturingSiding(12, TransportMode.TRAIN, 0, 9, false);
        CapturingSiding wrongMode = new CapturingSiding(13, TransportMode.BOAT, 0, 2, false);
        CapturingSiding outside = new CapturingSiding(14, TransportMode.TRAIN, 1000, 1, false);
        Set<Siding> sidings = new LinkedHashSet<>();
        if (!scenario.equals("filtered") && !scenario.equals("no-routes")) sidings.addAll(List.of(high, low));
        sidings.addAll(List.of(wrongMode, outside));
        if (directPlan) {
            DepotRoutePlan plan = DepotRoutePlan.capture(depot.routeIds, cache);
            try {
                DepotRoutePlan.Result result = plan.assemble(rails, depot.cruisingAltitude, scenario.equals("high-altitude"));
                require(!scenario.equals("uncached-missing"), "Missing uncached platform did not fail during assembly");
                require(packets.isEmpty() && high.calls == 0 && low.calls == 0, "Plan performed siding/packet IO");
                require(originalRecord.equals(describe(originals)), "Plan changed cached path metadata");
                for (PathData part : result.path) for (PathData original : originals) require(part != original, "Plan borrowed cached path metadata");
                require(result.firstPlatform == (scenario.equals("no-routes") ? null : p1), "First platform identity changed");
                require(result.lastPlatform == (scenario.equals("no-routes") ? null : scenario.equals("bridge") ? p4 : p3), "Last platform identity changed");
                return scenario + "\t" + result.mainSegments + ":" + (result.firstPlatform == null ? 0 : result.firstPlatform.id)
                        + ":" + (result.lastPlatform == null ? 0 : result.lastPlatform.id) + ":" + describe(result.path);
            } catch (NullPointerException error) {
                require(scenario.equals("uncached-missing"), "Unexpected assembly failure: " + scenario);
                require(originalRecord.equals(describe(originals)) && packets.isEmpty(), "Failed plan changed cached metadata or sent packets");
                return scenario + "\tFAIL";
            }
        }
        AtomicReference<Thread> worker = new AtomicReference<>();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream(), stderr = new ByteArrayOutputStream();
        PrintStream previousOut = System.out, previousErr = System.err;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8); PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(out); System.setErr(err);
            generate(depot, cache, rails, sidings, thread -> {
                require(Thread.currentThread() == caller && thread.getState() == Thread.State.NEW, "Callback must run synchronously before Thread.start");
                require(worker.getAndSet(thread) == null, "Callback called twice");
                if (scenario.equals("callback-snapshot")) {
                    depot.repeatInfinitely = true;
                    depot.cruisingAltitude = 999;
                    depot.name = "Changed after snapshot";
                }
                if (allowInlineWorker) {
                    thread.run();
                    require(thread.getState() == Thread.State.NEW && packets.size() == 1 && packetThreads.getFirst() == caller,
                            "Callback-run did not complete synchronously before Thread.start");
                    require(high.calls == 1 && high.path.size() == 5, "First callback-run assembled the wrong route");
                }
            });
            require(worker.get() != null, "Callback did not provide worker");
            worker.get().join(5000);
            if (worker.get().isAlive()) { worker.get().interrupt(); throw new AssertionError("Generator worker did not finish"); }
        } finally { System.setOut(previousOut); System.setErr(previousErr); }
        require(packets.size() == (allowInlineWorker ? 2 : 1), "Wrong completion packet count: " + packets);
        if (allowInlineWorker) {
            require(packetThreads.getLast() == worker.get() && packets.stream().allMatch(packet -> packet.equals("123:4")), "Callback-run/start notification order changed");
            require(high.calls == 2 && low.calls == 2 && high.path.size() == 3, "Second run did not retain consumed legacy route state");
        }
        require(originalRecord.equals(describe(originals)), "Cached route path objects were modified");
        require(wrongMode.calls == 0 && outside.calls == 0, "Siding mode/area filters changed");
        boolean failure = scenario.equals("uncached-missing") || scenario.equals("siding-failure");
        require(packets.getFirst().equals("123:" + (failure ? 0 : sidings.size() == 2 ? Integer.MAX_VALUE : 4)), "Result minimum/failure behavior changed: " + scenario + " " + packets);
        require(stdout.toString(StandardCharsets.UTF_8).contains((failure ? "Failed to generate path" : "Finished path generation") + " for Fixture"), "Completion log snapshot changed");
        require(failure == !stderr.toString(StandardCharsets.UTF_8).isEmpty(), "Failure diagnostics changed");
        if (high.path != null) {
            for (PathData part : high.path) for (PathData original : originals) require(part != original, "Worker retained a mutable cached PathData object");
            require(high.altitude == (scenario.equals("high-altitude") ? 384 : 383), "Altitude snapshot changed");
            require(high.fast == scenario.equals("high-altitude"), "Fast-flight threshold changed");
            require(high.repeat == scenario.equals("callback-snapshot"), "Live repeat flag changed");
        }
        return scenario + "\t" + packets.getFirst() + "\t" + high.describe() + "\t" + low.calls;
    }

    private static void callbackFailure() {
        Depot depot = new Depot(123, TransportMode.TRAIN);
        AtomicReference<Thread> worker = new AtomicReference<>();
        RuntimeException marker = new RuntimeException("callback-marker");
        try {
            generate(depot, null, null, null, thread -> { worker.set(thread); throw marker; });
            throw new AssertionError("Callback exception swallowed");
        } catch (RuntimeException error) {
            require(error == marker && worker.get().getState() == Thread.State.NEW, "Callback exception identity or before-start ordering changed");
        }
    }

    private static void planContracts() {
        DataCache cache = new DataCache(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        Platform start = new Platform(10, TransportMode.TRAIN, pos(0), pos(1));
        Platform end = new Platform(20, TransportMode.TRAIN, pos(2), pos(3));
        cache.platformIdMap.put(10L, start); cache.platformIdMap.put(20L, end);
        List<PathData> source = new ArrayList<>(List.of(part(0, 1, 10, 20, RailType.PLATFORM), part(1, 2, 0, 0, RailType.IRON), part(2, 3, 20, 20, RailType.PLATFORM)));
        Route route = route(1, source, start, end); cache.routeIdMap.put(1L, route);
        List<Long> ids = new ArrayList<>(List.of(1L));
        DepotRoutePlan firstPlan = DepotRoutePlan.capture(ids, cache), secondPlan = DepotRoutePlan.capture(ids, cache);
        PathData firstSource = source.getFirst(); Rail borrowedRail = firstSource.rail;
        ((PathDataAccessor) (Object) firstSource).setDwellTime(777);
        source.clear(); route.platformIds.clear(); ids.clear(); cache.routeIdMap.clear(); cache.platformIdMap.clear();
        DepotRoutePlan.Result first = firstPlan.assemble(null, 383, false), second = secondPlan.assemble(null, 383, false);
        require(first.path.size() == 3 && first.path.getFirst().dwellTime == 20, "Capture did not own cached metadata before computation");
        require(first.path.getFirst().rail == borrowedRail && first.firstPlatform == start && first.lastPlatform == end, "Capture cloned borrowed domain references");
        require(first.path != second.path && describe(first.path).equals(describe(second.path)), "Independent plans lost equal results");
        for (PathData a : first.path) for (PathData b : second.path) require(a != b, "Independent plans share mutable metadata");
        ((PathDataAccessor) (Object) second.path.getFirst()).setDwellTime(999); second.path.removeLast();
        require(first.path.size() == 3 && first.path.getFirst().dwellTime == 20 && firstSource.dwellTime == 777, "Result mutation escaped its request");

        cache.platformIdMap.put(10L, start); cache.platformIdMap.put(20L, end);
        List<PathData> path = new ArrayList<>(List.of(part(0, 1, 10, 20, RailType.PLATFORM), part(1, 2, 0, 0, RailType.IRON), part(2, 3, 20, 20, RailType.PLATFORM)));
        Route cached = route(1, path, start, end), empty = route(2, new ArrayList<>(), end);
        cache.routeIdMap.put(1L, cached); cache.routeIdMap.put(2L, empty);
        List<Long> orderedIds = new AbstractList<>() {
            @Override public Long get(int index) { if (index == 1) ((PathDataAccessor) (Object) path.getFirst()).setDwellTime(45); return index + 1L; }
            @Override public int size() { return 2; }
        };
        require(DepotRoutePlan.capture(orderedIds, cache).assemble(null, 383, false).path.getFirst().dwellTime == 45,
                "Metadata was copied before all routes were captured");

        List<PathData> malformed = new ArrayList<>(); Route uncached = route(3, malformed, start, end);
        uncached.platformIds.add(new Route.RoutePlatform(999)); cache.routeIdMap.put(3L, uncached);
        DepotRoutePlan failed = DepotRoutePlan.capture(new ArrayList<>(List.of(3L)), cache);
        Map<BlockPos, Map<BlockPos, Rail>> graph = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            RailType type = i % 2 == 0 ? RailType.PLATFORM : RailType.IRON;
            graph.computeIfAbsent(pos(i), ignored -> new LinkedHashMap<>()).put(pos(i + 1), rail(i, i + 1, type));
            graph.computeIfAbsent(pos(i + 1), ignored -> new LinkedHashMap<>()).put(pos(i), rail(i + 1, i, type));
        }
        try { failed.assemble(graph, 383, false); throw new AssertionError("Missing platform accepted"); }
        catch (NullPointerException expected) { require(malformed.isEmpty(), "Failed search changed route cache"); }
        uncached.platformIds.removeLast();
        Map<BlockPos, Map<BlockPos, Rail>> laterGraph = new LinkedHashMap<>();
        DepotRoutePlan retry = DepotRoutePlan.capture(new ArrayList<>(List.of(3L)), cache);
        laterGraph.putAll(graph);
        require(retry.assemble(laterGraph, 383, false).path.size() == 3 && malformed.isEmpty(), "New request failed or graph was eagerly captured");

        ((IRoute) (Object) cached).getPathData().add(null);
        try { DepotRoutePlan.capture(new ArrayList<>(List.of(1L)), cache); throw new AssertionError("Malformed cached metadata deferred to assembly"); }
        catch (NullPointerException expected) { require(path.getLast() == null, "Capture failure changed source list"); }
        path.removeLast();

        // Equal IDs do not substitute for platform object identity at inter-route joins.
        Route secondRoute = route(4, new ArrayList<>(path), end, start); cache.routeIdMap.put(4L, secondRoute);
        Platform twin = new Platform(20, TransportMode.TRAIN, pos(2), pos(3));
        List<Long> identityIds = new AbstractList<>() {
            @Override public Long get(int index) { if (index == 1) cache.platformIdMap.put(20L, twin); return index == 0 ? 1L : 4L; }
            @Override public int size() { return 2; }
        };
        DepotRoutePlan distinctReferences = DepotRoutePlan.capture(identityIds, cache);
        try { distinctReferences.assemble(new LinkedHashMap<>(), 383, false); throw new AssertionError("Equal platform ID skipped reference-based connection search"); }
        catch (NullPointerException expected) { require(path.size() == 3, "Failed connection search changed cached route"); }
        require(packets.isEmpty(), "Plan contracts emitted completion packets");
    }

    private static void generate(Depot depot, DataCache cache, Map<BlockPos, Map<BlockPos, Rail>> rails, Set<Siding> sidings, java.util.function.Consumer<Thread> callback) {
        if (throughDepot) depot.generateMainRoute(null, world, cache, rails, sidings, callback);
        else DepotPathGen.generateMainRoute(null, world, cache, rails, sidings, callback, depot);
    }

    private static Route route(long id, List<PathData> path, Platform... platforms) {
        Route route = new Route(id, TransportMode.TRAIN);
        ((IRoute) (Object) route).setPathData(path);
        route.platformIds.clear();
        for (Platform platform : platforms) route.platformIds.add(new Route.RoutePlatform(platform.id));
        return route;
    }

    private static PathData part(int a, int b, long id, int dwell, RailType type) { return new PathData(rail(a, b, type), id, dwell, pos(a), pos(b), 99); }
    private static BlockPos pos(int i) { return new BlockPos(i * 10, 64, 0); }
    private static Rail rail(int a, int b, RailType type) { return new Rail(pos(a), a < b ? RailAngle.E : RailAngle.W, pos(b), a < b ? RailAngle.W : RailAngle.E, type, TransportMode.TRAIN); }

    private static String describe(List<PathData> path) {
        StringJoiner result = new StringJoiner(";");
        for (PathData part : path) result.add(part.startingPos.getX() + ">" + ((PathDataAccessor) (Object) part).getEndingPos().getX()
                + ":" + part.rail.railType + ":" + part.savedRailBaseId + ":" + part.dwellTime + ":" + part.stopIndex);
        return result.toString();
    }

    public static final class CapturingSiding extends Siding {
        private final int result;
        private final boolean fail;
        private int calls;
        private List<PathData> path;
        private int mainSegments, altitude;
        private boolean fast, repeat;
        private long first, last;

        public CapturingSiding(long id, TransportMode mode, int x, int result, boolean fail) {
            super(id, mode, new BlockPos(x, 64, 0), new BlockPos(x + 1, 64, 0), 1);
            this.result = result; this.fail = fail;
        }

        @Override public int generateRoute(MinecraftServer server, List<PathData> mainPath, int mainSegments, Map<BlockPos, Map<BlockPos, Rail>> rails,
                SavedRailBase firstPlatform, SavedRailBase lastPlatform, boolean repeat, int altitude, boolean fast) {
            require(server == null && rails != null && (Thread.currentThread() != caller || allowInlineWorker), "Siding dispatch context changed");
            calls++; path = new ArrayList<>(mainPath); this.mainSegments = mainSegments; this.altitude = altitude; this.fast = fast; this.repeat = repeat;
            first = firstPlatform == null ? 0 : firstPlatform.id; last = lastPlatform == null ? 0 : lastPlatform.id;
            if (fail) throw new IllegalStateException("siding-marker");
            return result;
        }

        private String describe() { return calls + ":" + mainSegments + ":" + first + ":" + last + ":" + altitude + ":" + fast + ":" + repeat + ":" + (path == null ? "none" : DepotPathScenario.describe(path)); }
    }

    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
