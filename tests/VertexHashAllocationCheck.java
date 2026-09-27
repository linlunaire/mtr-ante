package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcerext.model.Vertex;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;

/** Measures only the actual vertex hash, not overall mesh construction or deduplication. */
public final class VertexHashAllocationCheck {
    private static volatile int sink;
    public static void main(String[] args) throws Exception {
        if (!ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-Xint")) throw new AssertionError("Use -Xint to exclude JIT escape analysis");
        if (!Path.of(Vertex.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(Path.of(args[0]).toRealPath())) throw new AssertionError("Wrong vertex implementation");
        boolean baseline = args.length > 1 && args[1].equals("--baseline");
        Vertex[] vertices = new Vertex[4];
        for (int i = 0; i < vertices.length; i++) { Vertex vertex = new Vertex(new Vector3f(i, i + 1, i - 2), new Vector3f(0f, 1f, 0f)); vertex.u = i + 0.25f; vertex.v = i - 0.75f; vertex.color = 10000 + i; vertex.light = 20000 + i; vertices[i] = vertex; }
        for (int i = 0; i < 4096; i++) sink = vertices[i & 3].hashCode();
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId(); bean.getThreadAllocatedBytes(thread);
        long start = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 32768; i++) sink = vertices[i & 3].hashCode();
        long bytes = bean.getThreadAllocatedBytes(thread) - start;
        System.out.println("VERTEX_HASH_ALLOCATION: " + bytes + " bytes / 32768 calls = " + bytes / 32768.0 + " bytes/call (-Xint)");
        if (baseline ? bytes <= 0 : bytes != 0) throw new AssertionError("Unexpected vertex hash allocation: " + bytes);
    }
}
