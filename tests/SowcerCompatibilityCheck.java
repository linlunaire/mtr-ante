package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.batch.*;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.model.Mesh;
import cn.zbx1425.sowcer.object.*;
import cn.zbx1425.sowcer.shader.ShaderManager;
import cn.zbx1425.sowcer.util.AttrUtil;
import cn.zbx1425.sowcer.util.DrawContext;
import cn.zbx1425.sowcer.vertex.*;
import cn.zbx1425.sowcerext.model.*;
import cn.zbx1425.sowcerext.model.integration.BufferSourceProxy;
import cn.zbx1425.sowcerext.reuse.DrawScheduler;
import cn.zbx1425.sowcerext.reuse.ModelManager;
import cn.zbx1425.mtrsteamloco.scripting.util.client.DynamicModelHolder;
import mtr.mappings.RenderBufferSource;
import mtr.mappings.RenderSnapshot;
import mtr.mappings.RetainedGeometry;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Actual RawMesh -> upload/layout -> immutable batch -> MTR submit-node path, no GL context. */
public final class SowcerCompatibilityCheck {
    private static final int COLOR = 0x80123456, LIGHT = 0x00B00050, OVERLAY = 0x00090004;
    private static int assertions;

    public static void main(String[] args) throws Exception {
        buffers();
        globalAndReplay();
        instances();
        asyncSnapshot();
        schedulerAndHolder();
        materialRoundTrip();
        coloredSeams();
        packedLayout();
        materialPipelineFlags();
        uploadInvalidation();
        retainedSnapshots();
        retainedMeshAllocation();
        cn.zbx1425.sowcer.ContextCapability.checkContextVersion();
        require(cn.zbx1425.sowcer.ContextCapability.supportVertexAttribDivisor, "Portable instance layout incorrectly requires a GL context");
        require(cn.zbx1425.sowcer.ContextCapability.contextVersion == 0, "Uninitialized backend fabricated an OpenGL version");
        System.out.println("PASS: SOWCER real CPU upload, attributes, instancing, retained ownership, immutable delayed submission and async snapshot; " + assertions + " assertions (no GPU)");
    }

    private static VertAttrMapping mapping(boolean instanced, boolean vertexColor) {
        var builder = new VertAttrMapping.Builder();
        for (var type : VertAttrType.values()) builder.set(type, VertAttrSrc.GLOBAL);
        builder.set(VertAttrType.POSITION, VertAttrSrc.VERTEX_BUF).set(VertAttrType.NORMAL, VertAttrSrc.VERTEX_BUF)
                .set(VertAttrType.UV_TEXTURE, VertAttrSrc.VERTEX_BUF);
        if (instanced) builder.set(VertAttrType.MATRIX_MODEL, VertAttrSrc.INSTANCE_BUF)
                .set(VertAttrType.COLOR, VertAttrSrc.INSTANCE_BUF_OR_GLOBAL).set(VertAttrType.UV_LIGHTMAP, VertAttrSrc.INSTANCE_BUF_OR_GLOBAL);
        if (vertexColor) builder.set(VertAttrType.COLOR, VertAttrSrc.VERTEX_BUF_OR_GLOBAL).set(VertAttrType.UV_LIGHTMAP, VertAttrSrc.VERTEX_BUF_OR_GLOBAL);
        return builder.build();
    }

    private static RawMesh raw() {
        var raw = new RawMesh(new MaterialProp());
        for (int i = 0; i < 3; i++) {
            var vertex = new Vertex(new Vector3f(i == 1 ? 1 : 0, i == 2 ? 1 : 0, 0), new Vector3f(1, 1, 0));
            vertex.u = 0.25F; vertex.v = 0.75F; vertex.color = 0x80563412; vertex.light = LIGHT;
            raw.vertices.add(vertex);
        }
        raw.faces.add(new Face(new int[]{0, 1, 2}));
        return raw;
    }

    private static void buffers() {
        VertBuf buffer = new VertBuf();
        ByteBuffer input = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(123).putInt(456);
        buffer.upload(input, VertBuf.USAGE_STATIC_DRAW);
        input.putInt(0, 999);
        require(input.position() == 8 && buffer.snapshot().getInt(0) == 123, "Upload must copy bytes without changing input position");
        require(buffer.snapshot().isReadOnly(), "Uploaded data is mutable");
        buffer.close(); buffer.close();
        rejects(() -> buffer.snapshot(), "Closed upload remained accessible");
        rejects(() -> buffer.upload(input, VertBuf.USAGE_STATIC_DRAW), "Closed upload resurrected");
    }

    private static void uploadInvalidation() {
        var layout = mapping(false, true);
        try (var mesh = raw().upload(layout); var array = new VertArray()) {
            array.create(mesh, layout, null);
            var state = new VertAttrState().setOverlayUV(OVERLAY)
                    .setModelMatrix(new Matrix4f(new org.joml.Matrix4f().translation(7, 0, 0)));
            var initial = array.capture(state, ShaderProp.DEFAULT);
            var batches = new BatchManager();
            batches.enqueue(array, new EnqueueProp(state), ShaderProp.DEFAULT);

            var previousVertices = mesh.vertBuf.snapshot();
            var replacement = ByteBuffer.allocate(previousVertices.remaining()).order(previousVertices.order());
            replacement.put(previousVertices);
            replacement.putFloat(layout.pointers.get(VertAttrType.POSITION), 12);
            mesh.vertBuf.upload(replacement, VertBuf.USAGE_DYNAMIC_DRAW);
            require(array.capture(state, ShaderProp.DEFAULT).getFirst().a().x() == 19,
                    "Retained geometry ignored a vertex buffer upload");
            require(initial.getFirst().a().x() == 7, "Vertex upload mutated an earlier capture");
            var next = new BatchManager();
            next.enqueue(array, new EnqueueProp(state), ShaderProp.DEFAULT);
            require(replay(snapshot(next), (float) (1 / Math.sqrt(2))).equals(List.of(19F, 8F, 7F)),
                    "Retained queued geometry ignored a vertex buffer upload");

            var indices = ByteBuffer.allocate(12).order(ByteOrder.nativeOrder());
            indices.putInt(1).putInt(0).putInt(2);
            mesh.indexBuf.upload(indices, VertBuf.USAGE_DYNAMIC_DRAW);
            var reordered = array.capture(state, ShaderProp.DEFAULT).getFirst();
            require(reordered.a().x() == 8 && reordered.b().x() == 19,
                    "Retained geometry ignored an index buffer upload");
            next.enqueue(array, new EnqueueProp(state), ShaderProp.DEFAULT);
            require(replay(snapshot(next), (float) (1 / Math.sqrt(2))).equals(List.of(8F, 19F, 7F)),
                    "Retained queued geometry ignored an index buffer upload");

            require(replay(snapshot(batches), (float) (1 / Math.sqrt(2))).equals(List.of(7F, 8F, 7F)),
                    "Upload changed geometry already queued for a delayed frame");
        }
    }

    /** Exercises the rail call site, including a moving camera, without a graphics context. */
    private static void retainedMeshAllocation() {
        final int triangleCount = 4096, warmupFrames = 48, measuredFrames = 32;
        final long maxBytesPerFrame = 64 * 1024;
        var layout = mapping(false, true);
        var raw = new RawMesh(new MaterialProp());
        for (int face = 0; face < triangleCount; face++) {
            int first = raw.vertices.size();
            for (int corner = 0; corner < 3; corner++) {
                var vertex = new Vertex(new Vector3f(face * 2 + (corner == 1 ? 1 : 0), corner == 2 ? 1 : 0, 0),
                        new Vector3f(0, 1, 0));
                vertex.color = 0x80563412; vertex.light = LIGHT;
                raw.vertices.add(vertex);
            }
            raw.faces.add(new Face(new int[]{first, first + 1, first + 2}));
        }
        var allocationBean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        require(allocationBean.isThreadAllocatedMemorySupported(), "JVM cannot verify retained mesh allocation budget");
        allocationBean.setThreadAllocatedMemoryEnabled(true);
        final long thread = Thread.currentThread().threadId();
        try (var mesh = raw.upload(layout); var array = new VertArray()) {
            array.create(mesh, layout, null);
            require(array.getFaceCount() == triangleCount, "Performance fixture lost geometry during upload");
            var batches = new BatchManager();
            for (int frame = 0; frame < warmupFrames; frame++) enqueueRailFrame(batches, array, frame);
            final long allocationStart = allocationBean.getThreadAllocatedBytes(thread);
            final long start = System.nanoTime();
            for (int frame = 0; frame < measuredFrames; frame++) enqueueRailFrame(batches, array, frame + warmupFrames);
            final long duration = System.nanoTime() - start;
            final long bytesPerFrame = (allocationBean.getThreadAllocatedBytes(thread) - allocationStart) / measuredFrames;
            System.out.printf(java.util.Locale.ROOT,
                    "Retained rail enqueue: %,d triangles, %,d B/frame, %.3f ms/frame; budget %,d B/frame%n",
                    triangleCount, bytesPerFrame, duration / 1_000_000.0 / measuredFrames, maxBytesPerFrame);
            require(bytesPerFrame <= maxBytesPerFrame,
                    "Retained rail enqueue allocates per vertex instead of reusing uploaded geometry: " + bytesPerFrame + " B/frame");
        }
    }

    private static void enqueueRailFrame(BatchManager batches, VertArray array, int frame) {
        // MeshBuildingRailChunk supplies its changing camera transform through the global model matrix.
        var camera = new Matrix4f(new org.joml.Matrix4f().translation(frame * 0.125F, 2, -5));
        var state = new VertAttrState().setModelMatrix(camera).setOverlayUV(OVERLAY).setColor(-1);
        batches.enqueue(array, new EnqueueProp(state), ShaderProp.DEFAULT);
        batches.clear();
    }

    private static void retainedSnapshots() {
        var layout = mapping(false, true);
        var mesh = raw().upload(layout);
        var array = new VertArray(); array.create(mesh, layout, null);
        var model = new Matrix4f(new org.joml.Matrix4f().translation(2, 3, 4));
        var state = new VertAttrState().setOverlayUV(OVERLAY).setModelMatrix(model);
        var batches = new BatchManager();
        batches.enqueue(array, new EnqueueProp(state), ShaderProp.DEFAULT);
        var first = retained(snapshot(batches));
        var expected = array.capture(state, ShaderProp.DEFAULT).getFirst();
        require(emittedValues(first).equals(List.of(expected.a(), expected.b(), expected.c())),
                "Retained node changed positions, UVs, normals or packed attributes");

        model.translate(10, 20, 30);
        batches.enqueue(array, new EnqueueProp(state), ShaderProp.DEFAULT);
        var moved = retained(snapshot(batches));
        require(first.geometry() == moved.geometry(), "Camera movement rebuilt immutable object-space geometry");
        require(first.x() == 2 && first.y() == 3 && first.z() == 4 && moved.x() == 12 && moved.y() == 23 && moved.z() == 34,
                "Queued retained translation aliased the mutable camera matrix");
        first.pose().translate(100, 100, 100);
        require(first.x() == 2, "Retained pose accessor exposed mutable captured state");

        state.setColor(0xAABBCCDD).setLightmapUV(AttrUtil.exchangeLightmapUVBits(123)).setOverlayUV(456);
        batches.enqueue(array, new EnqueueProp(state), ShaderProp.DEFAULT);
        var changed = retained(snapshot(batches));
        require(changed.geometry() != first.geometry(), "Changing global attributes reused stale geometry");
        var changedValue = emittedValues(changed).getFirst();
        require(changedValue.color() == 0xDDAABBCC && changedValue.light() == 123 && changedValue.overlay() == 456,
                "Retained geometry lost changed global color/light/overlay");
        require(emittedValues(first).getFirst().color() == COLOR, "Changed attributes mutated an earlier snapshot");

        array.materialProp.attrState.setColor(0x10203040);
        batches.enqueue(array, new EnqueueProp(state), ShaderProp.DEFAULT);
        var materialChanged = retained(snapshot(batches));
        require(emittedValues(materialChanged).getFirst().color() == 0x40102030,
                "Retained cache ignored higher-priority material attributes");
        // Keep the next draw queued until its array and owned uploads are all closed.
        batches.enqueue(array, new EnqueueProp(state), ShaderProp.DEFAULT);
        array.close(); mesh.close();
        require(mesh.vertBuf.id == 0 && mesh.indexBuf.id == 0, "Retained CPU cache leaked upload ownership");
        require(retained(snapshot(batches)).geometry() == materialChanged.geometry(),
                "Close-before-submission invalidated immutable retained geometry");
        require(emittedValues(first).size() == 3, "Closing the model invalidated a prior retained frame");

        var coldMesh = raw().upload(layout);
        var coldArray = new VertArray(); coldArray.create(coldMesh, layout, null);
        batches.enqueue(coldArray, new EnqueueProp(new VertAttrState().setModelMatrix(
                new Matrix4f(new org.joml.Matrix4f().translation(3, 0, 0)))), ShaderProp.DEFAULT);
        coldArray.close(); coldMesh.close();
        var cold = emittedValues(retained(snapshot(batches)));
        require(cold.size() == 3 && cold.getFirst().x() == 3 && cold.getFirst().color() == COLOR,
                "Closing before the first retained replay invalidated cold geometry");
    }

    private static RetainedGeometry.Submit retained(RenderSnapshot snapshot) {
        var storage = new SubmitNodeStorage();
        snapshot.submit(new PoseStack(), storage, new CameraRenderState());
        var nodes = new ArrayList<RetainedGeometry.Submit>();
        storage.drainPhases(phase -> phase.sortInto((node, blending) -> {
            require(node instanceof RetainedGeometry.Submit, "Opaque translated fixture did not take the retained path");
            nodes.add((RetainedGeometry.Submit) node);
        }));
        require(nodes.size() == 1, "Retained fixture submitted a duplicate or missing draw");
        return nodes.getFirst();
    }

    private static List<VertArray.Value> emittedValues(RetainedGeometry.Submit submit) {
        var result = new ArrayList<VertArray.Value>();
        submit.geometry().emit(new VertexConsumer() {
            private float x, y, z, u, v;
            private int color, light, overlay;
            public VertexConsumer addVertex(float x, float y, float z) { this.x = x; this.y = y; this.z = z; return this; }
            public VertexConsumer setColor(int r, int g, int b, int a) { color = a << 24 | r << 16 | g << 8 | b; return this; }
            public VertexConsumer setColor(int color) { this.color = color; return this; }
            public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; return this; }
            public VertexConsumer setUv1(int u, int v) { overlay = u | v << 16; return this; }
            public VertexConsumer setUv2(int u, int v) { light = u | v << 16; return this; }
            public VertexConsumer setNormal(float nx, float ny, float nz) {
                result.add(new VertArray.Value(x + submit.x(), y + submit.y(), z + submit.z(), nx, ny, nz, u, v, color, light, overlay));
                return this;
            }
            public VertexConsumer setLineWidth(float width) { return this; }
        });
        return result;
    }

    private static void materialRoundTrip() throws Exception {
        for (boolean billboard : new boolean[]{false, true}) {
            var material = new MaterialProp(); material.cutoutHack = true;
            if (billboard) material.setMatixProcess(VertAttrState.BILLBOARD);
            var bytes = new java.io.ByteArrayOutputStream();
            material.serializeTo(new java.io.DataOutputStream(bytes));
            var restored = new MaterialProp(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
            require(restored.useMatixProcess() == billboard, "NMB ordinary material became a billboard");
            require(material.copy().cutoutHack, "Captured material lost cutout pass flag");
        }
    }

    private static void materialPipelineFlags() {
        for (String shader : new String[]{"rendertype_entity_cutout", "rendertype_entity_translucent_cull", "rendertype_beacon_beam"}) {
            for (boolean transparent : new boolean[]{false, true}) for (boolean cutout : new boolean[]{false, true}) for (boolean depth : new boolean[]{false, true}) {
                var material = new MaterialProp(shader); material.translucent = transparent; material.cutoutHack = cutout; material.writeDepthBuf = depth;
                var type = material.getBlazeRenderType();
                require(type.pipeline().getColorTargetState().blendFunction().isPresent() == (transparent || cutout), "Material blending/cutout flag ignored");
                require(type.pipeline().getDepthStencilState().writeDepth() == depth, "Material depth-write override ignored");
                require(type == material.copy().getBlazeRenderType(), "Equivalent material pipeline was not cached");
            }
        }
    }

    private static void coloredSeams() {
        var raw = raw();
        for (int i = 0; i < 3; i++) { var copy = raw.vertices.get(i).copy(); copy.color = 0xFF0000FF; copy.light = 123; raw.vertices.add(copy); }
        raw.faces.add(new Face(new int[]{3, 4, 5}));
        var layout = mapping(false, true);
        try (var mesh = raw.upload(layout); var array = new VertArray()) {
            array.create(mesh, layout, null);
            var faces = array.capture(null, ShaderProp.DEFAULT);
            require(faces.size() == 2 && faces.get(0).a().color() == COLOR && faces.get(1).a().color() == 0xFFFF0000,
                    "Vertex deduplication merged separate rail colors");
            require(faces.get(1).a().light() == 123, "Vertex deduplication merged separate rail light levels");
            require(raw.vertices.size() == 6 && raw.faces.get(1).vertices[0] == 3, "Upload mutated source indices");
        }
    }

    private static void packedLayout() {
        var builder = new VertAttrMapping.Builder();
        for (var type : VertAttrType.values()) builder.set(type, VertAttrSrc.VERTEX_BUF);
        var layout = builder.build();
        var raw = raw(); raw.materialProp.attrState.setOverlayUV(OVERLAY);
        raw.materialProp.attrState.setModelMatrix(new Matrix4f(new org.joml.Matrix4f().translation(5, 6, 7)));
        try (var mesh = raw.upload(layout); var array = new VertArray()) {
            array.create(mesh, layout, null);
            var value = array.capture(null, ShaderProp.DEFAULT).getFirst().a();
            require(value.overlay() == OVERLAY && value.light() == LIGHT, "Overlay packing displaced following attributes");
            require(value.x() == 5 && value.y() == 6 && value.z() == 7, "Byte-unaligned model matrix lost precision/offset");
        }
    }

    private static void globalAndReplay() {
        var layout = mapping(false, true);
        var raw = raw();
        Mesh mesh = raw.upload(layout);
        VertArray array = new VertArray(); array.create(mesh, layout, null);
        var model = new Matrix4f(new org.joml.Matrix4f().translation(10, 20, 30).scale(2, 1, 1));
        var state = new VertAttrState().setModelMatrix(model).setOverlayUV(OVERLAY);
        var shader = new ShaderProp();
        var faces = array.capture(state, shader);
        require(faces.size() == 1, "Raw triangle upload count");
        var v = faces.getFirst().a();
        require(v.color() == COLOR && v.light() == LIGHT && v.overlay() == OVERLAY, "Packed vertex attributes changed");
        near(v.nx(), (float) (1 / Math.sqrt(5)), "Nonuniform scale normal X");
        near(v.ny(), (float) (2 / Math.sqrt(5)), "Nonuniform scale normal Y");
        require(v.x() == 10 && v.y() == 20 && v.z() == 30 && v.u() == 0.25F && v.v() == 0.75F, "Geometry transform/UV");
        var copy = array.copyForMaterialChanges();
        copy.materialProp.attrState.setColor(0xAABBCCDD);
        require(copy.capture(state, shader).getFirst().a().color() == 0xDDAABBCC, "Material override priority");
        require(array.capture(state, shader).getFirst().a().color() == COLOR, "Material copy modified original");
        var batches = new BatchManager();
        batches.enqueue(array, new EnqueueProp(state), shader);
        mesh.close(); array.close(); array.close();
        require(copy.capture(state, shader).size() == 1, "Sibling close invalidated retained material copy");
        copy.close();
        require(mesh.vertBuf.id == 0 && mesh.indexBuf.id == 0, "Final retained owner leaked uploads");
        model.translate(1000, 1000, 1000); raw.clear(); state.setColor(0);
        RenderSnapshot snapshot;
        var context = new DrawContext();
        try (var source = RenderBufferSource.begin(Vec3.ZERO)) {
            batches.drawAll(new ShaderManager(), context);
            batches.drawAll(new ShaderManager(), context);
            snapshot = source.snapshot();
        }
        context.resetFrameProfiler();
        require(context.drawCallCount == 1 && context.singleFaceCount == 1 && context.batchCount == 1, "Closed-array statistics / duplicate batch replay");
        var positions = replay(snapshot);
        require(positions.equals(List.of(10F, 12F, 10F)), "Delayed immutable replay, winding or vertex count changed");
    }

    private static void instances() {
        var layout = mapping(true, false);
        Mesh mesh = raw().upload(layout);
        var instances = new InstanceBuf(2);
        ByteBuffer data = ByteBuffer.allocate(layout.strideInstance * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 2; i++) {
            data.putInt(0x80563412).putInt(LIGHT);
            for (float value : new org.joml.Matrix4f().translation(i * 5, 0, 0).get(new float[16])) data.putFloat(value);
        }
        instances.upload(data, VertBuf.USAGE_DYNAMIC_DRAW);
        var array = new VertArray(); array.create(mesh, layout, instances);
        var state = new VertAttrState().setOverlayUV(OVERLAY);
        var shader = new ShaderProp().setViewMatrix(new Matrix4f(new org.joml.Matrix4f().translation(7, 0, 0)));
        var faces = array.capture(state, shader);
        require(faces.size() == 2 && faces.get(0).a().x() == 7 && faces.get(1).a().x() == 12, "Instance matrix / view transform");
        require(faces.get(1).a().color() == COLOR && faces.get(1).a().light() == LIGHT, "Instance packed attributes");
        state.setColor(0x10203040).setLightmapUV(AttrUtil.exchangeLightmapUVBits(123));
        require(array.capture(state, shader).getFirst().a().color() == 0x40102030, "Toggleable enqueue override");
        require(array.capture(state, shader).getFirst().a().light() == 123, "Global light word order");
        data.putInt(0, 0); instances.size = 0;
        require(array.capture(state, shader).isEmpty(), "Zero instance count");
        require(faces.getFirst().a().color() == COLOR, "Captured instance data mutated");
        array.close(); instances.close(); mesh.close();
    }

    private static void asyncSnapshot() {
        var layout = mapping(false, true);
        var raw = raw();
        var upload = raw.uploadAsync(layout);
        raw.clear(); raw.materialProp.attrState.setColor(0);
        Mesh mesh = upload.get();
        require(mesh.indexBuf.faceCount == 1, "Async upload reads mutated face count instead of captured count");
        require(upload.get() == mesh, "Repeated supplier created an uninitialized mesh");
        var array = new VertArray(); array.create(mesh, layout, null);
        require(array.capture(null, ShaderProp.DEFAULT).getFirst().a().color() == COLOR, "Async material input was not captured");
        array.close(); mesh.close();
    }

    private static List<Float> replay(RenderSnapshot snapshot) {
        return replay(snapshot, (float) (1 / Math.sqrt(5)));
    }

    private static List<Float> replay(RenderSnapshot snapshot, float normalX) {
        var storage = new SubmitNodeStorage();
        snapshot.submit(new PoseStack(), storage, new CameraRenderState());
        var positions = new ArrayList<Float>();
        storage.drainPhases(phase -> phase.sortInto((node, blending) -> {
            float translation = node instanceof RetainedGeometry.Submit retained ? retained.x() : 0;
            emitNode(node, new VertexConsumer() {
                public VertexConsumer addVertex(float x, float y, float z) { positions.add(x + translation); return this; }
                public VertexConsumer setColor(int r, int g, int b, int a) { require((a << 24 | r << 16 | g << 8 | b) == COLOR, "Replay color"); return this; }
                public VertexConsumer setColor(int color) { require(color == COLOR, "Replay color"); return this; }
                public VertexConsumer setUv(float u, float v) { require(u == 0.25F && v == 0.75F, "Replay UV"); return this; }
                public VertexConsumer setUv1(int u, int v) { require((u | v << 16) == OVERLAY, "Replay overlay"); return this; }
                public VertexConsumer setUv2(int u, int v) { require((u | v << 16) == LIGHT, "Replay light"); return this; }
                public VertexConsumer setNormal(float x, float y, float z) { near(x, normalX, "Replay normal"); return this; }
                public VertexConsumer setLineWidth(float width) { return this; }
            });
        }));
        return positions;
    }

    private static RenderSnapshot snapshot(BatchManager batches) {
        try (var source = RenderBufferSource.begin(Vec3.ZERO)) {
            batches.drawAll(new ShaderManager(), new DrawContext());
            return source.snapshot();
        }
    }

    private static void emitNode(Object node, VertexConsumer consumer) {
        if (node instanceof RetainedGeometry.Submit retained) {
            retained.geometry().emit(consumer);
        } else {
            var geometry = (CustomFeatureRenderer.Submit) node;
            geometry.customGeometryRenderer().render(geometry.pose(), consumer);
        }
    }

    private static RawModel rawModel() { var result = new RawModel(); result.append(raw()); return result; }

    private static int vertices(RenderSnapshot snapshot) {
        var storage = new SubmitNodeStorage();
        snapshot.submit(new PoseStack(), storage, new CameraRenderState());
        int[] count = {0};
        storage.drainPhases(phase -> phase.sortInto((node, blending) -> {
            emitNode(node, new VertexConsumer() {
                public VertexConsumer addVertex(float x, float y, float z) { count[0]++; return this; }
                public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
                public VertexConsumer setColor(int color) { return this; }
                public VertexConsumer setUv(float u, float v) { return this; }
                public VertexConsumer setUv1(int u, int v) { return this; }
                public VertexConsumer setUv2(int u, int v) { return this; }
                public VertexConsumer setNormal(float x, float y, float z) { return this; }
                public VertexConsumer setLineWidth(float width) { return this; }
            });
        }));
        return count[0];
    }

    private static int commit(DrawScheduler scheduler, boolean blaze) {
        try (var source = RenderBufferSource.begin(Vec3.ZERO)) {
            var proxy = new BufferSourceProxy(source);
            var context = new DrawContext(); context.drawWithBlaze = blaze;
            scheduler.commit(proxy, context); proxy.commit();
            return vertices(source.snapshot());
        }
    }

    private static void schedulerAndHolder() {
        var scheduler = new DrawScheduler();
        scheduler.enqueue(rawModel(), new Matrix4f(), LIGHT, OVERLAY);
        require(commit(scheduler, true) == 3, "Raw-only queue was skipped");
        require(commit(scheduler, true) == 0, "Raw-only queue was replayed next frame");
        for (boolean blaze : new boolean[]{false, true}) {
            var holder = new DynamicModelHolder();
            var raw = rawModel();
            holder.uploadLater(raw);
            raw.applyTranslation(1000, 0, 0); raw.meshList.clear();
            var first = holder.getUploadedModel();
            require(first.opaqueParts.getFaceCount() == 1, "Deferred holder retained mutable raw geometry");
            var firstIndex = first.uploadedOpaqueParts.meshList.getFirst().indexBuf;
            var second = rawModel(); second.applyTranslation(5, 0, 0);
            holder.uploadLater(rawModel()); holder.uploadLater(second);
            var current = holder.getUploadedModel();
            require(current.opaqueParts.meshList.values().iterator().next().vertices.getFirst().position.x() == 5, "Deferred latest upload did not win");
            require(firstIndex.id == 0, "Replaced untracked model leaked buffer ownership");
            holder.withUploadedModel(model -> scheduler.enqueue(model, new Matrix4f(), LIGHT, OVERLAY));
            var currentIndex = current.uploadedOpaqueParts.meshList.getFirst().indexBuf;
            holder.close(); holder.close();
            require(commit(scheduler, blaze) == 3, "Holder close invalidated queued frame");
            require(commit(scheduler, blaze) == 0 && currentIndex.id == 0, "Queued frame leaked model data or replayed");
            require(holder.getUploadedModel() == null, "Closed holder recreated its model");
            rejects(() -> holder.uploadLater(rawModel()), "Closed holder accepted deferred upload");
            rejects(() -> holder.uploadNow(rawModel()), "Closed holder accepted immediate upload");
        }
        var holder = new DynamicModelHolder();
        holder.uploadLater(rawModel()); holder.close();
        require(holder.getUploadedModel() == null, "Pending upload survived close");
        var modelUpload = rawModel().uploadAsync(ModelManager.DEFAULT_MAPPING);
        var model = modelUpload.get();
        require(modelUpload.get() == model && model.meshList.size() == 1, "Async model repeated its mesh uploads");
        model.close();
    }

    private static void rejects(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); } catch (IllegalStateException expected) { assertions++; }
    }
    private static void near(float actual, float expected, String message) { require(Math.abs(actual - expected) < 0.0001F, message + ": " + actual); }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
