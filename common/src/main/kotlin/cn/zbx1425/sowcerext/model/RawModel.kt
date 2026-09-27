package cn.zbx1425.sowcerext.model

import cn.zbx1425.sowcer.batch.MaterialProp
import cn.zbx1425.sowcer.math.Matrix4f
import cn.zbx1425.sowcer.math.Vector3f
import cn.zbx1425.sowcer.model.Mesh
import cn.zbx1425.sowcer.model.Model
import cn.zbx1425.sowcer.util.AttrUtil
import cn.zbx1425.sowcer.util.DrawContext
import cn.zbx1425.sowcer.vertex.VertAttrMapping
import cn.zbx1425.sowcer.vertex.VertAttrType
import cn.zbx1425.sowcerext.model.integration.BufferSourceProxy
import net.minecraft.resources.Identifier
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.function.Function
import java.util.function.Supplier

open class RawModel {
    @JvmField var sourceLocation: Identifier? = null
    @JvmField var meshList: HashMap<MaterialProp?, RawMesh?>? = HashMap()
    private var originalMaterialProps: MutableMap<RawMesh?, MaterialProp?>? = null

    constructor()

    @Throws(IOException::class)
    constructor(dis: DataInputStream?) {
        val count = dis!!.readInt()
        for (i in 0 until count) append(RawMesh(dis))
    }

    open fun upload(mapping: VertAttrMapping?): Model {
        val model = Model()
        for (mesh in meshList!!.values) {
            if (mesh!!.faces!!.isEmpty()) continue
            model.meshList!!.add(mesh.upload(mapping))
        }
        return model
    }

    open fun uploadAsync(mapping: VertAttrMapping?): Supplier<Model> {
        val uploads = ArrayList<Supplier<Mesh>?>()
        for (mesh in meshList!!.values) {
            if (mesh!!.faces!!.isNotEmpty()) uploads.add(mesh.uploadAsync(mapping))
        }
        return object : Supplier<Model> {
            private var result: Model? = null

            @Synchronized override fun get(): Model {
                if (result == null) {
                    val candidate = Model()
                    for (upload in uploads) candidate.meshList!!.add(upload!!.get())
                    result = candidate
                }
                return result!!
            }
        }
    }

    open fun append(nextMesh: RawMesh?) {
        if (meshList!!.containsKey(nextMesh!!.materialProp)) {
            meshList!![nextMesh.materialProp]!!.append(nextMesh)
        } else {
            val newMesh = RawMesh(nextMesh.materialProp)
            meshList!![nextMesh.materialProp] = newMesh
            newMesh.append(nextMesh)
        }
    }

    open fun appendTransformed(nextModel: RawModel?, mat: Matrix4f?, color: Int, light: Int) {
        for (nextMesh in nextModel!!.meshList!!.values) {
            if (meshList!!.containsKey(nextMesh!!.materialProp)) {
                meshList!![nextMesh.materialProp]!!.appendTransformed(nextMesh, mat, color, light)
            } else {
                val newMesh = RawMesh(nextMesh.materialProp)
                meshList!![nextMesh.materialProp] = newMesh
                newMesh.appendTransformed(nextMesh, mat, color, light)
            }
        }
    }

    open fun getVertexCount(): Int {
        var result = 0
        for (mesh in meshList!!.values) result += mesh!!.vertices!!.size
        return result
    }

    open fun getFaceCount(): Int {
        var result = 0
        for (mesh in meshList!!.values) result += mesh!!.faces!!.size
        return result
    }

    open fun append(nextMesh: MutableCollection<RawMesh?>?) {
        for (mesh in nextMesh!!) append(mesh)
    }

    open fun append(nextModel: RawModel?) { append(nextModel!!.meshList!!.values) }

    open fun applyMatrix(matrix: Matrix4f?) {
        for (mesh in meshList!!.values) mesh!!.applyMatrix(matrix)
    }

    open fun applyTranslation(x: Float, y: Float, z: Float) {
        for (mesh in meshList!!.values) mesh!!.applyTranslation(x, y, z)
    }

    open fun applyRotation(axis: Vector3f?, angle: Float) {
        for (mesh in meshList!!.values) mesh!!.applyRotation(axis, angle)
    }

    open fun applyScale(x: Float, y: Float, z: Float) {
        for (mesh in meshList!!.values) mesh!!.applyScale(x, y, z)
    }

    open fun applyMirror(vx: Boolean, vy: Boolean, vz: Boolean, nx: Boolean, ny: Boolean, nz: Boolean) {
        for (mesh in meshList!!.values) mesh!!.applyMirror(vx, vy, vz, nx, ny, nz)
    }

    open fun applyUVMirror(u: Boolean, v: Boolean) {
        for (mesh in meshList!!.values) mesh!!.applyUVMirror(u, v)
    }

    open fun generateNormals() { for (mesh in meshList!!.values) mesh!!.generateNormals() }
    open fun distinct() { for (mesh in meshList!!.values) mesh!!.distinct() }
    open fun triangulate() { for (mesh in meshList!!.values) mesh!!.triangulate() }

    open fun applyShear(dir: Vector3f?, shear: Vector3f?, ratio: Float) {
        for (mesh in meshList!!.values) mesh!!.applyShear(dir, shear, ratio)
    }

    open fun setAllRenderType(renderType: String?) {
        if (originalMaterialProps == null) {
            originalMaterialProps = HashMap()
            for ((material, mesh) in meshList!!) originalMaterialProps!![mesh] = material!!.copy()
        }
        for (entry in meshList!!.entries) {
            if (renderType!!.equals("reset")) {
                val originalProp = originalMaterialProps!![entry.value]
                if (originalProp != null) {
                    entry.value!!.materialProp!!.copyFrom(originalProp)
                    entry.key!!.copyFrom(originalProp)
                } else entry.value!!.setRenderType(renderType)
            } else entry.value!!.setRenderType(renderType)
            entry.key!!.shaderName = entry.value!!.materialProp!!.shaderName
        }
    }

    open fun replaceTexture(oldTexture: String?, newTexture: Identifier?) {
        for ((material, mesh) in meshList!!) {
            if (material!!.texture == null) continue
            val oldPath = material.texture!!.path
            if (oldPath.substring(oldPath.lastIndexOf('/') + 1).equals(oldTexture)) {
                mesh!!.materialProp!!.texture = newTexture
                material.texture = newTexture
            }
        }
    }

    open fun replaceAllTexture(newTexture: Identifier?) {
        for ((material, mesh) in meshList!!) {
            mesh!!.materialProp!!.texture = newTexture
            material!!.texture = newTexture
        }
    }

    open fun clearAttrState(attrType: VertAttrType?) {
        for ((material, _) in meshList!!) material!!.attrState!!.clearAttr(forwardNullable(attrType))
    }

    open fun writeBlazeBuffer(vertexConsumers: BufferSourceProxy?, matrix: Matrix4f?, light: Int, overlay: Int, drawContext: DrawContext?) {
        if (meshList!!.isEmpty()) return
        for (entry in meshList!!.entries) {
            val renderType = entry.key!!.getBlazeRenderType()
            val resultColor = entry.key!!.attrState!!.color ?: -1
            val resultLight = entry.key!!.attrState!!.lightmapUV ?: light
            val resultOverlay = entry.key!!.attrState!!.overlayUV?.let { AttrUtil.exchangeLightmapUVBits(it) } ?: overlay
            // A material callback can replace this entry's mesh. Read it after the callback,
            // then evaluate buffer dispatch even when the selected receiver is null.
            val mesh = entry.value
            val buffer = vertexConsumers!!.getBuffer(renderType, entry.key!!.translucent)
            mesh!!.writeBlazeBuffer(buffer, matrix, resultColor, resultLight, resultOverlay, drawContext)
        }
    }

    open fun copy(): RawModel {
        val result = RawModel()
        result.sourceLocation = sourceLocation
        for (mesh in meshList!!.values) {
            val meshCopy = mesh!!.copy()
            result.meshList!![meshCopy.materialProp] = meshCopy
        }
        return result
    }

    open fun copyForMaterialChanges(): RawModel {
        val result = RawModel()
        result.sourceLocation = sourceLocation
        for (mesh in meshList!!.values) {
            val meshCopy = mesh!!.copyForMaterialChanges()
            result.meshList!![meshCopy.materialProp] = meshCopy
        }
        return result
    }

    @Throws(IOException::class)
    open fun serializeTo(dos: DataOutputStream?) {
        val count = meshList!!.size
        dos!!.writeInt(count)
        for (mesh in meshList!!.values) mesh!!.serializeTo(dos)
    }

    open fun setMatixProcess(matrixProcess: Function<Matrix4f?, Matrix4f?>?) {
        for (mesh in meshList!!.values) mesh!!.setMatixProcess(matrixProcess)
    }

    // Java subclasses may handle null even when the Kotlin base API marks its argument non-null.
    @Suppress("UNCHECKED_CAST") private fun <T> forwardNullable(value: T?): T = value as T
}
