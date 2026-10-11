package cn.zbx1425.mtrsteamloco.mixin;

import cn.zbx1425.mtrsteamloco.render.PreviewProjection;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Projection ownership moved from GameRenderer to Camera in 26.2.
@Mixin(Camera.class)
public class GameRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void previewProjection(CameraRenderState state, float tickDelta, CallbackInfo ci) {
        if (PreviewProjection.isActive()) state.projectionMatrix.set(PreviewProjection.apply(state.projectionMatrix));
    }

    @Inject(method = "createProjectionMatrixForCulling", at = @At("RETURN"), cancellable = true)
    private void previewCulling(CallbackInfoReturnable<Matrix4f> cir) {
        if (PreviewProjection.isActive()) cir.setReturnValue(PreviewProjection.apply(cir.getReturnValue()));
    }
}
