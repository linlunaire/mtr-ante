package cn.zbx1425.sowcerext.model

import cn.zbx1425.sowcer.batch.MaterialProp
import cn.zbx1425.sowcer.math.Matrix4f
import cn.zbx1425.sowcer.math.Vector3f
import cn.zbx1425.sowcer.model.Mesh
import cn.zbx1425.sowcer.`object`.IndexBuf
import cn.zbx1425.sowcer.`object`.VertBuf
import cn.zbx1425.sowcer.util.AttrUtil
import cn.zbx1425.sowcer.util.DrawContext
import cn.zbx1425.sowcer.vertex.VertAttrMapping
import cn.zbx1425.sowcer.vertex.VertAttrType
import cn.zbx1425.sowcerext.model.integration.FaceList
import net.minecraft.client.renderer.texture.OverlayTexture
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

open class RawMesh {
    @JvmField val materialProp: MaterialProp?
    @JvmField var vertices: MutableList<Vertex?>? = ArrayList()
    @JvmField var faces: MutableList<Face?>? = ArrayList()

    constructor(materialProp: MaterialProp?) { this.materialProp = materialProp }

    @Throws(IOException::class)
    constructor(dis: DataInputStream?) {
        materialProp = MaterialProp(forwardNullable(dis))
        val numVertices = dis!!.readInt()
        vertices = ArrayList(numVertices)
        for (i in 0 until numVertices) vertices!!.add(Vertex(dis))
        val numFaces = dis.readInt()
        faces = ArrayList(numFaces)
        for (i in 0 until numFaces) faces!!.add(Face(dis))
    }

    open fun append(nextMesh: RawMesh?) {
        if (nextMesh === this) throw IllegalStateException("Mesh self-appending")
        val vertOffset = vertices!!.size
        vertices!!.addAll(nextMesh!!.vertices!!)
        for (face in nextMesh.faces!!) {
            val newFace = face!!.copy()
            var i = 0
            while (i < newFace.vertices!!.size) { newFace.vertices!![i] += vertOffset; i++ }
            faces!!.add(newFace)
        }
    }

    open fun appendTransformed(nextMesh: RawMesh?, mat: Matrix4f?, color: Int, light: Int) {
        if (nextMesh === this) throw IllegalStateException("Mesh self-appending")
        val vertOffset = vertices!!.size
        for (vertex in nextMesh!!.vertices!!) {
            val position = vertex!!.position
            val newVertex = Vertex(mat!!.transform(forwardNullable(position)), mat.transform3(forwardNullable(vertex.normal)))
            newVertex.u = vertex.u
            newVertex.v = vertex.v
            newVertex.color = color
            newVertex.light = light
            vertices!!.add(newVertex)
        }
        for (face in nextMesh.faces!!) {
            val newFace = face!!.copy()
            var i = 0
            while (i < newFace.vertices!!.size) { newFace.vertices!![i] += vertOffset; i++ }
            faces!!.add(newFace)
        }
    }

    open fun clear() { vertices!!.clear(); faces!!.clear() }

    open fun validateVertIndex() {
        for (face in faces!!) for (vertIndex in face!!.vertices!!) {
            if (vertIndex < 0 || vertIndex >= vertices!!.size) {
                throw IndexOutOfBoundsException("RawMesh contains invalid vertex index $vertIndex (Should be 0 to ${vertices!!.size - 1})")
            }
        }
    }

    open fun triangulate() {
        val newFaces = ArrayList<Face?>()
        for (face in faces!!) newFaces.addAll(Face.triangulate(face!!.vertices, false))
        faces!!.clear()
        faces!!.addAll(newFaces)
    }

    /** Retains first-occurrence order, public lists and the legacy hash callback sequence. */
    open fun distinct() {
        val distinctVertices = ArrayList<Vertex?>(vertices!!.size)
        val verticesLookup = HashMap<Vertex?, Int>(vertices!!.size)
        val distinctFaces = LinkedHashSet<Face?>(faces!!.size)
        for (face in faces!!) {
            var i = 0
            while (i < face!!.vertices!!.size) {
                val vertex = vertices!![face.vertices!![i]]
                val newIndex: Int
                if (verticesLookup.containsKey(vertex)) newIndex = verticesLookup[vertex]!!
                else {
                    distinctVertices.add(vertex)
                    newIndex = distinctVertices.size - 1
                    verticesLookup[vertex] = newIndex
                }
                face.vertices!![i] = newIndex
                i++
            }
            distinctFaces.add(face)
        }
        vertices!!.clear()
        vertices!!.addAll(distinctVertices)
        faces!!.clear()
        faces!!.addAll(distinctFaces)
    }

    /** Generates missing normals and retains Java's float subtraction before double arithmetic. */
    open fun generateNormals() {
        val newVertices = ArrayList<Vertex?>(vertices!!.size)
        for (face in faces!!) {
            if (face!!.vertices!!.size >= 3) {
                val i0 = face.vertices!![0]
                val i1 = face.vertices!![1]
                val i2 = face.vertices!![2]
                val ax = (vertices!![i1]!!.position!!.x() - vertices!![i0]!!.position!!.x()).toDouble()
                val ay = (vertices!![i1]!!.position!!.y() - vertices!![i0]!!.position!!.y()).toDouble()
                val az = (vertices!![i1]!!.position!!.z() - vertices!![i0]!!.position!!.z()).toDouble()
                val bx = (vertices!![i2]!!.position!!.x() - vertices!![i0]!!.position!!.x()).toDouble()
                val by = (vertices!![i2]!!.position!!.y() - vertices!![i0]!!.position!!.y()).toDouble()
                val bz = (vertices!![i2]!!.position!!.z() - vertices!![i0]!!.position!!.z()).toDouble()
                val nx = ay * bz - az * by
                val ny = az * bx - ax * bz
                val nz = ax * by - ay * bx
                var t = nx * nx + ny * ny + nz * nz
                if (t != 0.0) {
                    t = 1.0 / Math.sqrt(t)
                    val mx = (nx * t).toFloat()
                    val my = (ny * t).toFloat()
                    val mz = (nz * t).toFloat()
                    var j = 0
                    while (j < face.vertices!!.size) {
                        val newVert = vertices!![face.vertices!![j]]!!.copy()
                        if (vecIsZero(newVert.normal)) newVert.normal = Vector3f(mx, my, mz)
                        newVertices.add(newVert)
                        face.vertices!![j] = newVertices.size - 1
                        j++
                    }
                } else {
                    var j = 0
                    while (j < face.vertices!!.size) {
                        val newVert = vertices!![face.vertices!![j]]!!.copy()
                        if (vecIsZero(vertices!![face.vertices!![j]]!!.normal)) newVert.normal = Vector3f(0F, 1F, 0F)
                        newVertices.add(newVert)
                        face.vertices!![j] = newVertices.size - 1
                        j++
                    }
                }
            }
        }
        vertices = newVertices
    }

    open fun upload(mesh: Mesh?, mapping: VertAttrMapping?) { _uploadAsync(mapping).accept(mesh) }

    open fun upload(mapping: VertAttrMapping?): Mesh {
        validateVertIndex()
        val vertBufObj = VertBuf()
        val indexBufObj = IndexBuf(faces!!.size, 0x1405)
        val target = Mesh(vertBufObj, indexBufObj, materialProp)
        upload(target, mapping)
        return target
    }

    open fun uploadAsync(mapping: VertAttrMapping?): Supplier<Mesh> {
        val upload = _uploadAsync(mapping)
        val capturedMaterial = materialProp!!.copy()
        return object : Supplier<Mesh> {
            private var result: Mesh? = null
            @Synchronized override fun get(): Mesh {
                if (result == null) {
                    val candidate = Mesh(VertBuf(), IndexBuf(0, 0x1405), capturedMaterial)
                    try { upload.accept(candidate); result = candidate }
                    catch (error: RuntimeException) { candidate.close(); throw error }
                    catch (error: Error) { candidate.close(); throw error }
                }
                return result!!
            }
        }
    }

    private fun _uploadAsync(mapping: VertAttrMapping?): Consumer<Mesh?> {
        // Packing owns its geometry and must not rewrite caller indices.
        val snapshot = copy()
        snapshot.validateVertIndex()
        snapshot.triangulate()
        snapshot.distinct()
        val vertices = snapshot.vertices!!
        val faces = snapshot.faces!!
        val faceCount = faces.size
        val vertBuf = ByteBuffer.allocate(Math.multiplyExact(vertices.size, mapping!!.strideVertex)).order(ByteOrder.nativeOrder())
        val attributes = VertAttrType.entries
        var i = 0
        while (i < vertices.size) {
            val vertex = vertices[i]
            // Indexed shared entries avoid both values() clones and per-vertex iterators.
            for (typeIndex in 0 until attributes.size) {
                val type = attributes[typeIndex]
                if (!mapping.sources[type]!!.inVertBuf()) continue
                vertBuf.position(getVertBufPos(mapping, i, type))
                when (type) {
                    VertAttrType.POSITION -> vertBuf.putFloat(vertex!!.position!!.x()).putFloat(vertex.position!!.y()).putFloat(vertex.position!!.z())
                    VertAttrType.COLOR -> vertBuf.putInt(vertex!!.color)
                    VertAttrType.UV_TEXTURE -> vertBuf.putFloat(vertex!!.u).putFloat(vertex.v)
                    VertAttrType.UV_OVERLAY -> {
                        val packed = snapshot.materialProp!!.attrState!!.overlayUV?.let { AttrUtil.exchangeLightmapUVBits(it) } ?: OverlayTexture.NO_OVERLAY
                        vertBuf.putShort(packed.toShort()).putShort((packed ushr 16).toShort())
                    }
                    VertAttrType.UV_LIGHTMAP -> vertBuf.putShort(vertex!!.light.toShort()).putShort((vertex.light ushr 16).toShort())
                    VertAttrType.NORMAL -> {
                        val normal = vertex!!.normal!!.copy()
                        if (!vecIsZero(normal)) normal.normalize()
                        vertBuf.put((normal.x() * 127).toInt().toByte()).put((normal.y() * 127).toInt().toByte()).put((normal.z() * 127).toInt().toByte())
                    }
                    VertAttrType.MATRIX_MODEL -> {
                        var matrix: Matrix4f? = Matrix4f()
                        if (snapshot.materialProp!!.attrState!!.matrixModel != null) matrix = snapshot.materialProp.attrState!!.matrixModel!!.apply(matrix)
                        for (value in matrix!!.asMoj()!!.get(FloatArray(16))) vertBuf.putFloat(value)
                    }
                }
            }
            i++
        }
        val indexBuf = ByteBuffer.allocate(Math.multiplyExact(faceCount, 12)).order(ByteOrder.nativeOrder())
        for (face in faces) for (vertex in face!!.vertices!!) indexBuf.putInt(vertex)
        return Consumer { mesh ->
            mesh!!.vertBuf!!.upload(vertBuf, VertBuf.USAGE_STATIC_DRAW)
            mesh.indexBuf!!.upload(indexBuf, VertBuf.USAGE_STATIC_DRAW)
            mesh.indexBuf!!.setFaceCount(faceCount)
        }
    }

    open fun applyMatrix(matrix: Matrix4f?) {
        for (vertex in vertices!!) {
            val position = vertex!!.position
            vertex.position = matrix!!.transform(forwardNullable(position))
            vertex.normal = matrix.transform3(forwardNullable(vertex.normal))
        }
    }

    open fun applyTranslation(x: Float, y: Float, z: Float) {
        for (vertex in vertices!!) vertex!!.position!!.add(x, y, z)
    }

    open fun applyRotation(axis: Vector3f?, angle: Float) {
        for (vertex in vertices!!) {
            vertex!!.position!!.rotDeg(forwardNullable(axis), angle)
            vertex.normal!!.rotDeg(forwardNullable(axis), angle)
        }
    }

    open fun applyScale(x: Float, y: Float, z: Float) {
        val rx = (1.0 / x).toFloat()
        val ry = (1.0 / y).toFloat()
        val rz = (1.0 / z).toFloat()
        val rx2 = rx * rx
        val ry2 = ry * ry
        val rz2 = rz * rz
        val reverse = x * y * z < 0.0
        for (vertex in vertices!!) {
            vertex!!.position!!.mul(x, y, z)
            val nx2 = vertex.normal!!.x() * vertex.normal!!.x()
            val ny2 = vertex.normal!!.y() * vertex.normal!!.y()
            val nz2 = vertex.normal!!.z() * vertex.normal!!.z()
            var u = nx2 * rx2 + ny2 * ry2 + nz2 * rz2
            if (u != 0.0F) {
                u = Math.sqrt(((nx2 + ny2 + nz2) / u).toDouble()).toFloat()
                vertex.normal!!.mul(rx * u, ry * u, rz * u)
            }
        }
        if (reverse) for (face in faces!!) face!!.flip()
    }

    open fun applyMirror(vx: Boolean, vy: Boolean, vz: Boolean, nx: Boolean, ny: Boolean, nz: Boolean) {
        for (vertex in vertices!!) {
            vertex!!.position!!.mul(if (vx) -1F else 1F, if (vy) -1F else 1F, if (vz) -1F else 1F)
            vertex.normal!!.mul(if (nx) -1F else 1F, if (ny) -1F else 1F, if (nz) -1F else 1F)
        }
        var numFlips = 0
        if (vx) numFlips++
        if (vy) numFlips++
        if (vz) numFlips++
        if (numFlips % 2 != 0) for (face in faces!!) face!!.flip()
    }

    open fun applyUVMirror(u: Boolean, v: Boolean) {
        for (vertex in vertices!!) {
            if (u) vertex!!.u = 1 - vertex.u
            if (v) vertex!!.v = 1 - vertex.v
        }
    }

    open fun applyShear(dir: Vector3f?, shear: Vector3f?, ratio: Float) {
        for (vertex in vertices!!) {
            val n1 = ratio * (dir!!.x() * vertex!!.position!!.x() + dir.y() * vertex.position!!.y() + dir.z() * vertex.position!!.z())
            val offset1 = shear!!.copy()
            offset1.mul(n1)
            vertex.position!!.add(forwardNullable(offset1))
            if (!vecIsZero(vertex.normal)) {
                val n2 = ratio * (shear.x() * vertex.normal!!.x() + shear.y() * vertex.normal!!.y() + shear.z() * vertex.normal!!.z())
                val offset2 = dir.copy()
                offset2.mul(-n2)
                vertex.normal!!.add(forwardNullable(offset2))
                vertex.normal!!.normalize()
            }
        }
    }

    open fun setRenderType(type: String?) {
        materialProp!!.translucent = false
        materialProp.writeDepthBuf = true
        materialProp.cutoutHack = false
        materialProp.attrState = materialProp.attrState!!.copy()
        materialProp.attrState!!.lightmapUV = null
        when (type!!) {
            "exterior" -> materialProp.shaderName = "rendertype_entity_cutout"
            "exteriortranslucent" -> { materialProp.shaderName = "rendertype_entity_translucent_cull"; materialProp.translucent = true }
            "interior" -> { materialProp.shaderName = "rendertype_entity_cutout"; materialProp.attrState!!.setLightmapUV((15 shl 4) or (15 shl 20)) }
            "interiortranslucent" -> {
                materialProp.shaderName = "rendertype_entity_translucent_cull"
                materialProp.translucent = true
                materialProp.attrState!!.setLightmapUV((15 shl 4) or (15 shl 20))
            }
            "light" -> { materialProp.shaderName = "rendertype_beacon_beam"; materialProp.cutoutHack = true }
            "lighttranslucent" -> { materialProp.shaderName = "rendertype_beacon_beam"; materialProp.translucent = true; materialProp.writeDepthBuf = false }
            else -> throw IllegalArgumentException("Invalid render type: $type")
        }
    }

    open fun writeBlazeBuffer(vertexConsumer: FaceList?, matrix: Matrix4f?, color: Int, light: Int, overlay: Int, drawContext: DrawContext?) {
        val count = faces!!.size
        drawContext!!.recordBlazeAction(count)
        for (face in faces!!) {
            if (ASSERTIONS_ENABLED && face!!.vertices!!.size != 3) throw AssertionError()
            val transformedVertices = arrayOfNulls<Vertex>(face!!.vertices!!.size)
            var i = 0
            while (i < face.vertices!!.size) {
                val position = vertices!![face.vertices!![i]]!!.position
                val transformedPosition = matrix!!.transform(forwardNullable(position))
                val transformedNormal = matrix.transform3(forwardNullable(vertices!![face.vertices!![i]]!!.normal))
                transformedVertices[i] = Vertex(transformedPosition, transformedNormal)
                transformedVertices[i]!!.u = vertices!![face.vertices!![i]]!!.u
                transformedVertices[i]!!.v = vertices!![face.vertices!![i]]!!.v
                i++
            }
            vertexConsumer!!.addFace(transformedVertices, color, light, overlay)
        }
    }

    open fun copy(): RawMesh {
        val result = RawMesh(materialProp!!.copy())
        for (vertex in vertices!!) result.vertices!!.add(vertex!!.copy())
        for (face in faces!!) result.faces!!.add(face!!.copy())
        return result
    }

    open fun copyForMaterialChanges(): RawMesh {
        val result = RawMesh(materialProp!!.copy())
        result.vertices = vertices
        result.faces = faces
        return result
    }

    @Throws(IOException::class)
    open fun serializeTo(dos: DataOutputStream?) {
        materialProp!!.serializeTo(forwardNullable(dos))
        writeInt(dos, vertices!!.size)
        for (vertex in vertices!!) vertex!!.serializeTo(dos)
        writeInt(dos, faces!!.size)
        for (face in faces!!) face!!.serializeTo(dos)
    }

    open fun setMatixProcess(matrixProcess: Function<Matrix4f?, Matrix4f?>?) { materialProp!!.setMatixProcess(matrixProcess) }

    private fun writeInt(output: DataOutputStream?, value: Int) { output!!.writeInt(value) }

    companion object {
        private val ASSERTIONS_ENABLED = RawMesh::class.java.desiredAssertionStatus()
        private fun getVertBufPos(mapping: VertAttrMapping, vertId: Int, type: VertAttrType): Int = mapping.strideVertex * vertId + mapping.pointers[type]!!
        private fun vecIsZero(vec: Vector3f?): Boolean = vec!!.x() == 0F && vec.y() == 0F && vec.z() == 0F
        @Suppress("UNCHECKED_CAST") private fun <T> forwardNullable(value: T?): T = value as T
    }
}
