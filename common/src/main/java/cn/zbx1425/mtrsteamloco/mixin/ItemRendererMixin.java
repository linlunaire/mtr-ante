package cn.zbx1425.mtrsteamloco.mixin;

import cn.zbx1425.mtrsteamloco.render.EyeCandyItemRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Intercept extraction, not the removed immediate ItemRenderer.render path.
@Mixin(ItemModelResolver.class)
public abstract class ItemRendererMixin {
    @Shadow @Final private ModelManager modelManager;

    @Redirect(method = "appendItemLayers", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/item/ItemModel;update(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/item/ItemModelResolver;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/ItemOwner;I)V"))
    private void ante$updateEyeCandy(ItemModel model, ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                                    ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed) {
        EyeCandyItemRenderer.update(modelManager, model, state, stack, resolver, context, level, owner, seed);
    }
}
