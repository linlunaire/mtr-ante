package cn.zbx1425.mtrsteamloco.mixin;

import cn.zbx1425.mtrsteamloco.render.PreviewProjection;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Hud.class)
public class HudPreviewMixin {
    @Inject(method = "isHidden", at = @At("RETURN"), cancellable = true)
    private void hideDuringPreview(CallbackInfoReturnable<Boolean> cir) {
        // Do not mutate the user's F1 state; closing the preview restores it automatically.
        if (PreviewProjection.isActive()) cir.setReturnValue(true);
    }
}
