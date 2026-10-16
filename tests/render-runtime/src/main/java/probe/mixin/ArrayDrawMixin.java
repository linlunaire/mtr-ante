package probe.mixin;

import cn.zbx1425.sowcer.object.VertArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import probe.RenderRuntimeRegression;

@Mixin(value = VertArray.class, remap = false)
public class ArrayDrawMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private void begin(CallbackInfo ci) { RenderRuntimeRegression.beforeDraw((VertArray)(Object)this); }

    @Inject(method = "draw", at = @At("RETURN"))
    private void end(CallbackInfo ci) { RenderRuntimeRegression.afterDraw(); }
}
