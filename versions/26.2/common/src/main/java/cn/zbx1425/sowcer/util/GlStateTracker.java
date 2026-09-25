package cn.zbx1425.sowcer.util;

/** Compatibility guard for old call sites. Blaze3D now owns GPU bindings; these are CPU preparation scopes. */
public final class GlStateTracker {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    public static volatile boolean isStateProtected;

    public static void capture() {
        DEPTH.set(DEPTH.get() + 1);
        isStateProtected = true;
    }

    public static void restore() {
        final int depth = DEPTH.get();
        if (depth == 0) throw new IllegalStateException("Render preparation scope not captured");
        DEPTH.set(depth - 1);
        isStateProtected = depth > 1;
    }

    public static void assertProtected() {
        if (DEPTH.get() == 0) throw new IllegalStateException("Render preparation scope not protected");
    }
}
