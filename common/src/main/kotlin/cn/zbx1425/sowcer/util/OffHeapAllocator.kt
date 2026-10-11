package cn.zbx1425.sowcer.util

import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

open class OffHeapAllocator {
    companion object {
        private val ALLOCATOR: MemoryUtil.MemoryAllocator = MemoryUtil.getAllocator(false)

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun allocate(size: Int): ByteBuffer {
            val ptr = ALLOCATOR.malloc(size.toLong())
            if (ptr == 0L) throw OutOfMemoryError()
            return MemoryUtil.memByteBuffer(ptr, size)
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun resize(buf: ByteBuffer?, byteSize: Int): ByteBuffer {
            val ptr = ALLOCATOR.realloc(MemoryUtil.memAddress0(buf!!), byteSize.toLong())
            if (ptr == 0L) throw OutOfMemoryError()
            return MemoryUtil.memByteBuffer(ptr, byteSize)
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun free(buf: ByteBuffer?) {
            val ptr = MemoryUtil.memAddress0(buf!!)
            ALLOCATOR.free(ptr)
        }
    }
}
