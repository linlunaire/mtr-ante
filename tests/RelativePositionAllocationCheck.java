package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.data.RelativePosition;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.List;

/** Interpreter-only allocation gate for the real Java-callable position hash. */
public final class RelativePositionAllocationCheck {
    private static volatile int sink;
    public static void main(String[] args) throws Exception {
        if (!ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-Xint")) throw new AssertionError("Use -Xint to prevent escape-analysis artifacts");
        if (!Path.of(RelativePosition.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(Path.of(args[0]).toRealPath())) throw new AssertionError("Wrong position implementation");
        boolean baseline = args.length == 2 && args[1].equals("--baseline");
        List<RelativePosition> positions = List.of(RelativePosition.ZERO, new RelativePosition(-128, 127, 31), new RelativePosition(45, -74, -2), new RelativePosition(127, 126, 125));
        for (int i = 0; i < 4096; i++) sink = positions.get(i & 3).hashCode();
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId(); bean.getThreadAllocatedBytes(thread);
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 32768; i++) sink = positions.get(i & 3).hashCode();
        long bytes = bean.getThreadAllocatedBytes(thread) - before;
        System.out.println("POSITION_HASH_ALLOCATION: " + bytes + " bytes / 32768 calls = " + bytes / 32768.0 + " bytes/call (-Xint)");
        if (baseline ? bytes <= 0 : bytes != 0) throw new AssertionError("Unexpected position hash allocation: " + bytes);
    }
}
