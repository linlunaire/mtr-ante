package cn.zbx1425.mtrsteamloco.render.rail;

import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.data.RailModelRegistry;
import cn.zbx1425.mtrsteamloco.render.ByteBufferOutputStream;
import cn.zbx1425.sowcer.batch.BatchManager;
import cn.zbx1425.sowcer.batch.EnqueueProp;
import cn.zbx1425.sowcer.batch.ShaderProp;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.model.Model;
import cn.zbx1425.sowcer.model.VertArrays;
import org.msgpack.core.MessagePacker;
import cn.zbx1425.sowcerext.reuse.DrawScheduler;
import cn.zbx1425.sowcer.object.InstanceBuf;
import cn.zbx1425.sowcer.object.VertBuf;
import cn.zbx1425.sowcer.util.OffHeapAllocator;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import cn.zbx1425.sowcer.vertex.VertAttrSrc;
import cn.zbx1425.sowcer.vertex.VertAttrState;
import cn.zbx1425.sowcer.vertex.VertAttrType;
import com.google.common.io.LittleEndianDataOutputStream;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.UncheckedIOException;
import cn.zbx1425.sowcerext.model.PreparedUpload;

public class InstancedRailChunk extends RailChunkBase {

    private final Model railModel;
    private InstanceBuf instanceBuf;
    private VertArrays vertArrays;

    private static final VertAttrMapping RAIL_MAPPING = new VertAttrMapping.Builder()
            .set(VertAttrType.POSITION, VertAttrSrc.VERTEX_BUF)
            .set(VertAttrType.COLOR, VertAttrSrc.INSTANCE_BUF_OR_GLOBAL)
            .set(VertAttrType.UV_TEXTURE, VertAttrSrc.VERTEX_BUF)
            .set(VertAttrType.UV_OVERLAY, VertAttrSrc.GLOBAL)
            .set(VertAttrType.UV_LIGHTMAP, VertAttrSrc.INSTANCE_BUF_OR_GLOBAL)
            .set(VertAttrType.NORMAL, VertAttrSrc.VERTEX_BUF)
            .set(VertAttrType.MATRIX_MODEL, VertAttrSrc.INSTANCE_BUF)
            .build();

    public InstancedRailChunk(Long chunkId, String modelKey) {
        super(chunkId, modelKey);
        railModel = RailModelRegistry.getProperty(modelKey).uploadedModel;
    }

    @Override
    public void rebuildBuffer(Level world) {
        if (railModel == null) return;
        scheduleBuild(rails -> Math.multiplyExact((long) instanceCount(rails), RAIL_MAPPING.strideInstance), snapshot -> {
            int instanceCount = instanceCount(snapshot.rails);
            int byteSize = Math.toIntExact(Math.multiplyExact((long) instanceCount, RAIL_MAPPING.strideInstance));
            float yMin = 256, yMax = -64;
            if (instanceCount == 0) return PreparedUpload.of(0,
                    () -> new UploadedInstances(null, null, -64, -64), () -> {});
            ByteBuffer byteBuf = OffHeapAllocator.allocate(byteSize);
            boolean transferred = false;
            try {
            ByteBufferOutputStream byteArrayOutputStream = new ByteBufferOutputStream(byteBuf, false);
            LittleEndianDataOutputStream oStream = new LittleEndianDataOutputStream(byteArrayOutputStream);
            byte[] lookAtBytes = new byte[4 * 16];
            FloatBuffer matFloatBuf = ByteBuffer.wrap(lookAtBytes).order(ByteOrder.nativeOrder()).asFloatBuffer();

            for (Map.Entry<BakedRail, ArrayList<Matrix4f>> entry : snapshot.rails.entrySet()) {
                ArrayList<Matrix4f> railSpan = entry.getValue();
                for (Matrix4f pieceMat : railSpan) {
                    snapshot.checkCancelled();
                    try {
                        oStream.writeInt(entry.getKey().color);

                        final Vector3f lightPos = pieceMat.getTranslationPart();
                        yMin = Math.min(yMin, lightPos.y());
                        yMax = Math.max(yMax, lightPos.y());
                        final BlockPos lightBlockPos = new BlockPos(Mth.floor(lightPos.x()), Mth.floor(lightPos.y() + 0.1), Mth.floor(lightPos.z()));
                        final int light = LightTexture.pack(world.getBrightness(LightLayer.BLOCK, lightBlockPos), world.getBrightness(LightLayer.SKY, lightBlockPos));
                        oStream.writeInt(light);

                        pieceMat.store(matFloatBuf);
                        oStream.write(lookAtBytes);

                        for (int k = 0; k < RAIL_MAPPING.paddingInstance; k++) oStream.writeByte(0);
                    } catch (IOException ex) { throw new UncheckedIOException(ex); }
                }
            }

            if (yMin > yMax) yMin = yMax;
            final float minimum = yMin, maximum = yMax;
            PreparedUpload<UploadedInstances> prepared = PreparedUpload.of(byteSize, () -> {
                InstanceBuf buffer = new InstanceBuf(0);
                VertArrays arrays = null;
                try {
                    buffer.upload(byteBuf, VertBuf.USAGE_DYNAMIC_DRAW);
                    buffer.size = instanceCount;
                    arrays = VertArrays.createAll(railModel, RAIL_MAPPING, buffer);
                    return new UploadedInstances(buffer, arrays, minimum, maximum);
                } catch (RuntimeException | Error ex) {
                    if (arrays != null) try { arrays.close(); } catch (RuntimeException | Error cleanup) { ex.addSuppressed(cleanup); }
                    try { buffer.close(); } catch (RuntimeException | Error cleanup) { ex.addSuppressed(cleanup); }
                    throw ex;
                }
            }, () -> OffHeapAllocator.free(byteBuf));
            transferred = true;
            return prepared;
            } finally { if (!transferred) OffHeapAllocator.free(byteBuf); }
        }, (prepared, snapshot) -> {
            UploadedInstances next = prepared.get();
            try { setBoundingBox(next.yMin, next.yMax); }
            catch (RuntimeException | Error ex) { next.close(); throw ex; }
            InstanceBuf previousBuffer = instanceBuf; VertArrays previousArrays = vertArrays;
            instanceBuf = next.buffer; vertArrays = next.arrays;
            try { if (previousArrays != null) previousArrays.close(); }
            finally { if (previousBuffer != null) previousBuffer.close(); }
        });
    }

    private static int instanceCount(HashMap<BakedRail, ArrayList<Matrix4f>> rails) {
        int count = 0;
        for (ArrayList<Matrix4f> transforms : rails.values()) count = Math.addExact(count, transforms.size());
        return count;
    }

    @Override
    public void enqueue(BatchManager batchManager, ShaderProp shaderProp) {
        if (vertArrays == null) return;

        if (instanceBuf.size < 1) return;
        VertAttrState attrState = new VertAttrState().setOverlayUVNoOverlay();
        if (!RailRenderDispatcher.isHoldingRailItem) attrState.setColor(-1);
        batchManager.enqueue(vertArrays, new EnqueueProp(attrState), shaderProp);
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            cancelBuild();
            InstanceBuf buffer = instanceBuf; VertArrays arrays = vertArrays;
            instanceBuf = null; vertArrays = null;
            try { if (arrays != null) arrays.close(); }
            finally { if (buffer != null) buffer.close(); }
        }
    }

    private static final class UploadedInstances {
        final InstanceBuf buffer;
        final VertArrays arrays;
        final float yMin, yMax;
        UploadedInstances(InstanceBuf buffer, VertArrays arrays, float yMin, float yMax) {
            this.buffer = buffer; this.arrays = arrays; this.yMin = yMin; this.yMax = yMax;
        }
        void close() {
            try { if (arrays != null) arrays.close(); }
            finally { if (buffer != null) buffer.close(); }
        }
    }
}
