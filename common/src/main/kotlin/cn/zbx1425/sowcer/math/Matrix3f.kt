package cn.zbx1425.sowcer.math

import java.nio.FloatBuffer

open class Matrix3f(private val impl: org.joml.Matrix3f?) : Posture {
    constructor() : this(org.joml.Matrix3f())
    constructor(other: Matrix3f) : this(org.joml.Matrix3f(other.asMoj()))
    constructor(q: Quaternionf) : this() { impl!!.set(q.asMoj()) }
    constructor(m: Matrix4f) : this(org.joml.Matrix3f(m.asMoj()))

    open fun store(buffer: FloatBuffer) {
        buffer.put(0, impl!!.m00()).put(1, impl.m01()).put(2, impl.m02())
            .put(3, impl.m10()).put(4, impl.m11()).put(5, impl.m12())
            .put(6, impl.m20()).put(7, impl.m21()).put(8, impl.m22())
    }

    open fun load(buffer: FloatBuffer) {
        val bufferValues = FloatArray(9)
        buffer.get(bufferValues)
        impl!!.set(bufferValues)
    }

    open fun setIdentity() { impl!!.identity() }
    open fun multiply(other: Matrix3f) { impl!!.mul(other.impl) }
    open fun multiply(q: Quaternionf) { impl!!.rotate(q.asMoj()) }
    open fun add(other: Matrix3f) { impl!!.add(other.impl) }
    open fun sub(other: Matrix3f) { impl!!.sub(other.impl) }
    open fun scale(sx: Float, sy: Float, sz: Float) { impl!!.scale(sx, sy, sz, impl) }
    open fun asMoj(): org.joml.Matrix3f? = impl

    open fun transform(src: Vector3f): Vector3f {
        // Keep direct package-level field access; asMoj is overridable by existing Java/script callers.
        val srcCpy = org.joml.Vector3f(src.backingVector())
        return Vector3f(impl!!.transform(srcCpy))
    }

    open fun scale(s: Float) { scale(s, s, s) }
    open fun rotateX(angle: Float) { multiply(Quaternionf(Vector3f.XP, angle)) }
    open fun rotateY(angle: Float) { multiply(Quaternionf(Vector3f.YP, angle)) }
    open fun rotateZ(angle: Float) { multiply(Quaternionf(Vector3f.ZP, angle)) }
    override fun getAsMatrix3f(): Matrix3f = this
    override fun getAsMatrix4f(): Matrix4f = Matrix4f(this)
    override fun getAsMatrices(): Matrices = Matrices(getAsMatrix4f())
    override fun getAsPose(): Pose = Pose(getAsMatrix4f())
    override fun getAsPoseStack(): PoseStack = PoseStack(getAsPose())
}
