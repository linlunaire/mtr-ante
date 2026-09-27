package cn.zbx1425.sowcer.`object`

import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/** Retained heap upload with shared ownership; snapshots never expose mutable backing storage. */
open class VertBuf : Closeable {
    @Volatile @JvmField var id: Int = NEXT_ID.getAndIncrement()
    private var data: ByteBuffer? = null
    private var references: Int = 1
    private var ownerClosed: Boolean = false
    private var revision: Long = 0

    open fun upload(buffer: ByteBuffer?, usage: Int) { upload(buffer, buffer!!.capacity(), usage) }

    @Synchronized open fun upload(buffer: ByteBuffer?, size: Int, usage: Int) {
        if (ownerClosed) throw IllegalStateException("Upload to a closed buffer")
        if (size < 0 || size > buffer!!.capacity()) throw IllegalArgumentException("Invalid buffer byte count: " + size)
        if (usage != USAGE_STATIC_DRAW && usage != USAGE_DYNAMIC_DRAW && usage != USAGE_STREAM_DRAW) throw IllegalArgumentException("Invalid buffer usage")
        val input = buffer.duplicate().order(buffer.order())
        input.clear().limit(size)
        val copy = ByteBuffer.allocate(size).order(buffer.order())
        copy.put(input).flip()
        data = copy.asReadOnlyBuffer().order(copy.order())
        revision++
    }

    // Kotlin has no package-private visibility. Protected preserves same-package Java access
    // and original virtual JVM names; synthetic internal bridges serve the Kotlin package.
    @Synchronized protected open fun snapshotUpload(): Upload = Upload(snapshot(), revision)

    /** Implementation snapshot. Public only because Kotlin cannot express Java package visibility. */
    @JvmRecord
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    data class Upload(open val data: ByteBuffer?, open val revision: Long) {
        final override fun equals(other: Any?): Boolean = this === other ||
            other is Upload && java.util.Objects.equals(data, other.data) && revision == other.revision
        final override fun hashCode(): Int = 31 * java.util.Objects.hashCode(data) + java.lang.Long.hashCode(revision)
        final override fun toString(): String = "Upload[data=" + data + ", revision=" + revision + "]"
    }

    @Synchronized open fun snapshot(): ByteBuffer {
        if (references == 0 || data == null) throw IllegalStateException("Buffer is closed or not uploaded")
        return data!!.asReadOnlyBuffer().order(data!!.order())
    }

    @Synchronized protected open fun retain() {
        if (references == 0) throw IllegalStateException("Retain of a released buffer")
        references++
    }

    @Synchronized protected open fun release() {
        if (references == 0) throw IllegalStateException("Buffer released twice")
        if (--references == 0) { data = null; id = 0 }
    }

    @Synchronized override fun close() {
        if (!ownerClosed) { ownerClosed = true; release() }
    }

    @JvmSynthetic internal fun retainShared() { retain() }
    @JvmSynthetic internal fun releaseShared() { release() }
    @JvmSynthetic internal fun snapshotShared(): Upload = snapshotUpload()

    companion object {
        private val NEXT_ID = AtomicInteger(1)
        const val USAGE_STATIC_DRAW: Int = 0x88E4
        const val USAGE_DYNAMIC_DRAW: Int = 0x88E8
        const val USAGE_STREAM_DRAW: Int = 0x88E0
    }
}
