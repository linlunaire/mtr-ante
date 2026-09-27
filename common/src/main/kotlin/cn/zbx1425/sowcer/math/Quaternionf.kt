package cn.zbx1425.sowcer.math

import org.joml.AxisAngle4f

open class Quaternionf {
    private val impl: org.joml.Quaternionf

    constructor() : this(Vector3f.ZERO, 0F)
    constructor(axis: Vector3f, angle: Float) {
        impl = org.joml.Quaternionf(AxisAngle4f(angle, axis.asMoj()))
    }
    constructor(other: Quaternionf) { impl = org.joml.Quaternionf(other.impl) }

    open fun mul(other: Quaternionf): Quaternionf { impl.mul(other.impl); return this }
    open fun i(): Float = impl.x
    open fun j(): Float = impl.y
    open fun k(): Float = impl.z
    open fun r(): Float = impl.w
    open fun set(i: Float, j: Float, k: Float, r: Float) {
        impl.x = i
        impl.y = j
        impl.z = k
        impl.w = r
    }
    open fun i(i: Float) { impl.x = i }
    open fun j(j: Float) { impl.y = j }
    open fun k(k: Float) { impl.z = k }
    open fun r(r: Float) { impl.w = r }
    open fun asMoj(): org.joml.Quaternionf = impl
    open fun rotateX(angle: Double): Quaternionf = rotateX(angle.toFloat())
    open fun rotateY(angle: Double): Quaternionf = rotateY(angle.toFloat())
    open fun rotateZ(angle: Double): Quaternionf = rotateZ(angle.toFloat())
    open fun rotateX(angle: Float): Quaternionf { rotate(Vector3f.XP, angle); return this }
    open fun rotateY(angle: Float): Quaternionf { rotate(Vector3f.YP, angle); return this }
    open fun rotateZ(angle: Float): Quaternionf { rotate(Vector3f.ZP, angle); return this }
    open fun rotate(axis: Vector3f, angle: Float): Quaternionf { mul(Quaternionf(axis, angle)); return this }
}
