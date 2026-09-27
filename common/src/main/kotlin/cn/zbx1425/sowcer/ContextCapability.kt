package cn.zbx1425.sowcer

import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack
import java.util.Locale

open class ContextCapability {
    companion object {
        @JvmField var supportVertexAttribDivisor: Boolean = true
        @JvmField var contextVersion: Int = 0
        @JvmField var isGL4ES: Boolean = false
        @JvmField var backendDescription: String? = "Blaze3D (not initialized)"

        // Non-final static bridges retain Java subclass static-method hiding.
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun createWindow(width: Int, height: Int, title: CharSequence?, monitor: Long, share: Long): Long {
            // Preserve Minecraft's backend-specific GLFW hints and avoid the public GLFW mixin target.
            MemoryStack.stackPush().use { stack ->
                stack.nUTF8(title!!, true)
                return GLFW.nglfwCreateWindow(width, height, stack.pointerAddress, monitor, share)
            }
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun checkContextVersion() {
            val device = RenderSystem.tryGetDevice()
            if (device == null) {
                backendDescription = "Blaze3D (not initialized)"
                isGL4ES = false
            } else {
                val info = device.deviceInfo
                backendDescription = info.backendName() + " / " + info.name() + " / " + info.driverInfo()
                isGL4ES = backendDescription!!.lowercase(Locale.ROOT).contains("gl4es")
            }
            contextVersion = 0
            supportVertexAttribDivisor = true
        }
    }
}
