package cn.zbx1425.sowcer.math

open class PoseStack(private val impl: com.mojang.blaze3d.vertex.PoseStack?) : Posture {
    constructor() : this(com.mojang.blaze3d.vertex.PoseStack())
    constructor(pose: Pose) : this() { impl!!.last().set(pose.asMoj()) }

    open fun pushPose() { impl!!.pushPose() }
    open fun popPose() { impl!!.popPose() }
    open fun mul(rotation: Quaternionf) { impl!!.mulPose(rotation.asMoj()) }
    open fun last(): Pose = Pose(impl!!.last())
    open fun clear(): Boolean = impl!!.isEmpty
    open fun setIdentity() { impl!!.setIdentity() }
    override fun getAsPoseStack(): PoseStack = this
    override fun getAsPose(): Pose = last()
    override fun getAsMatrix4f(): Matrix4f? = last().pose()
    override fun getAsMatrix3f(): Matrix3f? = last().normal()
    override fun getAsMatrices(): Matrices = Matrices(getAsMatrix4f()!!)
}
