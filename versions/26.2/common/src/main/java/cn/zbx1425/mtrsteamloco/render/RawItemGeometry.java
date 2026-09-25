package cn.zbx1425.mtrsteamloco.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Immutable CPU geometry. No live model, world or GPU upload is retained by an item state. */
public final class RawItemGeometry implements SpecialModelRenderer<Void> {
    private final List<Batch> batches;

    private RawItemGeometry(List<Batch> batches) {
        this.batches = List.copyOf(batches);
    }

    @Override
    public void submit(Void argument, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
        for (Batch batch : batches) {
            final List<Triangle> triangles;
            if (batch.key.sorted) {
                triangles = new ArrayList<>(batch.triangles);
                triangles.sort(Comparator.comparingDouble((Triangle triangle) -> triangle.distanceSquared(pose.last())).reversed());
            } else {
                triangles = batch.triangles;
            }
            // Raw ANTE items did not use vanilla foil/outline passes. Keep their material pipeline.
            collector.submitCustomGeometry(pose, batch.key.type, (capturedPose, vertices) -> {
                for (Triangle triangle : triangles) {
                    final int faceLight = triangle.light == null ? light : triangle.light;
                    final int faceOverlay = triangle.overlay == null ? overlay : triangle.overlay;
                    triangle.a.write(vertices, capturedPose, triangle.color, faceLight, faceOverlay);
                    triangle.b.write(vertices, capturedPose, triangle.color, faceLight, faceOverlay);
                    triangle.c.write(vertices, capturedPose, triangle.color, faceLight, faceOverlay);
                }
            });
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (Batch batch : batches) for (Triangle triangle : batch.triangles) {
            output.accept(triangle.a.position());
            output.accept(triangle.b.position());
            output.accept(triangle.c.position());
        }
    }

    @Override public Void extractArgument(ItemStack stack) { return null; }

    public Vector3fc[] extents() {
        final List<Vector3fc> result = new ArrayList<>();
        getExtents(result::add);
        return result.toArray(Vector3fc[]::new);
    }

    public record Vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        private Vector3f position() { return new Vector3f(x, y, z); }

        private void write(VertexConsumer output, PoseStack.Pose pose, int color, int light, int overlay) {
            output.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setLight(light).setOverlay(overlay).setNormal(pose, nx, ny, nz);
        }
    }

    private record Triangle(Vertex a, Vertex b, Vertex c, int color, Integer light, Integer overlay) {
        private float distanceSquared(PoseStack.Pose pose) {
            return pose.pose().transformPosition(new Vector3f((a.x + b.x + c.x) / 3, (a.y + b.y + c.y) / 3, (a.z + b.z + c.z) / 3)).lengthSquared();
        }
    }

    private record BatchKey(RenderType type, boolean sorted) { }
    private record Batch(BatchKey key, List<Triangle> triangles) { }

    public static final class Builder {
        private final Map<BatchKey, List<Triangle>> batches = new LinkedHashMap<>();

        public void triangle(RenderType type, boolean sorted, Vertex a, Vertex b, Vertex c, int color, Integer light, Integer overlay) {
            batches.computeIfAbsent(new BatchKey(type, sorted), key -> new ArrayList<>()).add(new Triangle(a, b, c, color, light, overlay));
        }

        public RawItemGeometry build() {
            return new RawItemGeometry(batches.entrySet().stream().map(entry -> new Batch(entry.getKey(), List.copyOf(entry.getValue()))).toList());
        }
    }
}
