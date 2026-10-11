package cn.zbx1425.mtrsteamloco.render.rail;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;

/** Twelve independent edges, captured through the same extraction buffer as the rails. */
public final class RailDebugGeometry {
    private RailDebugGeometry() {}

    public static void renderLineBox(PoseStack matrices, VertexConsumer vertices, AABB box, float r, float g, float b, float a) {
        int color = (int) (a * 255) << 24 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
        for (int axis = 0; axis < 3; axis++) {
            for (int corner = 0; corner < 8; corner++) {
                if ((corner & 1 << axis) != 0) continue;
                for (int endpoint = 0; endpoint < 2; endpoint++) {
                    int point = corner | endpoint << axis;
                    vertices.addVertex(matrices.last(),
                            (float) ((point & 1) == 0 ? box.minX : box.maxX),
                            (float) ((point & 2) == 0 ? box.minY : box.maxY),
                            (float) ((point & 4) == 0 ? box.minZ : box.maxZ))
                            .setColor(color)
                            .setNormal(matrices.last(), axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0)
                            .setLineWidth(1);
                }
            }
        }
    }
}
