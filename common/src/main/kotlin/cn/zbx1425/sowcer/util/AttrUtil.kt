package cn.zbx1425.sowcer.util

import cn.zbx1425.sowcer.math.Matrix4f
import java.nio.ByteBuffer

open class AttrUtil {
    companion object {
        @JvmField val MAT_NO_TRANSFORM = Matrix4f()

        // Non-final static bridges preserve the original Java utility interface.
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun exchangeLightmapUVBits(light: Int): Int = (light ushr 16) or (light.toShort().toInt() shl 16)

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun zeroRotation(src: Matrix4f?) {
            val byteBuffer = ByteBuffer.allocate(16 * 4)
            src!!.store(byteBuffer.asFloatBuffer())
            byteBuffer.clear()
            byteBuffer.asFloatBuffer().put(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f))
            byteBuffer.clear()
            src.load(byteBuffer.asFloatBuffer())
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun zeroTranslation(src: Matrix4f?) {
            val byteBuffer = ByteBuffer.allocate(16 * 4)
            src!!.store(byteBuffer.asFloatBuffer())
            byteBuffer.position(12 * 4)
            byteBuffer.asFloatBuffer().put(floatArrayOf(0f, 0f, 0f, 1f))
            byteBuffer.clear()
            src.load(byteBuffer.asFloatBuffer())
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun argbToBgr(color: Int): Int {
            val a = 0xFF
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return (a shl 24) or (b shl 16) or (g shl 8) or r
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun rgbaToArgb(color: Int): Int {
            val r = (color shr 24) and 0xFF
            val g = (color shr 16) and 0xFF
            val b = (color shr 8) and 0xFF
            val a = color and 0xFF
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
