package cn.zbx1425.mtrsteamloco.render.rail;

import cn.zbx1425.mtrsteamloco.compatibility.RailUploadLifecycleCheck;
import cn.zbx1425.sowcer.batch.BatchManager;
import cn.zbx1425.sowcer.batch.ShaderProp;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.util.OffHeapAllocator;
import cn.zbx1425.sowcerext.model.PreparedUpload;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.level.Level;

/** Uses production admission, workers and uploads, with an owned native fixture at its GPU seam. */
public final class RailBuildBudgetCheck {
    private static void check(boolean ok, String message) { RailUploadLifecycleCheck.require(ok, message); }
    private static void await(CountDownLatch latch) throws Exception {
        check(latch.await(5, TimeUnit.SECONDS), "worker reaches controlled native preparation");
    }
    private static void drained() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!RailBuildScheduler.diagnostics().contains("nativeReserved=0/") && System.nanoTime() < deadline) Thread.sleep(1);
        check(RailBuildScheduler.diagnostics().contains("nativeReserved=0/"), "all rail native reservations released");
        check(RailBuildScheduler.diagnostics().contains("buildingReserved=0, nativeStaged=0, pendingBytes=0"),
                "all build/staged/pending byte counters return to zero");
    }
    private static void pending() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (RailBuildScheduler.UPLOADS.isEmpty() && System.nanoTime() < deadline) Thread.sleep(1);
        check(!RailBuildScheduler.UPLOADS.isEmpty(), "controlled owned upload becomes pending");
    }
    public static void run() throws Exception {
        ControlledChunk legacy = new ControlledChunk();
        legacy.containingRailsWriting.put(null, new ArrayList<>());
        legacy.legacyRebuild();
        check(legacy.bufferBuilding && legacy.bufferBuilt && !legacy.isDirty
                && legacy.containingRails.containsKey(null), "legacy super.rebuildBuffer hook retains published flags and map copy");
        legacy.close(); legacy.legacyRebuild();
        check(!legacy.bufferBuilding && !legacy.isDirty, "closed legacy hook cannot restart a disposed chunk");
        check(RailRepeatGeometry.step(Double.POSITIVE_INFINITY, 1) == 0
                && RailRepeatGeometry.step(0, 1) == 0 && RailRepeatGeometry.step(1, 0) == 0,
                "shared repeat guard rejects nonfinite and zero geometry inputs");
        try { RailRepeatGeometry.advance(1, Double.MIN_VALUE); check(false, "nonadvancing floating-point repeat must fail"); }
        catch (IllegalArgumentException expected) { check(true, "nonadvancing floating-point repeat fails without looping"); }
        ArrayList<RailBuildScheduler.Reservation> reservations = new ArrayList<>();
        for (int i = 0; i < 8; i++) reservations.add(RailBuildScheduler.reserve(8L * 1024 * 1024));
        check(reservations.stream().allMatch(java.util.Objects::nonNull), "normal uploads can fill exactly 64 MiB budget");
        check(RailBuildScheduler.reserve(1) == null, "normal native bytes cannot exceed 64 MiB");
        reservations.forEach(RailBuildScheduler.Reservation::close); reservations.clear();
        RailBuildScheduler.Reservation oversized = RailBuildScheduler.reserve(RailBuildScheduler.NATIVE_BUDGET + 1);
        check(oversized != null, "oversized rail admitted alone, not permanently hidden");
        check(RailBuildScheduler.reserve(0) == null && RailBuildScheduler.reserve(1) == null,
                "oversized upload has exclusive native admission including zero-byte jobs");
        oversized.close(); oversized.close(); drained();
        for (int i = 0; i < 32; i++) reservations.add(RailBuildScheduler.reserve(0));
        check(reservations.stream().allMatch(java.util.Objects::nonNull) && RailBuildScheduler.reserve(0) == null,
                "at most 32 admitted native jobs including zero-byte models");
        ControlledChunk retry = new ControlledChunk(); retry.rebuildBuffer(null);
        check(retry.isDirty && !retry.bufferBuilding && retry.entered.getCount() == 1,
                "full admission keeps dirty chunk eligible without render-thread work");
        reservations.forEach(RailBuildScheduler.Reservation::close); reservations.clear();
        retry.release.countDown(); retry.rebuildBuffer(null); await(retry.entered); pending(); RailChunkBase.upload();
        check(retry.bufferBuilt && !retry.bufferBuilding && retry.uploads == 1, "dirty chunk retries successfully after capacity becomes available");
        retry.close(); drained();

        ControlledChunk active = new ControlledChunk(); active.rebuildBuffer(null); await(active.entered);
        check(OffHeapAllocator.allocatedBytes() == 64, "active worker owns its staged native bytes");
        active.close();
        check(OffHeapAllocator.allocatedBytes() == 64 && RailBuildScheduler.diagnostics().contains("nativeReserved=64/"),
                "cancel cannot free native buffer while active worker still accesses it");
        active.release.countDown(); drained();
        check(OffHeapAllocator.allocatedBytes() == 0 && active.uploads == 0 && !active.bufferBuilding,
                "active stale build discards native result instead of creating GPU resources");

        ControlledChunk queued = new ControlledChunk(); queued.release.countDown(); queued.rebuildBuffer(null);
        await(queued.entered); pending();
        check(RailBuildScheduler.diagnostics().contains("nativeStaged=64, pendingBytes=64"), "prepared/pending counters track real bytes");
        RailBuildScheduler.UPLOADS.clear(); drained();
        check(queued.isDirty && !queued.bufferBuilding && queued.uploads == 0 && OffHeapAllocator.allocatedBytes() == 0,
                "clearing pending uploads discards ownership and permits rebuilding");
        queued.close();
        System.out.println("Rail scheduler diagnostics: " + RailBuildScheduler.diagnostics());
    }

    private static final class ControlledChunk extends RailChunkBase {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile int uploads;
        ControlledChunk() { super(0, "test"); isDirty = true; }
        void legacyRebuild() { super.rebuildBuffer(null); }
        @Override public void rebuildBuffer(Level ignored) {
            scheduleBuild(rails -> 64L, snapshot -> {
                ByteBuffer bytes = OffHeapAllocator.allocate(64);
                boolean transferred = false;
                try {
                    entered.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try { release.await(); break; }
                        catch (InterruptedException cancelled) { interrupted = true; }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                    PreparedUpload<ByteBuffer> prepared = PreparedUpload.of(64, () -> bytes, () -> OffHeapAllocator.free(bytes));
                    transferred = true; return prepared;
                } finally { if (!transferred) OffHeapAllocator.free(bytes); }
            }, (prepared, snapshot) -> { prepared.get(); uploads++; });
        }
        @Override public void enqueue(BatchManager ignored, ShaderProp prop) { }
        @Override public void close() { cancelBuild(); }
    }
}
