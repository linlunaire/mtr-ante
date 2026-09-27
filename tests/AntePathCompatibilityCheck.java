package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.path.BetterPathFinder;
import mtr.data.Rail;
import mtr.data.SavedRailBase;
import mtr.path.KotlinPathCompatibilityCheck;
import mtr.path.PathData;
import net.minecraft.core.BlockPos;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Exercises ANTE's own policy with the shared graph corpus, not MTR's search implementation. */
public final class AntePathCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(BetterPathFinder.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if (!source.equals(Path.of(args[1]).toRealPath())) throw new AssertionError("Unexpected ANTE finder source: " + source);
        new BetterPathFinder(); // Retained public Java constructor.
        String mtrSource = Path.of(PathData.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        String[] sharedArgs = args.length == 3 ? new String[]{args[0], mtrSource, args[2]} : new String[]{args[0], mtrSource};
        KotlinPathCompatibilityCheck.check(sharedArgs, new KotlinPathCompatibilityCheck.Finder() {
            @Override public int findPath(List<PathData> path, Map<BlockPos, Map<BlockPos, Rail>> rails, List<SavedRailBase> stops, int offset, int altitude, boolean fast) {
                return BetterPathFinder.findPath(path, rails, stops, offset, altitude, fast);
            }
            @Override public void appendPath(List<PathData> path, List<PathData> partial) {
                BetterPathFinder.appendPath(path, partial);
            }
        }, false); // The original ANTE policy does not connect separated runway endpoints.
        if (java.util.Arrays.stream(BetterPathFinder.class.getDeclaredAnnotations()).anyMatch(a -> a.annotationType().getName().equals("kotlin.Metadata"))) KotlinPathCompatibilityCheck.checkCancellation();
    }
}
