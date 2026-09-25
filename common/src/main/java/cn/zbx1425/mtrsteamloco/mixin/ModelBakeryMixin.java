package cn.zbx1425.mtrsteamloco.mixin;

import cn.zbx1425.mtrsteamloco.data.EyeCandyItemResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Keep the existing client mixin entry. ModelBakery is too late in 26.2: item
// definitions, model JSON and atlas sources have already been read asynchronously.
@Mixin(MultiPackResourceManager.class)
public abstract class ModelBakeryMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void ante$prepareItemResources(PackType type, List<PackResources> packs, CallbackInfo ci) {
        EyeCandyItemResources.prepare(type, (MultiPackResourceManager) (Object) this);
    }
}
