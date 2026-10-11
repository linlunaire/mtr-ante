package cn.zbx1425.mtrsteamloco.data

import cn.zbx1425.mtrsteamloco.ClientConfig
import cn.zbx1425.sowcer.math.Matrix4f
import cn.zbx1425.sowcer.math.Quaternionf
import cn.zbx1425.sowcer.math.Vector3f
import com.mojang.blaze3d.vertex.PoseStack

open class Rolling {
    open class Rotation(x: Double, y: Double, z: Double, @JvmField val yaw: Float,
                        @JvmField val pitch: Float, @JvmField val roll: Float, @JvmField val reversed: Boolean) {
        @JvmField val pos = Vector3f(x, y, z)
        open fun isIdentity(): Boolean = yaw == 0F && pitch == 0F && roll == 0F
        open fun copy(): Rotation = Rotation(pos.x().toDouble(), pos.y().toDouble(), pos.z().toDouble(), yaw, pitch, roll, reversed)
        companion object {
            @JvmField val IDENTITY = Rotation(0.0, 0.0, 0.0, 0F, 0F, 0F, false)
        }
    }

    companion object {
        private var rotation = Rotation.IDENTITY
        private var tempRotation: Rotation? = null

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun setRotation(rotation: Rotation?) { tempRotation = rotation }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun update() {
            val pending = tempRotation
            if (pending != null) { rotation = pending; tempRotation = null } else rotation = Rotation.IDENTITY
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun applyRolling(poseStack: PoseStack?) {
            if (!ClientConfig.enableRolling || rotation.isIdentity()) return
            poseStack!!.mulPose(getRollQuaternion(true).asMoj())
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun applyRolling(pos: Vector3f?, eyeHeight: Float): Vector3f? {
            if (rotation.isIdentity()) return pos
            val result = pos!!.copy()
            val rot = rotation
            val matrix = Matrix4f()
            matrix.rotateY(rot.yaw)
            matrix.rotateX(-rot.pitch)
            matrix.rotateZ(if (rot.reversed) rot.roll else -rot.roll)
            result.add(0F, -eyeHeight, 0F)
            val eye = matrix.transform(Vector3f(0F, eyeHeight, 0F))
            result.add(eye)
            return result
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun getRollQuaternion(): Quaternionf = getRollQuaternion(false)

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun getRollQuaternion(reversed: Boolean): Quaternionf {
            if (rotation.isIdentity()) return Quaternionf(Vector3f(0F, 0F, 0F), 0F)
            val rot = rotation
            val roll = if (reversed != rot.reversed) rot.roll else -rot.roll
            val pitch = if (reversed) rot.pitch else -rot.pitch
            return if (ClientConfig.enableRolling) Quaternionf().rotateY(rot.yaw).rotateX(pitch).rotateZ(roll).rotateY(-rot.yaw)
            else Quaternionf().rotateY(rot.yaw).rotateX(pitch).rotateY(-rot.yaw)
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun _getRollQuaternion(reversed: Boolean): Quaternionf {
            if (rotation.isIdentity() || !ClientConfig.enableRolling) return Quaternionf(Vector3f(0F, 0F, 0F), 0F)
            val rot = rotation
            val matrix = Matrix4f()
            matrix.rotateY(rot.yaw)
            matrix.rotateX(rot.pitch)
            val forward = matrix.transform(Vector3f(0F, 0F, 1F))
            return Quaternionf(forward, if (reversed != rot.reversed) rot.roll else -rot.roll)
        }
    }
}
