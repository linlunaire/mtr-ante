package cn.zbx1425.sowcer.math

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

open class Vector3f(@JvmField protected val impl: org.joml.Vector3f?) {
    constructor(x: Float, y: Float, z: Float) : this(org.joml.Vector3f(x, y, z))
    constructor(x: Double, y: Double, z: Double) : this(x.toFloat(), y.toFloat(), z.toFloat())
    private constructor(other: Vector3f) : this(org.joml.Vector3f(other.impl))
    constructor(blockPos: BlockPos) : this(blockPos.x.toFloat(), blockPos.y.toFloat(), blockPos.z.toFloat())
    constructor(vec3: Vec3) : this(vec3.x.toFloat(), vec3.y.toFloat(), vec3.z.toFloat())

    open fun x(): Float = impl!!.x()
    open fun y(): Float = impl!!.y()
    open fun z(): Float = impl!!.z()
    open fun copy(): Vector3f = Vector3f(this)
    open fun normalize() { impl!!.normalize() }
    open fun add(x: Float, y: Float, z: Float) { impl!!.add(x, y, z) }
    open fun add(x: Double, y: Double, z: Double) { add(x.toFloat(), y.toFloat(), z.toFloat()) }
    open fun add(other: Vector3f) { impl!!.add(other.impl) }
    open fun sub(other: Vector3f) { impl!!.sub(other.impl) }
    open fun mul(x: Float, y: Float, z: Float) { impl!!.mul(x, y, z) }
    open fun mul(n: Float) { impl!!.mul(n) }
    open fun rot(axis: Vector3f, rad: Float) { impl!!.rotateAxis(rad, axis.x(), axis.y(), axis.z()) }
    open fun rotDeg(axis: Vector3f, deg: Float) {
        impl!!.rotateAxis(Math.toRadians(deg.toDouble()).toFloat(), axis.x(), axis.y(), axis.z())
    }
    open fun rotX(rad: Float) { impl!!.rotateX(rad) }
    open fun rotY(rad: Float) { impl!!.rotateY(rad) }
    open fun rotZ(rad: Float) { impl!!.rotateZ(rad) }
    open fun cross(other: Vector3f) { impl!!.cross(other.impl) }
    open fun asMoj(): org.joml.Vector3f? = impl
    // Preserve the Java protected field and direct, non-virtual package access used by matrix operations.
    @JvmSynthetic internal fun backingVector(): org.joml.Vector3f? = impl
    override fun hashCode(): Int = impl!!.hashCode()
    override fun toString(): String = "(" + x() + ", " + y() + ", " + z() + ")"

    open fun distance(other: Vector3f): Float {
        val dx = x() - other.x()
        val dy = y() - other.y()
        val dz = z() - other.z()
        return Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }

    open fun distanceSq(other: Vector3f): Float {
        val dx = x() - other.x()
        val dy = y() - other.y()
        val dz = z() - other.z()
        return dx * dx + dy * dy + dz * dz
    }

    open fun toBlockPos(): BlockPos = BlockPos(Mth.floor(x()), Mth.floor(y()), Mth.floor(z()))
    open fun toVec3(): Vec3 = Vec3(x().toDouble(), y().toDouble(), z().toDouble())
    open fun lengthSquared(): Float = x() * x() + y() * y() + z() * z()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        return impl!!.equals((other as Vector3f).impl)
    }

    companion object {
        @JvmField val ZERO = Vector3f(0F, 0F, 0F)
        @JvmField val XP = Vector3f(1F, 0F, 0F)
        @JvmField val YP = Vector3f(0F, 1F, 0F)
        @JvmField val ZP = Vector3f(0F, 0F, 1F)
    }
}
