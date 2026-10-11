package cn.zbx1425.sowcerext.model

import cn.zbx1425.sowcer.math.Vector3f
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

open class Vertex {
    @JvmField var position: Vector3f? = null
    @JvmField var normal: Vector3f? = null
    @JvmField var u: Float = 0F
    @JvmField var v: Float = 0F
    @JvmField var color: Int = 0
    @JvmField var light: Int = 0

    constructor()
    constructor(position: Vector3f?) {
        this.position = position
        normal = Vector3f(0F, 0F, 0F)
    }
    constructor(position: Vector3f?, normal: Vector3f?) {
        this.position = position
        this.normal = normal
    }

    @Throws(IOException::class)
    constructor(dis: DataInputStream?) {
        position = Vector3f(dis!!.readFloat(), dis.readFloat(), dis.readFloat())
        normal = Vector3f(dis.readFloat(), dis.readFloat(), dis.readFloat())
        u = dis.readFloat()
        v = dis.readFloat()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val vertex = other as Vertex
        return color == vertex.color && light == vertex.light &&
            java.lang.Float.compare(vertex.u, u) == 0 && java.lang.Float.compare(vertex.v, v) == 0 &&
            position!!.equals(vertex.position) && normal!!.equals(vertex.normal)
    }

    override fun hashCode(): Int {
        // Objects.hash captured every field before invoking overridable vector hashes.
        // Retain that ordering without its temporary Object[] and four boxed primitives.
        val capturedPosition = position
        val capturedNormal = normal
        val capturedU = u
        val capturedV = v
        val capturedColor = color
        val capturedLight = light
        var result = 1
        result = 31 * result + (capturedPosition?.hashCode() ?: 0)
        result = 31 * result + (capturedNormal?.hashCode() ?: 0)
        result = 31 * result + java.lang.Float.hashCode(capturedU)
        result = 31 * result + java.lang.Float.hashCode(capturedV)
        result = 31 * result + capturedColor
        return 31 * result + capturedLight
    }

    open fun copy(): Vertex {
        val clone = Vertex(position!!.copy(), normal!!.copy())
        clone.u = u
        clone.v = v
        clone.color = color
        clone.light = light
        return clone
    }

    @Throws(IOException::class)
    open fun serializeTo(dos: DataOutputStream?) {
        writeFloat(dos, position!!.x())
        writeFloat(dos, position!!.y())
        writeFloat(dos, position!!.z())
        writeFloat(dos, normal!!.x())
        writeFloat(dos, normal!!.y())
        writeFloat(dos, normal!!.z())
        writeFloat(dos, u)
        writeFloat(dos, v)
    }

    // Java evaluates the value (including virtual getters) before failing a null receiver.
    private fun writeFloat(output: DataOutputStream?, value: Float) { output!!.writeFloat(value) }
}
