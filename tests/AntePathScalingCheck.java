package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.path.BetterPathFinder;
import mtr.data.*;
import mtr.path.PathData;
import net.minecraft.core.BlockPos;

import java.nio.file.Path;
import java.util.*;

/** Counts real repeated path-membership work; no wall-clock or FPS assertion. */
public final class AntePathScalingCheck {
    public static void main(String[] args) throws Exception {
        if (!Path.of(BetterPathFinder.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()
                .equals(Path.of(args[0]).toRealPath())) throw new AssertionError("Wrong finder implementation");
        Map<BlockPos, Map<BlockPos, Rail>> rails = new LinkedHashMap<>();
        List<BlockPos> line = new ArrayList<>();
        for (int i = 0; i < 400; i++) line.add(new BlockPos(i * 10, 64, 0));
        CountingPos hub = new CountingPos(4000, 64, 0);
        line.add(hub);
        for (int i = 0; i < line.size() - 1; i++) connect(rails, line.get(i), line.get(i + 1));
        for (int i = 0; i < 32; i++) connect(rails, hub, new BlockPos(4100 + i * 10, 64, 0));
        BlockPos end1 = new BlockPos(9000, 64, 0), end2 = new BlockPos(9010, 64, 0);
        connect(rails, end1, end2);
        List<SavedRailBase> stops = new ArrayList<>(List.of(new Platform(1, TransportMode.TRAIN, line.get(0), line.get(1)), new Platform(2, TransportMode.TRAIN, end1, end2)));
        List<PathData> path = new ArrayList<>();
        hub.comparisons = 0;
        int result = BetterPathFinder.findPath(path, rails, stops, 0, 256, false);
        if (result != 1 || !path.isEmpty()) throw new AssertionError("Unreachable branch graph semantics changed");
        if (args.length == 1 && hub.comparisons > 500) throw new AssertionError("Repeated membership scans remain: " + hub.comparisons + " hub comparisons; maximum 500");
        System.out.println("PATH_SCALING: " + hub.comparisons + " hub comparisons for a 400-edge approach and 32 dead-end branches; result unchanged (not a latency/FPS measurement)");
    }

    private static void connect(Map<BlockPos, Map<BlockPos, Rail>> rails, BlockPos a, BlockPos b) {
        rails.computeIfAbsent(a, ignored -> new LinkedHashMap<>()).put(b, new Rail(a, RailAngle.E, b, RailAngle.W, RailType.IRON, TransportMode.TRAIN));
        rails.computeIfAbsent(b, ignored -> new LinkedHashMap<>()).put(a, new Rail(b, RailAngle.W, a, RailAngle.E, RailType.IRON, TransportMode.TRAIN));
    }

    private static final class CountingPos extends BlockPos {
        private int comparisons;
        private CountingPos(int x, int y, int z) { super(x, y, z); }
        @Override public boolean equals(Object other) { comparisons++; return super.equals(other); }
    }
}
