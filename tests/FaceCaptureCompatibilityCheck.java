package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.shader.BlazeRenderType;
import cn.zbx1425.sowcerext.model.Vertex;
import cn.zbx1425.sowcerext.model.integration.BufferSourceProxy;
import cn.zbx1425.sowcerext.model.integration.FaceList;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.mappings.RenderBufferSource;
import mtr.mappings.RenderSnapshot;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Uses ANTE's real face batches and MTR's real capture/submission bridge, without a GPU. */
public final class FaceCaptureCompatibilityCheck {
    private static final int COLOR = 0x80123456, LIGHT = 0x00B00050, OVERLAY = 0x00090004;

    public static void main(String[] args) {
        Identifier texture = Identifier.parse("mtrsteamloco:test/capture.png");
        RenderType opaque = BlazeRenderType.entityCutout(texture);
        RenderType transparent = BlazeRenderType.entityTranslucentCull(texture);
        RenderSnapshot snapshot;
        BufferSourceProxy stale;
        List<Vertex[]> inputs = new ArrayList<>();
        try (RenderBufferSource buffers = RenderBufferSource.begin(new Vec3(100, 200, 300))) {
            BufferSourceProxy proxy = new BufferSourceProxy(buffers);
            stale = proxy;
            FaceList sorted = proxy.getBuffer(transparent, true);
            require(sorted == proxy.getBuffer(transparent, true), "Material batches no longer share their face list");
            add(sorted, 1, inputs);
            add(sorted, 9, inputs);
            add(sorted, 3, inputs);
            FaceList unsorted = proxy.getBuffer(opaque, false);
            add(unsorted, 2, inputs);
            add(unsorted, 7, inputs);
            proxy.commit();
            proxy.commit(); // An empty second commit must not replay the previous faces.
            for (Vertex[] face : inputs) {
                for (Vertex vertex : face) {
                    vertex.position.add(1000, 1000, 1000);
                    vertex.normal.mul(0);
                    vertex.u = vertex.v = -100;
                }
                face[0] = null;
            }
            snapshot = buffers.snapshot();
        }
        PoseStack pose = new PoseStack();
        pose.translate(10, 20, 30);
        SubmitNodeStorage storage = new SubmitNodeStorage();
        snapshot.submit(pose, storage, new CameraRenderState());
        pose.setIdentity();
        Map<RenderType, List<Float>> positions = new HashMap<>();
        storage.drainPhases(phase -> phase.sortInto((node, blending) -> {
            require(node instanceof CustomFeatureRenderer.Submit, "Face batch did not submit custom geometry");
            CustomFeatureRenderer.Submit geometry = (CustomFeatureRenderer.Submit) node;
            require(geometry.renderType().primitiveTopology() == PrimitiveTopology.TRIANGLES, "Triangle faces became quads");
            require(!positions.containsKey(geometry.renderType()), "A material was submitted more than once");
            List<Float> xs = new ArrayList<>();
            positions.put(geometry.renderType(), xs);
            geometry.customGeometryRenderer().render(geometry.pose(), new VertexConsumer() {
                @Override public VertexConsumer addVertex(float x, float y, float z) {
                    xs.add(x); require(y == (xs.size() % 3 == 0 ? 20.5F : 20F) && z == 30, "Captured pose/position changed"); return this;
                }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) {
                    require((a << 24 | r << 16 | g << 8 | b) == COLOR, "Face color changed"); return this;
                }
                @Override public VertexConsumer setColor(int color) { require(color == COLOR, "Face alpha/tint changed"); return this; }
                @Override public VertexConsumer setUv(float u, float v) { require(u == 0.25F && v == 0.75F, "Mutable input leaked into UVs"); return this; }
                @Override public VertexConsumer setUv1(int u, int v) { require((u | v << 16) == OVERLAY, "Overlay packing changed"); return this; }
                @Override public VertexConsumer setUv2(int u, int v) { require((u | v << 16) == LIGHT, "Light packing changed"); return this; }
                @Override public VertexConsumer setNormal(float x, float y, float z) { require(x == 0 && y == 1 && z == 0, "Mutable normal leaked into the snapshot"); return this; }
                @Override public VertexConsumer setLineWidth(float width) { return this; }
            });
        }));
        require(positions.size() == 2, "Expected two material batches");
        require(positions.get(transparent).equals(List.of(19F, 19.25F, 19F, 13F, 13.25F, 13F, 11F, 11.25F, 11F)), "Transparent faces lost back-to-front sorting or winding");
        require(positions.get(opaque).equals(List.of(12F, 12.25F, 12F, 17F, 17.25F, 17F)), "Opaque insertion order, winding or vertex count changed");
        add(stale.getBuffer(opaque, false), 0, new ArrayList<>());
        try {
            stale.commit();
            throw new AssertionError("A closed extraction source accepted another frame");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("finished"), "Unexpected closed-scope error");
        }
        System.out.println("PASS: real ANTE face capture into 26.2 submit nodes, 15 triangle vertices, material batches, sorting, attributes, immutable delayed replay and closed-scope rejection (no GPU)");
    }

    private static void add(FaceList faces, float x, List<Vertex[]> inputs) {
        Vertex[] vertices = new Vertex[3];
        for (int i = 0; i < 3; i++) {
            vertices[i] = new Vertex(new Vector3f(x + (i == 1 ? 0.25F : 0), i == 2 ? 0.5F : 0, 0), new Vector3f(0, 1, 0));
            vertices[i].u = 0.25F;
            vertices[i].v = 0.75F;
        }
        inputs.add(vertices);
        faces.addFace(vertices, COLOR, LIGHT, OVERLAY);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
