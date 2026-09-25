package cn.zbx1425.sowcer.batch;

import cn.zbx1425.sowcer.model.VertArrays;
import cn.zbx1425.sowcer.object.VertArray;
import cn.zbx1425.sowcer.shader.ShaderManager;
import cn.zbx1425.sowcer.util.DrawContext;
import mtr.mappings.RenderBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Queues immutable retained meshes where possible, with captured triangles as the fallback. */
public class BatchManager {
    private final List<RenderCall> calls = new ArrayList<>();

    public void clear() { calls.clear(); }

    public void enqueue(VertArrays model, EnqueueProp enqueue, ShaderProp shader) {
        for (VertArray array : model.meshList) enqueue(array, enqueue, shader);
    }

    public void enqueue(VertArray array, EnqueueProp enqueue, ShaderProp shader) {
        final var retained = array.captureRetained(enqueue.attrState, shader);
        calls.add(new RenderCall(array, array.materialProp.copy(),
                retained == null ? array.capture(enqueue.attrState, shader) : null, retained));
    }

    public void drawAll(ShaderManager shaders, DrawContext context) {
        final Map<Key, List<VertArray.Face>> batches = new LinkedHashMap<>();
        int retainedBatches = 0;
        for (RenderCall call : calls) {
            context.recordDrawCall(call);
            if (call.retained != null) {
                RenderBufferSource.current().drawRetained(call.retained.geometry(), call.retained.pose());
                retainedBatches++;
                continue;
            }
            final var material = call.material;
            final int stage = material.translucent ? 2 : material.cutoutHack ? 1 : 0;
            batches.computeIfAbsent(new Key(shaders.material(material), stage), key -> new ArrayList<>()).addAll(call.faces);
        }
        context.recordBatches(batches.size() + retainedBatches);
        for (var entry : batches.entrySet().stream().sorted(Comparator.comparingInt(entry -> entry.getKey().stage)).toList()) {
            if (entry.getKey().stage == 2) entry.getValue().sort(Comparator.comparingDouble(VertArray.Face::distanceSquared).reversed());
            final var consumer = RenderBufferSource.current().getBuffer(entry.getKey().type);
            for (VertArray.Face face : entry.getValue()) face.emit(consumer);
        }
        calls.clear();
    }

    private record Key(RenderType type, int stage) { }

    public static final class RenderCall {
        public final VertArray vertArray;
        public final int faceCount;
        public final boolean instanced;
        private final MaterialProp material;
        private final List<VertArray.Face> faces;
        private final VertArray.RetainedDraw retained;
        private RenderCall(VertArray array, MaterialProp material, List<VertArray.Face> faces, VertArray.RetainedDraw retained) {
            vertArray = array; this.material = material; this.faces = faces;
            this.retained = retained;
            faceCount = retained == null ? faces.size() : retained.geometry().vertexCount() / 3;
            instanced = array.instanceBuf != null;
        }
    }
}
