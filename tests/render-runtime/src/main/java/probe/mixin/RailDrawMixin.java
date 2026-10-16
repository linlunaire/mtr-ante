package probe.mixin;

import cn.zbx1425.sowcerext.reuse.DrawScheduler;
import cn.zbx1425.sowcer.util.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import probe.RenderRuntimeRegression;

@Mixin(value = DrawScheduler.class, remap = false)
public class RailDrawMixin {
    @Inject(method = "commitRaw", at = @At("HEAD"))
    private void begin(DrawContext context, CallbackInfo ci) { RenderRuntimeRegression.inRaw = true; }

    @Inject(method = "commitRaw", at = @At("RETURN"))
    private void end(DrawContext context, CallbackInfo ci) { RenderRuntimeRegression.inRaw = false; }
}
