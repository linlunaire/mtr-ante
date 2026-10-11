package cn.zbx1425.sowcer.batch

import cn.zbx1425.sowcer.math.Matrix4f
import java.util.Objects

/** Shader state supplied at enqueue time. Affects batching. */
open class ShaderProp {
    /**
     * Null: model matrices derive from the entity render pose and cannot be instanced.
     * Non-null: model matrices use world space and may be instanced.
     * Normals use world coordinates in both cases.
     */
    @JvmField var viewMatrix: Matrix4f? = null

    open fun setViewMatrix(viewMatrix: Matrix4f?): ShaderProp { this.viewMatrix = viewMatrix; return this }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        return Objects.equals(viewMatrix, (other as ShaderProp).viewMatrix)
    }

    override fun hashCode(): Int = Objects.hash(viewMatrix)

    companion object {
        @JvmField var DEFAULT: ShaderProp? = ShaderProp()
    }
}
