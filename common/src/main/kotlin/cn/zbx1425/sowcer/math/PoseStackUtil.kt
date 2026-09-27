package cn.zbx1425.sowcer.math

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis

open class PoseStackUtil {
    // open affects the generated Java static bridge, where existing subclasses may hide these helpers.
    @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
    companion object {
        @JvmStatic open fun rotX(matrices: PoseStack, rad: Float) { matrices.mulPose(Axis.XP.rotation(rad)) }
        @JvmStatic open fun rotY(matrices: PoseStack, rad: Float) { matrices.mulPose(Axis.YP.rotation(rad)) }
        @JvmStatic open fun rotZ(matrices: PoseStack, rad: Float) { matrices.mulPose(Axis.ZP.rotation(rad)) }
    }
}
