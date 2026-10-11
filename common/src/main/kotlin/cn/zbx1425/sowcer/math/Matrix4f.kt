package cn.zbx1425.sowcer.math

import java.nio.FloatBuffer

open class Matrix4f(@JvmField protected val impl: org.joml.Matrix4f?) : Posture {
    constructor() : this(org.joml.Matrix4f()) { impl!!.identity() }
    constructor(mat3: Matrix3f) : this(org.joml.Matrix4f(mat3.asMoj()))
    constructor(other: Matrix4f) : this(org.joml.Matrix4f(other.impl))

    open fun copy(): Matrix4f = Matrix4f(this)
    open fun asMoj(): org.joml.Matrix4f? = impl
    open fun multiply(other: Matrix4f) { impl!!.mul(other.impl) }
    open fun multiply(q: Quaternionf) { impl!!.rotate(q.asMoj()) }
    open fun store(buffer: FloatBuffer) {
        buffer.put(0, impl!!.m00()).put(1, impl.m01()).put(2, impl.m02()).put(3, impl.m03())
            .put(4, impl.m10()).put(5, impl.m11()).put(6, impl.m12()).put(7, impl.m13())
            .put(8, impl.m20()).put(9, impl.m21()).put(10, impl.m22()).put(11, impl.m23())
            .put(12, impl.m30()).put(13, impl.m31()).put(14, impl.m32()).put(15, impl.m33())
    }
    open fun load(buffer: FloatBuffer) {
        val bufferValues = FloatArray(16)
        buffer.get(bufferValues)
        impl!!.set(bufferValues)
    }
    open fun rotateX(rad: Float) { impl!!.rotateX(rad) }
    open fun rotateY(rad: Float) { impl!!.rotateY(rad) }
    open fun rotateZ(rad: Float) { impl!!.rotateZ(rad) }
    open fun rotate(axis: Vector3f, rad: Float) { impl!!.rotate(rad, axis.backingVector()) }
    open fun translate(x: Float, y: Float, z: Float) { impl!!.translate(x, y, z) }
    open fun transform(src: Vector3f): Vector3f {
        val srcCpy = org.joml.Vector3f(src.backingVector())
        return Vector3f(impl!!.transformPosition(srcCpy))
    }
    open fun transform3(src: Vector3f): Vector3f {
        val srcCpy = org.joml.Vector3f(src.backingVector())
        return Vector3f(impl!!.transformDirection(srcCpy))
    }
    open fun getRotationPart(): org.joml.Matrix3f {
        val result = org.joml.Matrix3f()
        return impl!!.get3x3(result)
    }
    open fun getTranslationPart(): Vector3f {
        val result = org.joml.Vector3f()
        return Vector3f(impl!!.getTranslation(result))
    }
    open fun scale(x: Float, y: Float, z: Float) { impl!!.scale(x, y, z) }
    open fun mul(other: Matrix4f) { multiply(other) }
    open fun scale(factor: Float) { scale(factor, factor, factor) }
    open fun translate(vec: Vector3f) { translate(vec.x(), vec.y(), vec.z()) }
    open fun rotateXYZ(x: Float, y: Float, z: Float) { rotateX(x); rotateY(y); rotateZ(z) }
    open fun rotateXYZ(vec: Vector3f) { rotateXYZ(vec.x(), vec.y(), vec.z()) }
    open fun rotateYXZ(y: Float, x: Float, z: Float) { rotateY(y); rotateX(x); rotateZ(z) }
    open fun rotateYXZ(vec: Vector3f) { rotateYXZ(vec.y(), vec.x(), vec.z()) }
    open fun rotateZYX(z: Float, y: Float, x: Float) { rotateZ(z); rotateY(y); rotateX(x) }
    open fun rotateZYX(vec: Vector3f) { rotateZYX(vec.z(), vec.y(), vec.x()) }

    open fun getEulerAnglesZYX(): Vector3f {
        val src = FloatArray(16)
        store(FloatBuffer.wrap(src))
        val x = Math.atan2(src[index(1, 2)].toDouble(), src[index(2, 2)].toDouble()).toFloat()
        val y = Math.atan2((-src[index(0, 2)]).toDouble(), Math.sqrt((1F - src[index(0, 2)] * src[index(0, 2)]).toDouble())).toFloat()
        val z = Math.atan2(src[index(0, 1)].toDouble(), src[index(0, 0)].toDouble()).toFloat()
        return Vector3f(x, y, z)
    }
    open fun getEulerAnglesXYZ(): Vector3f {
        val src = FloatArray(16)
        store(FloatBuffer.wrap(src))
        val x = Math.atan2((-src[index(2, 1)]).toDouble(), src[index(2, 2)].toDouble()).toFloat()
        val y = Math.atan2(src[index(2, 0)].toDouble(), Math.sqrt((1F - src[index(2, 0)] * src[index(2, 0)]).toDouble())).toFloat()
        val z = Math.atan2((-src[index(1, 0)]).toDouble(), src[index(0, 0)].toDouble()).toFloat()
        return Vector3f(x, y, z)
    }
    open fun getEulerAnglesYXZ(): Vector3f {
        val src = FloatArray(16)
        store(FloatBuffer.wrap(src))
        val x = Math.atan2((-src[index(2, 1)]).toDouble(), Math.sqrt((1F - src[index(2, 1)] * src[index(2, 1)]).toDouble())).toFloat()
        val y = Math.atan2(src[index(2, 0)].toDouble(), src[index(2, 2)].toDouble()).toFloat()
        val z = Math.atan2(src[index(1, 0)].toDouble(), src[index(1, 1)].toDouble()).toFloat()
        return Vector3f(x, y, z)
    }

    // JVM protected preserves Java package access without exposing a new public method.
    protected open fun index(row: Int, column: Int): Int = column * 4 + row
    override fun hashCode(): Int = impl!!.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        return impl!!.equals((other as Matrix4f).impl)
    }
    override fun toString(): String {
        val src = FloatArray(16)
        store(FloatBuffer.wrap(src))
        val result = StringBuilder()
        result.append("Matrix4f[\n")
        for (i in 0 until 4) {
            result.append("  ")
            for (j in 0 until 4) { result.append(src[i * 4 + j]); result.append(", ") }
            result.append("\n")
        }
        result.append("]")
        return result.toString()
    }
    override fun getAsMatrix4f(): Matrix4f = this
    override fun getAsMatrix3f(): Matrix3f = Matrix3f(this)
    override fun getAsPose(): Pose = Pose(this)
    override fun getAsPoseStack(): PoseStack = PoseStack(getAsPose())
    override fun getAsMatrices(): Matrices = Matrices(this)

    companion object {
        // open keeps the generated Java static bridge non-final, preserving subclass method hiding.
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun translation(x: Float, y: Float, z: Float): Matrix4f {
            val result = Matrix4f()
            result.impl!!.translation(x, y, z)
            return result
        }
        @JvmField val IDENTITY = Matrix4f()
    }
}
