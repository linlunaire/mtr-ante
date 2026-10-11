package cn.zbx1425.sowcerext.model.integration

import cn.zbx1425.sowcer.batch.MaterialProp
import cn.zbx1425.sowcerext.model.Face
import cn.zbx1425.sowcerext.model.RawMesh
import cn.zbx1425.sowcerext.model.Vertex
import cn.zbx1425.sowcer.math.Vector3f
import net.minecraft.resources.Identifier
import java.util.stream.IntStream

open class RawMeshBuilder(private val faceSize: Int, renderType: String?, texture: Identifier?) {
    private val mesh: RawMesh
    private var buildingVertex = Vertex()

    init {
        mesh = RawMesh(MaterialProp())
        mesh.setRenderType(renderType)
        mesh.materialProp!!.texture = texture
        mesh.materialProp.attrState!!.setColor(255, 255, 255, 255)
    }

    open fun getMesh(): RawMesh = mesh

    open fun reset(): RawMeshBuilder {
        mesh.vertices!!.clear()
        mesh.faces!!.clear()
        setNewDefaultVertex()
        return this
    }

    open fun vertex(position: Vector3f?): RawMeshBuilder {
        buildingVertex.position = position
        return this
    }

    open fun vertex(d: Double, e: Double, f: Double): RawMeshBuilder {
        buildingVertex.position = Vector3f(d.toFloat(), e.toFloat(), f.toFloat())
        return this
    }

    open fun normal(f: Float, g: Float, h: Float): RawMeshBuilder {
        buildingVertex.normal = Vector3f(f, g, h)
        return this
    }

    open fun uv(f: Float, g: Float): RawMeshBuilder {
        buildingVertex.u = f
        buildingVertex.v = g
        return this
    }

    open fun endVertex(): RawMeshBuilder {
        mesh.vertices!!.add(buildingVertex)
        setNewDefaultVertex()
        if (mesh.vertices!!.size % faceSize == 0) {
            // Java evaluates triangulation before invoking a possibly null list receiver.
            appendFaces(mesh.faces, Face.triangulate(IntStream.range(mesh.vertices!!.size - faceSize, mesh.vertices!!.size).toArray(), false))
        }
        return this
    }

    open fun color(r: Int, g: Int, b: Int, a: Int): RawMeshBuilder {
        mesh.materialProp!!.attrState!!.setColor(r, g, b, a)
        return this
    }

    open fun lightMapUV(u: Short, v: Short): RawMeshBuilder {
        mesh.materialProp!!.attrState!!.setLightmapUV(u, v)
        return this
    }

    private fun setNewDefaultVertex(): RawMeshBuilder {
        buildingVertex = Vertex(Vector3f(0F, 0F, 0F))
        buildingVertex.normal = Vector3f(0F, 0F, 0F)
        buildingVertex.u = 0F
        buildingVertex.v = 0F
        return this
    }

    private fun appendFaces(faces: MutableList<Face?>?, triangles: Collection<Face>) {
        faces!!.addAll(triangles)
    }
}
