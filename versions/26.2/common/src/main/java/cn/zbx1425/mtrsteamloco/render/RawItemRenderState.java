package cn.zbx1425.mtrsteamloco.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class RawItemRenderState {
    private RawItemRenderState() { }

    public static void append(ItemModel base, ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                              ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed,
                              RawItemGeometry geometry, Matrix4fc itemTransform) {
        final int firstLayer = state.activeLayerCount;
        // Let the resource-pack model supply display transforms, lighting and particle material.
        base.update(state, stack, resolver, context, level, owner, seed);
        final var layer = state.activeLayerCount > firstLayer ? state.layers[firstLayer] : state.newLayer();
        layer.prepareQuadList().clear();
        // Replace this item's geometry only, retaining layers appended by an outer composite model.
        for (int i = firstLayer + 1; i < state.activeLayerCount; i++) state.layers[i].clear();
        state.activeLayerCount = firstLayer + 1;
        // 26.2 ItemTransform already centers by (-.5, -.5, -.5). Legacy raw items
        // centered only on Y, so compensate X/Z before their own fitting transform.
        layer.setLocalTransform(new Matrix4f().translation(0.5F, 0, 0.5F).mul(itemTransform));
        layer.setupSpecialModel(geometry, null);
        layer.setExtents(geometry::extents);
        state.appendModelIdentityElement(geometry);
        // Script/resource changes can alter a raw mesh between frames; do not reuse a GUI cache entry.
        state.setAnimated();
    }
}
