package cn.zbx1425.mtrsteamloco.render;

import cn.zbx1425.mtrsteamloco.block.BlockEyeCandy;
import cn.zbx1425.mtrsteamloco.data.EyeCandyRegistry;
import cn.zbx1425.sowcer.util.AttrUtil;
import cn.zbx1425.sowcerext.model.ModelCluster;
import cn.zbx1425.sowcerext.model.RawModel;
import cn.zbx1425.sowcerext.model.Vertex;
import mtr.mappings.ItemStackUtilities;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public final class EyeCandyItemRenderer {
    private EyeCandyItemRenderer() { }

    public static void update(ModelManager models, ItemModel original, ItemStackRenderState state, ItemStack stack,
                              ItemModelResolver resolver, ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed) {
        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BlockEyeCandy) {
            final CompoundTag data = ItemStackUtilities.getCustomData(stack).getCompoundOrEmpty("BlockEntityTag");
            final var properties = EyeCandyRegistry.ELEMENTS.get(data.getStringOr("prefabId", ""));
            if (properties != null) {
                if (properties.itemModelId != null) {
                    // Query the current model manager, not a baked object cached across resource reloads.
                    models.getItemModel(properties.itemModelId).update(state, stack, resolver, context, level, owner, seed);
                    return;
                }
                if (properties.itemModel != null) {
                    RawItemRenderState.append(original, state, stack, resolver, context, level, owner, seed,
                            capture(properties.itemModel), properties.itemTransform == null ? new Matrix4f() : properties.itemTransform.asMoj());
                    return;
                }
            }
        }
        original.update(state, stack, resolver, context, level, owner, seed);
    }

    private static RawItemGeometry capture(ModelCluster cluster) {
        final RawItemGeometry.Builder builder = new RawItemGeometry.Builder();
        for (RawModel model : new RawModel[]{cluster.opaqueParts, cluster.translucentParts}) {
            model.meshList.forEach((material, mesh) -> {
                final var type = material.getBlazeRenderType();
                final int color = material.attrState.color == null ? 0xFFFFFFFF : material.attrState.color;
                final Integer overlay = material.attrState.overlayUV == null ? null : AttrUtil.exchangeLightmapUVBits(material.attrState.overlayUV);
                for (var face : mesh.faces) {
                    if (face.vertices.length != 3) throw new IllegalArgumentException("ANTE raw item faces must be triangles");
                    builder.triangle(type, material.translucent,
                            capture(mesh.vertices.get(face.vertices[0])), capture(mesh.vertices.get(face.vertices[1])), capture(mesh.vertices.get(face.vertices[2])),
                            color, material.attrState.lightmapUV, overlay);
                }
            });
        }
        return builder.build();
    }

    private static RawItemGeometry.Vertex capture(Vertex vertex) {
        return new RawItemGeometry.Vertex(vertex.position.x(), vertex.position.y(), vertex.position.z(),
                vertex.normal.x(), vertex.normal.y(), vertex.normal.z(), vertex.u, vertex.v);
    }
}
