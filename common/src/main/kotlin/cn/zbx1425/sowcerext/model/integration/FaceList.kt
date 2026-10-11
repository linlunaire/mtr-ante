package cn.zbx1425.sowcerext.model.integration

import cn.zbx1425.sowcer.math.Vector3f
import cn.zbx1425.sowcerext.model.Vertex
import mtr.mappings.RenderBufferSource
import net.minecraft.client.renderer.rendertype.RenderType

open class FaceList(private val renderType: RenderType?, private val needSorting: Boolean) {
    private val queuedFaces = ArrayList<TransformedFace>()

    open fun addFace(vertices: Array<out Vertex?>?, color: Int, light: Int, overlay: Int) {
        queuedFaces.add(TransformedFace(vertices, color, light, overlay))
    }

    open fun commit(bufferSource: RenderBufferSource?) {
        val vertexConsumer = bufferSource!!.getBuffer(renderType)
        if (needSorting) {
            queuedFaces.sortWith { a, b ->
                -java.lang.Float.compare(a.sortingVector.distanceSq(Vector3f.ZERO), b.sortingVector.distanceSq(Vector3f.ZERO))
            }
        }
        for (face in queuedFaces) {
            for (vertex in face.vertices) {
                vertexConsumer
                    .addVertex(vertex!!.position!!.x(), vertex.position!!.y(), vertex.position!!.z())
                    .setColor(face.color)
                    .setUv(vertex.u, vertex.v)
                    .setOverlay(face.overlay)
                    .setLight(face.light)
                    .setNormal(vertex.normal!!.x(), vertex.normal!!.y(), vertex.normal!!.z())
            }
        }
    }

    // Geometry stays live until commit; only the summed sorting position is captured here.
    private class TransformedFace(vertices: Array<out Vertex?>?, val color: Int, val light: Int, val overlay: Int) {
        val vertices = vertices!!
        val sortingVector = Vector3f(0F, 0F, 0F)
        init { for (vertex in this.vertices) sortingVector.add(vertex!!.position!!) }
    }
}
