package cn.zbx1425.mtrsteamloco.mixin;

import cn.zbx1425.mtrsteamloco.MainClient;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelExtractor.class, priority = 100)
public class LevelRendererMixin {
    // Dirty-section notifications moved to extraction. The frame handoff is in RenderTrainsMixin.
    @Inject(method = "setSectionDirtyWithNeighbors", at = @At("HEAD"))
    private void setSectionDirtyWithNeighbors(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
        for (int z = sectionZ - 1; z <= sectionZ + 1; z++) {
            for (int x = sectionX - 1; x <= sectionX + 1; x++) {
                MainClient.railRenderDispatcher.registerLightUpdate(x, sectionY - 1, sectionY + 1, z);
            }
        }
    }

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void setSectionDirty(int x, int y, int z, boolean reRenderOnMainThread, CallbackInfo ci) {
        MainClient.railRenderDispatcher.registerLightUpdate(x, y, y, z);
    }

    @Inject(method = "setSectionDirty(III)V", at = @At("HEAD"))
    private void setSectionDirty(int x, int y, int z, CallbackInfo ci) {
        MainClient.railRenderDispatcher.registerLightUpdate(x, y, y, z);
    }
}
