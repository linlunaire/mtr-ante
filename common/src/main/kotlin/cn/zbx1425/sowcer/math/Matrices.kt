package cn.zbx1425.sowcer.math

import java.util.Stack

open class Matrices : Posture {
    private val stack = Stack<Matrix4f>()

    constructor() { stack.push(Matrix4f()) }
    constructor(matrix: Matrix4f) { stack.push(matrix.copy()) }
    constructor(matrices: Matrices) {
        for (matrix in matrices.stack) stack.push(matrix.copy())
    }

    open fun copy(): Matrices = Matrices(this)
    open fun translate(x: Double, y: Double, z: Double) { translate(x.toFloat(), y.toFloat(), z.toFloat()) }
    open fun translate(x: Float, y: Float, z: Float) { stack.peek().translate(x, y, z) }
    open fun rotate(x: Float, y: Float, z: Float, radian: Float) { stack.peek().rotate(Vector3f(x, y, z), radian) }
    open fun rotateX(radian: Float) { stack.peek().rotateX(radian) }
    open fun rotateY(radian: Float) { stack.peek().rotateY(radian) }
    open fun rotateZ(radian: Float) { stack.peek().rotateZ(radian) }
    open fun pushPose() { stack.push(stack.peek().copy()) }
    open fun popPose() { stack.pop() }
    open fun popPushPose() { stack.pop(); stack.push(stack.peek().copy()) }
    open fun last(): Matrix4f = stack.peek()
    open fun clear(): Boolean = stack.size == 1
    open fun setIdentity() { stack.pop(); stack.push(Matrix4f()) }
    override fun getAsPoseStack(): PoseStack = PoseStack(getAsPose())
    override fun getAsPose(): Pose = Pose(last())
    override fun getAsMatrix4f(): Matrix4f = last()
    override fun getAsMatrix3f(): Matrix3f = Matrix3f(last())
    override fun getAsMatrices(): Matrices = this
}
