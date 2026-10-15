package cn.zbx1425.mtrsteamloco.render.rail;

import cn.zbx1425.mtrsteamloco.data.RailModelRegistry;
import cn.zbx1425.sowcer.batch.BatchManager;
import cn.zbx1425.sowcer.batch.EnqueueProp;
import cn.zbx1425.sowcer.batch.ShaderProp;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.model.Model;
import cn.zbx1425.sowcer.model.VertArrays;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import cn.zbx1425.sowcer.vertex.VertAttrSrc;
import cn.zbx1425.sowcer.vertex.VertAttrState;
import cn.zbx1425.sowcer.vertex.VertAttrType;
import cn.zbx1425.sowcerext.model.*;
import net.minecraft.client.renderer.LightTexture;
import cn.zbx1425.mtrsteamloco.data.RailExtraSupplier;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import cn.zbx1425.mtrsteamloco.data.RailModelProperties;
import com.mojang.blaze3d.systems.RenderSystem;
import mtr.data.Rail;
import net.minecraft.world.level.LightLayer;
import cn.zbx1425.sowcerext.reuse.DrawScheduler;
import net.minecraft.world.phys.Vec3;
import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.mtrsteamloco.render.RenderUtil;
import cn.zbx1425.mtrsteamloco.ClientConfig;

import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class MeshBuildingRailChunk extends RailChunkBase {

    private final RawModel railModel;

    private boolean deform = false;
    private Model uploadedCombinedModel;
    private VertArrays vertArrays;

	private static final double ACCEPT_THRESHOLD = 1e-4;
    private static final double HALF_ACCEPT_THRESHOLD = ACCEPT_THRESHOLD / 2;


    private static final VertAttrMapping RAIL_MAPPING = new VertAttrMapping.Builder()
            .set(VertAttrType.POSITION, VertAttrSrc.VERTEX_BUF)
            .set(VertAttrType.COLOR, VertAttrSrc.VERTEX_BUF_OR_GLOBAL)
            .set(VertAttrType.UV_TEXTURE, VertAttrSrc.VERTEX_BUF)
            .set(VertAttrType.UV_OVERLAY, VertAttrSrc.GLOBAL)
            .set(VertAttrType.UV_LIGHTMAP, VertAttrSrc.VERTEX_BUF_OR_GLOBAL)
            .set(VertAttrType.NORMAL, VertAttrSrc.VERTEX_BUF)
            .set(VertAttrType.MATRIX_MODEL, VertAttrSrc.GLOBAL)
            .build();

    protected MeshBuildingRailChunk(Long chunkId, String modelKey) {
        super(chunkId, modelKey);
        this.railModel = RailModelRegistry.getProperty(modelKey).rawModel;
    }

    @Override
    public void rebuildBuffer(Level world) {
        if (railModel == null) return;
        final boolean deforming = ClientConfig.enableRailDeform;
        final HashMap<BakedRail, DeformInput> deformation = new HashMap<>();
        scheduleBuild(rails -> estimateBytes(rails, deforming, deformation), snapshot -> {
            RawModel combinedModel = deforming ? transformModelDeform(world, snapshot, deformation)
                    : transformModel(world, snapshot);
            snapshot.checkCancelled();
            float[] bounds = bounds(snapshot);
            PreparedUpload<Model> modelUpload = combinedModel.prepareUpload(RAIL_MAPPING);
            try { return PreparedUpload.of(modelUpload.byteSize(), () -> {
                Model model = modelUpload.get();
                VertArrays arrays = null;
                try {
                    arrays = VertArrays.createAll(model, RAIL_MAPPING, null);
                    return new UploadedMesh(model, arrays, bounds[0], bounds[1]);
                } catch (RuntimeException | Error ex) {
                    if (arrays != null) try { arrays.close(); } catch (RuntimeException | Error cleanup) { ex.addSuppressed(cleanup); }
                    try { model.close(); } catch (RuntimeException | Error cleanup) { ex.addSuppressed(cleanup); }
                    throw ex;
                }
            }, modelUpload::close); }
            catch (RuntimeException | Error ex) { modelUpload.close(); throw ex; }
        }, (prepared, snapshot) -> {
            UploadedMesh next = prepared.get();
            try { setBoundingBox(next.yMin, next.yMax); }
            catch (RuntimeException | Error ex) { next.close(); throw ex; }
            Model previousModel = uploadedCombinedModel;
            VertArrays previousArrays = vertArrays;
            uploadedCombinedModel = next.model; vertArrays = next.arrays; deform = deforming;
            try { if (previousArrays != null) previousArrays.close(); }
            finally { if (previousModel != null) previousModel.close(); }
        });
    }

    private long estimateBytes(HashMap<BakedRail, ArrayList<Matrix4f>> rails, boolean deforming,
            HashMap<BakedRail, DeformInput> deformation) {
        long bytes = 0;
        long perPiece = deforming ? 0 : railModel.estimatedUploadBytes(RAIL_MAPPING);
        for (Map.Entry<BakedRail, ArrayList<Matrix4f>> entry : rails.entrySet()) {
            long count;
            if (deforming) {
                DeformInput input = new DeformInput(entry.getKey());
                deformation.put(entry.getKey(), input);
                double length = input.rail.getLength() - ACCEPT_THRESHOLD;
                double step = RailRepeatGeometry.step(length, input.interval);
                // Upper bound includes all repeats, even those subsequently rejected by chunk position.
                count = step > 0 ? (long) Math.ceil(length / step) : 0;
                if (count > 0) count = Math.addExact(count, 1);
                bytes = Math.addExact(bytes, Math.multiplyExact(count, input.model.estimatedUploadBytes(RAIL_MAPPING)));
            } else {
                count = entry.getValue().size();
                bytes = Math.addExact(bytes, Math.multiplyExact(count, perPiece));
            }
        }
        return bytes;
    }

    private RawModel transformModel(Level world, BuildSnapshot snapshot) {
        RawModel combinedModel = new RawModel();

        for (Map.Entry<BakedRail, ArrayList<Matrix4f>> entry : snapshot.rails.entrySet()) {
            ArrayList<Matrix4f> railSpan = entry.getValue();
            for (Matrix4f pieceMat : railSpan) {
                snapshot.checkCancelled();
                final Vector3f lightPos = pieceMat.getTranslationPart();
                final BlockPos lightBlockPos = new BlockPos(Mth.floor(lightPos.x()), Mth.floor(lightPos.y() + 0.1), Mth.floor(lightPos.z()));
                final int light = LightTexture.pack(world.getBrightness(LightLayer.BLOCK, lightBlockPos), world.getBrightness(LightLayer.SKY, lightBlockPos));
                combinedModel.appendTransformed(railModel, pieceMat, entry.getKey().color, light);
            }
        }

        return  combinedModel;
    }

    private float[] bounds(BuildSnapshot snapshot) {
        float yMin = 256, yMax = -64;
        for (Map.Entry<BakedRail, ArrayList<Matrix4f>> entry : snapshot.rails.entrySet()) {
            for (Matrix4f pieceMat : entry.getValue()) {
                snapshot.checkCancelled();
                final Vector3f lightPos = pieceMat.getTranslationPart();
                yMin = Math.min(yMin, lightPos.y());
                yMax = Math.max(yMax, lightPos.y());
            }
        }

        if (yMin > yMax) yMin = yMax;
        return new float[] {yMin, yMax};
    }

    private RawModel transformModelDeform(Level world, BuildSnapshot snapshot, Map<BakedRail, DeformInput> inputs) {
        RawModel combinedModel = new RawModel();
        for (BakedRail bakedRail : snapshot.rails.keySet()) {
            snapshot.checkCancelled();
            DeformInput input = inputs.get(bakedRail);
            Rail rail = input.rail;
            boolean reverse = input.reverse;
            float interval = input.interval;
            float yOffset = input.yOffset;
            RawModel railModel = input.model;
            final double length = rail.getLength() - ACCEPT_THRESHOLD;
            final double ins = RailRepeatGeometry.step(length, interval);
            if (ins == 0) continue;
            final double halfIns = ins / 2;

            for (double i = halfIns; i <= length ; i = RailRepeatGeometry.advance(i, ins)) {
                snapshot.checkCancelled();
                Vec3 mid = rail.getPosition(i);
                if (BakedRail.chunkIdFromWorldPos((int) mid.x, (int) mid.z) != chunkId) continue;

                Vector3f lightPos = new Vector3f(mid);
                BlockPos lightBlockPos = new BlockPos(Mth.floor(lightPos.x()), Mth.floor(lightPos.y() + 0.1), Mth.floor(lightPos.z()));
                int light = LightTexture.pack(world.getBrightness(LightLayer.BLOCK, lightBlockPos), world.getBrightness(LightLayer.SKY, lightBlockPos));

                RawModel rm = railModel.copy();
                for (Map.Entry<MaterialProp, RawMesh> entry : rm.meshList.entrySet()) {
                    for (Vertex v : entry.getValue().vertices) {
                        snapshot.checkCancelled();
                        Matrix4f mat = getLookAtMat(rail, transform(i, ins, interval, v.position.z(), reverse), yOffset, reverse);
                        v.position = mat.transform(new Vector3f(v.position.x(), v.position.y(), 0));
                        v.normal = mat.transform3(v.normal);
                        v.light = light;
                        v.color = bakedRail.color;
                    }
                }
                combinedModel.append(rm);
            }
        }
        return combinedModel;
    }

    private double transform(double i, double di, double interval, double value, boolean reverse) {
        return i + di * (value / interval) * (reverse ? -1 : 1);
    }

    private Matrix4f getLookAtMat(Rail rail, double value, float yOffset, boolean reverse) {
        if (value < HALF_ACCEPT_THRESHOLD) value = HALF_ACCEPT_THRESHOLD;
        else if (value + HALF_ACCEPT_THRESHOLD > rail.getLength()) value = rail.getLength() - HALF_ACCEPT_THRESHOLD;
        Vec3 from = rail.getPosition(value - HALF_ACCEPT_THRESHOLD);
        Vec3 to = rail.getPosition(value + HALF_ACCEPT_THRESHOLD);
        Vec3 mid = from.add(to).scale(0.5F);
        float roll = RailExtraSupplier.getRollAngle(rail, value);
        return BakedRail.getLookAtMat(mid, from, to, roll, yOffset, reverse);
    }

    @Override
    public void enqueue(BatchManager batchManager, ShaderProp shaderProp) {
        if (railModel == null) return;

        if (vertArrays == null) return;

        if (ClientConfig.enableRailDeform != deform) isDirty = true;

        VertAttrState attrState = new VertAttrState().setModelMatrix(shaderProp.viewMatrix).setOverlayUVNoOverlay();
        if (!RailRenderDispatcher.isHoldingRailItem) attrState.setColor(-1);
        batchManager.enqueue(vertArrays, new EnqueueProp(attrState), ShaderProp.DEFAULT);
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            cancelBuild();
            VertArrays arrays = vertArrays; Model model = uploadedCombinedModel;
            vertArrays = null; uploadedCombinedModel = null;
            try { if (arrays != null) arrays.close(); }
            finally { if (model != null) model.close(); }
        }
    }

    private static final class DeformInput {
        final Rail rail;
        final RawModel model;
        final float interval, yOffset;
        final boolean reverse;
        DeformInput(BakedRail bakedRail) {
            rail = bakedRail.rail;
            RailModelProperties prop = bakedRail.getProperties();
            model = prop.rawModel; interval = prop.repeatInterval; yOffset = prop.yOffset;
            reverse = ((RailExtraSupplier) rail).getRenderReversed();
        }
    }

    private static final class UploadedMesh {
        final Model model;
        final VertArrays arrays;
        final float yMin, yMax;
        UploadedMesh(Model model, VertArrays arrays, float yMin, float yMax) {
            this.model = model; this.arrays = arrays; this.yMin = yMin; this.yMax = yMax;
        }
        void close() { try { arrays.close(); } finally { model.close(); } }
    }
}
