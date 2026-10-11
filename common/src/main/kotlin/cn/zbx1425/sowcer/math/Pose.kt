package cn.zbx1425.sowcer.math

import net.minecraft.util.Mth

open class Pose : Posture {
    private val pose: Matrix4f?
    private val normal: Matrix3f?

    constructor() { pose = Matrix4f(); normal = Matrix3f() }
    constructor(pose: Pose) {
        this.pose = Matrix4f(pose.pose()!!)
        normal = Matrix3f(pose.normal()!!)
    }
    constructor(pose: com.mojang.blaze3d.vertex.PoseStack.Pose) {
        this.pose = Matrix4f(pose.pose())
        normal = Matrix3f(pose.normal())
    }
    constructor(pose: Matrix4f) { this.pose = pose; normal = Matrix3f(pose) }
    constructor(pose: Matrix4f?, normal: Matrix3f?) { this.pose = pose; this.normal = normal }

    open fun pose(): Matrix4f? = pose
    open fun normal(): Matrix3f? = normal
    open fun translate(x: Float, y: Float, z: Float) { pose!!.translate(x, y, z) }
    open fun scale(x: Float, y: Float, z: Float) {
        pose!!.scale(x, y, z)
        if (x == y && y == z) {
            if (x > 0F) return
            normal!!.scale(-1F)
        }
        val f = 1F / x
        val f1 = 1F / y
        val f2 = 1F / z
        val f3 = Mth.fastInvCubeRoot(f * f1 * f2)
        normal!!.scale(f3 * f, f3 * f1, f3 * f2)
    }
    open fun scale(factor: Float) { scale(factor, factor, factor) }
    open fun multiply(q: Quaternionf) { pose!!.multiply(q); normal!!.multiply(q) }
    open fun rotateX(angle: Float) { multiply(Quaternionf(Vector3f.XP, angle)) }
    open fun rotateY(angle: Float) { multiply(Quaternionf(Vector3f.YP, angle)) }
    open fun rotateZ(angle: Float) { multiply(Quaternionf(Vector3f.ZP, angle)) }
    open fun asMoj(): com.mojang.blaze3d.vertex.PoseStack.Pose {
        val result = com.mojang.blaze3d.vertex.PoseStack.Pose()
        result.mulPose(pose!!.asMoj()!!)
        result.normal().set(normal!!.asMoj())
        return result
    }
    override fun getAsMatrix4f(): Matrix4f? = pose
    override fun getAsMatrix3f(): Matrix3f? = normal
    override fun getAsPose(): Pose = this
    override fun getAsPoseStack(): PoseStack = PoseStack(this)
    override fun getAsMatrices(): Matrices = Matrices(getAsMatrix4f()!!)
}
