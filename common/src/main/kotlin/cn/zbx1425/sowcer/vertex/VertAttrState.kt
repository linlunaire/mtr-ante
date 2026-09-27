package cn.zbx1425.sowcer.vertex

import cn.zbx1425.sowcer.math.Matrix4f
import cn.zbx1425.sowcer.math.Vector3f
import cn.zbx1425.sowcer.util.AttrUtil
import net.minecraft.client.renderer.texture.OverlayTexture
import java.util.Objects
import java.util.function.Function

open class VertAttrState {
    @JvmField var position: Vector3f? = null
    @JvmField var color: Int? = null
    @JvmField var texU: Float? = null
    @JvmField var texV: Float? = null
    @JvmField var overlayUV: Int? = null
    @JvmField var lightmapUV: Int? = null
    @JvmField var normal: Vector3f? = null
    @JvmField var matrixModel: Function<Matrix4f?, Matrix4f?>? = null
    @JvmField var useMatixProcess: Boolean = false

    open fun setPosition(position: Vector3f?): VertAttrState { this.position = position; return this }
    open fun setColor(r: Int, g: Int, b: Int, a: Int): VertAttrState {
        color = (r shl 24) or (g shl 16) or (b shl 8) or a
        return this
    }
    open fun setColor(rgba: Int): VertAttrState { color = rgba; return this }
    open fun setTextureUV(u: Float, v: Float): VertAttrState { texU = u; texV = v; return this }
    open fun setLightmapUV(u: Short, v: Short): VertAttrState {
        // Deliberately retain Java sign extension for the short v operand.
        lightmapUV = (u.toInt() shl 16) or v.toInt()
        return this
    }
    open fun setOverlayUV(uv: Int): VertAttrState { overlayUV = AttrUtil.exchangeLightmapUVBits(uv); return this }
    open fun setOverlayUVNoOverlay(): VertAttrState {
        overlayUV = AttrUtil.exchangeLightmapUVBits(OverlayTexture.NO_OVERLAY)
        return this
    }
    open fun setLightmapUV(uv: Int): VertAttrState { lightmapUV = uv; return this }
    open fun setNormal(position: Vector3f?): VertAttrState { normal = position; return this }
    open fun setModelMatrix(matrix: Matrix4f?): VertAttrState {
        matrixModel = Function { matrix }
        return this
    }
    open fun setMatixProcess(matrixProcess: Function<Matrix4f?, Matrix4f?>?): VertAttrState {
        matrixModel = matrixProcess
        useMatixProcess = matrixProcess != null
        return this
    }
    open fun useMatixProcess(): Boolean = useMatixProcess

    open fun hasAttr(attrType: VertAttrType): Boolean = when (attrType) {
        VertAttrType.POSITION -> position != null
        VertAttrType.COLOR -> color != null
        VertAttrType.NORMAL -> normal != null
        VertAttrType.UV_OVERLAY -> overlayUV != null
        VertAttrType.UV_TEXTURE -> texU != null && texV != null
        VertAttrType.UV_LIGHTMAP -> lightmapUV != null
        VertAttrType.MATRIX_MODEL -> matrixModel != null
    }

    open fun clearAttr(attrType: VertAttrType) {
        when (attrType) {
            VertAttrType.POSITION -> position = null
            VertAttrType.COLOR -> color = null
            VertAttrType.NORMAL -> normal = null
            VertAttrType.UV_OVERLAY -> overlayUV = null
            VertAttrType.UV_TEXTURE -> { texU = null; texV = null }
            VertAttrType.UV_LIGHTMAP -> lightmapUV = null
            VertAttrType.MATRIX_MODEL -> matrixModel = null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as VertAttrState
        // Objects.equals preserves the original boxed Float NaN and signed-zero behavior.
        return Objects.equals(overlayUV, that.overlayUV) && useMatixProcess == that.useMatixProcess &&
            Objects.equals(position, that.position) && Objects.equals(color, that.color) && Objects.equals(texU, that.texU) &&
            Objects.equals(texV, that.texV) && Objects.equals(lightmapUV, that.lightmapUV) && Objects.equals(normal, that.normal) &&
            Objects.equals(matrixModel, that.matrixModel)
    }

    override fun hashCode(): Int = Objects.hash(position, color, texU, texV, overlayUV, lightmapUV, normal, matrixModel, useMatixProcess)

    open fun copy(): VertAttrState {
        val clone = VertAttrState()
        clone.position = position?.copy()
        clone.color = color
        clone.overlayUV = overlayUV
        clone.useMatixProcess = useMatixProcess
        clone.texU = texU
        clone.texV = texV
        clone.lightmapUV = lightmapUV
        clone.normal = normal?.copy()
        clone.matrixModel = matrixModel
        return clone
    }

    companion object {
        @JvmField val BILLBOARD: Function<Matrix4f?, Matrix4f?> = Function { matrix ->
            val pos = matrix!!.getTranslationPart()
            val result = Matrix4f()
            result.translate(pos)
            result
        }
    }
}
