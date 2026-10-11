package cn.zbx1425.sowcer.model

import cn.zbx1425.sowcer.math.Matrix4f
import cn.zbx1425.sowcer.`object`.InstanceBuf
import cn.zbx1425.sowcer.`object`.VertArray
import cn.zbx1425.sowcer.vertex.VertAttrMapping
import net.minecraft.resources.Identifier
import java.io.Closeable
import java.util.ArrayList
import java.util.function.Function

open class VertArrays : Closeable {
    @JvmField val meshList = ArrayList<VertArray?>()

    open fun replaceTexture(oldTexture: String?, newTexture: Identifier?) {
        for (vertArray in meshList) {
            val material = vertArray!!.materialProp!!
            if (material.texture == null) continue
            val oldPath = material.texture!!.path
            if (oldPath.substring(oldPath.lastIndexOf('/') + 1) == oldTexture) material.texture = newTexture
        }
    }

    open fun replaceAllTexture(newTexture: Identifier?) {
        for (vertArray in meshList) vertArray!!.materialProp!!.texture = newTexture
    }

    open fun copyForMaterialChanges(): VertArrays {
        val result = VertArrays()
        for (vertArray in meshList) result.meshList.add(vertArray!!.copyForMaterialChanges())
        return result
    }

    open fun setMatixProcess(matrixProcess: Function<Matrix4f?, Matrix4f?>?) {
        for (vertArray in meshList) vertArray!!.materialProp!!.setMatixProcess(matrixProcess)
    }

    override fun close() {
        for (mesh in meshList) mesh!!.close()
    }

    companion object {
        // Preserve the non-final Java static factory, including subclass static method hiding.
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun createAll(model: Model, mapping: VertAttrMapping?, instanceBuf: InstanceBuf?): VertArrays {
            val result = VertArrays()
            for (mesh in model.meshList!!) {
                val meshVertArray = VertArray()
                meshVertArray.create(mesh, mapping, instanceBuf)
                result.meshList.add(meshVertArray)
            }
            return result
        }
    }
}
