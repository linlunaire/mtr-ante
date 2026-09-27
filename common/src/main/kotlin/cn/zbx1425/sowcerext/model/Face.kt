package cn.zbx1425.sowcerext.model

import org.apache.commons.lang3.ArrayUtils
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Arrays
import java.util.stream.IntStream

open class Face {
    @JvmField var vertices: IntArray?

    constructor(vertices: IntArray?) { this.vertices = vertices }

    @Throws(IOException::class)
    constructor(dis: DataInputStream?) {
        vertices = intArrayOf(dis!!.readInt(), dis.readInt(), dis.readInt())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        return Arrays.equals(vertices, (other as Face).vertices)
    }

    override fun hashCode(): Int = Arrays.hashCode(vertices)
    open fun copy(): Face = Face(Arrays.copyOf(vertices!!, vertices!!.size))
    open fun flip() { ArrayUtils.reverse(vertices) }

    @Throws(IOException::class)
    open fun serializeTo(dos: DataOutputStream?) {
        // Java's assertion switch is per class, unlike Kotlin's shared assertion setting.
        if (ASSERTIONS_ENABLED && vertices!!.size != 3) throw AssertionError()
        writeInt(dos, vertices!![0])
        writeInt(dos, vertices!![1])
        writeInt(dos, vertices!![2])
    }

    // Retain array-access failure before a null stream receiver, as in Java.
    private fun writeInt(output: DataOutputStream?, value: Int) { output!!.writeInt(value) }

    companion object {
        private val ASSERTIONS_ENABLED = Face::class.java.desiredAssertionStatus()

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun triangulate(vertices: IntArray?, isFace2: Boolean): MutableList<Face> {
            val result = ArrayList<Face>()
            appendTriangles(vertices!!, result)
            if (isFace2) {
                ArrayUtils.reverse(vertices)
                appendTriangles(vertices, result)
            }
            return result
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun triangulate(begin: Int, end: Int, isFace2: Boolean): MutableList<Face> =
            triangulate(IntStream.range(begin, end + 1).toArray(), isFace2)

        private fun appendTriangles(vertices: IntArray, result: MutableList<Face>) {
            if (vertices.size > 3) {
                for (i in 2 until vertices.size) result.add(Face(intArrayOf(vertices[0], vertices[i - 1], vertices[i])))
            } else result.add(Face(intArrayOf(vertices[0], vertices[1], vertices[2])))
        }
    }
}
