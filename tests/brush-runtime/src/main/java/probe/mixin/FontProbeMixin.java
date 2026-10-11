package probe.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import probe.BrushRuntimeProbe;

@Mixin(Font.class)
public class FontProbeMixin {
    @Inject(method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I", at = @At("HEAD"))
    private void record(String text, float x, float y, int color, boolean shadow, Matrix4f pose,
                        MultiBufferSource source, Font.DisplayMode mode, int background, int light,
                        boolean bidi, CallbackInfoReturnable<Integer> info) {
        if (text.matches("[LR][0-9].*")) BrushRuntimeProbe.record(text, x, y, pose);
    }
}
