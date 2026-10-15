package cn.zbx1425.mtrsteamloco.render.rail;

import cn.zbx1425.mtrsteamloco.data.RailModelRegistry;
import cn.zbx1425.sowcer.batch.BatchManager;
import cn.zbx1425.sowcer.batch.ShaderProp;
import cn.zbx1425.sowcer.math.Matrix4f;
import mtr.data.Rail;
import mtr.data.RailAngle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import cn.zbx1425.mtrsteamloco.scripting.util.client.DynamicModelHolder;
import cn.zbx1425.sowcerext.reuse.DrawScheduler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import cn.zbx1425.sowcerext.model.PreparedUpload;

public abstract class RailChunkBase implements Closeable {

    protected static ExecutorService EXECUTOR = RailBuildScheduler.EXECUTOR;
    protected static ConcurrentLinkedQueue<Runnable> UPLOAD_QUEUE = RailBuildScheduler.UPLOADS;

    final Object lifecycleLock = new Object();
    private volatile boolean closed;
    private volatile long generation;
    private RailBuildScheduler.Task<?> buildTask;
    private long retryAfterNanos;
    private int consecutiveFailures;

    public Long chunkId;
    public AABB boundingBox;
    public HashMap<BakedRail, ArrayList<Matrix4f>> containingRails = new HashMap<>();
    public HashMap<BakedRail, ArrayList<Matrix4f>> containingRailsWriting = new HashMap<>();

    public final String modelKey;

    protected float modelYMin;
    protected float modelYMax;

    public volatile boolean isDirty = false;
    public volatile boolean bufferBuilt = false;
    public volatile boolean bufferBuilding = false;
    public double cameraDistManhattanXZ = 0;

    public RailChunkBase(long chunkId, String modelKey) {
        this.chunkId = chunkId;
        this.modelKey = modelKey;
        long boundary = RailModelRegistry.getProperty(modelKey).boundingBox;
        modelYMin = Float.intBitsToFloat((int)(boundary >> 32));
        modelYMax = Float.intBitsToFloat((int)(boundary & 0xFFFFFFFFL));
        setBoundingBox(0, 0);
    }

    protected void setBoundingBox(float yMin, float yMax) {
        int posXMin = (int)(chunkId >> 32) << (4 + BakedRail.POS_SHIFT);
        int posZMin = (int)(chunkId & 0xFFFFFFFFL) << (4 + BakedRail.POS_SHIFT);
        int span = 1 << (4 + BakedRail.POS_SHIFT);
        boundingBox = new AABB(posXMin, yMin + modelYMin - 1, posZMin,
                posXMin + span, yMax + modelYMax + 1, posZMin + span);
    }

    public boolean isEven() { // Just for ease of debugging to show a checkerboard pattern.
        return ((int)(chunkId >> 32) + (int)(chunkId & 0xFFFFFFFFL)) % 2 == 0;
    }

    public ChunkPos getChunkPos() {
        return new ChunkPos((int)(chunkId >> 32) << BakedRail.POS_SHIFT, (int)(chunkId & 0xFFFFFFFFL) << BakedRail.POS_SHIFT);
    }

    public boolean containsYSection(int yMin, int yMax) {
        return (yMin << 4) < boundingBox.minY || (yMax << 4) > boundingBox.maxY;
    }

    public double getCameraDistManhattanXZ(Vec3 cameraPos) {
        cameraDistManhattanXZ = Math.abs(cameraPos.x - (boundingBox.minX + boundingBox.maxX) / 2)
                + Math.abs(cameraPos.z - (boundingBox.minZ + boundingBox.maxZ) / 2);
        return cameraDistManhattanXZ;
    }

    public void addRail(BakedRail rail) {
        synchronized (lifecycleLock) {
            if (closed) return;
            containingRailsWriting.put(rail, rail.coveredChunks.get(chunkId));
            isDirty = true;
        }
    }

    public void removeRail(BakedRail rail) {
        synchronized (lifecycleLock) {
            if (closed) return;
            containingRailsWriting.remove(rail);
            isDirty = true;
        }
    }

    public void rebuildBuffer(Level world) {
        // Legacy subclass hook. Built-in chunks use owned scheduleBuild admission instead.
        synchronized (lifecycleLock) {
            if (closed) return;
            bufferBuilding = true;
            isDirty = false;
            bufferBuilt = true;
            containingRails.clear();
            containingRails.putAll(containingRailsWriting);
        }
    }
    public abstract void enqueue(BatchManager batchManager, ShaderProp shaderProp);

    public static void upload() {
        RailBuildScheduler.logDiagnostics();
        long deadline = System.nanoTime() + 2_000_000L;
        for (int count = 0; count < 4; count++) {
            Runnable task = UPLOAD_QUEUE.poll();
            if (task == null) break;
            task.run();
            if (System.nanoTime() >= deadline) break;
        }
    }

    public static String getAsyncDiagnostics() { return RailBuildScheduler.diagnostics(); }

    protected final <T> void scheduleBuild(Function<HashMap<BakedRail, ArrayList<Matrix4f>>, Long> estimate,
            Function<BuildSnapshot, PreparedUpload<T>> build, BiConsumer<PreparedUpload<T>, BuildSnapshot> upload) {
        synchronized (lifecycleLock) {
            if (closed || bufferBuilding || (retryAfterNanos != 0 && System.nanoTime() - retryAfterNanos < 0)) return;
            RailBuildScheduler.Reservation reservation = null;
            try {
                // Estimate before copying geometry or allocating native staging data.
                reservation = RailBuildScheduler.reserve(estimate.apply(containingRailsWriting));
                if (reservation == null) return;
                HashMap<BakedRail, ArrayList<Matrix4f>> rails = new HashMap<>();
                containingRailsWriting.forEach((rail, transforms) -> {
                    ArrayList<Matrix4f> copies = new ArrayList<>(transforms.size());
                    for (Matrix4f transform : transforms) copies.add(transform.copy());
                    rails.put(rail, copies);
                });
                BuildSnapshot snapshot = new BuildSnapshot(rails, ++generation);
                RailBuildScheduler.Task<T> task = new RailBuildScheduler.Task<>(this, generation, reservation,
                        () -> build.apply(snapshot), prepared -> upload.accept(prepared, snapshot));
                buildTask = task;
                bufferBuilding = true;
                isDirty = false;
                containingRails.clear(); containingRails.putAll(rails);
                try { EXECUTOR.execute(task); }
                catch (RejectedExecutionException full) { task.close(); }
                catch (RuntimeException | Error ex) { task.close(); throw ex; }
            } catch (RuntimeException | Error ex) {
                if (reservation != null) reservation.close();
                failedBuild(ex);
            }
        }
    }

    protected final class BuildSnapshot {
        final HashMap<BakedRail, ArrayList<Matrix4f>> rails;
        final long generation;
        BuildSnapshot(HashMap<BakedRail, ArrayList<Matrix4f>> rails, long generation) {
            this.rails = rails; this.generation = generation;
        }
        void checkCancelled() { checkBuildCurrent(generation); }
    }

    final void checkBuildCurrent(long expected) {
        if (closed || generation != expected || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    final void buildFinished(RailBuildScheduler.Task<?> task, long expected, Throwable failure) {
        synchronized (lifecycleLock) {
            if (buildTask != task || generation != expected || closed) return;
            buildTask = null; bufferBuilding = false;
            if (failure == null) {
                bufferBuilt = true; consecutiveFailures = 0; retryAfterNanos = 0;
            } else if (failure instanceof CancellationException) {
                isDirty = true;
            } else failedBuild(failure);
        }
    }

    private void failedBuild(Throwable failure) {
        isDirty = true; bufferBuilding = false;
        int delaySeconds = Math.min(30, 1 << Math.min(consecutiveFailures++, 5));
        retryAfterNanos = System.nanoTime() + delaySeconds * 1_000_000_000L;
        RailBuildScheduler.LOGGER.warn("Rail build failed for model {} chunk {}; retry in {} s ({})",
                modelKey, chunkId, delaySeconds, getAsyncDiagnostics(), failure);
    }

    protected final void cancelBuild() {
        synchronized (lifecycleLock) {
            if (closed) return;
            closed = true; generation++;
            RailBuildScheduler.Task<?> task = buildTask; buildTask = null;
            bufferBuilding = false; isDirty = false;
            if (task != null) task.close();
        }
    }

    @Override
    public abstract void close();
}
