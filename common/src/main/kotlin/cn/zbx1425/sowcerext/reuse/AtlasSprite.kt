package cn.zbx1425.sowcerext.reuse

import cn.zbx1425.sowcerext.model.RawMesh
import cn.zbx1425.sowcerext.model.Vertex
import cn.zbx1425.sowcerext.util.Logging
import net.minecraft.resources.Identifier

open class AtlasSprite(
    @JvmField var sheet: Identifier?,
    @JvmField var sheetWidth: Int,
    @JvmField var sheetHeight: Int,
    @JvmField var frameX: Int,
    @JvmField var frameY: Int,
    @JvmField var frameWidth: Int,
    @JvmField var frameHeight: Int,
    @JvmField var spriteX: Int,
    @JvmField var spriteY: Int,
    @JvmField var spriteWidth: Int,
    @JvmField var spriteHeight: Int,
    @JvmField var sourceWidth: Int,
    @JvmField var sourceHeight: Int,
    @JvmField var rotated: Boolean // TODO
) {
    open fun applyToMesh(mesh: RawMesh?) {
        var uvBleeding = false
        for (vertex: Vertex? in mesh!!.vertices!!) {
            vertex!!.u = mapRange(vertex.u, spriteX.toFloat() / sourceWidth, (spriteX + spriteWidth).toFloat() / sourceWidth, 0F, 1F)
            vertex.v = mapRange(vertex.v, spriteY.toFloat() / spriteHeight, (spriteY + spriteHeight).toFloat() / sourceHeight, 0F, 1F)
            if (vertex.u < 0 || vertex.u > 1 || vertex.v < 0 || vertex.v > 1) uvBleeding = true
            vertex.u = mapRange(vertex.u, 0F, 1F, frameX.toFloat() / sheetWidth, (frameX + frameWidth).toFloat() / sheetWidth)
            vertex.v = mapRange(vertex.v, 0F, 1F, frameY.toFloat() / sheetHeight, (frameY + frameHeight).toFloat() / sheetHeight)
        }
        if (uvBleeding) {
            Logging.LOGGER.warn("UV bleeding into adjacent sprite in " + mesh.materialProp!!.texture)
        }
        mesh.materialProp!!.texture = sheet
    }

    private fun mapRange(x: Float, inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float =
        (x - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
}
