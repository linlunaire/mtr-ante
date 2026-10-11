package cn.zbx1425.mtrsteamloco.mixin;

import cn.zbx1425.sowcer.ContextCapability;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GLFW.class)
public class GLFWMixin {

    // Preserve Minecraft's backend-specific window hints; the compatibility helper calls the native entry.

    @Inject(method = "glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", at = @At("HEAD"), cancellable = true, remap = false)
    private static void glfwCreateWindow(int width, int height, CharSequence title, long monitor, long share, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(ContextCapability.createWindow(width, height, title, monitor, share));
    }

}
