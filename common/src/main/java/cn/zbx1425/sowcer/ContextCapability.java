package cn.zbx1425.sowcer;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import java.util.Locale;

/** Backend-neutral capability reporting. Minecraft owns window hints and GPU initialization. */
public class ContextCapability {
    /** Legacy name: the 26.2 instance-buffer layout is expanded during CPU extraction. */
    public static boolean supportVertexAttribDivisor = true;
    public static int contextVersion;
    public static boolean isGL4ES;
    public static String backendDescription = "Blaze3D (not initialized)";

    public static long createWindow(int width, int height, CharSequence title, long monitor, long share) {
        // This is invoked by the GLFW mixin: use the native entry to avoid recursion.
        // In particular, retain GLFW_NO_API when Minecraft has selected Vulkan.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            stack.nUTF8(title, true);
            return GLFW.nglfwCreateWindow(width, height, stack.getPointerAddress(), monitor, share);
        }
    }

    public static void checkContextVersion() {
        final var device = RenderSystem.tryGetDevice();
        if (device == null) {
            backendDescription = "Blaze3D (not initialized)";
            isGL4ES = false;
        } else {
            final var info = device.getDeviceInfo();
            backendDescription = info.backendName() + " / " + info.name() + " / " + info.driverInfo();
            isGL4ES = backendDescription.toLowerCase(Locale.ROOT).contains("gl4es");
        }
        // No raw GL version probing: it is invalid for non-OpenGL backends.
        contextVersion = 0;
        supportVertexAttribDivisor = true;
    }
}
