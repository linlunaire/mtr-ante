package cn.zbx1425.sowcer.math

interface Posture {
    fun getAsMatrix4f(): Matrix4f?
    fun getAsMatrix3f(): Matrix3f?
    fun getAsMatrices(): Matrices?
    fun getAsPose(): Pose?
    fun getAsPoseStack(): PoseStack?
}
